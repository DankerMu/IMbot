## ADDED Requirements

### Requirement: Companion-backed session startup echoes the initial user message

For `claude` and `book`, relay SHALL persist and broadcast a `user_message` event when a session is started from an initial prompt.

#### Scenario: Create session with prompt

- **GIVEN** a client sends `POST /sessions` with `provider: "book"` and `prompt: "hello"`
- **WHEN** relay successfully starts the provider session
- **THEN** relay SHALL store a `user_message` event with payload `{ text: "hello" }`
- **AND** the event SHALL be available in subsequent session history reads

#### Scenario: First message starts an empty idle session

- **GIVEN** a `claude` session exists with `status: "idle"` and `provider_session_id: null`
- **WHEN** the client sends `POST /sessions/:id/message { text: "continue" }`
- **AND** relay successfully starts the provider session from that first message
- **THEN** relay SHALL store and broadcast a `user_message` event with payload `{ text: "continue" }`

#### Scenario: OpenClaw is not synthetic-echoed

- **GIVEN** a session is started with `provider: "openclaw"`
- **WHEN** relay starts the session from an initial prompt
- **THEN** relay SHALL NOT synthesize an extra `user_message` event on behalf of the provider
