# Proposal: p3-error-ux-and-cleanup

## Why

Production quality requires clear error communication (three-layer fault distinction), automatic data cleanup, and robust connection handling. Without these, the user sees generic errors with no actionable guidance, stale data accumulates indefinitely, and the WebSocket connection silently degrades. This change hardens the system for daily unsupervised use.

## What Changes

| Area | Change |
|------|--------|
| Three-layer error display | UI distinguishes relay_unreachable (ConnectionBanner red), host_offline (page-level orange banner), provider_unreachable (session-level orange banner). Error priority: connection > page > session > snackbar. Each error has clear message and recovery suggestion. |
| Data purge job | Relay runs daily cron (03:00 UTC) to delete sessions inactive > 30 days. CASCADE deletes session_events. Android syncs stale removals on refresh. Purge count logged. |
| Connection stability | WS ping/pong every 30s (both Android↔relay and companion↔relay). Idle timeout 60s. Companion heartbeat stale detection (90s). Android foreground service lifecycle. Network change listener for force reconnect. |
| Error states UX | Empty states for all pages. Loading skeletons (shimmer). Inline retry buttons. Snackbar for transient errors (auto-dismiss 4s). Dialog for destructive confirmations. |

## Capabilities

- `three-layer-error-display` -- Hierarchical error banners with priority system and actionable messages.
- `data-purge-job` -- Server-side 30-day session cleanup with CASCADE and Android-side sync.
- `connection-stability` -- Ping/pong, idle timeout, heartbeat stale detection, foreground service lifecycle, network change reconnect.
- `error-states-ux` -- Empty states, shimmer skeletons, inline retry, Snackbar, confirmation dialogs.

## Affected Areas

- `packages/relay/src/cleanup/` -- Purge job implementation (node-cron)
- `packages/relay/src/ws/hub.ts` -- Ping/pong, idle timeout
- `packages/relay/src/companion/manager.ts` -- Heartbeat stale detection (90s)
- `packages/android/app/src/main/java/.../imbot/ui/components/` -- ConnectionBanner, EmptyState, ShimmerSkeleton, InlineRetry
- `packages/android/app/src/main/java/.../imbot/ui/home/` -- Empty state, shimmer, error handling
- `packages/android/app/src/main/java/.../imbot/ui/detail/` -- Session-level error banner
- `packages/android/app/src/main/java/.../imbot/ui/workspace/` -- Empty state, error handling
- `packages/android/app/src/main/java/.../imbot/service/SessionService.kt` -- Foreground service lifecycle, network change listener
- `packages/android/app/src/main/java/.../imbot/data/remote/` -- Ping/pong, reconnect on network switch

## Dependencies

- Relay SQLite schema with `ON DELETE CASCADE` on session_events (from `p0-relay-minimal`).
- `node-cron` dependency on relay (already in tech stack).
- Android `ConnectivityManager.NetworkCallback` for network change detection.
- Foreground service infrastructure (from `p0-android-prototype`).
- ConnectionBanner animation (from `p3-theme-and-animations`).
- Room DB for Android-side stale session cleanup.

## Risks

| Risk | Mitigation |
|------|-----------|
| Purge accidentally deletes running sessions | WHERE clause explicitly excludes `running` and `queued` statuses |
| Purge on large dataset blocks SQLite | Batch delete in chunks of 100 with IMMEDIATE transaction; monitor execution time |
| Network change listener fires too aggressively | Debounce reconnect with 1s delay; ignore duplicate AVAILABLE callbacks |
| Foreground service battery drain | Stop service 5 min after all sessions complete + app background; respect battery saver |
| Error banner overlapping content | Banner pushes content down (not overlays); max one banner visible at a time per priority |

## References

- docs/specs/ui-ux-rd-spec/03_Patterns/PATTERNS.md (P-05 error handling, P-06 loading)
- docs/engineering-spec/02_Technical_Design/DATA_MODEL.md (30-day purge logic, session_events CASCADE)
- docs/engineering-spec/02_Technical_Design/API_SPEC.md (error codes, WS protocol, heartbeat)
- docs/engineering-spec/02_Technical_Design/ARCHITECTURE.md (foreground service lifecycle)
