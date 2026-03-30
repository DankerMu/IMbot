# Proposal: p1-reconnect-and-catchup

## Why

Network interruptions are inevitable across all three tiers of the IMbot system (Android to relay, companion to relay, relay to OpenClaw gateway). The PRD mandates "断线恢复后事件完整性 100%（无丢失事件）" and "WSS 连接稳定性 > 99%". Without robust reconnection and event catch-up, any network blip results in lost events and a broken user experience.

Each tier has different reconnection characteristics:
- **Android**: user-facing, must show connection status and catch up seamlessly.
- **Companion**: running CLI processes survive disconnects, events must be buffered and flushed.
- **OpenClaw**: gateway restart kills active sessions, acceptable event loss in that scenario.
- **Relay**: must serve catch-up queries efficiently from SQLite event store.

## What Changes

| Area | Change |
|------|--------|
| Android WS client | OkHttp WebSocket auto-reconnect with exponential backoff (1s → 30s max). ConnectionBanner UI component. Resume subscriptions after reconnect. |
| Android event catch-up | After reconnect, `GET /events?since_seq=lastKnownSeq` per subscribed session. Merge into Room cache. Dedup by seq. "Syncing" indicator during catch-up. |
| Companion WSS reconnect | Exponential backoff reconnect. On reconnect: re-send heartbeat, report running process status, flush buffered events. |
| OpenClaw bridge reconnect | Bridge reconnects to localhost gateway. Re-map active sessions. Accept event loss on gateway restart. |
| Relay catch-up API | `GET /v1/sessions/:id/events?since_seq=N&limit=500` with `has_more` flag. Efficient SQLite query using existing `idx_events_session_seq` index. |

## Capabilities

- `android-ws-reconnect` — OkHttp WebSocket auto-reconnect with exponential backoff.
- `android-event-catchup` — Post-reconnect event catch-up and local cache merge.
- `companion-reconnect` — Companion WSS reconnect with re-registration and event flush.
- `openclaw-reconnect` — OpenClaw bridge reconnect with session re-mapping.
- `relay-catchup-api` — REST endpoint for paginated event catch-up queries.

## Dependencies

- `p0-relay-minimal` must have `session_events` table with `idx_events_session_seq` index.
- `p0-companion-minimal` must have base WSS client infrastructure.
- `p0-android-prototype` must have base WS client and Room database.
- `p1-relay-session-lifecycle` for seq allocation guarantees.

## Risk

| Risk | Mitigation |
|------|-----------|
| Large event backlogs during extended disconnect (10min+) | Paginated catch-up with `limit=500` and `has_more`; Android processes pages sequentially |
| Companion event buffer grows unbounded during disconnect | Cap buffer at 10,000 events; drop oldest if exceeded; log warning |
| Seq gap detection false positives during catch-up | Only check seq continuity after catch-up completes, not during |
| Reconnect storms after relay restart | Jitter added to backoff (random 0-1s) to spread companion reconnections |
