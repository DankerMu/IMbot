# Design: p0-android-prototype

## Architecture Overview

The prototype is a single-Activity Compose app with two logical screens (Settings and Main) managed by simple conditional rendering (no Navigation library needed for Phase 0). State lives in ViewModels; the WebSocket client is a singleton provided by Hilt.

```
Application (@HiltAndroidApp)
└── MainActivity (@AndroidEntryPoint)
    └── ComposeContent
        ├── SettingsSection (relay URL + token inputs)
        ├── StatusBar (connection indicator)
        ├── CreateSessionButton
        └── EventList (LazyColumn of raw JSON strings)

Hilt Graph:
├── RelayWsClient (@Singleton)       -- OkHttp WebSocket, reconnect, Flow
├── RelayHttpClient (@Singleton)     -- OkHttp for REST calls
├── SettingsRepository (@Singleton)  -- SharedPreferences wrapper
└── MainViewModel (@HiltViewModel)   -- UI state, event list, actions
```

## Key Design Decisions

### 1. Single Activity, No Navigation

Phase 0 has only one screen with settings and events combined. No Jetpack Navigation, no Fragment. Settings are shown as a collapsible section at the top or a simple dialog. This keeps the prototype maximally simple.

### 2. OkHttp WebSocket Client

OkHttp is chosen because:
- Already needed for REST calls.
- Battle-tested WebSocket implementation.
- No additional dependency (vs. Ktor, Scarlet).

The `RelayWsClient` wraps OkHttp's `WebSocketListener` callbacks and bridges them to coroutine Flows:
- `connectionState: StateFlow<ConnectionState>` -- emits `NotConfigured`, `Connecting`, `Connected`, `Disconnected(reason)`.
- `messages: SharedFlow<ServerMessage>` -- parsed JSON messages, replay=0 (no buffering old messages).

### 3. Reconnect Strategy

On disconnect, schedule reconnect via `CoroutineScope.launch` with `delay()`:
- Delays: 1s, 2s, 4s, 8s, 16s, 30s (capped).
- Reset to 1s on successful connection.
- Cancel reconnect job if user changes settings or app is destroyed.

No reconnect on explicit `close()` call (user-initiated disconnect).

### 4. ServerMessage Parsing

Kotlin sealed class hierarchy:
```kotlin
sealed class ServerMessage {
    data class Event(val sessionId: String, val seq: Int, val eventType: String, val payload: JsonObject, val timestamp: String) : ServerMessage()
    data class Status(val sessionId: String, val status: String) : ServerMessage()
    data class HostStatus(val hostId: String, val status: String) : ServerMessage()
    data class Error(val code: String, val message: String) : ServerMessage()
    data object Pong : ServerMessage()
}
```

Parsed using `org.json.JSONObject` (Android built-in) or kotlinx.serialization. Phase 0 uses `JSONObject` to avoid adding another dependency.

### 5. REST Client for Session Creation

Simple `OkHttpClient` POST to `/v1/sessions` with `Authorization: Bearer <token>` header. Response parsed as JSON. No Retrofit -- overkill for one endpoint in Phase 0.

### 6. State Management

`MainViewModel` holds:
- `events: MutableStateFlow<List<String>>` -- raw JSON strings appended as they arrive.
- `sessionId: MutableStateFlow<String?>` -- current session, null if none.
- `isCreating: MutableStateFlow<Boolean>` -- loading state for create button.

ViewModel collects from `RelayWsClient.messages` and appends event JSON to the list. List is truncated at 5000 entries to prevent OOM in extreme cases.

### 7. Settings Persistence

`SettingsRepository` wraps `SharedPreferences`:
- `relayUrl: String`
- `token: String`

Read on app start, written on save. No encryption in Phase 0 (single-user device).

## File Structure

```
android/app/src/main/java/com/imbot/
├── IMbotApplication.kt           -- @HiltAndroidApp
├── MainActivity.kt               -- @AndroidEntryPoint, setContent
├── ui/
│   ├── MainScreen.kt             -- root composable
│   ├── SettingsSection.kt        -- URL + token inputs
│   ├── StatusBar.kt              -- connection indicator
│   ├── CreateSessionButton.kt    -- create button + loading
│   └── EventList.kt              -- LazyColumn of raw JSON
├── viewmodel/
│   └── MainViewModel.kt          -- state + actions
├── network/
│   ├── RelayWsClient.kt          -- WebSocket + reconnect
│   ├── RelayHttpClient.kt        -- REST POST /sessions
│   └── ServerMessage.kt          -- sealed class hierarchy
├── data/
│   └── SettingsRepository.kt     -- SharedPreferences wrapper
└── di/
    └── NetworkModule.kt          -- Hilt @Module for singletons
```
