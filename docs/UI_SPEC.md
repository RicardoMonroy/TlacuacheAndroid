# UI Spec — Tlacuache para Android

Referencia: la distribución y el comportamiento de Tlacuache Browser para Linux (`../TlacuacheBrowser/docs/UI_SPEC.md`), adaptados a táctil y a modo escritorio. Identidad visual: `BRAND.md`; colores y estilo: `THEMES.md` (ambos en el repo Linux).

## 1. Principio: el layout depende de la ventana, no del dispositivo

El layout se elige con los umbrales de las *window size classes* de Material 3 (600 y 840 dp), a partir del **ancho de la ventana actual** (`LocalWindowInfo.containerSize`, no la pantalla). Una tablet en vertical, un teléfono en horizontal y una ventana de DeX a medio ancho comparten layout. Al redimensionar la ventana en modo escritorio el layout cambia en vivo, sin recrear el estado (pestañas, selección, desplazamiento, cola de operaciones).

| Clase de ancho | Ejemplos | Barra lateral | Paneles | Barra de acciones |
|---|---|---|---|---|
| Compacta (< 600 dp) | Teléfono vertical | Cajón modal (botón ☰ o deslizar desde el borde) | 1 visible; alternador **A / B** en la barra superior | Barra inferior contextual |
| Media (600–839 dp) | Tablet vertical, teléfono horizontal, plegable abierto | Riel de navegación (íconos) que se expande | 2 lado a lado si `dual_pane` está activo (por defecto sí en horizontal) | Franja central entre paneles + barra inferior contextual |
| Expandida (≥ 840 dp) | Tablet horizontal, DeX, escritorio de Pixel | Permanente, colapsable (F9), ancho redimensionable | 2 lado a lado, divisor arrastrable | Encabezado por panel (ADR-A16) + franja central |

Con dos paneles, cada uno mide al menos 400 dp (botones y ruta legibles); en un teléfono horizontal el divisor apenas se mueve. «Doble panel» en el menú ⋮ (F3) apaga y enciende el modo dual en media y expandida; apagado se ve un panel con el alternador A/B. En compacta, sobre la fila de navegación va una fila con el alternador A/B y el botón de pestañas.

La barra permanente necesita además 480 dp de alto: con poca altura (teléfono horizontal, 923 dp de ancho) se muestra el riel para no quitar sitio a los paneles, y su ☰ abre la barra completa como cajón. En expandida, ☰ o F9 reduce la permanente a riel y la vuelve a expandir. En compacta, ☰ va al inicio de la fila del alternador A/B.

**Poca altura** (ventana de menos de 480 dp de alto, p. ej. teléfono en horizontal): el contenido manda. El encabezado se reduce a una sola fila de 48 dp: ‹ › ↑, ruta, botón de pestañas (número de pestañas; abre la lista para elegir, cerrar o abrir una nueva), alternador A/B si se ve un solo panel, buscar y ⋮. Sin tira de pestañas. Nada se dibuja debajo del recorte de la cámara a los lados.

Columnas Miller (v0.2): solo disponibles en clase expandida.

```
Expandida (tablet horizontal / escritorio)
┌────────────┬─────────────────────────────┬──┬─────────────────────────────┐
│ ☰          │ [Fotos][Descargas][+]       │  │ [Documentos][+]             │
│ LUGARES    │ ‹ › ↑ Interno › DCIM  🔍 ⋮  │C→│ ‹ › ↑ Interno › Docs  🔍 ⋮  │
│  Fotos     │                             │  │                             │
│  Descargas │ ┌─────────────────────────┐ │  │ ┌─────────────────────────┐ │
│  Documentos│ │ nombre   tamaño edad    │ │M→│ │ nombre   tamaño edad    │ │
│ VOLÚMENES  │ │ IMG_01   4 MB   2h      │ │  │ │ factura  80 KB  3d      │ │
│  Interno 62%│ └─────────────────────────┘ │  │ └─────────────────────────┘ │
│  SD 30%    │ 120 elementos · 3 sel · 12 MB│  │ 8 elementos · 41 GB libres  │
│ FAVORITOS  │                             │  │                             │
│ Papelera   │                             │  │      ▸ 2 operaciones        │
└────────────┴─────────────────────────────┴──┴─────────────────────────────┘

Compacta (teléfono vertical)
┌──────────────────────────────┐
│ ☰  [ A | B ]   🔍  ⋮         │
│ ‹ ↑ Interno › DCIM › Camera  │
│ ┌──────────────────────────┐ │
│ │ IMG_01      4 MB    2h   │ │
│ │ IMG_02      3 MB    1d   │ │
│ └──────────────────────────┘ │
│ ─ 3 seleccionados ────────── │
│ [Copiar→B][Mover→B][⋯][🗑]   │
└──────────────────────────────┘
```

## 2. Panel

- **Panel activo**: el último tocado o con foco; en modo dual se marca con una línea de acento arriba (token `pane_active_border`). Las acciones «al otro panel» usan el otro como destino. En compacta, el alternador A/B cambia el panel visible y activo; el otro conserva su carpeta, pestañas y selección.
- **Pestañas**: tira propia con pestañas de ancho fijo y título a la izquierda, desplazable. Botón + y menú (Duplicar, Cerrar, Cerrar las demás). En compacta y con poca altura se reemplaza por un botón con el número de pestañas que abre un selector. Clic medio cierra; arrastrar una pestaña la reordena o la pasa al otro panel (expandida).
- **Barra de ruta**: breadcrumb tocable que empieza en el **volumen** («Interno», «SD», «USB»), nunca en `/storage/emulated/0`. Tocar la zona vacía o Ctrl+L la vuelve editable, con autocompletado (`core::path_input`). Se desplaza horizontalmente si no cabe, mostrando siempre el final.
- **Vistas**:
  - Lista detallada: columnas visibles según el ancho del panel (nombre siempre; luego edad, tamaño, fecha). Encabezados tocables para ordenar; carpetas siempre primero (`core::sort`).
  - Íconos: cuadrícula de miniaturas (fotos, videos, PDF) o íconos de tipo; nombre en dos líneas. Pellizcar cambia el tamaño (v0.2). Cada pestaña recuerda su vista; se cambia con ⋮ › Vista de íconos o Ctrl+1/Ctrl+2.
  - Columnas Miller (v0.2, solo expandida).
- **Edad relativa**: chip (o texto, según el tema) con formato compacto (`ahora`, `12min`, `2h`, `3d`, `3M`, `1a`); colores por bucket según `THEMES.md` §3.
- **Barra de estado** del panel, al pie: elementos, seleccionados, tamaño seleccionado, espacio libre del volumen («133 GB libres de 256 GB»; del interno, el total nominal que muestra Ajustes, no el del sistema de archivos). En compacta se reduce a «N elementos» o «N seleccionados · tamaño»; en un panel de menos de 400 dp, con selección no se repiten los elementos y el espacio dice solo «133 GB libres». El espacio se relee con cada lectura de la carpeta.
- **Estados vacíos** con la voz de la marca («Aquí no hay nada… ni para el tlacuache.»). Nunca humor en errores.

## 3. Interacción por tipo de puntero

La app distingue el tipo de puntero de cada evento (`PointerType.Touch` / `Mouse` / `Stylus`) y no el dispositivo.

| Gesto | Táctil | Ratón / touchpad |
|---|---|---|
| Abrir carpeta o archivo | Toque | Doble clic (Enter con teclado) |
| Seleccionar | Pulsación larga entra en modo selección (y empieza a arrastrar el elemento; si no se mueve, solo lo selecciona); luego toque alterna | Clic selecciona; Ctrl+clic alterna; Shift+clic rango |
| Menú contextual | Botón ⋯ de la barra contextual (la pulsación larga sobre lo seleccionado es para arrastrar) | Clic derecho (en el puntero); tecla Menú o Shift+F10 |
| Arrastrar | Pulsación larga + arrastrar (lo seleccionado, o ese elemento) | Arrastrar directamente |
| Hover | — | Resaltado de fila (`hover_bg`) y tooltips en botones |
| Subir/atrás | Gesto atrás del sistema; botón ↑ | Botones ‹ › ↑, botones laterales del ratón |

**Densidad**: filas de 48 dp con táctil y de 32 dp cuando el último puntero fue un ratón (`row_height` del tema como base). Preferencia: Auto / Cómoda / Compacta.

**Gesto atrás del sistema** (y Esc), en este orden: cierra el menú o diálogo abierto → limpia el filtro → sale del modo selección → retrocede en el historial de la pestaña → sale de la app. Con *predictive back* (Android 14+) se anima la vuelta.

## 4. Copiar y mover entre paneles (lo central)

- **Franja central** (media y expandida, solo en modo dual): botones verticales **Copiar →** y **Mover →** cuyas flechas apuntan del panel activo al otro; deshabilitados sin selección. Debajo, **Pegar** si hay algo en el portapapeles interno.
- La franja también es el divisor: se arrastra para cambiar la proporción. Con un solo panel visible (compacta o modo dual apagado) no hay franja y la barra inferior de la selección dice el destino: «Copiar a B · Mover a B · Compartir».
- **Barra contextual** (con selección): Copiar al otro panel, Mover al otro panel, Renombrar, Compartir, Papelera y ⋯ (Copiar, Cortar, Abrir con…, Añadir a favoritos, Copiar ruta, Propiedades, Borrar permanentemente). En compacta los textos dicen el destino: «Copiar a B».
- **Arrastrar y soltar**: entre paneles (copia en volúmenes distintos, mueve en el mismo volumen; con Ctrl fuerza copiar y con Shift fuerza mover; al soltar con el botón derecho se pregunta), a una carpeta de la lista, a un favorito o volumen de la barra lateral, y hacia/desde otras apps en multiventana o escritorio.
- **Portapapeles interno** (MVP): Copiar/Cortar y luego Pegar en cualquier carpeta o pestaña. Una píldora «3 elementos para pegar · Pegar · ✕» queda visible mientras haya algo.
- Al terminar: Snackbar «Se copiaron 3 elementos a Documents» con **Deshacer** (`core::ops::undo_request`).

## 5. Operaciones en segundo plano

- Cada operación se encola (`core::ops`) y corre en un servicio en primer plano con notificación: progreso, elemento actual, velocidad y botón **Cancelar**.
- En la app: indicador en la barra superior («▸ 2 operaciones») que despliega el detalle de cada operación (progreso, cancelar, resultado, Deshacer).
- **Conflictos**: diálogo con Reemplazar (o Combinar, en carpetas), Omitir, Conservar ambos, Cancelar y la casilla «Aplicar a todos» (`core::ops::ConflictPolicy`). Muestra tamaño y fecha de ambos y cuál es más reciente.
- **Borrar**: Papelera por defecto, sin confirmación (se puede deshacer). Borrado permanente: confirmación con el número de elementos y el tamaño.

## 6. Barra lateral

- **Lugares**: Fotos (`DCIM`), Imágenes (`Pictures`), Videos (`Movies`), Descargas (`Download`), Documentos (`Documents`), Música (`Music`), Sonidos (`Ringtones`, `Notifications`, `Alarms` agrupados; se omiten si no existen), Grabaciones (`Recordings`, Android 12+). Cada lugar aparece solo si su carpeta existe; «Sonidos» abre la primera de las tres que exista. La raíz del almacenamiento interno se abre desde su volumen («Interno»).
- **Volúmenes**: Interno, tarjetas SD y USB (`StorageManager`), con barra de uso (aviso ≥ 80 %, crítico ≥ 90 %, `core::usage`) y espacio libre. Aparecen y desaparecen al conectarlos.
- **Favoritos**: grupos con nombre; añadir arrastrando una carpeta o desde el menú contextual (⋮ del panel o pulsación larga sobre una carpeta: «Añadir a favoritos», al primer grupo); reordenar arrastrando. Táctil: pulsación larga sobre un favorito da Subir, Bajar, Mover a «grupo» y Quitar; tocar el nombre de un grupo da Renombrar y Eliminar; + crea un grupo. En el riel aparecen como carpetas. Persistidos en `[[favorites]]` de `config.toml`, con `~` para el almacenamiento interno, como en Linux.
- **Papelera**: abre la vista de papelera de todos los volúmenes (Restaurar, Borrar permanentemente, Vaciar).
- Tocar un lugar abre la carpeta en el panel activo; con Ctrl+clic o clic medio, en una pestaña nueva.

## 7. Vista previa (v0.2)

- Por panel; abajo o a la derecha (preferencia; a la derecha solo en expandida). En compacta se abre como hoja inferior.
- Imagen (zoom con pellizco, rueda o Ctrl+rueda), video/audio (Media3, sin reproducción automática), PDF (página a página), texto (1 MB máx.), Markdown renderizado (`core::markdown`), carpeta (conteo, tamaño en segundo plano, nota de la carpeta).
- Detalles: nombre, tipo, tamaño, modificado, edad, dimensiones/duración.
- Se actualiza con debounce de 120 ms (`core::preview::DEBOUNCE_MS`) y cancela la petición anterior.
- Qué se muestra (`core::preview::target`): un elemento → su vista previa; varios → resumen; ninguno → la carpeta actual.

## 8. Atajos de teclado (con teclado físico; remapear con `[keys]` llega cuando el core lo permita, ADR-A15)

| Acción | Atajo |
|---|---|
| Abrir / entrar | Enter, doble clic |
| Mover el cursor (la selección) / extender | ↑ ↓ / Shift+↑ ↓ |
| Subir nivel | Backspace, Alt+↑ |
| Atrás / Adelante | Alt+← / Alt+→ |
| Cambiar panel activo | Tab |
| Alternar 1/2 paneles (A/B en compacta) | F3 |
| Copiar al otro panel | F5 |
| Mover al otro panel | F6 |
| Nueva carpeta | F7 / Ctrl+Shift+N |
| Nuevo archivo vacío | Ctrl+Alt+N |
| Papelera | Supr |
| Borrado permanente | Shift+Supr (con confirmación) |
| Renombrar (varios: renombrado masivo, v0.2) | F2 |
| Copiar / cortar / pegar | Ctrl+C / Ctrl+X / Ctrl+V |
| Seleccionar todo / ninguno | Ctrl+A / Esc |
| Deshacer la última operación | Ctrl+Z |
| Vista previa (v0.2) | Espacio (alternar) |
| Menú contextual | Tecla Menú, Shift+F10 |
| Preferencias | Ctrl+, |
| Menú principal | F10 |
| Barra lateral | F9 |
| Filtro rápido | escribir directamente; Esc limpia |
| Mostrar ocultos | Ctrl+H |
| Recargar (conserva selección y desplazamiento) | Ctrl+R |
| Ruta editable | Ctrl+L |
| Buscar desde la carpeta actual (v0.2) | Ctrl+F |
| Vista lista / íconos / Miller | Ctrl+1 / Ctrl+2 / Ctrl+3 |
| Nueva pestaña / cerrar / siguiente | Ctrl+T / Ctrl+W / Ctrl+Tab |
| Nueva ventana (multiventana/escritorio) | Ctrl+N |

- **Filtro rápido** y su interacción con Backspace y Espacio: igual que Linux (ADR-006 de Linux, `core::filter::apply_key`). Subcadena sin mayúsculas ni acentos.
- Se registran en `onProvideKeyboardShortcuts` para que aparezcan en la ayuda de atajos del sistema (Meta+/).
- No usar combinaciones con Meta (Win/Cmd): Android, DeX y el escritorio de Pixel las reservan.
- F4 queda libre (en Linux era la terminal).

## 9. Modo escritorio y multiventana

- Actividad redimensionable, sin orientación fija, sin relación de aspecto mínima/máxima. El estado vive en `ViewModel` + `SavedStateHandle`; los cambios de tamaño no recrean la cola ni pierden la selección.
- **Varias ventanas**: Ctrl+N o «Abrir en ventana nueva» en el menú de carpeta; cada ventana es una tarea propia con su sesión. Se arrastran archivos entre ventanas de Tlacuache y con otras apps.
- Cursor: mano en elementos clicables, I en campos de texto, flechas de redimensión en los divisores (`pointerHoverIcon`).
- Desplazamiento con rueda y touchpad; Shift+rueda desplaza horizontal (breadcrumb, Miller).
- Ventana mínima útil: 360 × 480 dp; por debajo se usa el layout compacto.
- Tooltips en todos los botones de solo ícono (con su atajo: «Copiar al otro panel (F5)»).

## 10. Permisos y primer arranque

- Pantalla de bienvenida con el logo, una frase que explica para qué se pide el acceso y un botón:
  - Android 11+: abre `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` para Tlacuache.
  - Android 10: diálogo de permiso de almacenamiento.
- Si se revoca el permiso, la app vuelve a esta pantalla en lugar de mostrar carpetas vacías.
- Notificaciones (Android 13+): se piden la primera vez que se lanza una operación en segundo plano, explicando que son para el progreso.

## 11. Tema

Ver `THEMES.md` (repo Linux). Predeterminado: `nord`. Opción «Seguir al sistema» (claro → `claro`, oscuro → `nord`, configurables). Se elige en ⋮ › «Tema…» (y en Preferencias, A5.2): tocar un tema lo aplica al instante; con «Seguir al sistema» se marca un tema para el modo claro y otro para el oscuro, y al apagarlo se queda el que se ve. Sobre la cabecera, el texto secundario es `header_fg` atenuado (no `fg_muted`, que solo contrasta sobre el panel). Los colores de la barra de estado y de navegación del sistema siguen al tema (edge-to-edge). Íconos de tipo de archivo teñidos por categoría (`[filetypes]`), como en Linux (ADR-010 de Linux).
