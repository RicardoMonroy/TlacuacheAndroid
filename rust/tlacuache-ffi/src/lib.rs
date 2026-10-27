//! Puente UniFFI que expone `tlacuache-core` a la app de Android.
//!
//! Solo lo que la app usa, con registros propios y llamadas por lotes
//! (ADR-A02, ADR-A03).

uniffi::setup_scaffolding!();

mod history;
mod listing;
mod path_input;
mod theme;
mod usage;

/// Versión de la FFI y app-id del core enlazado; prueba de que el puente funciona.
#[uniffi::export]
pub fn core_version() -> String {
    format!(
        "tlacuache-ffi {} · {}",
        env!("CARGO_PKG_VERSION"),
        tlacuache_core::APP_ID
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn core_version_names_ffi_and_core() {
        let v = core_version();
        assert!(v.starts_with("tlacuache-ffi "));
        assert!(v.ends_with(tlacuache_core::APP_ID));
    }
}
