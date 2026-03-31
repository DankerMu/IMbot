# Proposal: p4-vps-deployment-artifacts

## Why

All 4 phases of code implementation (p0–p3) are complete, but the system cannot run in production because no deployment artifacts exist. The relay needs to run on a RackNerd VPS behind Caddy with pm2, the companion needs a launchd plist on MacBook, and operators need a repeatable, one-command deployment path. Without these, the gap between "code done" and "system running" remains a manual, error-prone process.

## What Changes

| Area | Change |
|------|--------|
| Relay deployment | `.env.example`, pm2 `ecosystem.config.cjs`, `Caddyfile` template, `scripts/deploy-relay.sh` (rsync + pm2 reload), `scripts/setup-vps.sh` (first-time VPS provisioning: Node 22, pm2, Caddy, directories, firewall) |
| Companion deployment | `companion.json.example`, `com.imbot.companion.plist`, `scripts/install-companion.sh` (build, copy, load launchd) |
| Operations | `scripts/health-check.sh` (curl healthz + companion status), `scripts/backup-db.sh` (SQLite backup + rotation) |
| Android | `scripts/build-release.sh` (assembleRelease wrapper), signing guidance in README |
| Token generation | `scripts/generate-token.sh` (openssl rand hex for RELAY_STATIC_TOKEN) |

## Capabilities

- `one-command-vps-setup` — Fresh VPS → running relay in one script
- `one-command-deploy` — Code change → deployed relay in one script
- `companion-launchd` — MacBook companion as persistent launchd service
- `ops-scripts` — Health check, backup, token generation

## Dependencies

- All p0–p3 code complete (done)
- VPS with SSH access (provided by operator at deploy time)
- Domain pointing to VPS IP (for Caddy HTTPS)

## Risks

| Risk | Mitigation |
|------|-----------|
| VPS specs unknown until operator provides credentials | Scripts parameterized; detect OS and validate prerequisites |
| Caddy HTTPS requires domain DNS | Script validates DNS resolution before enabling HTTPS; falls back to HTTP |
| pm2 ecosystem config tightly coupled to directory layout | Use variables for all paths; document layout in README |
| SQLite WAL mode + backup race | Use `.backup` API (online backup), not file copy |

## Non-Goals

- No Docker/container orchestration (single VPS, pm2 is sufficient)
- No CI/CD pipeline for deployment (manual trigger via script)
- No multi-VPS or load balancing
- No Android Play Store publishing workflow
