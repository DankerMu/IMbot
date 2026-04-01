#!/usr/bin/env bash
set -euo pipefail

# shellcheck source=./scripts/_common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"

REPO_ROOT="$(resolve_repo_root)"
readonly REPO_ROOT
readonly RELAY_DIR="${REPO_ROOT}/packages/relay"
readonly WIRE_DIR="${REPO_ROOT}/packages/wire"

DRY_RUN=false
TARGET=""

usage() {
  cat <<'EOF'
Usage: ./scripts/deploy-relay.sh <user@host> [--dry-run]

Build the relay locally and deploy it to /opt/imbot/relay on the VPS.
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
        if [[ -n "${TARGET}" ]]; then
          die "Unexpected argument: $1"
        fi
        TARGET="$1"
        shift
        ;;
    esac
  done

  [[ -n "${TARGET}" ]] || {
    usage >&2
    exit 1
  }
}

resolve_node_modules_source() {
  if [[ -d "${RELAY_DIR}/node_modules" ]]; then
    printf '%s\n' "${RELAY_DIR}/node_modules"
    return 0
  fi

  if [[ -d "${REPO_ROOT}/node_modules" ]]; then
    log_info "packages/relay/node_modules is missing; using repo root node_modules with symlink dereferencing" >&2
    printf '%s\n' "${REPO_ROOT}/node_modules"
    return 0
  fi

  die "No node_modules directory found. Run npm install first."
}

build_locally() {
  run_cmd npm run build:wire
  run_cmd npm run build:relay
}

prepare_remote_layout() {
  if [[ "${DRY_RUN}" == "true" ]]; then
    print_command ssh "${TARGET}" bash -s
    cat <<'REMOTE'
set -euo pipefail
mkdir -p /opt/imbot/relay /opt/imbot/logs /opt/imbot/relay/node_modules/@imbot/wire
REMOTE
    return 0
  fi

  ssh "${TARGET}" bash -s <<'REMOTE'
set -euo pipefail
mkdir -p /opt/imbot/relay /opt/imbot/logs /opt/imbot/relay/node_modules/@imbot/wire
REMOTE
}

sync_artifacts() {
  local node_modules_source="$1"

  run_cmd rsync -a --delete "${RELAY_DIR}/dist/" "${TARGET}:/opt/imbot/relay/dist/"
  run_cmd rsync -aL --delete "${node_modules_source}/" "${TARGET}:/opt/imbot/relay/node_modules/"
  run_cmd rsync -a "${RELAY_DIR}/package.json" "${RELAY_DIR}/ecosystem.config.cjs" "${TARGET}:/opt/imbot/relay/"
  run_cmd rsync -a "${WIRE_DIR}/package.json" "${TARGET}:/opt/imbot/relay/node_modules/@imbot/wire/package.json"
  run_cmd rsync -a --delete "${WIRE_DIR}/dist/" "${TARGET}:/opt/imbot/relay/node_modules/@imbot/wire/dist/"
}

rebuild_remote_modules() {
  if [[ "${DRY_RUN}" == "true" ]]; then
    print_command ssh "${TARGET}" bash -s
    cat <<'REMOTE'
set -euo pipefail
cd /opt/imbot/relay
npm rebuild --omit=dev
REMOTE
    return 0
  fi

  ssh "${TARGET}" bash -s <<'REMOTE'
set -euo pipefail
cd /opt/imbot/relay
npm rebuild --omit=dev
REMOTE
}

run_remote_restart() {
  if [[ "${DRY_RUN}" == "true" ]]; then
    print_command ssh "${TARGET}" bash -s
    cat <<'REMOTE'
set -euo pipefail
cd /opt/imbot/relay

if [[ ! -f .env ]]; then
  printf 'Missing /opt/imbot/relay/.env; run scripts/setup-vps.sh first.\n' >&2
  exit 1
fi

pm2 startOrReload ecosystem.config.cjs --update-env
for attempt in {1..10}; do
  if curl -sf --connect-timeout 2 --max-time 5 http://localhost:3000/healthz; then
    exit 0
  fi
  sleep 1
done

printf 'Relay /healthz did not return success after restart.\n' >&2
exit 1
REMOTE
    return 0
  fi

  ssh "${TARGET}" bash -s <<'REMOTE'
set -euo pipefail
cd /opt/imbot/relay

if [[ ! -f .env ]]; then
  printf 'Missing /opt/imbot/relay/.env; run scripts/setup-vps.sh first.\n' >&2
  exit 1
fi

pm2 startOrReload ecosystem.config.cjs --update-env
for attempt in {1..10}; do
  if curl -sf --connect-timeout 2 --max-time 5 http://localhost:3000/healthz; then
    exit 0
  fi
  sleep 1
done

printf 'Relay /healthz did not return success after restart.\n' >&2
exit 1
REMOTE
}

main() {
  parse_args "$@"

  require_command npm
  require_command rsync
  require_command ssh

  local node_modules_source
  node_modules_source="$(resolve_node_modules_source)"

  log_info "Building relay locally"
  build_locally

  log_info "Preparing remote relay directory"
  prepare_remote_layout

  log_info "Syncing relay artifacts to ${TARGET}"
  sync_artifacts "${node_modules_source}"

  log_info "Rebuilding remote native modules"
  rebuild_remote_modules

  log_info "Restarting relay and checking /healthz"
  run_remote_restart

  log_success "Relay deployment completed for ${TARGET}"
}

main "$@"
