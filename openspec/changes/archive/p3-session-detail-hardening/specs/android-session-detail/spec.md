## MODIFIED Requirements

### Requirement: Session detail supports resumable inactive sessions

The session detail screen SHALL treat `completed`, `failed`, and `cancelled` sessions with a valid provider mapping as resumable states.

#### Scenario: Open cancelled session detail

- **WHEN** the user opens a session detail page whose status is `cancelled`
- **THEN** Android loads history first
- **AND** Android automatically calls `POST /v1/sessions/:id/resume`
- **AND** on success the session transitions back to `running` and then `idle`

#### Scenario: Resume fallback remains available

- **WHEN** a session detail page is showing `completed`, `failed`, or `cancelled`
- **THEN** the overflow menu contains a "恢复会话" action
- **AND** tapping it retries `POST /v1/sessions/:id/resume`

#### Scenario: Resumable inactive placeholder copy

- **WHEN** session detail is temporarily showing `completed`, `failed`, or `cancelled`
- **THEN** the disabled input placeholder communicates that the session can be resumed and continued
