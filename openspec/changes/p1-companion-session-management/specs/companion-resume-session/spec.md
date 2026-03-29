# Capability: companion-resume-session

## ADDED Requirements

### Requirement: Resume Session Command Handler

The companion SHALL handle the `resume_session` command by spawning the appropriate CLI binary with `--resume --session-id <provider_session_id> --output-format stream-json` in the session's original `cwd`. The handler MUST parse and forward streaming JSON events to the relay identically to `create_session`. The spawned process SHALL be tracked in the running process map.

#### Scenario: resume a valid completed session

WHEN the companion receives `{ cmd: "resume_session", req_id: "r1", session_id: "relay-sess-1", provider_session_id: "prov-sess-1", cwd: "/Users/danker/Desktop/AI-vault/IMbot" }`
AND `provider_session_id` "prov-sess-1" exists in local CLI history
THEN the companion spawns `claude --resume --session-id prov-sess-1 --output-format stream-json` with cwd set to `/Users/danker/Desktop/AI-vault/IMbot`
AND responds with `{ type: "ack", req_id: "r1", status: "ok", data: { provider_session_id: "prov-sess-1" } }`
AND streaming JSON events from the CLI stdout are forwarded to relay with `session_id: "relay-sess-1"`

#### Scenario: resume with non-existent session-id

WHEN the companion receives a `resume_session` command with `provider_session_id: "nonexistent-id"`
AND the CLI binary exits with an error indicating the session does not exist
THEN the companion responds with `{ type: "ack", req_id: "r2", status: "error", error_code: "session_not_resumable", message: "CLI could not find session: nonexistent-id" }`

#### Scenario: resume in wrong cwd

WHEN the companion receives a `resume_session` command with `cwd: "/wrong/directory"`
AND the directory does not exist on the local filesystem
THEN the companion responds with `{ type: "ack", req_id: "r3", status: "error", error_code: "directory_not_found", message: "Directory does not exist: /wrong/directory" }`
AND no CLI process is spawned

#### Scenario: resume book session uses book binary

WHEN the companion receives `{ cmd: "resume_session", req_id: "r4", session_id: "relay-sess-2", provider_session_id: "book-sess-1", cwd: "/Users/danker/Desktop/novel/project-1" }`
AND the relay command context indicates `provider: "book"`
THEN the companion spawns the book binary (configured binary name, default `book`) instead of `claude`
AND all other behavior (event parsing, forwarding) is identical

#### Scenario: events from resumed session carry correct session_id

WHEN a session is resumed with `session_id: "relay-sess-5"` and `provider_session_id: "prov-sess-5"`
AND the CLI process emits `assistant_delta`, `tool_call_started`, `session_result` events
THEN each forwarded event message has `session_id: "relay-sess-5"` (the relay session ID, not the provider session ID)

---

### Requirement: Resume Process Lifecycle

The resumed CLI process SHALL follow the same lifecycle management as created sessions: stdout line-by-line parsing, stderr logging, exit code detection, and automatic cleanup on process termination.

#### Scenario: resumed session completes normally

WHEN a resumed CLI process exits with code 0
THEN the companion emits a `session_result` event to the relay
AND removes the process from the running process map

#### Scenario: resumed session exits with error

WHEN a resumed CLI process exits with a non-zero exit code
THEN the companion emits a `session_error` event with the exit code and stderr content
AND removes the process from the running process map

#### Scenario: resumed session process is tracked

WHEN a session is resumed successfully
THEN the process is added to the running process map keyed by `session_id`
AND `send_message` and `cancel_session` commands can target this process
