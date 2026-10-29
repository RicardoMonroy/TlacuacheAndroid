# PRD — Tlacuache para Android

## 1. Visión

Un gestor de archivos para Android que se sienta igual de cómodo en el teléfono, en la tablet y en **modo escritorio** (Samsung DeX, escritorio de Pixel con pantalla externa). Lleva la filosofía de Tlacuache Browser para Linux (uno o dos paneles, copiar y mover entre paneles sin fricción, vista previa, edad de los archivos con color, orientado a teclado cuando hay teclado) a una plataforma táctil, sin perder lo que hace bueno al original cuando se conecta un ratón y un teclado.

## 2. Problema

- Los exploradores de Android están pensados para el teléfono vertical: en tablet y en DeX se ven como una lista estirada.
- Copiar o mover entre carpetas exige «copiar → navegar → pegar». El doble panel resuelve eso, pero las apps que lo ofrecen (Total Commander, X-plore) tienen una interfaz anticuada o saturada.
- En modo escritorio casi ninguno responde bien a clic derecho, hover, atajos de teclado, arrastrar entre ventanas o ventanas redimensionables.
- Muchos exploradores populares traen anuncios, rastreadores o piden permisos de más.

## 3. Usuario objetivo

- Primario: el autor (Pixel 10 con modo escritorio, tablet Android 11).
- Secundario: usuarios de tablet y DeX que organizan fotos, videos, descargas y documentos y quieren una herramienta seria, gratuita y sin anuncios.

## 4. Alcance

### MVP (v0.1) — "lo uso a diario en la tablet y en el escritorio"

- Permiso de acceso a archivos con una pantalla de bienvenida que explica por qué (Android 11+: todos los archivos; Android 10: almacenamiento).
- **Uno o dos paneles** según el ancho de la ventana, con alternador manual. En teléfono, dos paneles lógicos (A/B) que se alternan, para que «copiar al otro panel» funcione igual.
- Por panel: pestañas, barra de ruta (breadcrumb tocable y editable), vista de **lista detallada** y de **íconos/miniaturas**.
- Navegación con táctil, ratón y teclado; historial atrás/adelante por pestaña; gesto atrás del sistema integrado.
- Columnas de detalle: nombre, tamaño, fecha, **edad relativa con color** (las que quepan según el ancho).
- Operaciones: copiar, mover, renombrar, crear carpeta/archivo, enviar a la **papelera propia**, borrar permanente (con confirmación), **copiar/mover al otro panel** con un botón, F5/F6 o arrastrando.
- **Cola de operaciones** en segundo plano con notificación de progreso, cancelación, conflictos (Reemplazar/Omitir/Conservar ambos/Aplicar a todos) y **Deshacer**.
- Barra lateral: **Lugares** (Fotos/DCIM, Imágenes, Videos, Descargas, Documentos, Música, Sonidos, Grabaciones), **Volúmenes** (almacenamiento interno, SD, USB) con uso de disco, **Favoritos** agrupados y Papelera.
- Abrir archivos con su app, «Abrir con…», **Compartir**.
- Filtro rápido de la carpeta (escribir con teclado físico o con el botón de buscar), ocultos (Ctrl+H o menú).
- **Modo escritorio**: ventana redimensionable, clic derecho, hover, selección con Ctrl/Shift, atajos de teclado, arrastrar y soltar entre paneles y con otras apps, varias ventanas.
- Los cinco temas de Linux (Nord por defecto, Consola, Claro, Cyberpunk, Talavera), con opción de seguir el modo claro/oscuro del sistema.

### v0.2

- Vista previa por panel: imágenes (zoom), video/audio, PDF, texto, Markdown renderizado, carpeta (conteo y tamaño).
- Columnas Miller en ventanas anchas (tablet horizontal, escritorio).
- Búsqueda recursiva por nombre en segundo plano.
- Renombrado masivo con regex y vista previa.
- Notas por carpeta.
- Portapapeles del sistema: pegar archivos copiados desde otras apps y hacia ellas.
- Recibir archivos compartidos desde otras apps («Guardar en…» con el selector de Tlacuache).
- Publicación en F-Droid.

### v0.3+

- Ser selector de archivos para otras apps (`ACTION_GET_CONTENT` / `DocumentsProvider`).
- Archivos comprimidos (zip) navegables como carpetas.
- Comparar/sincronizar carpetas entre paneles.
- Acceso de red (SMB/SFTP).
- Publicación en Play Store.

## 5. No-objetivos

- Root, Shizuku o acceso a `/data`, `/system` y a `Android/data`/`Android/obb` de otras apps.
- Terminal integrada.
- Editar permisos Unix o propietarios.
- Limpiador de almacenamiento, «optimizador», anuncios o analítica.
- Montar o desmontar volúmenes (lo hace el sistema).

## 6. Requisitos no funcionales

- Arranque en frío < 800 ms en la tablet de pruebas (medir).
- Abrir una carpeta de 10 000 archivos sin congelar la UI (listado en segundo plano + listas perezosas).
- 60 fps al desplazar listas con miniaturas en la tablet de pruebas.

Medido el 2026-10-28 (A5.5) en la tablet Lenovo TB-J706F (Android 11), build release minificada:

| Medida | Resultado |
|---|---|
| Arranque en frío (`am start -S -W`, 10 veces) | mediana 530 ms (500–700 ms; un caso de 1.9 s) |
| Memoria en reposo (PSS) | 107 MB |
| Memoria con 1000 miniaturas vistas (PSS) | 212 MB (141 MB de gráficos) |
| Lista de 1000 archivos | 2.2 % de cuadros tarde; p90 12 ms, p95 14 ms |
| Cuadrícula de miniaturas, primera pasada (decodificando) | 7.7 % tarde; p90 15 ms, p95 18 ms |
| Cuadrícula de miniaturas, ya en caché | 2.3 % tarde; p90 12 ms, p95 15 ms |
- Ninguna pérdida de datos: copias verificadas por tamaño, mover entre volúmenes = copiar + borrar solo si la copia terminó.
- Funcionar en teléfono, tablet, plegable y modo escritorio, en vertical y horizontal, y al redimensionar la ventana sin perder el estado.
- APK sin dependencias propietarias; permisos solo los imprescindibles (almacenamiento, notificaciones, servicio en primer plano).

## 7. Métrica de éxito

El autor lo usa como gestor de archivos principal en la tablet y en el modo escritorio del Pixel durante 2 semanas sin volver a otro.
