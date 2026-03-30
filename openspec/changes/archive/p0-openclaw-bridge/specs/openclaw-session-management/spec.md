# Capability: openclaw-session-management

## ADDED Requirements

### Requirement: Create Session via Gateway

The bridge SHALL send a session creation request to the OpenClaw gateway when the SessionOrchestrator routes an openclaw session to the bridge. The gateway responds with a `session_key` that the bridge MUST map to the relay's `session_id`. The session transitions to `running` on successful acknowledgment.

#### Scenario: create session -- gateway acknowledges successfully

WHEN `bridge.createSession(relaySessionId, cwd, prompt)` is called
AND the gateway connection is active
THEN the bridge sends a create message to the gateway containing `cwd` and `prompt`
AND the gateway responds with an acknowledgment containing a `session_key`
AND the bridge stores the mapping: `relaySessionId <-> session_key`
AND the bridge notifies the SessionOrchestrator of successful creation
AND the session transitions from `queued` to `running`

#### Scenario: create session -- gateway offline

WHEN `bridge.createSession(relaySessionId, cwd, prompt)` is called
AND the gateway connection is NOT active
THEN the bridge immediately returns an error with code `provider_unreachable`
AND the session transitions from `queued` to `failed` with error_code `provider_unreachable`
AND no message is sent over the WebSocket

#### Scenario: create session -- gateway ack timeout

WHEN `bridge.createSession(relaySessionId, cwd, prompt)` is called
AND the gateway connection is active
AND the gateway does not respond within 30 seconds
THEN the bridge returns a timeout error with code `command_timeout`
AND the session transitions from `queued` to `failed` with error_code `command_timeout`

#### Scenario: create session -- gateway returns error

WHEN `bridge.createSession(relaySessionId, cwd, prompt)` is called
AND the gateway responds with an error (e.g., invalid cwd, resource exhaustion)
THEN the bridge propagates the error to the SessionOrchestrator
AND the session transitions from `queued` to `failed` with the gateway's error details

---

### Requirement: Resume Session via Gateway

The bridge SHALL send a resume request to the OpenClaw gateway using the previously stored `session_key`. The gateway re-establishes the session context and begins emitting events again.

#### Scenario: resume session with valid key

WHEN `bridge.resumeSession(relaySessionId, openclawSessionKey)` is called
AND the gateway connection is active
AND the `openclawSessionKey` corresponds to a valid prior session on the gateway
THEN the bridge sends a resume message to the gateway with the `session_key`
AND the gateway acknowledges successfully
AND the session transitions to `running`
AND the bridge resumes event translation for this session

#### Scenario: resume session with invalid key

WHEN `bridge.resumeSession(relaySessionId, openclawSessionKey)` is called
AND the gateway does not recognize the `session_key` (expired, purged)
THEN the gateway returns an error
AND the session transitions to `failed` with error_code `session_not_resumable`

---

### Requirement: Send Message to Running Session

The bridge SHALL forward follow-up messages from the user to the correct OpenClaw session identified by `session_key`.

#### Scenario: send message to running session

WHEN `bridge.sendMessage(relaySessionId, text)` is called
AND the session is mapped to a valid `session_key`
AND the gateway connection is active
THEN the bridge sends the message to the gateway with the `session_key` and `text`
AND the gateway processes the message and begins emitting response events

#### Scenario: send message when gateway offline

WHEN `bridge.sendMessage(relaySessionId, text)` is called
AND the gateway connection is NOT active
THEN the bridge returns an error with code `provider_unreachable`
AND the caller receives a `502 host_offline` error

---

### Requirement: Cancel Running Session

The bridge SHALL send a cancellation request to the OpenClaw gateway for the specified session. The gateway terminates the session and emits no further events for it.

#### Scenario: cancel running session

WHEN `bridge.cancelSession(relaySessionId)` is called
AND the session is mapped to a valid `session_key`
AND the gateway connection is active
THEN the bridge sends a cancel message to the gateway with the `session_key`
AND the gateway acknowledges the cancellation
AND the session transitions to `cancelled`
AND the bridge removes the session_key mapping from the active map

#### Scenario: cancel when gateway offline

WHEN `bridge.cancelSession(relaySessionId)` is called
AND the gateway connection is NOT active
THEN the bridge marks the session as `cancelled` locally
AND the session_key mapping is removed
AND the session transitions to `cancelled` (best-effort; gateway session may linger)

---

### Requirement: Session Key Mapping

The bridge SHALL maintain a bidirectional `Map<string, string>` mapping relay `session_id` to OpenClaw `session_key`. This mapping MUST be used to correlate incoming gateway events with relay sessions and to address outgoing commands to the correct gateway session.

#### Scenario: concurrent sessions each with own key mapping

WHEN three openclaw sessions are created concurrently
AND each receives a distinct `session_key` from the gateway
THEN the bridge maintains three separate mappings
AND events arriving for `session_key_A` are routed only to `relay_session_id_A`
AND events arriving for `session_key_B` are routed only to `relay_session_id_B`
AND events arriving for `session_key_C` are routed only to `relay_session_id_C`

#### Scenario: mapping cleaned up on session terminal state

WHEN a session reaches a terminal state (`completed`, `failed`, `cancelled`)
THEN the bridge removes the corresponding entry from the session_key map
AND subsequent events from the gateway for that `session_key` are ignored with a debug log
