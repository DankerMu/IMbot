# Capability: bottom-navigation

Material 3 `NavigationBar` with 3 tabs providing the app's primary navigation structure. Includes a badge indicator for running sessions.

## ADDED Requirements

### Requirement: Three-Tab Navigation Bar

The app SHALL display a Material 3 `NavigationBar` at the bottom of the screen with 3 tabs: 会话 (Sessions, home), 目录 (Workspace), 设置 (Settings). Each tab has an icon and label.

#### Scenario: Tap tabs -- navigate correctly

WHEN the user taps the "目录" tab
THEN the screen navigates to `WorkspaceScreen`
AND the "目录" tab is highlighted as selected
WHEN the user taps the "设置" tab
THEN the screen navigates to `SettingsScreen`
WHEN the user taps the "会话" tab
THEN the screen navigates back to `HomeScreen`

---

### Requirement: Selected Tab Highlighting

The currently active tab SHALL be visually highlighted using Material 3's default `NavigationBar` selection style (filled indicator behind selected icon).

#### Scenario: Selected tab highlighted

WHEN the user is on the `HomeScreen`
THEN the "会话" tab shows the Material 3 active indicator
AND the other two tabs show their default unselected style

---

### Requirement: Badge Dot on Running Sessions

The "会话" tab SHALL display a small badge dot when there are one or more sessions with `status == running` and the user is NOT on the home tab.

#### Scenario: Badge shows when running sessions exist

WHEN there is at least one session with `status == running`
AND the user is on the "目录" or "设置" tab
THEN the "会话" tab icon shows a small badge dot

#### Scenario: Badge hides when no running sessions

WHEN all sessions have terminal status (completed/failed/cancelled/queued) or there are no sessions
THEN the "会话" tab icon shows no badge dot

---

### Requirement: Back Press Behavior

Pressing the system back button from a non-home tab SHALL navigate to the home tab instead of exiting the app.

#### Scenario: Back press from non-home tab -- goes to home tab

WHEN the user is on the "设置" tab
AND presses the system back button
THEN the app navigates to the "会话" tab (home)
AND pressing back again triggers the default system behavior (exit or minimize)
