# Capability: host-status-tracking

## ADDED Requirements

### Requirement: Heartbeat Updates Host Status

The relay SHALL update a host's status to `online` and refresh its `last_heartbeat_at` timestamp whenever a heartbeat message is received from the companion. The heartbeat also updates the host's provider list.

#### Scenario: heartbeat received -- status online and timestamp updated

WHEN the companion for `macbook-1` sends a heartbeat message with `{ host_id: "macbook-1", providers: ["claude", "book"], uptime: 3600 }`
THEN `hosts.status` for `macbook-1` is set to `"online"`
AND `hosts.last_heartbeat_at` is set to the current UTC ISO 8601 timestamp
AND `hosts.updated_at` is set to the current UTC timestamp
AND the host's associated providers are recorded as `["claude", "book"]`

#### Scenario: heartbeat from new companion registers the host

WHEN a companion connects with `host_id: "macbook-1"`
AND no host record exists for `macbook-1`
THEN a new host record is inserted with `id: "macbook-1"`, `type: "macbook"`, `status: "online"`
AND `last_heartbeat_at` is set to the current timestamp

---

### Requirement: Stale Heartbeat Detection

The relay SHALL run a periodic check (every 60 seconds) to detect hosts whose `last_heartbeat_at` is older than 90 seconds. Such hosts MUST be marked `offline`.

#### Scenario: no heartbeat for 90s -- status offline

WHEN a host's `last_heartbeat_at` is more than 90 seconds ago
AND the periodic check runs
THEN `hosts.status` is updated to `"offline"`
AND `hosts.updated_at` is set to the current timestamp
AND all running sessions on that host are transitioned to `failed` with `error_code: "host_disconnected"`

#### Scenario: heartbeat arrives within 90s -- host stays online

WHEN a host's `last_heartbeat_at` is 45 seconds ago
AND the periodic check runs
THEN the host status remains `"online"`
AND no status change broadcast occurs

---

### Requirement: Status Change WebSocket Broadcast

When a host's status changes (online -> offline or offline -> online), the relay SHALL broadcast a `host_status` message to all connected Android WebSocket clients.

#### Scenario: status change broadcasts to all Android clients

WHEN host `macbook-1` status changes from `online` to `offline`
THEN all connected Android WebSocket clients receive:
```json
{ "type": "host_status", "host_id": "macbook-1", "status": "offline" }
```

WHEN host `macbook-1` status changes from `offline` to `online`
THEN all connected Android WebSocket clients receive:
```json
{ "type": "host_status", "host_id": "macbook-1", "status": "online" }
```

#### Scenario: no broadcast when status unchanged

WHEN a heartbeat arrives for a host already in `online` status
THEN no `host_status` message is broadcast (status did not change)
AND `last_heartbeat_at` is still updated silently

---

### Requirement: Companion Reconnect Restores Online Status

When a companion reconnects after a disconnection, the host status SHALL transition back to `online` upon receiving the first heartbeat.

#### Scenario: companion reconnect -- status back to online

WHEN host `macbook-1` is currently `offline` (companion was disconnected)
AND a new companion WebSocket connection is established for `macbook-1`
AND the companion sends its first heartbeat
THEN `hosts.status` is updated to `"online"`
AND a `host_status` broadcast is sent to all Android clients with `status: "online"`
AND an audit log entry `host.online` is written

#### Scenario: companion disconnect -- immediate offline

WHEN the companion WebSocket connection for `macbook-1` drops (close event)
THEN `hosts.status` is immediately updated to `"offline"` (do not wait for heartbeat timeout)
AND a `host_status` broadcast is sent to Android clients with `status: "offline"`
AND an audit log entry `host.offline` is written with `reason: "disconnect"`

---

### Requirement: Relay-Local Host is Always Online

The `relay-local` host SHALL always have `status: "online"`. It does not depend on heartbeats or WebSocket connections. Its status is set to `online` at relay startup and never changes.

#### Scenario: relay-local is always online

WHEN `GET /v1/hosts` is called at any time
THEN the `relay-local` host has `status: "online"`
AND `last_heartbeat_at` is `null`
AND the host is never marked offline by the periodic check
