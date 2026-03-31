# Tasks: p4-vps-deployment-artifacts

## 1. Relay Deployment Artifacts

- [ ] 1.1 Create `packages/relay/.env.example` with all RELAY_* vars, comments, and safe defaults
- [ ] 1.2 Create `packages/relay/ecosystem.config.cjs` for pm2: name, script, cwd, env, log paths, restart policy
- [ ] 1.3 Create `deploy/Caddyfile` template with reverse_proxy to localhost:3000 and WebSocket support
- [ ] 1.4 Create `scripts/generate-token.sh`: generate 64-char hex token via openssl

## 2. VPS Setup Script

- [ ] 2.1 Create `scripts/setup-vps.sh`: SSH to VPS, install Node 22 (NodeSource), pm2, Caddy
- [ ] 2.2 Create directory structure: /opt/imbot/{relay,data,backup,logs}
- [ ] 2.3 Configure Caddy with domain parameter (write Caddyfile, reload)
- [ ] 2.4 Configure firewall: allow 80, 443, deny direct 3000
- [ ] 2.5 Generate .env from template if not exists (auto-generate token)
- [ ] 2.6 Make script idempotent (skip already-installed components)

## 3. Relay Deploy Script

- [ ] 3.1 Create `scripts/deploy-relay.sh`: build locally, rsync to VPS, pm2 reload
- [ ] 3.2 Build step: `npm run build:wire && npm run build:relay`
- [ ] 3.3 rsync: dist/, node_modules/ (production), ecosystem.config.cjs, package.json
- [ ] 3.4 Remote: pm2 startOrReload ecosystem.config.cjs
- [ ] 3.5 Post-deploy: health check curl /healthz

## 4. Companion Deployment Artifacts

- [ ] 4.1 Create `packages/companion/companion.json.example` with placeholder values
- [ ] 4.2 Create `deploy/com.imbot.companion.plist` for launchd
- [ ] 4.3 Create `scripts/install-companion.sh`: build, copy, create config from template, load launchd
- [ ] 4.4 Handle upgrade: unload existing plist before reinstall

## 5. Operations Scripts

- [ ] 5.1 Create `scripts/health-check.sh`: curl /healthz, parse JSON, check companion/db status
- [ ] 5.2 Create `scripts/backup-db.sh`: SQLite .backup, 7-day rotation, cron-ready
- [ ] 5.3 Add cron setup guidance in setup-vps.sh (backup + optional log rotation)

## 6. Android Build Helper

- [ ] 6.1 Create `scripts/build-android.sh`: clean + assembleDebug, copy APK to known path
- [ ] 6.2 Print APK path and install command at end

## Verification

- [ ] 7.1 All scripts pass shellcheck
- [ ] 7.2 setup-vps.sh runs idempotently on a fresh Ubuntu 22.04 (verify with --dry-run flag)
- [ ] 7.3 deploy-relay.sh builds and rsyncs successfully (verify with --dry-run flag)
- [ ] 7.4 install-companion.sh creates config and loads plist successfully
- [ ] 7.5 health-check.sh parses valid and invalid /healthz responses
- [ ] 7.6 generate-token.sh produces 64-char hex string
- [ ] 7.7 All scripts have usage help (--help flag)
