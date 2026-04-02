## MODIFIED Requirements

### Requirement: Session state transitions

The relay SHALL enforce the following state transition table:

```text
queued     → running, failed
running    → idle, completed, failed, cancelled
idle       → running, completed, failed, cancelled
completed  → running  (resume only)
failed     → running  (resume only)
cancelled  → running  (resume only)
```

#### Scenario: Cancelled to running on resume

- **WHEN** session is `cancelled`
- **AND** the session still has a persisted `provider_session_id`
- **AND** the client sends `POST /v1/sessions/:id/resume`
- **THEN** relay dispatches `resume_session`
- **AND** the session transitions to `running`

### Requirement: Reject send_message on inactive states

- **WHEN** session is in `completed`, `failed`, or `cancelled` status
- **AND** relay receives `POST /v1/sessions/:id/message`
- **THEN** relay returns HTTP 409 with error_code `state_conflict`

### Requirement: Resume endpoint accepts resumable inactive states

#### Scenario: Resume a cancelled session with a surviving mapping

- **WHEN** the client sends `POST /v1/sessions/:id/resume`
- **AND** session status is `cancelled`
- **AND** `provider_session_id` is present
- **THEN** relay returns HTTP 200
- **AND** the returned session status is `running`

#### Scenario: Resume a cancelled session without a mapping

- **WHEN** the client sends `POST /v1/sessions/:id/resume`
- **AND** session status is `cancelled`
- **AND** `provider_session_id` is missing
- **THEN** relay returns HTTP 409 with error_code `session_not_resumable`
