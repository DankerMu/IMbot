#!/usr/bin/env bash
set -euo pipefail

# shellcheck source=./scripts/_common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"

usage() {
  cat <<'EOF'
Usage: ./scripts/generate-token.sh

Generate a 64-character hex token for RELAY_STATIC_TOKEN.
EOF
}

parse_args() {
  if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
    usage
    exit 0
  fi

  if [[ $# -gt 0 ]]; then
    die "Unknown argument: $1"
  fi
}

main() {
  parse_args "$@"
  require_command openssl

  local token
  token="$(openssl rand -hex 32)"

  [[ "${token}" =~ ^[0-9a-f]{64}$ ]] || die "Failed to generate a 64-character hex token"
  printf '%s\n' "${token}"
}

main "$@"
