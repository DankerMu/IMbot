## MODIFIED Requirements

### Requirement: New session creation does not require prompt

Android new session flow SHALL allow creating a session with only provider, host, and directory selected. The prompt input step becomes optional.

#### Scenario: Create session without typing a prompt
- **GIVEN** user has selected provider "book", host "mac-1", and directory "/path/to/project"
- **AND** user has NOT entered any prompt text
- **WHEN** user taps "开始" button
- **THEN** app sends `POST /sessions` with `{ provider, host_id, cwd }` (no prompt field)
- **AND** navigates to session detail page

#### Scenario: Create session with a prompt (existing behavior)
- **GIVEN** user has selected provider, host, directory, AND entered prompt "hello"
- **WHEN** user taps "开始"
- **THEN** app sends `POST /sessions` with `{ provider, host_id, cwd, prompt: "hello" }`
- **AND** navigates to session detail page

#### Scenario: canCreate validation without prompt
- **GIVEN** `NewSessionUiState` with `provider = "book"`, `hostId = "mac-1"`, `cwd = "/path"`, `prompt = ""`
- **WHEN** `canCreate(state)` is evaluated
- **THEN** returns `true`

#### Scenario: canCreate still requires provider
- **GIVEN** `NewSessionUiState` with `provider = null`
- **WHEN** `canCreate(state)` is evaluated
- **THEN** returns `false`

#### Scenario: canCreate still requires cwd
- **GIVEN** `NewSessionUiState` with `cwd = null`
- **WHEN** `canCreate(state)` is evaluated
- **THEN** returns `false`

#### Scenario: "开始" button enabled after directory selection
- **GIVEN** user is on step 1 (directory selection) and has chosen a valid directory
- **AND** provider and host are already selected from step 0
- **WHEN** directory selection completes
- **THEN** "开始" button is enabled (canCreate is true)
- **AND** user can proceed to step 2 (optional prompt) OR tap "开始" directly

### Requirement: Prompt step is optional in wizard

#### Scenario: Step titles reflect optional prompt
- **THEN** step titles are `["Provider", "目录", "消息（可选）"]`
- **AND** step 2 (prompt input) clearly indicates it is not required

#### Scenario: User skips prompt step
- **GIVEN** user is on step 1 and taps "开始" instead of "下一步"
- **THEN** session is created without prompt
- **AND** user navigates to detail page

#### Scenario: User enters prompt on step 2
- **GIVEN** user advanced to step 2 and typed "hello"
- **WHEN** user taps "开始"
- **THEN** session is created with `prompt: "hello"`

### Requirement: Detail page handles empty idle session

#### Scenario: Empty session detail page shows input bar
- **GIVEN** an idle session with `initial_prompt: null`
- **WHEN** detail page loads
- **THEN** input bar is enabled with placeholder "发送消息..."
- **AND** session status badge shows "空闲"

#### Scenario: First message in empty session triggers running state
- **GIVEN** user is on detail page of an empty idle session
- **WHEN** user types "hello" and taps send
- **THEN** message is sent via `POST /sessions/:id/message`
- **AND** session status transitions to "running"
- **AND** UI updates: status badge → "运行中", input bar → disabled during send

### Requirement: RelayHttpClient prompt parameter is nullable

#### Scenario: createSession called without prompt
- **GIVEN** `prompt` parameter is `null`
- **WHEN** `RelayHttpClient.createSession()` builds the JSON body
- **THEN** the `prompt` key is NOT included in the JSON object
- **AND** the request succeeds with 201

#### Scenario: createSession called with prompt
- **GIVEN** `prompt` parameter is `"hello"`
- **WHEN** `RelayHttpClient.createSession()` builds the JSON body
- **THEN** the `prompt` key IS included with value `"hello"`
