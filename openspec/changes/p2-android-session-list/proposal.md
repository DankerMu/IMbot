# Proposal: p2-android-session-list

## Why

The Phase 0 prototype uses a minimal placeholder UI for the session list. For production use, the home screen needs a polished session list with provider filtering, real-time status updates via WebSocket, local Room caching for instant startup, and a Material 3 bottom navigation bar. This is the first screen the user sees on every app launch and the primary navigation hub for all sessions.

## What Changes

| Area | Change |
|------|--------|
| Session list screen | `HomeScreen` as default route with `TopAppBar`, `LazyColumn` of `SessionCard`s, FAB for new session, pull-to-refresh, running sessions pinned to top, and empty state. |
| Session card component | Production card showing provider icon, workspace path (last 2 segments), prompt summary (truncated 50 chars), status indicator (colored dot), relative timestamp. Swipe-to-delete with confirmation. Long press context menu. |
| Provider filter | Dropdown filter in `TopAppBar`: All / Claude Code / book / OpenClaw. Applied locally (no API re-fetch). Persisted across restarts via SharedPreferences. |
| Room session cache | Room database caches session summaries. Cache-first strategy: show cache on startup, refresh from API, merge updates. Offline reads from cache. |
| Bottom navigation | Material 3 `NavigationBar` with 3 tabs: 会话 (home), 目录 (workspace), 设置 (settings). Badge dot on 会话 tab when running sessions exist. |

## Capabilities

- `session-list-screen`
- `session-card-component`
- `provider-filter`
- `room-session-cache`
- `bottom-navigation`

## Affected Areas

- `packages/android/.../ui/home/HomeScreen.kt` -- session list Compose UI
- `packages/android/.../ui/home/HomeViewModel.kt` -- state management
- `packages/android/.../ui/home/SessionCard.kt` -- card component
- `packages/android/.../ui/theme/` -- status colors, provider colors
- `packages/android/.../data/local/SessionDao.kt` -- Room DAO for caching
- `packages/android/.../data/local/AppDatabase.kt` -- Room database
- `packages/android/.../data/repository/SessionRepository.kt` -- cache-first logic
- `packages/android/.../ui/navigation/` -- bottom navigation setup

## Dependencies

- Relay REST API (`GET /v1/sessions`) from `p1-relay-session-lifecycle`.
- WebSocket real-time status updates from `p0-relay-minimal`.
- Room database setup (Android project scaffold from `p0-monorepo-and-wire`).
- UI foundation tokens from `docs/specs/ui-ux-rd-spec/01_Foundation/FOUNDATION.md`.

## Risks

| Risk | Mitigation |
|------|-----------|
| Large session list (200+) causes UI jank | `LazyColumn` with stable keys; pagination via `limit`/`offset` if needed |
| Real-time updates cause list flicker | DiffUtil-equivalent via Compose `key` on `session.id`; immutable state |
| Offline cache stale after long time | Show cache immediately + refresh indicator; stale data is better than empty screen |

## References

- docs/specs/ui-ux-rd-spec/04_Pages/01_SessionListScreen.md
- docs/specs/ui-ux-rd-spec/02_Components/COMPONENTS.md (C-01: SessionCard, C-06: ProviderChip, C-07: StatusIndicator, C-09: EmptyState, C-10: ConnectionBanner)
- docs/engineering-spec/02_Technical_Design/ARCHITECTURE.md (Android App Layers)
- docs/engineering-spec/02_Technical_Design/DATA_MODEL.md (Android Room Schema)
