# Capability: sqlite-schema

## ADDED Requirements

### Requirement: Database Initialization

The relay server SHALL create and initialize a SQLite database using better-sqlite3 at the configured `RELAY_DB_PATH`, with WAL journal mode enabled.

#### Scenario: fresh database is created on first startup

WHEN the relay server starts and `RELAY_DB_PATH` points to a non-existent file
THEN the database file is created at the specified path
AND WAL journal mode is enabled (`PRAGMA journal_mode = WAL`)
AND foreign keys are enabled (`PRAGMA foreign_keys = ON`)
AND all 7 tables are created

#### Scenario: existing database is reused

WHEN the relay server starts and the database file already exists with all tables
THEN no tables are dropped or recreated
AND the server starts without errors

#### Scenario: parent directory is created if missing

WHEN `RELAY_DB_PATH=./data/imbot.db` and the `data/` directory does not exist
THEN the directory is created before opening the database

#### Scenario: relay-local host is upserted on startup

WHEN the database is initialized
THEN a host record with `id='relay-local'`, `name='Relay VPS'`, `type='relay_local'`, `status='online'` exists
AND if the record already existed, it is not duplicated

---

### Requirement: Hosts Table

The `hosts` table SHALL store registered host machines with their connection status.

#### Scenario: hosts table schema

WHEN the `hosts` table is described
THEN it has columns:
- `id TEXT PRIMARY KEY`
- `name TEXT NOT NULL`
- `type TEXT NOT NULL` with CHECK constraint `IN ('macbook', 'relay_local')`
- `status TEXT NOT NULL DEFAULT 'offline'` with CHECK constraint `IN ('online', 'offline')`
- `last_heartbeat_at TEXT` (nullable, ISO 8601)
- `created_at TEXT NOT NULL DEFAULT (datetime('now'))`
- `updated_at TEXT NOT NULL DEFAULT (datetime('now'))`

#### Scenario: insert host with valid type

WHEN a host with `type='macbook'` is inserted
THEN the insert succeeds

#### Scenario: insert host with invalid type fails

WHEN a host with `type='windows'` is inserted
THEN the insert fails with a CHECK constraint violation

#### Scenario: host status defaults to offline

WHEN a host is inserted without specifying `status`
THEN `status` defaults to `'offline'`

---

### Requirement: Workspace Roots Table

The `workspace_roots` table SHALL store registered workspace directory paths per host and provider.

#### Scenario: workspace_roots table schema

WHEN the `workspace_roots` table is described
THEN it has columns: `id TEXT PRIMARY KEY`, `host_id TEXT NOT NULL REFERENCES hosts(id)`, `provider TEXT NOT NULL` with CHECK `IN ('claude', 'book', 'openclaw')`, `path TEXT NOT NULL`, `label TEXT` (nullable), `created_at TEXT NOT NULL`
AND a UNIQUE constraint on `(host_id, provider, path)`
AND indexes on `host_id` and `provider`

#### Scenario: duplicate root is rejected

WHEN two workspace roots with the same `(host_id, provider, path)` are inserted
THEN the second insert fails with a UNIQUE constraint violation

#### Scenario: root references valid host

WHEN a workspace root is inserted with a `host_id` that does not exist in `hosts`
THEN the insert fails with a foreign key constraint violation

---

### Requirement: Sessions Table

The `sessions` table SHALL store session records with lifecycle status tracking.

#### Scenario: sessions table schema

WHEN the `sessions` table is described
THEN it has columns: `id TEXT PRIMARY KEY`, `provider TEXT NOT NULL` with CHECK, `provider_session_id TEXT`, `host_id TEXT NOT NULL REFERENCES hosts(id)`, `workspace_root TEXT`, `workspace_cwd TEXT NOT NULL`, `initial_prompt TEXT`, `model TEXT`, `permission_mode TEXT NOT NULL DEFAULT 'bypassPermissions'`, `status TEXT NOT NULL DEFAULT 'queued'` with CHECK `IN ('queued', 'running', 'completed', 'failed', 'cancelled')`, `error_message TEXT`, `error_code TEXT`, `created_at TEXT NOT NULL`, `updated_at TEXT NOT NULL`, `last_active_at TEXT NOT NULL`
AND indexes on `provider`, `status`, `host_id`, `workspace_cwd`, `last_active_at`

#### Scenario: session defaults to queued status

WHEN a session is inserted without specifying `status`
THEN `status` defaults to `'queued'`

#### Scenario: invalid status is rejected

WHEN a session's `status` is updated to `'paused'`
THEN the update fails with a CHECK constraint violation

#### Scenario: session references valid host

WHEN a session is inserted with a `host_id` that does not exist in `hosts`
THEN the insert fails with a foreign key constraint violation

---

### Requirement: Session Events Table

The `session_events` table SHALL store ordered event records per session with monotonically increasing sequence numbers.

#### Scenario: session_events table schema

WHEN the `session_events` table is described
THEN it has columns: `id TEXT PRIMARY KEY`, `session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE`, `seq INTEGER NOT NULL`, `type TEXT NOT NULL`, `payload TEXT NOT NULL DEFAULT '{}'`, `created_at TEXT NOT NULL`
AND a UNIQUE constraint on `(session_id, seq)`
AND an index on `(session_id, seq)`

#### Scenario: cascade delete removes events

WHEN a session is deleted from the `sessions` table
THEN all associated events in `session_events` are automatically deleted

#### Scenario: duplicate seq within session is rejected

WHEN two events with the same `session_id` and `seq` are inserted
THEN the second insert fails with a UNIQUE constraint violation

#### Scenario: events from different sessions can share seq numbers

WHEN session A has an event with `seq=1` and session B inserts an event with `seq=1`
THEN both inserts succeed (seq is per-session, not global)

---

### Requirement: Approvals Table

The `approvals` table SHALL store tool approval requests (reserved for future use, MVP does not actively use it).

#### Scenario: approvals table schema

WHEN the `approvals` table is described
THEN it has columns: `id TEXT PRIMARY KEY`, `session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE`, `tool_name TEXT NOT NULL`, `tool_input TEXT`, `status TEXT NOT NULL DEFAULT 'pending'` with CHECK `IN ('pending', 'approved', 'denied', 'expired')`, `decision_at TEXT`, `expires_at TEXT`, `created_at TEXT NOT NULL`
AND indexes on `session_id` and `status`

#### Scenario: approvals cascade on session delete

WHEN a session is deleted
THEN all associated approvals are deleted

---

### Requirement: Push Subscriptions Table

The `push_subscriptions` table SHALL store FCM push notification tokens.

#### Scenario: push_subscriptions table schema

WHEN the `push_subscriptions` table is described
THEN it has columns: `id TEXT PRIMARY KEY`, `fcm_token TEXT NOT NULL UNIQUE`, `created_at TEXT NOT NULL`, `updated_at TEXT NOT NULL`

#### Scenario: duplicate FCM token is rejected

WHEN two subscriptions with the same `fcm_token` are inserted
THEN the second insert fails with a UNIQUE constraint violation

---

### Requirement: Audit Logs Table

The `audit_logs` table SHALL store audit trail entries for key operations.

#### Scenario: audit_logs table schema

WHEN the `audit_logs` table is described
THEN it has columns: `id TEXT PRIMARY KEY`, `action TEXT NOT NULL`, `session_id TEXT` (nullable), `host_id TEXT` (nullable), `detail TEXT` (nullable, JSON), `created_at TEXT NOT NULL`
AND an index on `created_at`

#### Scenario: audit log with no session or host

WHEN an audit log is inserted with `session_id=NULL` and `host_id=NULL`
THEN the insert succeeds (these are optional context fields)
