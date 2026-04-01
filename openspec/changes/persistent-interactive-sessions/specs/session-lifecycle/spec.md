## MODIFIED Requirements

### Requirement: Session status enum

The wire protocol SHALL define session statuses including the new `idle` state.

#### Scenario: Valid session statuses
- **WHEN** a session status value is used anywhere in the system
- **THEN** it SHALL be one of: `queued`, `running`, `idle`, `completed`, `failed`, `cancelled`

### Requirement: Session state transitions

The relay SHALL enforce the following state transition table:

```
queued     → running, failed
running    → idle, completed, failed, cancelled
idle       → running, completed, failed, cancelled
completed  → running  (resume only)
failed     → running  (resume only)
cancelled  → (no outbound transitions)
```

#### Scenario: Running to idle on turn completion
- **WHEN** session is `running` and relay receives `session_idle` event from companion
- **THEN** relay transitions session to `idle`
- **AND** broadcasts `session_status_changed` with `status: "idle"` to all WS clients

#### Scenario: Idle to running on send_message
- **WHEN** session is `idle` and relay receives `POST /sessions/:id/message`
- **THEN** relay transitions session to `running`
- **AND** dispatches `send_message` to companion (companion writes JSON to process stdin)
- **AND** broadcasts `session_status_changed` with `status: "running"`

#### Scenario: Idle to completed on complete
- **WHEN** session is `idle` and relay receives `POST /sessions/:id/complete`
- **THEN** relay dispatches `complete_session` to companion
- **AND** companion SIGTERMs the process
- **AND** relay transitions session to `completed` on receiving `session_result`

#### Scenario: Idle to cancelled on cancel
- **WHEN** session is `idle` and relay receives `POST /sessions/:id/cancel`
- **THEN** relay dispatches `cancel_session` to companion
- **AND** companion SIGTERMs the process
- **AND** relay transitions session to `cancelled`

#### Scenario: Running to completed on process exit
- **WHEN** session is `running` and relay receives `session_result` (process exited)
- **THEN** relay transitions session to `completed`

#### Scenario: Reject send_message on terminal states
- **WHEN** session is in `completed`, `failed`, or `cancelled` status
- **AND** relay receives `POST /sessions/:id/message`
- **THEN** relay returns HTTP 409 with error_code `state_conflict`

## ADDED Requirements

### Requirement: Session idle event type

The wire protocol SHALL define `session_idle` as a valid event type.

#### Scenario: session_idle event structure
- **WHEN** companion emits a `session_idle` event
- **THEN** the event SHALL have structure `{ type: "event", session_id: string, event_type: "session_idle", payload: { result: unknown } }`
- **AND** the payload contains the turn result

### Requirement: Complete session command

The wire protocol SHALL define `complete_session` as a valid companion command.

#### Scenario: complete_session command structure
- **WHEN** relay sends `complete_session` to companion
- **THEN** the command SHALL have structure `{ cmd: "complete_session", req_id: string, session_id: string }`

### Requirement: Complete session REST endpoint

Relay SHALL expose `POST /v1/sessions/:id/complete`.

#### Scenario: Complete an idle session via REST
- **WHEN** client sends `POST /v1/sessions/:id/complete` with valid auth and session in `idle`
- **THEN** relay returns 200 with updated session (status: `completed`)

#### Scenario: Complete a running session via REST
- **WHEN** client sends `POST /v1/sessions/:id/complete` with valid auth and session in `running`
- **THEN** relay returns 200 with updated session (status: `completed`)

#### Scenario: Complete a terminal-state session
- **WHEN** client sends `POST /v1/sessions/:id/complete` and session is `completed`/`failed`/`cancelled`
- **THEN** relay returns HTTP 409 with error_code `state_conflict`

### Requirement: Send message allowed from idle

`POST /v1/sessions/:id/message` SHALL accept messages when session is `idle`.

#### Scenario: Send message to idle session
- **WHEN** session is `idle` and client sends message
- **THEN** relay transitions to `running`, dispatches `send_message` to companion, returns 200

#### Scenario: Send message to running session
- **WHEN** session is `running` and client sends message
- **THEN** relay dispatches `send_message` to companion (writes to process stdin, existing behavior)

### Requirement: Database schema supports idle status

The sessions table SHALL accept `idle` as a valid status value.

#### Scenario: Schema migration
- **WHEN** relay starts with the new version
- **THEN** sessions table accepts `idle` as status
- **AND** existing sessions are unaffected

### Requirement: Event catch-up includes idle events

#### Scenario: Client reconnects and catches up
- **WHEN** WS client reconnects and requests events after seq N
- **THEN** catch-up includes `session_idle` events in sequence order
