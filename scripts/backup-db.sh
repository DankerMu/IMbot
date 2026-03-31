#!/usr/bin/env bash
set -euo pipefail

# shellcheck source=./scripts/_common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"

DRY_RUN=false
TARGET=""

usage() {
  cat <<'EOF'
Usage: ./scripts/backup-db.sh <user@host> [--dry-run]

Run a remote SQLite .backup on the VPS and prune backups older than 7 days.
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

render_remote_backup_script() {
  cat <<'REMOTE'
set -euo pipefail

backup_dir="/opt/imbot/backup"
db_path="/opt/imbot/data/imbot.db"

if ! command -v sqlite3 >/dev/null 2>&1; then
  printf 'sqlite3 is not installed on the remote host.\n' >&2
  exit 1
fi

if [[ ! -f "${db_path}" ]]; then
  printf 'Database not found at %s\n' "${db_path}" >&2
  exit 1
fi

mkdir -p "${backup_dir}"

backup_path="${backup_dir}/imbot-$(date +%Y%m%d).db"
sqlite3 "${db_path}" ".backup ${backup_path}"
find "${backup_dir}" -type f -name 'imbot-*.db' -mtime +7 -delete

printf '%s\n' "${backup_path}"
REMOTE
}

run_remote_backup() {
  if [[ "${DRY_RUN}" == "true" ]]; then
    print_command ssh "${TARGET}" bash -s
    render_remote_backup_script
    return 0
  fi

  render_remote_backup_script | ssh "${TARGET}" bash -s
}

main() {
  parse_args "$@"

  require_command ssh

  log_info "Running remote SQLite backup on ${TARGET}"
  if [[ "${DRY_RUN}" == "true" ]]; then
    run_remote_backup
    exit 0
  fi

  local backup_path
  backup_path="$(run_remote_backup)"
  log_success "Backup created at ${backup_path}"
}

main "$@"
