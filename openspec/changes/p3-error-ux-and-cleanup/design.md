# Design: p3-error-ux-and-cleanup

## Architecture Overview

This change spans all three components (relay, companion behavior awareness, Android). The relay gets a purge job and improved connection management. Android gets a hierarchical error state system, loading states, and connection lifecycle improvements.

```
Relay:
  node-cron (03:00 UTC) → PurgeJob → DELETE sessions WHERE stale
  WsHub → ping every 30s → idle timeout 60s → close stale
  CompanionManager → check heartbeat every 60s → mark offline if >90s

Android:
  ConnectionManager
  ├── WebSocket (ping/pong 30s)
  ├── NetworkCallback (force reconnect on switch)
  └── ErrorStateManager
      ├── Layer 1: ConnectionBanner (relay unreachable)
      ├── Layer 2: PageBanner (host offline)
      ├── Layer 3: SessionBanner (provider unreachable)
      └── Layer 4: Snackbar (transient)
  SessionService (foreground)
  ├── start on Activity.onStart
  └── stop after 5min background + no running sessions
```

## Key Design Decisions

### 1. Three-Layer Error State as Centralized StateFlow

**Decision**: Implement `ErrorStateManager` as a singleton (Hilt @Singleton) holding `StateFlow<ErrorState>` where `ErrorState` is a data class with `relayConnected: Boolean`, `hostStatuses: Map<String, Boolean>`, `sessionErrors: Map<String, String>`. Each screen observes the relevant subset.

**Rationale**: Centralized error state avoids duplication. Each screen composes its error display by reading the fields it cares about. Priority enforcement is simple: check Layer 1 first, then Layer 2, then Layer 3.

```kotlin
data class ErrorState(
    val relayConnected: Boolean = true,
    val isReconnecting: Boolean = false,
    val hostStatuses: Map<String, Boolean> = emptyMap(),  // hostId -> online
    val sessionErrors: Map<String, String> = emptyMap()   // sessionId -> error msg
)
```

### 2. Error Priority in Compose

**Decision**: Each screen uses a `@Composable fun ErrorBannerHost(errorState, scope)` that evaluates priority:

```kotlin
@Composable
fun ErrorBannerHost(errorState: ErrorState, scope: ErrorScope) {
    when {
        !errorState.relayConnected -> ConnectionBanner(...)
        scope == ErrorScope.WORKSPACE && !errorState.hostStatuses["macbook-1"] -> HostBanner(...)
        scope == ErrorScope.SESSION(id) && errorState.sessionErrors[id] != null -> SessionBanner(...)
    }
}
```

**Rationale**: Composable function naturally evaluates top-to-bottom, matching priority order. Only one banner renders per scope.

### 3. Purge Job Implementation

**Decision**: Use `node-cron` with schedule `'0 3 * * *'` (03:00 UTC daily). The job runs:

```sql
DELETE FROM sessions
WHERE id IN (
    SELECT id FROM sessions
    WHERE status IN ('completed', 'failed', 'cancelled')
      AND last_active_at < datetime('now', '-30 days')
    LIMIT 100
);
```

Loop until zero rows affected. Log count after each iteration.

**Rationale**: Batching with LIMIT 100 prevents long-running write transactions that could block concurrent reads. CASCADE handles events automatically. Loop ensures all qualifying sessions are purged even if > 100.

### 4. Android-Side Stale Session Cleanup

**Decision**: On session list refresh, compare relay response IDs with Room IDs. Delete from Room any IDs not present in relay response (for the loaded page). Use a simple set difference.

**Rationale**: No need for a dedicated "purge notification" channel. The diff-on-refresh approach is simple and handles all causes of server-side deletion (purge, manual delete, etc.).

**Edge case**: Paginated responses -- only diff within the loaded page range. Sessions beyond the loaded offset are not deleted from Room.

### 5. Foreground Service Lifecycle State Machine

**Decision**: Track service state with a simple enum: `ACTIVE` (sessions running or app foreground), `COOLING_DOWN` (app backgrounded, no running sessions, 5min timer started), `STOPPED`.

```
App opens → ACTIVE
App backgrounds + no running sessions → COOLING_DOWN (start 5min timer)
Timer expires → STOPPED (stopSelf)
App foregrounds during COOLING_DOWN → ACTIVE (cancel timer)
New session starts during COOLING_DOWN → ACTIVE (cancel timer)
```

**Rationale**: Three states cover all cases. The 5min cooldown prevents service churn (user switching apps briefly shouldn't kill the service).

### 6. Network Change Reconnect with Debounce

**Decision**: Register `ConnectivityManager.NetworkCallback`. On `onAvailable()` or `onCapabilitiesChanged()`, debounce 1 second, then close existing WS and open new connection (bypass backoff).

**Rationale**: Network callbacks fire multiple times during transitions (WiFi → mobile). The 1s debounce collapses these into one reconnect attempt. Closing the old connection first ensures clean state.

### 7. Shimmer Skeleton Design

**Decision**: Create reusable `ShimmerSkeleton` composable that accepts a `content` slot defining the skeleton shape. The shimmer effect is a horizontal gradient that sweeps left-to-right using `rememberInfiniteTransition` and `Brush.linearGradient`.

**Rationale**: One shimmer composable used across all screens. The shape is defined per-screen to match the expected content layout (SessionCard skeleton, directory entry skeleton, etc.).

### 8. Snackbar Host at Scaffold Level

**Decision**: Place `SnackbarHost` in the root `Scaffold` of each bottom-nav screen. Use `SnackbarHostState` shared via ViewModel or CompositionLocal. All transient errors call `snackbarHostState.showSnackbar()`.

**Rationale**: Material 3 standard pattern. SnackbarHost handles queuing and auto-dismiss. 4s duration is set via `SnackbarDuration.Short` (actually 4s in M3).

## File Structure

```
packages/relay/src/cleanup/
├── purge-job.ts            -- node-cron schedule, DELETE query, batch loop, logging

packages/relay/src/ws/
├── hub.ts                  -- (modified) add 30s ping interval, 60s idle timeout

packages/relay/src/companion/
├── manager.ts              -- (modified) add 60s heartbeat check interval, 90s stale threshold

packages/android/app/src/main/java/.../imbot/
├── data/
│   ├── remote/
│   │   └── ConnectionManager.kt   -- (modified) ping/pong, NetworkCallback, reconnect
│   └── ErrorStateManager.kt       -- centralized error state
├── service/
│   └── SessionService.kt          -- (modified) lifecycle state machine, 5min cooldown
├── ui/
│   ├── components/
│   │   ├── ConnectionBanner.kt     -- (modified from p3-theme) red/green/orange variants
│   │   ├── EmptyState.kt           -- reusable empty state with illustration + CTA
│   │   ├── ShimmerSkeleton.kt      -- shimmer effect composable
│   │   ├── InlineRetry.kt          -- error message + "重试" button
│   │   └── ErrorBannerHost.kt      -- priority-based banner composable
│   ├── home/
│   │   └── HomeScreen.kt           -- (modified) add empty state, shimmer, error handling
│   ├── detail/
│   │   └── DetailScreen.kt         -- (modified) add session-level error banner
│   └── workspace/
│       └── WorkspaceScreen.kt      -- (modified) add empty state, host offline banner
```
