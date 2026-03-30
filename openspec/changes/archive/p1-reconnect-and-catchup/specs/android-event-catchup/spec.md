# Capability: android-event-catchup

## ADDED Requirements

### Requirement: Post-Reconnect Event Catch-Up

After a successful WebSocket reconnection, the Android client SHALL fetch missed events for each subscribed session by calling `GET /v1/sessions/:id/events?since_seq=<lastKnownSeq>`. Events MUST be merged into the local Room cache in seq order. Duplicate events (same seq) MUST be ignored.

#### Scenario: catch up after short disconnect (60s, 50 events)

WHEN the client reconnects after a 60-second disconnect
AND session "sess-1" has 50 new events since the last known seq (seq 100)
THEN the client calls `GET /v1/sessions/sess-1/events?since_seq=100`
AND receives 50 events (seq 101-150)
AND all 50 events are inserted into the Room database in seq order
AND the UI updates to show the missed content

#### Scenario: catch up after long disconnect (10min, 500+ events)

WHEN the client reconnects after a 10-minute disconnect
AND session "sess-1" has 600 new events since the last known seq
THEN the client calls `GET /v1/sessions/sess-1/events?since_seq=100&limit=500`
AND receives 500 events with `has_more: true`
AND inserts the 500 events into Room
AND calls again with `since_seq=600` (last received seq)
AND receives the remaining 100 events with `has_more: false`
AND all 600 events are now in the local cache

#### Scenario: no missed events during catch-up (seq continuity)

WHEN catch-up completes for a session
THEN the local event cache has no seq gaps: every integer from 1 to max_seq is present
AND the client verifies seq continuity after catch-up

#### Scenario: duplicate event is ignored

WHEN the catch-up response contains an event with a seq that already exists in the local Room cache
THEN the duplicate event is silently ignored (INSERT OR IGNORE)
AND no error is raised

#### Scenario: catch-up during active streaming

WHEN catch-up is in progress for session "sess-1"
AND new real-time events arrive via WebSocket for the same session
THEN both catch-up events and real-time events are merged into Room
AND duplicates (same seq) are handled via INSERT OR IGNORE
AND the final state is consistent with no gaps

#### Scenario: catch-up finds seq gap

WHEN catch-up completes and the client detects a seq gap (e.g., seq 100, 101, 103 — missing 102)
THEN the client logs the gap as a warning
AND shows a non-blocking error indicator to the user
AND continues operating (does not crash or block)

---

### Requirement: Syncing Indicator

The Android app SHALL show a "syncing" indicator while event catch-up is in progress. The indicator MUST hide after all catch-up queries complete for all subscribed sessions.

#### Scenario: syncing indicator shows during catch-up

WHEN catch-up begins after reconnect
THEN a "Syncing..." indicator appears (e.g., in the session detail header or connection banner)
AND the indicator shows progress if multiple pages are being fetched

#### Scenario: syncing indicator hides after completion

WHEN catch-up completes for all subscribed sessions
THEN the "Syncing..." indicator hides
AND the UI returns to normal state

#### Scenario: syncing indicator for multiple sessions

WHEN the client is catching up on 3 sessions simultaneously
THEN the syncing indicator remains visible until ALL 3 sessions have completed catch-up

---

### Requirement: Catch-Up Ordering

The Android client SHALL process catch-up events strictly in seq order per session. Cross-session ordering is not required (sessions are independent).

#### Scenario: events inserted in seq order

WHEN catch-up returns events [seq 105, seq 103, seq 104] (hypothetical out-of-order response)
THEN events are sorted by seq before insertion into Room
AND the UI renders them in correct seq order
