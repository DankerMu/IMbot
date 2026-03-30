# Capability: android-ws-reconnect

## ADDED Requirements

### Requirement: Exponential Backoff Reconnection

The Android WebSocket client SHALL automatically attempt to reconnect when the connection is lost. Reconnection attempts MUST use exponential backoff with intervals of 1s, 2s, 4s, 8s, 16s, 30s (max). The backoff timer MUST reset to 1s after a successful reconnection.

#### Scenario: WebSocket disconnects and reconnects

WHEN the WebSocket connection to the relay is lost
THEN the client starts a reconnection timer at 1s
AND after 1s, attempts to reconnect
AND if the attempt fails, the next retry is scheduled at 2s
AND the backoff doubles on each failure: 1s → 2s → 4s → 8s → 16s → 30s

#### Scenario: backoff caps at 30 seconds

WHEN the reconnection has failed 6 or more times consecutively
THEN the backoff interval remains at 30s (does not increase further)
AND reconnection attempts continue indefinitely at 30s intervals

#### Scenario: successful reconnect resets backoff

WHEN the client successfully reconnects after N failed attempts
THEN the backoff counter resets to 1s
AND the next disconnection starts the backoff sequence from 1s again

#### Scenario: multiple disconnects in sequence

WHEN the client reconnects after backoff
AND the new connection drops again within 5 seconds
THEN the backoff continues from 1s (since it was reset on the successful connect)

#### Scenario: network type change triggers reconnect

WHEN the Android device switches from WiFi to mobile data (or vice versa)
AND the WebSocket connection is lost during the switch
THEN the reconnection logic triggers immediately (0s delay for first attempt after network change)
AND falls back to normal exponential backoff if the immediate attempt fails

---

### Requirement: Connection Status Banner

The Android app SHALL display a `ConnectionBanner` component that shows the current WebSocket connection status. The banner MUST be visible whenever the connection is not in a healthy state.

#### Scenario: banner shows during disconnection

WHEN the WebSocket connection is lost
THEN a banner appears at the top of the screen showing "Connecting..." with a progress indicator
AND the banner remains visible throughout all reconnection attempts

#### Scenario: banner shows reconnection countdown

WHEN a reconnection attempt fails and the next attempt is scheduled in N seconds
THEN the banner shows "Reconnecting in Ns..." with a countdown

#### Scenario: banner hides on successful connection

WHEN the WebSocket connection is established (initial or reconnect)
THEN the banner hides (with a brief "Connected" flash, then dismiss)

#### Scenario: banner shows during extended outage

WHEN the client has been disconnected for more than 60 seconds
THEN the banner updates to show "Connection lost. Retrying..." to indicate an extended outage

---

### Requirement: Subscription Resumption

After a successful reconnection, the Android client SHALL re-subscribe to all previously subscribed sessions by sending `subscribe` messages for each session.

#### Scenario: resubscribe after reconnect

WHEN the client reconnects to the relay
AND the client was previously subscribed to sessions ["sess-1", "sess-2", "sess-3"]
THEN the client sends `{ action: "subscribe", session_id: "sess-1" }`, `{ action: "subscribe", session_id: "sess-2" }`, `{ action: "subscribe", session_id: "sess-3" }`
AND event streaming resumes for all three sessions

#### Scenario: foreground service maintains reconnect loop

WHEN the Android app is in the background
AND a foreground service is running for active sessions
THEN the reconnection loop continues operating
AND reconnection attempts are not paused by the OS
