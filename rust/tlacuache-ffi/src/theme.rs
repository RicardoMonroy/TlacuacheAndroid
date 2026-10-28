//! Temas del core como tokens planos para Compose (ADR-A07).
//!
//! Los colores viajan como ARGB opaco (`0xFFRRGGBB`), listos para
//! `androidx.compose.ui.graphics.Color(Int)`.

use std::path::Path;

use tlacuache_core::config::{self, Config};
use tlacuache_core::theme::{self as core, Color};
use tlacuache_core::theme_catalog::ThemeCatalog;

fn argb(c: Color) -> u32 {
    0xFF00_0000 | (u32::from(c.r) << 16) | (u32::from(c.g) << 8) | u32::from(c.b)
}

/// Registro UniFFI con un `u32` ARGB por campo, convertido desde la tabla de colores del core.
macro_rules! color_record {
    ($name:ident, $src:ty, [$($field:ident),+ $(,)?]) => {
        #[derive(Debug, Clone, PartialEq, uniffi::Record)]
        pub struct $name {
            $(pub $field: u32,)+
        }

        impl From<&$src> for $name {
            fn from(src: &$src) -> Self {
                Self { $($field: argb(src.$field),)+ }
            }
        }
    };
}

color_record!(
    ThemeColors,
    core::Colors,
    [
        window_bg,
        sidebar_bg,
        sidebar_fg,
        header_bg,
        header_fg,
        pane_bg,
        pane_alt_row,
        fg,
        fg_muted,
        border,
        border_strong,
        hover_bg,
        accent,
        accent_fg,
        focus_ring,
        selection_bg,
        selection_fg,
        selection_inactive_bg,
        pane_active_border,
        success,
        warning,
        error,
        info,
        link,
    ]
);

color_record!(
    AgeColors,
    core::AgeColors,
    [chip_fg, hour, day, week, month, year]
);

color_record!(
    FileTypeColors,
    core::FileTypeColors,
    [
        folder,
        code,
        image,
        video,
        audio,
        archive,
        document,
        pdf,
        spreadsheet,
        text,
        executable,
        other,
    ]
);

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum IconStyle {
    Color,
    Symbolic,
    Glyph,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum SelectionStyle {
    Fill,
    Invert,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum AgeStyle {
    Chip,
    Text,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct ThemeStyle {
    /// Vacío = fuente del sistema.
    pub font_ui: String,
    pub font_mono: String,
    pub mono_ui: bool,
    pub radius: u32,
    pub border_width: u32,
    pub row_height: u32,
    pub flat: bool,
    pub glow: bool,
    pub uppercase_headers: bool,
    pub bracket_titles: bool,
    pub icon_style: IconStyle,
    pub selection_style: SelectionStyle,
    pub age_style: AgeStyle,
}

/// Glifos en lugar de íconos (`icon_style = "glyph"`).
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct ThemeGlyphs {
    pub folder: String,
    pub file: String,
    pub symlink: String,
    pub executable: String,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct ThemeTokens {
    pub id: String,
    pub name: String,
    pub dark: bool,
    pub style: ThemeStyle,
    pub colors: ThemeColors,
    pub age: AgeColors,
    pub filetypes: FileTypeColors,
    /// Solo si el tema usa glifos.
    pub glyphs: Option<ThemeGlyphs>,
}

impl From<&core::Theme> for ThemeTokens {
    fn from(t: &core::Theme) -> Self {
        let s = &t.style;
        Self {
            id: t.meta.id.clone(),
            name: t.meta.name.clone(),
            dark: t.meta.variant == core::Variant::Dark,
            style: ThemeStyle {
                font_ui: s.font_ui.clone(),
                font_mono: s.font_mono.clone(),
                mono_ui: s.mono_ui,
                radius: s.radius,
                border_width: s.border_width,
                row_height: s.row_height,
                flat: s.flat,
                glow: s.glow,
                uppercase_headers: s.uppercase_headers,
                bracket_titles: s.bracket_titles,
                icon_style: match s.icon_style {
                    core::IconStyle::Color => IconStyle::Color,
                    core::IconStyle::Symbolic => IconStyle::Symbolic,
                    core::IconStyle::Glyph => IconStyle::Glyph,
                },
                selection_style: match s.selection_style {
                    core::SelectionStyle::Fill => SelectionStyle::Fill,
                    core::SelectionStyle::Invert => SelectionStyle::Invert,
                },
                age_style: match s.age_style {
                    core::AgeStyle::Chip => AgeStyle::Chip,
                    core::AgeStyle::Text => AgeStyle::Text,
                },
            },
            colors: (&t.colors).into(),
            age: (&t.age).into(),
            filetypes: (&t.filetypes).into(),
            glyphs: t.glyphs.as_ref().map(|g| ThemeGlyphs {
                folder: g.folder.clone(),
                file: g.file.clone(),
                symlink: g.symlink.clone(),
                executable: g.executable.clone(),
            }),
        }
    }
}

/// Un tema del catálogo; `contrast` lista los pares que no cumplen (solo temas de usuario: los incluidos
/// se validan en las pruebas del core).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct CatalogTheme {
    pub tokens: ThemeTokens,
    pub user: bool,
    pub contrast: Vec<String>,
}

/// Los temas disponibles y los archivos de usuario que no se pudieron leer («roto.toml: … (línea N)»).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct ThemeList {
    pub themes: Vec<CatalogTheme>,
    pub errors: Vec<String>,
}

/// Incluidos más los `*.toml` de `user_dir` (THEMES.md §6), en una sola llamada. Lee disco: llamar en IO.
#[uniffi::export]
pub fn theme_catalog(user_dir: String) -> ThemeList {
    let catalog = ThemeCatalog::load(Path::new(&user_dir));
    let themes = catalog
        .themes()
        .iter()
        .map(|t| {
            let user = catalog.is_user(&t.meta.id);
            let contrast = if user {
                t.validate().iter().map(ToString::to_string).collect()
            } else {
                Vec::new()
            };
            CatalogTheme {
                tokens: t.into(),
                user,
                contrast,
            }
        })
        .collect();
    let errors = catalog
        .errors()
        .iter()
        .map(|e| {
            let file = e.path.file_name().map_or_else(
                || e.path.display().to_string(),
                |n| n.to_string_lossy().into_owned(),
            );
            format!("{file}: {}", e.message)
        })
        .collect();
    ThemeList { themes, errors }
}

/// `[theme]` de config.toml (THEMES.md §5.4).
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct ThemeSetting {
    pub name: String,
    pub follow_system: bool,
    pub light: String,
    pub dark: String,
}

impl From<config::Theme> for ThemeSetting {
    fn from(t: config::Theme) -> Self {
        Self {
            name: t.name,
            follow_system: t.follow_system,
            light: t.light,
            dark: t.dark,
        }
    }
}

impl From<&ThemeSetting> for config::Theme {
    fn from(t: &ThemeSetting) -> Self {
        Self {
            name: t.name.clone(),
            follow_system: t.follow_system,
            light: t.light.clone(),
            dark: t.dark.clone(),
        }
    }
}

/// `[theme]` del texto de config.toml; `None` si no es TOML válido (se usan los valores por omisión).
#[uniffi::export]
pub fn theme_setting(config_toml: String) -> Option<ThemeSetting> {
    Config::from_toml(&config_toml).ok().map(|c| c.theme.into())
}

/// Id del tema a usar según la preferencia clara/oscura del sistema.
#[uniffi::export]
pub fn resolve_theme(setting: ThemeSetting, system_dark: bool) -> String {
    config::Theme::from(&setting)
        .resolve(system_dark)
        .to_owned()
}

/// Reescribe `[theme]` conservando comentarios y el resto; `None` si el texto no es TOML válido (no se
/// toca el archivo).
#[uniffi::export]
pub fn update_theme_setting(config_toml: String, setting: ThemeSetting) -> Option<String> {
    config::update_theme_toml(&config_toml, &(&setting).into()).ok()
}

/// Id del tema predeterminado (`nord`).
#[uniffi::export]
pub fn default_theme_id() -> String {
    core::DEFAULT_THEME.to_owned()
}

/// Tema incluido por id, sin tocar disco. `None` si no existe.
#[uniffi::export]
pub fn builtin_theme(id: String) -> Option<ThemeTokens> {
    ThemeCatalog::builtin().get(&id).map(ThemeTokens::from)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::favorites::config_template;

    #[test]
    fn argb_is_opaque_rgb() {
        assert_eq!(
            argb(Color {
                r: 0x2E,
                g: 0x34,
                b: 0x40
            }),
            0xFF2E_3440
        );
    }

    #[test]
    fn default_theme_is_dark_nord() {
        let nord = builtin_theme(default_theme_id()).expect("nord incluido");
        assert_eq!(nord.id, "nord");
        assert!(nord.dark);
        assert_eq!(nord.colors.window_bg, 0xFF2E_3440);
        assert_eq!(nord.style.font_mono, "JetBrains Mono");
    }

    #[test]
    fn every_builtin_converts() {
        for id in core::Theme::builtin_ids() {
            assert!(builtin_theme(id.to_owned()).is_some(), "{id}");
        }
        assert!(builtin_theme("nope".into()).is_none());
    }

    #[test]
    fn catalog_adds_user_themes_and_reports_broken_files() {
        let dir = std::env::temp_dir().join(format!("tl-themes-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        let nord = include_str!("../tests/nord_copy.toml");
        std::fs::write(
            dir.join("mio.toml"),
            nord.replace("id = \"nord\"", "id = \"mio\""),
        )
        .unwrap();
        std::fs::write(dir.join("roto.toml"), "[meta\n").unwrap();
        let list = theme_catalog(dir.display().to_string());
        std::fs::remove_dir_all(&dir).unwrap();
        let mio = list
            .themes
            .iter()
            .find(|t| t.tokens.id == "mio")
            .expect("tema de usuario");
        assert!(mio.user);
        assert!(
            list.themes
                .iter()
                .any(|t| t.tokens.id == "consola" && t.tokens.glyphs.is_some())
        );
        assert_eq!(list.errors.len(), 1);
        assert!(
            list.errors[0].starts_with("roto.toml: "),
            "{}",
            list.errors[0]
        );
    }

    #[test]
    fn setting_resolves_and_rewrites_config() {
        let text = config_template();
        let mut setting = theme_setting(text.clone()).expect("plantilla válida");
        assert_eq!(resolve_theme(setting.clone(), true), "nord");
        setting.follow_system = true;
        assert_eq!(resolve_theme(setting.clone(), false), "claro");
        let updated = update_theme_setting(text, setting.clone()).expect("TOML válido");
        assert_eq!(theme_setting(updated), Some(setting));
        assert!(theme_setting("[theme".into()).is_none());
    }
}
