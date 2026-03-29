# Tasks: p2-android-workspace-settings

## 1. Data Layer -- Preferences & Repository

- [ ] 1.1 Create `PrefsDataStore.kt`: DataStore wrapper exposing `relayUrl: Flow<String?>`, `token: Flow<String?>`, `themeMode: Flow<ThemeMode>` with corresponding save methods; use EncryptedSharedPreferences for token storage
- [ ] 1.2 Update `WorkspaceRepository.kt`: add `getHostsWithRoots(): Flow<List<HostWithRoots>>` combining `GET /v1/hosts` + per-host `GET /v1/hosts/:id/roots`; add `addRoot(hostId, provider, path, label)` calling POST; add `removeRoot(hostId, rootId)` calling DELETE
- [ ] 1.3 Add `browseDirectory(hostId, path): List<DirectoryEntry>` to `WorkspaceRepository` calling `GET /v1/hosts/:hostId/browse?path=`
- [ ] 1.4 Add `getSessionsByPathPrefix(pathPrefix: String): Flow<List<SessionSummary>>` to `SessionRepository` for listing sessions under a workspace root
- [ ] 1.5 Add `testConnection(relayUrl, token): HealthzResponse` to a standalone `RelayApiClient` calling `GET {relayUrl}/healthz` with Bearer token, 10s timeout
- [ ] 1.6 Add `clearLocalCache()` to `SessionRepository` that clears all Room tables (sessions + session_events)

## 2. Onboarding Screen

- [ ] 2.1 Create `OnboardingViewModel.kt` with `OnboardingUiState`, methods `updateUrl(url)`, `updateToken(token)`, `testConnection()`, `saveAndProceed()`
- [ ] 2.2 Create `OnboardingScreen.kt`: logo + title layout, relay URL input (keyboard type URL), token input (password + visibility toggle), "测试连接" button with loading spinner, result area (success green / error red), "开始使用" button (visible only after success)
- [ ] 2.3 Implement `testConnection()` in ViewModel: call `RelayApiClient.testConnection()`, map 200 → Success with host statuses, map network error → Error "无法连接", map 401 → Error "认证失败", map timeout → Error "连接超时"
- [ ] 2.4 Implement `saveAndProceed()`: save URL + token to PrefsDataStore, emit navigation event to home
- [ ] 2.5 Update `NavGraph.kt`: read PrefsDataStore for relay URL presence, set `startDestination` to "onboarding" or "home" accordingly; onboarding → home uses `popUpTo("onboarding") { inclusive = true }`

## 3. Workspace Manager Screen

- [ ] 3.1 Create `WorkspaceViewModel.kt` with `WorkspaceUiState`, init fetch `getHostsWithRoots()`, expose `refresh()`, `removeRoot(hostId, rootId)`
- [ ] 3.2 Create `WorkspaceScreen.kt`: TopAppBar "目录管理" with "+" button, LazyColumn of host sections → root items, each root with provider icon + label + path + "✕" button, pull-to-refresh via `PullToRefreshBox`, empty state when no roots
- [ ] 3.3 Create `AddRootBottomSheet.kt`: ModalBottomSheet with provider chip group (Claude/book/OpenClaw), auto-resolved host display, embedded directory browser (LazyColumn fetching browse API), label text input, "取消"/"添加" buttons
- [ ] 3.4 Implement directory browser inside AddRootBottomSheet: breadcrumb navigation, tap directory → fetch children → update path, back arrow to go up one level
- [ ] 3.5 Implement add root submission: validate fields → call `WorkspaceRepository.addRoot()` → on 201 dismiss sheet + refresh list → on 409 show inline "该目录已添加" → on 502 show "主机离线"
- [ ] 3.6 Implement remove root: tap "✕" → show AlertDialog "确认移除此根目录？已有会话不受影响。" → on confirm call DELETE → animate item out → on error show Snackbar

## 4. Root Detail Screen

- [ ] 4.1 Create `RootDetailScreen.kt`: TopAppBar with back arrow + root label, LazyColumn with directory entries + "该目录下的会话" section header + SessionCard list
- [ ] 4.2 Create `RootDetailViewModel.kt`: fetch `browseDirectory(hostId, path)` for directory entries, fetch `getSessionsByPathPrefix(path)` for sessions, expose `navigateToSubdirectory(path)` and `navigateUp()`
- [ ] 4.3 Wire tap root in WorkspaceScreen → navigate to RootDetailScreen with root id + path arguments
- [ ] 4.4 Wire tap subdirectory → update current path + re-fetch directories and sessions

## 5. Settings Screen

- [ ] 5.1 Create `SettingsViewModel.kt`: observe `PrefsDataStore` for relayUrl + themeMode, observe WebSocket connection state from ConnectionManager, observe host statuses, expose `setTheme(mode)`, `updateRelayUrl(url)`, `clearCache()`
- [ ] 5.2 Create `SettingsScreen.kt`: sectioned LazyColumn with "连接" (relay URL row, connection status row, MacBook status row, OpenClaw status row), "外观" (theme RadioGroup), "数据" (clear cache row, retention row), "关于" (version row)
- [ ] 5.3 Create `EditRelayDialog.kt`: AlertDialog with URL text input pre-filled with current value, "取消"/"保存" buttons, empty URL validation
- [ ] 5.4 Wire relay URL edit: on save → update PrefsDataStore → trigger WebSocket + REST client reconnect to new URL
- [ ] 5.5 Wire theme toggle: RadioGroup selection → `SettingsViewModel.setTheme(mode)` → PrefsDataStore save → IMbotApp recomposes with new theme
- [ ] 5.6 Wire clear cache: tap → confirmation AlertDialog → on confirm call `SessionRepository.clearLocalCache()` → Snackbar "已清除"
- [ ] 5.7 Display version from `BuildConfig.VERSION_NAME` in the About section

## 6. Navigation Integration

- [ ] 6.1 Register WorkspaceScreen and SettingsScreen as bottom nav destinations (tab 2 and tab 3) in NavGraph
- [ ] 6.2 Register RootDetailScreen as a sub-route under workspace with arguments (rootId, hostId, path)
- [ ] 6.3 Verify bottom nav tab switching preserves each tab's scroll position and state

## 7. Tests

- [ ] 7.1 Unit test OnboardingViewModel: test button enable/disable logic (empty fields → disabled, both filled → enabled), testConnection success/error/timeout mapping, saveAndProceed writes to prefs
- [ ] 7.2 Unit test WorkspaceViewModel: initial load populates hosts+roots, refresh re-fetches, removeRoot updates state, error handling
- [ ] 7.3 Unit test SettingsViewModel: theme persistence round-trip, relay URL update triggers reconnect, clearCache delegates to repository
- [ ] 7.4 Unit test PrefsDataStore: write/read relay URL, write/read token (encrypted), write/read theme mode, missing values return null/default
- [ ] 7.5 UI test OnboardingScreen: verify first-launch navigation guard, test connection flow (mock API), proceed saves and navigates
- [ ] 7.6 UI test WorkspaceScreen: verify root list rendering, add root bottom sheet flow, remove root confirmation
- [ ] 7.7 UI test SettingsScreen: verify all sections render, theme toggle changes state, relay URL edit dialog
