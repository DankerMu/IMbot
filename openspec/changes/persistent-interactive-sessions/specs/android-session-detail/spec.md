## MODIFIED Requirements

### Requirement: Session detail supports continuous input

The session detail screen SHALL allow sending messages when session status is `idle`, enabling multi-turn conversation without leaving the screen.

#### Scenario: Input enabled in idle state
- **WHEN** session detail screen is displayed and session status is `idle`
- **THEN** the message input field SHALL be enabled and focused
- **AND** the send button SHALL be enabled
- **AND** a visual indicator shows "Session ready" or equivalent

#### Scenario: Input disabled during running state
- **WHEN** session detail screen is displayed and session status is `running`
- **THEN** the message input field SHALL be disabled
- **AND** a visual indicator shows "Claude is responding..." or equivalent

#### Scenario: Send message from idle state
- **WHEN** user types a message and taps send while session is `idle`
- **THEN** Android calls `POST /v1/sessions/:id/message` with the message text
- **AND** input field clears and disables (session transitions to `running`)
- **AND** new events stream into the timeline in real-time

#### Scenario: Input disabled in terminal states
- **WHEN** session status is `completed`, `failed`, or `cancelled`
- **THEN** the message input field SHALL be hidden or replaced with a "Resume" button (existing behavior)

### Requirement: Session detail renders idle status

The session detail screen SHALL render the `idle` status distinctly from `completed`.

#### Scenario: Idle status indicator
- **WHEN** session status transitions to `idle`
- **THEN** the status chip shows "Idle" with a distinct color (not the same as "Completed")
- **AND** the timeline shows a visual separator indicating turn boundary

#### Scenario: Idle to running transition
- **WHEN** session transitions from `idle` to `running` (user sent a message)
- **THEN** the status chip updates to "Running"
- **AND** the user's message appears in the timeline
- **AND** subsequent assistant events append below

## ADDED Requirements

### Requirement: Complete session action

The session detail screen SHALL provide an action to explicitly complete (end) a persistent session.

#### Scenario: Complete button in idle state
- **WHEN** session is in `idle` state
- **THEN** session detail screen shows a "End Session" action (in toolbar or overflow menu)
- **WHEN** user taps "End Session"
- **THEN** Android calls `POST /v1/sessions/:id/complete`
- **AND** session transitions to `completed`
- **AND** input field is hidden/replaced with "Resume" button

#### Scenario: Complete button hidden in non-idle states
- **WHEN** session is in `running`, `completed`, `failed`, or `cancelled` state
- **THEN** the "End Session" action SHALL NOT be shown

### Requirement: Session list shows idle status

The session list screen SHALL display `idle` sessions with appropriate visual treatment.

#### Scenario: Idle session in list
- **WHEN** session list contains a session with status `idle`
- **THEN** the session card shows an "Idle" status badge
- **AND** the card is visually distinct from completed sessions (e.g., active color, pulsing dot)

#### Scenario: Idle session sort order
- **WHEN** session list is sorted by default (most recent first)
- **THEN** `idle` sessions sort by `last_active_at` alongside `running` sessions
- **AND** `idle` sessions appear above `completed`/`failed` sessions
