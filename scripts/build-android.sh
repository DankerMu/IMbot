#!/usr/bin/env bash
set -euo pipefail

# shellcheck source=./scripts/_common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"

REPO_ROOT="$(resolve_repo_root)"
readonly REPO_ROOT
readonly ANDROID_DIR="${REPO_ROOT}/packages/android"
readonly APK_PATH="${ANDROID_DIR}/app/build/outputs/apk/debug/app-debug.apk"

DRY_RUN=false

usage() {
  cat <<'EOF'
Usage: ./scripts/build-android.sh [--dry-run]

Build the Android debug APK and print the resulting path.
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

build_android() {
  if [[ "${DRY_RUN}" == "true" ]]; then
    print_command "${ANDROID_DIR}/gradlew" assembleDebug
    return 0
  fi

  print_command "${ANDROID_DIR}/gradlew" assembleDebug
  (
    cd "${ANDROID_DIR}"
    ./gradlew clean assembleDebug
  )
}

main() {
  parse_args "$@"

  [[ -x "${ANDROID_DIR}/gradlew" ]] || die "Missing Android Gradle wrapper: ${ANDROID_DIR}/gradlew"

  log_info "Building Android debug APK"
  build_android

  if [[ "${DRY_RUN}" == "true" ]]; then
    printf '%s\n' "${APK_PATH}"
    exit 0
  fi

  [[ -f "${APK_PATH}" ]] || die "APK not found after build: ${APK_PATH}"

  log_success "APK ready at ${APK_PATH}"
  printf 'Install with: adb install -r %q\n' "${APK_PATH}"
}

main "$@"
