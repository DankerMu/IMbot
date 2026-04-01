#!/usr/bin/env bash
set -euo pipefail

# shellcheck source=./scripts/_common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"

REPO_ROOT="$(resolve_repo_root)"
readonly REPO_ROOT
readonly COMPANION_DIR="${REPO_ROOT}/packages/companion"
readonly WIRE_DIR="${REPO_ROOT}/packages/wire"
readonly COMPANION_TEMPLATE="${COMPANION_DIR}/companion.json.example"
readonly PLIST_TEMPLATE="${REPO_ROOT}/deploy/com.imbot.companion.plist"

DRY_RUN=false

usage() {
  cat <<'EOF'
Usage: ./scripts/install-companion.sh [--dry-run]

Build and install the IMbot companion into ~/.imbot, then load launchd.
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
        die "Unexpected argument: $1"
        ;;
    esac
  done
}

resolve_node_modules_source() {
  if [[ -d "${COMPANION_DIR}/node_modules" ]]; then
    printf '%s\n' "${COMPANION_DIR}/node_modules"
    return 0
  fi

  if [[ -d "${REPO_ROOT}/node_modules" ]]; then
    log_info "packages/companion/node_modules is missing; using repo root node_modules with symlink dereferencing" >&2
    printf '%s\n' "${REPO_ROOT}/node_modules"
    return 0
  fi

  die "No node_modules directory found. Run npm install first."
}

build_locally() {
  run_cmd npm run build:wire
  run_cmd npm run build:companion
}

create_directories() {
  run_cmd mkdir -p "${HOME}/.imbot/companion" "${HOME}/.imbot/logs" "${HOME}/Library/LaunchAgents"
}

sync_companion_files() {
  local node_modules_source="$1"
  local install_root="${HOME}/.imbot/companion"

  run_cmd rsync -a --delete "${COMPANION_DIR}/dist/" "${install_root}/dist/"
  run_cmd rsync -aL --delete "${node_modules_source}/" "${install_root}/node_modules/"
  run_cmd mkdir -p "${install_root}/node_modules/@imbot/wire"
  run_cmd rsync -a "${WIRE_DIR}/package.json" "${install_root}/node_modules/@imbot/wire/package.json"
  run_cmd rsync -a --delete "${WIRE_DIR}/dist/" "${install_root}/node_modules/@imbot/wire/dist/"
}

ensure_config_file() {
  local config_path="${HOME}/.imbot/companion.json"

  [[ -f "${COMPANION_TEMPLATE}" ]] || die "Missing companion template: ${COMPANION_TEMPLATE}"

  if [[ -f "${config_path}" ]]; then
    log_info "${config_path} already exists; leaving it unchanged"
    return 0
  fi

  run_cmd cp "${COMPANION_TEMPLATE}" "${config_path}"
}

render_plist() {
  local node_bin="$1"

  python3 - "${PLIST_TEMPLATE}" "${HOME}" "${node_bin}" <<'PY'
import html
import os
import sys

template_path, home_dir, node_bin = sys.argv[1:4]

with open(template_path, "r", encoding="utf-8") as handle:
    template = handle.read()

env_keys = [
    "PATH",
    "USER",
    "LOGNAME",
    "SHELL",
    "LANG",
    "TMPDIR",
    "SSH_AUTH_SOCK",
    "TERM",
    "TERM_PROGRAM",
    "COLORTERM",
    "XPC_FLAGS",
    "__CF_USER_TEXT_ENCODING",
    "ALL_PROXY",
    "HTTP_PROXY",
    "HTTPS_PROXY",
    "http_proxy",
    "https_proxy",
    "NO_PROXY",
    "no_proxy",
    "NODE_EXTRA_CA_CERTS"
]

extra_lines: list[str] = []
for key in env_keys:
    value = os.environ.get(key, "").strip()
    if not value:
        continue

    extra_lines.append(f"    <key>{html.escape(key)}</key>")
    extra_lines.append(f"    <string>{html.escape(value)}</string>")

rendered = (
    template.replace("__HOME__", html.escape(home_dir))
    .replace("__NODE_BIN__", html.escape(node_bin))
    .replace("__EXTRA_ENV_VARS__", "\n".join(extra_lines))
)
sys.stdout.write(rendered)
PY
}

install_launch_agent() {
  local plist_path="${HOME}/Library/LaunchAgents/com.imbot.companion.plist"
  local node_bin

  [[ -f "${PLIST_TEMPLATE}" ]] || die "Missing plist template: ${PLIST_TEMPLATE}"
  node_bin="$(command -v node)"

  if [[ -f "${plist_path}" ]]; then
    run_allow_fail launchctl unload -w "${plist_path}"
  fi

  if [[ "${DRY_RUN}" == "true" ]]; then
    log_info "Dry run: rendered plist would be written to ${plist_path}"
    render_plist "${node_bin}"
  else
    render_plist "${node_bin}" > "${plist_path}"
    chmod 0644 "${plist_path}"
  fi

  run_cmd launchctl load -w "${plist_path}"
}

main() {
  parse_args "$@"

  [[ "$(uname -s)" == "Darwin" ]] || die "install-companion.sh must be run on macOS"

  require_command npm
  require_command rsync
  require_command node
  require_command python3
  require_command launchctl

  local node_modules_source
  node_modules_source="$(resolve_node_modules_source)"

  log_info "Building companion locally"
  build_locally

  log_info "Preparing ~/.imbot"
  create_directories

  log_info "Installing companion files"
  sync_companion_files "${node_modules_source}"
  ensure_config_file

  log_info "Installing launchd plist"
  install_launch_agent

  log_success "Companion installed in ${HOME}/.imbot/companion"
}

main "$@"
