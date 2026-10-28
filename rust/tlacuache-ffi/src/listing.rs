//! Entradas de carpeta: orden, filtro y edad del core, por lotes (ADR-A03).

use std::time::{Duration, SystemTime, UNIX_EPOCH};

use tlacuache_core::age as core_age;
use tlacuache_core::entry as core_entry;
use tlacuache_core::filter::{self as core_filter, Filter};
use tlacuache_core::sort as core_sort;

/// Lo que Kotlin lee de cada entrada de la carpeta.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FileEntry {
    pub name: String,
    pub is_dir: bool,
    /// Lo decide la app: nombres con `.` (`Android/` ni se lista, ADR-A06).
    pub is_hidden: bool,
    pub size: u64,
    /// Milisegundos desde 1970; `None` si no se pudo leer.
    pub modified_ms: Option<i64>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum SortKey {
    Name,
    Extension,
    Size,
    Modified,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Record)]
pub struct SortSpec {
    pub key: SortKey,
    pub descending: bool,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum AgeBucket {
    Hour,
    Day,
    Week,
    Month,
    Year,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum AgeUnit {
    Now,
    Minutes,
    Hours,
    Days,
    Months,
    Years,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Record)]
pub struct Age {
    pub bucket: AgeBucket,
    pub unit: AgeUnit,
    /// Cantidad en `unit` (0 para `Now`).
    pub amount: u64,
}

pub(crate) fn system_time(ms: i64) -> SystemTime {
    let offset = Duration::from_millis(ms.unsigned_abs());
    if ms >= 0 {
        UNIX_EPOCH + offset
    } else {
        UNIX_EPOCH.checked_sub(offset).unwrap_or(UNIX_EPOCH)
    }
}

impl From<FileEntry> for core_entry::FileEntry {
    fn from(e: FileEntry) -> Self {
        let mut entry = core_entry::FileEntry::new(e.name, e.is_dir);
        entry.is_hidden = e.is_hidden;
        entry.size = e.size;
        entry.modified = e.modified_ms.map(system_time);
        entry
    }
}

impl From<SortSpec> for core_sort::SortSpec {
    fn from(s: SortSpec) -> Self {
        Self {
            key: match s.key {
                SortKey::Name => core_sort::SortKey::Name,
                SortKey::Extension => core_sort::SortKey::Extension,
                SortKey::Size => core_sort::SortKey::Size,
                SortKey::Modified => core_sort::SortKey::Modified,
            },
            direction: if s.descending {
                core_sort::SortDirection::Descending
            } else {
                core_sort::SortDirection::Ascending
            },
        }
    }
}

impl From<core_sort::SortSpec> for SortSpec {
    fn from(s: core_sort::SortSpec) -> Self {
        Self {
            key: match s.key {
                core_sort::SortKey::Name => SortKey::Name,
                core_sort::SortKey::Extension => SortKey::Extension,
                core_sort::SortKey::Size => SortKey::Size,
                core_sort::SortKey::Modified => SortKey::Modified,
            },
            descending: s.direction == core_sort::SortDirection::Descending,
        }
    }
}

/// Índices de `entries` que se muestran, ya ordenados: filtro de ocultos y
/// filtro rápido (`core::filter`) y orden natural con carpetas primero
/// (`core::sort`). Una sola llamada por listado.
#[uniffi::export]
pub fn arrange(
    entries: Vec<FileEntry>,
    sort: SortSpec,
    query: String,
    show_hidden: bool,
) -> Vec<u32> {
    let filter = Filter::new(show_hidden, &query);
    let spec = core_sort::SortSpec::from(sort);
    let entries: Vec<core_entry::FileEntry> = entries.into_iter().map(Into::into).collect();
    let mut visible: Vec<u32> = (0..entries.len() as u32)
        .filter(|&i| filter.matches(&entries[i as usize]))
        .collect();
    // sort_by es estable, como `SortSpec::sort` en Linux.
    visible.sort_by(|&a, &b| spec.compare(&entries[a as usize], &entries[b as usize]));
    visible
}

/// Tecla para el filtro rápido (ya sin Ctrl/Alt). `Char` lleva un solo carácter.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Enum)]
pub enum FilterKey {
    Char { text: String },
    Backspace,
    Escape,
}

/// Consulta nueva si la tecla la consume (ADR-006 de Linux), o `None` si debe
/// seguir a su acción normal (Backspace sube, Espacio alterna la vista previa).
#[uniffi::export]
pub fn apply_filter_key(query: String, key: FilterKey) -> Option<String> {
    let key = match key {
        FilterKey::Char { text } => {
            let mut chars = text.chars();
            match (chars.next(), chars.next()) {
                (Some(c), None) => core_filter::FilterKey::Char(c),
                _ => return None,
            }
        }
        FilterKey::Backspace => core_filter::FilterKey::Backspace,
        FilterKey::Escape => core_filter::FilterKey::Escape,
    };
    core_filter::apply_key(&query, key)
}

/// Edad de `modified_ms` vista desde `now_ms` (columna «Edad»). Se llama por
/// fila visible, no por entrada del listado.
#[uniffi::export]
pub fn age(modified_ms: i64, now_ms: i64) -> Age {
    let a = core_age::Age::between(system_time(modified_ms), system_time(now_ms));
    Age {
        bucket: match a.bucket {
            core_age::AgeBucket::Hour => AgeBucket::Hour,
            core_age::AgeBucket::Day => AgeBucket::Day,
            core_age::AgeBucket::Week => AgeBucket::Week,
            core_age::AgeBucket::Month => AgeBucket::Month,
            core_age::AgeBucket::Year => AgeBucket::Year,
        },
        unit: match a.unit {
            core_age::AgeUnit::Now => AgeUnit::Now,
            core_age::AgeUnit::Minutes => AgeUnit::Minutes,
            core_age::AgeUnit::Hours => AgeUnit::Hours,
            core_age::AgeUnit::Days => AgeUnit::Days,
            core_age::AgeUnit::Months => AgeUnit::Months,
            core_age::AgeUnit::Years => AgeUnit::Years,
        },
        amount: a.amount,
    }
}

#[cfg(test)]
mod tests {
    use std::time::Instant;

    use super::*;

    const NAME_ASC: SortSpec = SortSpec {
        key: SortKey::Name,
        descending: false,
    };

    fn file(name: &str) -> FileEntry {
        FileEntry {
            name: name.into(),
            is_dir: false,
            is_hidden: name.starts_with('.'),
            size: 0,
            modified_ms: None,
        }
    }

    fn dir(name: &str) -> FileEntry {
        FileEntry {
            is_dir: true,
            ..file(name)
        }
    }

    fn names(entries: &[FileEntry], idx: &[u32]) -> Vec<String> {
        idx.iter()
            .map(|&i| entries[i as usize].name.clone())
            .collect()
    }

    #[test]
    fn folders_first_and_natural_order_with_spanish_letters() {
        let entries = vec![
            file("img10.jpg"),
            file("Ñu.txt"),
            dir("zeta"),
            file("img2.jpg"),
            file("Árbol.md"),
            file("nube"),
        ];
        let got = names(
            &entries,
            &arrange(entries.clone(), NAME_ASC, String::new(), false),
        );
        assert_eq!(
            got,
            [
                "zeta",
                "Árbol.md",
                "img2.jpg",
                "img10.jpg",
                "nube",
                "Ñu.txt"
            ]
        );
    }

    #[test]
    fn descending_keeps_folders_first() {
        let entries = vec![file("a"), dir("d"), file("b")];
        let spec = SortSpec {
            descending: true,
            ..NAME_ASC
        };
        assert_eq!(
            names(
                &entries,
                &arrange(entries.clone(), spec, String::new(), false)
            ),
            ["d", "b", "a"]
        );
    }

    #[test]
    fn hidden_and_quick_filter() {
        let entries = vec![
            file(".nomedia"),
            file("Canción.mp3"),
            file("foto.jpg"),
            dir("Android"),
        ];
        // Aquí solo prueba el filtro de ocultos; la app ni lista `Android/`.
        let mut entries = entries;
        entries[3].is_hidden = true;
        assert_eq!(
            names(
                &entries,
                &arrange(entries.clone(), NAME_ASC, String::new(), false)
            ),
            ["Canción.mp3", "foto.jpg"]
        );
        assert_eq!(
            names(
                &entries,
                &arrange(entries.clone(), NAME_ASC, "cancion".into(), false)
            ),
            ["Canción.mp3"]
        );
        assert_eq!(
            arrange(entries.clone(), NAME_ASC, String::new(), true).len(),
            4
        );
    }

    #[test]
    fn sort_by_size_and_modified() {
        let mut a = file("a");
        a.size = 30;
        a.modified_ms = Some(3_000);
        let mut b = file("b");
        b.size = 10;
        b.modified_ms = None; // sin fecha = la más antigua
        let mut c = file("c");
        c.size = 20;
        c.modified_ms = Some(1_000);
        let entries = vec![a, b, c];
        let by_size = SortSpec {
            key: SortKey::Size,
            descending: false,
        };
        assert_eq!(
            names(
                &entries,
                &arrange(entries.clone(), by_size, String::new(), false)
            ),
            ["b", "c", "a"]
        );
        let newest = SortSpec {
            key: SortKey::Modified,
            descending: true,
        };
        assert_eq!(
            names(
                &entries,
                &arrange(entries.clone(), newest, String::new(), false)
            ),
            ["a", "c", "b"]
        );
    }

    #[test]
    fn arranges_ten_thousand_entries_in_one_call() {
        let entries: Vec<FileEntry> = (0..10_000)
            .map(|i| {
                if i % 10 == 0 {
                    dir(&format!("carpeta {i}"))
                } else {
                    file(&format!("IMG_{i}.jpg"))
                }
            })
            .collect();
        let start = Instant::now();
        let idx = arrange(entries.clone(), NAME_ASC, String::new(), false);
        let elapsed = start.elapsed();
        assert_eq!(idx.len(), 10_000);
        assert!(entries[idx[0] as usize].is_dir && !entries[idx[1_000] as usize].is_dir);
        assert_eq!(entries[idx[1_000] as usize].name, "IMG_1.jpg");
        // Holgado para debug y CI; en release ronda unos ms.
        assert!(elapsed.as_millis() < 1_000, "arrange tardó {elapsed:?}");
        println!("arrange 10 000 entradas: {elapsed:?}");
    }

    #[test]
    fn age_buckets_and_units() {
        const H: i64 = 3_600_000;
        const D: i64 = 24 * H;
        let now = 400 * D;
        let a = age(now - 2 * H, now);
        assert_eq!(
            (a.bucket, a.unit, a.amount),
            (AgeBucket::Hour, AgeUnit::Hours, 2)
        );
        let a = age(now - 3 * D, now);
        assert_eq!(
            (a.bucket, a.unit, a.amount),
            (AgeBucket::Day, AgeUnit::Days, 3)
        );
        let a = age(now - 12 * D, now);
        assert_eq!(
            (a.bucket, a.unit, a.amount),
            (AgeBucket::Week, AgeUnit::Days, 12)
        );
        let a = age(now - 95 * D, now);
        assert_eq!(
            (a.bucket, a.unit, a.amount),
            (AgeBucket::Month, AgeUnit::Months, 3)
        );
        let a = age(0, now);
        assert_eq!(
            (a.bucket, a.unit, a.amount),
            (AgeBucket::Year, AgeUnit::Years, 1)
        );
        // Fecha futura (reloj desfasado) = ahora.
        assert_eq!(age(now + H, now).unit, AgeUnit::Now);
    }

    #[test]
    fn filter_keys_follow_adr_006() {
        let ch = |c: &str| FilterKey::Char { text: c.into() };
        assert_eq!(apply_filter_key("".into(), ch("c")).as_deref(), Some("c"));
        // Sin filtro, Espacio, Backspace y Esc siguen a su acción normal.
        assert_eq!(apply_filter_key("".into(), ch(" ")), None);
        assert_eq!(apply_filter_key("".into(), FilterKey::Backspace), None);
        assert_eq!(apply_filter_key("".into(), FilterKey::Escape), None);
        assert_eq!(
            apply_filter_key("can".into(), ch(" ")).as_deref(),
            Some("can ")
        );
        assert_eq!(
            apply_filter_key("can".into(), FilterKey::Backspace).as_deref(),
            Some("ca")
        );
        assert_eq!(
            apply_filter_key("can".into(), FilterKey::Escape).as_deref(),
            Some("")
        );
        assert_eq!(apply_filter_key("".into(), ch("ab")), None);
    }

    #[test]
    fn negative_millis_do_not_panic() {
        assert_eq!(system_time(-1_000), UNIX_EPOCH - Duration::from_secs(1));
        assert_eq!(age(-1_000, 0).unit, AgeUnit::Now);
    }
}
