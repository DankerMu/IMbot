# Capability: android-prototype-ui

Minimal Jetpack Compose UI for the Phase 0 prototype: settings input, session creation, and raw event display.

## ADDED Requirements

### Requirement: Settings Input for Relay URL and Token

The prototype SHALL provide text input fields for the relay URL and authentication token. Values SHALL be persisted to SharedPreferences and loaded on app start. The settings MUST be accessible before any session creation.

#### Scenario: Enter relay URL and token -- save to SharedPreferences

WHEN the user enters `wss://relay.example.com` in the relay URL field and a token string in the token field
AND taps the "Save" button (or settings auto-save on change)
THEN both values are persisted to SharedPreferences
AND on next app launch, the fields are pre-filled with the saved values

#### Scenario: Empty relay URL -- connection not attempted

WHEN the relay URL field is empty
THEN the WebSocket client does not attempt connection
AND the status text shows "Not configured"

#### Scenario: Modify settings while connected -- reconnect

WHEN the user changes the relay URL or token while connected
AND taps "Save"
THEN the existing WebSocket connection is closed
AND a new connection is established with the updated URL/token

### Requirement: Create Session Button

The prototype SHALL provide a "Create Session" button that sends a `POST /v1/sessions` request to the relay. The request body SHALL use the configured provider, cwd, and prompt (which MAY be editable text fields or hardcoded defaults for Phase 0). After creation, the prototype automatically subscribes to the new session's event stream via WebSocket.

#### Scenario: Tap create -- POST /sessions -- start WS subscription

WHEN the user taps "Create Session" with provider="claude", cwd="/Users/dev/project", prompt="analyze this code"
THEN the app sends `POST /v1/sessions` with `{ provider: "claude", host_id: "macbook-1", cwd: "/Users/dev/project", prompt: "analyze this code", permission_mode: "bypassPermissions" }` to the relay
AND on 201 response, the app extracts the `session.id`
AND sends a `subscribe` message over WebSocket for that session_id

#### Scenario: Create session while disconnected

WHEN the user taps "Create Session" while the WebSocket is disconnected
THEN the HTTP POST may still succeed (REST is independent of WS)
AND the app shows a warning that real-time events may not arrive until reconnected

#### Scenario: Create session -- relay returns error

WHEN the relay returns 502 (host offline)
THEN the app displays the error message from the response body
AND the "Create Session" button is re-enabled

### Requirement: Raw Event Display

The prototype SHALL display incoming session events in a scrollable `LazyColumn`. Each event is shown as a raw JSON string (no formatting or pretty-printing required). New events appear at the bottom of the list. The list auto-scrolls to the latest event.

#### Scenario: Events appear in list as they arrive

WHEN the WebSocket receives `event` messages for the subscribed session
THEN each event's JSON payload is appended to the list
AND the list scrolls to show the newest event

#### Scenario: Many events -- performance acceptable

WHEN 500+ events arrive in rapid succession
THEN the LazyColumn renders without dropped frames or ANR
AND memory usage remains bounded (events stored as strings in ViewModel list)

#### Scenario: No events yet -- empty state

WHEN a session is created but no events have arrived yet
THEN the list area shows a "Waiting for events..." placeholder

### Requirement: Connection Status Indicator

The prototype SHALL display a status text showing the current WebSocket connection state: "Connected", "Disconnected", "Connecting...", or "Not configured". The indicator MUST update in real-time as the connection state changes.

#### Scenario: Disconnect indicator shows correctly

WHEN the WebSocket connection drops
THEN the status text changes from "Connected" to "Disconnected" within 1 second
AND the text color changes to indicate the error state (e.g., red)

WHEN the WebSocket successfully reconnects
THEN the status text changes to "Connected"
AND the text color returns to normal (e.g., green)

#### Scenario: Connecting state visible

WHEN a connection attempt is in progress
THEN the status text shows "Connecting..."

### Requirement: State Preservation Across Configuration Changes

The prototype SHALL preserve UI state (event list, connection state, input field values) across device rotation and other configuration changes using ViewModel and `rememberSaveable`.

#### Scenario: Rotate device -- state preserved

WHEN the device is rotated from portrait to landscape while events are displayed
THEN the event list retains all previously received events
AND the connection status remains accurate
AND the settings fields retain their values
