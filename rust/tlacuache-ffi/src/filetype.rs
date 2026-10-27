//! Categoría de un archivo para su ícono y color (`core::filetype`).

use tlacuache_core::filetype::{self as core};

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum FileCategory {
    Folder,
    Code,
    Image,
    Video,
    Audio,
    Archive,
    Document,
    Pdf,
    Spreadsheet,
    Text,
    Executable,
    Other,
}

/// Categoría de una entrada según su tipo MIME (por extensión) y su nombre.
/// Se llama por celda visible, no por entrada del listado (ADR-A03).
#[uniffi::export]
pub fn classify(name: String, mime: Option<String>, is_dir: bool) -> FileCategory {
    match core::classify(mime.as_deref(), &name, is_dir, false) {
        core::FileCategory::Folder => FileCategory::Folder,
        core::FileCategory::Code => FileCategory::Code,
        core::FileCategory::Image => FileCategory::Image,
        core::FileCategory::Video => FileCategory::Video,
        core::FileCategory::Audio => FileCategory::Audio,
        core::FileCategory::Archive => FileCategory::Archive,
        core::FileCategory::Document => FileCategory::Document,
        core::FileCategory::Pdf => FileCategory::Pdf,
        core::FileCategory::Spreadsheet => FileCategory::Spreadsheet,
        core::FileCategory::Text => FileCategory::Text,
        core::FileCategory::Executable => FileCategory::Executable,
        core::FileCategory::Other => FileCategory::Other,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn classifies_by_mime_and_name() {
        assert_eq!(classify("x".into(), None, true), FileCategory::Folder);
        assert_eq!(
            classify("a.jpg".into(), Some("image/jpeg".into()), false),
            FileCategory::Image
        );
        assert_eq!(
            classify("a.pdf".into(), Some("application/pdf".into()), false),
            FileCategory::Pdf
        );
        assert_eq!(classify("main.rs".into(), None, false), FileCategory::Code);
        assert_eq!(classify("raro".into(), None, false), FileCategory::Other);
    }
}
