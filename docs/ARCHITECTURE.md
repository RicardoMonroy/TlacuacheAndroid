# Arquitectura — Tlacuache para Android

## 1. Estructura del repositorio

```
TlacuacheAndroid/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml        # catálogo de versiones (verificar vigentes antes de fijar)
├── gradlew, gradle/wrapper/
├── rust/
│   ├── Cargo.toml                   # workspace: tlacuache-ffi
│   └── tlacuache-ffi/
│       ├── Cargo.toml               # tlacuache-core = { git = ".../Tlacuache", tag = "vX.Y.Z" }
│       ├── src/lib.rs               # registros, funciones y objetos UniFFI (§3)
│       └── src/bin/uniffi-bindgen.rs
├── scripts/
│   ├── build-rust.sh                # cargo-ndk por ABI + bindings Kotlin
│   └── check.sh                     # fmt/clippy de la ffi + lint + ktlint + tests
├── app/
│   ├── build.gradle.kts             # tarea buildRust antes de preBuild
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── res/                 # values/strings.xml (es), xml/file_paths.xml, íconos
│       │   └── kotlin/io/github/ricardomonroy/tlacuache/
│       │       ├── TlacuacheApp.kt      # Application: carga config, catálogo de temas, singletons
│       │       ├── MainActivity.kt      # una actividad por ventana; edge-to-edge
│       │       ├── core/                # CoreBridge: envoltorios Kotlin sobre los bindings generados
│       │       ├── storage/
│       │       │   ├── StorageAccess.kt # acceso a archivos según API
│       │       │   ├── Volumes.kt       # StorageManager + StatFs + avisos de montaje
│       │       │   ├── FavoritesStore.kt # [[favorites]] de config.toml (core) con escritura atómica
│       │       │   ├── Places.kt        # carpetas estándar
│       │       │   ├── Listing.kt       # lectura de carpetas en IO, por lotes
│       │       │   ├── FolderWatcher.kt # FileObserver + debounce
│       │       │   ├── Protected.kt     # carpetas protegidas (ADR-A06)
│       │       │   └── MediaScan.kt     # aviso a MediaStore
│       │       ├── ops/
│       │       │   ├── OpsRepository.kt # cola (core::ops) compartida por todas las ventanas
│       │       │   ├── OpsService.kt    # servicio en primer plano + notificación
│       │       │   ├── OpsRunner.kt     # E/S: copiar, mover, papelera, borrar, renombrar
│       │       │   ├── TrashStore.kt    # papelera propia (ADR-A05)
│       │       │   └── ClipboardStore.kt# portapapeles interno de archivos
│       │       ├── intents/             # abrir, abrir con, compartir, FileProvider, arrastre externo
│       │       ├── state/               # WindowViewModel, PaneState, TabState, sesión
│       │       └── ui/
│       │           ├── theme/           # TlacuacheTheme, tokens, tipografías
│       │           ├── window/          # AdaptiveScaffold: elige layout por clase de ancho
│       │           ├── sidebar/
│       │           ├── pane/            # pestañas, ruta, lista, íconos, barra de estado
│       │           ├── ops/             # indicador, detalle, diálogo de conflictos
│       │           ├── preview/         # v0.2
│       │           ├── dialogs/
│       │           └── input/           # puntero (táctil/ratón), teclado, atajos, arrastre
│       ├── test/                    # pruebas JVM
│       └── androidTest/             # pruebas instrumentadas
└── docs/
```

Lo generado (`.so` por ABI y bindings Kotlin) va a `app/build/` y no se versiona.

## 2. Compilación

- `scripts/build-rust.sh [debug|release] [abis…]`:
  1. `cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o app/build/rustJniLibs build -p tlacuache-ffi` (`--release` en release).
  2. `cargo build -p tlacuache-ffi` para el host y `cargo run --features cli --bin uniffi-bindgen -- generate --library rust/target/debug/libtlacuache_ffi.so --language kotlin --out-dir app/build/generated/uniffi` (binario propio de la crate, misma versión de UniFFI). Se usa la librería del host porque las de Android en release van sin símbolos (`strip = true`) y sin ellos no hay metadatos de UniFFI.
- `app/build.gradle.kts` registra por variante una tarea `buildRust<Variante>` (`BuildRustTask`, que llama al script con el perfil `debug` o `release` y el NDK fijado en `ndkVersion`) y la conecta con `variant.sources.jniLibs/kotlin.addGeneratedSourceDirectory`: Gradle decide las carpetas de salida (`app/build/generated/…`) y la ejecuta antes de compilar. Sin argumentos, el script deja todo en `app/build/rustJniLibs` y `app/build/generated/uniffi` (uso manual).
- La propiedad `-Ptlacuache.abis=arm64-v8a` limita las ABI para iterar rápido; las mismas ABI van a `ndk.abiFilters`, porque JNA trae `libjnidispatch.so` para más arquitecturas (x86, mips…) donde el core no existe.
- El paquete Kotlin generado es `io.github.ricardomonroy.tlacuache.ffi` (`rust/tlacuache-ffi/uniffi.toml`).
- NDK 28 alinea los `.so` a 16 KB por defecto (requisito de Play para Android 15+).
- Los bindings de UniFFI usan JNA: dependencia `net.java.dev.jna:jna` (AAR) y reglas de R8 para conservar JNA y el paquete generado.
- Pruebas JVM: `cargo build -p tlacuache-ffi` para el host (x86_64-linux-gnu) y la tarea `test` pasa `jna.library.path` hacia ese `target/`.
- Desarrollo local del core sin publicar etiqueta: en `rust/Cargo.toml`, `[patch."https://github.com/RicardoMonroy/Tlacuache"] tlacuache-core = { path = "../../TlacuacheBrowser/crates/tlacuache-core" }`, solo en la copia local y nunca en un commit.

## 3. Puente con el core (`tlacuache-ffi`)

Expone **solo lo que la app usa**, con registros propios (UniFFI no puede anotar tipos de otra crate) y `From` hacia/desde los tipos del core. `tlacuache-core` no se modifica para Android salvo mejoras que también valgan en Linux (ADR-A02).

| Módulo del core | Uso en Android | Forma en la FFI |
|---|---|---|
| `sort`, `filter`, `text` | Orden natural con `ñ`/acentos, filtro rápido | `arrange(entries, sort, query, show_hidden) -> Vec<u32>` (índices; una llamada por listado, ADR-A03); `apply_filter_key` |
| `entry`, `age`, `summary` | Modelo, chips de edad, barra de estado | Registros `FileEntry`, `Age`, `SelectionSummary`; `age(modified_ms, now_ms)` |
| `history` | Atrás/adelante por pestaña | Objeto `PathHistory` (con `Mutex` interno) |
| `ops` (`queue`, `conflict`, `undo`) | Cola, estados, progreso, conflictos, deshacer | Objetos `OpQueue` (instantánea con `operations()`; `complete(id, journal)` guarda el deshacer de `undo_request`) y `ConflictPolicy`; registros `OpRequest`, `Operation`, `Progress`, `Journal`; `undo_request(kind, journal)`, `replace_allowed`, `newer` |
| `names`, `bulk_rename` | Validar nombres, «Conservar ambos», renombrado masivo | `validate_name` (devuelve el error como valor), `stem_len`, `next_free_name(name, is_dir, existing)`, `plan_rename(rule, items)` |
| `filetype` | Categoría para íconos y colores | `classify(name, mime, is_dir)` |
| `theme`, `theme_catalog` | Temas | `theme_catalog(user_dir) -> Vec<ThemeTokens>` (colores ARGB y flags de `[style]`) |
| `config`, `favorites`, `session`, `keymap` | Configuración, favoritos, sesión, atajos | Entrada y salida como texto TOML; la E/S de archivos la hace Kotlin (escritura atómica) |
| `path_input`, `usage`, `preview`, `markdown`, `search`, `notes` | Autocompletado de ruta, nivel de uso, vista previa, búsqueda, notas | Funciones directas; `search` como objeto con `cancel()` y una *callback interface* para lotes |
| `shell`, `clipboard`, `places`, `thumbnail`, `perms`, `folder_watch`, `listing_diff` | — | No se exponen (terminal, Linux o resuelto de otra forma) |

Reglas:
- Los errores del core (`thiserror`) se exponen como enums de error de UniFFI y en Kotlin llegan como excepciones tipadas que `CoreBridge` convierte en resultados.
- Ninguna llamada FFI se hace desde el hilo principal si recorre disco (`search`, `theme_catalog` con temas de usuario).
- La paridad con Linux se cuida desde el core: si un comportamiento debe ser igual en ambas versiones, vive allí con sus pruebas.

## 4. Almacenamiento y permisos (ADR-A04)

- **Android 11+ (API 30+)**: `MANAGE_EXTERNAL_STORAGE`; se comprueba con `Environment.isExternalStorageManager()`. Da acceso con `java.io`/`java.nio` a todo el almacenamiento compartido de todos los volúmenes, excepto `Android/data` y `Android/obb` de otras apps.
- **Android 10 (API 29)**: `READ/WRITE_EXTERNAL_STORAGE` + `android:requestLegacyExternalStorage="true"`. Los volúmenes extraíbles (SD, USB) son de **solo lectura** en Android 10 (escribir exigiría SAF); la UI lo indica.
- **Volúmenes** (`Volumes.kt`): `StorageManager.storageVolumes`; la raíz sale de `StorageVolume.directory` (API 30+) o, en API 29, de `getExternalFilesDirs()` recortando `/Android/data/<paquete>/files`. Uso con `StatFs`. Cambios: `StorageVolumeCallback` (API 30+) o broadcast de `ACTION_MEDIA_MOUNTED`/`UNMOUNTED` (API 29). Nombre visible: «Interno», `StorageVolume.getDescription()` para los demás.
- **Lugares** (`Places.kt`): `Environment.getExternalStoragePublicDirectory(DIRECTORY_*)`; se omiten los que no existen.
- **MediaStore** (`MediaScan.kt`): tras cada operación, `MediaScannerConnection.scanFile` con las rutas viejas y nuevas (archivos, no carpetas enteras). La papelera tiene `.nomedia` para que lo enviado allí desaparezca de la galería.

## 5. Modelo de estado

- `OpsRepository`, `ClipboardStore`, la configuración y el catálogo de temas son **singletons de proceso** (creados en `TlacuacheApp`), compartidos por todas las ventanas.
- Cada ventana (`MainActivity`, una tarea por ventana) tiene un `WindowViewModel`: modo 1/2 paneles, panel activo, visibilidad de la barra lateral, dos `PaneState` (A y B siempre existen, ADR-A08).
- `PaneState`: pestañas y pestaña activa. `TabState`: ruta, `PathHistory` (core), vista, orden, filtro, ocultos, selección (conjunto de nombres), entradas listadas y estado `loading`/`error`.
- La UI observa `StateFlow` y emite intenciones (`navigate`, `select`, `copyToOther`…) al ViewModel. Nada de lógica de negocio en composables.
- `SavedStateHandle` guarda lo mínimo para sobrevivir a la muerte del proceso; la sesión completa (pestañas de ambos paneles, modo, proporción del divisor) se escribe con `core::session` en `filesDir/session.toml` al pasar a segundo plano (`ON_STOP`). Solo la ventana principal restaura sesión en v0.1; la UI espera a leerla (en IO) antes de mostrar los paneles. En Android `uri` guarda la ruta absoluta (la sesión no se comparte con Linux); las pestañas cuya carpeta ya no existe se omiten. Se guardan por pestaña carpeta, vista y ocultos, y por ventana modo dual, proporción del divisor, panel activo y barra lateral reducida; el historial atrás/adelante no. Regla del core: con el modo dual apagado se restaura el panel A como activo.

## 6. Listado de carpetas (ADR-A10)

- `Listing.read(dir)` en `Dispatchers.IO`: `Files.newDirectoryStream` + `Files.readAttributes(BasicFileAttributes)` por entrada (una llamada, no varias de `File`), emitiendo lotes (primer lote en cuanto hay 200 entradas) para que la lista aparezca rápido. Cancelable: al cambiar de carpeta se cancela la corrutina.
- Cada lote se pasa por `arrange()` del core (una llamada) y se publica la lista ordenada y filtrada.
- La `LazyColumn`/`LazyVerticalGrid` usa **el nombre como `key`**: releer la carpeta y publicar la lista nueva conserva desplazamiento y selección sin diff explícito.
- **Resincronización**: `FolderWatcher` (`FileObserver` sobre la carpeta visible) programa una relectura a los 300 ms y junta los eventos que lleguen mientras tanto. También se relee al volver la app a primer plano y con Ctrl+R. Si en algún volumen `FileObserver` no recibe eventos de cambios hechos por otras apps (a verificar en FUSE y USB), se revisa periódicamente mientras la lista está visible, cada 3 a 30 s según lo que tardó la última lectura (mismas reglas que `core::folder_watch` en Linux).
- Ocultos: nombres que empiezan con `.`; se muestran con Ctrl+H. `Android/` en la raíz de un volumen ni siquiera se lista (ADR-A06); con los ocultos visibles tampoco aparece.

## 7. Operaciones de archivo

- `OpsRepository` envuelve `core::ops::OpQueue` (FIFO, una operación activa) y expone `StateFlow<List<Operation>>`. Al encolar, arranca `OpsService`.
- `OpsService`: servicio en primer plano de tipo `dataSync` (ADR-A09) con notificación de progreso y acción Cancelar; mantiene un `PARTIAL_WAKE_LOCK` mientras corre una operación; se detiene al vaciarse la cola. En Android 15+, `onTimeout` pausa la cola de forma segura (termina el archivo en curso o lo revierte) y avisa con una notificación para continuar.
- `OpsRunner` (E/S en Kotlin, modelo en el core):
  - **Copiar archivo**: a `<nombre>.tlacuache-part` en el destino, por bloques de 1 MB con progreso y comprobación de cancelación entre bloques; al terminar se verifica el tamaño, se copia la fecha de modificación y se renombra al nombre final. Si falla o se cancela, se borra el `.part`.
  - **Copiar carpeta**: recorrido recursivo, con total de bytes calculado antes para el progreso.
  - **Mover**: en el mismo volumen, `Files.move(ATOMIC_MOVE)` (renombrado instantáneo); entre volúmenes, copiar todo y borrar los orígenes **solo** si la copia terminó sin errores.
  - **Renombrar / nueva carpeta / nuevo archivo**: validación con `core::names`.
  - **Papelera / Restaurar**: `TrashStore` (§8). **Borrar**: recursivo, solo tras confirmación en la UI.
  - Cada operación devuelve un `Journal` del core; Deshacer usa `core::ops::undo_request`.
  - Fuentes `content://` (arrastre o pegado desde otras apps): se copian por `ContentResolver.openInputStream` con el nombre de `OpenableColumns.DISPLAY_NAME`.
- **Conflictos**: el runner suspende y pide una decisión a la UI por un canal (`CompletableDeferred<ConflictAction>`, expuesto como `OpsRepository.conflict`; cerrar el diálogo cancela la operación); la política «aplicar a todos» vive en `core::ops::ConflictPolicy`. Si no hay ninguna ventana visible, la notificación cambia a «Se necesita tu decisión» y abre la app.
- Antes de ejecutar, cada origen y destino pasa por `Protected.kt`: una operación que toque una carpeta protegida se rechaza con un mensaje claro.

## 8. Papelera propia (ADR-A05)

- Por volumen: `<raíz del volumen>/.Tlacuache/Papelera/` con `files/` y `info/`, y un `.nomedia` en `.Tlacuache/`.
- Enviar a la papelera = renombrar (mismo volumen, instantáneo) a `files/<nombre>` y escribir `info/<nombre>.trashinfo` con el formato de freedesktop:
  ```
  [Trash Info]
  Path=/storage/emulated/0/DCIM/IMG_01.jpg
  DeletionDate=2026-10-27T18:04:11
  ```
  Si `<nombre>` ya existe en la papelera se usa el siguiente libre (`core::names::numbered_names`).
- Restaurar: vuelve a `Path`; si está ocupado, pregunta (conflicto) o restaura con nombre numerado. Si la carpeta original ya no existe, se recrea.
- Purga: al arrancar la app, lo que tenga más de 30 días se borra en segundo plano. «Vaciar papelera» con confirmación.
- En Android 10 los volúmenes extraíbles son de solo lectura: ahí «Papelera» no está disponible y solo se ofrece borrar permanentemente con confirmación (y tampoco eso, si el volumen no es escribible).

## 9. Integración con el sistema (`intents/`)

- `FileProvider` con `external-path` para el almacenamiento interno y `root-path` para los volúmenes extraíbles.
- Abrir: `ACTION_VIEW` con URI del provider, tipo MIME por extensión (`MimeTypeMap`, si no `*/*`) y `FLAG_GRANT_READ_URI_PERMISSION`. «Abrir con…»: `Intent.createChooser`. Sin app capaz: Snackbar.
- Compartir: `ACTION_SEND` / `ACTION_SEND_MULTIPLE`.
- Arrastre hacia otras apps: `ClipData` con URIs del provider y `DRAG_FLAG_GLOBAL | DRAG_FLAG_GLOBAL_URI_READ`. Arrastre interno (entre paneles y ventanas de Tlacuache): `ClipDescription` con un MIME propio (`application/x-tlacuache-paths`) y rutas en texto, para operar con rutas y no con URIs. Arrastre desde otras apps: `requestDragAndDropPermissions` y copia por `ContentResolver`.
- Nueva ventana: `Intent` a `MainActivity` con `FLAG_ACTIVITY_NEW_DOCUMENT | FLAG_ACTIVITY_MULTIPLE_TASK | FLAG_ACTIVITY_LAUNCH_ADJACENT` y la ruta inicial como extra.

## 10. Entrada (`ui/input/`)

- Tipo de puntero por evento (`PointerEvent.changes[].type`) → decide toque-abre vs. clic-selecciona/doble-clic-abre y la densidad automática.
- Clic derecho: `PointerEvent.buttons.isSecondaryPressed` abre un `DropdownMenu` anclado en la posición del puntero.
- Teclado: `onPreviewKeyEvent` en la raíz de la ventana despacha a una tabla de acciones; las teclas se leen de `core::keymap` + `[keys]` de la config. El foco de texto (filtro, ruta, diálogos) tiene prioridad.
- `Activity.onProvideKeyboardShortcuts` publica la tabla para la ayuda del sistema.
- `pointerHoverIcon` para cursores; `Modifier.hoverable` + tooltips (`TooltipBox`).

## 11. Temas (ADR-A07)

- `theme_catalog` del core devuelve los temas incluidos (embebidos en el core) y los del usuario (`filesDir/themes/*.toml`) ya validados.
- La FFI entrega un registro `ThemeTokens` (`colors`, `age`, `filetypes` con cada color como `UInt` ARGB `0xFFRRGGBB`, y `style` con `radius`, `row_height`, `mono_ui`, `icon_style`, `selection_style`, `age_style`, `uppercase_headers`, `bracket_titles`, `glow`…). `TlacuacheTheme(tokens)` lo publica tal cual en un `CompositionLocal` (`TlacuacheTheme.tokens`, sin copiarlo a otra clase Kotlin) y la extensión `UInt.color` lo convierte a `Color` al usarlo.
- También deriva un `ColorScheme` de Material 3 para los componentes estándar (`primary` = `accent`, `background` = `window_bg`, `surface` = `pane_bg`, `surfaceVariant` = `sidebar_bg`, `outline` = `border_strong`, `error` = `error`…), las `Shapes` con `radius` y, si `mono_ui`, toda la tipografía en monoespaciada.
- Barras del sistema transparentes (`enableEdgeToEdge` con `SystemBarStyle` según `variant`); cada pantalla aplica sus `WindowInsets`.
- Fuente monoespaciada: JetBrains Mono empaquetada (OFL); `font_ui` vacío = fuente del sistema.
- `[terminal]` y `[decor]` se ignoran en v0.1.

## 12. Configuración (ADR-A11)

- `filesDir/config.toml`, leído y escrito con `core::config` (mismas claves que Linux: `[general]`, `[preview]`, `[theme]`, `[[favorites]]`, `[keys]`; `[terminal]` se ignora). Si el TOML es inválido no se sobrescribe: defaults + aviso.
- Preferencias propias de Android (densidad, layout por clase, recordar el panel A/B) en `SharedPreferences`.
- Exportar/importar `config.toml` desde Preferencias.

## 13. Errores y registro

- `Log` de Android con etiqueta `Tlacuache` a través de un envoltorio mínimo; sin analítica ni envío de reportes.
- Errores visibles: `Snackbar` (no bloqueante) o `AlertDialog` (decisiones). Mensajes en español, en segunda persona, sin humor (`BRAND.md`).

## 14. Pruebas

- `tlacuache-core`: sus pruebas siguen en el repo Linux.
- `tlacuache-ffi`: pruebas de las conversiones y de `arrange` (host).
- JVM (`app/src/test`): `OpsRunner` y `TrashStore` sobre carpetas temporales, `Protected`, reglas de layout por ancho, despacho de atajos; el core se carga desde la build de host.
- Instrumentadas (`androidTest`): permisos, volúmenes, `FileProvider`, servicio de operaciones.
- UI: checklist de prueba manual por hito en `ROADMAP.md`, con el dispositivo indicado.
