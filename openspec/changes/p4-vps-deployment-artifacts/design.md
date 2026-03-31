# Design: p4-vps-deployment-artifacts

## Architecture

```
Operator MacBook                          RackNerd VPS
─────────────────                         ──────────────────────
scripts/deploy-relay.sh ──── rsync ────→  /opt/imbot/relay/
                              SSH          ├── dist/
                                           ├── node_modules/
                                           ├── .env
                                           └── ecosystem.config.cjs
                                                    │
                                              pm2 start
                                                    │
                                              localhost:3000
                                                    │
                                              Caddy reverse proxy
                                                    │
                                              https://relay.example.com
                                                    ↕ WSS
companion (launchd) ←──── WSS ────────→  relay WS hub
Android app         ←──── HTTPS/WSS ──→  relay REST + WS
```

## Directory Layout on VPS

```
/opt/imbot/
├── relay/
│   ├── dist/              # compiled JS
│   ├── node_modules/      # production deps
│   ├── .env               # from .env.example
│   └── ecosystem.config.cjs
├── data/
│   └── imbot.db           # SQLite (auto-created)
├── backup/
│   └── imbot-YYYYMMDD.db  # daily backups
├── fcm-sa.json            # Firebase service account (optional)
└── logs/                  # pm2 logs
```

## Key Decisions

### 1. pm2 over systemd
pm2 provides log rotation, restart on crash, zero-downtime reload, and `pm2 deploy` support — all without writing unit files. Single process, no cluster needed for SQLite.

### 2. Caddy over nginx
Auto-HTTPS via Let's Encrypt with zero config. WebSocket proxying works out of the box. Single binary, no `certbot` cron.

### 3. rsync-based deploy
Simple, fast, incremental. Build locally (or in CI), rsync dist + node_modules to VPS, pm2 reload. No Docker registry needed.

### 4. Scripts are idempotent
`setup-vps.sh` can be re-run safely. `deploy-relay.sh` is a no-op if nothing changed. `install-companion.sh` unloads before reloading.

## Script Parameters

### setup-vps.sh
```
Usage: ./scripts/setup-vps.sh <user@host> [--domain relay.example.com]
  - Installs Node 22, pm2, Caddy
  - Creates /opt/imbot directory structure
  - Configures Caddy with domain (or skip if no domain)
  - Opens firewall ports 80, 443
  - Generates RELAY_STATIC_TOKEN if .env doesn't exist
```

### deploy-relay.sh
```
Usage: ./scripts/deploy-relay.sh <user@host>
  - Builds relay locally (npm run build:wire && npm run build:relay)
  - rsync dist/ + production node_modules to VPS
  - Copies ecosystem.config.cjs
  - pm2 reload imbot-relay (or pm2 start if first time)
```

### install-companion.sh
```
Usage: ./scripts/install-companion.sh
  - Builds companion locally
  - Copies to ~/.imbot/companion/
  - Creates companion.json from template if not exists
  - Installs launchd plist
  - launchctl load
```
