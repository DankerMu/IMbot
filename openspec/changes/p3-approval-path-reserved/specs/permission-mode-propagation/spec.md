# Capability: permission-mode-propagation

## ADDED Requirements

### Requirement: Session `permission_mode` Is Preserved End-To-End

The system SHALL treat `permission_mode` as a persisted and transportable session field. When omitted, the default SHALL remain `bypassPermissions`.

#### Scenario: create session without `permission_mode`

WHEN `POST /v1/sessions` is called without a `permission_mode`
THEN the relay stores `permission_mode = "bypassPermissions"`
AND the companion command includes `permission_mode = "bypassPermissions"`

#### Scenario: create session with explicit non-default mode

WHEN `POST /v1/sessions` is called with `permission_mode = "default"`
THEN the relay stores `permission_mode = "default"`
AND the companion command includes `permission_mode = "default"`
AND the value is NOT rewritten to `bypassPermissions`

#### Scenario: session detail exposes the configured mode

WHEN `GET /v1/sessions/:id` is called for a session created with `permission_mode = "default"`
THEN the response includes `permission_mode = "default"`

### Requirement: CLI Adapter Uses Supplied Permission Mode

The companion CLI adapter SHALL spawn `claude` / `book` with the exact `permission_mode` supplied by the session command.

#### Scenario: default mode spawn

WHEN the companion receives `create_session` with `permission_mode = "bypassPermissions"`
THEN it spawns the CLI with `--permission-mode bypassPermissions`

#### Scenario: non-default mode spawn

WHEN the companion receives `create_session` with `permission_mode = "default"`
THEN it spawns the CLI with `--permission-mode default`
