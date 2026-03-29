# Tasks: p0-relay-minimal

## Server Bootstrap

- [x] 1.1 Create `packages/relay/src/config.ts`: load config from env/dotenv with defaults per CONFIGURATION.md; validate `RELAY_STATIC_TOKEN` is present (throw and exit if missing); validate `RELAY_PORT` is a valid number
- [x] 1.2 Create `packages/relay/src/index.ts`: initialize Fastify with logger, register `@fastify/cors` and `@fastify/websocket` plugins, call DB init, register all routes, listen on configured host:port, setup SIGTERM/SIGINT graceful shutdown (close server, close WS connections with 1001, close DB)
- [x] 1.3 Create `packages/relay/src/routes/health.ts`: `GET /healthz` (no auth) returning `{ status, uptime, db, companion }` with appropriate online/offline values
- [x] 1.4 Add `packages/relay/package.json` dependencies: `fastify`, `@fastify/cors`, `@fastify/websocket`, `better-sqlite3`, `dotenv`, `uuid`; devDependencies: `@types/better-sqlite3`, `@types/ws`

## SQLite Schema

- [x] 2.1 Create `packages/relay/src/db/init.ts`: open database at `RELAY_DB_PATH` (create parent dir if needed), enable `PRAGMA journal_mode = WAL` and `PRAGMA foreign_keys = ON`, execute DDL for all 7 tables
- [x] 2.2 Implement DDL for `hosts`, `workspace_roots`, `sessions`, `session_events`, `approvals`, `push_subscriptions`, `audit_logs` tables with all columns, CHECK constraints, indexes, and foreign keys per DATA_MODEL.md
- [x] 2.3 Implement relay-local host upsert: `INSERT OR IGNORE INTO hosts (id, name, type, status) VALUES ('relay-local', 'Relay VPS', 'relay_local', 'online')` on startup

## Authentication

- [x] 3.1 Create `packages/relay/src/auth/guard.ts`: Fastify `preHandler` that extracts Bearer token from `Authorization` header, compares with `crypto.timingSafeEqual`, returns 401 `{ error: 'unauthenticated' }` on mismatch
- [x] 3.2 Implement WS auth for Android (`/v1/ws`): check `token` query param or first `auth` message; close with code 4001 on failure
- [x] 3.3 Implement WS auth for companion (`/v1/companion`): check `token` and `host_id` query params; close 4001 for bad token, 4002 for missing host_id

## WebSocket Hub

- [x] 4.1 Create `packages/relay/src/ws/hub.ts`: `WsHub` class with `androidClients` map, `companionClients` map, `subscriptions` map (sessionId → Set<WebSocket>); methods: `addAndroidClient`, `removeAndroidClient`, `subscribe`, `unsubscribe`, `broadcastToSession`, `broadcastHostStatus`, `broadcastAll`
- [x] 4.2 Create `packages/relay/src/ws/android.ts`: handle incoming Android WS messages (subscribe, unsubscribe, ping→pong); dispatch to WsHub
- [x] 4.3 Create `packages/relay/src/ws/companion.ts`: handle incoming companion WS messages (ack, event, heartbeat); on connect → register host online + broadcast; on disconnect → mark host offline + broadcast; dispatch events to orchestrator
- [x] 4.4 Implement ping/pong keepalive: server sends WS-level ping every `RELAY_WS_PING_INTERVAL_MS`; detect dead connections after 2 missed pongs; clean up dead connections from all maps

## Session Orchestrator

- [x] 5.1 Create `packages/relay/src/session/orchestrator.ts`: `create()` method -- validate required fields, check host online, insert session (queued), send `create_session` command, await ack, transition to running on success or failed on error/timeout
- [x] 5.2 Implement `handleEvent()` method: allocate seq via `MAX(seq)+1` query, insert event, update `sessions.last_active_at`, broadcast to subscribers, check if terminal event → trigger `transition()`
- [x] 5.3 Implement `transition()` method: validate transition against `VALID_TRANSITIONS` map, update session status/updated_at/error fields, emit `session_status_changed` event, broadcast status to subscribers
- [x] 5.4 Create `packages/relay/src/session/seq.ts`: `allocateSeq(sessionId)` function using `SELECT COALESCE(MAX(seq), 0) + 1 FROM session_events WHERE session_id = ?`
- [x] 5.5 Create `packages/relay/src/companion/manager.ts`: `CompanionManager` class with `sendCommand(hostId, command)` → returns Promise that resolves on ack or rejects on timeout (`RELAY_COMPANION_TIMEOUT_MS`); `isOnline(hostId)` check

## REST Routes

- [x] 6.1 Create `packages/relay/src/routes/sessions.ts`: `POST /v1/sessions` (calls orchestrator.create, returns 201), `GET /v1/sessions` (query with optional filters: provider, status, host_id, limit, offset), `GET /v1/sessions/:id` (return session or 404)
- [x] 6.2 Create `packages/relay/src/routes/events.ts`: `GET /v1/sessions/:id/events?since_seq=<n>&limit=500` -- return events with seq > since_seq, ordered by seq ASC, with `has_more` flag
