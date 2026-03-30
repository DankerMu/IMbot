# Design: p1-relay-workspace-api

## Overview

This change implements the workspace management APIs that allow the Android app to list hosts, manage workspace roots, and browse directories. The relay acts as a gateway, proxying browse requests to the companion for macbook hosts or reading the local filesystem for relay-local.

## Module Structure

```
packages/relay/src/
├── routes/
│   └── hosts.ts           # Extended: GET /hosts, root CRUD, browse
├── companion/
│   └── manager.ts         # Extended: heartbeat processing, status tracking
├── ws/
│   └── hub.ts             # Extended: host_status broadcast
├── db/
│   └── queries.ts         # Extended: hosts + workspace_roots CRUD queries
└── util/
    └── path-security.ts   # New: path validation and traversal prevention
```

## Key Design Decisions

### 1. Two-Path Browse Architecture

Browse requests follow different paths based on host type:

- **macbook host**: Relay sends `browse_directory` command to companion via WS → companion reads fs → returns directory list → relay forwards to client.
- **relay-local host**: Relay reads local filesystem directly using `fs.readdir({ withFileTypes: true })`.

Both paths validate the requested path against workspace roots before execution.
In the current slice, the relay is the policy enforcement point for root containment and traversal rejection. After local filesystem access or companion browse returns, the relay revalidates the canonical path to prevent symlink-based escape from a registered root.

### 2. Path Security as Middleware

Path validation is extracted into a reusable utility:

```typescript
function validateBrowsePath(requestedPath: string, roots: WorkspaceRoot[]): { valid: boolean; error?: string } {
  const resolved = path.resolve(requestedPath);
  if (requestedPath.includes('..')) return { valid: false, error: 'forbidden' };
  const underRoot = roots.some(r => resolved.startsWith(r.path));
  if (!underRoot) return { valid: false, error: 'forbidden' };
  return { valid: true };
}
```

This is called in the route handler BEFORE any filesystem access or companion proxy, and the returned canonical path is checked again after the browse result is produced.

### 3. Heartbeat-Driven Status with Immediate Disconnect Detection

Two mechanisms update host status:
- **Heartbeat**: Every 30s from companion. Updates `last_heartbeat_at`.
- **Disconnect**: WebSocket `close` event. Immediately marks offline.
- **Periodic check**: Every 60s. Marks hosts with stale heartbeat (>90s) as offline.

The combination ensures fast detection (disconnect) and robustness (heartbeat timeout catches half-open connections).

### 4. Host Auto-Registration

When a companion connects with an unknown `host_id`, the relay auto-creates the host record with `type: "macbook"`. This avoids manual host provisioning. The relay-local host is seeded on startup.

### 5. Directories Only -- No File Listing

The browse API returns only directories, never files. This is a deliberate product decision: the browse UI is for selecting a working directory for session creation, not a file manager. This also reduces data transfer and simplifies the UI.

## Database Queries

### Hosts

```sql
-- List all hosts
SELECT id, name, type, status, last_heartbeat_at, created_at, updated_at FROM hosts;

-- Update heartbeat
UPDATE hosts SET status = 'online', last_heartbeat_at = ?, updated_at = datetime('now') WHERE id = ?;

-- Mark stale hosts offline
UPDATE hosts SET status = 'offline', updated_at = datetime('now')
WHERE type = 'macbook' AND status = 'online' AND last_heartbeat_at < datetime('now', '-90 seconds');
```

### Workspace Roots

```sql
-- List by host
SELECT * FROM workspace_roots WHERE host_id = ?;

-- Insert (UNIQUE constraint on host_id+provider+path catches duplicates)
INSERT INTO workspace_roots (id, host_id, provider, path, label) VALUES (?, ?, ?, ?, ?);

-- Delete
DELETE FROM workspace_roots WHERE id = ? AND host_id = ?;
```

## Broadcast Protocol

Host status changes are broadcast as:
```json
{ "type": "host_status", "host_id": "macbook-1", "status": "offline" }
```

This goes to ALL connected Android WebSocket clients, not just those subscribed to a specific session. Host status is a global concern.

## Error Handling

| Scenario | Response |
|----------|----------|
| Host not found | 404 not_found |
| Target directory not found during root add or browse | 404 not_found |
| Path not under root | 403 forbidden |
| Path traversal detected | 403 forbidden |
| Directory not found | 404 not_found |
| Provider not supported on selected host | 400 invalid_request |
| Companion offline (browse/root add for macbook) | 502 host_offline |
| Duplicate root | 409 state_conflict |
| Missing required field | 400 invalid_request |
