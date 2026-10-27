//! Nombres de archivo (`core::names`): validar, parte a seleccionar al
//! renombrar y nombre libre para «Conservar ambos».

use std::collections::HashSet;

use tlacuache_core::names as core;

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum NameError {
    Empty,
    /// «.» o «..».
    Reserved,
    ContainsSlash,
    ContainsNul,
    /// Más de 255 bytes.
    TooLong,
}

/// `None` si el nombre es válido; si no, por qué (para mostrarlo en el diálogo).
#[uniffi::export]
pub fn validate_name(name: String) -> Option<NameError> {
    core::validate_name(&name).err().map(|e| match e {
        core::NameError::Empty => NameError::Empty,
        core::NameError::Reserved => NameError::Reserved,
        core::NameError::ContainsSlash => NameError::ContainsSlash,
        core::NameError::ContainsNul => NameError::ContainsNul,
        core::NameError::TooLong => NameError::TooLong,
    })
}

/// Caracteres a seleccionar al renombrar: el nombre sin la extensión.
#[uniffi::export]
pub fn stem_len(name: String, is_dir: bool) -> u32 {
    core::stem_len(&name, is_dir) as u32
}

/// `name` si no está en `existing`; si no, la primera variante libre
/// («foto (2).jpg»). `existing` son los nombres del destino, en una sola llamada.
#[uniffi::export]
pub fn next_free_name(name: String, is_dir: bool, existing: Vec<String>) -> String {
    let taken: HashSet<&str> = existing.iter().map(String::as_str).collect();
    if !taken.contains(name.as_str()) {
        return name;
    }
    // Las variantes no se acaban: siempre hay una libre.
    let free = core::numbered_variants(&name, is_dir)
        .find(|candidate| !taken.contains(candidate.as_str()));
    free.unwrap_or(name)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn validates_names() {
        assert_eq!(validate_name("foto.jpg".into()), None);
        assert_eq!(validate_name("  ".into()), Some(NameError::Empty));
        assert_eq!(validate_name("..".into()), Some(NameError::Reserved));
        assert_eq!(validate_name("a/b".into()), Some(NameError::ContainsSlash));
        assert_eq!(validate_name("x".repeat(256)), Some(NameError::TooLong));
    }

    #[test]
    fn free_names_keep_the_extension() {
        let existing = vec![
            "foto.jpg".to_owned(),
            "foto (2).jpg".to_owned(),
            "Fotos".to_owned(),
        ];
        assert_eq!(
            next_free_name("nota.txt".into(), false, existing.clone()),
            "nota.txt"
        );
        assert_eq!(
            next_free_name("foto.jpg".into(), false, existing.clone()),
            "foto (3).jpg"
        );
        assert_eq!(next_free_name("Fotos".into(), true, existing), "Fotos (2)");
        assert_eq!(stem_len("foto.jpg".into(), false), 4);
        assert_eq!(stem_len(".bashrc".into(), false), 7);
    }
}
