## MODIFIED Requirements

### Requirement: Reconciler uses full-scan instead of per-root cwd-filtered discovery

SessionReconciler SHALL use `discoverAllSessions()` for each provider instead of calling `discoverSessions(root.path, ...)` per workspace root. This ensures sessions created from ANY cwd are discovered regardless of workspace root configuration.

#### Scenario: Reconciler discovers sessions outside configured workspace roots
- **GIVEN** workspace roots are configured as `[{ path: "/Users/danker/projects", provider: "book" }]`
- **AND** a session was created from Android with `cwd: "/Users/danker/Desktop/AI-vault/IMbot"` (outside configured root)
- **WHEN** reconciler runs `doReconcile()`
- **THEN** it calls `discoverAllSessions("book", { claudeProjectsDir })` once
- **AND** discovers the session created from `/Users/danker/Desktop/AI-vault/IMbot`
- **AND** reports it to relay via `report_local_sessions`

#### Scenario: Reconciler deduplicates per provider
- **GIVEN** two workspace roots for the same provider: `[{ path: "/a", provider: "book" }, { path: "/b", provider: "book" }]`
- **WHEN** reconciler runs `doReconcile()`
- **THEN** it calls `discoverAllSessions("book", ...)` exactly ONCE (not twice)

#### Scenario: Reconciler handles multiple providers
- **GIVEN** roots for two providers: `[{ path: "/a", provider: "book" }, { path: "/b", provider: "claude" }]`
- **WHEN** reconciler runs `doReconcile()`
- **THEN** it calls `discoverAllSessions("book", ...)` once AND `discoverAllSessions("claude", ...)` once

#### Scenario: Reconciler skips sessions already in index
- **GIVEN** session with `provider_session_id: "abc-123"` is already in the session index
- **WHEN** reconciler discovers this session via full scan
- **THEN** it is skipped (not reported again)
- **AND** the skip counter increments

#### Scenario: Reconciler skips non-completed sessions
- **GIVEN** a discovered session has `status: "unknown"` (zero-size JSONL)
- **WHEN** reconciler processes discovered sessions
- **THEN** it is skipped (only `status: "completed"` sessions are reported)

#### Scenario: Reconciler reports new sessions to relay
- **GIVEN** a discovered session is NOT in the session index
- **AND** its status is `"completed"`
- **WHEN** reconciler processes it
- **THEN** it sends `report_local_sessions` message to relay with `{ provider_session_id, provider, cwd, created_at }`
- **AND** adds it to the session index with `source: "local"`

#### Scenario: Reconciler triggers on companion connect
- **WHEN** companion establishes WS connection to relay
- **THEN** reconciler `reconcile()` is invoked
- **AND** newly discovered sessions are reported

#### Scenario: Reconciler is idempotent under concurrent calls
- **WHEN** `reconcile()` is called while another reconcile is running
- **THEN** the second call returns `{ reported: 0, skipped: 0 }` immediately
- **AND** does not interfere with the running reconciliation

## UNCHANGED Requirements

### Requirement: Existing discoverSessions behavior is preserved

`discoverSessions(cwd, provider, options)` SHALL continue to filter by cwd as before. It is still used by `list_sessions` with a specific cwd parameter.

#### Scenario: discoverSessions only returns cwd-matching sessions
- **GIVEN** projects directory contains sessions for `/a` and `/b`
- **WHEN** `discoverSessions("/a", "book", options)` is called
- **THEN** only sessions under `/a` are returned
- **AND** sessions under `/b` are NOT returned
