//! Uso de disco de un volumen para la barra lateral (`core::usage`).

use tlacuache_core::usage as core;

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum UsageLevel {
    Normal,
    /// ≥ 80 % usado.
    Warning,
    /// ≥ 90 % usado.
    Critical,
}

#[derive(Debug, Clone, Copy, PartialEq, uniffi::Record)]
pub struct Usage {
    /// Fracción usada, 0.0–1.0.
    pub fraction: f64,
    pub level: UsageLevel,
}

/// Fracción usada y nivel de aviso de un volumen, en una sola llamada.
#[uniffi::export]
pub fn usage(free: u64, size: u64) -> Usage {
    let fraction = core::used_fraction(free, size);
    let level = match core::usage_level(fraction) {
        core::UsageLevel::Normal => UsageLevel::Normal,
        core::UsageLevel::Warning => UsageLevel::Warning,
        core::UsageLevel::Critical => UsageLevel::Critical,
    };
    Usage { fraction, level }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn usage_follows_core_thresholds() {
        assert_eq!(usage(50, 100).level, UsageLevel::Normal);
        assert_eq!(usage(20, 100).level, UsageLevel::Warning);
        assert_eq!(usage(5, 100).level, UsageLevel::Critical);
        assert_eq!(usage(25, 100).fraction, 0.75);
        assert_eq!(usage(0, 0).fraction, 0.0);
    }
}
