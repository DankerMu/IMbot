# Capability: status-animations

Animations for session status indicators, status transitions, and connection banners.

## ADDED Requirements

### Requirement: Running Status Pulse Animation

When a session is in `running` status, the status indicator dot SHALL pulse with an infinite loop animation: alpha oscillates between 0.3 and 1.0 over 1500ms using `Linear` easing. The pulse SHALL stop when the session transitions to a terminal state (completed, failed, cancelled).

#### Scenario: Running session -- green dot pulses

WHEN a session's status is `running`
THEN the green status indicator dot pulses:
  - Alpha animates 0.3 → 1.0 → 0.3 in a 1500ms loop
  - Uses Linear easing for smooth breathing effect
AND the pulse continues indefinitely while status remains `running`

#### Scenario: Session completes -- pulse stops

WHEN a running session transitions to `completed`
THEN the pulse animation stops
AND the indicator holds at full alpha (1.0) with the completed color

### Requirement: Status Change Color Morph

When a session's status changes (e.g., queued → running, running → completed), the status indicator color SHALL morph from the old status color to the new status color over 300ms using `Standard` easing via `animateColorAsState`.

#### Scenario: Status changes -- color smoothly transitions

WHEN a session transitions from `queued` (gray) to `running` (green)
THEN the indicator color morphs from gray to green over 300ms
AND there is no abrupt color jump

#### Scenario: Running to failed -- color morphs to red

WHEN a session transitions from `running` (green) to `failed` (red)
THEN the indicator color morphs from green to red over 300ms

### Requirement: ConnectionBanner Appear/Disappear Animation

The ConnectionBanner SHALL slide down from the top when a connection error is detected, and slide up (out of view) when the connection is restored. The banner SHALL use `AnimatedVisibility` with `slideInVertically` (from top) and `slideOutVertically` (to top).

#### Scenario: Connection lost -- banner slides down

WHEN the WebSocket connection is lost
THEN a red ConnectionBanner slides down from the top of the screen
AND the banner shows "无法连接服务器" with a reconnecting spinner
AND page content shifts down to accommodate the banner

#### Scenario: Connection restored -- banner shows recovery then slides up

WHEN the WebSocket connection is restored
THEN the banner text changes to "已恢复" with green background
AND after 2 seconds, the banner slides up and out of view
AND page content shifts back up

#### Scenario: Multiple status changes during animation

WHEN the connection drops and restores rapidly (within 1 second)
THEN the banner animation does not stutter or produce visual artifacts
AND the final state (connected or disconnected) is correctly displayed
