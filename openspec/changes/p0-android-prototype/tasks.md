# Tasks: p0-android-prototype

## 1. Project Scaffold

- [x] 1.1 Create Android project with Kotlin DSL, Jetpack Compose, Material 3; set minSdk=26, targetSdk=35
- [x] 1.2 Add dependencies to `build.gradle.kts`: OkHttp, Coroutines, Hilt, Compose BOM, Material 3
- [x] 1.3 Configure Hilt: `@HiltAndroidApp` Application class, Hilt Gradle plugin
- [x] 1.4 Add `buildConfigField` for `DEFAULT_RELAY_URL` in build.gradle.kts
- [ ] 1.5 Verify: `./gradlew assembleDebug` succeeds, APK installs on API 26 emulator

## 2. Settings Persistence

- [x] 2.1 Implement `SettingsRepository`: read/write `relayUrl` and `token` from SharedPreferences
- [x] 2.2 Provide `SettingsRepository` as `@Singleton` via Hilt `@Module`

## 3. ServerMessage Data Model

- [x] 3.1 Define `ServerMessage` sealed class: `Event`, `Status`, `HostStatus`, `Error`, `Pong`
- [x] 3.2 Implement JSON parsing function `parseServerMessage(json: String): ServerMessage?` with error handling for malformed input
- [x] 3.3 Write unit tests for each message type + malformed JSON edge case

## 4. WebSocket Client

- [x] 4.1 Implement `RelayWsClient` with OkHttp WebSocket: `connect(url, token)`, `disconnect()`, `send(message)`
- [x] 4.2 Expose `connectionState: StateFlow<ConnectionState>` (NotConfigured, Connecting, Connected, Disconnected)
- [x] 4.3 Expose `messages: SharedFlow<ServerMessage>` parsed from incoming text frames
- [x] 4.4 Implement auto-reconnect with exponential backoff (1s, 2s, 4s, ..., 30s cap), reset on success
- [x] 4.5 Implement `subscribe(sessionId)` -- sends subscribe message, queues if disconnected, re-sends on reconnect
- [x] 4.6 Provide `RelayWsClient` as `@Singleton` via Hilt

## 5. HTTP Client

- [x] 5.1 Implement `RelayHttpClient` with OkHttp: `createSession(provider, hostId, cwd, prompt, permissionMode): Result<SessionResponse>`
- [x] 5.2 Send POST to `/v1/sessions` with `Authorization: Bearer <token>` header, parse JSON response
- [x] 5.3 Provide `RelayHttpClient` as `@Singleton` via Hilt

## 6. ViewModel

- [x] 6.1 Implement `MainViewModel` (@HiltViewModel): inject `RelayWsClient`, `RelayHttpClient`, `SettingsRepository`
- [x] 6.2 Expose state: `events: StateFlow<List<String>>`, `sessionId: StateFlow<String?>`, `isCreating: StateFlow<Boolean>`, `connectionState: StateFlow<ConnectionState>`
- [x] 6.3 Implement `createSession()` action: POST to relay → on success subscribe via WS → collect events
- [x] 6.4 Implement `saveSettings(url, token)` action: persist + reconnect WS client
- [x] 6.5 Collect `RelayWsClient.messages` and append event JSON to events list (cap at 5000 entries)

## 7. Compose UI

- [x] 7.1 Implement `MainScreen` root composable wiring ViewModel state
- [x] 7.2 Implement `SettingsSection`: two `OutlinedTextField` (URL, token) + Save button, pre-fill from SettingsRepository
- [x] 7.3 Implement `StatusBar`: colored text showing connection state (green=Connected, red=Disconnected, yellow=Connecting, gray=NotConfigured)
- [x] 7.4 Implement `CreateSessionButton`: button with loading indicator, disabled while creating or disconnected
- [x] 7.5 Implement `EventList`: `LazyColumn` of raw JSON strings, auto-scroll to bottom on new item, "Waiting for events..." empty state
- [x] 7.6 Wire everything in `MainActivity` with `setContent { MainScreen() }`

## 8. Integration Verification

- [ ] 8.1 Manual test: configure settings → connect to running relay → verify "Connected" status
- [ ] 8.2 Manual test: create session → events appear in list → session completes
- [ ] 8.3 Manual test: disconnect relay → status shows "Disconnected" → restart relay → auto-reconnect
- [ ] 8.4 Manual test: rotate device → events and settings preserved
