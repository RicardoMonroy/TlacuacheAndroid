//! Temas del core como tokens planos para Compose (ADR-A07).
//!
//! Los colores viajan como ARGB opaco (`0xFFRRGGBB`), listos para
//! `androidx.compose.ui.graphics.Color(Int)`.

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

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct ThemeTokens {
    pub id: String,
    pub name: String,
    pub dark: bool,
    pub style: ThemeStyle,
    pub colors: ThemeColors,
    pub age: AgeColors,
    pub filetypes: FileTypeColors,
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
        }
    }
}

/// Id del tema predeterminado (`nord`).
#[uniffi::export]
pub fn default_theme_id() -> String {
    core::DEFAULT_THEME.to_owned()
}

/// Tema incluido por id, sin tocar disco. `None` si no existe.
// shortcut: solo temas incluidos; los de usuario (ThemeCatalog::load) llegan en A5.1.
#[uniffi::export]
pub fn builtin_theme(id: String) -> Option<ThemeTokens> {
    ThemeCatalog::builtin().get(&id).map(ThemeTokens::from)
}

#[cfg(test)]
mod tests {
    use super::*;

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
}
