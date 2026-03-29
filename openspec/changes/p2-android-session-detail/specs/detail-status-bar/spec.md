# Capability: detail-status-bar

A thin color strip below the TopAppBar that indicates the current session status. Uses color and animation to provide at-a-glance status awareness.

## ADDED Requirements

### Requirement: Status Color Bar

A 2dp-height horizontal bar SHALL be displayed immediately below the `TopAppBar`. The bar color SHALL correspond to the session status.

#### Scenario: Running -- green pulse

WHEN the session status is `running`
THEN the status bar is green
AND the bar has a pulse animation (alpha oscillates between 0.3 and 1.0 over 750ms)

#### Scenario: Completed -- static green

WHEN the session status is `completed`
THEN the status bar is static green (no animation)

#### Scenario: Failed -- static red

WHEN the session status is `failed`
THEN the status bar is static red (no animation)

#### Scenario: Cancelled -- static gray

WHEN the session status is `cancelled`
THEN the status bar is static gray (no animation)

---

### Requirement: Color Transition Animation

When the session status changes, the status bar color SHALL transition smoothly via a color morph animation.

#### Scenario: Status changes -- color morph animation 300ms

WHEN the session status changes from `running` (green) to `completed` (green, static)
THEN the pulse animation stops and the bar becomes static green over 300ms
WHEN the session status changes from `running` (green) to `failed` (red)
THEN the bar color morphs from green to red over 300ms
AND the pulse animation stops during the transition
