# Capability: companion-relay-client

Connect the companion daemon to the relay server over WSS with token authentication, automatic reconnection, and bidirectional message passing.

## ADDED Requirements

### Requirement: WSS Connection with Token Auth

The companion SHALL establish an outbound WebSocket connection to `wss://{relay}/v1/companion?token=<TOKEN>&host_id=<HOST_ID>` on startup. The relay URL, static token, and host_id SHALL be read from the local configuration file (`companion.json`). The connection MUST use WSS (TLS) for all non-localhost relay URLs.

#### Scenario: Successful connection to relay

WHEN the companion starts with a valid relay URL and token in config
THEN a WSS connection is established to `wss://{relay}/v1/companion?token=<TOKEN>&host_id=<HOST_ID>`
AND the connection enters the OPEN state
AND the companion logs "connected to relay" with the relay URL

#### Scenario: Connection refused -- retry with exponential backoff

WHEN the relay server is unreachable (connection refused, DNS failure, network error)
THEN the companion retries with exponential backoff: 1s, 2s, 4s, 8s, 16s, 30s
AND the backoff caps at 30 seconds (subsequent retries remain at 30s)
AND each retry attempt is logged with the delay and attempt number

#### Scenario: Token invalid -- relay closes connection

WHEN the companion connects with an invalid token
THEN the relay closes the WebSocket with code 4001
AND the companion logs the authentication error
AND the companion retries with exponential backoff (token may be rotated on relay side)

#### Scenario: Relay restarts -- companion reconnects

WHEN an established WSS connection drops unexpectedly (relay restart, network interruption)
THEN the companion detects the close event
AND the backoff timer resets to 1s
AND reconnection begins immediately with exponential backoff
AND upon successful reconnection, the companion sends a heartbeat as re-registration

#### Scenario: Send message when disconnected -- drop with warning

WHEN the companion attempts to send a message (ack, event, heartbeat) while disconnected
THEN the message is dropped (not buffered)
AND a warning is logged with the message type and target session_id (if applicable)

### Requirement: Incoming Message Parsing

The companion SHALL parse all incoming WebSocket text frames as JSON. Each message MUST contain a `cmd` field (for relay-to-companion commands). Messages that fail JSON parsing SHALL be logged and discarded.

#### Scenario: Valid JSON command received

WHEN the relay sends a text frame containing valid JSON with a `cmd` field
THEN the companion parses it into a typed command object
AND passes it to the command dispatcher

#### Scenario: Invalid JSON received

WHEN the relay sends a text frame that is not valid JSON
THEN the companion logs the parse error with the raw message (truncated to 200 chars)
AND the frame is discarded without crashing

### Requirement: Outgoing Message Send

The companion SHALL provide a `send(message)` method that serializes the message to JSON and writes it to the WebSocket. The method MUST check connection state before sending.

#### Scenario: Send message while connected

WHEN `send()` is called with a valid message object and the connection is OPEN
THEN the message is JSON-serialized and sent as a text frame

#### Scenario: Send message while disconnected

WHEN `send()` is called while the connection is CLOSED or CONNECTING
THEN the send is a no-op
AND a warning is logged
