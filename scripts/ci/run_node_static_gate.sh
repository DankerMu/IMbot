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
  local required_scripts=(
    "lint"
    "typecheck"
    "test:unit"
    "build"
  )

  if [[ ! -f package.json ]]; then
    echo "No root package.json found. Skipping Node static gate."
    exit 0
  fi

  [[ -f package-lock.json ]] || fail "Root package-lock.json is required once Node code exists."

  if [[ -f packages/relay/package.json || -f packages/companion/package.json || -f packages/wire/package.json ]]; then
    required_scripts+=("test:contract")
  fi

  for script_name in "${required_scripts[@]}"; do
    assert_root_script "$script_name"
  done

  npm ci
  npm run lint
  npm run typecheck
  npm run test:unit

  if [[ -f packages/relay/package.json || -f packages/companion/package.json || -f packages/wire/package.json ]]; then
    npm run test:contract
  fi

  npm run build
}

main "$@"
