# Roadmap — Tlacuache para Android

Trabajar en orden. Cada tarea = un commit. Marcar `[x]` al cumplir el criterio de aceptación (CA). Dispositivos: **T** = tablet Android 11, **P** = Pixel 10 (teléfono), **PE** = Pixel 10 en modo escritorio con teclado y ratón, **E10** = emulador Android 10.

## Hito A0 — Esqueleto
- [x] **A0.1 Proyecto Gradle**: wrapper, Kotlin DSL, catálogo de versiones (vigentes y compatibles), módulo `app` con Compose y Material 3, `applicationId` y `namespace` según ADR-A12, `minSdk 29`/`targetSdk 36`. Licencia GPL, `README.md`, `.gitignore`, `git init`. CA: `./gradlew assembleDebug` genera el APK.
- [x] **A0.2 Crate `tlacuache-ffi`**: workspace en `rust/`, dependencia `tlacuache-core` por git a la etiqueta `v0.2.0`, una función de prueba (`core_version()`) y el binario `uniffi-bindgen`. Instalar `cargo-ndk`. CA: `cargo test` de la crate en verde y `cargo ndk` compila para las tres ABI.
- [x] **A0.3 Integración Gradle ↔ Rust**: `scripts/build-rust.sh`, tarea `buildRust` antes de `preBuild`, `jniLibs` y bindings generados en `sourceSets`, JNA y reglas de R8. CA: la app instalada muestra el texto devuelto por `core_version()`; el APK release (sin firmar) también.
- [ ] **A0.4 Arranque en dispositivos**: comprobar la ABI de **T** (`adb shell getprop ro.product.cpu.abi`), instalar en **T** y **P**; crear el emulador **E10** (instalar imagen de sistema API 29). CA: la app abre en los tres.
  - Avance (2026-10-27): **P** (Android 17, arm64-v8a) y **E10** (AVD `Tlacuache_API29`, x86_64) muestran el texto del core. Falta **T**: se prueba cuando el autor la conecte.
- [x] **A0.5 Tema base**: `TlacuacheTheme` con los tokens de `nord` desde `core::theme_catalog`, edge-to-edge, JetBrains Mono empaquetada. CA: fondo y acentos Nord visibles; ningún color en el código (grep).
- [x] **A0.6 Calidad**: ktlint, Android Lint con advertencias como error, `scripts/check.sh` (fmt/clippy de la ffi + lint + ktlint + test), pruebas JVM que cargan el core de host. CA: script en verde.

## Hito A1 — Un panel que navega
- [ ] **A1.1 Permisos**: pantalla de bienvenida, `MANAGE_EXTERNAL_STORAGE` (30+) y almacenamiento heredado (29); volver a ella si se revoca. CA: probado en **T**, **P** y **E10**, incluido revocar desde Ajustes.
  - Avance (2026-10-27): **P** (conceder en Ajustes, volver, revocar → bienvenida) y **E10** (denegar, permitir, Abrir Ajustes, revocar) correctos. Falta **T**. En E10, tras conceder fuera de la app (`pm grant`) la primera lectura de la raíz devolvió `null`: A1.3 debe releer al volver a primer plano y mostrar error recuperable, no lista vacía.
- [x] **A1.2 FFI de listado**: registros `FileEntry`/`Age`, `arrange()` y `age()` con pruebas en la crate. CA: `arrange` ordena 10 000 entradas en una llamada (prueba con tiempo medido).
- [ ] **A1.3 Lista detallada**: `Listing` por lotes en IO + `arrange` + `LazyColumn` con `key` = nombre; columnas según ancho; chip de edad. CA: abre una carpeta de 10 000 archivos en **T** sin congelar la UI (generar con `adb shell`).
- [ ] **A1.4 Navegación**: tocar/Enter entra, ↑/Backspace sube, historial con `PathHistory`, gesto atrás según UI_SPEC §3. CA: prueba JVM del orden del gesto atrás + prueba manual en **P**.
- [ ] **A1.5 Barra de ruta**: breadcrumb desde el volumen («Interno › DCIM»), modo editable (Ctrl+L o toque en zona vacía) con autocompletado del core. CA: navegar escribiendo una ruta en **PE**.
- [ ] **A1.6 Abrir archivos**: `FileProvider`, Abrir, Abrir con…, Compartir. CA: abre una foto, un PDF y un video con sus apps en **T**.
- [ ] **A1.7 Filtro rápido y ocultos**: escribir con teclado físico, botón de buscar en táctil, Esc limpia, Ctrl+H; `Android/` oculta (ADR-A06). CA: `cancion` encuentra `Canción` en **PE**.
- [ ] **A1.8 Resincronización**: `FolderWatcher` con debounce, relectura al volver a primer plano, Ctrl+R. Verificar si llegan eventos de cambios hechos por otra app en interno, SD y USB; si no, revisión periódica. CA: una foto tomada con la cámara aparece sola en `DCIM/Camera`; conclusión anotada en ADR-A10.

## Hito A2 — Pestañas, doble panel, layouts y barra lateral
- [ ] **A2.1 Estado de ventana**: `WindowViewModel` con paneles A y B, panel activo, pestañas por panel (Ctrl+T, Ctrl+W, Ctrl+Tab). CA: rotar la pantalla y redimensionar la ventana en **PE** no pierde pestañas ni selección.
- [ ] **A2.2 Layouts adaptativos**: compacta (1 panel + A/B), media y expandida (2 paneles con divisor arrastrable), F3, Tab y acento del panel activo. CA: en **PE**, al estrechar la ventana pasa de 2 paneles a A/B y vuelve, sin perder estado; en **T** vertical y horizontal se ve según UI_SPEC §1.
- [ ] **A2.3 Barra lateral: lugares y volúmenes**: carpetas estándar, volúmenes con uso de disco, aparición al conectar SD/USB; cajón, riel o permanente según el ancho (F9). CA: conectar una USB en **T** la muestra y quitarla la oculta.
- [ ] **A2.4 Favoritos**: grupos, añadir desde el menú o arrastrando, reordenar; persistidos en `config.toml` vía core. CA: sobreviven a reiniciar la app.
- [ ] **A2.5 Barra de estado del panel**: elementos, selección, tamaño, espacio libre. CA: coincide con lo que muestra Ajustes › Almacenamiento (± redondeo).
- [ ] **A2.6 Vista de íconos y miniaturas**: cuadrícula, Coil con caché, frames de video y primera página de PDF; Ctrl+1/Ctrl+2. CA: `DCIM/Camera` con 1 000 fotos se desplaza fluido en **T**.
- [ ] **A2.7 Sesión**: restaurar pestañas, modo y proporción del divisor con `core::session`. CA: matar el proceso (`adb shell am kill`) y reabrir restaura todo.

## Hito A3 — Operaciones de archivo
- [ ] **A3.1 FFI de operaciones**: `OpQueue`, `OpRequest`, `Journal`, `ConflictPolicy`, `undo_request`, `validate_name`, `next_free_name`. CA: pruebas de la crate.
- [ ] **A3.2 Runner y servicio**: `OpsRunner` (copiar con `.tlacuache-part`, mover atómico o copiar+borrar entre volúmenes) y `OpsService` con notificación, cancelar y wake lock; aviso a MediaStore. CA: copiar 2 GB de interno a SD en **T** con la pantalla apagada y cancelar a mitad sin dejar `.part`; pruebas JVM del runner.
- [ ] **A3.3 Copiar/mover al otro panel**: franja central, barra contextual con destino («Copiar a B»), F5/F6. CA: en **P** copiar de A a B solo con toques; en **PE** con F5.
- [ ] **A3.4 Selección**: pulsación larga y modo selección (táctil), clic/Ctrl/Shift (ratón), Ctrl+A, Esc. CA: seleccionar un rango con Shift+clic en **PE** y varios con toques en **T**.
- [ ] **A3.5 Papelera propia y borrado**: `TrashStore` según ADR-A05, vista de Papelera (Restaurar, Vaciar), purga a 30 días, Supr y Shift+Supr con confirmación. CA: pruebas JVM; una foto enviada a la papelera desaparece de Google Fotos y vuelve al restaurarla.
- [ ] **A3.6 Carpetas protegidas**: `Protected.kt` en UI y runner (ADR-A06). CA: pruebas JVM; `DCIM` no se puede renombrar ni borrar desde ningún camino (menú, tecla, arrastre).
- [ ] **A3.7 Renombrar, nueva carpeta, nuevo archivo** (F2, F7, Ctrl+Alt+N). CA: nombres inválidos muestran el error del core.
- [ ] **A3.8 Conflictos**: diálogo Reemplazar/Combinar, Omitir, Conservar ambos, Cancelar, Aplicar a todos; notificación si no hay ventana visible. CA: copiar una carpeta sobre otra con archivos repetidos en **T**.
- [ ] **A3.9 Indicador y Deshacer**: indicador en la barra superior con detalle, Snackbar con Deshacer, Ctrl+Z. CA: deshacer copiar, mover, renombrar y papelera.
- [ ] **A3.10 Portapapeles interno**: Copiar/Cortar/Pegar (Ctrl+C/X/V) con píldora de pendientes. CA: cortar en una pestaña y pegar en otra.
- [ ] **A3.11 Menú contextual**: Abrir, Abrir con…, Compartir, Cortar, Copiar, Pegar, Renombrar, Papelera, Añadir a favoritos, Copiar ruta, Propiedades, Borrar permanentemente. CA: por clic derecho en **PE** y por ⋯ en **P**.

## Hito A4 — Modo escritorio y teclado
- [ ] **A4.1 Despacho de teclado**: tabla de UI_SPEC §8 desde `core::keymap` + `[keys]`, prioridad del foco de texto, `onProvideKeyboardShortcuts`. CA: todos los atajos de la tabla funcionan en **PE**; Meta+/ los lista.
- [ ] **A4.2 Ratón**: clic selecciona y doble clic abre, hover, cursores, tooltips con atajo, botones laterales atrás/adelante, densidad automática. CA: en **PE** las filas pasan a 32 dp al usar el ratón y a 48 dp al tocar.
- [ ] **A4.3 Arrastrar y soltar interno**: entre paneles, a carpetas, a favoritos y volúmenes; copiar/mover según volumen con Ctrl/Shift. CA: en **PE** y en **T**.
- [ ] **A4.4 Arrastre con otras apps**: hacia otras apps (URIs del provider) y desde ellas (copia por `ContentResolver`). CA: arrastrar una foto a Gmail y un adjunto de otra app a una carpeta en **PE**.
- [ ] **A4.5 Varias ventanas**: Ctrl+N y «Abrir en ventana nueva»; cola y portapapeles compartidos entre ventanas; arrastre entre ventanas. CA: dos ventanas en **PE** copiando entre sí.
- [ ] **A4.6 Pase de pulido en tablet y escritorio**: revisar UI_SPEC §1–§9 en **T** (vertical y horizontal), **P**, **PE** y, si se consigue, un Samsung con DeX. CA: lista de problemas resuelta o anotada como tareas.

## Hito A5 — Temas, preferencias y v0.1
- [ ] **A5.1 Temas**: los cinco temas, cambio en vivo, «Seguir al sistema», temas de usuario en `filesDir/themes`; estilos `glyph`, `invert`, `uppercase_headers`, `bracket_titles`. CA: cambiar de tema no recrea la actividad ni pierde estado.
- [ ] **A5.2 Preferencias**: pantalla de preferencias (`config.toml` + `SharedPreferences`), exportar/importar `config.toml`, mostrar `Android/` con aviso. CA: un `config.toml` de Linux importado aplica tema y favoritos existentes.
- [ ] **A5.3 Estados vacíos y textos**: revisar microcopia contra `BRAND.md`; todas las cadenas en `strings.xml`. CA: Lint sin cadenas en el código.
- [ ] **A5.4 Accesibilidad**: TalkBack en **P** recorre la lista, la barra contextual y los diálogos; contraste del core en los cinco temas. CA: checklist de accesibilidad completa.
- [ ] **A5.5 Rendimiento**: medir arranque en frío, memoria y fps en **T**; *baseline profile* si el arranque supera 800 ms. CA: cifras anotadas aquí y en el PRD.
- [ ] **A5.6 Release v0.1**: firma (clave fuera del repo), APK por ABI, ícono adaptativo desde `branding/logo-app.svg`, `CHANGELOG.md`, release en GitHub. CA: APK instalado desde la release en **T** y **P**.

## Hito A6 — v0.2
- [ ] **A6.1 Vista previa** por panel: imagen (zoom), video/audio (Media3), PDF (`PdfRenderer`), texto, Markdown (`core::markdown`), carpeta; debounce y cancelación (`core::preview`).
- [ ] **A6.2 Columnas Miller** en ventanas expandidas (Ctrl+3).
- [ ] **A6.3 Búsqueda recursiva** (Ctrl+F) con `core::search`, pestaña de resultados y cancelación.
- [ ] **A6.4 Renombrado masivo** (F2 con varios) con `core::bulk_rename` y vista previa.
- [ ] **A6.5 Notas por carpeta** con `core::notes`.
- [ ] **A6.6 Portapapeles del sistema**: pegar archivos copiados en otras apps y copiar hacia ellas.
- [ ] **A6.7 Recibir compartidos**: «Guardar en Tlacuache» con selector de carpeta.
- [ ] **A6.8 F-Droid**: metadatos (fastlane, `es-MX`), compilación reproducible con Rust, solicitud de inclusión.

## Hito A7 — v0.3+ (por definir)
- [ ] Selector de archivos para otras apps (`ACTION_GET_CONTENT` / `DocumentsProvider`).
- [ ] Zip navegable como carpeta.
- [ ] Comparar/sincronizar carpetas entre paneles.
- [ ] SMB/SFTP.
- [ ] Play Store: declaración de acceso a todos los archivos, ficha, capturas de teléfono, tablet y escritorio.
