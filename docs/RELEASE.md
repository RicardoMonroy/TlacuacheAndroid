# Publicar una versión

1. Sube `versionCode` y `versionName` en `app/build.gradle.kts` y añade la entrada en `CHANGELOG.md`.
2. `scripts/check.sh` en verde.
3. Firma: Gradle lee `~/.config/tlacuache/signing.properties` (o `-Ptlacuache.signing=<ruta>`); la clave y el archivo nunca entran al repo. Sin él, la release sale sin firmar.

   ```properties
   storeFile=/home/<usuario>/.config/tlacuache/release.jks
   storePassword=…
   keyAlias=tlacuache
   keyPassword=…
   ```

   La clave se crea una sola vez (y se respalda: sin ella no se pueden publicar actualizaciones):

   ```bash
   keytool -genkeypair -v -keystore ~/.config/tlacuache/release.jks -alias tlacuache -keyalg RSA -keysize 4096 -validity 10000
   ```

4. `./gradlew assembleRelease` deja un APK por ABI en `app/build/outputs/apk/release/` (`arm64-v8a`, `armeabi-v7a`, `x86_64`).
5. Instálalo en **T** y **P** (`adb install`), etiqueta `vX.Y.Z` y sube los APK a la release de GitHub con la entrada del changelog.
