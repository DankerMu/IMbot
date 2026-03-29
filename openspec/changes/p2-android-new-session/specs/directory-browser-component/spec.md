# Capability: directory-browser-component

A reusable Compose component for browsing remote directories on a host. Fetches directory listings via API, provides breadcrumb navigation, and handles loading/error states.

## ADDED Requirements

### Requirement: Fetch and Display Directories

The `DirectoryBrowser` component SHALL fetch directory contents from `GET /v1/hosts/:hostId/browse?path=<currentPath>` and display only directory entries (no files). Each directory entry is a tappable row with a folder icon and directory name.

#### Scenario: Fetch success -- dirs shown

WHEN the component loads with `hostId = "macbook-1"` and `initialPath = "/Users/danker/Desktop/AI-vault"`
THEN `GET /v1/hosts/macbook-1/browse?path=/Users/danker/Desktop/AI-vault` is called
AND the response directories are displayed as a list of folder entries

#### Scenario: Fetch error -- error + retry

WHEN the API call fails (network error, host offline, etc.)
THEN an inline error message is shown
AND a "重试" button is provided
AND tapping "重试" re-fetches the same path

#### Scenario: Empty directory -- "无子目录" text

WHEN the API returns an empty `directories` array
THEN the component displays "无子目录" text
AND the "选择此目录" button remains available (the user can still select the empty directory)

---

### Requirement: Breadcrumb Navigation

The component SHALL display a breadcrumb bar at the top showing the current path hierarchy. Each segment of the breadcrumb is tappable to navigate back to that ancestor directory.

#### Scenario: Breadcrumb tap -- navigate to ancestor

WHEN the current path is `/Users/danker/Desktop/AI-vault/IMbot/packages/relay`
AND the breadcrumb shows: `AI-vault / IMbot / packages / relay`
AND the user taps "IMbot" in the breadcrumb
THEN the component fetches and displays the contents of `/Users/danker/Desktop/AI-vault/IMbot`
AND the breadcrumb updates to `AI-vault / IMbot`

#### Scenario: Deep nesting -- breadcrumb scrollable

WHEN the path has 6+ segments
THEN the breadcrumb bar is horizontally scrollable
AND the rightmost (deepest) segment is visible by default

---

### Requirement: Directory Entry Navigation

Tapping a directory entry SHALL navigate into that directory, fetching its contents and updating the breadcrumb.

#### Scenario: Tap directory -- enter subdirectory

WHEN the user taps the "packages" directory entry
THEN `GET /v1/hosts/:id/browse?path=<packages_full_path>` is called
AND the subdirectories of "packages" are displayed
AND the breadcrumb appends "packages"

---

### Requirement: Loading State

While fetching directory contents, the component SHALL display a loading shimmer animation.

#### Scenario: Loading shimmer shown during fetch

WHEN a directory listing API call is in progress
THEN shimmer placeholder rows are displayed
AND the shimmer is replaced by actual entries when the response arrives

---

### Requirement: Select Current Directory

A "选择此目录" button SHALL be available. Tapping it invokes the `onSelect(currentPath)` callback with the current directory path.

#### Scenario: Select button returns current path

WHEN the user is browsing `/Users/danker/Desktop/AI-vault/IMbot`
AND taps "选择此目录"
THEN `onSelect("/Users/danker/Desktop/AI-vault/IMbot")` is called
