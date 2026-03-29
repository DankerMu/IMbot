# Capability: connection-stability

WebSocket ping/pong, idle timeout, heartbeat stale detection, foreground service lifecycle, and network change handling.

## ADDED Requirements

### Requirement: WebSocket Ping/Pong (Both Directions)

The relay server SHALL send a WebSocket protocol-level ping frame to all connected clients (Android and companion) every 30 seconds. Clients SHALL automatically respond with pong (handled by the WebSocket library). The Android client SHALL also send application-level `{ action: "ping" }` messages every 30 seconds and expect `{ type: "pong" }` responses for connection health monitoring.

#### Scenario: Normal ping/pong -- connection maintained 24h+

WHEN the relay and Android are connected with regular ping/pong exchange
THEN the connection remains stable for 24+ hours
AND no unexpected disconnections occur

#### Scenario: One missed pong -- still ok

WHEN one pong response is not received within the expected interval
THEN the connection is NOT immediately closed
AND the next ping is sent normally
AND if the next pong is also missed (60s total), then the connection is considered dead

### Requirement: Idle Timeout (60 Seconds)

The relay server SHALL close WebSocket connections that have had no activity (no messages sent or received, no ping/pong) for 60 seconds. The client SHALL detect the close and trigger reconnection. This prevents zombie connections from accumulating on the relay.

#### Scenario: 60s no activity -- connection closed and reconnects

WHEN no messages or pings are exchanged for 60 seconds
THEN the relay closes the WebSocket connection
AND the Android client detects the close event
AND the Android client initiates reconnection with exponential backoff

### Requirement: Companion Heartbeat Stale Detection

The relay SHALL monitor companion heartbeat timestamps. If a companion has not sent a heartbeat for 90 seconds, the relay SHALL mark the host as `offline` and broadcast `host_status: offline` to all connected Android clients. The 90-second threshold accounts for two missed heartbeats (30s interval) plus network jitter.

#### Scenario: Companion heartbeat stale -- host marked offline

WHEN a companion's last heartbeat was 91 seconds ago
THEN the relay updates the host status to `offline` in the database
AND the relay broadcasts `{ type: "host_status", host_id, status: "offline" }` to Android clients
AND the Android UI updates to show the host as offline

#### Scenario: Companion recovers -- next heartbeat restores online

WHEN an offline companion sends a new heartbeat
THEN the relay updates the host status to `online`
AND broadcasts `host_status: online` to Android clients

### Requirement: Android Foreground Service Lifecycle

The Android foreground service (SessionService) SHALL start when the app opens (Activity.onStart). The service SHALL stop when ALL of the following are true: (a) no sessions are in `running` or `queued` status, AND (b) the app has been in the background for 5 minutes. The foreground notification SHALL show the count of active sessions while running.

#### Scenario: Foreground service starts on app open

WHEN the user opens the IMbot app
THEN SessionService starts as a foreground service
AND a persistent notification shows "IMbot 运行中"

#### Scenario: Foreground service stops after 5min background + no running sessions

WHEN all sessions are completed/failed/cancelled
AND the app moves to background
AND 5 minutes pass with no new session activity
THEN SessionService stops
AND the persistent notification is removed

#### Scenario: Foreground service continues with running sessions even in background

WHEN the app moves to background but sessions are still running
THEN SessionService continues running
AND the notification shows "n 个会话运行中"
AND WebSocket remains connected to receive events

#### Scenario: Battery saver mode -- foreground service continues

WHEN the device enters battery saver mode
THEN the foreground service continues running (important notification channel)
AND WebSocket ping/pong continues normally

### Requirement: Network Change Listener -- Force Reconnect

The Android app SHALL register a `ConnectivityManager.NetworkCallback` to detect network changes (WiFi ↔ mobile data switches). On a network change, the app SHALL immediately close the current WebSocket connection and open a new one to the relay, bypassing the exponential backoff delay.

#### Scenario: Network switch -- immediate reconnect

WHEN the device switches from WiFi to mobile data (or vice versa)
THEN the current WebSocket connection is closed
AND a new WebSocket connection is opened immediately (no backoff delay)
AND the ConnectionBanner briefly shows "正在重连..." then clears on success

#### Scenario: Network lost completely -- wait for availability

WHEN the device loses all network connectivity
THEN the WebSocket connection fails
AND reconnection attempts pause until the NetworkCallback reports a network is available
AND when a network becomes available, reconnection starts immediately
