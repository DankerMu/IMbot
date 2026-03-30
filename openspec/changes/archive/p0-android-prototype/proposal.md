# Proposal: p0-android-prototype

## Why

The Android app is the user-facing entry point of the IMbot system. Phase 0 needs a minimal prototype to prove end-to-end connectivity: phone sends a create-session request to relay, relay dispatches to companion, companion spawns Claude CLI, streaming events flow back through relay to the phone UI.

This is NOT the production app. It is the thinnest possible slice that validates the full data path and provides a debugging surface for relay and companion development.

## What Changes

| Area | Change |
|------|--------|
| Android project | New Kotlin + Jetpack Compose project with Material 3, min SDK 26, target SDK 35. |
| Dependencies | OkHttp (HTTP + WebSocket), Coroutines, Hilt (DI), Room (optional, not used in prototype). |
| WSS client | OkHttp WebSocket connection to relay with query-param token auth, auto-reconnect with exponential backoff, parse ServerMessage JSON, emit events as Kotlin Flow. |
| Prototype UI | Settings screen (relay URL + token input, saved to SharedPreferences), one "Create Session" button with hardcoded provider/cwd/prompt, LazyColumn showing raw event JSON as it arrives, connected/disconnected status indicator. |
| Build config | `buildConfigField` for default relay URL. |

## Capabilities

- `android-project-setup` -- Project structure, dependencies, build configuration.
- `android-ws-client` -- OkHttp WebSocket client with auth, reconnect, Flow emission.
- `android-prototype-ui` -- Minimal Compose UI for settings, session creation, and event display.

## Dependencies

- Relay server must be running and accessible (from `p0-relay-minimal`).
- Companion must be running for claude/book sessions (from `p0-companion-minimal`).
- Android device or emulator with API 26+.

## Non-Goals (Phase 0)

- No Room database / local caching.
- No session list or session detail screens (those are P2).
- No Foreground Service or FCM push.
- No workspace browsing or directory picker.
- No multiple concurrent sessions in UI.
- No error retry UI or user-friendly error messages.

## Risk

| Risk | Mitigation |
|------|-----------|
| OkHttp WebSocket reconnect complexity | Keep it simple -- close + reconnect, no partial frame recovery |
| Compose state loss on config change | Use `rememberSaveable` for text fields, ViewModel for event list |
| Hardcoded session params too limiting for testing | Make cwd and prompt editable in a future iteration; prototype uses text fields |
