# Capability: new-session-flow

A 3-step wizard for creating new AI agent sessions. Uses HorizontalPager with step indicator, forward/back navigation, and final submission.

## ADDED Requirements

### Requirement: Three-Step HorizontalPager

The `NewSessionScreen` SHALL use a `HorizontalPager` with 3 pages: Provider Picker (step 1), Directory Browser (step 2), Prompt Input (step 3). A step indicator at the top shows the current step. Navigation between steps is via "下一步" / "上一步" buttons (not swipe).

#### Scenario: Complete flow happy path

WHEN the user opens the new session screen
AND selects "Claude Code" provider on step 1
AND taps "下一步"
AND browses to `/Users/danker/Desktop/AI-vault/IMbot` on step 2
AND taps "下一步"
AND enters prompt "帮我分析架构" on step 3
AND taps "开始"
THEN `POST /v1/sessions` is called with `{ provider: "claude", host_id: "macbook-1", cwd: "/Users/danker/Desktop/AI-vault/IMbot", prompt: "帮我分析架构", model: "sonnet" }`
AND on success, the app navigates to `SessionDetailScreen` for the new session

---

### Requirement: Back Navigation Preserves State

Navigating back from a later step to an earlier step SHALL preserve all selections made on the earlier step.

#### Scenario: Go back from step 2 -- step 1 preserved

WHEN the user selects "Claude Code" on step 1, advances to step 2
AND taps "上一步"
THEN step 1 is shown with "Claude Code" still selected

#### Scenario: Go back from step 3 -- step 2 preserved

WHEN the user selects a directory on step 2, advances to step 3
AND taps "上一步"
THEN step 2 is shown with the previously selected directory still highlighted
AND the breadcrumb shows the previously browsed path

---

### Requirement: Create Session Success Navigation

On successful session creation, the app SHALL navigate to the `SessionDetailScreen` for the newly created session.

#### Scenario: Create success -- navigate to detail

WHEN `POST /v1/sessions` returns `201` with the new session
THEN the app navigates to `SessionDetailScreen` with the new session's ID
AND the new session screen is removed from the back stack

---

### Requirement: Create Session Failure Handling

On session creation failure, the app SHALL display a `Snackbar` error and remain on step 3 so the user can retry.

#### Scenario: Create failure -- Snackbar error + stay on step 3

WHEN `POST /v1/sessions` returns an error (e.g., `502 host_offline`)
THEN a Snackbar is shown with the error message (e.g., "创建失败：MacBook 离线")
AND the user remains on step 3
AND the "开始" button returns to its normal (non-loading) state

---

### Requirement: Double-Tap Prevention

The "开始" button SHALL enter a loading state on tap, preventing duplicate submissions.

#### Scenario: Double tap "开始" -- prevented (loading state)

WHEN the user taps "开始"
THEN the button shows a loading indicator and becomes disabled
AND tapping it again has no effect
AND only one `POST /v1/sessions` request is made
