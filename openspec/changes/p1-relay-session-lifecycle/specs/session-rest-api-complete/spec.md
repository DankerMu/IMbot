# Capability: session-rest-api-complete

## ADDED Requirements

### Requirement: List Sessions with Filters and Pagination

`GET /v1/sessions` SHALL return a paginated list of sessions with optional filtering by provider, status, and host_id. Results SHALL be sorted by `created_at` descending (newest first).

#### Scenario: GET /sessions with no filters returns all sessions

WHEN `GET /v1/sessions` is called with no query parameters
THEN the response is `200` with a JSON body containing `sessions`, `total`, `limit`, `offset`
AND `sessions` contains up to 50 sessions (default limit) sorted by `created_at` descending
AND `total` reflects the total number of sessions in the database
AND `limit` is 50 and `offset` is 0

#### Scenario: GET /sessions with provider filter

WHEN `GET /v1/sessions?provider=claude` is called
THEN only sessions with `provider: "claude"` are returned
AND `total` reflects the count of claude sessions only

#### Scenario: GET /sessions with status filter

WHEN `GET /v1/sessions?status=running` is called
THEN only sessions with `status: "running"` are returned

#### Scenario: GET /sessions with host_id filter

WHEN `GET /v1/sessions?host_id=macbook-1` is called
THEN only sessions with `host_id: "macbook-1"` are returned

#### Scenario: GET /sessions with combined filters

WHEN `GET /v1/sessions?provider=openclaw&status=completed` is called
THEN only sessions matching both `provider: "openclaw"` AND `status: "completed"` are returned

#### Scenario: GET /sessions with pagination

WHEN `GET /v1/sessions?limit=10&offset=20` is called
THEN the response contains at most 10 sessions starting from the 21st session
AND `limit` is 10 and `offset` is 20 in the response

#### Scenario: GET /sessions with limit exceeding max

WHEN `GET /v1/sessions?limit=500` is called
THEN the limit is clamped to 200 (maximum)
AND at most 200 sessions are returned

#### Scenario: GET /sessions with no results

WHEN `GET /v1/sessions?provider=nonexistent` is called
THEN the response is `200` with `sessions: []`, `total: 0`

---

### Requirement: Get Session by ID

`GET /v1/sessions/:id` SHALL return the full session object including `provider_session_id`, `permission_mode`, `error_message`, and `error_code`.

#### Scenario: GET /sessions/:id found

WHEN `GET /v1/sessions/:id` is called with a valid session id
THEN the response is `200` with the full session object
AND the response includes all fields: `id`, `provider`, `provider_session_id`, `host_id`, `workspace_cwd`, `initial_prompt`, `model`, `permission_mode`, `status`, `error_message`, `error_code`, `created_at`, `updated_at`, `last_active_at`

#### Scenario: GET /sessions/:id not found

WHEN `GET /v1/sessions/:id` is called with a non-existent id
THEN the response is `404` with `{ "error": "not_found" }`

---

### Requirement: Create Session

`POST /v1/sessions` SHALL create a new session, insert it into the database with status `queued`, dispatch the create command to the appropriate companion or bridge, and return the updated session after a successful startup ack.

#### Scenario: POST /sessions success

WHEN `POST /v1/sessions` is called with valid body `{ provider, host_id, cwd, prompt, model, permission_mode }`
AND the target host is online
AND the provider is available on that host
THEN a new session record is inserted with `status: "queued"`
AND a `create_session` command is dispatched to the companion (or bridge for openclaw)
AND on ack ok, the session status changes to `running`
AND the response is `201` with the updated session object

#### Scenario: POST /sessions host offline

WHEN `POST /v1/sessions` is called
AND the target host's status is `offline`
THEN the response is `502` with `{ "error": "host_offline" }`
AND no session record is created

#### Scenario: POST /sessions invalid provider

WHEN `POST /v1/sessions` is called with `provider: "invalid"`
THEN the response is `400` with `{ "error": "invalid_request", "message": "Unknown provider" }`

#### Scenario: POST /sessions missing required fields

WHEN `POST /v1/sessions` is called without `provider`, `host_id`, `cwd`, or `prompt`
THEN the response is `400` with `{ "error": "invalid_request" }` and a message indicating the missing field

#### Scenario: POST /sessions openclaw provider unreachable

WHEN `POST /v1/sessions` is called with `provider: "openclaw"`
AND the OpenClaw gateway is not connected
THEN the response is `502` with `{ "error": "provider_unreachable" }`

---

### Requirement: Resume Session

`POST /v1/sessions/:id/resume` SHALL resume a completed or failed session by sending a resume command to the companion or bridge.

#### Scenario: POST /sessions/:id/resume success from completed

WHEN `POST /v1/sessions/:id/resume` is called
AND the session is in `completed` state
AND the host is online
THEN a `resume_session` command is dispatched
AND on ack ok, the session status changes to `running`
AND the response is `200` with the updated session object

#### Scenario: POST /sessions/:id/resume success from failed

WHEN `POST /v1/sessions/:id/resume` is called
AND the session is in `failed` state
AND the host is online
THEN a `resume_session` command is dispatched
AND on ack ok, the session status changes to `running` and error fields are cleared
AND the response is `200` with the updated session object

#### Scenario: POST /sessions/:id/resume not resumable

WHEN `POST /v1/sessions/:id/resume` is called
AND the session is in `running` or `queued` or `cancelled` state
THEN the response is `409` with `{ "error": "state_conflict" }`

#### Scenario: POST /sessions/:id/resume host offline

WHEN `POST /v1/sessions/:id/resume` is called
AND the session's host is offline
THEN the response is `502` with `{ "error": "host_offline" }`
AND the session status is unchanged

#### Scenario: POST /sessions/:id/resume provider unavailable

WHEN `POST /v1/sessions/:id/resume` is called for an OpenClaw session
AND the OpenClaw gateway is offline or rejects the resume request
THEN the response is `502` with `{ "error": "provider_unreachable" }`
AND the session status is unchanged

---

### Requirement: Send Message to Session

`POST /v1/sessions/:id/message` SHALL forward a user message to a running session.

#### Scenario: POST /sessions/:id/message success

WHEN `POST /v1/sessions/:id/message` is called with `{ "text": "..." }`
AND the session is in `running` state
AND the host is online
THEN a `send_message` command is dispatched to the companion (or bridge)
AND the response is `200` with `{ "ok": true }`

#### Scenario: POST /sessions/:id/message not running

WHEN `POST /v1/sessions/:id/message` is called
AND the session is NOT in `running` state
THEN the response is `409` with `{ "error": "state_conflict" }`

#### Scenario: POST /sessions/:id/message host offline

WHEN `POST /v1/sessions/:id/message` is called
AND the host is offline
THEN the response is `502` with `{ "error": "host_offline" }`

#### Scenario: POST /sessions/:id/message provider unavailable

WHEN `POST /v1/sessions/:id/message` is called for an OpenClaw session
AND the OpenClaw gateway is offline
THEN the response is `502` with `{ "error": "provider_unreachable" }`

---

### Requirement: Cancel Session

`POST /v1/sessions/:id/cancel` SHALL cancel a running session by sending a cancel command and transitioning to `cancelled`.

#### Scenario: POST /sessions/:id/cancel success

WHEN `POST /v1/sessions/:id/cancel` is called
AND the session is in `running` state
THEN a `cancel_session` command is dispatched
AND the session status changes to `cancelled`
AND the response is `200` with the updated session object

#### Scenario: POST /sessions/:id/cancel not cancellable

WHEN `POST /v1/sessions/:id/cancel` is called
AND the session is in `completed`, `failed`, `cancelled`, or `queued` state
THEN the response is `409` with `{ "error": "state_conflict" }`

---

### Requirement: Delete Session

`DELETE /v1/sessions/:id` SHALL delete the session record and all associated events (via CASCADE).

#### Scenario: DELETE /sessions/:id success

WHEN `DELETE /v1/sessions/:id` is called with a valid session id
AND the session is in `completed`, `failed`, or `cancelled` state
THEN the session record is deleted from the database
AND all associated `session_events` records are deleted (ON DELETE CASCADE)
AND the response is `204` with no body

#### Scenario: DELETE /sessions/:id not found

WHEN `DELETE /v1/sessions/:id` is called with a non-existent id
THEN the response is `404` with `{ "error": "not_found" }`

#### Scenario: DELETE /sessions/:id active session

WHEN `DELETE /v1/sessions/:id` is called
AND the session is in `queued` or `running` state
THEN the response is `409` with `{ "error": "state_conflict" }`
AND the session record remains unchanged

---

### Requirement: Get Session Events (Catch-up)

`GET /v1/sessions/:id/events` SHALL return events for a session with seq greater than `since_seq`, supporting pagination via `limit` and a `has_more` indicator.

#### Scenario: GET /sessions/:id/events returns correct events

WHEN `GET /v1/sessions/:id/events?since_seq=10` is called
AND the session has events with seq 1 through 25
THEN the response is `200` with events having seq 11 through 25 (15 events)
AND events are sorted by `seq` ascending
AND `has_more` is `false` (all remaining events fit within the limit)

#### Scenario: GET /sessions/:id/events with has_more flag

WHEN `GET /v1/sessions/:id/events?since_seq=0&limit=10` is called
AND the session has 25 events
THEN the response contains 10 events (seq 1 through 10)
AND `has_more` is `true`

#### Scenario: GET /sessions/:id/events empty result

WHEN `GET /v1/sessions/:id/events?since_seq=100` is called
AND the session's max seq is 50
THEN the response is `200` with `events: []` and `has_more: false`

#### Scenario: GET /sessions/:id/events default limit

WHEN `GET /v1/sessions/:id/events?since_seq=0` is called without a `limit` parameter
THEN the default limit of 500 is applied

#### Scenario: GET /sessions/:id/events session not found

WHEN `GET /v1/sessions/:id/events` is called with a non-existent session id
THEN the response is `404` with `{ "error": "not_found" }`
