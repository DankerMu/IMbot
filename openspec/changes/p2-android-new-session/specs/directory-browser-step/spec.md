# Capability: directory-browser-step

Step 2 of the new session flow. Uses the DirectoryBrowser component to let the user select a working directory on the target host.

## ADDED Requirements

### Requirement: Show Roots for Selected Provider

On entering step 2, the screen SHALL fetch workspace roots for the selected provider's host via `GET /v1/hosts/:id/roots` and display them using the `DirectoryBrowser` component.

#### Scenario: Shows correct roots for selected provider

WHEN the user selected "Claude Code" (host: macbook-1) on step 1
AND step 2 is entered
THEN `GET /v1/hosts/macbook-1/roots` is called
AND roots with `provider == "claude"` are displayed as the top-level entries

---

### Requirement: book Provider Restricted to Novel Roots

When the selected provider is `book`, only roots with `provider == "book"` SHALL be shown. Other provider roots SHALL be filtered out.

#### Scenario: book -- only novel root shown

WHEN the user selected "book" on step 1
THEN only roots with `provider == "book"` are displayed (e.g., the "novel" root)
AND Claude/OpenClaw roots are not shown

---

### Requirement: Directory Browsing

The user SHALL be able to tap a directory to enter it and browse subdirectories. The `DirectoryBrowser` component handles this interaction.

#### Scenario: Tap directory -- enters subdirectory

WHEN the user taps a directory entry
THEN `GET /v1/hosts/:id/browse?path=<entry_path>` is called
AND the subdirectories of the tapped directory are displayed
AND the breadcrumb updates to show the current path

#### Scenario: Breadcrumb navigation works

WHEN the user has navigated 3 levels deep
THEN the breadcrumb shows all 3 levels
AND tapping a breadcrumb segment navigates back to that level

---

### Requirement: Directory Selection

A "选择此目录" button at the bottom of the browser SHALL save the current path as the `cwd` for the new session.

#### Scenario: Select directory -- cwd saved

WHEN the user is viewing the contents of `/Users/danker/Desktop/AI-vault/IMbot`
AND taps "选择此目录"
THEN `cwd = "/Users/danker/Desktop/AI-vault/IMbot"` is saved in the ViewModel state
AND the "下一步" button becomes enabled

---

### Requirement: Loading and Error States

The directory browser SHALL show a loading shimmer while fetching directory contents and an inline error with retry button on failure.

#### Scenario: Loading state while browsing

WHEN the user taps a directory to enter it
THEN a shimmer/loading indicator is shown while the API call is in progress

#### Scenario: Error fetching dirs -- inline error + retry

WHEN `GET /v1/hosts/:id/browse` fails (e.g., host offline, network error)
THEN an inline error message is shown (e.g., "无法加载目录")
AND a "重试" button is shown
AND tapping "重试" re-fetches the directory listing
