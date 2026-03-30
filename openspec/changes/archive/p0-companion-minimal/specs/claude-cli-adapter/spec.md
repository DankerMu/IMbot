# Capability: claude-cli-adapter

Spawn `claude` or `book` CLI processes with streaming JSON output, parse events from stdout, forward them to the relay, and support cancel and sendMessage operations on running sessions.

## ADDED Requirements

### Requirement: Spawn CLI Process for New Session

The adapter SHALL spawn the CLI binary (`claude` or `book`) as a child process using `child_process.spawn` with the following arguments:
- `--output-format stream-json`
- `--print-session-id`
- `-p "<prompt>"`
- `--permission-mode bypassPermissions`

The process SHALL be spawned with `cwd` set to the requested working directory. stdio SHALL be configured as `['pipe', 'pipe', 'pipe']` to enable stdin writing, stdout reading, and stderr capture. The binary path SHALL be configurable via `companion.json` (`providers.claude.binary` and `providers.book.binary`).

#### Scenario: Create session happy path (spawn, events flow, completion)

WHEN `createSession({ provider: "claude", cwd: "/Users/dev/project", prompt: "analyze this code", permission_mode: "bypassPermissions" })` is called
THEN the adapter spawns: `claude --output-format stream-json --print-session-id -p "analyze this code" --permission-mode bypassPermissions`
AND the process cwd is `/Users/dev/project`
AND stdout events are parsed and forwarded to relay as `{ type: "event", session_id, event_type, payload }`
AND when the CLI process exits with code 0, a `session_result` event is emitted
AND the adapter returns `{ providerSessionId: "<extracted-id>" }` via the initial ack

#### Scenario: Create with invalid cwd -- process error

WHEN `createSession` is called with `cwd: "/nonexistent/path"`
THEN the spawn fails or the CLI process exits immediately with an error
AND the adapter sends an error ack: `{ status: "error", error_code: "spawn_error", message: "..." }`

#### Scenario: Book provider uses book binary path from config

WHEN `createSession({ provider: "book", ... })` is called
THEN the adapter reads `providers.book.binary` from config (e.g., `/usr/local/bin/book`)
AND spawns the `book` binary with identical CLI flags as `claude`

### Requirement: Parse stream-json Events from stdout

The adapter SHALL read stdout of the CLI process line-by-line. Each line SHALL be parsed as JSON. Parsed events SHALL be mapped to the relay event model (`assistant_delta`, `assistant_message`, `tool_call_started`, `tool_call_completed`, `session_result`, `session_error`). Non-JSON lines SHALL be logged and skipped.

#### Scenario: Parse stream-json events correctly

WHEN the CLI outputs a line `{"type":"assistant","subtype":"text","text":"Hello"}`
THEN the adapter maps it to relay event_type `assistant_delta` with payload `{ text: "Hello" }`

WHEN the CLI outputs a line `{"type":"tool_use","tool":"Read","input":{...}}`
THEN the adapter maps it to relay event_type `tool_call_started` with the tool name and input in payload

WHEN the CLI outputs a line `{"type":"result","result":"..."}`
THEN the adapter maps it to relay event_type `session_result` with the result in payload

#### Scenario: Non-JSON line in stdout

WHEN the CLI outputs a line that is not valid JSON (e.g., a progress indicator or debug message)
THEN the line is logged at debug level
AND it is not forwarded to relay
AND parsing continues with subsequent lines

#### Scenario: Long-running session streams thousands of events

WHEN a session produces 5000+ stdout lines over 30 minutes
THEN all events are parsed and forwarded without memory leak
AND backpressure is handled (if relay send queue grows, stdout reading is not blocked)

### Requirement: Extract provider_session_id

The adapter SHALL extract the `provider_session_id` from the CLI's `--print-session-id` output. This ID appears in the stream-json output as a specific event type. The extracted ID SHALL be included in the initial ack to the relay and stored in the local session index.

#### Scenario: Extract session-id from output

WHEN the CLI outputs a session ID event (e.g., `{"type":"system","session_id":"sess-abc-def"}`)
THEN the adapter captures `sess-abc-def` as the `provider_session_id`
AND includes it in the ack data: `{ provider_session_id: "sess-abc-def" }`

### Requirement: Cancel Running Session via SIGINT

The adapter SHALL support cancellation of a running CLI process by sending `SIGINT` to the child process. After sending SIGINT, the adapter SHALL wait up to 5 seconds for the process to exit. If it does not exit, `SIGKILL` SHALL be sent.

#### Scenario: Cancel running session -- SIGINT -- process exits

WHEN `cancel(providerSessionId)` is called for a running session
THEN the adapter sends SIGINT to the CLI child process
AND the process exits (typically with code 130 or similar)
AND a `session_status_changed` event with `cancelled` status is emitted
AND the session is removed from the active sessions map

#### Scenario: Cancel unresponsive process -- SIGKILL fallback

WHEN SIGINT is sent but the process does not exit within 5 seconds
THEN the adapter sends SIGKILL
AND logs a warning about forced termination
AND the session is cleaned up

### Requirement: Send Message to Running Session via stdin

The adapter SHALL support sending a new message to a running CLI process by writing to its stdin pipe. The text SHALL be written followed by a newline character.

#### Scenario: Send message to stdin -- new turn begins

WHEN `sendMessage(providerSessionId, "now refactor the module")` is called for a running session
THEN the adapter writes `"now refactor the module\n"` to the CLI process's stdin
AND the CLI process begins a new turn
AND new stdout events flow as a result

#### Scenario: Send message to non-existent session

WHEN `sendMessage` is called with a `providerSessionId` that has no running process
THEN the adapter returns an error: `{ error_code: "session_not_found", message: "No running process for session <id>" }`

### Requirement: Process Crash Detection

The adapter SHALL detect when a CLI process exits unexpectedly (non-zero exit code, signal termination). Upon crash detection, the adapter SHALL emit a `session_error` event to the relay.

#### Scenario: Process crashes -- detect and report error event

WHEN the CLI process exits with a non-zero exit code (e.g., code 1)
THEN the adapter emits: `{ type: "event", session_id, event_type: "session_error", payload: { error_code: "process_crash", message: "CLI exited with code 1", stderr: "<last 500 chars of stderr>" } }`
AND the session is removed from the active sessions map

#### Scenario: Process killed by system (OOM, etc.)

WHEN the CLI process is killed by signal (e.g., SIGKILL from OOM killer)
THEN the adapter detects the signal exit
AND emits a `session_error` event with `error_code: "process_killed"` and the signal name

### Requirement: Local Session Index

The adapter SHALL maintain a local JSON file (`~/.imbot/sessions.json`) mapping `relay_session_id` to `{ provider_session_id, cwd, provider, created_at }`. This index persists across companion restarts and enables session resume.

#### Scenario: New session added to index

WHEN a new CLI session is successfully spawned
THEN an entry is written to `sessions.json` with all mapping fields

#### Scenario: Index survives companion restart

WHEN the companion process restarts
THEN the adapter reads `sessions.json` from disk
AND the session mappings are available for resume commands
