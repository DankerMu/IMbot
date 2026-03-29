# Capability: companion-book-provider

## ADDED Requirements

### Requirement: Book Binary Selection

The companion SHALL use a configurable binary name for the book provider (default: `book`). The binary path is read from `~/.imbot/companion.json` under `binaries.book`. When spawning sessions for `provider: "book"`, the companion MUST use this binary instead of `claude`.

#### Scenario: create book session spawns book binary

WHEN the companion receives a `create_session` command with `provider: "book"`
AND `~/.imbot/companion.json` has `binaries.book` set to `"book"`
THEN the companion spawns `book --output-format stream-json -p "<prompt>"` (not `claude`)
AND all event parsing and forwarding works identically to claude sessions

#### Scenario: create book session with custom binary path

WHEN `~/.imbot/companion.json` has `binaries.book` set to `"/usr/local/bin/my-book"`
AND a `create_session` command arrives with `provider: "book"`
THEN the companion spawns `/usr/local/bin/my-book --output-format stream-json -p "<prompt>"`

#### Scenario: book binary not found

WHEN a `create_session` command arrives with `provider: "book"`
AND the configured book binary does not exist on the system
THEN the companion responds with `{ type: "ack", status: "error", error_code: "provider_unreachable", message: "Book binary not found: book" }`
AND no process is spawned

---

### Requirement: Book Workspace Directory Restriction

The book provider SHALL only accept workspace directories that fall under configured novel directories. The companion MUST reject session creation or resume for book provider if the `cwd` is not under a recognized novel root.

#### Scenario: create book session in novel directory

WHEN the companion receives a `create_session` command with `provider: "book"` and `cwd: "/Users/danker/Desktop/novel/project-1"`
AND `/Users/danker/Desktop/novel` is a configured workspace root for the book provider
THEN the session is created normally

#### Scenario: create book session in non-novel directory

WHEN the companion receives a `create_session` command with `provider: "book"` and `cwd: "/Users/danker/Desktop/AI-vault/IMbot"`
AND `/Users/danker/Desktop/AI-vault` is NOT a configured workspace root for the book provider
THEN the companion responds with `{ type: "ack", status: "error", error_code: "forbidden", message: "Book provider is restricted to novel directories" }`
AND no process is spawned

#### Scenario: resume book session enforces directory restriction

WHEN the companion receives a `resume_session` command with `provider: "book"` and `cwd: "/not/a/novel/dir"`
AND the cwd is not under any configured book workspace root
THEN the companion rejects with `error_code: "forbidden"`

---

### Requirement: Book Session History

The book provider MAY use a separate history path if the book binary stores sessions in a different location than Claude Code. The companion SHALL support configuring the session history base path per provider.

#### Scenario: list book sessions reads book-specific history

WHEN the companion receives `{ cmd: "list_sessions", provider: "book", cwd: "/Users/danker/Desktop/novel/project-1" }`
AND the book binary stores session history in a different path than `~/.claude/projects/`
THEN the companion reads from the book-specific history path
AND returns only book sessions

#### Scenario: list book sessions when history path is same as claude

WHEN the book binary uses the same `~/.claude/projects/` directory as Claude Code
THEN the companion uses the `provider` filter to distinguish book sessions from claude sessions
AND only returns sessions created by the book binary

---

### Requirement: Book Provider in Heartbeat

The companion heartbeat MUST include `"book"` in the `providers` array if and only if the book binary is available on the system.

#### Scenario: heartbeat includes book when binary exists

WHEN the companion starts and the book binary is found on the system PATH or at the configured path
THEN the heartbeat includes `providers: ["claude", "book"]`

#### Scenario: heartbeat excludes book when binary missing

WHEN the companion starts and the book binary is NOT found
THEN the heartbeat includes `providers: ["claude"]` only
AND the companion logs a warning about the missing book binary
