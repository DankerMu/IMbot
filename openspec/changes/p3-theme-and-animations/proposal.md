# Proposal: p3-theme-and-animations

## Why

The user has high aesthetic requirements. The app needs a complete Material 3 theme system with dynamic color, smooth animations, and polished transitions to feel premium. Without a proper theme system, the app defaults to generic M3 colors with no provider identity, no dark mode support, and abrupt transitions. This change establishes the visual foundation that elevates the entire user experience.

## What Changes

| Area | Change |
|------|--------|
| Theme system | Material 3 custom seed colors (Primary blue, Secondary green, Error red). System/Light/Dark modes. Dynamic color on Android 12+. Provider-specific colors (Claude amber, book violet, OpenClaw red). Code syntax highlighting palettes. Theme persistence in SharedPreferences. Cross-fade switch (400ms). |
| Page transitions | Shared element transition SessionCard → DetailScreen (400ms Emphasized). Standard forward/back (300ms/250ms). Bottom sheet slide-up. Cross-fade between bottom nav tabs. |
| Message animations | New message fade-in + slide-up (200ms). Streaming cursor blink (▊). Tool call card expand/collapse (200ms). Staggered message list appearance. |
| Status animations | Running status pulse (1500ms loop, alpha 0.3→1.0). Status change color morph (300ms). ConnectionBanner appear/disappear slide. |
| Code theme | GitHub-style syntax highlighting for light and dark themes. Code block background follows theme. Copy button on hover/tap. |

## Capabilities

- `theme-system` -- Material 3 color scheme, dynamic color, provider colors, theme persistence, switch animation.
- `page-transitions` -- Shared element, forward/back nav, bottom sheet, tab cross-fade.
- `message-animations` -- Message appearance, streaming cursor, tool call expand/collapse, staggered loading.
- `status-animations` -- Running pulse, status color morph, connection banner slide.
- `code-theme` -- Syntax highlighting palettes for both themes, code block styling, copy functionality.

## Affected Areas

- `packages/android/app/src/main/java/.../imbot/ui/theme/` -- Theme.kt, Color.kt, Type.kt, Animation.kt (new)
- `packages/android/app/src/main/java/.../imbot/ui/home/` -- SessionCard shared element setup
- `packages/android/app/src/main/java/.../imbot/ui/detail/` -- DetailScreen shared element target, message animations
- `packages/android/app/src/main/java/.../imbot/ui/components/` -- MessageBubble, ToolCallCard, StatusIndicator, ConnectionBanner, CodeBlock
- `packages/android/app/src/main/java/.../imbot/ui/navigation/` -- NavHost transition specs

## Dependencies

- Jetpack Compose Animation APIs: `AnimatedVisibility`, `animateColorAsState`, `SharedTransitionLayout`, `Crossfade`.
- Material 3 dynamic color: `dynamicDarkColorScheme()` / `dynamicLightColorScheme()` (Android 12+).
- SharedPreferences / DataStore for theme persistence (from `p2-android-workspace-settings`).
- Syntax highlighting library: Compose-compatible highlight-compose or manual token coloring via `AnnotatedString`.

## Risks

| Risk | Mitigation |
|------|-----------|
| Shared element transition flickers on low-end devices | Test on API 26 minimum device; degrade to fade if SharedTransitionLayout not available |
| Dynamic color conflicts with provider colors | Provider colors are explicit overrides, not derived from dynamic color; keep them in a separate `ProviderColors` object |
| Animation jank on long message lists | Profile with Compose layout inspector; skip animations for off-screen items; use `LazyColumn` key-based animations only for visible items |
| Syntax highlighting performance on large code blocks | Limit highlighting to first 500 lines; use memoization for parsed tokens |

## References

- docs/specs/ui-ux-rd-spec/01_Foundation/FOUNDATION.md (color system, animation tokens, code palettes)
- docs/specs/ui-ux-rd-spec/03_Patterns/PATTERNS.md (P-03 shared element, P-06 loading patterns)
- docs/specs/ui-ux-rd-spec/02_Components/COMPONENTS.md
