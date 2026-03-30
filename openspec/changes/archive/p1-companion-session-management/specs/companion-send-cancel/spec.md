# Capability: companion-send-cancel

## ADDED Requirements

### Requirement: Send Message to Running Session

The companion SHALL handle the `send_message` command by writing `text + newline` to the stdin of the running CLI process identified by `session_id`. The handler MUST verify the session is currently running before attempting the write.

#### Scenario: send message to a running session

WHEN the companion receives `{ cmd: "send_message", req_id: "r1", session_id: "sess-1", text: "refactor this module" }`
AND session "sess-1" has a running CLI process in the process map
THEN the companion writes `"refactor this module\n"` to the process stdin
AND responds with `{ type: "ack", req_id: "r1", status: "ok" }`
AND new turn events (assistant_delta, etc.) arrive from the CLI stdout

#### Scenario: send message to a non-running session

WHEN the companion receives `{ cmd: "send_message", req_id: "r2", session_id: "sess-2", text: "do something" }`
AND session "sess-2" is not in the running process map (no active process)
THEN the companion responds with `{ type: "ack", req_id: "r2", status: "error", error_code: "state_conflict", message: "Session sess-2 is not running" }`

#### Scenario: send message with empty text

WHEN the companion receives `{ cmd: "send_message", req_id: "r3", session_id: "sess-1", text: "" }`
THEN the companion responds with `{ type: "ack", req_id: "r3", status: "error", error_code: "invalid_request", message: "Message text must not be empty" }`

#### Scenario: send message after process has exited

WHEN the companion receives a `send_message` command for session "sess-3"
AND the CLI process for "sess-3" has exited but cleanup hasn't propagated yet
THEN the stdin write fails
AND the companion responds with an error ack indicating the session has terminated

---

### Requirement: Cancel Running Session

The companion SHALL handle the `cancel_session` command by sending SIGINT to the running CLI process identified by `session_id`. The handler MUST detect process exit after the signal and emit appropriate events.

#### Scenario: cancel a running session

WHEN the companion receives `{ cmd: "cancel_session", req_id: "r4", session_id: "sess-1" }`
AND session "sess-1" has a running CLI process
THEN the companion sends SIGINT to the process
AND responds with `{ type: "ack", req_id: "r4", status: "ok" }`
AND when the process exits, the companion emits a `session_status_changed` event with status `cancelled` (or `completed` if the CLI handles SIGINT gracefully)
AND removes the process from the running process map

#### Scenario: cancel an already finished session

WHEN the companion receives `{ cmd: "cancel_session", req_id: "r5", session_id: "sess-4" }`
AND session "sess-4" is not in the running process map
THEN the companion responds with `{ type: "ack", req_id: "r5", status: "error", error_code: "state_conflict", message: "Session sess-4 is not running" }`

#### Scenario: cancel with SIGINT timeout escalation

WHEN SIGINT is sent to a running process
AND the process does not exit within 10 seconds
THEN the companion sends SIGKILL to force termination
AND emits `session_error` with `error_code: "process_killed"`

---

### Requirement: Running Process Map

The companion SHALL maintain a `Map<session_id, ChildProcess>` that tracks all currently running CLI processes. Processes MUST be added on successful spawn (create or resume) and removed on process exit (any exit code or signal).

#### Scenario: multiple running sessions independently controllable

WHEN sessions "sess-A", "sess-B", and "sess-C" are all running with separate CLI processes
AND `send_message` is sent to "sess-B"
THEN only the process for "sess-B" receives the stdin write
AND processes for "sess-A" and "sess-C" are unaffected

#### Scenario: process exit triggers cleanup

WHEN a CLI process for "sess-X" exits (any reason)
THEN the process is removed from the running process map
AND subsequent `send_message` or `cancel_session` for "sess-X" returns an error ack
