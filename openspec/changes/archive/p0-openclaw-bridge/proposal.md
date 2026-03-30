# Proposal: p0-openclaw-bridge

## Why

OpenClaw is the third AI provider in the IMbot system, running directly on the relay VPS itself. Unlike Claude and book which require the MacBook companion to spawn CLI processes, OpenClaw runs a local gateway on port 18789. The relay needs a bridge module that connects to this local gateway via WebSocket, translates OpenClaw-native events into the relay's unified event model, and manages session lifecycle through the gateway's own protocol. Without this bridge, OpenClaw sessions cannot be created, monitored, or controlled from the Android app.

## What Changes

1. **OpenClaw bridge connection** -- A WebSocket client inside the relay that connects to `ws://localhost:18789` (the OpenClaw gateway). The connection auto-reconnects on failure with a 30-second interval. Gateway availability is tracked and reported via the `/healthz` endpoint and host status broadcasts.
2. **OpenClaw session management** -- Session lifecycle operations (create, resume, send message, cancel) are sent to the OpenClaw gateway. The bridge maintains a bidirectional mapping between relay `session_id` and OpenClaw `session_key` so that events from the gateway can be routed to the correct relay session.
3. **OpenClaw event translation** -- OpenClaw emits its own event types (`transcript.text`, `session.complete`, `tool.start`, etc.). The bridge translates each into the relay's unified `EventType` enum (`assistant_delta`, `session_result`, `tool_call_started`, etc.) before handing them to the SessionOrchestrator for seq allocation and broadcasting.

## Capabilities

- `openclaw-bridge-connection`
- `openclaw-session-management`
- `openclaw-event-translation`

## Affected Areas

- `packages/relay/src/openclaw/bridge.ts` (new module)
- `packages/relay/src/openclaw/event-translator.ts` (new module)
- `packages/relay/src/session/orchestrator.ts` (integration point -- route openclaw provider to bridge)
- `packages/wire/src/events.ts` (no schema change, but OpenClaw events map to existing EventType)
- `packages/relay/src/routes/health.ts` (report openclaw gateway status)

## Risks

- OpenClaw gateway event format may drift from the documented mapping table in BUSINESS_LOGIC.md. The translation layer must be validated against the actual gateway during Phase 0.
- If the gateway is slow to respond, session creation may hit the 30s ack timeout. The bridge should propagate timeouts as `session_error` events.
- Concurrent sessions on a single gateway connection require multiplexing by `session_key`. Message ordering must be preserved per-session.

## References

- docs/engineering-spec/02_Technical_Design/ARCHITECTURE.md (OpenClaw Bridge module)
- docs/engineering-spec/02_Technical_Design/BUSINESS_LOGIC.md (Section 6: OpenClaw Event Translation)
- docs/engineering-spec/02_Technical_Design/API_SPEC.md (Companion internal protocol, adapted for OpenClaw)
