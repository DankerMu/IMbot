#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

main() {
  local gradle_root="packages/android"
  local gradlew="$gradle_root/gradlew"

  if [[ ! -d "$gradle_root" ]]; then
    echo "No Android package found. Skipping Android instrumented gate."
    exit 0
  fi

  [[ -x "$gradlew" ]] || fail "Expected executable Gradle wrapper at $gradlew"

  "$gradlew" \
    --project-dir "$gradle_root" \
    --no-daemon \
    --stacktrace \
    connectedDebugAndroidTest
}

main "$@"
