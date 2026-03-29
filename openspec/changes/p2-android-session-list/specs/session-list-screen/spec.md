# Capability: session-list-screen

The home screen of the IMbot Android app. Displays all sessions in a scrollable list with real-time status updates, provider filtering, and actions for creating new sessions.

## ADDED Requirements

### Requirement: HomeScreen as Default Route

The `HomeScreen` SHALL be the default route when the app launches (after onboarding is complete). It SHALL display a `TopAppBar` with the title "IMbot" and a filter dropdown, a `LazyColumn` of `SessionCard` components, and a `FloatingActionButton` for creating new sessions.

#### Scenario: App launch -- load sessions from cache then refresh from API

WHEN the app launches and onboarding is complete
THEN `HomeScreen` is displayed as the default route
AND sessions are immediately loaded from Room cache (may be empty on first launch)
AND a background API call to `GET /v1/sessions` is triggered
AND when the API responds, the list updates with fresh data

#### Scenario: Pull to refresh -- new data loaded

WHEN the user pulls down on the session list
THEN a refresh indicator is shown
AND `GET /v1/sessions` is called
AND the list updates with the latest data from the API
AND the refresh indicator is dismissed

---

### Requirement: Running Sessions Pinned to Top

Sessions with `status == running` SHALL be displayed at the top of the list, visually separated from non-running sessions by a divider. Running sessions SHALL show a pulse animation on their status indicator.

#### Scenario: Running sessions at top with pulse animation

WHEN the session list contains 2 running and 5 completed sessions
THEN the 2 running sessions appear at the top of the list
AND a visual divider separates running from non-running sessions
AND each running session's status dot has a pulse animation
AND non-running sessions are sorted by `lastActiveAt` descending below the divider

---

### Requirement: Session Status Changes via WebSocket

The session list SHALL update in real-time when session status changes are received via the WebSocket connection. No manual refresh should be needed.

#### Scenario: Session status changes via WS -- list updates in real-time

WHEN a session transitions from `running` to `completed` via a WebSocket status message
THEN the session's card updates its status indicator from pulsing green to static green
AND the session moves from the pinned-top running group to the non-running group
AND the list re-sorts without a full refresh

---

### Requirement: Empty State

When the session list is empty (no sessions at all, or no sessions match the current filter), the screen SHALL display an empty state with a "新建会话" button.

#### Scenario: Empty list -- empty state with "新建会话" button

WHEN there are no sessions (fresh install or all deleted)
THEN an empty state illustration is shown with text "暂无会话"
AND a "新建会话" button is displayed
AND tapping the button navigates to `NewSessionScreen`

---

### Requirement: API Error During Refresh

If the API call fails during a pull-to-refresh or background refresh, the screen SHALL show a `Snackbar` error and retain the previously loaded data.

#### Scenario: API error during refresh -- Snackbar + keep old data

WHEN the user pulls to refresh
AND the API call to `GET /v1/sessions` fails (network error or server error)
THEN a Snackbar is shown with the error message (e.g., "刷新失败，请检查网络")
AND the previously loaded session list remains visible
AND the refresh indicator is dismissed

---

### Requirement: Filter by Provider

The `TopAppBar` SHALL contain a dropdown filter that allows filtering sessions by provider. Filtering SHALL be immediate and applied locally.

#### Scenario: Filter by provider -- immediate local filtering

WHEN the user selects "Claude Code" from the filter dropdown
THEN only sessions with `provider == CLAUDE` are shown
AND the filter change is instant (no API call)
AND if no sessions match, the empty state is shown with the filter context
