# Capability: workspace-manager-screen

Tab 2 of the bottom navigation. Displays workspace roots grouped by host with full CRUD and directory browsing.

## ADDED Requirements

### Requirement: Workspace Root List Grouped by Host

The WorkspaceScreen SHALL display all workspace roots grouped under their respective host. Each host section SHALL show the host name and online/offline status indicator (green dot for online, gray dot for offline). Each root item SHALL display the provider icon (Claude amber, book violet, OpenClaw red), the user-defined label, and the absolute path (truncated with ellipsis if exceeding one line). The list SHALL be fetched from `GET /v1/hosts` combined with `GET /v1/hosts/:hostId/roots` for each host.

#### Scenario: Roots displayed grouped by host correctly

WHEN the user navigates to the Workspace tab
THEN the screen displays hosts as section headers with their online/offline status
AND each host section contains its workspace roots
AND each root shows the provider-colored icon, label, and path
AND the roots are ordered by creation time (oldest first) within each host

#### Scenario: Host status updates in real-time

WHEN a host goes offline while the user is viewing the Workspace tab
THEN the host section header updates to show a gray dot and "离线" label
AND no navigation or stale-data errors occur

#### Scenario: Empty workspace roots

WHEN no workspace roots are configured for any host
THEN the screen shows an empty state illustration with text "暂无根目录"
AND a prominent "添加根目录" CTA button is displayed
AND tapping the CTA opens the Add Root Bottom Sheet

### Requirement: Add Workspace Root via Bottom Sheet

The WorkspaceScreen SHALL provide a "+" button in the TopAppBar that opens a Bottom Sheet for adding a new workspace root. The Bottom Sheet SHALL contain: a provider selector (dropdown or chip group for Claude/book/OpenClaw), a host selector (auto-populated based on provider -- MacBook for claude/book, Relay VPS for openclaw), a directory browser for selecting the path, and an optional label text input. The "添加" button SHALL call `POST /v1/hosts/:hostId/roots` and on success dismiss the sheet and refresh the list.

#### Scenario: Add root -- full happy path

WHEN the user taps "+" in the TopAppBar
THEN a Bottom Sheet slides up with the add-root form
WHEN the user selects provider "Claude", browses to `/Users/danker/Desktop/AI-vault`, enters label "AI-vault", and taps "添加"
THEN a POST request is sent to the relay
AND on 201 response, the Bottom Sheet dismisses
AND the root appears in the list under the MacBook host section

#### Scenario: Add root -- host offline

WHEN the user selects provider "Claude" and the MacBook host is offline
THEN the directory browser shows an error "MacBook 离线，无法浏览目录"
AND the "添加" button is disabled
AND the user can dismiss the sheet and retry later

#### Scenario: Add root -- duplicate path conflict

WHEN the user tries to add a root with a host+provider+path that already exists
THEN the relay returns 409
AND the Bottom Sheet shows an inline error "该目录已添加"
AND the form remains open for correction

#### Scenario: Add root -- provider selection determines host

WHEN the user selects provider "OpenClaw"
THEN the host is automatically set to "Relay VPS"
AND the directory browser uses the relay-local host for browsing
WHEN the user selects provider "Claude" or "book"
THEN the host is automatically set to the MacBook host

#### Scenario: Book roots only appear under novel directory constraint

WHEN the user adds a root with provider "book"
THEN the directory browser SHALL filter to show only directories relevant to book provider
AND the root appears in the list with the violet book icon

### Requirement: Remove Workspace Root

Each root item SHALL display a "✕" button. Tapping it SHALL show a confirmation AlertDialog: "确认移除此根目录？已有会话不受影响。" On confirm, the app SHALL call `DELETE /v1/hosts/:hostId/roots/:rootId` and remove the item from the list with an animation.

#### Scenario: Remove root -- happy path

WHEN the user taps "✕" on a root item
THEN a confirmation dialog appears: "确认移除此根目录？已有会话不受影响。"
WHEN the user taps "确认"
THEN a DELETE request is sent to the relay
AND on 204 response, the root item animates out of the list
AND existing sessions under that root are NOT affected

#### Scenario: Remove root -- cancel

WHEN the user taps "✕" on a root item and then taps "取消" in the dialog
THEN the dialog dismisses
AND the root remains in the list unchanged

#### Scenario: Remove root -- API failure

WHEN the DELETE request fails (network error or 404)
THEN a Snackbar shows "删除失败，请重试"
AND the root remains in the list

### Requirement: Tap Root to Browse Directory and Sessions

Tapping a workspace root SHALL navigate to a RootDetailScreen showing: the root label in the TopAppBar with a back arrow, a list of subdirectories (fetched from `GET /v1/hosts/:hostId/browse?path=<root_path>`), and a "该目录下的会话" section listing sessions whose `workspace_cwd` starts with the root path.

#### Scenario: Tap root -- shows subdirectories and sessions

WHEN the user taps a root item labeled "AI-vault"
THEN the app navigates to RootDetailScreen
AND the TopAppBar shows "← AI-vault"
AND subdirectories are listed (e.g., IMbot, projects, tools)
AND sessions in that directory are shown as SessionCards below the directory list

#### Scenario: Tap root -- empty directory

WHEN the root directory contains no subdirectories and no sessions
THEN the directory section shows "此目录为空"
AND the session section shows "暂无会话"

#### Scenario: Tap subdirectory -- navigates deeper

WHEN the user taps a subdirectory in the RootDetailScreen
THEN the directory browser navigates into that subdirectory
AND the TopAppBar updates to show the current directory name
AND sessions for the new path are loaded

### Requirement: Pull-to-Refresh

The WorkspaceScreen root list SHALL support pull-to-refresh using Material 3 `PullToRefreshBox`. Refreshing SHALL re-fetch hosts and roots from the relay.

#### Scenario: Pull to refresh -- success

WHEN the user pulls down on the workspace root list
THEN a refresh indicator appears
AND hosts + roots are re-fetched from the relay
AND the list updates with any changes
AND the indicator disappears

#### Scenario: Pull to refresh -- failure

WHEN the refresh API call fails
THEN a Snackbar shows "刷新失败"
AND the existing list data is preserved
