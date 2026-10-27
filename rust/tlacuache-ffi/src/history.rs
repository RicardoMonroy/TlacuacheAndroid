//! Historial atrás/adelante de una pestaña (`core::history`), como objeto UniFFI.

use std::sync::Mutex;

use tlacuache_core::history::History;

/// Rutas absolutas como texto. Las llamadas llegan desde un solo hilo, pero
/// UniFFI exige `Sync`.
#[derive(uniffi::Object)]
pub struct PathHistory(Mutex<History<String>>);

#[uniffi::export]
impl PathHistory {
    #[uniffi::constructor]
    pub fn new(initial: String) -> Self {
        Self(Mutex::new(History::new(initial)))
    }

    pub fn current(&self) -> String {
        self.lock().current().clone()
    }

    pub fn can_go_back(&self) -> bool {
        self.lock().can_go_back()
    }

    pub fn can_go_forward(&self) -> bool {
        self.lock().can_go_forward()
    }

    /// `false` si ya estaba en `to`.
    pub fn navigate(&self, to: String) -> bool {
        self.lock().navigate(to)
    }

    pub fn back(&self) -> Option<String> {
        self.lock().back().cloned()
    }

    pub fn forward(&self) -> Option<String> {
        self.lock().forward().cloned()
    }
}

impl PathHistory {
    fn lock(&self) -> std::sync::MutexGuard<'_, History<String>> {
        // Un pánico con el candado tomado no deja el historial a medias: se sigue usando.
        self.0
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn back_and_forward_round_trip() {
        let h = PathHistory::new("/a".into());
        assert!(!h.can_go_back());
        assert!(h.navigate("/a/b".into()));
        assert!(!h.navigate("/a/b".into()));
        assert_eq!(h.back().as_deref(), Some("/a"));
        assert!(h.can_go_forward());
        assert_eq!(h.forward().as_deref(), Some("/a/b"));
        assert_eq!(h.forward(), None);
        assert_eq!(h.current(), "/a/b");
    }
}
