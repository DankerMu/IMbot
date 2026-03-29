# Capability: error-states-ux

Empty states, loading skeletons, inline retry buttons, Snackbar for transient errors, and confirmation dialogs for destructive actions.

## ADDED Requirements

### Requirement: Empty States for All Pages

Every page that displays a list SHALL have a dedicated empty state with an illustration and a call-to-action. The empty states SHALL be shown when the list is empty after a successful data load (not during loading or error).

#### Scenario: Session list empty -- illustration + CTA

WHEN the session list loads successfully but contains zero sessions
THEN the screen displays an empty state illustration
AND the text "暂无会话" is shown
AND a "新建会话" CTA button is displayed
AND tapping the CTA navigates to NewSessionScreen

#### Scenario: Directory browser error -- inline retry

WHEN the directory browser fails to load (network error)
THEN the browser area shows an error message "加载失败"
AND an inline "重试" button is displayed below the message
AND tapping "重试" re-fetches the directory listing

#### Scenario: Workspace manager empty -- add root CTA

WHEN the workspace manager has no configured roots
THEN an empty state shows "暂无根目录"
AND an "添加根目录" CTA button is displayed

### Requirement: Loading Skeletons (Shimmer)

Initial data loads SHALL display shimmer skeleton placeholders that match the shape and layout of the expected content. The shimmer animation SHALL be a horizontal gradient sweep from left to right repeating every 1.5 seconds.

#### Scenario: Shimmer shown during initial load

WHEN the user opens SessionListScreen for the first time
THEN shimmer skeleton cards are displayed matching the SessionCard layout
AND the shimmer animates with a horizontal gradient sweep
AND when data loads, the shimmer is replaced by actual content

#### Scenario: Shimmer for directory listing

WHEN the user enters a directory in the browser and data is loading
THEN shimmer skeleton rows matching directory entry height are displayed
AND they are replaced by actual entries when the API responds

#### Scenario: No shimmer on subsequent visits (cached data)

WHEN the user has cached session data in Room
THEN the cached data is shown immediately (no shimmer)
AND a background refresh updates the data if changed

### Requirement: Inline Retry Buttons for Recoverable Errors

When a data load fails with a recoverable error (network timeout, server 5xx), the error area SHALL display the error message and an inline "重试" button. Tapping "重试" SHALL re-execute the failed request.

#### Scenario: API timeout -- retry button

WHEN a REST API call times out
THEN the content area shows "请求超时" with an inline "重试" button
AND tapping "重试" re-sends the request
AND on success, the content replaces the error state

#### Scenario: Server error -- retry button

WHEN the relay returns a 500 error
THEN the content area shows "服务器错误" with an inline "重试" button

### Requirement: Snackbar for Transient Errors

Transient errors that do not block page content (e.g., a failed delete, a failed send) SHALL be displayed as a Snackbar at the bottom of the screen. The Snackbar SHALL auto-dismiss after 4 seconds. Snackbars SHALL use the Material 3 Snackbar component.

#### Scenario: Snackbar -- API timeout

WHEN a non-blocking API call (e.g., delete session) times out
THEN a Snackbar appears at the bottom: "请求超时"
AND the Snackbar auto-dismisses after 4 seconds

#### Scenario: Snackbar auto-dismiss after 4s

WHEN a Snackbar is displayed
THEN it automatically disappears after 4 seconds
AND the user can manually dismiss it by swiping

#### Scenario: Snackbar with action

WHEN a transient error has a possible recovery action
THEN the Snackbar includes an action button (e.g., "重试")
AND tapping the action re-executes the failed operation

### Requirement: Destructive Confirmation Dialogs

All destructive actions SHALL require confirmation via an AlertDialog before execution. The dialog SHALL have a clear description of the action's consequence, a "取消" (Cancel) button, and a destructive action button (e.g., "删除", "清除").

#### Scenario: Delete confirmation dialog

WHEN the user triggers a delete action (session, root, cache)
THEN an AlertDialog appears with a description of the consequence
AND the dialog has "取消" and "删除" (or "清除") buttons
AND "取消" dismisses the dialog with no effect
AND the destructive button executes the action

#### Scenario: Cancel button dismisses safely

WHEN the user taps "取消" in a confirmation dialog
THEN the dialog dismisses
AND no data is modified
AND the original list state is preserved
