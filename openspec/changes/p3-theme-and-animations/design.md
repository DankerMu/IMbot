# Design: p3-theme-and-animations

## Architecture Overview

The theme and animation system is a pure Android-side change centered on `ui/theme/` with touch points in every screen and component. No relay or companion changes.

```
IMbotApp (root composable)
├── IMbotTheme(themeMode, dynamicColor)
│   ├── MaterialTheme(colorScheme, typography, shapes)
│   │   ├── ProviderColors (CompositionLocal)
│   │   ├── StatusColors (CompositionLocal)
│   │   └── CodeTheme (CompositionLocal)
│   └── NavHost(transitions)
│       ├── HomeScreen → SharedTransitionLayout → DetailScreen
│       ├── WorkspaceScreen
│       └── SettingsScreen
```

## Key Design Decisions

### 1. Theme Architecture -- CompositionLocal Layering

**Decision**: Use Material 3 `MaterialTheme` for core colors + three custom `CompositionLocal` providers for provider colors, status colors, and code theme colors.

**Rationale**: Material 3 handles surfaces, text, and standard components. Provider colors (amber, violet, red) and status colors (queued gray, running green, etc.) are domain-specific and should not pollute the M3 color scheme. `CompositionLocal` makes them available anywhere in the tree without prop drilling.

```kotlin
val LocalProviderColors = staticCompositionLocalOf { ProviderColors() }
val LocalStatusColors = staticCompositionLocalOf { StatusColors() }
val LocalCodeTheme = staticCompositionLocalOf { CodeTheme.Light }
```

### 2. Dynamic Color Detection

**Decision**: Check `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` at theme initialization. If true and theme is System, use `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)`. Otherwise, use static `lightColorScheme()` / `darkColorScheme()` with seed colors.

**Rationale**: Standard Material 3 guidance. Dynamic color provides personality on supported devices; custom seeds provide consistency on older devices.

### 3. Cross-Fade Theme Switch (No Flash)

**Decision**: Wrap the `MaterialTheme` content in `Crossfade(targetState = colorScheme, animationSpec = tween(400))`. The `colorScheme` is the fully resolved scheme for the current mode.

**Rationale**: Direct `MaterialTheme` swap causes an instant repaint (white/black flash). `Crossfade` interpolates between the old and new composition, avoiding the flash. 400ms matches the FOUNDATION.md spec.

**Alternative considered**: `AnimatedContent` -- more complex, same visual result. `Crossfade` is simpler and sufficient.

### 4. Shared Element Transition Implementation

**Decision**: Use Compose `SharedTransitionLayout` + `sharedElement()` modifier (available from Compose 1.7+). The SessionCard and DetailScreen TopAppBar share a key `"session-${session.id}"`. Transition spec: 400ms `Emphasized` easing.

**Rationale**: First-party Compose API, no third-party library needed. Compose 1.7 `SharedTransitionLayout` is stable and handles the complex clipping/bounds calculation.

**Fallback**: On devices where SharedTransitionLayout has issues (low memory, old Compose runtime), degrade to a standard fade transition. Detect via try-catch or feature flag.

### 5. Message Animation Strategy -- Key-Based LazyColumn

**Decision**: Use `LazyColumn` with `item(key = event.id)` and `AnimatedVisibility` / `animateItem()` for message appearance. Each new item uses `fadeIn + slideInVertically`. Staggered initial load uses `LaunchedEffect` with incremental delay.

**Rationale**: Key-based animations ensure only new items animate. `animateItem()` (Compose 1.7) handles item reorder/add/remove animations. For the initial stagger, we control delay explicitly via coroutine.

**Performance guard**: Skip animation for items that are already in the list (scroll-back scenario). Only animate items whose `seq` is greater than the last-seen seq on enter.

### 6. Streaming Cursor Implementation

**Decision**: Append "▊" to the last `assistant_delta` text. Use `rememberInfiniteTransition()` with `animateFloat(0f, 1f, 1000ms loop)` to toggle cursor visibility. The cursor is part of the `AnnotatedString` with alpha controlled by animation.

**Rationale**: Simple, no custom canvas drawing. The cursor is just a character with animated alpha. Removed when `assistant_message` (final) event arrives.

### 7. Status Pulse via InfiniteTransition

**Decision**: Use `rememberInfiniteTransition().animateFloat(initialValue = 0.3f, targetValue = 1.0f, animationSpec = infiniteRepeatable(tween(1500, Linear), RepeatMode.Reverse))` for the running status dot.

**Rationale**: Compose `InfiniteTransition` is the standard API for looping animations. It respects lifecycle (pauses when off-screen) and is efficient.

### 8. Syntax Highlighting Approach

**Decision**: Use a lightweight tokenizer (regex-based per language) that produces `AnnotatedString` with `SpanStyle` for each token type. Language grammars for Kotlin, TypeScript, Python, JSON, SQL, Bash. Unknown languages fall back to plain monospace.

**Rationale**: Full TextMate grammar parsing is overkill for a mobile chat app. Regex-based tokenization handles 90% of cases (keywords, strings, comments, numbers) with minimal code. The token colors come from `LocalCodeTheme`.

**Performance**: Tokenization runs in `Dispatchers.Default` via `remember { derivedStateOf {} }`. Results are cached per code block content hash.

## File Structure

```
packages/android/app/src/main/java/.../imbot/ui/theme/
├── Theme.kt                -- IMbotTheme composable, dynamic color, cross-fade
├── Color.kt                -- Seed colors, ProviderColors, StatusColors
├── Type.kt                 -- Typography with code font
├── Shape.kt                -- Corner radii per FOUNDATION.md
├── Animation.kt            -- Transition specs, duration constants, easing curves
├── CodeTheme.kt            -- Light/Dark code palettes, tokenizer color map
└── Transitions.kt          -- NavHost enter/exit/shared element transition specs

packages/android/app/src/main/java/.../imbot/ui/components/
├── StatusIndicator.kt      -- Pulse animation, color morph
├── ConnectionBanner.kt     -- Slide in/out animation
├── CodeBlock.kt            -- Syntax highlighting, copy button, horizontal scroll
└── StreamingCursor.kt      -- Blinking cursor composable
```

## Animation Constants

```kotlin
object IMbotAnimations {
    const val PAGE_ENTER_MS = 300
    const val PAGE_EXIT_MS = 250
    const val SHARED_ELEMENT_MS = 400
    const val MESSAGE_FADE_MS = 200
    const val TOOL_EXPAND_MS = 200
    const val STATUS_MORPH_MS = 300
    const val PULSE_MS = 1500
    const val THEME_CROSSFADE_MS = 400
    const val STAGGER_DELAY_MS = 50
    const val BANNER_RECOVERY_DISPLAY_MS = 2000
}
```
