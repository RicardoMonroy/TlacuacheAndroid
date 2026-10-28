//! Modelo de operaciones de archivo (`core::ops`): cola, conflictos y
//! deshacer. La E/S la hace el runner en Kotlin y va informando a la cola
//! (ARCHITECTURE §7). Las ubicaciones son rutas absolutas en texto.

use std::fmt;
use std::sync::{Mutex, MutexGuard};

use tlacuache_core::ops as core;

use crate::listing::system_time;

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum OpKind {
    Copy,
    Move,
    Trash,
    /// Borrado permanente (requiere confirmación en la UI).
    Delete,
    Mkdir,
    CreateFile,
    Rename,
    RenameMany,
    /// Devolver cada origen a su ruta exacta en `targets` (deshacer mover).
    Restore,
    /// Sacar de la papelera (deshacer enviar a la papelera).
    Untrash,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct OpRequest {
    pub kind: OpKind,
    pub sources: Vec<String>,
    pub dest: Option<String>,
    /// Destino exacto de cada origen (`Restore`, `RenameMany`).
    pub targets: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Enum)]
pub enum OpState {
    Queued,
    Running,
    /// Se pidió cancelar; el runner aún no confirma que se detuvo.
    Cancelling,
    Done,
    Failed {
        message: String,
    },
    Cancelled,
}

#[derive(Debug, Clone, Default, PartialEq, Eq, uniffi::Record)]
pub struct Progress {
    pub bytes_done: u64,
    pub bytes_total: Option<u64>,
    pub files_done: u64,
    pub files_total: Option<u64>,
    /// Elemento en curso, para mostrarlo.
    pub current: Option<String>,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct Operation {
    pub id: u64,
    pub kind: OpKind,
    pub sources: Vec<String>,
    pub dest: Option<String>,
    pub targets: Vec<String>,
    pub state: OpState,
    pub progress: Progress,
    /// Fracción hecha (0–1) por bytes o, si no, por archivos; `None` sin totales.
    pub fraction: Option<f64>,
    /// Terminó y se puede deshacer (una vez, con [`OpQueue::take_undo`]).
    pub can_undo: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct MovedItem {
    pub from: String,
    /// Destino final; puede ser «x (2)» por un conflicto.
    pub to: String,
}

/// Lo que hizo realmente una operación terminada; de aquí sale cómo deshacerla.
#[derive(Debug, Clone, Default, PartialEq, Eq, uniffi::Record)]
pub struct Journal {
    pub pairs: Vec<MovedItem>,
    /// Se combinaron carpetas: no se puede revertir con seguridad.
    pub merged: bool,
    pub trashed: Vec<String>,
    pub created: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Error)]
pub enum OpsError {
    NotFound {
        id: u64,
    },
    InvalidTransition {
        id: u64,
        event: String,
        state: String,
    },
}

impl fmt::Display for OpsError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::NotFound { id } => write!(f, "no existe la operación {id}"),
            Self::InvalidTransition { id, event, state } => {
                write!(
                    f,
                    "la operación {id} no admite «{event}» en el estado {state}"
                )
            }
        }
    }
}

impl std::error::Error for OpsError {}

impl From<core::OpsError> for OpsError {
    fn from(e: core::OpsError) -> Self {
        match e {
            core::OpsError::NotFound(id) => Self::NotFound { id },
            core::OpsError::InvalidTransition { id, event, state } => Self::InvalidTransition {
                id,
                event: event.to_owned(),
                state: format!("{state:?}"),
            },
        }
    }
}

/// Cola FIFO de operaciones con límite de concurrencia (`core::ops::OpQueue`).
#[derive(uniffi::Object)]
pub struct OpQueue(Mutex<core::OpQueue>);

#[uniffi::export]
impl OpQueue {
    #[uniffi::constructor]
    pub fn new(max_concurrent: u32) -> Self {
        Self(Mutex::new(core::OpQueue::new(max_concurrent as usize)))
    }

    pub fn enqueue(&self, request: OpRequest) -> u64 {
        self.lock().enqueue_request(request.into())
    }

    /// Todas, en orden de llegada (una llamada para pintar la lista).
    pub fn operations(&self) -> Vec<Operation> {
        self.lock().iter().map(Operation::from).collect()
    }

    pub fn get(&self, id: u64) -> Option<Operation> {
        self.lock().get(id).map(Operation::from)
    }

    /// Sin operaciones en cola ni en curso.
    pub fn is_idle(&self) -> bool {
        self.lock().is_idle()
    }

    /// Pasa a `Running` las siguientes mientras haya huecos; el runner ejecuta estas.
    pub fn start_next(&self) -> Vec<u64> {
        self.lock().start_next()
    }

    pub fn update_progress(&self, id: u64, progress: Progress) -> Result<(), OpsError> {
        Ok(self.lock().update_progress(id, progress.into())?)
    }

    /// Termina con éxito; el deshacer sale de [`undo_request`] con lo que registró el runner.
    pub fn complete(&self, id: u64, journal: Journal) -> Result<(), OpsError> {
        let mut queue = self.lock();
        let kind = queue.get(id).ok_or(OpsError::NotFound { id })?.kind;
        let undo = core::undo_request(kind, &journal.into());
        Ok(queue.complete_with_undo(id, undo)?)
    }

    /// Entrega una sola vez la operación que deshace `id`.
    pub fn take_undo(&self, id: u64) -> Option<OpRequest> {
        self.lock().take_undo(id).map(OpRequest::from)
    }

    pub fn fail(&self, id: u64, message: String) -> Result<(), OpsError> {
        Ok(self.lock().fail(id, message)?)
    }

    /// En cola se cancela al instante; en curso devuelve `true` para que el runner aborte.
    pub fn cancel(&self, id: u64) -> Result<bool, OpsError> {
        Ok(self.lock().cancel(id)?)
    }

    /// El runner confirma que se detuvo tras cancelar.
    pub fn cancelled(&self, id: u64) -> Result<(), OpsError> {
        Ok(self.lock().cancelled(id)?)
    }

    pub fn clear_finished(&self) {
        self.lock().clear_finished();
    }
}

impl OpQueue {
    fn lock(&self) -> MutexGuard<'_, core::OpQueue> {
        self.0.lock().unwrap_or_else(|e| e.into_inner())
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum ConflictAction {
    /// Sobrescribir; con carpetas, combinar su contenido.
    Replace,
    Skip,
    /// Con un nombre libre («foto (2).jpg»).
    KeepBoth,
}

/// Recuerda «aplicar a todos» durante una operación (`core::ops::ConflictPolicy`).
#[derive(uniffi::Object, Default)]
pub struct ConflictPolicy(Mutex<core::ConflictPolicy>);

#[uniffi::export]
impl ConflictPolicy {
    #[uniffi::constructor]
    pub fn new() -> Self {
        Self::default()
    }

    /// Decisión ya tomada para todos, si vale para este conflicto; reemplazar no se
    /// reutiliza donde no está permitido.
    pub fn remembered(&self, replace_allowed: bool) -> Option<ConflictAction> {
        self.lock()
            .remembered(replace_allowed)
            .map(ConflictAction::from)
    }

    pub fn remember(&self, action: ConflictAction) {
        self.lock().remember(action.into());
    }
}

impl ConflictPolicy {
    fn lock(&self) -> MutexGuard<'_, core::ConflictPolicy> {
        self.0.lock().unwrap_or_else(|e| e.into_inner())
    }
}

/// Velocidad y tiempo restante tras una muestra; `None` mientras no se sabe.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Record)]
pub struct Rate {
    pub bytes_per_sec: Option<u64>,
    pub eta_secs: Option<u64>,
}

/// Velocidad de una operación en curso (`core::ops::RateMeter`); una por operación.
#[derive(uniffi::Object, Default)]
pub struct RateMeter(Mutex<core::RateMeter>);

#[uniffi::export]
impl RateMeter {
    #[uniffi::constructor]
    pub fn new() -> Self {
        Self::default()
    }

    /// Añade una muestra (reloj monótono en ms) y devuelve la lectura: una llamada por
    /// aviso de progreso.
    pub fn sample(&self, now_ms: u64, bytes_done: u64, bytes_total: Option<u64>) -> Rate {
        let mut m = self.0.lock().unwrap_or_else(|e| e.into_inner());
        m.sample(now_ms, bytes_done);
        Rate {
            bytes_per_sec: m.bytes_per_sec(),
            eta_secs: bytes_total.and_then(|t| m.eta_secs(t)),
        }
    }
}

/// Reemplazar solo entre elementos del mismo tipo que no sean el mismo elemento.
#[uniffi::export]
pub fn replace_allowed(source_is_dir: bool, dest_is_dir: bool, same_item: bool) -> bool {
    core::replace_allowed(source_is_dir, dest_is_dir, same_item)
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum Newer {
    Existing,
    Incoming,
    Same,
}

/// Cuál de las dos versiones en conflicto es más reciente (fechas en ms); `None` si falta alguna.
#[uniffi::export]
pub fn newer(existing_ms: Option<i64>, incoming_ms: Option<i64>) -> Option<Newer> {
    core::newer(existing_ms.map(system_time), incoming_ms.map(system_time)).map(|n| match n {
        core::Newer::Existing => Newer::Existing,
        core::Newer::Incoming => Newer::Incoming,
        core::Newer::Same => Newer::Same,
    })
}

/// Operación que deshace `kind` según `journal`, o `None` si no es reversible.
#[uniffi::export]
pub fn undo_request(kind: OpKind, journal: Journal) -> Option<OpRequest> {
    core::undo_request(kind.into(), &journal.into()).map(OpRequest::from)
}

// Conversiones con el core.

impl From<OpKind> for core::OpKind {
    fn from(k: OpKind) -> Self {
        match k {
            OpKind::Copy => Self::Copy,
            OpKind::Move => Self::Move,
            OpKind::Trash => Self::Trash,
            OpKind::Delete => Self::Delete,
            OpKind::Mkdir => Self::Mkdir,
            OpKind::CreateFile => Self::CreateFile,
            OpKind::Rename => Self::Rename,
            OpKind::RenameMany => Self::RenameMany,
            OpKind::Restore => Self::Restore,
            OpKind::Untrash => Self::Untrash,
        }
    }
}

impl From<core::OpKind> for OpKind {
    fn from(k: core::OpKind) -> Self {
        match k {
            core::OpKind::Copy => Self::Copy,
            core::OpKind::Move => Self::Move,
            core::OpKind::Trash => Self::Trash,
            core::OpKind::Delete => Self::Delete,
            core::OpKind::Mkdir => Self::Mkdir,
            core::OpKind::CreateFile => Self::CreateFile,
            core::OpKind::Rename => Self::Rename,
            core::OpKind::RenameMany => Self::RenameMany,
            core::OpKind::Restore => Self::Restore,
            core::OpKind::Untrash => Self::Untrash,
        }
    }
}

impl From<OpRequest> for core::OpRequest {
    fn from(r: OpRequest) -> Self {
        Self {
            kind: r.kind.into(),
            sources: r.sources,
            dest: r.dest,
            targets: r.targets,
        }
    }
}

impl From<core::OpRequest> for OpRequest {
    fn from(r: core::OpRequest) -> Self {
        Self {
            kind: r.kind.into(),
            sources: r.sources,
            dest: r.dest,
            targets: r.targets,
        }
    }
}

impl From<Progress> for core::Progress {
    fn from(p: Progress) -> Self {
        Self {
            bytes_done: p.bytes_done,
            bytes_total: p.bytes_total,
            files_done: p.files_done,
            files_total: p.files_total,
            current: p.current,
        }
    }
}

impl From<&core::Operation> for Operation {
    fn from(op: &core::Operation) -> Self {
        Self {
            id: op.id,
            kind: op.kind.into(),
            sources: op.sources.clone(),
            dest: op.dest.clone(),
            targets: op.targets.clone(),
            state: match &op.state {
                core::OpState::Queued => OpState::Queued,
                core::OpState::Running => OpState::Running,
                core::OpState::Cancelling => OpState::Cancelling,
                core::OpState::Done => OpState::Done,
                core::OpState::Failed(message) => OpState::Failed {
                    message: message.clone(),
                },
                core::OpState::Cancelled => OpState::Cancelled,
            },
            progress: Progress {
                bytes_done: op.progress.bytes_done,
                bytes_total: op.progress.bytes_total,
                files_done: op.progress.files_done,
                files_total: op.progress.files_total,
                current: op.progress.current.clone(),
            },
            fraction: op.progress.fraction(),
            can_undo: op.undo.is_some(),
        }
    }
}

impl From<Journal> for core::Journal {
    fn from(j: Journal) -> Self {
        Self {
            pairs: j.pairs.into_iter().map(|p| (p.from, p.to)).collect(),
            merged: j.merged,
            trashed: j.trashed,
            created: j.created,
        }
    }
}

impl From<ConflictAction> for core::ConflictAction {
    fn from(a: ConflictAction) -> Self {
        match a {
            ConflictAction::Replace => Self::Replace,
            ConflictAction::Skip => Self::Skip,
            ConflictAction::KeepBoth => Self::KeepBoth,
        }
    }
}

impl From<core::ConflictAction> for ConflictAction {
    fn from(a: core::ConflictAction) -> Self {
        match a {
            core::ConflictAction::Replace => Self::Replace,
            core::ConflictAction::Skip => Self::Skip,
            core::ConflictAction::KeepBoth => Self::KeepBoth,
        }
    }
}

#[cfg(test)]
mod tests {
    #[test]
    fn rate_meter_reads_after_a_second() {
        let m = RateMeter::new();
        m.sample(0, 0, Some(10_000_000));
        let r = m.sample(1_000, 2_000_000, Some(10_000_000));
        assert_eq!(r.bytes_per_sec, Some(2_000_000));
        assert_eq!(r.eta_secs, Some(4));
        assert_eq!(m.sample(1_500, 3_000_000, None).eta_secs, None);
    }

    use super::*;

    fn request(kind: OpKind, sources: &[&str], dest: Option<&str>) -> OpRequest {
        OpRequest {
            kind,
            sources: sources.iter().map(|s| (*s).to_owned()).collect(),
            dest: dest.map(str::to_owned),
            targets: Vec::new(),
        }
    }

    #[test]
    fn queue_runs_one_at_a_time_and_moves_can_be_undone() {
        let queue = OpQueue::new(1);
        let a = queue.enqueue(request(OpKind::Move, &["/i/a.jpg"], Some("/sd/Fotos")));
        let b = queue.enqueue(request(OpKind::Copy, &["/i/b.jpg"], Some("/sd/Fotos")));
        assert_eq!(queue.start_next(), [a]);
        assert!(queue.start_next().is_empty());

        let progress = Progress {
            bytes_done: 50,
            bytes_total: Some(200),
            ..Progress::default()
        };
        queue.update_progress(a, progress).unwrap();
        assert_eq!(queue.get(a).unwrap().fraction, Some(0.25));

        let journal = Journal {
            pairs: vec![MovedItem {
                from: "/i/a.jpg".into(),
                to: "/sd/Fotos/a (2).jpg".into(),
            }],
            ..Journal::default()
        };
        queue.complete(a, journal).unwrap();
        assert!(queue.get(a).unwrap().can_undo);
        let undo = queue.take_undo(a).unwrap();
        assert_eq!(undo.kind, OpKind::Restore);
        assert_eq!(undo.sources, ["/sd/Fotos/a (2).jpg"]);
        assert_eq!(undo.targets, ["/i/a.jpg"]);
        assert_eq!(queue.take_undo(a), None);

        assert_eq!(queue.start_next(), [b]);
        assert!(!queue.is_idle());
    }

    #[test]
    fn cancel_and_invalid_transitions() {
        let queue = OpQueue::new(1);
        let a = queue.enqueue(request(OpKind::Copy, &["/x"], Some("/y")));
        let b = queue.enqueue(request(OpKind::Copy, &["/x"], Some("/z")));
        queue.start_next();
        assert_eq!(queue.cancel(a), Ok(true));
        assert_eq!(queue.cancel(b), Ok(false));
        assert_eq!(queue.get(b).unwrap().state, OpState::Cancelled);
        queue.cancelled(a).unwrap();
        assert!(matches!(
            queue.complete(a, Journal::default()),
            Err(OpsError::InvalidTransition { .. })
        ));
        assert_eq!(
            queue.fail(99, "x".into()),
            Err(OpsError::NotFound { id: 99 })
        );
        queue.clear_finished();
        assert!(queue.operations().is_empty());
    }

    #[test]
    fn copies_undo_to_trash_and_merges_are_final() {
        let journal = Journal {
            pairs: vec![MovedItem {
                from: "/a".into(),
                to: "/b/a".into(),
            }],
            ..Journal::default()
        };
        let undo = undo_request(OpKind::Copy, journal.clone()).unwrap();
        assert_eq!(
            (undo.kind, undo.sources),
            (OpKind::Trash, vec!["/b/a".to_owned()])
        );
        assert_eq!(
            undo_request(
                OpKind::Copy,
                Journal {
                    merged: true,
                    ..journal
                }
            ),
            None
        );
        assert_eq!(undo_request(OpKind::Delete, Journal::default()), None);
    }

    #[test]
    fn conflict_policy_does_not_reuse_replace_where_not_allowed() {
        let policy = ConflictPolicy::new();
        assert_eq!(policy.remembered(true), None);
        policy.remember(ConflictAction::Replace);
        assert_eq!(policy.remembered(true), Some(ConflictAction::Replace));
        assert_eq!(policy.remembered(replace_allowed(true, false, false)), None);
        assert_eq!(newer(Some(10_000), Some(20_000)), Some(Newer::Incoming));
        assert_eq!(newer(Some(10_000), Some(10_500)), Some(Newer::Same));
        assert_eq!(newer(None, Some(1)), None);
    }
}
