# Proposal: p2-android-workspace-settings

## Why

Users need to manage workspace roots (add/remove), view host status, configure relay connection, switch themes, and complete first-time onboarding -- all from the Android app. These three screens (Workspace Manager, Settings, Onboarding) are the remaining Android pages required for a complete mobile console experience. Without them, the user cannot add new project directories, adjust app configuration, or get started on a fresh install.

## What Changes

| Area | Change |
|------|--------|
| Workspace Manager (Tab 2) | New screen listing workspace roots grouped by host. Each root shows provider icon, label, path. "+" button opens Bottom Sheet for adding roots (provider selector + directory browser + label input). "✕" removes root with confirmation dialog. Tap root navigates to directory browser showing subdirectories + sessions. |
| Settings (Tab 3) | New screen with sections: Connection (relay URL editable, connection/MacBook/OpenClaw status), Appearance (theme radio group: System/Light/Dark), Data (clear local cache with confirmation, session retention 30-day read-only), About (version). |
| Onboarding | Shown on first launch when no relay URL + token are saved. Logo + title, relay URL input, token password field, "测试连接" calls /healthz, success shows host statuses + enables "开始使用" button. Saves to SharedPreferences → navigates to home. |

## Capabilities

- `workspace-manager-screen` -- Tab 2 of bottom nav; workspace root CRUD, directory browsing, session listing per directory.
- `settings-screen` -- Tab 3 of bottom nav; connection status, theme toggle, cache management, version display.
- `onboarding-screen` -- First-launch configuration flow; relay URL + token input, connection test, save + proceed.

## Affected Areas

- `packages/android/app/src/main/java/.../imbot/ui/workspace/` -- WorkspaceScreen, WorkspaceViewModel, AddRootBottomSheet, RootDetailScreen
- `packages/android/app/src/main/java/.../imbot/ui/settings/` -- SettingsScreen, SettingsViewModel, EditRelayDialog
- `packages/android/app/src/main/java/.../imbot/ui/onboarding/` -- OnboardingScreen, OnboardingViewModel
- `packages/android/app/src/main/java/.../imbot/data/repository/` -- WorkspaceRepository, HostRepository updates
- `packages/android/app/src/main/java/.../imbot/data/local/` -- SharedPreferences wrapper for relay config + theme

## Dependencies

- Relay REST API: `GET /v1/hosts`, `GET /v1/hosts/:hostId/roots`, `POST /v1/hosts/:hostId/roots`, `DELETE /v1/hosts/:hostId/roots/:rootId`, `GET /v1/hosts/:hostId/browse` (from `p1-relay-workspace-api`).
- Relay health endpoint: `GET /healthz` (from `p0-relay-minimal`).
- WebSocket `host_status` messages for real-time host online/offline updates.
- Bottom navigation component (from `p2-android-session-list`).
- DirectoryBrowser component (from `p2-android-new-session`).
- Material 3 theme system (from `p3-theme-and-animations`, but theme toggle UI can be wired independently).

## Risks

| Risk | Mitigation |
|------|-----------|
| Host offline during add-root flow | Show error in Bottom Sheet; disable confirm button; relay returns 502 host_offline |
| Directory browser latency on deep trees | Lazy load one level at a time; show loading shimmer per level |
| Onboarding /healthz unreachable | Clear error message "无法连接"; keep "测试连接" button enabled for retry |
| Theme toggle without theme-system implementation | Wire the RadioGroup to SharedPreferences; actual theme application depends on p3-theme-and-animations |

## References

- docs/specs/ui-ux-rd-spec/04_Pages/04_WorkspaceScreen.md
- docs/specs/ui-ux-rd-spec/04_Pages/05_SettingsScreen.md
- docs/specs/ui-ux-rd-spec/04_Pages/00_OnboardingScreen.md
- docs/engineering-spec/02_Technical_Design/API_SPEC.md
- docs/engineering-spec/02_Technical_Design/DATA_MODEL.md
