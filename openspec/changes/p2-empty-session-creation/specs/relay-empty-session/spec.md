## MODIFIED Requirements

### Requirement: Session creation without initial prompt

Relay SHALL accept `POST /sessions` without a `prompt` field. When prompt is absent or blank, the session is created in `idle` status without starting a companion provider process.

#### Scenario: Create session without prompt
- **WHEN** client sends `POST /sessions { provider: "book", host_id: "mac-1", cwd: "/path" }`
- **AND** no `prompt` field is present
- **THEN** relay creates session with `status: "idle"`, `initial_prompt: null`
- **AND** does NOT send `create_session` command to companion
- **AND** broadcasts `session_idle` event with `payload: { reason: "awaiting_first_message" }`
- **AND** returns 201 with session object

#### Scenario: Create session with empty string prompt
- **WHEN** client sends `POST /sessions { ..., prompt: "" }`
- **THEN** relay treats it as no prompt (trim → blank → null)
- **AND** creates session in `idle` status

#### Scenario: Create session with whitespace-only prompt
- **WHEN** client sends `POST /sessions { ..., prompt: "   " }`
- **THEN** relay treats it as no prompt
- **AND** creates session in `idle` status

#### Scenario: Create session with valid prompt (existing behavior preserved)
- **WHEN** client sends `POST /sessions { ..., prompt: "hello" }`
- **THEN** relay creates session, dispatches to companion, transitions to `running`
- **AND** existing behavior is unchanged

#### Scenario: Required fields still enforced
- **WHEN** client sends `POST /sessions` without `provider`
- **THEN** relay returns 400 with validation error
- **AND** same for missing `host_id` or `cwd`

### Requirement: First message starts an idle empty session

When a user sends the first message to an idle session that has no provider process, the orchestrator SHALL start the companion process with that message as the initial prompt.

#### Scenario: Send first message to empty idle session
- **GIVEN** session exists with `status: "idle"` and `provider_session_id: null`
- **WHEN** client sends `POST /sessions/:id/message { text: "hello" }`
- **THEN** orchestrator updates `initial_prompt` to `"hello"` in DB
- **AND** sends `create_session` command to companion with `prompt: "hello"`
- **AND** transitions session from `idle` → `running`
- **AND** broadcasts `session_started` event

#### Scenario: Send message to idle session with provider (existing multi-turn)
- **GIVEN** session exists with `status: "idle"` and `provider_session_id: "abc-123"`
- **WHEN** client sends `POST /sessions/:id/message { text: "continue" }`
- **THEN** orchestrator sends `send_message` command to companion (existing behavior)
- **AND** transitions session from `idle` → `running`

#### Scenario: Companion offline when first message sent
- **GIVEN** empty idle session exists
- **AND** companion host is offline
- **WHEN** client sends first message
- **THEN** relay returns `host_offline` error
- **AND** session stays in `idle` status

#### Scenario: Concurrent first messages to same empty session
- **GIVEN** empty idle session exists
- **WHEN** two clients send messages simultaneously
- **THEN** lifecycle lock ensures only one succeeds
- **AND** the other receives a conflict error or waits

### Requirement: State transition queued → idle is valid

#### Scenario: queued to idle transition
- **GIVEN** `VALID_TRANSITIONS` in wire enums
- **THEN** `queued` allows transitions to `["running", "idle", "failed"]`
- **AND** `idle` allows transitions to `["running", "completed", "failed", "cancelled"]` (unchanged)

### Requirement: Empty session lifecycle operations

#### Scenario: Cancel empty idle session
- **GIVEN** session in `idle` with `provider_session_id: null`
- **WHEN** client sends `POST /sessions/:id/cancel`
- **THEN** session transitions to `cancelled` directly
- **AND** no companion command is sent (no process to cancel)

#### Scenario: Complete empty idle session
- **GIVEN** session in `idle` with `provider_session_id: null`
- **WHEN** client sends `POST /sessions/:id/complete`
- **THEN** session transitions to `completed` directly
- **AND** no companion command is sent

#### Scenario: Delete empty idle session
- **GIVEN** session in `idle` with `provider_session_id: null`
- **WHEN** client sends `DELETE /sessions/:id`
- **THEN** session and its events are deleted
- **AND** no companion command is sent

#### Scenario: Resume does not apply to idle sessions
- **GIVEN** session in `idle` status
- **WHEN** client sends `POST /sessions/:id/resume`
- **THEN** relay returns `state_conflict` error
- **AND** resume is only valid from terminal states (completed, failed, cancelled)
