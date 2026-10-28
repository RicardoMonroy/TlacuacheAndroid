//! `[general]` de config.toml (Preferencias) y validación al importar un
//! config.toml (ADR-A11). Entra y sale texto TOML; la E/S la hace Kotlin.

use tlacuache_core::config::{self, Config, ViewMode as CoreView};

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum ViewMode {
    /// Columnas Miller (en Android, lista detallada hasta A6.2).
    Columns,
    Details,
    Icons,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct GeneralSetting {
    pub show_hidden: bool,
    pub dual_pane: bool,
    pub confirm_trash: bool,
    pub default_view: ViewMode,
}

impl From<config::General> for GeneralSetting {
    fn from(g: config::General) -> Self {
        Self {
            show_hidden: g.show_hidden,
            dual_pane: g.dual_pane,
            confirm_trash: g.confirm_trash,
            default_view: match g.default_view {
                CoreView::Columns => ViewMode::Columns,
                CoreView::Details => ViewMode::Details,
                CoreView::Icons => ViewMode::Icons,
            },
        }
    }
}

impl From<&GeneralSetting> for config::General {
    fn from(g: &GeneralSetting) -> Self {
        Self {
            show_hidden: g.show_hidden,
            dual_pane: g.dual_pane,
            confirm_trash: g.confirm_trash,
            default_view: match g.default_view {
                ViewMode::Columns => CoreView::Columns,
                ViewMode::Details => CoreView::Details,
                ViewMode::Icons => CoreView::Icons,
            },
        }
    }
}

/// `[general]` del texto; `None` si no es TOML válido (valores por omisión).
#[uniffi::export]
pub fn general_setting(config_toml: String) -> Option<GeneralSetting> {
    Config::from_toml(&config_toml)
        .ok()
        .map(|c| c.general.into())
}

/// Reescribe `[general]` conservando comentarios; `None` si el texto no es TOML válido.
#[uniffi::export]
pub fn update_general_setting(config_toml: String, setting: GeneralSetting) -> Option<String> {
    config::update_general_toml(&config_toml, &(&setting).into()).ok()
}

/// Error de un config.toml que se quiere importar («… (línea N)»), o `None` si es válido.
#[uniffi::export]
pub fn config_error(config_toml: String) -> Option<String> {
    let err = Config::from_toml(&config_toml).err()?;
    let line = err.span().map(|span| {
        config_toml[..span.start.min(config_toml.len())]
            .matches('\n')
            .count()
            + 1
    });
    Some(match line {
        Some(line) => format!("{} (línea {line})", err.message()),
        None => err.message().to_owned(),
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::favorites::config_template;

    #[test]
    fn general_round_trips_through_the_template() {
        let text = config_template();
        let mut general = general_setting(text.clone()).expect("plantilla válida");
        assert_eq!(general.default_view, ViewMode::Columns);
        general.show_hidden = true;
        general.default_view = ViewMode::Icons;
        let updated = update_general_setting(text, general.clone()).expect("TOML válido");
        assert_eq!(general_setting(updated), Some(general));
    }

    #[test]
    fn import_errors_say_the_line() {
        assert_eq!(config_error(config_template()), None);
        let err = config_error("[general]\nshow_hidden = maybe\n".into()).expect("inválido");
        assert!(err.ends_with("(línea 2)"), "{err}");
    }
}
