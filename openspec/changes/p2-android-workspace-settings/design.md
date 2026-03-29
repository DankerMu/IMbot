# Design: p2-android-workspace-settings

## Architecture Overview

Three new screens (Workspace, Settings, Onboarding) follow the existing MVVM pattern: Compose UI → ViewModel (StateFlow) → Repository → DataSource. All three are pure Android-side changes with no relay or companion modifications.

```
WorkspaceScreen ← WorkspaceViewModel ← WorkspaceRepository ← REST (hosts, roots, browse)
SettingsScreen  ← SettingsViewModel  ← PrefsRepository     ← SharedPreferences + WS status
OnboardingScreen← OnboardingViewModel← REST /healthz       ← OkHttp one-shot
```

## Key Design Decisions

### 1. Workspace Data Flow

**Decision**: Fetch hosts and roots in a single coroutine scope, combining `GET /v1/hosts` + `GET /v1/hosts/:id/roots` per host. Cache in ViewModel state only (no Room persistence for roots).

**Rationale**: Workspace roots are lightweight metadata (typically < 10 items). Room caching adds complexity without benefit -- the list is always fetched fresh. Pull-to-refresh re-triggers the same flow.

### 2. Add Root Bottom Sheet as Modal

**Decision**: Use Material 3 `ModalBottomSheet` with a multi-step form inside (provider selector → directory browser → label → confirm).

**Rationale**: Bottom Sheets are the standard M3 pattern for creation flows on mobile. A full-screen dialog would be overkill for a 3-field form. The directory browser is an embedded `LazyColumn` that fetches one directory level at a time via `GET /v1/hosts/:hostId/browse`.

### 3. Provider → Host Auto-Mapping

**Decision**: Provider selection determines the host automatically. Claude/book → MacBook host (first host with type `macbook`). OpenClaw → relay-local host.

**Rationale**: IMbot is single-user with at most one MacBook and one relay. Explicit host selection adds UI clutter for no gain. If multiple MacBooks are supported in the future, this can become a dropdown.

### 4. Settings Persistence Strategy

**Decision**: Use SharedPreferences (via Jetpack DataStore wrapper) for: relay URL, token (EncryptedSharedPreferences), theme mode. Room DB is NOT used for settings.

**Rationale**: Settings are key-value pairs, not relational data. DataStore provides coroutine-native reads/writes. EncryptedSharedPreferences protects the token at rest.

### 5. Onboarding as Navigation Guard

**Decision**: Implement onboarding as a navigation guard in the NavHost. On app start, check SharedPreferences. If relay URL or token is missing, set `startDestination = "onboarding"`. Otherwise, set `startDestination = "home"`.

**Rationale**: Simple conditional start destination avoids splash screen complexity. The onboarding screen replaces (not pushes onto) the back stack, so back-press from home exits the app.

### 6. Connection Status from SessionService

**Decision**: SettingsViewModel observes WebSocket connection state from the existing SessionService (or a shared ConnectionManager singleton). Host statuses come from `host_status` WebSocket messages stored in a `StateFlow<Map<String, HostStatus>>`.

**Rationale**: Reuses the existing WebSocket infrastructure. No new connections needed for settings.

### 7. Theme Toggle Wiring

**Decision**: SettingsScreen writes theme preference to DataStore. The top-level `IMbotApp` composable observes theme preference via `collectAsState()` and passes the resolved `ThemeMode` to the Material 3 theme wrapper. The actual theme implementation (colors, dynamic color, cross-fade) is part of `p3-theme-and-animations`; this change only provides the UI control and persistence.

**Rationale**: Decouples the toggle UI from the theme implementation. The toggle works immediately (writes to DataStore), and theme visuals improve when p3 lands.

## File Structure

```
packages/android/app/src/main/java/.../imbot/
├── ui/
│   ├── workspace/
│   │   ├── WorkspaceScreen.kt         -- Root list grouped by host
│   │   ├── WorkspaceViewModel.kt      -- Hosts + roots state
│   │   ├── AddRootBottomSheet.kt      -- Modal bottom sheet form
│   │   └── RootDetailScreen.kt        -- Directory browser + sessions
│   ├── settings/
│   │   ├── SettingsScreen.kt          -- Sectioned settings list
│   │   ├── SettingsViewModel.kt       -- Prefs + connection status
│   │   └── EditRelayDialog.kt         -- URL edit dialog
│   ├── onboarding/
│   │   ├── OnboardingScreen.kt        -- First-launch flow
│   │   └── OnboardingViewModel.kt     -- Test connection + save
│   └── navigation/
│       └── NavGraph.kt                -- Updated with onboarding guard
├── data/
│   ├── local/
│   │   └── PrefsDataStore.kt          -- DataStore wrapper (URL, token, theme)
│   └── repository/
│       └── WorkspaceRepository.kt     -- Hosts + roots + browse API calls
```

## State Models

```kotlin
// WorkspaceScreen
data class WorkspaceUiState(
    val hosts: List<HostWithRoots> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

// AddRootBottomSheet
data class AddRootState(
    val provider: Provider? = null,
    val host: HostInfo? = null,
    val currentPath: String = "",
    val directories: List<DirectoryEntry> = emptyList(),
    val label: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null
)

// OnboardingScreen
data class OnboardingUiState(
    val relayUrl: String = "",
    val token: String = "",
    val isTesting: Boolean = false,
    val testResult: TestResult? = null
)

sealed class TestResult {
    data class Success(val version: String, val macbook: String, val openclaw: String) : TestResult()
    data class Error(val message: String) : TestResult()
}
```
