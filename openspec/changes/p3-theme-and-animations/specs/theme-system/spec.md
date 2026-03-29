# Capability: theme-system

Material 3 theme system with custom seed colors, dynamic color, provider-specific colors, and theme persistence.

## ADDED Requirements

### Requirement: Material 3 Color Scheme with Custom Seed Colors

The app SHALL use Material 3 `ColorScheme` generated from custom seed colors: Primary `#1A73E8` (blue), Secondary `#34A853` (green), Error `#EA4335` (red). Surface SHALL be `#FAFAFA` (light) / `#1E1E1E` (dark). Background SHALL be `#FFFFFF` (light) / `#121212` (dark). The color scheme SHALL be generated using `colorScheme()` with these seeds as input.

#### Scenario: Default theme is System (follows OS)

WHEN the app launches for the first time with no persisted theme preference
THEN the theme follows the OS dark/light mode setting
AND if the OS is in light mode, all surfaces use the light palette
AND if the OS is in dark mode, all surfaces use the dark palette

#### Scenario: Manual switch to Light mode

WHEN the user selects "浅色" (Light) in Settings
THEN all Material 3 surfaces, text colors, and component colors switch to the light palette
AND Primary color is `#1A73E8`, Secondary `#34A853`, Error `#EA4335`
AND Surface is `#FAFAFA`, Background `#FFFFFF`

#### Scenario: Manual switch to Dark mode

WHEN the user selects "深色" (Dark) in Settings
THEN all Material 3 surfaces, text colors, and component colors switch to the dark palette
AND Primary color is `#8AB4F8`, Secondary `#81C995`, Error `#F28B82`
AND Surface is `#1E1E1E`, Background `#121212`

### Requirement: Dynamic Color on Android 12+

On devices running Android 12 (API 31) or higher, the app SHALL use `dynamicLightColorScheme()` / `dynamicDarkColorScheme()` to derive colors from the user's wallpaper. On pre-12 devices, the app SHALL fall back to the custom seed colors.

#### Scenario: Android 12+ uses dynamic color from wallpaper

WHEN the app runs on Android 12+ with System theme
THEN the color scheme is derived from the device wallpaper via Material You
AND provider-specific colors are NOT affected by dynamic color (they remain hardcoded)

#### Scenario: Pre-Android 12 uses custom seed colors

WHEN the app runs on Android 11 or lower
THEN the color scheme uses the hardcoded seed colors defined in FOUNDATION.md
AND dynamic color APIs are not called

### Requirement: Provider-Specific Colors

The theme system SHALL define provider-specific colors as a separate `ProviderColors` object that is NOT affected by dynamic color or theme mode. Colors: Claude Code `#D97706` (amber), book `#7C3AED` (violet), OpenClaw `#DC2626` (red). These colors SHALL be used for provider icons, chips, and session card accents.

#### Scenario: Provider colors consistent in both themes

WHEN the app is in Light mode
THEN Claude sessions use amber (`#D97706`), book sessions use violet (`#7C3AED`), OpenClaw sessions use red (`#DC2626`)
WHEN the app switches to Dark mode
THEN the same provider colors are used (no palette shift)

### Requirement: Theme Persistence and Switch Animation

The selected theme mode (System/Light/Dark) SHALL be persisted in SharedPreferences (via DataStore). On theme switch, the app SHALL animate with a cross-fade transition of 400ms duration. There MUST be no white or black flash during the transition.

#### Scenario: Cross-fade on theme switch (no flash)

WHEN the user toggles from Light to Dark
THEN a 400ms cross-fade plays between the light and dark surfaces
AND there is no intermediate frame showing pure white or pure black

#### Scenario: Theme persisted across app restarts

WHEN the user selects Dark mode and restarts the app
THEN the app launches directly in dark mode
AND no flash of light theme is visible during startup

### Requirement: Status Colors Follow Foundation Spec

Status indicator colors SHALL match the FOUNDATION.md spec: queued `#9CA3AF`/`#6B7280`, running `#10B981`/`#34D399`, completed `#059669`/`#6EE7B7`, failed `#EF4444`/`#FCA5A5`, cancelled `#6B7280`/`#9CA3AF` (light/dark).

#### Scenario: Status colors correct in light theme

WHEN viewing a running session in light mode
THEN the status indicator uses `#10B981` (green)

#### Scenario: Status colors correct in dark theme

WHEN viewing a running session in dark mode
THEN the status indicator uses `#34D399` (lighter green)
