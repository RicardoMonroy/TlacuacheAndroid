//! Favoritos de la barra lateral (`core::favorites`), guardados en
//! `[[favorites]]` de `config.toml` (ADR-A11).
//!
//! La E/S del archivo la hace Kotlin con escritura atómica: aquí entra y sale
//! texto TOML (ARCHITECTURE §4).

use std::fmt;
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex, MutexGuard};

use tlacuache_core::config::{self, Config, FavoriteGroup as CoreGroup};
use tlacuache_core::favorites as core;
use tlacuache_core::path_input::expand_tilde;

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FavoriteGroup {
    pub name: String,
    /// Rutas absolutas (en el archivo se guardan con `~` si están en el interno).
    pub paths: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Error)]
pub enum FavoritesError {
    EmptyName,
    DuplicateGroup {
        name: String,
    },
    OutOfRange,
    /// `config.toml` no es TOML válido: no se sobrescribe.
    InvalidToml,
}

impl fmt::Display for FavoritesError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::EmptyName => f.write_str("el nombre del grupo está vacío"),
            Self::DuplicateGroup { name } => write!(f, "ya existe un grupo llamado «{name}»"),
            Self::OutOfRange => f.write_str("posición fuera de rango"),
            Self::InvalidToml => f.write_str("config.toml no es TOML válido"),
        }
    }
}

impl std::error::Error for FavoritesError {}

impl From<core::FavoritesError> for FavoritesError {
    fn from(e: core::FavoritesError) -> Self {
        match e {
            core::FavoritesError::EmptyName => Self::EmptyName,
            core::FavoritesError::DuplicateGroup(name) => Self::DuplicateGroup { name },
            core::FavoritesError::OutOfRange => Self::OutOfRange,
        }
    }
}

/// Plantilla de `config.toml` para crearlo si no existe.
#[uniffi::export]
pub fn config_template() -> String {
    config::DEFAULT_TEMPLATE.to_owned()
}

/// Grupos de favoritos en memoria; cada cambio se guarda con [`Favorites::update_toml`].
#[derive(uniffi::Object)]
pub struct Favorites {
    groups: Mutex<Vec<CoreGroup>>,
    /// Raíz del almacenamiento interno: el `~` de las rutas guardadas.
    home: PathBuf,
}

#[uniffi::export]
impl Favorites {
    #[uniffi::constructor]
    pub fn from_toml(text: String, home: String) -> Result<Arc<Self>, FavoritesError> {
        let config = Config::from_toml(&text).map_err(|_| FavoritesError::InvalidToml)?;
        Ok(Arc::new(Self {
            groups: Mutex::new(config.favorites),
            home: PathBuf::from(home),
        }))
    }

    pub fn groups(&self) -> Vec<FavoriteGroup> {
        self.lock()
            .iter()
            .map(|g| FavoriteGroup {
                name: g.group.clone(),
                paths: g
                    .paths
                    .iter()
                    .map(|p| expand_tilde(p, &self.home).to_string_lossy().into_owned())
                    .collect(),
            })
            .collect()
    }

    /// Añade `path` al final del primer grupo (lo crea como `default_group` si no hay
    /// ninguno). `false` si ya estaba.
    pub fn add(&self, path: String, default_group: String) -> bool {
        let mut groups = self.lock();
        let group = core::default_group(&mut groups, &default_group);
        let stored = core::contract_home(Path::new(&path), &self.home);
        core::insert_favorite(&mut groups, group, None, &stored).unwrap_or(false)
    }

    pub fn remove(&self, group: u32, index: u32) -> Result<(), FavoritesError> {
        core::remove_favorite(&mut self.lock(), (group as usize, index as usize))?;
        Ok(())
    }

    /// Mueve un favorito dentro de su grupo o a otro; `to_index` cuenta antes de quitarlo.
    pub fn move_favorite(
        &self,
        from_group: u32,
        from_index: u32,
        to_group: u32,
        to_index: u32,
    ) -> Result<(), FavoritesError> {
        core::move_favorite(
            &mut self.lock(),
            (from_group as usize, from_index as usize),
            (to_group as usize, to_index as usize),
        )?;
        Ok(())
    }

    pub fn add_group(&self, name: String) -> Result<u32, FavoritesError> {
        Ok(core::add_group(&mut self.lock(), &name)? as u32)
    }

    pub fn rename_group(&self, index: u32, name: String) -> Result<(), FavoritesError> {
        core::rename_group(&mut self.lock(), index as usize, &name)?;
        Ok(())
    }

    pub fn remove_group(&self, index: u32) -> Result<(), FavoritesError> {
        core::remove_group(&mut self.lock(), index as usize)?;
        Ok(())
    }

    /// `text` con `[[favorites]]` reescrito; el resto del archivo queda igual.
    pub fn update_toml(&self, text: String) -> Result<String, FavoritesError> {
        config::update_favorites_toml(&text, &self.lock()).map_err(|_| FavoritesError::InvalidToml)
    }
}

impl Favorites {
    fn lock(&self) -> MutexGuard<'_, Vec<CoreGroup>> {
        self.groups.lock().unwrap_or_else(|e| e.into_inner())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const HOME: &str = "/storage/emulated/0";

    #[test]
    fn add_move_and_round_trip_through_toml() {
        let favs = Favorites::from_toml(config_template(), HOME.into()).unwrap();
        assert!(favs.add(format!("{HOME}/DCIM"), "Favoritos".into()));
        assert!(favs.add("/storage/1234-ABCD/Fotos".into(), "Favoritos".into()));
        assert!(!favs.add(format!("{HOME}/DCIM"), "Favoritos".into()));
        favs.move_favorite(0, 1, 0, 0).unwrap();
        let text = favs.update_toml(config_template()).unwrap();
        assert!(text.contains("\"~/DCIM\""));

        let again = Favorites::from_toml(text, HOME.into()).unwrap();
        assert_eq!(
            again.groups(),
            [FavoriteGroup {
                name: "Favoritos".into(),
                paths: vec!["/storage/1234-ABCD/Fotos".into(), format!("{HOME}/DCIM")],
            }]
        );
    }

    #[test]
    fn groups_validate_names_and_invalid_toml_is_refused() {
        let favs = Favorites::from_toml(String::new(), HOME.into()).unwrap();
        assert_eq!(favs.add_group("  ".into()), Err(FavoritesError::EmptyName));
        assert_eq!(favs.add_group("Clases".into()), Ok(0));
        assert_eq!(
            favs.add_group("Clases".into()),
            Err(FavoritesError::DuplicateGroup {
                name: "Clases".into()
            })
        );
        assert_eq!(favs.remove(0, 0), Err(FavoritesError::OutOfRange));
        assert!(Favorites::from_toml("[[x".into(), HOME.into()).is_err());
        assert_eq!(
            favs.update_toml("[[x".into()),
            Err(FavoritesError::InvalidToml)
        );
    }
}
