# Tasks: p0-openclaw-bridge

## 1. OpenClaw Bridge Connection

- [ ] 1.1 Create `packages/relay/src/openclaw/types.ts` with OpenClaw-specific type definitions (gateway messages, session_key, event types)
- [ ] 1.2 Implement `OpenClawBridge` class in `packages/relay/src/openclaw/bridge.ts` with WebSocket client to `ws://localhost:18789`
- [ ] 1.3 Implement auto-reconnect with fixed 30-second interval using `setTimeout`
- [ ] 1.4 Add `isAvailable(): boolean` method that returns `true` only when WebSocket is in OPEN state
- [ ] 1.5 On WebSocket `close`/`error`, transition all running OpenClaw sessions to `failed` with `provider_unreachable`
- [ ] 1.6 On successful reconnect, cancel the retry timer and mark openclaw as available
- [ ] 1.7 Integrate bridge status into `GET /healthz` endpoint (`"openclaw": "online" | "offline"`)
- [ ] 1.8 Initialize bridge connection during relay server startup (non-blocking, relay starts even if gateway is down)
- [ ] 1.9 Write unit tests: connect success, connect failure + retry, disconnect detection, reconnect success, healthz status reflection

## 2. OpenClaw Session Management

- [ ] 2.1 Implement `createSession(relaySessionId, cwd, prompt): Promise<void>` in bridge -- send create message to gateway, await ack with `session_key`
- [ ] 2.2 Implement bidirectional `Map<string, string>` for `relaySessionId ↔ session_key` mapping
- [ ] 2.3 Implement `resumeSession(relaySessionId, openclawSessionKey): Promise<void>` -- send resume to gateway
- [ ] 2.4 Implement `sendMessage(relaySessionId, text): Promise<void>` -- lookup session_key and forward text
- [ ] 2.5 Implement `cancelSession(relaySessionId): Promise<void>` -- send cancel to gateway, remove mapping on ack
- [ ] 2.6 Add 30-second timeout for each gateway command (create, resume, cancel) -- reject Promise on timeout
- [ ] 2.7 Handle gateway offline case for all operations: return `provider_unreachable` error immediately
- [ ] 2.8 Clean up session_key mapping when session reaches terminal state (completed, failed, cancelled)
- [ ] 2.9 Wire SessionOrchestrator to route `provider === 'openclaw'` to bridge instead of CompanionManager
- [ ] 2.10 Write unit tests: create success, create offline, create timeout, resume valid/invalid, send message, cancel, concurrent session mapping, mapping cleanup

## 3. OpenClaw Event Translation

- [ ] 3.1 Create `packages/relay/src/openclaw/event-translator.ts` with `translateEvent(openclawEvent): { type: EventType, payload: object } | null`
- [ ] 3.2 Implement mapping: `transcript.text` (partial) → `assistant_delta`
- [ ] 3.3 Implement mapping: `transcript.text` (complete) → `assistant_message`
- [ ] 3.4 Implement mapping: `tool.start` → `tool_call_started`
- [ ] 3.5 Implement mapping: `tool.end` → `tool_call_completed`
- [ ] 3.6 Implement mapping: `session.ready` → `session_started`
- [ ] 3.7 Implement mapping: `session.complete` → `session_result`
- [ ] 3.8 Implement mapping: `session.error` → `session_error`
- [ ] 3.9 Implement mapping: `message.user` → `user_message`
- [ ] 3.10 Handle unknown event types: return `null`, log warning with event type and session_key
- [ ] 3.11 Handle malformed events (missing type field, empty payload): return `null`, log warning
- [ ] 3.12 Wire translated events into `SessionOrchestrator.handleEvent()` via the bridge's WebSocket `message` handler
- [ ] 3.13 Ensure per-session event ordering is preserved (process events sequentially per session_key)
- [ ] 3.14 Write unit tests: each mapping individually, unknown event, malformed event, empty payload, rapid burst ordering
