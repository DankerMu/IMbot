# Tasks: p2-android-session-list

## Room Session Cache

- [ ] 1.1 Define `SessionEntity` in Room with fields: id, provider, hostId, workspaceCwd, initialPrompt, model, status, errorMessage, createdAt, updatedAt, lastActiveAt
- [ ] 1.2 Create `SessionDao` with: `@Insert(onConflict = REPLACE) insertAll(sessions)`, `@Query getAll()`, `@Query getByProvider(provider)`, `@Query deleteById(id)`, `@Query deleteNotIn(ids)` (for cache consistency)
- [ ] 1.3 Update `AppDatabase` to include `SessionEntity` and `SessionDao`
- [ ] 1.4 Implement cache-first logic in `SessionRepository`: `getSessions()` returns Flow that emits cache immediately, then triggers API refresh, then emits merged cache
- [ ] 1.5 Implement `SessionRepository.refreshFromApi()`: call `GET /v1/sessions`, upsert into Room, delete sessions not in API response
- [ ] 1.6 Implement `SessionRepository.updateSessionStatus(sessionId, status)` for WebSocket-driven cache updates

## Session List Screen

- [ ] 2.1 Create `HomeScreen` composable with `TopAppBar` (title "IMbot", filter dropdown action), `LazyColumn`, and `FloatingActionButton`
- [ ] 2.2 Create `HomeViewModel` with `HomeUiState` StateFlow: sessions, filter, isLoading, isRefreshing, error, isConnected
- [ ] 2.3 Implement session sorting: running sessions first (sorted by lastActiveAt desc), then non-running (sorted by lastActiveAt desc), with visual divider between groups
- [ ] 2.4 Implement pull-to-refresh using `PullToRefreshContainer` composable
- [ ] 2.5 Implement empty state with "暂无会话" text and "新建会话" button
- [ ] 2.6 Implement loading skeleton (3 shimmer cards) for initial load
- [ ] 2.7 Wire WebSocket status events from `SessionService` to `HomeViewModel` for real-time list updates
- [ ] 2.8 Implement API error handling: Snackbar on refresh failure, retain cached data

## Session Card Component

- [ ] 3.1 Create `SessionCard` composable with layout: provider icon, workspace path (last 2 segments), prompt summary (50 char truncation), status dot, relative timestamp
- [ ] 3.2 Implement status dot colors: running (green pulse), completed (static green), failed (static red), queued/cancelled (static gray)
- [ ] 3.3 Implement swipe-to-delete with `SwipeToDismiss` composable: red background reveal, threshold, confirmation dialog
- [ ] 3.4 Implement long-press context menu with "归档" and "删除" options
- [ ] 3.5 Implement `formatRelativeTime(Instant)` utility: "刚刚", "3 分钟前", "2 小时前", "昨天", etc.
- [ ] 3.6 Implement workspace path truncation: extract last 2 segments from full path

## Provider Filter

- [ ] 4.1 Create filter dropdown composable in TopAppBar with options: All, Claude Code, book, OpenClaw
- [ ] 4.2 Implement local filtering in `HomeViewModel.applyFilter(provider)`: filter the session list without API call
- [ ] 4.3 Persist selected filter to SharedPreferences on change
- [ ] 4.4 Restore filter from SharedPreferences on ViewModel init
- [ ] 4.5 Show empty state when filter matches 0 sessions

## Bottom Navigation

- [ ] 5.1 Create `MainScreen` scaffold composable with Material 3 `NavigationBar` and `NavHost`
- [ ] 5.2 Define 3 navigation items: 会话 (home icon), 目录 (folder icon), 设置 (settings icon)
- [ ] 5.3 Implement tab selection and navigation with `NavController`
- [ ] 5.4 Implement badge dot on 会话 tab: observe running session count from `SessionRepository`
- [ ] 5.5 Implement back-press interception: from non-home tab, navigate to home instead of exiting
- [ ] 5.6 Preserve scroll position and state when switching tabs (use `rememberSaveable` or `NavBackStackEntry`)
