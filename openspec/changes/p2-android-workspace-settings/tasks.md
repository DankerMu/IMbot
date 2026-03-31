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

## Unit Tests: OnboardingViewModel

- [ ] 7.1 Init state: both fields empty, testResult=null, isTesting=false
- [ ] 7.2 updateUrl/updateToken: values reflected in uiState immediately
- [ ] 7.3 testConnection with empty URL or empty token → error "请填写完整的连接信息" without API call
- [ ] 7.4 testConnection → HTTP 200 → TestResult.Success with host statuses parsed
- [ ] 7.5 testConnection → HTTP 401 → TestResult.Error "认证失败"
- [ ] 7.6 testConnection → network error (IOException) → TestResult.Error "无法连接"
- [ ] 7.7 testConnection → timeout (SocketTimeoutException) → TestResult.Error "连接超时"
- [ ] 7.8 testConnection sets isTesting=true during request, false on completion (success or failure)
- [ ] 7.9 Double-tap testConnection guard: ignore when isTesting=true
- [ ] 7.10 saveAndProceed: writes relayUrl+token to PrefsDataStore, emits navigation event
- [ ] 7.11 saveAndProceed without successful testResult → blocked (button should be disabled)
- [ ] 7.12 Invalid URL format (no scheme, non-https) → testConnection returns error

## Unit Tests: WorkspaceViewModel

- [ ] 8.1 Init fetches hosts+roots, populates WorkspaceUiState.hosts
- [ ] 8.2 Refresh re-fetches and updates state
- [ ] 8.3 Empty hosts list → empty state
- [ ] 8.4 Host with multiple roots → roots grouped correctly
- [ ] 8.5 Book provider roots filtered to book-only in display
- [ ] 8.6 removeRoot success → root removed from state, Snackbar "已移除"
- [ ] 8.7 removeRoot failure → Snackbar error, root remains in state
- [ ] 8.8 removeRoot shows confirmation dialog before API call
- [ ] 8.9 Network failure on init → error state with retry
- [ ] 8.10 Provider-to-host auto-mapping: claude/book → macbook host, openclaw → relay-local

## Unit Tests: AddRootBottomSheet State

- [ ] 9.1 Provider selection → auto-resolves hostId from hosts list
- [ ] 9.2 Directory browse → updates currentPath and entries
- [ ] 9.3 Breadcrumb navigation → fetches parent directory
- [ ] 9.4 Submit with valid fields → API call, dismiss on 201
- [ ] 9.5 Submit → 409 conflict → inline error "该目录已添加"
- [ ] 9.6 Submit → 502 → inline error "主机离线"
- [ ] 9.7 Submit → network error → inline error with retry
- [ ] 9.8 Double-tap submit guard: ignore when isSubmitting=true
- [ ] 9.9 Empty label defaults to directory basename

## Unit Tests: SettingsViewModel

- [ ] 10.1 Init observes PrefsDataStore for relayUrl + themeMode
- [ ] 10.2 Init observes WS connection state
- [ ] 10.3 setTheme writes to PrefsDataStore, new mode reflected in state
- [ ] 10.4 updateRelayUrl saves to prefs and triggers WS reconnect
- [ ] 10.5 clearCache calls SessionRepository.clearLocalCache, shows Snackbar
- [ ] 10.6 clearCache failure → error Snackbar
- [ ] 10.7 Host statuses updated from WS host_status messages

## Unit Tests: RootDetailViewModel

- [ ] 11.1 Init fetches directory entries for root path
- [ ] 11.2 Init fetches sessions filtered by path prefix
- [ ] 11.3 navigateToSubdirectory → updates path + re-fetches both
- [ ] 11.4 navigateUp → parent path + re-fetch
- [ ] 11.5 Empty directory → shows "此目录为空"
- [ ] 11.6 Empty sessions → shows "暂无会话"
- [ ] 11.7 Network failure → error state with retry

## Unit Tests: WorkspaceRepository

- [ ] 12.1 getHostsWithRoots combines GET /hosts + GET /hosts/:id/roots per host
- [ ] 12.2 addRoot calls POST /hosts/:id/roots with correct body
- [ ] 12.3 removeRoot calls DELETE /hosts/:id/roots/:rootId
- [ ] 12.4 browseDirectory calls GET /hosts/:id/browse?path=
- [ ] 12.5 getSessionsByPathPrefix filters sessions with path startsWith

## Unit Tests: Navigation Guard

- [ ] 13.1 No saved relayUrl → startDestination = "onboarding"
- [ ] 13.2 Saved relayUrl + token → startDestination = "home"
- [ ] 13.3 Onboarding saveAndProceed → navigates to home, back-press exits app (popUpTo inclusive)
