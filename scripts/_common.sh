#!/usr/bin/env bash
set -euo pipefail

if [[ -t 1 ]]; then
  readonly COLOR_RED=$'\033[31m'
  readonly COLOR_GREEN=$'\033[32m'
  readonly COLOR_YELLOW=$'\033[33m'
  readonly COLOR_RESET=$'\033[0m'
else
  readonly COLOR_RED=""
  readonly COLOR_GREEN=""
  readonly COLOR_YELLOW=""
  readonly COLOR_RESET=""
fi

log_info() {
  printf '%s%s%s\n' "${COLOR_YELLOW}" "$*" "${COLOR_RESET}"
}

log_success() {
  printf '%s%s%s\n' "${COLOR_GREEN}" "$*" "${COLOR_RESET}"
}

log_error() {
  printf '%s%s%s\n' "${COLOR_RED}" "$*" "${COLOR_RESET}" >&2
}

die() {
  log_error "$*"
  exit 1
}

print_command() {
  local arg

  printf '%s+' "${COLOR_YELLOW}"
  for arg in "$@"; do
    printf ' %q' "${arg}"
  done
  printf '%s\n' "${COLOR_RESET}"
}

run_cmd() {
  if [[ "${DRY_RUN:-false}" == "true" ]]; then
    print_command "$@"
    return 0
  fi

  print_command "$@"
  "$@"
}

run_allow_fail() {
  if [[ "${DRY_RUN:-false}" == "true" ]]; then
    print_command "$@"
    return 0
  fi

  print_command "$@"
  "$@" || return 0
}

require_command() {
  local command_name="$1"

  command -v "${command_name}" >/dev/null 2>&1 || die "Missing required command: ${command_name}"
}

resolve_repo_root() {
  local script_dir

  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  cd "${script_dir}/.." >/dev/null 2>&1 && pwd
}

escape_sed_replacement() {
  printf '%s' "$1" | sed -e 's/[\/&]/\\&/g'
}
