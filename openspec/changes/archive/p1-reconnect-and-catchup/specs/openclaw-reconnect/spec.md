# Capability: openclaw-reconnect

## ADDED Requirements

### Requirement: Bridge Reconnection to Gateway

The OpenClaw bridge on the relay SHALL automatically reconnect to the localhost OpenClaw gateway when the connection is lost. Reconnection MUST use exponential backoff (1s, 2s, 4s, 8s, 16s, 30s max).

#### Scenario: gateway restarts and bridge reconnects

WHEN the OpenClaw gateway process restarts on the relay VPS
AND the bridge's WebSocket connection to `ws://localhost:18789` drops
THEN the bridge starts reconnection with exponential backoff
AND on successful reconnect, the bridge marks OpenClaw provider as "available"
AND new sessions can be created via OpenClaw

#### Scenario: bridge detects disconnect and marks unavailable

WHEN the bridge loses its connection to the OpenClaw gateway
THEN the bridge immediately marks the OpenClaw provider as "unavailable"
AND subsequent `create_session` requests with `provider: "openclaw"` receive `502 provider_unreachable`
AND the bridge continues reconnection attempts in the background

#### Scenario: bridge reconnects and restores availability

WHEN the bridge successfully reconnects to the gateway
THEN the OpenClaw provider status changes to "available"
AND the relay broadcasts `{ type: "host_status", host_id: "relay-local", status: "online" }` to connected Android clients

---

### Requirement: Active Session Handling During Gateway Restart

Active OpenClaw sessions SHALL be treated as lost when the gateway restarts, since the gateway does not persist session state across restarts. This is an acceptable trade-off documented in the design.

#### Scenario: active session during gateway disconnect

WHEN an OpenClaw session "sess-oc-1" is running
AND the gateway restarts
THEN the bridge detects the disconnect
AND the relay transitions session "sess-oc-1" to `failed` with `error_code: "provider_unreachable"` and `message: "OpenClaw gateway restarted"`
AND the Android client sees the session status change

#### Scenario: no event catch-up for OpenClaw after gateway restart

WHEN the bridge reconnects after a gateway restart
THEN the bridge does NOT attempt to resume or catch up events for sessions that were active during the restart
AND those sessions remain in `failed` state (user can create new sessions)

---

### Requirement: Session Re-Mapping on Reconnect

If the gateway reconnects without a full restart (e.g., brief network hiccup on localhost), the bridge SHALL attempt to re-map active sessions by checking their status with the gateway.

#### Scenario: brief localhost hiccup without gateway restart

WHEN the WebSocket to the gateway drops but the gateway process continues running
AND the bridge reconnects within 30 seconds
THEN the bridge queries the gateway for active session status
AND sessions that are still active on the gateway continue event forwarding
AND no sessions are marked as failed
