# Tlacuache para Android

Gestor de archivos para Android pensado para teléfono, tablet y **modo escritorio** (Samsung DeX y el escritorio de Pixel): uno o dos paneles, copiar y mover entre paneles con un toque o una tecla, edad de archivos con color y los temas de [Tlacuache para Linux](https://github.com/RicardoMonroy/Tlacuache). Sin root, sin anuncios y sin rastreadores.

> En desarrollo. Ver [docs/ROADMAP.md](docs/ROADMAP.md).

## Compilar

Requisitos: JDK 17, Android SDK (plataforma 37, build-tools 36) con `sdk.dir` en `local.properties` o `ANDROID_HOME`.

```bash
./gradlew assembleDebug
./gradlew installDebug   # con un dispositivo conectado por adb
```

## Documentación

- [docs/PRD.md](docs/PRD.md): qué construimos y qué no.
- [docs/UI_SPEC.md](docs/UI_SPEC.md): layouts, gestos, atajos y modo escritorio.
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): módulos, puente con el core en Rust, almacenamiento y operaciones.
- [docs/DECISIONS.md](docs/DECISIONS.md): decisiones de arquitectura.

## Licencia

GPL-3.0-or-later. Ver [LICENSE](LICENSE).
