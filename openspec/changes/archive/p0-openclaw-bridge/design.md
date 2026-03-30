# Design: p0-openclaw-bridge

## Overview

The OpenClaw bridge is a relay-internal module that acts as a WebSocket client to the local OpenClaw gateway (`ws://localhost:18789`). It mirrors the role of CompanionManager but for the co-located OpenClaw provider. The bridge translates between OpenClaw's native event protocol and the relay's unified event model.

## Module Structure

```
packages/relay/src/openclaw/
├── bridge.ts              # WebSocket client, reconnect logic, session operations
├── event-translator.ts    # OpenClaw event → relay EventType mapping
└── types.ts               # OpenClaw-specific type definitions
```

## Key Design Decisions

### 1. Single WebSocket, Multiplexed Sessions

The bridge maintains a single WebSocket connection to the gateway. All sessions are multiplexed over this connection, identified by `session_key`. This mirrors how the gateway itself works -- one gateway process, many concurrent sessions.

### 2. Reconnect Strategy: Fixed 30s Interval

Unlike the companion (which uses exponential backoff), the OpenClaw gateway is local and either running or not. A fixed 30-second retry is simpler and sufficient. No jitter is needed since there's only one client.

### 3. Session Key Mapping

The bridge maintains `Map<string, string>` in both directions:
- `relaySessionId → openclawSessionKey` (for outgoing commands)
- `openclawSessionKey → relaySessionId` (for incoming events)

This mapping is in-memory only. If the relay restarts, running OpenClaw sessions are lost (they transition to `failed` on reconnect since the gateway may have cleaned up).

### 4. Event Translation as Pure Function

`translateEvent(openclawEvent) → { type: EventType, payload: object }` is a stateless pure function. This makes it easy to unit test against the mapping table without requiring a live gateway.

### 5. Failure Cascade on Disconnect

When the gateway connection drops, ALL running OpenClaw sessions must be transitioned to `failed`. This is necessary because:
- The gateway may have crashed, losing all session state.
- Even if it restarts, session_keys may be invalidated.
- The user should be notified immediately rather than left waiting.

## Integration Points

| Component | Integration |
|-----------|------------|
| SessionOrchestrator | Calls `bridge.createSession()` when `provider === 'openclaw'` |
| SessionOrchestrator | Receives translated events via `orchestrator.handleEvent()` |
| WsHub | Bridge does NOT directly broadcast; it goes through the orchestrator |
| Health endpoint | Reads `bridge.isAvailable()` for healthz response |
| Host status | OpenClaw availability is part of relay-local host status |

## Error Handling

| Error | Handling |
|-------|---------|
| `ECONNREFUSED` on connect | Mark unavailable, schedule 30s retry |
| WebSocket `error` event | Log, close socket, mark unavailable, start retry |
| Gateway ack timeout (30s) | Reject the pending Promise, session → failed |
| Unknown event type | Log warning, discard event |
| Malformed event payload | Log warning, discard event |
