# Tasks: p1-relay-workspace-api

## 1. Host Management API

- [x] 1.1 Implement `GET /v1/hosts` route in `packages/relay/src/routes/hosts.ts`
- [x] 1.2 Query `hosts` table and join with provider information from workspace_roots or heartbeat data
- [x] 1.3 Include `status`, `last_heartbeat_at`, and `providers` array in each host response object
- [x] 1.4 Ensure relay-local host is always present (seeded on startup via `INSERT OR IGNORE`)
- [x] 1.5 Write unit tests: response includes relay-local, macbook shows correct status, providers list is correct

## 2. Workspace Root CRUD

- [x] 2.1 Implement `POST /v1/hosts/:hostId/roots` route with request validation (provider, path required)
- [x] 2.2 Validate hostId exists (return 404 if not)
- [x] 2.3 Validate path is non-empty and absolute
- [x] 2.4 INSERT with UNIQUE constraint handling: catch `SQLITE_CONSTRAINT_UNIQUE` and return 409
- [x] 2.5 Generate UUID for root `id`, auto-set `created_at`
- [x] 2.6 Implement `GET /v1/hosts/:hostId/roots` route returning all roots for the host
- [x] 2.7 Validate hostId exists (return 404 if not)
- [x] 2.8 Implement `DELETE /v1/hosts/:hostId/roots/:rootId` route
- [x] 2.9 Validate root exists and belongs to the specified host (return 404 if not)
- [x] 2.10 Return 204 on successful delete
- [x] 2.11 Add audit log calls for root.add and root.remove
- [x] 2.12 Write unit tests: add success, add duplicate 409, list by host, delete 204, delete 404, different providers same path allowed

## 3. Directory Browsing

- [x] 3.1 Create `packages/relay/src/util/path-security.ts` with `validateBrowsePath(path, roots)` function
- [x] 3.2 Implement path traversal detection: reject paths containing `..` components
- [x] 3.3 Implement root containment check: resolved path must start with at least one root's path
- [x] 3.4 Implement `GET /v1/hosts/:hostId/browse` route with `path` query param validation
- [x] 3.5 For macbook hosts: proxy to companion via `browse_directory` command, await ack, return result
- [x] 3.6 For relay-local host: read local filesystem using `fs.readdir(path, { withFileTypes: true })`
- [x] 3.7 Filter results to directories only (exclude files, symlinks to files)
- [x] 3.8 Return 403 for path not under any root or path traversal
- [x] 3.9 Return 404 for non-existent directory
- [x] 3.10 Return 502 for macbook host when companion is offline
- [x] 3.11 Write unit tests: valid browse returns dirs only, path not under root 403, traversal 403, non-existent 404, offline 502, empty dir returns empty array

## 4. Host Status Tracking

- [x] 4.1 Implement heartbeat handler in `CompanionManager`: update `hosts.status` to `online`, refresh `last_heartbeat_at`
- [x] 4.2 Auto-register unknown host_id when the companion first connects, then transition online on the first heartbeat
- [x] 4.3 On companion WebSocket `close` event: immediately set host status to `offline`
- [x] 4.4 Implement periodic stale heartbeat check (setInterval 60s): mark hosts with `last_heartbeat_at` > 90s ago as `offline`
- [x] 4.5 Skip relay-local host in stale heartbeat check (always online)
- [x] 4.6 On status change (online→offline or offline→online): broadcast `host_status` message to all Android WS clients
- [x] 4.7 On companion disconnect → offline: transition all running sessions on that host to `failed` with `host_disconnected`
- [x] 4.8 Add audit log calls for host.online and host.offline
- [x] 4.9 Write unit tests: heartbeat updates status and timestamp, 90s timeout marks offline, disconnect marks offline immediately, status broadcast fires on change only, relay-local never marked offline
