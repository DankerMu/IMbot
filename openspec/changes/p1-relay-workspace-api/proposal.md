# Proposal: p1-relay-workspace-api

## Why

Users manage workspace roots and browse project directories from the Android app. The relay serves as the gateway for these operations: it either proxies requests to the MacBook companion (for claude/book providers) or handles them locally (for OpenClaw on the relay VPS). Without these APIs, the Android app cannot display available hosts, manage workspace roots, or let users pick a working directory when creating a new session.

## What Changes

1. **Host management API** -- `GET /v1/hosts` returns all registered hosts with their current status (online/offline), supported providers, and last heartbeat timestamp. The relay-local host is always present and always online.
2. **Workspace root CRUD** -- `POST /v1/hosts/:hostId/roots` to add a root, `GET /v1/hosts/:hostId/roots` to list roots, `DELETE /v1/hosts/:hostId/roots/:rootId` to remove a root. Uniqueness constraint on (host_id, provider, path) prevents duplicates. For macbook hosts, adding a root requires the companion to be online to validate the path.
3. **Directory browsing** -- `GET /v1/hosts/:hostId/browse?path=...` returns subdirectories only (no files). The path must be under an existing workspace root. For macbook hosts, the request is proxied to the companion via the `browse_directory` command. For relay-local, the relay reads the local filesystem directly. Path traversal attempts (`../`) are rejected.
4. **Host status tracking** -- Companion heartbeats (every 30s) update host status to online. If no heartbeat is received for 90s, the host is marked offline. Status changes are broadcast to all connected Android WebSocket clients as `host_status` messages.

## Capabilities

- `host-management-api`
- `workspace-root-crud`
- `directory-browsing`
- `host-status-tracking`

## Affected Areas

- `packages/relay/src/routes/hosts.ts` (host listing, root CRUD, browse endpoint)
- `packages/relay/src/companion/manager.ts` (heartbeat handling, status tracking, browse proxy)
- `packages/relay/src/ws/hub.ts` (host_status broadcast to Android clients)
- `packages/relay/src/openclaw/bridge.ts` (local filesystem browse for relay-local host)
- `packages/relay/src/db/` (hosts table queries, workspace_roots CRUD)

## Risks

- Path traversal in directory browsing is a security concern. Both the relay (for local browse) and the companion (for macbook browse) must validate paths against workspace roots and reject `..` components.
- When the companion is offline, root addition for macbook hosts cannot validate that the path exists on the target machine. The API returns `502 host_offline`.
- Filesystem operations on relay-local could expose sensitive directories if workspace roots are misconfigured. Root paths should be validated as absolute paths.

## References

- docs/engineering-spec/02_Technical_Design/API_SPEC.md (GET /v1/hosts, root CRUD, browse endpoint)
- docs/engineering-spec/02_Technical_Design/DATA_MODEL.md (hosts table, workspace_roots table)
- docs/engineering-spec/02_Technical_Design/BUSINESS_LOGIC.md (Section 9: Directory Security Validation)
- docs/engineering-spec/02_Technical_Design/ARCHITECTURE.md (CompanionManager, host status)
