## ADDED Requirements

### Requirement: CLI session viewer package

A new `packages/viewer/` package SHALL provide a CLI tool that connects to relay WebSocket and displays session events in real-time.

#### Scenario: View a specific session
- **WHEN** user runs `imbot-viewer --relay wss://relay.example.com/v1/ws --token <token> --session <session-id>`
- **THEN** viewer connects to relay WebSocket
- **AND** requests event catch-up for the specified session
- **AND** renders each event to stdout as formatted text (timestamp, event type, content)
- **AND** continues streaming new events in real-time

#### Scenario: View all active sessions
- **WHEN** user runs `imbot-viewer --relay wss://relay.example.com/v1/ws --token <token>`
- **THEN** viewer connects to relay WebSocket
- **AND** displays events from ALL sessions, prefixed with session ID
- **AND** filters to show only `running` and `idle` sessions by default

#### Scenario: Config from companion config file
- **WHEN** user runs `imbot-viewer` without `--relay` and `--token` flags
- **AND** `~/.imbot/companion.json` exists with `relay_url` and `token`
- **THEN** viewer reads connection details from companion config
- **AND** connects using those credentials

### Requirement: Event formatting

Viewer SHALL render events in a human-readable format suitable for terminal output.

#### Scenario: Assistant delta rendering
- **WHEN** viewer receives `assistant_delta` event with `payload: { text: "Hello world" }`
- **THEN** viewer outputs the text content directly (no JSON)

#### Scenario: Tool call rendering
- **WHEN** viewer receives `tool_call_started` event with `payload: { tool: "Edit", input: { file: "foo.ts" } }`
- **THEN** viewer outputs a formatted line like `[tool] Edit: foo.ts`

#### Scenario: Status change rendering
- **WHEN** viewer receives `session_status_changed` event with `payload: { status: "idle" }`
- **THEN** viewer outputs a status line like `--- session idle ---`

#### Scenario: User message rendering
- **WHEN** viewer receives `user_message` event with `payload: { text: "next question" }`
- **THEN** viewer outputs a formatted line like `[user] next question`

### Requirement: Graceful connection handling

Viewer SHALL handle connection lifecycle robustly.

#### Scenario: Relay unreachable
- **WHEN** viewer cannot connect to relay WebSocket
- **THEN** viewer prints error message and exits with code 1

#### Scenario: Connection lost during streaming
- **WHEN** WebSocket connection drops while streaming
- **THEN** viewer attempts reconnection with exponential backoff (reuse companion's ExponentialBackoff)
- **AND** on reconnection, requests event catch-up from last seen sequence number

#### Scenario: Ctrl+C exit
- **WHEN** user presses Ctrl+C while viewer is running
- **THEN** viewer closes WebSocket cleanly and exits with code 0
