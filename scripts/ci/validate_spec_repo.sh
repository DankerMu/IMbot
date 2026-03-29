#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

check_file() {
  local path="$1"
  [[ -f "$path" ]] || fail "Missing required file: $path"
}

check_change_dir() {
  local dir="$1"
  local change_name
  change_name="$(basename "$dir")"

  check_file "$dir/.openspec.yaml"
  check_file "$dir/README.md"
  check_file "$dir/proposal.md"
  check_file "$dir/design.md"
  check_file "$dir/tasks.md"

  if ! find "$dir/specs" -type f -name spec.md -print -quit | grep -q .; then
    fail "OpenSpec change '$change_name' has no capability spec under specs/**/spec.md"
  fi

  rg -q "$change_name" openspec/README.md || fail "OpenSpec README is missing change '$change_name'"
}

check_markdown_links() {
  local file="$1"
  local base_dir
  base_dir="$(dirname "$file")"

  while IFS= read -r target; do
    local normalized
    normalized="${target%%#*}"

    case "$normalized" in
      "" | "#"* | http://* | https://* | mailto:* )
        continue
        ;;
      /* )
        [[ -e "$normalized" ]] || fail "Broken absolute markdown link in $file -> $normalized"
        ;;
      * )
        if [[ "$normalized" != *.* && "$normalized" != */* ]]; then
          continue
        fi
        [[ -e "$base_dir/$normalized" ]] || fail "Broken markdown link in $file -> $normalized"
        ;;
    esac
  done < <(
    perl -ne 'while (/\[[^][]*\]\(([^)[:space:]]+)(?:\s+"[^"]*")?\)/g) { print "$1\n"; }' "$file"
  )
}

main() {
  local required_root_files=(
    "AGENTS.md"
    ".github/ci-gates.json"
    ".github/dependabot.yml"
    ".github/pull_request_template.md"
    ".github/workflows/implementation-gates.yml"
    ".github/workflows/nightly-system-gates.yml"
    "docs/PRD.md"
    "docs/engineering-spec/SPEC_INDEX.md"
    "docs/engineering-spec/01_Requirements/REQUIREMENTS_MATRIX.md"
    "docs/specs/ui-ux-rd-spec/OVERVIEW.md"
    "openspec/README.md"
    ".github/workflows/repo-governance.yml"
    ".github/workflows/security-supply-chain.yml"
  )

  local change_dir
  local markdown_file
  local i

  for path in "${required_root_files[@]}"; do
    check_file "$path"
  done

  [[ -d openspec/changes ]] || fail "Missing openspec/changes directory"

  for change_dir in openspec/changes/*; do
    [[ -d "$change_dir" ]] || continue
    [[ -f "$change_dir/.openspec.yaml" ]] || continue
    check_change_dir "$change_dir"
  done

  for i in $(seq 1 10); do
    local fr_id
    printf -v fr_id 'FR-%02d' "$i"
    rg -q "$fr_id" docs/engineering-spec/01_Requirements/REQUIREMENTS_MATRIX.md || fail "Requirements matrix missing $fr_id"
    rg -q "$fr_id" docs/specs/ui-ux-rd-spec/00_SourceInventory/COVERAGE.md || fail "UI coverage missing $fr_id"
  done

  for i in $(seq 1 4); do
    local nfr_id
    printf -v nfr_id 'NFR-%02d' "$i"
    rg -q "$nfr_id" docs/PRD.md || fail "PRD missing $nfr_id"
    rg -q "$nfr_id" docs/engineering-spec/01_Requirements/REQUIREMENTS_MATRIX.md || fail "Requirements matrix missing $nfr_id"
  done

  while IFS= read -r -d '' markdown_file; do
    check_markdown_links "$markdown_file"
  done < <(find AGENTS.md docs openspec -type f -name '*.md' -print0)

  echo "Spec repo validation passed."
}

main "$@"
