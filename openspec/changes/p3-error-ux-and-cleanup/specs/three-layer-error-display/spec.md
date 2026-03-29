# Capability: three-layer-error-display

Hierarchical error display system distinguishing relay, host, and provider faults with prioritized UI placement and actionable messages.

## ADDED Requirements

### Requirement: Relay Unreachable -- ConnectionBanner (Layer 1)

When the WebSocket connection to the relay server is lost, the UI SHALL display a red `ConnectionBanner` at the top of ALL pages. The banner SHALL show "无法连接服务器" with a reconnecting spinner icon. This is the highest-priority error and SHALL override all lower-level error displays.

#### Scenario: Relay down -- red banner covers all pages

WHEN the WebSocket connection to the relay fails or drops
THEN a red ConnectionBanner appears at the top of the current page
AND the banner text is "无法连接服务器"
AND the banner includes a small reconnecting spinner
AND navigating to any other page also shows this banner

#### Scenario: Relay recovers -- banner clears

WHEN the relay connection is restored
THEN the banner changes to green with text "已恢复"
AND after 2 seconds, the banner slides out of view

### Requirement: Host Offline -- Page-Level Banner (Layer 2)

When the relay is reachable but a required host (MacBook) is offline, pages that depend on that host SHALL display an orange inline banner: "MacBook 离线". This applies to pages that need companion access (WorkspaceScreen directory browser, NewSessionScreen for claude/book provider). The banner SHALL NOT appear on pages that don't require the host.

#### Scenario: Relay up but MacBook offline -- orange banner on relevant pages

WHEN the relay WebSocket is connected but MacBook host status is "offline"
THEN the WorkspaceScreen shows an orange banner "MacBook 离线" below the TopAppBar
AND the NewSessionScreen (when claude/book selected) shows the same banner
AND the SessionListScreen does NOT show this banner (it displays all providers)
AND the SettingsScreen shows MacBook as "离线" in its status row (not a banner)

#### Scenario: MacBook comes online -- banner disappears

WHEN the MacBook host status changes to "online" via WebSocket host_status message
THEN the orange "MacBook 离线" banner disappears from all pages
AND pages resume normal functionality

### Requirement: Provider Unreachable -- Session-Level Banner (Layer 3)

When both relay and host are online but a specific provider upstream fails (e.g., Claude API unreachable, OpenClaw gateway down), the error SHALL be displayed as an orange inline banner ONLY on the affected session's DetailScreen. The banner text SHALL identify the provider: "Claude upstream 不可用" or "OpenClaw 不可用".

#### Scenario: Claude upstream down -- session-level error only

WHEN a running session receives a `session_error` with error_code `provider_unreachable`
THEN the SessionDetailScreen for that session shows an orange banner "Claude upstream 不可用"
AND other sessions (e.g., OpenClaw sessions) are NOT affected
AND the SessionListScreen shows the session status as `failed` but no global banner

### Requirement: Error Priority System

The error display system SHALL enforce strict priority: ConnectionBanner (relay) > Page-level banner (host) > Session-level banner (provider) > Snackbar (transient). When a higher-priority error is active, lower-priority errors for the same scope SHALL be suppressed. When the higher-priority error resolves, suppressed lower-priority errors SHALL become visible if still active.

#### Scenario: Multiple errors -- highest priority shown

WHEN the relay is down (Layer 1) AND the MacBook is offline (Layer 2) AND a session has provider error (Layer 3)
THEN only the red ConnectionBanner is visible
WHEN the relay reconnects
THEN the ConnectionBanner clears, and the orange "MacBook 离线" banner becomes visible on relevant pages
WHEN the MacBook comes online
THEN the session-level provider error becomes visible on the affected session's detail page

#### Scenario: Error message includes actionable text

WHEN any error banner is displayed
THEN the error message includes actionable guidance:
  - Layer 1: "无法连接服务器，正在重连..."
  - Layer 2: "MacBook 离线，请检查 companion 是否运行"
  - Layer 3: "Claude upstream 不可用，请稍后重试"
AND the messages are NOT generic (e.g., not just "error" or "出错了")

### Requirement: Error Resolution Clears Banners

When the underlying fault resolves (relay reconnects, host comes online, provider recovers), the corresponding banner SHALL disappear automatically without user action.

#### Scenario: Error resolves -- banner disappears

WHEN a Layer 2 error (MacBook offline) is displayed
AND the MacBook comes back online (host_status: online received)
THEN the orange banner disappears with a slide-up animation
AND no user action is required
