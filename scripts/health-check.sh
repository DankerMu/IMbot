#!/usr/bin/env bash
set -euo pipefail

# shellcheck source=./scripts/_common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"

DRY_RUN=false
RELAY_URL=""
TOKEN=""

usage() {
  cat <<'EOF'
Usage: ./scripts/health-check.sh <relay-url> <token> [--dry-run]

Query /healthz and print the parsed relay status.
EOF
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --help|-h)
        usage
        exit 0
        ;;
      --dry-run)
        DRY_RUN=true
        shift
        ;;
      --*)
        die "Unknown option: $1"
        ;;
      *)
        if [[ -z "${RELAY_URL}" ]]; then
          RELAY_URL="$1"
        elif [[ -z "${TOKEN}" ]]; then
          TOKEN="$1"
        else
          die "Unexpected argument: $1"
        fi
        shift
        ;;
    esac
  done

  if [[ -z "${RELAY_URL}" || -z "${TOKEN}" ]]; then
    usage >&2
    exit 1
  fi
}

normalize_url() {
  printf '%s\n' "${1%/}"
}

parse_health_response() {
  local response="$1"

  printf '%s' "${response}" | node -e '
    const fs = require("node:fs");
    const input = fs.readFileSync(0, "utf8");
    let parsed;

    try {
      parsed = JSON.parse(input);
    } catch (error) {
      console.error("Invalid JSON response: " + error.message);
      process.exit(1);
    }

    const required = ["status", "db", "companion", "openclaw", "uptime"];
    for (const key of required) {
      if (!(key in parsed)) {
        console.error("Missing field in health response: " + key);
        process.exit(1);
      }
    }

    process.stdout.write(
      [
        String(parsed.status),
        String(parsed.db),
        String(parsed.companion),
        String(parsed.openclaw),
        String(parsed.uptime)
      ].join("\t")
    );
  '
}

main() {
  parse_args "$@"

  require_command curl
  require_command node

  local relay_url health_url
  relay_url="$(normalize_url "${RELAY_URL}")"
  health_url="${relay_url}/healthz"

  if [[ "${DRY_RUN}" == "true" ]]; then
    print_command curl -fsS -H "Authorization: Bearer ${TOKEN}" "${health_url}"
    exit 0
  fi

  local response parsed status db companion openclaw uptime
  response="$(curl -fsS -H "Authorization: Bearer ${TOKEN}" "${health_url}")"
  parsed="$(parse_health_response "${response}")"
  IFS=$'\t' read -r status db companion openclaw uptime <<<"${parsed}"

  printf 'status=%s db=%s companion=%s openclaw=%s uptime=%s\n' \
    "${status}" "${db}" "${companion}" "${openclaw}" "${uptime}"

  if [[ "${status}" != "ok" || "${db}" != "ok" ]]; then
    die "Relay health check failed"
  fi

  if [[ "${companion}" != "online" ]]; then
    log_info "Companion is ${companion}"
  fi

  if [[ "${openclaw}" != "online" ]]; then
    log_info "OpenClaw is ${openclaw}"
  fi

  log_success "Relay health check passed"
}

main "$@"
