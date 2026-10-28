# Checklist de accesibilidad (A5.4)

Se repasa antes de cada release. **P** = Pixel con TalkBack; lo estático se comprueba en el código o con pruebas.

## Estático

- [x] Todo ícono o glifo que actúa tiene descripción (`contentDescription`/`semantics`); los decorativos junto a un texto, `null`.
- [x] Ninguna cadena de UI en el código (A5.3).
- [x] Contraste: el core valida sus tokens en los cinco temas (`builtin_themes_parse_and_pass_contrast`) y avisa de los temas de usuario con poco contraste; `ThemeContrastTest` valida los colores que deriva Android (texto secundario sobre la cabecera, pestaña activa) con 4.5:1.
- [x] Objetivos táctiles de 48 dp: botones de ícono, filas con el dedo y entradas de la barra lateral (32 dp solo con ratón).
- [x] La edad se lee completa («hace 2 meses»), no la abreviatura del chip.
- [x] Encabezados de orden: rol de botón y el sentido como estado («Tamaño, orden descendente»), sin leer la flecha.
- [x] Estado seleccionado anunciado en filas, pestañas y en el lugar o volumen actual de la barra lateral.
- [x] Todo se puede usar sin gestos complejos: el arrastre tiene equivalente en menús y botones (Copiar/Mover al otro panel, Cortar y Pegar).

## TalkBack en **P**

- [x] La lista: cada fila dice nombre, tamaño (si se ve), edad y si está seleccionada; deslizar recorre en orden.
- [x] Entrar al modo selección con doble toque mantenido; la barra contextual dice «N seleccionados», «Salir de la selección», «Todo» y «Más opciones».
- [x] La barra lateral (cajón en compacta): lugares, volúmenes con su espacio libre y cuál es el actual.
- [x] Diálogos: enviar a la papelera, conflicto («ya existe»), renombrar y Propiedades leen título, texto y botones, y el foco queda dentro.
- [x] Encabezados de orden y pestañas se anuncian como botones con su estado.

Último pase: 2026-10-28, Pixel 10 con TalkBack (v0.1 en desarrollo).
