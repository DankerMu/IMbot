#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

assert_root_script() {
  local script_name="$1"

  # shellcheck disable=SC2016
  node -e '
    const fs = require("fs");
    const pkg = JSON.parse(fs.readFileSync("package.json", "utf8"));
    const scripts = pkg.scripts || {};
    const scriptName = process.argv[1];
    if (!scripts[scriptName]) {
      console.error(`Missing required npm script: ${scriptName}`);
      process.exit(1);
    }
  ' "$script_name"
}

main() {
  if [[ ! -f package.json ]]; then
    echo "No root package.json found. Skipping Node integration gate."
    exit 0
  fi

  [[ -f package-lock.json ]] || fail "Root package-lock.json is required once Node code exists."
  assert_root_script "test:integration"

  npm ci
  npm run test:integration
}

main "$@"
