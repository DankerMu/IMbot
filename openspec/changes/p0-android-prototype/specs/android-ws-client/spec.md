# Capability: android-ws-client

OkHttp-based WebSocket client that connects to the relay, authenticates via query-param token, parses server messages, and emits events as Kotlin Flow.

## ADDED Requirements

### Requirement: WebSocket Connection with Token Auth

The client SHALL connect to the relay WebSocket endpoint at `wss://{relay_url}/v1/ws?token=<TOKEN>`. The relay URL and token SHALL be read from the app's saved settings (SharedPreferences). The client SHALL use OkHttp's `WebSocket` API.

#### Scenario: Connect with valid token -- success

WHEN the client connects to a relay with a valid token
THEN the WebSocket enters the OPEN state
AND the client emits a `Connected` state event

#### Scenario: Connect with invalid token -- closed

WHEN the client connects to a relay with an invalid token
THEN the relay closes the connection (code 4001 or 1008)
AND the client emits a `Disconnected(reason="unauthenticated")` state event
AND the client attempts reconnection with exponential backoff

#### Scenario: Relay URL not configured

WHEN the user has not entered a relay URL in settings
THEN the client does not attempt connection
AND the connection state remains `NotConfigured`

### Requirement: Auto-Reconnect with Exponential Backoff

The client SHALL automatically reconnect when the connection drops. Reconnection delays SHALL follow exponential backoff: 1s, 2s, 4s, 8s, 16s, 30s (capped at 30s). The backoff SHALL reset to 1s on successful connection.

#### Scenario: Connection lost -- auto reconnect

WHEN an established WebSocket connection drops (network change, relay restart)
THEN the client emits a `Disconnected` state event
AND begins reconnection with 1s initial delay
AND exponentially increases the delay on each failed attempt up to 30s

#### Scenario: Reconnect successful -- resubscribe

WHEN the client successfully reconnects after a disconnect
THEN the client emits a `Connected` state event
AND if there was an active session subscription, the client re-sends the `subscribe` message for that session

### Requirement: Parse ServerMessage JSON

The client SHALL parse all incoming WebSocket text frames as JSON and map them to Kotlin data classes. The supported message types are: `event`, `status`, `host_status`, `error`, `pong`.

#### Scenario: Receive event message -- parsed to data class

WHEN the relay sends `{"type":"event","session_id":"s1","seq":42,"event_type":"assistant_delta","payload":{"text":"Hello"},"timestamp":"..."}`
THEN the client parses it into a `ServerMessage.Event` data class
AND emits it via the messages Flow

#### Scenario: Receive pong -- no crash

WHEN the relay sends `{"type":"pong"}`
THEN the client parses it as `ServerMessage.Pong`
AND no error occurs (pong is silently consumed or emitted)

#### Scenario: Receive malformed JSON

WHEN the relay sends a text frame that is not valid JSON
THEN the client logs the error
AND does not crash
AND continues listening for subsequent messages

### Requirement: Emit Events as Kotlin Flow

The client SHALL expose a `messages: Flow<ServerMessage>` that emits parsed server messages. It SHALL also expose a `connectionState: StateFlow<ConnectionState>` for UI observation. Both flows MUST be safe to collect from the main thread (emissions happen on a background dispatcher).

#### Scenario: Multiple collectors receive events

WHEN two UI components collect from `messages` Flow
THEN both receive the same events

#### Scenario: Flow survives configuration change

WHEN the device is rotated while events are streaming
THEN the ViewModel retains the Flow collection
AND no events are lost during the rotation

### Requirement: Subscribe to Session Events

The client SHALL support subscribing to a session's event stream by sending `{ "action": "subscribe", "session_id": "<id>" }` over the WebSocket. Only one session subscription is active at a time in the prototype.

#### Scenario: Subscribe to session

WHEN `subscribe(sessionId)` is called
THEN the client sends `{"action":"subscribe","session_id":"<id>"}` over the WebSocket
AND subsequent `event` messages for that session are received

#### Scenario: Subscribe while disconnected

WHEN `subscribe(sessionId)` is called while disconnected
THEN the subscription is queued
AND sent automatically upon reconnection
