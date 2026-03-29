# Capability: openclaw-bridge-connection

## ADDED Requirements

### Requirement: Gateway WebSocket Connection

The OpenClaw bridge SHALL connect to the local OpenClaw gateway at `ws://localhost:18789` using a standard WebSocket client. The connection MUST be initiated during relay server startup. The bridge SHALL expose a method to check whether the gateway connection is currently active.

#### Scenario: gateway running at startup -- connect success

WHEN the relay server starts
AND the OpenClaw gateway is running on `localhost:18789`
THEN the bridge establishes a WebSocket connection to `ws://localhost:18789`
AND the bridge reports gateway status as `available`
AND the relay-local host's `openclaw` provider is marked as reachable

#### Scenario: gateway not running at startup -- reconnect loop

WHEN the relay server starts
AND the OpenClaw gateway is NOT running on `localhost:18789`
THEN the connection attempt fails (ECONNREFUSED or timeout)
AND the bridge marks openclaw as `unavailable`
AND the bridge schedules a reconnection attempt after 30 seconds
AND the relay server continues to start and serve other requests normally

#### Scenario: gateway crashes during operation -- detect disconnect

WHEN the bridge has an active connection to the gateway
AND the gateway process crashes or the WebSocket connection drops
THEN the bridge detects the `close` or `error` event on the WebSocket
AND the bridge marks openclaw as `unavailable`
AND the bridge broadcasts a status change (openclaw unavailable) to the relay internals
AND all running OpenClaw sessions are transitioned to `failed` with error_code `provider_unreachable`
AND the bridge begins the 30-second reconnect cycle

#### Scenario: gateway restarts after crash -- auto reconnect

WHEN the bridge is in the reconnect cycle (gateway previously unavailable)
AND the OpenClaw gateway starts up and begins listening on `localhost:18789`
THEN the next reconnect attempt succeeds
AND the bridge marks openclaw as `available`
AND the bridge broadcasts a status change (openclaw available)
AND the `/healthz` endpoint reflects `"openclaw": "online"`

#### Scenario: relay startup without gateway -- relay works normally

WHEN the relay server starts
AND the OpenClaw gateway is not running
THEN the relay starts successfully
AND all non-OpenClaw functionality works (companion sessions, Android WS, REST API)
AND `GET /healthz` returns `"openclaw": "offline"`
AND `POST /sessions` with `provider: "openclaw"` returns `502 provider_unreachable`

---

### Requirement: Reconnection Strategy

The bridge SHALL implement a fixed-interval reconnection strategy with a 30-second interval. Reconnection attempts MUST NOT block the relay event loop. The bridge MUST clean up stale connection state before each reconnection attempt.

#### Scenario: reconnect interval is 30 seconds

WHEN a connection attempt fails
THEN the bridge waits exactly 30 seconds before the next attempt
AND no reconnection is attempted during the 30-second wait
AND the timer is non-blocking (setTimeout, not busy-wait)

#### Scenario: successful reconnect stops the retry cycle

WHEN a reconnect attempt succeeds
THEN the reconnect timer is cancelled
AND no further reconnect attempts are scheduled
AND the bridge resumes normal operation

#### Scenario: stale state cleanup on reconnect

WHEN a reconnect attempt is about to be made
THEN any previous WebSocket instance is destroyed (listeners removed, socket closed)
AND the session_key mapping for sessions that were already transitioned to `failed` is cleared
AND a fresh WebSocket connection is created

---

### Requirement: Health Reporting

The bridge SHALL report its gateway connection status to the relay's health check endpoint and internal status tracking. The status MUST be either `online` (connected) or `offline` (disconnected/reconnecting).

#### Scenario: healthz reflects gateway status

WHEN `GET /healthz` is called
AND the gateway connection is active
THEN the response includes `"openclaw": "online"`

WHEN `GET /healthz` is called
AND the gateway connection is not active
THEN the response includes `"openclaw": "offline"`

#### Scenario: status is queryable internally

WHEN the SessionOrchestrator checks if openclaw is available before creating a session
THEN it calls `bridge.isAvailable()` which returns `true` only when the WebSocket is in OPEN state
