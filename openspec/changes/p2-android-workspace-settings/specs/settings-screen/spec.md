# Capability: settings-screen

Tab 3 of the bottom navigation. Provides connection management, theme toggle, data management, and app information.

## ADDED Requirements

### Requirement: Connection Section

The SettingsScreen SHALL display a "连接" section at the top containing: Relay URL (tappable to edit), Connection status (real-time WebSocket state), MacBook status (online/offline from host_status WS messages), and OpenClaw status (online/offline). Status indicators SHALL use a green filled dot (●) for online and a gray filled dot (●) for offline, with corresponding text labels "已连接"/"未连接" for relay, "在线"/"离线" for hosts.

#### Scenario: Relay URL tap to edit

WHEN the user taps the Relay URL row
THEN an EditDialog appears with the current URL pre-filled in a text input
WHEN the user modifies the URL and taps "保存"
THEN the new URL is saved to SharedPreferences
AND the WebSocket and REST client reconnect to the new URL
AND the connection status updates accordingly

#### Scenario: Relay URL edit -- empty input

WHEN the user clears the URL field and taps "保存"
THEN the dialog shows a validation error "URL 不能为空"
AND the save is blocked

#### Scenario: Connection status real-time update

WHEN the WebSocket connection is active
THEN the connection status row shows "已连接" with a green dot
WHEN the WebSocket connection drops
THEN the status immediately updates to "未连接" with a gray dot
WHEN the WebSocket reconnects
THEN the status returns to "已连接" with a green dot

#### Scenario: MacBook and OpenClaw status display

WHEN the MacBook companion is online
THEN the MacBook row shows "在线 ●" (green)
WHEN the MacBook companion goes offline
THEN the MacBook row updates to "离线 ●" (gray)
AND the same pattern applies to the OpenClaw row

### Requirement: Appearance Section with Theme Toggle

The SettingsScreen SHALL display an "外观" section with a theme selector. The selector SHALL be a RadioGroup with three options: "跟随系统" (System), "浅色" (Light), "深色" (Dark). The selected theme SHALL be persisted in SharedPreferences and applied immediately with a cross-fade transition (400ms).

#### Scenario: Theme toggle -- instant switch to Light

WHEN the user selects "浅色"
THEN the app theme switches to light mode immediately
AND a cross-fade transition (400ms) plays without any white/black flash
AND the selection is persisted in SharedPreferences

#### Scenario: Theme toggle -- instant switch to Dark

WHEN the user selects "深色"
THEN the app theme switches to dark mode immediately
AND a cross-fade transition (400ms) plays without any white/black flash
AND the selection is persisted in SharedPreferences

#### Scenario: Theme toggle -- System follows OS

WHEN the user selects "跟随系统"
THEN the app theme follows the OS dark/light setting
AND if the OS switches from light to dark, the app follows automatically

#### Scenario: Theme persisted across restarts

WHEN the user selects "深色" and restarts the app
THEN the app launches in dark mode directly (no flash of light theme)

### Requirement: Data Section

The SettingsScreen SHALL display a "数据" section with two rows: "清除本地缓存" (tappable) and "会话保留天数" (read-only, displaying "30 天").

#### Scenario: Clear local cache -- happy path

WHEN the user taps "清除本地缓存"
THEN a confirmation AlertDialog appears: "确认清除本地缓存？"
WHEN the user taps "确认"
THEN the Room database is cleared (all cached sessions and events)
AND a Snackbar shows "已清除"
AND the session list will be re-fetched from relay on next visit

#### Scenario: Clear local cache -- cancel

WHEN the user taps "清除本地缓存" and then taps "取消"
THEN the dialog dismisses and no data is cleared

#### Scenario: Session retention display

WHEN the user views the Data section
THEN "会话保留天数" displays "30 天" as read-only text
AND this value is not editable (the purge policy is server-side)

### Requirement: About Section

The SettingsScreen SHALL display a "关于" section with a "版本" row showing the app version from `BuildConfig.VERSION_NAME`.

#### Scenario: Version display

WHEN the user views the About section
THEN the version row shows the current app version (e.g., "1.0.0")
AND the version is read from `BuildConfig.VERSION_NAME`
