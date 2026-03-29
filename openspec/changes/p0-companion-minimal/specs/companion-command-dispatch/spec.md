# Capability: companion-command-dispatch

Receive JSON commands from the relay, route each command to the correct handler based on the `cmd` field, and return an acknowledgment with the corresponding `req_id`.

## ADDED Requirements

### Requirement: Command Routing by cmd Field

The companion SHALL maintain a registry of command handlers keyed by `cmd` string. When a command is received, the dispatcher SHALL look up the handler for the `cmd` value and invoke it with the command payload. Supported `cmd` values for Phase 0: `create_session`, `resume_session`, `send_message`, `cancel_session`.

#### Scenario: Known command dispatched correctly

WHEN a command `{ cmd: "create_session", req_id: "abc-123", provider: "claude", cwd: "/path", prompt: "hello", permission_mode: "bypassPermissions" }` arrives
THEN the dispatcher invokes the `create_session` handler with the full command payload
AND the handler receives the `req_id` for ack generation

#### Scenario: Unknown command -- error ack

WHEN a command with `cmd: "unknown_command"` and `req_id: "xyz-789"` arrives
THEN the dispatcher sends an error ack: `{ type: "ack", req_id: "xyz-789", status: "error", error_code: "unknown_command", message: "Unknown command: unknown_command" }`
AND no handler is invoked

#### Scenario: Malformed JSON -- error ack

WHEN the incoming message parses as JSON but lacks the required `cmd` field
THEN the dispatcher sends an error ack if `req_id` is present: `{ type: "ack", req_id: "<id>", status: "error", error_code: "invalid_request", message: "Missing cmd field" }`

WHEN the incoming message parses as JSON but lacks both `cmd` and `req_id`
THEN the dispatcher logs the error and discards the message (cannot ack without req_id)

#### Scenario: Concurrent commands handled independently

WHEN two commands arrive within the same event loop tick (e.g., `create_session` and `cancel_session` for different sessions)
THEN both are dispatched to their respective handlers independently
AND each produces its own ack with its own `req_id`
AND one command's execution does not block the other

### Requirement: Ack Generation

Every command with a `req_id` SHALL receive an ack response. The ack MUST be sent as soon as the handler completes (or fails). The ack format follows the CompanionMessage schema.

#### Scenario: Successful handler -- ok ack

WHEN a handler completes successfully with optional result data
THEN the dispatcher sends: `{ type: "ack", req_id: "<id>", status: "ok", data: <result> }`

#### Scenario: Handler throws -- error ack with error_code

WHEN a handler throws an error during execution
THEN the dispatcher catches the error
AND sends: `{ type: "ack", req_id: "<id>", status: "error", error_code: "<code>", message: "<error message>" }`
AND the error is logged locally
AND the dispatcher does not crash (continues to accept subsequent commands)

#### Scenario: Handler timeout

WHEN a handler does not resolve within 60 seconds
THEN the dispatcher sends a timeout error ack: `{ type: "ack", req_id: "<id>", status: "error", error_code: "handler_timeout", message: "Handler exceeded 60s timeout" }`
AND the handler's eventual result (if any) is discarded
