## ADDED Requirements

### Requirement: Stream-json bidirectional process spawning

Companion SHALL spawn Claude CLI with `-p --input-format stream-json --output-format stream-json --verbose --permission-mode <mode>`. The process SHALL remain alive across multiple turns, accepting user messages via stdin and emitting events via stdout.

#### Scenario: Process spawned in stream-json mode
- **WHEN** companion receives a `create_session` command
- **THEN** companion spawns `claude -p --input-format stream-json --output-format stream-json --verbose --permission-mode <mode> [--model <model>]`
- **AND** stdio is configured as `["pipe", "pipe", "pipe"]`

#### Scenario: First prompt delivered via stdin as JSON
- **WHEN** companion receives the `{ type: "system", subtype: "init", session_id: "..." }` event from stdout
- **THEN** companion captures the `session_id` as `provider_session_id`
- **AND** writes the first prompt to stdin as `{"type":"user","message":{"role":"user","content":"<prompt>"}}\n`

#### Scenario: Process stays alive after response
- **WHEN** Claude finishes responding (emits `{ type: "result", subtype: "success" }`)
- **AND** the child process exit code is null (still alive)
- **THEN** companion emits `session_idle` event to relay
- **AND** relay transitions session to `idle`

#### Scenario: Subsequent messages via stdin
- **WHEN** session is in `idle` status and companion receives `send_message`
- **THEN** companion writes `{"type":"user","message":{"role":"user","content":"<text>"}}\n` to the existing process stdin
- **AND** relay transitions session to `running` on dispatch
- **AND** process streams response events as usual

### Requirement: Idle timeout auto-completion

Companion SHALL enforce an idle timeout on persistent sessions to prevent process leaks.

#### Scenario: Idle timeout triggers auto-complete
- **WHEN** session has been in `idle` state beyond `idle_timeout_ms` (default 1800000ms / 30min)
- **THEN** companion sends SIGTERM to the process
- **AND** waits up to `kill_grace_ms` for exit
- **AND** emits `session_result` event
- **AND** relay transitions session to `completed`

#### Scenario: Idle timeout reset on interaction
- **WHEN** session is `idle` and `send_message` arrives before timeout
- **THEN** idle timer resets
- **AND** session transitions to `running`

#### Scenario: Configurable idle timeout
- **WHEN** companion config contains `idle_timeout_ms: 3600000`
- **THEN** companion uses 60min as idle timeout

### Requirement: Process health monitoring

Companion SHALL detect unexpected process exits and report them.

#### Scenario: Unexpected exit during idle
- **WHEN** process exits unexpectedly while session is `idle` (non-zero code or signal)
- **THEN** companion emits `session_error` with `error_code: "process_crash"` and stderr tail

#### Scenario: Unexpected exit during running
- **WHEN** process exits unexpectedly while session is `running`
- **THEN** companion emits `session_error` (same as current behavior)

#### Scenario: Clean exit without explicit complete
- **WHEN** process exits with code 0 while session is `idle` (Claude decided to exit)
- **THEN** companion emits `session_result`
- **AND** relay transitions session to `completed`

### Requirement: Complete session command

Companion SHALL support `complete_session` to gracefully terminate a persistent session's process.

#### Scenario: Complete an idle session
- **WHEN** companion receives `complete_session` for a session in `idle` state
- **THEN** companion sends SIGTERM to the process
- **AND** waits up to `kill_grace_ms` for exit
- **AND** emits `session_result`
- **AND** relay transitions session to `completed`

#### Scenario: Complete a running session
- **WHEN** companion receives `complete_session` for a session in `running` state
- **THEN** companion sends SIGTERM, waits, SIGKILL if needed
- **AND** emits `session_result`

#### Scenario: Complete a non-existent session
- **WHEN** companion receives `complete_session` for an unknown session
- **THEN** companion returns ack error with `error_code: "session_not_found"`

### Requirement: Companion restart recovery

When companion restarts, all active processes are lost.

#### Scenario: Companion restarts with active sessions
- **WHEN** companion starts and sessionIndex contains entries that were `idle` or `running`
- **THEN** for each, companion emits `session_error` with `error_code: "companion_restart"`
- **AND** relay transitions those sessions to `failed`
- **AND** user can resume from Android (resume spawns a fresh persistent process)

### Requirement: Resume spawns persistent process

When a failed/completed session is resumed, the new process SHALL use the same persistent stream-json protocol.

#### Scenario: Resume a failed session
- **WHEN** relay sends `resume_session` for a previously persistent session
- **THEN** companion spawns `claude -p --input-format stream-json --output-format stream-json --verbose -r <provider_session_id>`
- **AND** process stays alive after responding
- **AND** session follows the same idle lifecycle
