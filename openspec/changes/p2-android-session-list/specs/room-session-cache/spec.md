# Capability: room-session-cache

Room database caching of session summaries for instant startup and offline access. Implements cache-first strategy: display cached data immediately, refresh from API in background, merge updates.

## ADDED Requirements

### Requirement: Cache Session Summaries in Room

The Android app SHALL cache session summaries (not full event history) in a Room `sessions` table. The `SessionDao` SHALL support: `insertOrUpdate(sessions)`, `getAll()`, `getByProvider(provider)`, `deleteById(id)`, `deleteAll()`.

#### Scenario: Fresh install -- empty cache -- loading -- API -- populated

WHEN the app is launched for the first time (fresh install)
THEN the Room cache is empty
AND a loading skeleton (shimmer) is shown
AND `GET /v1/sessions` is called
AND on success, sessions are inserted into Room
AND the list is populated from the freshly cached data

#### Scenario: Subsequent launch -- cache shown immediately -- API refresh -- merged

WHEN the app is launched with existing cached sessions
THEN the cached sessions are displayed immediately (no loading state)
AND `GET /v1/sessions` is called in the background
AND on success, the cache is updated (new sessions added, existing ones updated, deleted ones removed)
AND the list reflects the merged data without visible flicker

---

### Requirement: Offline Mode

When the app is offline (API unreachable), the session list SHALL display data from the Room cache without showing a refresh error prominently.

#### Scenario: Offline -- cache shown -- no refresh error shown (silent)

WHEN the app launches without network connectivity
THEN the Room cache is displayed
AND no error Snackbar is shown for the initial load (silent fail)
AND the `ConnectionBanner` may indicate disconnected state
AND the user can browse cached sessions and open cached session details

---

### Requirement: Cache Consistency on Deletion

When the API returns a session list that no longer contains a previously cached session, that session SHALL be removed from the Room cache.

#### Scenario: API returns deleted session -- removed from cache

WHEN the Room cache contains sessions [A, B, C]
AND the API refresh returns only [A, C]
THEN session B is deleted from the Room cache
AND the list shows only [A, C]

---

### Requirement: Cache Update on WebSocket Status Change

When a session status change is received via WebSocket, the Room cache SHALL be updated for that specific session.

#### Scenario: WS status update -- cache updated

WHEN a WebSocket `status` message arrives for session "sess-123" with `status: "completed"`
THEN the Room cache row for "sess-123" is updated to `status = "completed"`
AND the UI list reflects the change immediately
