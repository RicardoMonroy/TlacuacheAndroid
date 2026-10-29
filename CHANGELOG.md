# Cambios

Formato de [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versiones con [SemVer](https://semver.org/lang/es/).

## [0.1.0] — 2026-10-28

Primera versión: el gestor de archivos de Tlacuache para teléfono, tablet y modo escritorio, con la lógica de `tlacuache-core` v0.2.3.

### Añadido

- **Paneles**: uno o dos paneles según el ancho de la ventana, con divisor arrastrable, alternador A/B en pantallas angostas y pestañas por panel (duplicar, cerrar las demás, reordenar o pasar al otro panel arrastrando, clic central para cerrar).
- **Navegación**: barra de ruta con migas y modo editable con autocompletado, historial atrás/adelante, filtro rápido al escribir, ocultos con Ctrl+H, orden por nombre, tamaño, fecha o edad, vista de lista y de íconos con miniaturas de fotos, videos y PDF.
- **Barra lateral**: lugares estándar, favoritos con grupos y volúmenes (interno, SD y USB) con su espacio libre; cajón, riel o permanente según el ancho.
- **Operaciones**: copiar y mover al otro panel, cortar/copiar/pegar, renombrar, nueva carpeta y archivo, en una cola en segundo plano con notificación, velocidad y tiempo restante, conflictos (reemplazar, combinar, omitir, conservar ambos) y Deshacer.
- **Papelera propia** que no borra de verdad hasta vaciarla o pasados 30 días; borrado permanente siempre con confirmación. Las carpetas del sistema no se mueven ni se borran.
- **Arrastrar y soltar** entre paneles, carpetas, la barra lateral, otras ventanas de Tlacuache y otras apps.
- **Modo escritorio**: varias ventanas, atajos de teclado (lista con Meta+/), ratón con clic, Ctrl/Shift+clic, doble clic, menú contextual, botones laterales y filas compactas.
- **Temas**: los cinco de Tlacuache para Linux y los del usuario, en vivo, con opción de seguir al sistema.
- **Preferencias** y exportar/importar `config.toml`, el mismo archivo que usa Tlacuache en Linux.
- **Sesión**: al reabrir vuelven los paneles, pestañas, vista y orden.
- **Accesibilidad**: TalkBack lee filas, edades, orden, selección y diálogos; checklist en `docs/ACCESSIBILITY.md`.

[0.1.0]: https://github.com/RicardoMonroy/TlacuacheAndroid/releases/tag/v0.1.0
