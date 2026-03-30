# Capability: openclaw-event-translation

## ADDED Requirements

### Requirement: Event Type Mapping

The bridge SHALL translate OpenClaw gateway events into the relay's unified `EventType` enum according to the mapping table defined in BUSINESS_LOGIC.md Section 6. Each translated event MUST be passed to the SessionOrchestrator for seq allocation, storage, and broadcasting.

#### Scenario: transcript.text (agent partial) maps to assistant_delta

WHEN the gateway emits a `transcript.text` event with role `agent` and partial flag
AND the event contains a `session_key` mapped to a relay session
THEN the bridge translates it to an event with `type: "assistant_delta"`
AND the payload contains the `text` field from the original event
AND the event is forwarded to `SessionOrchestrator.handleEvent()`

#### Scenario: transcript.text (complete) maps to assistant_message

WHEN the gateway emits a `transcript.text` event with role `agent` and complete flag
AND the event contains a `session_key` mapped to a relay session
THEN the bridge translates it to an event with `type: "assistant_message"`
AND the payload contains the full message text

#### Scenario: tool.start maps to tool_call_started

WHEN the gateway emits a `tool.start` event
THEN the bridge translates it to `type: "tool_call_started"`
AND the payload includes the tool name and input from the original event

#### Scenario: tool.end maps to tool_call_completed

WHEN the gateway emits a `tool.end` event
THEN the bridge translates it to `type: "tool_call_completed"`
AND the payload includes the tool name, output, and success/failure status

#### Scenario: session.ready maps to session_started

WHEN the gateway emits a `session.ready` event
THEN the bridge translates it to `type: "session_started"`
AND this event triggers the session to confirm its `running` state

#### Scenario: session.complete maps to session_result

WHEN the gateway emits a `session.complete` event
THEN the bridge translates it to `type: "session_result"`
AND the SessionOrchestrator transitions the session to `completed`
AND FCM push is triggered

#### Scenario: session.error maps to session_error

WHEN the gateway emits a `session.error` event
THEN the bridge translates it to `type: "session_error"`
AND the payload includes `error_message` and `error_code` from the original event
AND the SessionOrchestrator transitions the session to `failed`
AND FCM push is triggered

#### Scenario: message.user maps to user_message

WHEN the gateway emits a `message.user` event
THEN the bridge translates it to `type: "user_message"`
AND the payload contains the user message text (echo for UI display)

---

### Requirement: Unknown Event Handling

The bridge SHALL gracefully handle events from the gateway that do not match any known mapping. Unknown events MUST NOT crash the bridge or corrupt session state.

#### Scenario: unknown event type is ignored with log

WHEN the gateway emits an event with type `internal.debug` (not in the mapping table)
THEN the bridge logs a warning: "Unknown OpenClaw event type: internal.debug, session_key: xxx"
AND the event is NOT forwarded to the SessionOrchestrator
AND the session state is NOT affected
AND the bridge continues processing subsequent events normally

#### Scenario: malformed event type is handled

WHEN the gateway emits an event with a missing `type` field
THEN the bridge logs a warning with the raw event payload
AND the event is discarded
AND no error is thrown

---

### Requirement: Payload Validation

The bridge SHALL validate that translated event payloads contain the minimum required fields before forwarding to the SessionOrchestrator. Events with empty or missing critical payload data SHALL be handled gracefully.

#### Scenario: empty payload is handled gracefully

WHEN the gateway emits a `transcript.text` event with an empty payload (no text field)
THEN the bridge translates it to `assistant_delta` with `text: ""`
AND the event is forwarded (the orchestrator and UI handle empty text)
AND no error is thrown

#### Scenario: payload with extra fields is passed through

WHEN the gateway emits a `tool.start` event with additional fields not in the mapping
THEN the bridge includes only the mapped fields in the translated payload
AND extra fields are discarded
AND the translated event conforms to the relay event schema

---

### Requirement: Event Ordering and Throughput

The bridge SHALL process events in the order they are received from the gateway WebSocket. Under high event throughput (rapid burst), all events MUST be translated and forwarded without dropping.

#### Scenario: rapid event burst -- all translated and forwarded

WHEN the gateway emits 100 events in rapid succession for a single session
THEN the bridge translates all 100 events in receive order
AND all 100 are forwarded to the SessionOrchestrator
AND the SessionOrchestrator allocates seq 1 through 100 (assuming these are the first events)
AND no events are dropped or reordered

#### Scenario: interleaved events from multiple sessions

WHEN the gateway emits events alternating between session_key_A and session_key_B
THEN events for session_key_A are forwarded to relay_session_id_A in order
AND events for session_key_B are forwarded to relay_session_id_B in order
AND per-session event ordering is preserved

#### Scenario: event for unmapped session_key

WHEN the gateway emits an event with a `session_key` that has no mapping in the bridge
THEN the bridge logs a warning: "Received event for unknown session_key: xxx"
AND the event is discarded
AND no error is propagated
