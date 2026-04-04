## ADDED Requirements

### Requirement: Full-scan session discovery without cwd filtering

Companion SHALL expose a `discoverAllSessions(provider, options)` function that scans all project directories under `~/.claude/projects/` without filtering by cwd.

#### Scenario: Discover all sessions across all project directories
- **WHEN** `discoverAllSessions("book", { claudeProjectsDir })` is called
- **THEN** companion scans every subdirectory of `claudeProjectsDir`
- **AND** returns `.jsonl` files from all project directories, not just cwd-matching ones
- **AND** for each `.jsonl` file returns `{ provider_session_id, cwd, created_at, status }`

#### Scenario: Project directory cwd recovery via decode
- **WHEN** a project directory name is `-Users-danker-Desktop-AI-vault-IMbot`
- **THEN** `decodeProjectDirectory()` recovers cwd as `/Users/danker/Desktop/AI-vault/IMbot`
- **AND** the recovered cwd is used as `LocalSessionInfo.cwd`

#### Scenario: Undecodable project directory name
- **WHEN** a project directory name cannot be decoded to a valid path
- **THEN** companion uses the raw decoded path as a best-effort cwd
- **AND** logs a warning but does NOT skip the directory

#### Scenario: Results sorted by modification time
- **WHEN** multiple sessions are discovered
- **THEN** results are sorted by mtime descending (newest first)
- **AND** truncated to `options.limit` (default 200)

#### Scenario: Empty or missing projects directory
- **WHEN** `claudeProjectsDir` does not exist or is empty
- **THEN** returns an empty array without throwing

#### Scenario: Non-directory entries are skipped
- **WHEN** `claudeProjectsDir` contains files (not directories)
- **THEN** those entries are silently skipped

#### Scenario: Session file with zero size
- **WHEN** a `.jsonl` file has size 0
- **THEN** its status is `"unknown"`

#### Scenario: Unreadable session file
- **WHEN** a `.jsonl` file cannot be read (permission error)
- **THEN** it is skipped with a warning log

## MODIFIED Requirements

### Requirement: list_sessions command supports full-scan mode

The `list_sessions` companion command SHALL support an optional `cwd` parameter. When omitted or set to `"*"`, it uses full-scan mode.

#### Scenario: list_sessions with no cwd
- **WHEN** relay sends `list_sessions` with `cwd` undefined or empty
- **THEN** companion calls `discoverAllSessions(provider, options)`
- **AND** returns all sessions across all project directories

#### Scenario: list_sessions with wildcard cwd
- **WHEN** relay sends `list_sessions` with `cwd: "*"`
- **THEN** companion calls `discoverAllSessions(provider, options)`

#### Scenario: list_sessions with specific cwd (existing behavior preserved)
- **WHEN** relay sends `list_sessions` with `cwd: "/Users/danker/Desktop/AI-vault/IMbot"`
- **THEN** companion calls `discoverSessions(cwd, provider, options)` as before
- **AND** only returns sessions matching that cwd or its children

### Requirement: Wire protocol ListSessionsCommand cwd is optional

#### Scenario: ListSessionsCommand without cwd
- **WHEN** a `list_sessions` command is constructed without `cwd`
- **THEN** TypeScript compiles successfully (`cwd` is `string | undefined`)
- **AND** no wire validation error occurs
