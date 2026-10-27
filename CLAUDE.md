# Tlacuache para Android — guía para Claude Code

Gestor de archivos para Android (teléfono, tablet y **modo escritorio**: Samsung DeX y el escritorio de Pixel). Es el port de Tlacuache Browser para Linux: barra lateral, área central con **uno o dos paneles**, copiar y mover entre paneles con un toque o una tecla, vista previa y edad de archivos con color. Sin terminal y sin root: gestiona las carpetas del usuario (Fotos, Videos, Descargas, Documentos, Música, Sonidos…).

Proyecto Linux de referencia (solo lectura): `../TlacuacheBrowser` (repositorio público `github.com/RicardoMonroy/Tlacuache`). Su lógica vive en la crate `tlacuache-core`, que esta app reutiliza vía UniFFI.

## Documentos del proyecto (léelos antes de cambiar algo relacionado)

| Archivo | Cuándo leerlo |
|---|---|
| `docs/PRD.md` | Qué construimos, alcance, no-objetivos |
| `docs/UI_SPEC.md` | Layouts por ancho de ventana, táctil/ratón/teclado, atajos, modo escritorio |
| `docs/ARCHITECTURE.md` | Módulos, puente con el core, almacenamiento, hilos, operaciones, papelera |
| `docs/ROADMAP.md` | Hitos y tareas con criterios de aceptación. **Trabaja en orden** |
| `docs/DECISIONS.md` | Decisiones de arquitectura (ADR). Añade una entrada si tomas una decisión nueva |
| `../TlacuacheBrowser/docs/BRAND.md` | Logo, paleta de marca, íconos, voz. Vale igual en Android |
| `../TlacuacheBrowser/docs/THEMES.md` | Esquema de temas. Los TOML son los mismos; en Android se traducen a Compose (ADR-A07) |

## Stack (no cambiar sin un ADR nuevo)

- Kotlin + Jetpack Compose + Material 3 + Material 3 Adaptive (window size classes)
- `minSdk 29` (Android 10), `targetSdk 36`, `compileSdk 37` (lo exigen las bibliotecas AndroidX vigentes); JDK 17
- Gradle con Kotlin DSL y catálogo de versiones (`gradle/libs.versions.toml`); se usa el wrapper (`./gradlew`), no un Gradle del sistema
- Rust: crate `rust/tlacuache-ffi` (UniFFI) que expone `tlacuache-core` a Kotlin; compilada con `cargo-ndk` (NDK 28) para `arm64-v8a`, `armeabi-v7a` y `x86_64`
- Coroutines + Flow; Coil 3 (imágenes y miniaturas); Media3 (video/audio); `PdfRenderer` del sistema (PDF)
- Sin Google Play Services, Firebase ni analítica: la app debe poder entrar a F-Droid
- Sin WebView, sin Flutter, sin React Native

Antes de fijar versiones en `libs.versions.toml` o `Cargo.toml`, consulta la versión estable vigente de cada dependencia (AGP, Kotlin, Compose BOM, Material 3 Adaptive, Coil, Media3, UniFFI) y verifica que sean compatibles entre sí (AGP ↔ Kotlin ↔ plugin de Compose). No inventes versiones.

## Comandos

```bash
scripts/build-rust.sh           # cargo-ndk + bindings Kotlin de UniFFI (Gradle lo llama solo)
./gradlew assembleDebug         # APK de depuración
./gradlew installDebug          # instalar en el dispositivo conectado (adb)
./gradlew test                  # pruebas JVM (lógica Kotlin + core cargado desde la build de host)
./gradlew connectedCheck        # pruebas instrumentadas en dispositivo/emulador
./gradlew lint ktlintCheck      # Android Lint + estilo Kotlin
scripts/check.sh                # cargo fmt --check + clippy de la ffi + lint + ktlint + test
adb shell getprop ro.product.cpu.abi   # ABI de un dispositivo
export ANDROID_SERIAL=<serial>          # sin esto, installDebug instala en TODOS los dispositivos conectados
ANDROID_AVD_HOME=~/.config/.android/avd ~/Android/Sdk/emulator/emulator -avd Tlacuache_API29   # emulador Android 10
```

Definición de "terminado" para cualquier tarea: compila, `scripts/check.sh` en verde, criterio de aceptación de la tarea en `ROADMAP.md` cumplido y probado en el dispositivo que indique la tarea. Marca la casilla `[x]` en el roadmap al terminar.

## Reglas de código

1. **Nunca bloquear el hilo principal.** Listados, tamaños, copias, hashing, miniaturas, búsqueda y llamadas al core que recorren disco van en `Dispatchers.IO` (o `Default` si solo calculan). La UI observa `StateFlow`.
2. **El core manda en la lógica.** Orden, filtro, edad, historial, cola de operaciones, conflictos, deshacer, renombrado masivo, búsqueda, notas, temas y configuración se usan desde `tlacuache-core` vía `tlacuache-ffi`. No reimplementarlos en Kotlin. Si el core necesita un cambio, se hace en el repositorio Linux con pruebas, se publica una etiqueta y aquí se sube la versión fijada (ADR-A02).
3. **Llamadas al core por lotes**: nunca una llamada FFI por elemento ni por comparación (ordenar 10 000 entradas es una llamada que devuelve índices, ADR-A03).
4. Sin `!!` ni `error()` en rutas de UI o de E/S. Los errores de E/S se convierten en un resultado tipado y se muestran con `Snackbar` (no bloqueante) o un diálogo (decisiones).
5. Rutas: `java.io.File`/`java.nio.file.Path` dentro del almacenamiento compartido. A otras apps se les pasan `content://` de nuestro `FileProvider`, nunca `file://`.
6. **Operaciones destructivas**: borrar manda a la papelera propia (ADR-A05). El borrado permanente requiere confirmación explícita. Las carpetas protegidas (raíz de cada volumen, carpetas estándar y `Android/`) no se renombran, mueven ni borran (ADR-A06).
7. Tras crear, mover, renombrar o borrar, avisar a MediaStore (`MediaScannerConnection.scanFile`) de las rutas viejas y nuevas, para que la galería y otras apps lo vean.
8. Ningún color, radio, tamaño de fila ni fuente escritos en el código: todo sale del tema activo (`TlacuacheTheme`, ADR-A07). Los íconos nuevos siguen `BRAND.md` §5.
9. Nombres de código en inglés; textos de UI en español en `res/values/strings.xml` (preparado para traducciones con `values-en/`). Cero cadenas de UI en el código.
10. Todo control usable con táctil, ratón y teclado; descripción de accesibilidad (`contentDescription`/`semantics`) en cada ícono que actúa.
11. Commits pequeños, uno por tarea del roadmap, mensaje en imperativo: `feat(pane): add dual pane layout`.

## Flujo de trabajo esperado

1. Lee la siguiente tarea pendiente en `docs/ROADMAP.md`.
2. Si la tarea es ambigua o afecta la arquitectura, propón el enfoque en 3–5 líneas y espera confirmación.
3. Implementa, prueba y verifica (comandos de arriba). Para cambios de UI, describe cómo probarlo a mano y en qué dispositivo.
4. Marca la tarea y resume en 2–3 líneas qué cambió.

## Dispositivos de prueba

- Tablet con Android 11: tablets, permiso de todos los archivos, táctil.
- Pixel 10: teléfono y modo escritorio con pantalla externa, teclado y ratón.
- Emulador con Android 10 (API 29): permisos heredados (`requestLegacyExternalStorage`).
- Samsung con DeX: pendiente de conseguir; hasta entonces el modo escritorio se valida en el Pixel.

## Contexto del usuario

- Desarrollador full stack y docente; usa Arch Linux (Wayland). Prefiere respuestas directas y que se le corrija si se equivoca.
- No usa Android Studio: todo se compila y prueba desde la terminal (`./gradlew`, `adb`).
- Si una API de Android, Compose o UniFFI no está clara, consulta la documentación oficial de la versión fijada en vez de suponer.
