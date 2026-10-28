# Decisiones de arquitectura (ADR) — Android

Formato: contexto → decisión → consecuencias. Añadir nuevas al final con número consecutivo. Se numeran `ADR-A##` para no confundirlas con las de Linux (`../TlacuacheBrowser/docs/DECISIONS.md`), que siguen valiendo para todo lo que vive en `tlacuache-core`.

## ADR-A01 — App nativa en Kotlin + Jetpack Compose
- **Contexto**: la app debe verse bien en teléfono, tablet y modo escritorio (DeX, Pixel), con táctil, ratón y teclado. La versión Linux es GTK4, que no corre en Android.
- **Decisión**: Kotlin con Jetpack Compose, Material 3 y Material 3 Adaptive. Sin Flutter, React Native ni WebView; sin UI en Rust (Slint, egui).
- **Consecuencias**: acceso directo a las APIs de ventanas, arrastre entre apps, atajos, almacenamiento y servicios; accesibilidad del sistema. La UI se escribe desde cero, sin reutilizar código GTK.

## ADR-A02 — Reutilizar `tlacuache-core` vía UniFFI, fijado a una etiqueta
- **Contexto**: el core de Linux (unas 8 000 líneas, 230+ pruebas) contiene la lógica que define el comportamiento de Tlacuache: orden natural con `ñ` y acentos, filtro, edad, cola de operaciones, conflictos, deshacer, renombrado masivo, búsqueda, notas, temas y configuración. Compila sin cambios para `aarch64-linux-android` (verificado el 2026-10-27).
- **Decisión**: crate `rust/tlacuache-ffi` con UniFFI que expone solo lo necesario. `tlacuache-core` se consume del repositorio público como dependencia git fijada a una etiqueta (`v0.2.0` al inicio). Los cambios al core se hacen en el repo Linux, con pruebas, y se publican con una etiqueta nueva; aquí solo se sube la versión.
- **Consecuencias**: mismo comportamiento que Linux y las mejoras benefician a ambas versiones. Costo: `cargo-ndk`, `.so` por ABI (~unos MB por ABI, a medir), JNA como dependencia y un paso de compilación más. F-Droid debe compilar Rust (lo admite).

## ADR-A03 — FFI por lotes, con registros propios
- **Contexto**: cada llamada de Kotlin a Rust por JNA cuesta del orden de microsegundos. Ordenar 10 000 entradas con un comparador que cruza la FFI haría ~130 000 llamadas.
- **Decisión**: la FFI recibe y devuelve colecciones: `arrange(entries, sort, query, show_hidden)` devuelve índices en una sola llamada. Los tipos se definen como registros UniFFI en `tlacuache-ffi` con conversiones `From` hacia el core; el core no se anota con UniFFI.
- **Consecuencias**: rendimiento independiente del puente; `tlacuache-core` sigue sin saber de Android. Hay que mantener las conversiones cuando cambien los tipos del core.
- **Medido (2026-10-27)**: `arrange` con 10 000 entradas tarda ~10 ms en Rust release y ~110 ms de punta a punta desde la JVM con el core en debug (JNA + serialización incluidas).
- **Matiz**: una función barata llamada por **fila visible** (p. ej. `age()` al pintar la columna «Edad») está permitida; lo prohibido es lo que crece con el tamaño del listado.

## ADR-A04 — Acceso a archivos: «todos los archivos» en Android 11+, almacenamiento heredado en Android 10; sin root
- **Contexto**: un explorador de doble panel necesita listar y operar con rutas reales y rápido. SAF obliga a elegir carpeta por carpeta y es lento en carpetas grandes. El autor no quiere root.
- **Decisión**: `MANAGE_EXTERNAL_STORAGE` en API 30+ y `READ/WRITE_EXTERNAL_STORAGE` con `requestLegacyExternalStorage` en API 29. E/S con `java.io`/`java.nio`. Sin root, Shizuku ni SAF en v0.1.
- **Consecuencias**: acceso a todo el almacenamiento compartido de todos los volúmenes en 11+. Fuera de alcance: `Android/data` y `Android/obb` de otras apps, `/data` y `/system`. En Android 10 los volúmenes extraíbles son de solo lectura. Para Play Store hay que llenar la declaración de «acceso a todos los archivos» (los gestores de archivos son un caso aceptado).

## ADR-A05 — Papelera propia por volumen, con formato freedesktop
- **Contexto**: Android no tiene una papelera para cualquier archivo. La de MediaStore (API 30+) solo cubre multimedia y pide confirmación del sistema cada vez. La regla de Tlacuache es que borrar sea reversible.
- **Decisión**: cada volumen tiene `.Tlacuache/Papelera/{files,info}` en su raíz, con `.nomedia`. Enviar a la papelera es un renombrado (instantáneo, mismo volumen) más un `.trashinfo` (`Path`, `DeletionDate`) como en freedesktop. Purga automática a los 30 días; Restaurar y Vaciar desde la vista de Papelera.
- **Consecuencias**: funciona con cualquier archivo y sobrevive a desinstalar la app. Deja una carpeta oculta en cada volumen. En Android 10, sin papelera en volúmenes extraíbles (solo lectura).

## ADR-A06 — Carpetas protegidas y `Android/` oculta
- **Contexto**: con acceso a todos los archivos, un toque equivocado puede mover `DCIM` o borrar `Download`, y otras apps dependen de esas rutas. El objetivo es comodidad a nivel usuario, con precaución.
- **Decisión**: no se pueden renombrar, mover ni enviar a la papelera: la raíz de cada volumen, las carpetas estándar en la raíz (`DCIM`, `Pictures`, `Movies`, `Download`, `Documents`, `Music`, `Ringtones`, `Notifications`, `Alarms`, `Podcasts`, `Audiobooks`, `Recordings`), `.Tlacuache/` y todo lo que está bajo `Android/`. Su contenido sí se gestiona normalmente (salvo `Android/`, que es de solo lectura). `Android/` se oculta por defecto aunque se muestren los ocultos; se puede mostrar desde Preferencias con un aviso.
- **Consecuencias**: la UI deshabilita esas acciones con una explicación en el tooltip o el Snackbar, y `OpsRunner` las rechaza también (doble barrera).

## ADR-A07 — Los mismos temas TOML, traducidos a Compose
- **Contexto**: Linux tiene cinco temas con tokens semánticos y validación de contraste en el core (ADR-009 de Linux). Se quiere la misma identidad en Android.
- **Decisión**: los temas se leen con `core::theme_catalog` y se convierten en `TlacuacheColors`/`TlacuacheStyle` (`CompositionLocal`) y en un `ColorScheme` de Material 3. Ningún color, radio, tamaño de fila ni fuente se escribe en el código. Sin Material You (colores dinámicos del fondo de pantalla) en v0.1.
- **Consecuencias**: el mismo TOML sirve en ambas plataformas y los temas de usuario funcionan sin recompilar. Las claves `[terminal]` y `[decor]` no se usan por ahora.

## ADR-A08 — Layout por ancho de ventana y siempre dos paneles lógicos
- **Contexto**: la misma app corre en un teléfono de 360 dp y en una ventana de escritorio de 1 600 dp, y en modo escritorio la ventana cambia de tamaño en cualquier momento.
- **Decisión**: el layout se elige con las *window size classes* (compacta/media/expandida), nunca por tipo de dispositivo. Cada ventana tiene siempre los paneles A y B; en compacta se muestra uno y se alterna con A/B, así «copiar/mover al otro panel» y F5/F6 funcionan en cualquier tamaño.
- **Consecuencias**: un solo modelo de estado para todos los layouts y cambios de tamaño sin perder nada. En teléfono el doble panel es una función «oculta» que hay que enseñar (alternador A/B visible en la barra superior).

## ADR-A09 — Operaciones en un servicio en primer plano `dataSync`
- **Contexto**: copiar o mover varios GB debe seguir si el usuario cambia de app o apaga la pantalla, con progreso y cancelación. Android limita el trabajo en segundo plano.
- **Decisión**: `OpsService` de tipo `dataSync` con notificación de progreso y `PARTIAL_WAKE_LOCK` mientras corre una operación. En Android 15+, al recibir `onTimeout` (límite diario de `dataSync`) la cola se pausa de forma segura y se avisa para continuar. Permisos: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS` (13+), `WAKE_LOCK`.
- **Consecuencias**: operaciones largas fiables en API 29+. Si el límite resulta un problema real, evaluar en un ADR nuevo los *user-initiated data transfer jobs* (API 34+).

## ADR-A10 — Listado propio con `java.nio` y el nombre como identidad
- **Contexto**: en Linux, `fs::DirectorySource` aplica diferencias calculadas por `core::listing_diff` para conservar selección y desplazamiento (ADR-017 de Linux). En Compose, las listas perezosas con `key` estable ya conservan ambos al reemplazar la lista.
- **Decisión**: leer la carpeta en `Dispatchers.IO` con `newDirectoryStream` + `readAttributes`, por lotes; ordenar y filtrar con `arrange()` del core; publicar la lista completa con el nombre como `key`. Resincronizar con `FileObserver` (debounce de 300 ms), al volver a primer plano y con Ctrl+R; revisión periódica solo si `FileObserver` no recibe eventos en algún tipo de volumen.
- **Consecuencias**: no se usa `listing_diff`; menos código. Cada resincronización relee la carpeta entera (igual que en Linux).
- **Verificado (2026-10-27, Pixel 10, Android 17, almacenamiento interno)**: `FileObserver` sí recibe los cambios hechos por otros procesos a través de FUSE: `adb shell` (crear, renombrar, borrar) y una captura de pantalla guardada por SystemUI en `Pictures/Screenshots` aparecieron sin que la app pasara a segundo plano. Por eso no se implementa la revisión periódica. Pendiente: comprobarlo en SD/USB y en Android 10; si ahí no llegan eventos, añadir la revisión periódica solo para esos volúmenes.

- **Nota (2026-10-28)**: en **T** (Android 11), con los dos paneles en la misma carpeta solo uno recibía los avisos: Android asocia los eventos de `FileObserver` a la ruta dentro del proceso y el último observador desplazaba al primero. `FolderWatcher` comparte ahora un observador por carpeta entre todos los que la vigilan.

## ADR-A11 — Configuración compartida en `config.toml`; lo propio de Android en `SharedPreferences`
- **Contexto**: la configuración de Linux es un TOML gestionado por el core. Android añade preferencias que no existen en Linux (densidad, layout por clase de ancho).
- **Decisión**: `filesDir/config.toml` con `core::config` para las claves comunes (incluidos favoritos y atajos). Las preferencias exclusivas de Android van en `SharedPreferences`, sin tocar el esquema del core.
- **Consecuencias**: el `config.toml` se puede exportar e importar entre dispositivos y con Linux. Dos almacenes de preferencias; Preferencias los presenta juntos.

## ADR-A12 — Identificadores: `applicationId` `io.github.RicardoMonroy.Tlacuache`
- **Contexto**: el `applicationId` no se puede cambiar una vez publicado en Play Store. Linux usa `io.github.RicardoMonroy.Tlacuache` (ADR-015 de Linux).
- **Decisión**: `applicationId = "io.github.RicardoMonroy.Tlacuache"`; `namespace` y paquete Kotlin en minúsculas: `io.github.ricardomonroy.tlacuache`.
- **Consecuencias**: misma identidad en Flathub, F-Droid y Play Store. El paquete Kotlin sigue la convención en minúsculas sin afectar al id público.

## ADR-A13 — Repositorio propio, GPL-3.0-or-later, sin dependencias propietarias
- **Contexto**: la app será gratuita; primero APK y F-Droid, después Play Store. Linux es GPL-3.0-or-later con CLA (ADR-012 de Linux).
- **Decisión**: repositorio `TlacuacheAndroid` separado, GPL-3.0-or-later y con el mismo CLA. Solo dependencias libres (AndroidX, Compose, Coil, Media3, JNA, crates MIT/Apache). Sin Google Play Services, Firebase, anuncios ni analítica.
- **Consecuencias**: apto para F-Droid. Play Store admite apps GPL. La CI de Linux no se mezcla con Gradle.

## ADR-A14 — Sin terminal integrada
- **Contexto**: la terminal por panel es la función estrella en Linux, pero Android no ofrece un shell útil del usuario por carpeta y el objetivo aquí es la gestión de archivos a nivel usuario.
- **Decisión**: no hay terminal. F4 queda libre. `core::shell` no se expone.
- **Consecuencias**: menos permisos y menos superficie de riesgo. Si en el futuro se quiere, se evaluaría un «Abrir en Termux» opcional en un ADR nuevo.
