# Tasks: p1-reconnect-and-catchup

## 1. Shared Backoff Utility

- [ ] 1.1 Implement `ExponentialBackoff` class in `packages/wire/src/backoff.ts` with configurable base, max, jitter
- [ ] 1.2 Write unit tests: delay sequence correctness, max cap, jitter range, reset behavior

## 2. Relay Catch-Up API

- [ ] 2.1 Implement `GET /v1/sessions/:id/events` route with `since_seq`, `limit`, `has_more` logic
- [ ] 2.2 Use `LIMIT + 1` technique for `has_more` detection without extra COUNT query
- [ ] 2.3 Validate input: since_seq must be non-negative integer, session must exist
- [ ] 2.4 Cap limit at 500
- [ ] 2.5 Write unit tests: since_seq=0 all events, partial fetch, pagination with has_more, empty result, invalid since_seq (negative, NaN), session not found, limit cap, missing since_seq parameter

## 3. Android WebSocket Reconnection

- [ ] 3.1 Implement `ExponentialBackoff` in Kotlin (mirror wire package logic)
- [ ] 3.2 Add reconnection loop to `WsManager` with `ConnectionState` sealed class and StateFlow
- [ ] 3.3 Implement `SubscriptionTracker` to track and replay subscriptions on reconnect
- [ ] 3.4 Register `NetworkCallback` with ConnectivityManager for immediate reconnect on network change
- [ ] 3.5 Implement `ConnectionBanner` Compose component observing ConnectionState
- [ ] 3.6 Write unit tests: backoff sequence, state transitions, subscription replay, banner visibility states

## 4. Android Event Catch-Up

- [ ] 4.1 Add `getMaxSeq()` and `insertAll(onConflict=IGNORE)` to Room EventDao
- [ ] 4.2 Implement `CatchUpManager` with per-session paginated catch-up loop
- [ ] 4.3 Implement parallel catch-up for multiple sessions with syncing StateFlow
- [ ] 4.4 Handle catch-up during active streaming (dedup via INSERT OR IGNORE)
- [ ] 4.5 Add seq continuity check after catch-up completes (warn on gap)
- [ ] 4.6 Write unit tests: single page catch-up, multi-page pagination, duplicate handling, concurrent streaming + catch-up, seq gap detection, syncing indicator lifecycle

## 5. Companion Reconnection

- [ ] 5.1 Integrate `ExponentialBackoff` from `@imbot/wire` into companion `ws-client.ts`
- [ ] 5.2 Implement `EventBuffer` with bounded size (10,000) and oldest-first eviction
- [ ] 5.3 Buffer events during disconnect, flush in order on reconnect
- [ ] 5.4 Send heartbeat immediately on reconnect (re-register host)
- [ ] 5.5 Report running session statuses on reconnect
- [ ] 5.6 Write unit tests: buffer push/flush, overflow eviction, reconnect flow (heartbeat + flush), running processes survive disconnect

## 6. OpenClaw Bridge Reconnection

- [ ] 6.1 Add reconnection loop to `OpenClawBridge` with ExponentialBackoff
- [ ] 6.2 Track `available` state; reject new openclaw sessions when unavailable
- [ ] 6.3 On disconnect: mark active openclaw sessions as failed
- [ ] 6.4 On reconnect: check for live sessions (brief hiccup case), restore availability
- [ ] 6.5 Write unit tests: disconnect → unavailable → reconnect → available, active session failure on disconnect, brief hiccup session survival

## 7. Integration Testing

- [ ] 7.1 End-to-end test: Android connect → disconnect → reconnect → catch-up → events consistent
- [ ] 7.2 End-to-end test: Companion disconnect → events buffered → reconnect → events flushed → Android receives all
- [ ] 7.3 End-to-end test: OpenClaw gateway restart → sessions failed → bridge reconnects → new sessions work
