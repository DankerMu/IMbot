# Design: p2-android-session-list

## Key Decisions

### 1. Cache-First Loading Strategy

**Decision**: On app launch, immediately display sessions from Room cache, then refresh from the API in the background. On success, merge new data into cache and update UI. On failure, keep showing cached data with a Snackbar error.

**Rationale**: The user should never stare at a blank loading screen when cached data is available. This pattern is standard for offline-first mobile apps.

### 2. Local Filtering (No API Re-fetch)

**Decision**: Provider filter is applied client-side on the full session list already in memory/cache. Changing the filter does not trigger a new API call.

**Rationale**: The session list is small (single user, max ~200 sessions). Client-side filtering is instantaneous. Re-fetching with a server-side filter would add unnecessary latency and network calls.

### 3. SharedPreferences for Filter Persistence

**Decision**: The selected provider filter is stored in `SharedPreferences` and restored on app launch.

**Rationale**: Simple key-value persistence for a single enum value. Room or DataStore would be overkill.

### 4. Running Sessions Pinned to Top

**Decision**: Sessions with `status == running` are always displayed at the top of the list, separated from other sessions by a visual divider. Within each group, sessions are sorted by `lastActiveAt` descending.

**Rationale**: Running sessions are the most important -- the user launched a task and wants to see its progress immediately. Pinning prevents running sessions from being buried under completed ones.

### 5. Real-Time Updates via WebSocket

**Decision**: The `HomeViewModel` subscribes to session status changes via the WebSocket connection (managed by `SessionService`). When a `status` message arrives for any session, the list updates in real-time without requiring a manual refresh.

**Rationale**: The WebSocket connection is already maintained by `SessionService`. Leveraging it for list updates provides a live dashboard experience.

### 6. Bottom Navigation with Badge

**Decision**: Material 3 `NavigationBar` with 3 tabs. The 会话 tab shows a badge dot when there are running sessions and the user is on another tab.

**Rationale**: Badge dot draws attention to active sessions without being intrusive. Material 3 `NavigationBar` is the standard component.

## State Architecture

```
HomeViewModel
    │
    ├── SessionRepository
    │   ├── Room DAO (local cache)
    │   ├── REST API (remote)
    │   └── WS subscription (real-time status)
    │
    └── HomeUiState (StateFlow)
        ├── sessions: List<SessionSummary>
        ├── filter: Provider?
        ├── isLoading: Boolean
        ├── isRefreshing: Boolean
        ├── error: String?
        └── isConnected: Boolean
```

## Loading Sequence

```
App launch
    │
    ├─ 1. Read filter from SharedPreferences
    ├─ 2. Query Room cache → emit sessions (immediate)
    ├─ 3. Call GET /v1/sessions → on success:
    │      ├─ Upsert all sessions into Room
    │      └─ Re-query Room → emit updated sessions
    │   on error:
    │      └─ Emit Snackbar error (keep cached data)
    └─ 4. Subscribe to WS status events → on status change:
           ├─ Update Room cache for that session
           └─ Re-query Room → emit updated sessions
```

## Sorting/Filtering Pipeline

```kotlin
fun applyFilterAndSort(sessions: List<SessionSummary>, filter: Provider?): List<SessionSummary> {
    return sessions
        .filter { filter == null || it.provider == filter }
        .sortedWith(
            compareByDescending<SessionSummary> { it.status == SessionStatus.RUNNING }
                .thenByDescending { it.lastActiveAt }
        )
}
```
