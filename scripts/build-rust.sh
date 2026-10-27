#!/usr/bin/env bash
# Compila tlacuache-ffi para Android con cargo-ndk y genera los bindings Kotlin de UniFFI.
#
# Uso: scripts/build-rust.sh [debug|release] [dir-jniLibs] [dir-kotlin] [abi...]
# Gradle lo llama desde la tarea buildRust<Variante>; a mano sirve con los valores por defecto.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
profile="${1:-release}"
jni_out="${2:-$root/app/build/rustJniLibs}"
kotlin_out="${3:-$root/app/build/generated/uniffi}"
shift $(( $# < 3 ? $# : 3 ))
abis=("$@")
[[ ${#abis[@]} -eq 0 ]] && abis=(arm64-v8a armeabi-v7a x86_64)

case "$profile" in
  debug) release_flag=() ;;
  release) release_flag=(--release) ;;
  *) echo "perfil desconocido: $profile (debug|release)" >&2; exit 2 ;;
esac

# NDK: ANDROID_NDK_HOME (Gradle lo pasa) o la versión fijada dentro del SDK.
if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
  sdk="${ANDROID_HOME:-$(sed -n 's/^sdk\.dir=//p' "$root/local.properties" 2>/dev/null || true)}"
  export ANDROID_NDK_HOME="$sdk/ndk/28.2.13676358"
fi
[[ -d "$ANDROID_NDK_HOME" ]] || { echo "NDK no encontrado en $ANDROID_NDK_HOME" >&2; exit 1; }

cd "$root/rust"

targets=()
for abi in "${abis[@]}"; do targets+=(-t "$abi"); done
cargo ndk "${targets[@]}" --platform 29 -o "$jni_out" build -p tlacuache-ffi "${release_flag[@]}"

# Los bindings salen de la librería del host: las de Android en release van sin
# símbolos y sin ellos no hay metadatos de UniFFI (ARCHITECTURE §2).
cargo build -p tlacuache-ffi
rm -rf "$kotlin_out"
cargo run -q --features cli --bin uniffi-bindgen -- generate \
  --library target/debug/libtlacuache_ffi.so --language kotlin --no-format --out-dir "$kotlin_out"
