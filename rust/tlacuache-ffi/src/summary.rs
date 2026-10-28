//! Resumen de la selección para la barra de estado (`core::summary`).

use tlacuache_core::entry as core_entry;
use tlacuache_core::summary::SelectionSummary as CoreSummary;

use crate::listing::FileEntry;

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, uniffi::Record)]
pub struct SelectionSummary {
    pub files: u32,
    pub dirs: u32,
    /// Suma de tamaños de los archivos; las carpetas no cuentan.
    pub bytes: u64,
}

/// Cuántos archivos y carpetas hay en `entries` y cuánto pesan los archivos, en una llamada.
#[uniffi::export]
pub fn summarize(entries: Vec<FileEntry>) -> SelectionSummary {
    let entries: Vec<core_entry::FileEntry> = entries.into_iter().map(Into::into).collect();
    let s = CoreSummary::from_entries(&entries);
    SelectionSummary {
        files: s.files,
        dirs: s.dirs,
        bytes: s.bytes,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn counts_files_and_folders_and_sums_file_sizes() {
        let entry = |name: &str, is_dir: bool, size: u64| FileEntry {
            name: name.into(),
            is_dir,
            is_hidden: false,
            size,
            modified_ms: None,
        };
        let s = summarize(vec![
            entry("a.jpg", false, 10),
            entry("Fotos", true, 4096),
            entry("b.mp4", false, 5),
        ]);
        assert_eq!(
            s,
            SelectionSummary {
                files: 2,
                dirs: 1,
                bytes: 15
            }
        );
    }
}
