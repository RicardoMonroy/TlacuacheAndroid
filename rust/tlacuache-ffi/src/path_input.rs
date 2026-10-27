//! Barra de ruta editable (`core::path_input`): `~` y autocompletado.

use std::path::Path;

use tlacuache_core::path_input as core;

/// Lo escrito partido en la carpeta tal cual y el nombre a medias.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct CompletionSplit {
    pub dir: String,
    pub partial: String,
}

/// Expande `~` y `~/…` con `home` (en Android, la raíz del almacenamiento interno).
#[uniffi::export]
pub fn expand_tilde(input: String, home: String) -> String {
    core::expand_tilde(&input, Path::new(&home))
        .to_string_lossy()
        .into_owned()
}

#[uniffi::export]
pub fn split_for_completion(input: String) -> CompletionSplit {
    let (dir, partial) = core::split_for_completion(&input);
    CompletionSplit {
        dir: dir.to_owned(),
        partial: partial.to_owned(),
    }
}

/// Candidatos que empiezan con `partial`, en orden natural; ocultos solo si `partial` empieza con `.`.
#[uniffi::export]
pub fn completion_matches(partial: String, candidates: Vec<String>) -> Vec<String> {
    core::completion_matches(&partial, candidates.iter().map(String::as_str))
        .into_iter()
        .map(str::to_owned)
        .collect()
}

/// Texto completado al pulsar Tab, o `None` si no se puede extender.
#[uniffi::export]
pub fn complete(input: String, candidates: Vec<String>) -> Option<String> {
    let names: Vec<&str> = candidates.iter().map(String::as_str).collect();
    core::complete(&input, &names)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn names(list: &[&str]) -> Vec<String> {
        list.iter().map(|s| (*s).to_owned()).collect()
    }

    #[test]
    fn tilde_is_internal_storage() {
        let home = "/storage/emulated/0".to_owned();
        assert_eq!(
            expand_tilde("~/DCIM".into(), home.clone()),
            "/storage/emulated/0/DCIM"
        );
        assert_eq!(
            expand_tilde("~".into(), home.clone()),
            "/storage/emulated/0"
        );
        assert_eq!(expand_tilde("/sdcard".into(), home), "/sdcard");
    }

    #[test]
    fn completes_unique_and_common_prefix() {
        let dirs = names(&["Documents", "Download", "DCIM", ".thumbnails"]);
        assert_eq!(
            complete("~/Doc".into(), dirs.clone()).as_deref(),
            Some("~/Documents/")
        );
        assert_eq!(complete("~/Do".into(), dirs.clone()).as_deref(), None);
        assert_eq!(
            completion_matches("do".into(), dirs.clone()),
            ["Documents", "Download"]
        );
        assert!(
            completion_matches("".into(), dirs)
                .iter()
                .all(|d| !d.starts_with('.'))
        );
        assert_eq!(
            split_for_completion("~/DCIM/Cam".into()),
            CompletionSplit {
                dir: "~/DCIM/".into(),
                partial: "Cam".into()
            }
        );
    }
}
