#!/usr/bin/env bash
set -euo pipefail

# shellcheck source=./scripts/_common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"

REPO_ROOT="$(resolve_repo_root)"
readonly REPO_ROOT
readonly RELAY_ENV_TEMPLATE="${REPO_ROOT}/packages/relay/.env.example"

DRY_RUN=false
TARGET=""
DOMAIN=""

usage() {
  cat <<'EOF'
Usage: ./scripts/setup-vps.sh <user@host> [--domain DOMAIN] [--dry-run]

Provision a Debian/Ubuntu VPS for the IMbot relay:
  - install Node.js 22, pm2, Caddy, rsync, sqlite3, ufw
  - create /opt/imbot/{relay,data,backup,logs}
  - generate /opt/imbot/relay/.env if missing
  - configure Caddy when --domain is provided
  - allow 80/443 and block direct access to 3000 with ufw
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
      --domain)
        [[ $# -ge 2 ]] || die "--domain requires a value"
        DOMAIN="$2"
        shift 2
        ;;
      --domain=*)
        DOMAIN="${1#*=}"
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

  if [[ -n "${DOMAIN}" && ! "${DOMAIN}" =~ ^[A-Za-z0-9.-]+$ ]]; then
    die "Invalid domain: ${DOMAIN}"
  fi
}

render_remote_setup_script() {
  cat <<'REMOTE'
set -euo pipefail

log() {
  printf '[remote] %s\n' "$1"
}

fail() {
  printf '[remote] %s\n' "$1" >&2
  exit 1
}

if [[ ! -r /etc/os-release ]]; then
  fail "Cannot detect OS: /etc/os-release is missing"
fi

# shellcheck disable=SC1091
source /etc/os-release
case "${ID:-}" in
  ubuntu|debian)
    ;;
  *)
    fail "Unsupported OS: ${ID:-unknown}"
    ;;
esac

current_user="$(id -un)"
current_group="$(id -gn)"

log "Installing base packages"
sudo apt-get update
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y \
  ca-certificates \
  curl \
  gnupg \
  openssl \
  rsync \
  sqlite3 \
  ufw

node_major=""
if command -v node >/dev/null 2>&1; then
  node_major="$(node -p "process.versions.node.split('.')[0]")"
fi

if [[ "${node_major}" != "22" ]]; then
  log "Installing Node.js 22"
  curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y nodejs
else
  log "Node.js 22 already installed"
fi

if ! command -v pm2 >/dev/null 2>&1; then
  log "Installing pm2"
  sudo npm install -g pm2
else
  log "pm2 already installed"
fi

if ! command -v caddy >/dev/null 2>&1; then
  log "Installing Caddy"
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y caddy
else
  log "Caddy already installed"
fi

sudo install -d -m 0755 /opt/imbot /opt/imbot/relay /opt/imbot/data /opt/imbot/backup /opt/imbot/logs
sudo chown -R "${current_user}:${current_group}" /opt/imbot

if [[ ! -f /opt/imbot/relay/.env ]]; then
  log "Generating /opt/imbot/relay/.env"
  token="$(openssl rand -hex 32)"
  cat > /opt/imbot/relay/.env <<EOF
# Relay listen interface.
RELAY_HOST=0.0.0.0

# Relay listen port.
RELAY_PORT=3000

# Required: shared 64-character bearer token used by Android and the companion.
RELAY_STATIC_TOKEN=${token}

# SQLite database path for the deployed VPS layout.
RELAY_DB_PATH=/opt/imbot/data/imbot.db

# Optional Firebase push settings. Leave blank to disable FCM push.
RELAY_FCM_PROJECT_ID=
RELAY_FCM_SERVICE_ACCOUNT=/opt/imbot/fcm-sa.json

# OpenClaw gateway on the same VPS.
RELAY_OPENCLAW_URL=ws://127.0.0.1:18789
RELAY_OPENCLAW_TOKEN=

# Relay log verbosity: debug, info, warn, or error.
RELAY_LOG_LEVEL=info

# Companion command acknowledgement timeout in milliseconds.
RELAY_COMPANION_TIMEOUT_MS=30000

# Host heartbeat timing in milliseconds.
RELAY_HEARTBEAT_INTERVAL_MS=60000
RELAY_HEARTBEAT_STALE_MS=90000

# Purge inactive sessions after this many days.
RELAY_PURGE_DAYS=30

# WebSocket ping interval in milliseconds.
RELAY_WS_PING_INTERVAL_MS=30000
EOF
  chmod 0640 /opt/imbot/relay/.env
else
  log "/opt/imbot/relay/.env already exists; leaving it unchanged"
fi

if command -v systemctl >/dev/null 2>&1; then
  sudo systemctl enable --now caddy >/dev/null 2>&1 || true
fi

if [[ -n "${IMBOT_DOMAIN:-}" ]]; then
  log "Writing /etc/caddy/Caddyfile for ${IMBOT_DOMAIN}"
  sudo tee /etc/caddy/Caddyfile >/dev/null <<EOF
${IMBOT_DOMAIN} {
  encode zstd gzip
  reverse_proxy 127.0.0.1:3000
}
EOF

  if command -v systemctl >/dev/null 2>&1; then
    sudo systemctl reload caddy
  else
    sudo caddy reload --config /etc/caddy/Caddyfile
  fi
else
  log "No domain provided; leaving Caddy site configuration unchanged"
fi

log "Configuring firewall"
sudo ufw allow OpenSSH >/dev/null
sudo ufw allow 80/tcp >/dev/null
sudo ufw allow 443/tcp >/dev/null
sudo ufw deny 3000/tcp >/dev/null || true

if ufw status | grep -q '^Status: inactive'; then
  sudo ufw --force enable >/dev/null
fi

log "Remote provisioning complete"
REMOTE
}

run_remote_setup() {
  if [[ "${DRY_RUN}" == "true" ]]; then
    print_command ssh "${TARGET}" env "IMBOT_DOMAIN=${DOMAIN}" bash -s
    render_remote_setup_script
    return 0
  fi

  render_remote_setup_script | ssh "${TARGET}" env "IMBOT_DOMAIN=${DOMAIN}" bash -s
}

sync_env_template() {
  [[ -f "${RELAY_ENV_TEMPLATE}" ]] || die "Missing relay env template: ${RELAY_ENV_TEMPLATE}"

  run_cmd rsync -a "${RELAY_ENV_TEMPLATE}" "${TARGET}:/opt/imbot/relay/.env.example"
}

print_follow_up_guidance() {
  log_info "Suggested follow-up:"
  printf '  %s\n' "Deploy the relay with ./scripts/deploy-relay.sh ${TARGET}"
  printf '  %s\n' "Back up SQLite daily with ./scripts/backup-db.sh ${TARGET}"
  printf '  %s\n' "Optional pm2 log rotation on the VPS: pm2 install pm2-logrotate"
}

main() {
  parse_args "$@"

  require_command ssh
  require_command rsync

  log_info "Provisioning ${TARGET}"
  run_remote_setup
  sync_env_template
  log_success "VPS setup completed for ${TARGET}"

  if [[ -n "${DOMAIN}" ]]; then
    log_success "Caddy configured for https://${DOMAIN}"
  else
    log_info "No domain configured; relay HTTPS/WSS remains pending"
  fi

  print_follow_up_guidance
}

main "$@"
