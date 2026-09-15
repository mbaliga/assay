#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$ROOT/build/manual"
rm -rf "$BUILD" && mkdir -p "$BUILD"
kotlinc "$ROOT"/src/main/kotlin/dev/assay/*.kt "$ROOT"/src/test/kotlin/dev/assay/*.kt -include-runtime -d "$BUILD/assay-tests.jar"
java -cp "$BUILD/assay-tests.jar" dev.assay.AcceptanceTestKt
