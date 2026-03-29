# Capability: audit-logging

## ADDED Requirements

### Requirement: Session Lifecycle Audit Entries

The relay SHALL write audit log entries to the `audit_logs` table for session lifecycle actions. Each entry MUST include `action`, `session_id`, and `detail` (JSON) fields.

#### Scenario: session.create audit entry

WHEN a new session is created via `POST /v1/sessions`
THEN an audit log entry is inserted with:
- `action`: `"session.create"`
- `session_id`: the new session's id
- `detail`: JSON containing `provider`, `host_id`, `cwd`, `prompt` (truncated to 100 chars)
- `created_at`: current UTC timestamp

#### Scenario: session.resume audit entry

WHEN a session is resumed via `POST /v1/sessions/:id/resume`
THEN an audit log entry is inserted with:
- `action`: `"session.resume"`
- `session_id`: the session's id
- `detail`: JSON containing `previous_status` (completed or failed)

#### Scenario: session.cancel audit entry

WHEN a session is cancelled via `POST /v1/sessions/:id/cancel`
THEN an audit log entry is inserted with:
- `action`: `"session.cancel"`
- `session_id`: the session's id
- `detail`: JSON containing `previous_status` (running)

#### Scenario: session.delete audit entry

WHEN a session is deleted via `DELETE /v1/sessions/:id`
THEN an audit log entry is inserted with:
- `action`: `"session.delete"`
- `session_id`: the session's id
- `detail`: JSON containing `provider`, `status` at time of deletion

---

### Requirement: Host Lifecycle Audit Entries

The relay SHALL write audit log entries for host status changes.

#### Scenario: host.online audit entry

WHEN a companion connects and the host status changes to `online`
THEN an audit log entry is inserted with:
- `action`: `"host.online"`
- `host_id`: the host's id
- `detail`: JSON containing `providers` list from the heartbeat

#### Scenario: host.offline audit entry

WHEN a host is marked offline (heartbeat timeout or companion disconnect)
THEN an audit log entry is inserted with:
- `action`: `"host.offline"`
- `host_id`: the host's id
- `detail`: JSON containing `reason` ("heartbeat_timeout" or "disconnect")

---

### Requirement: Workspace Root Audit Entries

The relay SHALL write audit log entries when workspace roots are added or removed.

#### Scenario: root.add audit entry

WHEN a workspace root is added via `POST /v1/hosts/:hostId/roots`
THEN an audit log entry is inserted with:
- `action`: `"root.add"`
- `host_id`: the host's id
- `detail`: JSON containing `root_id`, `provider`, `path`, `label`

#### Scenario: root.remove audit entry

WHEN a workspace root is removed via `DELETE /v1/hosts/:hostId/roots/:rootId`
THEN an audit log entry is inserted with:
- `action`: `"root.remove"`
- `host_id`: the host's id
- `detail`: JSON containing `root_id`, `provider`, `path`

---

### Requirement: Audit Log Schema Compliance

All audit log entries SHALL conform to the `audit_logs` table schema. The `detail` field MUST be a valid JSON string. The `created_at` field MUST be an ISO 8601 UTC timestamp.

#### Scenario: each action writes correct fields

WHEN any auditable action occurs
THEN the audit log entry has a non-null `id` (UUID)
AND `action` is one of the defined action strings
AND `detail` is parseable as JSON
AND `created_at` is a valid ISO 8601 timestamp

#### Scenario: audit logs accumulate over time

WHEN 10 different auditable actions occur in sequence
THEN the `audit_logs` table contains 10 entries
AND they are ordered by `created_at` ascending
AND each has a unique `id`

#### Scenario: audit log write failure does not block the primary operation

WHEN an audit log INSERT fails (e.g., disk full edge case)
THEN the primary operation (session create, cancel, etc.) still completes successfully
AND the failure is logged to the application error log
AND the system continues operating
