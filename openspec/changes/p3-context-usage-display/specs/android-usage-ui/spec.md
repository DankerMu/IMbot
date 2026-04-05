## ADDED Requirements

### Requirement: SessionUsageState data model

Android SHALL maintain a `SessionUsageState` model that tracks token usage for the current session.

#### Scenario: Default usage state
- **GIVEN** a new session with no usage events received
- **THEN** `SessionUsageState` defaults to `inputTokens = 0, outputTokens = 0, model = null`
- **AND** `totalTokens = 0`, `usagePercent = 0f`

#### Scenario: Usage state with tokens
- **GIVEN** `SessionUsageState(inputTokens = 12500, outputTokens = 3200, contextWindow = 200000, model = "claude-sonnet-4-6")`
- **THEN** `totalTokens = 15700`
- **AND** `contextWindowSize = 200000`
- **AND** `usagePercent = 0.0785f`

#### Scenario: Context window is absent
- **GIVEN** `SessionUsageState(inputTokens = 12500, outputTokens = 3200, contextWindow = 0)`
- **THEN** `usagePercent = 0f`
- **AND** Android does not infer a total window from the model name alone

### Requirement: EventProcessor handles session_usage events

#### Scenario: session_usage event does not appear in timeline
- **WHEN** EventProcessor receives a `session_usage` event
- **THEN** it is NOT added to the messages list (no visible timeline entry)
- **AND** usage data is extracted and made available to ViewModel

#### Scenario: session_usage payload extraction
- **WHEN** event payload contains `{ input_tokens: 12500, output_tokens: 3200, context_window: 200000, model: "claude-sonnet-4-6" }`
- **THEN** EventProcessor produces a `SessionUsageState` carrying those exact values

#### Scenario: Consecutive session_usage events
- **WHEN** two `session_usage` events arrive
- **THEN** the latter replaces the former (latest snapshot wins)

### Requirement: DetailViewModel tracks usage state

#### Scenario: Usage state updated on session_usage event
- **WHEN** a `session_usage` event is processed
- **THEN** `uiState.usage` is updated with the new values
- **AND** UI recomposes to reflect the change

#### Scenario: Model extracted from session_started event
- **WHEN** `session_started` event contains `model: "claude-opus-4-6"` in payload
- **THEN** `uiState.usage.model` is set to `"claude-opus-4-6"`

#### Scenario: Model fallback from RelaySession
- **WHEN** `session_started` has no `model` in payload
- **AND** `RelaySession.model = "opus"`
- **THEN** `uiState.usage.model` falls back to `"opus"`

#### Scenario: Usage preserved after session ends
- **WHEN** session transitions to `completed` / `failed` / `cancelled`
- **THEN** `uiState.usage` retains its last values (not cleared)

#### Scenario: Usage cleared on session reload
- **WHEN** `loadSession()` is called (fresh load or reconnect)
- **THEN** `uiState.usage` resets to default `SessionUsageState()`

### Requirement: UsageIndicator composable in detail top bar

#### Scenario: UsageIndicator visible during active session
- **GIVEN** session `effectiveStatus` is `"running"` or `"idle"`
- **AND** `usage.totalTokens > 0`
- **WHEN** detail page renders top bar
- **THEN** `UsageIndicator` is visible showing token count and progress bar

#### Scenario: UsageIndicator hidden for inactive session
- **GIVEN** session `effectiveStatus` is `"completed"`, `"failed"`, or `"cancelled"`
- **WHEN** detail page renders top bar
- **THEN** `UsageIndicator` is NOT rendered

#### Scenario: UsageIndicator hidden when no usage data
- **GIVEN** `usage.totalTokens == 0`
- **WHEN** detail page renders top bar
- **THEN** `UsageIndicator` is NOT rendered (no empty/zero state shown)

#### Scenario: Token count display format
- **GIVEN** `totalTokens = 12500` and `contextWindowSize = 200000`
- **THEN** displays `"12.5k / 200k"`

#### Scenario: Progress bar color — normal
- **GIVEN** `usagePercent <= 0.80f`
- **THEN** progress bar and text color are green (`#66BB6A`)

#### Scenario: Progress bar color — warning
- **GIVEN** `0.80f < usagePercent <= 0.90f`
- **THEN** progress bar and text color are orange (`#FFA726`)

#### Scenario: Progress bar color — critical
- **GIVEN** `usagePercent > 0.90f`
- **THEN** progress bar and text color are red (`#E53935`)

#### Scenario: Progress bar dimensions
- **THEN** progress bar width is `40.dp`, height is `3.dp`, corner radius `1.5.dp`

### Requirement: formatTokenCount utility

#### Scenario: Format tokens < 1000
- **GIVEN** count = 500
- **THEN** returns `"500"`

#### Scenario: Format tokens in thousands
- **GIVEN** count = 12500
- **THEN** returns `"12.5k"`

#### Scenario: Format tokens = 1000 exactly
- **GIVEN** count = 1000
- **THEN** returns `"1k"`

#### Scenario: Format tokens in millions
- **GIVEN** count = 1500000
- **THEN** returns `"1.5M"`

#### Scenario: Format zero tokens
- **GIVEN** count = 0
- **THEN** returns `"0"`

### Requirement: Layout integration with existing top bar

#### Scenario: UsageIndicator placement
- **THEN** UsageIndicator is placed in the TopAppBar subtitle area, to the right of the workspace path
- **AND** does NOT overlap with provider badge or status badge

#### Scenario: Dark theme compatibility
- **WHEN** app is in dark theme
- **THEN** UsageIndicator colors adapt (green/orange/red on dark surface)
- **AND** progress bar track uses `MaterialTheme.colorScheme.surfaceVariant`

### Requirement: Session list shows persisted usage summary

#### Scenario: Session card renders real model and usage pills
- **GIVEN** relay session summary contains `model = "glm-5"`, `input_tokens = 42000`, `output_tokens = 9000`, `context_window = 200000`
- **WHEN** SessionCard renders
- **THEN** it shows a model pill with `glm-5`
- **AND** it shows a usage pill with `51k/200k`

#### Scenario: Session card hides usage pill without a real context window
- **GIVEN** relay session summary contains tokens but `context_window = 0`
- **WHEN** SessionCard renders
- **THEN** it does not show a `[used/total]` pill derived from hardcoded model knowledge
