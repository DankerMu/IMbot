# Capability: companion-list-sessions

## ADDED Requirements

### Requirement: List Sessions Command Handler

The companion SHALL handle the `list_sessions` command by reading local session history from the `~/.claude/projects/` directory structure. The handler MUST return a list of session records for sessions whose `cwd` matches the requested directory. Each record SHALL contain `provider_session_id`, `cwd`, `created_at`, and `status`. Results MUST be sorted by `created_at` descending (most recent first).

#### Scenario: list sessions for directory with existing sessions

WHEN the companion receives `{ cmd: "list_sessions", req_id: "r1", cwd: "/Users/danker/Desktop/AI-vault/IMbot", provider: "claude" }`
AND `~/.claude/projects/` contains 3 sessions for that cwd
THEN the companion responds with `{ type: "ack", req_id: "r1", status: "ok", data: { sessions: [...] } }`
AND `sessions` contains exactly 3 entries
AND each entry has `provider_session_id`, `cwd`, `created_at`, `status` fields
AND entries are sorted by `created_at` descending

#### Scenario: list sessions for directory with no sessions

WHEN the companion receives `{ cmd: "list_sessions", req_id: "r2", cwd: "/Users/danker/Desktop/empty-project", provider: "claude" }`
AND `~/.claude/projects/` contains no sessions for that cwd
THEN the companion responds with `{ type: "ack", req_id: "r2", status: "ok", data: { sessions: [] } }`

#### Scenario: list sessions for non-existent directory

WHEN the companion receives `{ cmd: "list_sessions", req_id: "r3", cwd: "/nonexistent/path", provider: "claude" }`
AND `/nonexistent/path` does not exist on the local filesystem
THEN the companion responds with `{ type: "ack", req_id: "r3", status: "error", error_code: "directory_not_found", message: "Directory does not exist: /nonexistent/path" }`

#### Scenario: sessions sorted by created_at descending

WHEN the companion receives a `list_sessions` command for a directory with sessions created at T1, T2, T3 (T3 being most recent)
THEN the response `sessions` array has the T3 session first, then T2, then T1

#### Scenario: filter by provider

WHEN the companion receives `{ cmd: "list_sessions", req_id: "r4", cwd: "/Users/danker/Desktop/AI-vault", provider: "book" }`
AND sessions exist for both `claude` and `book` providers in that cwd
THEN only sessions matching `provider: "book"` are returned
AND no `claude` sessions appear in the result

---

### Requirement: Session History Discovery

The companion SHALL discover session history by traversing the `~/.claude/projects/` directory tree. The directory structure encodes the project path in directory names. The companion MUST parse session metadata files (JSON) within each project directory to extract session information.

#### Scenario: parse session metadata from project directory

WHEN `~/.claude/projects/` contains a project directory for `/Users/danker/Desktop/AI-vault/IMbot`
AND that directory contains session metadata files with session IDs and timestamps
THEN the companion extracts `provider_session_id` from each metadata entry
AND derives `created_at` from the file metadata or content timestamps
AND sets `status` based on the most recent session state

#### Scenario: corrupted session metadata file

WHEN a session metadata file contains invalid JSON
THEN the companion skips that session entry
AND logs a warning with the file path
AND continues processing remaining sessions without error

#### Scenario: empty projects directory

WHEN `~/.claude/projects/` directory exists but is empty
THEN `list_sessions` returns an empty sessions array for any cwd

#### Scenario: projects directory does not exist

WHEN `~/.claude/projects/` directory does not exist
THEN `list_sessions` returns an empty sessions array for any cwd
AND does not return an error (absence of history is not an error condition)
