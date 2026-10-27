#!/usr/bin/env bash
# Verificación completa antes de cada commit: formato, lint y pruebas de Rust y de Kotlin.
#
# Uso: scripts/check.sh [--fix]   (--fix aplica cargo fmt y ktlintFormat antes de verificar)
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

if [[ "${1:-}" == "--fix" ]]; then
  (cd rust && cargo fmt --all)
  ./gradlew --console=plain -q ktlintFormat
fi

echo "== Rust: fmt, clippy, test"
(cd rust && cargo fmt --all --check && cargo clippy --all-targets --features cli -- -D warnings && cargo test -q)

echo "== Kotlin: ktlint, Android Lint, pruebas JVM"
./gradlew --console=plain -q ktlintCheck lintDebug testDebugUnitTest

echo "check.sh: todo en verde"
