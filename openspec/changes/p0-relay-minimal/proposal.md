# Proposal: p0-relay-minimal

## Why

The relay server is the central hub of the IMbot system. Every interaction between Android and the AI providers flows through it. A minimal but functional relay is the foundation for all subsequent work -- without it, nothing else can be tested end-to-end. This change delivers the skeleton: accept connections, authenticate, store sessions/events in SQLite, expose basic REST endpoints, and provide a WebSocket hub for Android and companion connections.

## What Changes

1. **Fastify server bootstrap** -- HTTP server on configurable port, dotenv config loading, graceful shutdown, CORS for development.
2. **SQLite schema** -- All 7 tables (hosts, workspace_roots, sessions, session_events, approvals, push_subscriptions, audit_logs) via better-sqlite3 with WAL mode.
3. **Static bearer token auth** -- Fastify preHandler guard on all REST routes and WebSocket connections.
4. **WebSocket hub** -- Separate Android WS and companion WS endpoints with subscribe/unsubscribe, ping/pong keepalive, and event broadcasting.
5. **Session orchestrator (basic)** -- Create session (insert DB + send command to companion), receive events (allocate seq + store + broadcast), basic status transitions.

## Capabilities

- `relay-server-bootstrap`
- `sqlite-schema`
- `relay-auth`
- `relay-ws-hub`
- `session-orchestrator-basic`

## Affected Areas

- `packages/relay/src/` (all new files)
- `packages/relay/package.json` (dependencies: fastify, better-sqlite3, dotenv, @fastify/websocket, @fastify/cors, uuid)

## Risks

- better-sqlite3 requires native compilation -- may need `node-gyp` setup on deployment VPS.
- WAL mode SQLite file creates `-wal` and `-shm` sidecar files that must be included in backup procedures.
- Single-process architecture means a crash loses all WS connections (acceptable for single-user MVP).

## References

- docs/engineering-spec/02_Technical_Design/ARCHITECTURE.md
- docs/engineering-spec/02_Technical_Design/DATA_MODEL.md
- docs/engineering-spec/02_Technical_Design/API_SPEC.md
- docs/engineering-spec/02_Technical_Design/BUSINESS_LOGIC.md
- docs/engineering-spec/03_Security/AUTH_DESIGN.md
- docs/engineering-spec/04_Operations/CONFIGURATION.md
