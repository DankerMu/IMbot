# Tasks: p3-theme-and-animations

## 1. Theme Foundation

- [ ] 1.1 Create `Color.kt`: define seed color constants (Primary blue, Secondary green, Error red), light/dark surface/background colors, `ProviderColors` data class (claude amber, book violet, openclaw red), `StatusColors` data class (queued/running/completed/failed/cancelled for light and dark), `LocalProviderColors` and `LocalStatusColors` CompositionLocal providers
- [ ] 1.2 Create `Type.kt`: Material 3 Typography with code font (JetBrains Mono → Fira Code → monospace fallback chain) at 14sp for code, standard type scale for all other roles per FOUNDATION.md
- [ ] 1.3 Create `Shape.kt`: Material 3 Shapes with corner radii: Card 12dp, Chip 8dp, Button 20dp, BottomSheet 28dp, Dialog 28dp, Input 12dp, CodeBlock 8dp
- [ ] 1.4 Create `Theme.kt`: `IMbotTheme(themeMode, content)` composable that resolves colorScheme (dynamic on API 31+, static seed on lower), provides `MaterialTheme` + custom CompositionLocal providers; wrap content in `Crossfade(targetState = resolvedColorScheme, animationSpec = tween(400))`
- [ ] 1.5 Wire `IMbotTheme` in `IMbotApp.kt`: observe `themeMode` from DataStore via `collectAsState()`, pass to `IMbotTheme`

## 2. Code Theme

- [ ] 2.1 Create `CodeTheme.kt`: define `CodeTheme` sealed class with `Light` and `Dark` objects containing color maps (keyword, string, comment, number, type, function, background); create `LocalCodeTheme` CompositionLocal
- [ ] 2.2 Implement regex-based tokenizer for supported languages (Kotlin, TypeScript, Python, JSON, SQL, Bash): extract keywords, strings, comments, numbers, types → return list of `TokenSpan(start, end, type)`
- [ ] 2.3 Create `CodeBlock.kt` composable: takes `code: String` and `language: String?`, applies syntax highlighting via `AnnotatedString` with `SpanStyle` from `LocalCodeTheme`, renders in monospace font, background from theme, horizontal scroll for long lines, copy button at top-right
- [ ] 2.4 Implement copy button: tap → copy code to clipboard via `ClipboardManager` → show Snackbar "已复制"
- [ ] 2.5 Performance optimization: memoize tokenization result per content hash via `remember(code, language)`, run tokenizer on `Dispatchers.Default`

## 3. Page Transitions

- [ ] 3.1 Create `Animation.kt`: define `IMbotAnimations` object with all duration/easing constants from FOUNDATION.md
- [ ] 3.2 Create `Transitions.kt`: define `NavHost` `enterTransition`, `exitTransition`, `popEnterTransition`, `popExitTransition` using slideInHorizontally/slideOutHorizontally with durations 300ms/250ms and Emphasized easing
- [ ] 3.3 Implement shared element transition: wrap SessionCard and DetailScreen TopAppBar in `SharedTransitionLayout`, use `sharedElement(state = rememberSharedContentState("session-${id}"))`, 400ms Emphasized easing
- [ ] 3.4 Implement bottom nav tab cross-fade: use `Crossfade` or `AnimatedContent` for tab content switching (no horizontal slide)
- [ ] 3.5 Verify bottom sheet uses standard M3 `ModalBottomSheet` slide-up/down animation (no custom animation needed)

## 4. Message Animations

- [ ] 4.1 Implement new message fade-in + slide-up: in message LazyColumn, use `AnimatedVisibility(enter = fadeIn(200ms) + slideInVertically(200ms, initialOffsetY = 24dp))` for items with seq > lastSeenSeq
- [ ] 4.2 Create `StreamingCursor.kt`: composable showing "▊" with `rememberInfiniteTransition().animateFloat(0.3f, 1f, tween(500, RepeatMode.Reverse))` controlling alpha; accepts `isStreaming: Boolean` to show/hide
- [ ] 4.3 Implement ToolCallCard expand/collapse: use `AnimatedVisibility(enter = expandVertically(200ms), exit = shrinkVertically(200ms))` for the detail section, triggered by tap toggle state
- [ ] 4.4 Implement staggered initial load: on DetailScreen enter with cached messages, use `LaunchedEffect` to sequentially set `visible = true` on first 10 items with 50ms delay between each; items beyond 10 start visible

## 5. Status Animations

- [ ] 5.1 Implement running pulse in `StatusIndicator.kt`: when status == `running`, use `rememberInfiniteTransition().animateFloat(0.3f, 1.0f, infiniteRepeatable(tween(1500, Linear), RepeatMode.Reverse))` to modulate dot alpha
- [ ] 5.2 Implement status color morph: use `animateColorAsState(targetValue = statusColor, animationSpec = tween(300))` in StatusIndicator; color changes smoothly on status transition
- [ ] 5.3 Implement ConnectionBanner animation: use `AnimatedVisibility(enter = slideInVertically(fromTop), exit = slideOutVertically(toTop))` in a top-level composable; on reconnect, show "已恢复" green for 2s → then slide out via `delay(2000)` + set `visible = false`

## 6. Integration

- [ ] 6.1 Apply `IMbotTheme` to all existing screens: verify HomeScreen, DetailScreen, NewSessionScreen, WorkspaceScreen, SettingsScreen all render correctly in Light, Dark, and System modes
- [ ] 6.2 Apply page transitions to NavHost: set transition specs on all `composable()` destinations in NavGraph
- [ ] 6.3 Apply message animations to SessionDetailScreen message list
- [ ] 6.4 Apply StatusIndicator with pulse/morph to SessionCard and DetailScreen status bar
- [ ] 6.5 Apply ConnectionBanner to the root scaffold (visible on all screens)

## 7. Tests

- [ ] 7.1 Unit test theme resolution: System mode + light OS → light scheme, System mode + dark OS → dark scheme, explicit Light → light scheme regardless of OS, explicit Dark → dark scheme regardless of OS
- [ ] 7.2 Unit test dynamic color: API 31+ → uses dynamic scheme, API 30 → uses static seed scheme
- [ ] 7.3 Unit test code tokenizer: Kotlin keywords highlighted, strings in correct color, comments detected, unknown language → plain text
- [ ] 7.4 Unit test animation constants: verify all durations match FOUNDATION.md spec
- [ ] 7.5 UI test theme switch: toggle Light → Dark → verify surface colors change, no flash (screenshot comparison)
- [ ] 7.6 UI test shared element: tap SessionCard → verify DetailScreen appears with animation (compose test rule)
- [ ] 7.7 UI test streaming cursor: verify cursor blinks during streaming, disappears on complete
- [ ] 7.8 UI test ConnectionBanner: verify banner appears on disconnect, shows "已恢复" on reconnect, slides out after 2s
