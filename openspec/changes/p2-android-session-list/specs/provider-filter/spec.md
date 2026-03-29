# Capability: provider-filter

A dropdown filter in the session list TopAppBar that allows filtering sessions by provider. Applied locally without API re-fetch. Persisted across app restarts.

## ADDED Requirements

### Requirement: Filter Dropdown Options

The filter dropdown in `TopAppBar` SHALL provide the options: All, Claude Code, book, OpenClaw. "All" shows all sessions regardless of provider.

#### Scenario: Default filter is All

WHEN the app launches for the first time (no saved preference)
THEN the filter is set to "All"
AND all sessions are displayed regardless of provider

#### Scenario: Select Claude -- only Claude sessions shown

WHEN the user selects "Claude Code" from the filter dropdown
THEN only sessions with `provider == CLAUDE` are shown in the list
AND the dropdown label updates to "Claude Code"

#### Scenario: Select book -- only book sessions shown

WHEN the user selects "book" from the filter dropdown
THEN only sessions with `provider == BOOK` are shown

#### Scenario: Select OpenClaw -- only OpenClaw sessions shown

WHEN the user selects "OpenClaw" from the filter dropdown
THEN only sessions with `provider == OPENCLAW` are shown

---

### Requirement: Filter Persistence via SharedPreferences

The selected filter SHALL be persisted to `SharedPreferences` so that it survives app restarts.

#### Scenario: Restart app -- filter persisted

WHEN the user selects "Claude Code" filter, kills the app, and relaunches
THEN the filter is restored to "Claude Code" on relaunch
AND only Claude sessions are shown initially

---

### Requirement: Filter with Zero Results

When the active filter matches no sessions, the empty state SHALL be displayed.

#### Scenario: Filter with 0 results -- empty state

WHEN the user selects "OpenClaw" filter
AND there are no sessions with `provider == OPENCLAW`
THEN the empty state is shown with text "暂无会话"
AND the "新建会话" button is available
