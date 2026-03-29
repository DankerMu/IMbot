# Capability: session-state-machine-full

## ADDED Requirements

### Requirement: Queued to Running Transition

The session SHALL transition from `queued` to `running` when the companion (or OpenClaw bridge) acknowledges the create command successfully. The transition MUST emit a `session_started` event and update timestamps.

#### Scenario: queued to running on companion ack ok

WHEN a session is in `queued` state
AND the companion sends `ack` with `status: "ok"` and a `provider_session_id`
THEN the session status is updated to `running`
AND `sessions.provider_session_id` is set to the value from the ack
AND `sessions.updated_at` is set to the current time
AND `sessions.last_active_at` is set to the current time
AND a `session_started` event is emitted (allocated seq, stored, broadcast)

---

### Requirement: Queued to Failed on Ack Error

The session SHALL transition from `queued` to `failed` when the companion returns an error acknowledgment. The error details MUST be stored on the session record.

#### Scenario: queued to failed on companion ack error

WHEN a session is in `queued` state
AND the companion sends `ack` with `status: "error"`, `error_code`, and `message`
THEN the session status is updated to `failed`
AND `sessions.error_message` is set to the ack's `message`
AND `sessions.error_code` is set to the ack's `error_code`
AND `sessions.updated_at` and `last_active_at` are updated
AND a `session_error` event is emitted with the error details in payload
AND FCM push is triggered with the error message

---

### Requirement: Queued Timeout

The session SHALL transition from `queued` to `failed` if no companion acknowledgment is received within 30 seconds. The timeout timer MUST be cancelled if an ack arrives before it fires.

#### Scenario: queued to failed on 30s timeout

WHEN a session is in `queued` state
AND 30 seconds elapse without receiving an ack for the corresponding `req_id`
THEN the session status is updated to `failed`
AND `sessions.error_code` is set to `command_timeout`
AND `sessions.error_message` is set to "Companion did not respond within 30 seconds"
AND a `session_error` event is emitted with `error_code: "command_timeout"`
AND FCM push is triggered

---

### Requirement: Running to Completed

The session SHALL transition from `running` to `completed` when a `session_result` event is received from the companion or OpenClaw bridge. This is the normal successful completion path.

#### Scenario: running to completed on session_result event

WHEN a session is in `running` state
AND the companion sends an event with `event_type: "session_result"`
THEN the session status is updated to `completed`
AND `sessions.updated_at` and `last_active_at` are updated
AND the `session_result` event is stored with allocated seq
AND a `session_status_changed` event is broadcast via WebSocket
AND FCM push is triggered with a completion notification

---

### Requirement: Running to Failed on Error

The session SHALL transition from `running` to `failed` when a `session_error` event is received from the companion or OpenClaw bridge, or when the companion disconnects unexpectedly.

#### Scenario: running to failed on session_error event

WHEN a session is in `running` state
AND the companion sends an event with `event_type: "session_error"`
THEN the session status is updated to `failed`
AND `sessions.error_message` and `error_code` are populated from the event payload
AND the `session_error` event is stored with allocated seq
AND FCM push is triggered with the error details

#### Scenario: running to failed on companion disconnect

WHEN a session is in `running` state
AND the companion WebSocket connection drops (close/error)
AND the session's host_id matches the disconnected companion's host_id
THEN the session status is updated to `failed`
AND `sessions.error_code` is set to `host_disconnected`
AND `sessions.error_message` is set to "Host companion disconnected unexpectedly"
AND a `session_error` event is emitted with `error_code: "host_disconnected"`
AND FCM push is triggered

---

### Requirement: Running to Cancelled

The session SHALL transition from `running` to `cancelled` when the user sends `POST /sessions/:id/cancel`. A cancel command MUST be forwarded to the companion or bridge.

#### Scenario: running to cancelled on POST /cancel

WHEN a session is in `running` state
AND `POST /v1/sessions/:id/cancel` is received
THEN a `cancel_session` command is sent to the companion (or bridge for openclaw)
AND the session status is updated to `cancelled`
AND `sessions.updated_at` and `last_active_at` are updated
AND a `session_status_changed` event is emitted
AND the session is NOT eligible for FCM push (cancellation is user-initiated)

---

### Requirement: Completed to Running on Resume

A completed session SHALL be resumable. The session transitions back to `running` when `POST /sessions/:id/resume` is received and the host is online.

#### Scenario: completed to running on POST /resume (re-run)

WHEN a session is in `completed` state
AND `POST /v1/sessions/:id/resume` is received
AND the session's host is online
THEN a `resume_session` command is sent to the companion (or bridge) with the `provider_session_id`
AND on successful ack, the session status is updated to `running`
AND `sessions.updated_at` and `last_active_at` are updated
AND a `session_started` event is emitted (new seq in the existing sequence)

---

### Requirement: Failed to Running on Resume (Retry)

A failed session SHALL be resumable if the error is recoverable and the host is online.

#### Scenario: failed to running on POST /resume (retry)

WHEN a session is in `failed` state
AND `POST /v1/sessions/:id/resume` is received
AND the session's host is online
THEN a `resume_session` command is sent to the companion (or bridge)
AND on successful ack, the session status is updated to `running`
AND `sessions.error_message` and `error_code` are cleared (set to null)
AND `sessions.updated_at` and `last_active_at` are updated
AND a `session_started` event is emitted

---

### Requirement: Cancelled is Terminal

The `cancelled` state SHALL be a terminal state. No transitions out of `cancelled` are allowed.

#### Scenario: cancelled is terminal -- cannot transition

WHEN a session is in `cancelled` state
AND any operation is attempted (resume, message, cancel)
THEN the operation returns `409 state_conflict`
AND the session status remains `cancelled`
AND no commands are sent to the companion or bridge

---

### Requirement: Invalid Transition Guard

The state machine SHALL reject any transition not explicitly defined in the transition table. Invalid transitions MUST return a `409 state_conflict` error.

#### Scenario: invalid transition returns 409 state_conflict

WHEN a session is in `queued` state
AND `POST /v1/sessions/:id/cancel` is received (queued -> cancelled is not a defined transition for user-initiated cancel via REST; it goes queued->failed via timeout)
THEN the response is `409` with error code `state_conflict`
AND the session status remains unchanged

WHEN a session is in `completed` state
AND `POST /v1/sessions/:id/cancel` is received
THEN the response is `409` with error code `state_conflict`

---

### Requirement: Terminal State FCM Push

Each transition to a terminal state (`completed`, `failed`) SHALL trigger an FCM push notification to all registered devices. The `cancelled` state does NOT trigger FCM push (user-initiated).

#### Scenario: each terminal state triggers FCM push

WHEN a session transitions to `completed`
THEN FCM push is sent with title containing the truncated prompt and "completed"
AND the push data includes `session_id` and `action: "open_session"`

WHEN a session transitions to `failed`
THEN FCM push is sent with title containing the truncated prompt and "failed"
AND the push body includes the `error_message`
AND the push data includes `session_id` and `action: "open_session"`

WHEN a session transitions to `cancelled`
THEN no FCM push is sent

---

### Requirement: Timestamp Updates on Transition

Every state transition SHALL update `sessions.updated_at` and `sessions.last_active_at` to the current UTC timestamp.

#### Scenario: each transition updates timestamps

WHEN any valid state transition occurs
THEN `sessions.updated_at` is set to `datetime('now')` (UTC)
AND `sessions.last_active_at` is set to `datetime('now')` (UTC)
AND the updated values are reflected in subsequent `GET /sessions/:id` responses
