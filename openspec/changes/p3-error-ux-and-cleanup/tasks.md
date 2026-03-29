# Tasks: p3-error-ux-and-cleanup

## 1. Relay -- Data Purge Job

- [ ] 1.1 Create `packages/relay/src/cleanup/purge-job.ts`: register node-cron job at `'0 3 * * *'` (03:00 UTC daily)
- [ ] 1.2 Implement purge query: `DELETE FROM sessions WHERE id IN (SELECT id FROM sessions WHERE status IN ('completed','failed','cancelled') AND last_active_at < datetime('now','-30 days') LIMIT 100)`; loop until zero rows affected
- [ ] 1.3 Log purge count after each run: `[purge] {timestamp} - purged {count} sessions`
- [ ] 1.4 Wire purge job in `app.ts` or `index.ts`: start cron on server startup
- [ ] 1.5 Write tests: verify 30-day boundary (31 days purged, 29 days not), running sessions never purged, CASCADE deletes events, batch processing of >100 sessions, zero-qualifying runs logged correctly

## 2. Relay -- Connection Stability (Server Side)

- [ ] 2.1 Add 30s ping interval to WsHub: relay sends WebSocket protocol-level ping to all connected clients every 30 seconds
- [ ] 2.2 Add 60s idle timeout: if no messages (including pong) received from a client in 60s, close the connection with code 1001
- [ ] 2.3 Add 60s heartbeat check interval to CompanionManager: every 60s, iterate all companion connections, if `last_heartbeat_at` > 90s ago, mark host offline and broadcast `host_status: offline`
- [ ] 2.4 Write tests: ping sent every 30s, idle timeout closes connection after 60s inactivity, companion stale detection triggers at 90s, companion recovery on next heartbeat

## 3. Android -- Error State Management

- [ ] 3.1 Create `ErrorStateManager.kt`: Hilt @Singleton holding `MutableStateFlow<ErrorState>` with methods `setRelayConnected(Boolean)`, `setHostStatus(hostId, online)`, `setSessionError(sessionId, message?)`, and `clearSessionError(sessionId)`
- [ ] 3.2 Wire `ErrorStateManager` to ConnectionManager: on WS connect → `setRelayConnected(true)`, on WS disconnect → `setRelayConnected(false)`
- [ ] 3.3 Wire `ErrorStateManager` to host_status WS messages: on `host_status` → `setHostStatus(hostId, status == "online")`
- [ ] 3.4 Wire `ErrorStateManager` to session error events: on `session_error` with `provider_unreachable` → `setSessionError(sessionId, "Claude upstream 不可用")`
- [ ] 3.5 Create `ErrorBannerHost.kt` composable: takes `errorState` and `scope: ErrorScope` (GLOBAL, WORKSPACE, SESSION(id)), renders the highest-priority applicable banner

## 4. Android -- Three-Layer Error Display

- [ ] 4.1 Implement Layer 1 (`ConnectionBanner`): red banner "无法连接服务器，正在重连..." with spinner; show via `AnimatedVisibility(slideInVertically)` when `!errorState.relayConnected`; on reconnect, show green "已恢复" for 2s then slide out
- [ ] 4.2 Implement Layer 2 (host offline banner): orange inline banner "MacBook 离线，请检查 companion 是否运行" below TopAppBar on WorkspaceScreen and NewSessionScreen; only show when relay is connected but host is offline
- [ ] 4.3 Implement Layer 3 (session error banner): orange inline banner on SessionDetailScreen showing provider-specific error text; only show when Layer 1 and Layer 2 are clear for this context
- [ ] 4.4 Integrate `ErrorBannerHost` into root Scaffold of HomeScreen, WorkspaceScreen, DetailScreen, NewSessionScreen with appropriate scope

## 5. Android -- Error States UX Components

- [ ] 5.1 Create `EmptyState.kt` composable: takes `illustration: @Composable`, `title: String`, `subtitle: String?`, `ctaText: String?`, `onCta: (() -> Unit)?`; centered layout with spacing per FOUNDATION.md
- [ ] 5.2 Create `ShimmerSkeleton.kt` composable: takes `modifier: Modifier`, renders shimmer gradient sweep (1.5s loop) using `rememberInfiniteTransition` + `Brush.linearGradient`; corner radius matches target element
- [ ] 5.3 Create `InlineRetry.kt` composable: takes `errorMessage: String`, `onRetry: () -> Unit`; displays message + "重试" OutlinedButton
- [ ] 5.4 Apply empty states: SessionListScreen ("暂无会话" + "新建会话" CTA), WorkspaceScreen ("暂无根目录" + "添加根目录" CTA), RootDetailScreen directory empty ("此目录为空"), RootDetailScreen sessions empty ("暂无会话")
- [ ] 5.5 Apply shimmer skeletons: SessionListScreen (3 skeleton SessionCards on initial load), DirectoryBrowser (5 skeleton rows), WorkspaceScreen (2 skeleton host sections)
- [ ] 5.6 Apply inline retry: DirectoryBrowser on load failure, RootDetailScreen on load failure
- [ ] 5.7 Configure Snackbar at root Scaffold: `SnackbarHost` with `SnackbarHostState`, auto-dismiss 4s, apply to all transient errors (delete failure, send failure, API timeout)

## 6. Android -- Connection Stability (Client Side)

- [ ] 6.1 Add application-level ping in ConnectionManager: send `{ action: "ping" }` every 30s; if no `pong` received within 60s, close and reconnect
- [ ] 6.2 Register `ConnectivityManager.NetworkCallback` in SessionService: on `onAvailable()` / `onCapabilitiesChanged()`, debounce 1s, then force-close WS and reconnect (bypass backoff)
- [ ] 6.3 Handle network lost: on `onLost()`, pause reconnection attempts; resume on `onAvailable()`
- [ ] 6.4 Implement foreground service lifecycle state machine: ACTIVE → COOLING_DOWN (5min timer) → STOPPED; cancel timer if app foregrounds or new session starts; start service in Activity.onStart via `ContextCompat.startForegroundService`
- [ ] 6.5 Update foreground notification: show "IMbot 运行中" when idle, "n 个会话运行中" when sessions active; use IMPORTANCE_LOW channel to minimize intrusiveness

## 7. Android -- Stale Session Sync

- [ ] 7.1 On session list refresh, compute set difference: `localSessionIds - remoteSessionIds` → delete stale from Room
- [ ] 7.2 Handle pagination: only diff within the loaded page range (do not delete sessions beyond the loaded offset)
- [ ] 7.3 Write test: mock relay returns 5 sessions, Room has 7, expect 2 deleted from Room

## 8. Tests

- [ ] 8.1 Unit test `ErrorStateManager`: set relay connected/disconnected → state updates; set host offline → state updates; priority: relay > host > session
- [ ] 8.2 Unit test `ErrorBannerHost` composable: relay down → ConnectionBanner shown; relay up + host down → HostBanner shown; relay up + host up + session error → SessionBanner shown; all clear → no banner
- [ ] 8.3 Unit test foreground service lifecycle: app open → ACTIVE; app background + no sessions → COOLING_DOWN → 5min → STOPPED; app foreground during cooldown → ACTIVE
- [ ] 8.4 Unit test network change reconnect: onAvailable → debounce 1s → reconnect called; multiple onAvailable within 1s → only one reconnect
- [ ] 8.5 Unit test purge job (relay): 31-day session purged, 29-day not, running skipped, cascade verified, log output correct
- [ ] 8.6 Unit test stale session sync: local has [A,B,C,D], remote has [A,C] → B and D deleted from Room
- [ ] 8.7 UI test empty states: verify illustration + CTA render for empty session list, empty workspace
- [ ] 8.8 UI test shimmer: verify shimmer composable renders during loading state, replaced by content on load
- [ ] 8.9 UI test Snackbar: verify auto-dismiss after 4s, verify action button triggers retry
