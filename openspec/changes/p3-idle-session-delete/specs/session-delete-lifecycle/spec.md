# Capability: session-delete-lifecycle

## MODIFIED Requirements

### Requirement: Delete session

Relay SHALL allow deleting interactive sessions from `idle` state.

#### Scenario: Delete an idle interactive session

- **GIVEN** a session in `idle`
- **AND** the session has a non-null `provider_session_id`
- **WHEN** the client sends `DELETE /v1/sessions/:id`
- **THEN** relay SHALL best-effort send `cancel_session` to the companion/provider
- **AND** relay SHALL delete the session record even if the companion no longer has an active process for that idle session
- **AND** the HTTP response SHALL be `204`

#### Scenario: Queued session still cannot be deleted

- **GIVEN** a session in `queued`
- **WHEN** the client sends `DELETE /v1/sessions/:id`
- **THEN** relay SHALL respond with `409 state_conflict`
