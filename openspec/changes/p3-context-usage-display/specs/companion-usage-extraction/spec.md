## ADDED Requirements

### Requirement: Wire protocol includes session_usage event type

#### Scenario: session_usage is a valid event type
- **GIVEN** `EVENT_TYPES` array in `packages/wire/src/enums.ts`
- **THEN** it includes `"session_usage"`
- **AND** `EventType` union type accepts `"session_usage"`

#### Scenario: SessionUsagePayload type definition
- **GIVEN** `packages/wire/src/models.ts`
- **THEN** exports `SessionUsagePayload` interface with fields:
  - `input_tokens: number`
  - `output_tokens: number`
  - `cache_creation_input_tokens?: number`
  - `cache_read_input_tokens?: number`
  - `context_window?: number`
  - `model?: string`

### Requirement: Companion extracts usage from stream-json result events

Companion SHALL extract token usage data from the Claude CLI `type: "result"` stream-json output and emit it as a `session_usage` event.

#### Scenario: Result event with usage data
- **WHEN** Claude CLI emits `{ "type": "result", "result": "...", "usage": { "input_tokens": 12500, "output_tokens": 3200 } }`
- **THEN** companion emits `session_usage` event with payload `{ input_tokens: 12500, output_tokens: 3200 }`
- **AND** the event is emitted AFTER the `session_result` event for the same result

#### Scenario: Result event with full usage data including cache
- **WHEN** CLI emits `{ "type": "result", "usage": { "input_tokens": 12500, "output_tokens": 3200, "cache_creation_input_tokens": 500, "cache_read_input_tokens": 8900 } }`
- **THEN** companion emits `session_usage` with all four token fields

#### Scenario: Result event with model field
- **WHEN** CLI emits `{ "type": "result", "usage": { ... }, "model": "claude-sonnet-4-6" }`
- **THEN** `session_usage` payload includes `model: "claude-sonnet-4-6"`

#### Scenario: Result event without model field
- **WHEN** CLI emits `{ "type": "result", "usage": { ... } }` without `model`
- **THEN** `session_usage` payload uses `session.model` as fallback (from initial command or system message)
- **AND** if session.model is also null, `model` field is omitted

#### Scenario: Result event without usage field
- **WHEN** CLI emits `{ "type": "result", "result": "..." }` without `usage` object
- **THEN** companion does NOT emit `session_usage` event
- **AND** `session_result` event is still emitted normally

#### Scenario: Result event with partial usage (only input_tokens)
- **WHEN** CLI emits `{ "type": "result", "usage": { "input_tokens": 5000 } }`
- **THEN** companion emits `session_usage` with `input_tokens: 5000, output_tokens: 0`

#### Scenario: Result event with non-numeric usage values
- **WHEN** CLI emits `{ "type": "result", "usage": { "input_tokens": "invalid" } }`
- **THEN** companion does NOT emit `session_usage` event (graceful skip)

### Requirement: Companion extracts model from system message

#### Scenario: System message contains model
- **WHEN** CLI emits `{ "type": "system", "session_id": "abc", "model": "claude-opus-4-6" }`
- **THEN** companion stores `model: "claude-opus-4-6"` on the RuntimeSession
- **AND** uses it as fallback for `session_usage` model field

#### Scenario: session_started event includes model
- **WHEN** companion emits `session_started` event
- **THEN** payload includes `model` field from command.model or system message
- **AND** relay broadcasts it to subscribers

### Requirement: Relay broadcasts session_usage events and persists latest usage summary

#### Scenario: session_usage event forwarded to subscribers
- **WHEN** companion sends `session_usage` event to relay
- **THEN** relay broadcasts it to all WS clients subscribed to that session
- **AND** the event passes through the standard event broadcast pipeline

#### Scenario: session_usage updates the session summary row
- **WHEN** `session_usage` event arrives at relay
- **THEN** relay updates `sessions.input_tokens`, `sessions.output_tokens`, and `sessions.context_window`
- **AND** if payload carries `model`, relay also refreshes `sessions.model`

#### Scenario: session list reads the latest persisted usage snapshot
- **GIVEN** a completed or idle session with persisted `input_tokens = 55000`, `output_tokens = 1200`, `context_window = 200000`
- **WHEN** Android requests `GET /v1/sessions`
- **THEN** the session summary includes those fields
- **AND** Android can render `56.2k/200k` without hardcoded model windows

### Requirement: Multiple turns accumulate usage

#### Scenario: Usage updates after each turn
- **GIVEN** session has completed turn 1 with `input_tokens: 5000, output_tokens: 2000`
- **WHEN** turn 2 completes with `input_tokens: 12000, output_tokens: 4500`
- **THEN** a new `session_usage` event is emitted with the turn-2 values
- **AND** the Android client uses the latest values (not cumulative — each event is a snapshot of total session usage as reported by the CLI)
