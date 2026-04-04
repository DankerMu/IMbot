## ADDED Requirements

### Requirement: Wire package defines AnswerInteractiveToolCommand type

The `@imbot/wire` package SHALL export an `AnswerInteractiveToolCommand` type and include it in the `CompanionCommand` union.

```typescript
type AnswerInteractiveToolCommand = {
  cmd: "answer_interactive_tool";
  req_id: string;
  session_id: string;
  call_id: string;
  answer: string;         // selected option label, or user free-form text
  question_index?: number; // defaults to 0 for single-question scenarios
};
```

#### Scenario: Type exported from wire package

- **WHEN** a consumer imports `AnswerInteractiveToolCommand` from `@imbot/wire`
- **THEN** the type SHALL be available and compatible with `CompanionCommand` union
- **AND** the `cmd` field SHALL be the literal `"answer_interactive_tool"`

#### Scenario: CompanionCommand union includes new type

- **WHEN** a function accepts `CompanionCommand`
- **THEN** it SHALL accept an `AnswerInteractiveToolCommand` value without type errors

---

### Requirement: Relay exposes POST /v1/sessions/:id/answer endpoint

The relay SHALL expose a new REST endpoint `POST /v1/sessions/:id/answer` that accepts an interactive tool answer from the Android client and forwards it to the companion.

#### Scenario: Successful answer submission

- **WHEN** Android sends `POST /v1/sessions/sess-1/answer` with body `{"call_id":"req-ask-1","answer":"Alpha","question_index":0}`
- **AND** the session "sess-1" exists and is in "running" status
- **AND** the companion host is online
- **THEN** the relay SHALL forward an `answer_interactive_tool` command to the companion: `{ cmd: "answer_interactive_tool", req_id: "<generated>", session_id: "sess-1", call_id: "req-ask-1", answer: "Alpha", question_index: 0 }`
- **AND** the response SHALL be `200 OK` with the companion's ack

#### Scenario: Answer for non-existent session

- **WHEN** Android sends `POST /v1/sessions/nonexistent/answer`
- **THEN** the relay SHALL respond with `404` and `{"error":"not_found"}`

#### Scenario: Answer for non-running session

- **WHEN** Android sends `POST /v1/sessions/sess-1/answer`
- **AND** session "sess-1" has status "idle" or "completed"
- **THEN** the relay SHALL respond with `409` and `{"error":"state_conflict"}`
- **AND** the reason SHALL indicate the session is not in a state that accepts tool answers

#### Scenario: Answer when companion is offline

- **WHEN** Android sends `POST /v1/sessions/sess-1/answer`
- **AND** the companion host for session "sess-1" is offline
- **THEN** the relay SHALL respond with `502` and `{"error":"host_offline"}`

#### Scenario: Missing required fields

- **WHEN** Android sends `POST /v1/sessions/sess-1/answer` with body `{}` (missing call_id and answer)
- **THEN** the relay SHALL respond with `400` and `{"error":"invalid_request"}`
- **AND** the schema SHALL require `call_id: string` and `answer: string`; `question_index: number` is optional (defaults to 0)

#### Scenario: Authentication required

- **WHEN** the request lacks a valid `Authorization: Bearer <token>` header
- **THEN** the relay SHALL respond with `401`

---

### Requirement: Relay orchestrator forwards answer to companion

The `SessionOrchestrator` SHALL have an `answerInteractiveTool(sessionId, callId, answer)` method that validates the session state and dispatches the command to the companion.

#### Scenario: Orchestrator dispatches to companion manager

- **WHEN** `orchestrator.answerInteractiveTool("sess-1", "req-ask-1", "Alpha")` is called
- **AND** session "sess-1" is in "running" status
- **THEN** the orchestrator SHALL call `companionManager.sendCommand(hostId, { cmd: "answer_interactive_tool", ... })`
- **AND** SHALL assert the ack is successful

#### Scenario: Orchestrator rejects non-running session

- **WHEN** `orchestrator.answerInteractiveTool("sess-1", ...)` is called
- **AND** session "sess-1" is in "idle" status
- **THEN** the orchestrator SHALL throw a `RelayError` with code `"state_conflict"`

---

### Requirement: Companion handles answer_interactive_tool command

The companion SHALL handle the `answer_interactive_tool` command by resolving the pending control response for the matching session and call_id.

#### Scenario: Answer resolves pending control response

- **WHEN** companion receives `{ cmd: "answer_interactive_tool", session_id: "sess-1", call_id: "req-ask-1", answer: "Alpha", question_index: 0 }`
- **AND** session "sess-1" has a pending control response with request_id "req-ask-1" and original input `{"questions":[{"question":"Pick one","options":[{"label":"Alpha"},{"label":"Beta"}]}]}`
- **THEN** the companion SHALL construct `updatedInput` by merging the original `questions` with `answers: { "0": "Alpha" }`
- **AND** the companion SHALL write to the child's stdin: `{"type":"control_response","response":{"subtype":"success","request_id":"req-ask-1","response":{"behavior":"allow","updatedInput":{"questions":[{"question":"Pick one","options":[{"label":"Alpha"},{"label":"Beta"}]}],"answers":{"0":"Alpha"}}}}}`
- **AND** the companion SHALL respond with ack `{ type: "ack", req_id: "<matching>", ok: true }`

#### Scenario: Answer for session without pending control response

- **WHEN** companion receives `answer_interactive_tool` for session "sess-1"
- **AND** session "sess-1" has no pending control response (already answered or timed out)
- **THEN** the companion SHALL respond with ack `{ type: "ack", req_id: "<matching>", ok: false, error: "no_pending_control_request" }`

#### Scenario: Answer for unknown session

- **WHEN** companion receives `answer_interactive_tool` for an unknown session_id
- **THEN** the companion SHALL respond with ack `{ type: "ack", req_id: "<matching>", ok: false, error: "session_not_found" }`

#### Scenario: Answer with mismatched call_id

- **WHEN** companion receives `answer_interactive_tool` with `call_id: "wrong-id"`
- **AND** the session's pending control response has request_id "correct-id"
- **THEN** the companion SHALL respond with ack `{ ok: false, error: "call_id_mismatch" }`

---

### Requirement: Android InteractiveToolCard submits answer via dedicated API

The Android `DetailViewModel.submitToolAnswer()` method SHALL call the `POST /v1/sessions/:id/answer` endpoint instead of `sendMessage()`.

#### Scenario: User taps option on InteractiveToolCard

- **WHEN** user taps "Alpha" option on an InteractiveToolCard with id "req-ask-1" for question index 0
- **THEN** the Android app SHALL call `POST /v1/sessions/<sessionId>/answer` with `{"call_id":"req-ask-1","answer":"Alpha","question_index":0}`
- **AND** SHALL NOT call `sendMessage()` with the answer text

#### Scenario: User types free-form answer and submits

- **WHEN** user types "Something else" in the text field and taps send
- **THEN** the Android app SHALL call the answer endpoint with `{"call_id":"<card_id>","answer":"Something else","question_index":0}`

#### Scenario: Answer API returns error

- **WHEN** the answer endpoint returns a non-2xx status
- **THEN** the Android app SHALL show an error via snackbar
- **AND** the InteractiveToolCard SHALL remain in unanswered state (user can retry)

#### Scenario: Optimistic UI update on submit

- **WHEN** user submits an answer
- **THEN** the InteractiveToolCard SHALL immediately show a "提交中..." loading state
- **AND** on success (tool_call_completed event arrives), transition to "已提交回答"
- **AND** on failure, revert to unanswered state with error message

---

### Requirement: Android RelayHttpClient provides answerInteractiveTool method

The `RelayHttpClient` SHALL expose a `suspend fun answerInteractiveTool(relayUrl, token, sessionId, callId, answer, questionIndex): Result<Unit>` method.

#### Scenario: Successful HTTP call

- **WHEN** `answerInteractiveTool(url, token, "sess-1", "req-ask-1", "Alpha", 0)` is called
- **THEN** it SHALL send `POST <url>/v1/sessions/sess-1/answer` with JSON body `{"call_id":"req-ask-1","answer":"Alpha","question_index":0}` and `Authorization: Bearer <token>` header
- **AND** SHALL return `Result.success(Unit)` on 200 response

#### Scenario: Network error

- **WHEN** the HTTP call fails due to network issues
- **THEN** it SHALL return `Result.failure()` with a descriptive exception

#### Scenario: Server error response

- **WHEN** the server responds with 409 (state_conflict)
- **THEN** it SHALL return `Result.failure()` with a `RelayHttpException` containing the error code

---

### Requirement: End-to-end AskUserQuestion flow completes in single model turn

The full flow from model tool call to model receiving the user's answer SHALL complete within a single API turn, matching the behavior of a real Claude Code terminal session.

#### Scenario: Happy path end-to-end

- **WHEN** the model calls AskUserQuestion with `input: { questions: [{ question: "Pick one", options: [{ label: "A" }, { label: "B" }, { label: "C" }] }] }`
- **THEN** Claude Code emits a `control_request` with `subtype: "can_use_tool", tool_name: "AskUserQuestion"` on stdout and blocks
- **AND** companion saves `request.input` (containing the questions array) and emits `tool_call_started` to relay → Android shows InteractiveToolCard
- **AND** user taps "B" → Android calls `POST /answer` with `{ call_id, answer: "B", question_index: 0 }` → relay → companion
- **AND** companion constructs `updatedInput: { questions: [<original>], answers: { "0": "B" } }` and writes `control_response` with `behavior: "allow"` to stdin
- **AND** Claude Code resumes — `AskUserQuestion.call({ questions, answers: { "0": "B" } })` returns `{ data: { questions, answers } }` without terminal interaction
- **AND** `mapToolResultToToolResultBlockParam` formats the tool_result as `"User has answered your questions: \"0\"=\"B\". You can now continue with the user's answers in mind."`
- **AND** companion emits `tool_call_completed` to relay → Android updates card to "已提交回答"
- **AND** the model continues in the SAME turn with the answer "B"
- **AND** no extra API calls are made compared to a real terminal session

#### Scenario: Multiple AskUserQuestion calls in one session

- **WHEN** the model calls AskUserQuestion, user answers, then the model calls AskUserQuestion again
- **THEN** each call SHALL be handled independently
- **AND** the second call SHALL create a new InteractiveToolCard
- **AND** both cards SHALL be visible in the timeline (first one as answered, second as pending)

#### Scenario: AskUserQuestion followed by regular tool calls

- **WHEN** the model calls AskUserQuestion, user answers, then the model calls Bash
- **THEN** the Bash control_request SHALL be auto-approved
- **AND** the Bash tool_call_started and tool_call_completed events SHALL flow normally
- **AND** the AskUserQuestion card and Bash tool card SHALL both appear in the timeline
