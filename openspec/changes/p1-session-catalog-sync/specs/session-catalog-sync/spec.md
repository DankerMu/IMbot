## ADDED Requirements

### Requirement: Companion periodically reconciles local session catalog while connected

Companion SHALL continue to run local session reconciliation after the initial relay connection, so native CLI sessions created later can still be discovered.

#### Scenario: New local session appears after relay connection
- **GIVEN** companion is already connected to relay
- **AND** a new native `book` session JSONL file is created locally after the connection is established
- **WHEN** the next reconcile interval fires
- **THEN** companion SHALL discover that session
- **AND** send `report_local_sessions` to relay without requiring a reconnect

### Requirement: Known local sessions refresh relay freshness when local activity advances

If a locally discoverable session already exists in the session index, but its local `last_active_at` has advanced, companion SHALL report it again so relay can refresh the catalog ordering.

#### Scenario: Existing local session becomes active again
- **GIVEN** relay already knows `provider_session_id = "sess-123"`
- **AND** session index records an older `last_observed_at`
- **WHEN** the local transcript file mtime advances within the recent-activity window
- **THEN** companion SHALL include that session in the next `report_local_sessions`
- **AND** update the session index `last_observed_at`

### Requirement: Relay updates session catalog freshness from local sync reports

Relay SHALL treat `report_local_sessions` as a catalog freshness signal, not only as initial shadow-record creation.

#### Scenario: Existing session receives a fresher local activity timestamp
- **GIVEN** relay already has a session with `provider_session_id = "sess-123"`
- **AND** a new `report_local_sessions` item arrives with a newer `last_active_at`
- **WHEN** relay processes that item
- **THEN** relay SHALL update `sessions.last_active_at`
- **AND** keep the existing relay session id unchanged

### Requirement: Session list API sorts by recent activity

`GET /sessions` SHALL return sessions ordered by `last_active_at DESC`, with `created_at DESC` as a stable tiebreaker.

#### Scenario: Older session becomes active again
- **GIVEN** session A was created yesterday and session B was created last month
- **AND** session B gets new local activity now
- **WHEN** client requests `GET /sessions`
- **THEN** session B SHALL appear ahead of session A if its `last_active_at` is newer

### Requirement: Relay broadcasts session catalog change hints to Android clients

Relay SHALL broadcast a lightweight list-refresh hint whenever local session reconciliation creates or refreshes session catalog entries.

#### Scenario: Local sync changes the catalog
- **GIVEN** `handleReportLocalSessions()` creates or refreshes at least one session row
- **WHEN** the transaction completes successfully
- **THEN** relay SHALL broadcast `{ type: "sessions_changed", reason: "local_sync", host_id: "<host>" }` to connected Android clients

### Requirement: Android Home auto-refreshes on session catalog change hints

Android Home SHALL refresh the session list when it receives a `sessions_changed` message.

#### Scenario: Home receives local sync hint
- **GIVEN** Home screen is active and connected to relay
- **WHEN** relay sends `{ type: "sessions_changed", reason: "local_sync" }`
- **THEN** Home SHALL trigger one session-list refresh
- **AND** newly created or newly active local sessions SHALL appear without manual pull-to-refresh
