//! Sesión que se restaura al reabrir la app (`core::session`): paneles,
//! pestañas, modo dual, divisor y barra lateral. La E/S la hace Kotlin.
//!
//! En Android `uri` guarda la ruta absoluta: la sesión es estado local, no
//! se comparte con Linux.

use tlacuache_core::config::ViewMode;
use tlacuache_core::session::{PaneSession, Session, TabSession};

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct SessionTab {
    pub path: String,
    pub icon_view: bool,
    pub show_hidden: bool,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct SessionPane {
    pub selected: u32,
    pub tabs: Vec<SessionTab>,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct SessionState {
    pub dual_pane: bool,
    /// Fracción del ancho para el panel A; `None` = mitad y mitad.
    pub panes_ratio: Option<f64>,
    pub active_pane: u32,
    pub sidebar_visible: bool,
    /// A y B, en ese orden.
    pub panes: Vec<SessionPane>,
}

/// Sesión guardada, ya saneada por el core; `None` si el TOML está dañado.
#[uniffi::export]
pub fn session_from_toml(text: String) -> Option<SessionState> {
    let s = Session::from_toml(&text).ok()?;
    Some(SessionState {
        dual_pane: s.window.dual_pane,
        panes_ratio: s.window.panes_ratio,
        active_pane: s.window.active_pane as u32,
        sidebar_visible: s.window.sidebar_visible,
        panes: s
            .panes
            .into_iter()
            .map(|p| SessionPane {
                selected: p.selected as u32,
                tabs: p
                    .tabs
                    .into_iter()
                    .map(|t| SessionTab {
                        path: t.uri,
                        icon_view: t.view == ViewMode::Icons,
                        show_hidden: t.show_hidden,
                    })
                    .collect(),
            })
            .collect(),
    })
}

/// TOML de [`SessionState`]; los campos que Android no usa quedan con su valor por defecto.
#[uniffi::export]
pub fn session_to_toml(state: SessionState) -> String {
    let mut s = Session::default();
    s.window.dual_pane = state.dual_pane;
    s.window.panes_ratio = state.panes_ratio;
    s.window.active_pane = state.active_pane as usize;
    s.window.sidebar_visible = state.sidebar_visible;
    s.panes = state
        .panes
        .into_iter()
        .map(|p| PaneSession {
            selected: p.selected as usize,
            tabs: p
                .tabs
                .into_iter()
                .map(|t| TabSession {
                    uri: t.path,
                    view: if t.icon_view {
                        ViewMode::Icons
                    } else {
                        ViewMode::Details
                    },
                    show_hidden: t.show_hidden,
                })
                .collect(),
            ..PaneSession::default()
        })
        .collect();
    // Solo campos simples: serializar no puede fallar.
    s.to_toml().unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn round_trip_and_sanitizing() {
        let state = SessionState {
            dual_pane: true,
            panes_ratio: Some(0.4),
            active_pane: 1,
            sidebar_visible: false,
            panes: vec![
                SessionPane {
                    selected: 1,
                    tabs: vec![
                        SessionTab {
                            path: "/storage/emulated/0".into(),
                            icon_view: false,
                            show_hidden: false,
                        },
                        SessionTab {
                            path: "/storage/emulated/0/DCIM".into(),
                            icon_view: true,
                            show_hidden: true,
                        },
                    ],
                },
                SessionPane {
                    selected: 0,
                    tabs: vec![SessionTab {
                        path: "/storage/emulated/0/Download".into(),
                        icon_view: false,
                        show_hidden: false,
                    }],
                },
            ],
        };
        assert_eq!(
            session_from_toml(session_to_toml(state.clone())),
            Some(state)
        );
        assert_eq!(session_from_toml("[[roto".into()), None);
        // El core corrige lo absurdo: proporción fuera de rango, pestaña elegida inexistente.
        let fixed =
            session_from_toml("[window]\npanes_ratio = 5.0\n[[panes]]\nselected = 9\n".into())
                .unwrap();
        assert_eq!(fixed.panes_ratio, Some(0.9));
        assert_eq!(fixed.panes[0].selected, 0);
    }
}
