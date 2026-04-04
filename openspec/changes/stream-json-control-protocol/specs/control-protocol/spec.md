## ADDED Requirements

### Requirement: Companion spawns Claude Code with bidirectional control protocol

The companion `ClaudeRuntimeAdapter` SHALL spawn Claude Code child processes with `--input-format stream-json --output-format stream-json --permission-prompt-tool stdio` flags. The `-p` (print) flag SHALL NOT be used.

The `--permission-prompt-tool stdio` flag enables Claude Code to send `control_request` JSON messages on stdout and block until a `control_response` JSON message is received on stdin.

#### Scenario: createSession spawn arguments

- **WHEN** `ClaudeRuntimeAdapter.createSession()` is called with provider "claude" and permission_mode "bypassPermissions"
- **THEN** the child process SHALL be spawned with args `["--input-format", "stream-json", "--output-format", "stream-json", "--verbose", "--permission-prompt-tool", "stdio", "--permission-mode", "bypassPermissions"]`
- **AND** the `-p` flag SHALL NOT be present in the args array

#### Scenario: resumeSession spawn arguments

- **WHEN** `ClaudeRuntimeAdapter.resumeSession()` is called with a known provider_session_id
- **THEN** the child process SHALL be spawned with `--permission-prompt-tool stdio` in addition to `--resume <id>` and stream-json flags
- **AND** the `-p` flag SHALL NOT be present

#### Scenario: Initial prompt delivered via stdin

- **WHEN** a session is created with an initial prompt
- **THEN** the prompt SHALL be sent via `writeUserMessage()` to the child's stdin (existing behavior)
- **AND** the prompt SHALL NOT be passed as a CLI argument

---

### Requirement: Companion routes stdout messages through control protocol dispatcher

The `handleStdoutLine` method SHALL parse each JSON line from stdout and dispatch based on the `type` field:
- `"control_request"` → `handleControlRequest()`
- `"control_cancel_request"` → `handleControlCancelRequest()`
- `"control_response"` → route to pending response handler
- All other types → existing `eventMapper.map()` pipeline

#### Scenario: control_request message dispatched before event mapper

- **WHEN** stdout emits `{"type":"control_request","request_id":"req-1","request":{"subtype":"can_use_tool","tool_name":"Bash","input":{"command":"ls"}}}`
- **THEN** the message SHALL be dispatched to `handleControlRequest()`
- **AND** the message SHALL NOT be passed to `eventMapper.map()`

#### Scenario: Regular assistant message processed normally

- **WHEN** stdout emits `{"type":"assistant","message":{"role":"assistant","content":[{"type":"text","text":"Hello"}]}}`
- **THEN** the message SHALL be passed to `eventMapper.map()` as before
- **AND** `handleControlRequest()` SHALL NOT be called

#### Scenario: control_cancel_request dispatched

- **WHEN** stdout emits `{"type":"control_cancel_request","request_id":"req-1"}`
- **THEN** the message SHALL be dispatched to `handleControlCancelRequest()`
- **AND** any pending control response for request_id "req-1" SHALL be rejected

---

### Requirement: Non-interactive tool control_requests auto-approved

When a `control_request` with `request.subtype === "can_use_tool"` arrives and the `tool_name` is NOT "AskUserQuestion", the companion SHALL immediately write a `control_response` to stdin approving the tool call.

#### Scenario: Bash tool auto-approved

- **WHEN** a control_request arrives with `request.tool_name === "Bash"` and `request.input === {"command":"ls"}`
- **THEN** the companion SHALL write to stdin: `{"type":"control_response","response":{"subtype":"success","request_id":"<matching_id>","response":{"behavior":"allow","updatedInput":{"command":"ls"}}}}`
- **AND** the `updatedInput` SHALL be the original `request.input` passed through unchanged
- **AND** the response SHALL be written within 10ms (no human interaction)

#### Scenario: Read tool auto-approved

- **WHEN** a control_request arrives with `request.tool_name === "Read"`
- **THEN** the companion SHALL write a `control_response` with `behavior: "allow"` to stdin

#### Scenario: Unknown tool subtype rejected

- **WHEN** a control_request arrives with `request.subtype !== "can_use_tool"`
- **THEN** the companion SHALL write a `control_response` with `subtype: "error"` and a descriptive error message

---

### Requirement: AskUserQuestion control_request blocks until Android user responds

When a `control_request` with `request.tool_name === "AskUserQuestion"` arrives, the companion SHALL:
1. Emit a `tool_call_started` event to the relay with the question content
2. Register a pending control response callback on the session
3. NOT write any `control_response` to stdin (Claude Code blocks here)
4. Wait until the Android user's answer arrives via the `answer_interactive_tool` command

#### Scenario: AskUserQuestion emits tool_call_started and blocks

- **WHEN** a control_request arrives with `request.tool_name === "AskUserQuestion"` and `request.input === {"questions":[{"question":"Pick one","options":[{"label":"A"},{"label":"B"}]}]}`
- **THEN** the companion SHALL emit to relay: `{ type: "event", session_id: "<id>", event_type: "tool_call_started", payload: { call_id: "<request_id>", tool: "AskUserQuestion", input: <request.input> } }`
- **AND** the companion SHALL NOT write a `control_response` to stdin
- **AND** the session SHALL have a `pendingControlResponse` registered with the request_id and the original `request.input`

#### Scenario: Android answer resolves the pending control response

- **WHEN** a pending AskUserQuestion control_request exists with request_id "req-ask-1" and original input `{"questions":[{"question":"Pick one","options":[{"label":"A"},{"label":"B"}]}]}`
- **AND** the companion receives an `answer_interactive_tool` command with `call_id === "req-ask-1"` and `answer === "A"` and `question_index === 0`
- **THEN** the companion SHALL write to stdin: `{"type":"control_response","response":{"subtype":"success","request_id":"req-ask-1","response":{"behavior":"allow","updatedInput":{"questions":[{"question":"Pick one","options":[{"label":"A"},{"label":"B"}]}],"answers":{"0":"A"}}}}}`
- **AND** the `updatedInput.questions` SHALL be the original `request.input.questions` passed through unchanged
- **AND** the `updatedInput.answers` SHALL map the question index (as string key) to the selected option label
- **AND** the `pendingControlResponse` SHALL be removed from the session
- **AND** Claude Code SHALL resume execution — `AskUserQuestion.call()` receives `{questions, answers}` and returns `{data: {questions, answers}}` without terminal interaction

#### Scenario: AskUserQuestion emits tool_call_completed after resolution

- **WHEN** Claude Code resumes after receiving the control_response for AskUserQuestion
- **THEN** Claude Code SHALL emit a `tool_result` message on stdout
- **AND** the event-mapper SHALL map it to a `tool_call_completed` event with the user's answer as result
- **AND** the Android InteractiveToolCard SHALL transition to "已提交回答" state

#### Scenario: AskUserQuestion with multiSelect question

- **WHEN** a control_request arrives with `request.input === {"questions":[{"question":"Pick several","options":[{"label":"X"},{"label":"Y"},{"label":"Z"}],"multiSelect":true}]}`
- **AND** the user selects "X" and "Z"
- **THEN** the `updatedInput.answers` SHALL be `{"0":"X, Z"}` (comma-separated labels)

#### Scenario: AskUserQuestion with user custom input (Other)

- **WHEN** the user types a free-form answer instead of selecting a predefined option
- **THEN** the `updatedInput.answers` SHALL contain the user's raw text as the value, e.g. `{"0":"Something custom"}`

#### Scenario: Timeout triggers error control_response

- **WHEN** a pending AskUserQuestion control_request exists
- **AND** the session's idle timeout (default 30 minutes) elapses without an answer
- **THEN** the companion SHALL write to stdin: `{"type":"control_response","response":{"subtype":"error","request_id":"<id>","error":"Interactive tool answer timeout"}}`
- **AND** the `pendingControlResponse` SHALL be removed
- **AND** Claude Code SHALL resume with the error, and the model will handle the timeout gracefully

#### Scenario: control_cancel_request clears pending AskUserQuestion

- **WHEN** a pending AskUserQuestion control_request exists with request_id "req-ask-1"
- **AND** stdout emits `{"type":"control_cancel_request","request_id":"req-ask-1"}`
- **THEN** the companion SHALL remove the `pendingControlResponse`
- **AND** the companion SHALL emit a `tool_call_completed` event with a cancellation indication
- **AND** the Android InteractiveToolCard SHALL transition to expired state

---

### Requirement: Session cleanup cancels pending control responses

When a session is cancelled, completed, or the child process exits, any pending control responses SHALL be cleaned up.

#### Scenario: Session cancel with pending AskUserQuestion

- **WHEN** a session has a pending AskUserQuestion control_request
- **AND** the relay sends a `cancel_session` command
- **THEN** the companion SHALL write an error `control_response` for the pending request
- **AND** then proceed with the normal cancel flow (SIGINT → SIGKILL)

#### Scenario: Child process exits with pending control response

- **WHEN** the Claude Code child process exits (code 0 or non-zero)
- **AND** a pending control response exists
- **THEN** the `pendingControlResponse` SHALL be rejected/cleaned up
- **AND** no `control_response` SHALL be written to stdin (process is gone)

#### Scenario: Relay disconnect with pending control response

- **WHEN** the companion loses connection to the relay
- **AND** a pending AskUserQuestion control_request exists
- **THEN** the companion SHALL write an error `control_response` to stdin with error "Relay disconnected"
- **AND** Claude Code SHALL resume and the model handles the error

---

### Requirement: Event-mapper removes -p mode workarounds

The `RuntimeEventMapper` SHALL remove the following state that was added to work around `-p` mode limitations:
- `interactiveToolActive` flag
- `suppressUserMessageCount` counter
- Interactive tool suppression in `emitToolCallCompleted()`

The `emittedTools` Map for deduplication SHALL be retained (verbose mode still sends duplicate messages regardless of `-p`).

#### Scenario: AskUserQuestion tool_result no longer suppressed

- **WHEN** event-mapper receives a `tool_result` for AskUserQuestion (from Claude Code after control_response)
- **THEN** it SHALL emit a `tool_call_completed` event normally (not return null)
- **AND** the payload SHALL contain the user's actual answer (not "Answer questions?")

#### Scenario: Assistant text after AskUserQuestion no longer suppressed

- **WHEN** event-mapper receives an `assistant` message after an AskUserQuestion tool call
- **THEN** it SHALL process the text content normally (not return null)
- **AND** the text SHALL be the model's genuine response to the user's answer

#### Scenario: Skill prompt suppression retained if still needed

- **WHEN** a Skill tool_use is followed by a user message containing the expanded skill prompt
- **THEN** the behavior SHALL match empirical testing of `--permission-prompt-tool stdio` mode
- **AND** if Skill prompts still appear as user messages, the `suppressUserMessageCount` logic SHALL be retained only for that case
