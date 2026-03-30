# Capability: companion-workspace-sync

## ADDED Requirements

### Requirement: Add Workspace Root

The companion SHALL handle the `add_root` command by validating that the specified path exists on the local filesystem and persisting the root to the companion configuration file (`~/.imbot/companion.json`). The handler MUST reject paths that do not exist.

#### Scenario: add root with valid existing path

WHEN the companion receives `{ cmd: "add_root", req_id: "r1", provider: "claude", path: "/Users/danker/Desktop/AI-vault", label: "AI-vault" }`
AND `/Users/danker/Desktop/AI-vault` exists on the local filesystem and is a directory
THEN the companion adds the root to `~/.imbot/companion.json` under the workspace_roots array
AND responds with `{ type: "ack", req_id: "r1", status: "ok" }`

#### Scenario: add root with non-existent path

WHEN the companion receives `{ cmd: "add_root", req_id: "r2", provider: "claude", path: "/nonexistent/path", label: "ghost" }`
AND `/nonexistent/path` does not exist on the local filesystem
THEN the companion responds with `{ type: "ack", req_id: "r2", status: "error", error_code: "directory_not_found", message: "Path does not exist: /nonexistent/path" }`
AND the root is NOT persisted to the config file

#### Scenario: add root that is a file, not a directory

WHEN the companion receives an `add_root` command with a path pointing to a regular file
THEN the companion responds with an error ack with `error_code: "invalid_request"` and message indicating the path is not a directory

#### Scenario: add duplicate root

WHEN the companion receives an `add_root` command for a path + provider combination that already exists in the config
THEN the companion responds with `{ type: "ack", req_id: "r3", status: "ok" }` (idempotent, no duplicate entry created)

---

### Requirement: Remove Workspace Root

The companion SHALL handle the `remove_root` command by removing the specified root from the companion configuration file. Removal SHALL NOT affect any already-running sessions.

#### Scenario: remove existing root

WHEN the companion receives `{ cmd: "remove_root", req_id: "r4", provider: "claude", path: "/Users/danker/Desktop/old-project" }`
AND the root exists in `~/.imbot/companion.json`
THEN the root is removed from the config file
AND the companion responds with `{ type: "ack", req_id: "r4", status: "ok" }`

#### Scenario: remove non-existent root

WHEN the companion receives `{ cmd: "remove_root", req_id: "r5", provider: "claude", path: "/never/added" }`
AND the root does not exist in the config
THEN the companion responds with `{ type: "ack", req_id: "r5", status: "error", error_code: "not_found", message: "Root not found: /never/added" }`

---

### Requirement: Config Persistence

The companion SHALL persist workspace roots in `~/.imbot/companion.json`. The config file MUST survive companion restarts. The companion SHALL create the config file and parent directory if they do not exist on first write.

#### Scenario: roots persist across companion restart

WHEN workspace roots A and B are added
AND the companion process is stopped and restarted
THEN reading the config file on startup shows roots A and B still present

#### Scenario: config file created on first write

WHEN `~/.imbot/` directory does not exist
AND an `add_root` command is received
THEN the companion creates `~/.imbot/` directory and `companion.json` file
AND writes the root entry

#### Scenario: concurrent config writes are safe

WHEN two `add_root` commands arrive in rapid succession
THEN both roots are persisted without corruption
AND the config file is valid JSON after both writes complete

---

### Requirement: Config File Schema

The `~/.imbot/companion.json` file SHALL follow this structure:

```json
{
  "workspace_roots": [
    {
      "provider": "claude",
      "path": "/Users/danker/Desktop/AI-vault",
      "label": "AI-vault",
      "added_at": "2026-03-28T10:00:00Z"
    }
  ],
  "binaries": {
    "claude": "claude",
    "book": "book"
  }
}
```

#### Scenario: config file is valid JSON

WHEN the companion reads or writes the config file
THEN the file is always valid JSON conforming to the schema above
AND unknown fields are preserved (forward compatibility)
