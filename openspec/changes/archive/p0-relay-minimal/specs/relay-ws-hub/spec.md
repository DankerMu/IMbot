# Capability: relay-ws-hub

## ADDED Requirements

### Requirement: Android WebSocket Connection Lifecycle

The relay server SHALL accept Android client WebSocket connections at `/v1/ws`, support session subscription, and relay events to subscribed clients.

#### Scenario: Android connects and subscribes to a session

WHEN an Android client opens a WebSocket to `/v1/ws?token=<valid-token>`
AND sends `{ "action": "subscribe", "session_id": "sess-123" }`
THEN the client is added to the broadcast group for session `sess-123`
AND subsequent events for `sess-123` are sent to this client

#### Scenario: Android receives session events after subscribe

WHEN an Android client is subscribed to session `sess-123`
AND a new event arrives for session `sess-123` (from companion)
THEN the client receives a ServerMessage: `{ "type": "event", "session_id": "sess-123", "seq": <n>, "event_type": "assistant_delta", "payload": {...}, "timestamp": "..." }`

#### Scenario: Android unsubscribes from a session

WHEN an Android client sends `{ "action": "unsubscribe", "session_id": "sess-123" }`
THEN the client is removed from the broadcast group for session `sess-123`
AND no more events for `sess-123` are sent to this client
AND the client remains connected (not disconnected)

#### Scenario: Android subscribes to multiple sessions simultaneously

WHEN an Android client subscribes to `sess-123` and `sess-456`
THEN it receives events for both sessions
AND each event message contains the correct `session_id` so the client can dispatch

#### Scenario: Android disconnects and cleans up

WHEN an Android client WebSocket connection closes (normal or abnormal)
THEN the client is removed from all session broadcast groups
AND no errors are thrown on the server side

#### Scenario: subscribe to non-existent session

WHEN an Android client subscribes to a session_id that does not exist in the database
THEN the subscription is accepted (optimistic -- events may arrive later)
AND no error is sent to the client

---

### Requirement: Companion WebSocket Connection Lifecycle

The relay server SHALL accept companion WebSocket connections at `/v1/companion`, register the host as online, and relay commands to the companion.

#### Scenario: companion connects and host goes online

WHEN a companion opens a WebSocket to `/v1/companion?token=<valid>&host_id=macbook-1`
THEN the host `macbook-1` is upserted in the `hosts` table with `status='online'`
AND the WsHub registers this connection for host `macbook-1`
AND all subscribed Android clients receive `{ "type": "host_status", "host_id": "macbook-1", "status": "online" }`

#### Scenario: companion disconnects and host goes offline

WHEN a companion WebSocket connection for `macbook-1` closes
THEN the host `macbook-1` status is updated to `'offline'` in the database
AND all subscribed Android clients receive `{ "type": "host_status", "host_id": "macbook-1", "status": "offline" }`

#### Scenario: companion sends heartbeat

WHEN a companion sends `{ "type": "heartbeat", "host_id": "macbook-1", "providers": ["claude", "book"], "uptime": 3600 }`
THEN the `hosts` table is updated: `last_heartbeat_at = now()`, `status = 'online'`

#### Scenario: companion sends event for a session

WHEN a companion sends `{ "type": "event", "session_id": "sess-123", "event_type": "assistant_delta", "payload": { "text": "hello" } }`
THEN the relay allocates a seq number for this event
AND stores it in `session_events`
AND broadcasts it to all Android clients subscribed to `sess-123`

#### Scenario: companion sends ack for a command

WHEN the relay has sent a command with `req_id: "req-abc"` to the companion
AND the companion replies `{ "type": "ack", "req_id": "req-abc", "status": "ok", "data": { "provider_session_id": "..." } }`
THEN the pending command promise for `req-abc` resolves with the ack data

#### Scenario: second companion for same host replaces first

WHEN a companion for `macbook-1` is already connected
AND a new companion connection opens for `macbook-1`
THEN the old connection is closed with code 4003 (replaced)
AND the new connection becomes the active companion for `macbook-1`

---

### Requirement: Ping/Pong Keepalive

The WebSocket hub SHALL implement ping/pong keepalive to detect dead connections.

#### Scenario: client sends ping and receives pong

WHEN an Android client sends `{ "action": "ping" }`
THEN the server responds with `{ "type": "pong" }`

#### Scenario: server sends WebSocket-level ping frames

WHEN a WebSocket connection has been idle for `RELAY_WS_PING_INTERVAL_MS` (default 30s)
THEN the server sends a WebSocket protocol-level ping frame
AND if the client responds with a pong frame, the connection stays alive

#### Scenario: dead connection is detected and cleaned up

WHEN a WebSocket connection fails to respond to 2 consecutive ping frames
THEN the server closes the connection
AND the client is removed from all subscription groups
AND if it was a companion, the host is marked offline

---

### Requirement: Event Broadcasting

The WebSocket hub SHALL broadcast events to all Android clients subscribed to the relevant session.

#### Scenario: event broadcast to multiple subscribers

WHEN 3 Android clients are subscribed to session `sess-123`
AND an event arrives for session `sess-123`
THEN all 3 clients receive the event message
AND each receives an identical message

#### Scenario: event not sent to unsubscribed clients

WHEN client A is subscribed to `sess-123` and client B is subscribed to `sess-456`
AND an event arrives for `sess-123`
THEN only client A receives the event
AND client B does not receive it

#### Scenario: host status broadcast to all connected clients

WHEN a companion for `macbook-1` disconnects
THEN ALL connected Android clients receive the host_status offline message
AND not just those subscribed to sessions on that host

#### Scenario: broadcast with no subscribers is a no-op

WHEN an event arrives for session `sess-999` which has no subscribers
THEN no error is thrown
AND the event is still stored in the database
