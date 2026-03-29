# Capability: session-orchestrator-basic

## ADDED Requirements

### Requirement: Create Session

The session orchestrator SHALL create a new session by inserting a record in the database, sending a `create_session` command to the appropriate companion, and returning the session object.

#### Scenario: create session happy path (companion online)

WHEN `POST /v1/sessions` is called with `{ "provider": "claude", "host_id": "macbook-1", "cwd": "/path/to/project", "prompt": "help me refactor", "model": "opus" }`
AND the companion for `macbook-1` is online
THEN a new session record is inserted in `sessions` with `status='queued'`
AND a `create_session` command with a unique `req_id` is sent to the companion
AND the companion ack is awaited (up to 30s timeout)
AND the API returns HTTP 201 with the session object

#### Scenario: create session when host is offline

WHEN `POST /v1/sessions` is called with `host_id: "macbook-1"`
AND no companion is connected for `macbook-1`
THEN the API returns HTTP 502 with `{ "error": "host_offline" }`
AND no session record is inserted in the database

#### Scenario: create session with missing required fields

WHEN `POST /v1/sessions` is called without `provider` or without `cwd`
THEN the API returns HTTP 400 with `{ "error": "invalid_request" }`

#### Scenario: create session companion ack timeout

WHEN a session is created and the companion does not ack within 30 seconds
THEN the session status transitions from `queued` to `failed`
AND `error_code` is set to `'command_timeout'`
AND a `session_error` event is stored and broadcast

#### Scenario: create session companion ack error

WHEN a session is created and the companion replies with `{ "type": "ack", "req_id": "...", "status": "error", "error_code": "...", "message": "..." }`
THEN the session status transitions from `queued` to `failed`
AND the error_code and error_message from the ack are stored on the session

#### Scenario: audit log is written on session create

WHEN a session is successfully created
THEN an audit log entry is inserted with `action='session.create'` and the session_id

---

### Requirement: Receive and Store Events

The session orchestrator SHALL process incoming events from companions by allocating a sequence number, storing the event, updating the session, and broadcasting to subscribers.

#### Scenario: event arrives and seq is allocated

WHEN a companion sends an event for session `sess-123`
AND the current max seq for `sess-123` is 5
THEN the event is stored with `seq = 6`
AND `sessions.last_active_at` is updated to the current time

#### Scenario: first event for a session gets seq 1

WHEN a companion sends the first event for a newly created session
THEN the event is stored with `seq = 1`

#### Scenario: seq is monotonically increasing

WHEN 10 events arrive in rapid succession for session `sess-123`
THEN each event receives a strictly increasing seq: 1, 2, 3, ..., 10
AND no gaps exist in the sequence

#### Scenario: event is broadcast after storage

WHEN an event is stored with seq and session_id
THEN it is broadcast to all Android clients subscribed to that session_id
AND the broadcast message includes the allocated seq, event_type, payload, and timestamp

#### Scenario: terminal event triggers status transition

WHEN an event with `event_type: 'session_result'` arrives
THEN the session status transitions to `'completed'`
AND a `session_status_changed` event is also stored and broadcast

WHEN an event with `event_type: 'session_error'` arrives
THEN the session status transitions to `'failed'`

#### Scenario: event for non-existent session is rejected

WHEN a companion sends an event with a `session_id` that does not exist in the database
THEN the event is silently dropped (not stored)
AND a warning is logged

---

### Requirement: Status Transitions

The session orchestrator SHALL enforce valid status transitions as defined in the state machine, rejecting invalid transitions.

#### Scenario: valid transition queued to running

WHEN a session with `status='queued'` receives a successful companion ack
THEN the status transitions to `'running'`
AND a `session_status_changed` event is stored and broadcast
AND `sessions.updated_at` is updated

#### Scenario: valid transition running to completed

WHEN a session with `status='running'` receives a `session_result` event
THEN the status transitions to `'completed'`

#### Scenario: valid transition running to failed

WHEN a session with `status='running'` receives a `session_error` event
THEN the status transitions to `'failed'`
AND `error_message` and `error_code` are populated from the event payload

#### Scenario: valid transition running to cancelled

WHEN `POST /v1/sessions/:id/cancel` is called for a running session
THEN a `cancel_session` command is sent to the companion
AND the status transitions to `'cancelled'`

#### Scenario: invalid transition cancelled to running is rejected

WHEN a session has `status='cancelled'`
AND code attempts to transition it to `'running'`
THEN the transition is rejected with a `state_conflict` error
AND the status remains `'cancelled'`

#### Scenario: invalid transition completed to failed is rejected

WHEN a session has `status='completed'`
AND code attempts to transition it to `'failed'`
THEN the transition is rejected with a `state_conflict` error

#### Scenario: status change broadcasts to subscribers

WHEN a session status transitions
THEN all Android clients subscribed to that session receive `{ "type": "status", "session_id": "...", "status": "<new-status>" }`

---

### Requirement: Basic REST Endpoints

The relay SHALL expose basic REST endpoints for session management in this minimal phase.

#### Scenario: POST /v1/sessions creates a session

WHEN `POST /v1/sessions` is called with valid auth and valid body
THEN HTTP 201 is returned with the session object including `id`, `status: 'queued'`, `created_at`

#### Scenario: GET /v1/sessions lists sessions

WHEN `GET /v1/sessions` is called with valid auth
THEN HTTP 200 is returned with `{ "sessions": [...], "total": <n>, "limit": 50, "offset": 0 }`

#### Scenario: GET /v1/sessions with filters

WHEN `GET /v1/sessions?provider=claude&status=running&limit=10` is called
THEN only sessions matching all filters are returned
AND at most 10 sessions are returned

#### Scenario: GET /v1/sessions/:id returns session detail

WHEN `GET /v1/sessions/sess-123` is called with valid auth
AND session `sess-123` exists
THEN HTTP 200 is returned with the full session object

#### Scenario: GET /v1/sessions/:id for non-existent session

WHEN `GET /v1/sessions/nonexistent` is called
THEN HTTP 404 is returned with `{ "error": "not_found" }`

#### Scenario: GET /v1/sessions/:id/events returns events

WHEN `GET /v1/sessions/sess-123/events?since_seq=5` is called
THEN HTTP 200 is returned with events where `seq > 5` for that session
AND events are ordered by seq ascending
AND `has_more` indicates if there are more events beyond the limit
