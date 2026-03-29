# Capability: wire-protocol-types

## ADDED Requirements

### Requirement: EventType Enum

The wire package SHALL export an `EventType` string literal union covering all session event types defined in the engineering spec.

#### Scenario: all event types are defined

WHEN the `EventType` type is inspected
THEN it includes exactly these values:
- `session_started`
- `assistant_delta`
- `assistant_message`
- `tool_call_started`
- `tool_call_completed`
- `approval_required`
- `approval_resolved`
- `session_status_changed`
- `session_result`
- `session_error`
- `user_message`

#### Scenario: assigning a valid event type compiles

WHEN a variable is typed as `EventType` and assigned `'assistant_delta'`
THEN TypeScript compilation succeeds without errors

#### Scenario: assigning an invalid event type fails compilation

WHEN a variable is typed as `EventType` and assigned `'invalid_event'`
THEN TypeScript compilation fails with a type error

#### Scenario: runtime array of all event types is available

WHEN `EVENT_TYPES` constant array is imported
THEN it contains all 11 event type string values
AND its type is `readonly EventType[]`

---

### Requirement: SessionStatus Enum

The wire package SHALL export a `SessionStatus` string literal union for session lifecycle states.

#### Scenario: all statuses are defined

WHEN the `SessionStatus` type is inspected
THEN it includes exactly: `queued`, `running`, `completed`, `failed`, `cancelled`

#### Scenario: status transition validation types

WHEN `VALID_TRANSITIONS` map is imported
THEN `VALID_TRANSITIONS['queued']` contains `['running', 'failed']`
AND `VALID_TRANSITIONS['running']` contains `['completed', 'failed', 'cancelled']`
AND `VALID_TRANSITIONS['completed']` contains `['running']` (resume)
AND `VALID_TRANSITIONS['failed']` contains `['running']` (resume)
AND `VALID_TRANSITIONS['cancelled']` is empty or undefined (terminal)

#### Scenario: invalid transition is detectable at runtime

WHEN code checks `VALID_TRANSITIONS['cancelled']?.includes('running')`
THEN the result is `false`

---

### Requirement: Provider Enum

The wire package SHALL export a `Provider` string literal union for supported AI providers.

#### Scenario: all providers are defined

WHEN the `Provider` type is inspected
THEN it includes exactly: `claude`, `book`, `openclaw`

#### Scenario: provider array is available

WHEN `PROVIDERS` constant array is imported
THEN it contains `['claude', 'book', 'openclaw']`
AND its type is `readonly Provider[]`

---

### Requirement: CompanionCommand Types

The wire package SHALL export a `CompanionCommand` discriminated union type covering all relay-to-companion commands.

#### Scenario: create_session command shape

WHEN a `CompanionCommand` with `cmd: 'create_session'` is constructed
THEN it MUST have fields: `req_id: string`, `provider: 'claude' | 'book'`, `cwd: string`, `prompt: string`, `permission_mode: string`
AND it MAY have optional field: `model?: string`

#### Scenario: resume_session command shape

WHEN a `CompanionCommand` with `cmd: 'resume_session'` is constructed
THEN it MUST have fields: `req_id: string`, `session_id: string`, `provider_session_id: string`, `cwd: string`

#### Scenario: send_message command shape

WHEN a `CompanionCommand` with `cmd: 'send_message'` is constructed
THEN it MUST have fields: `req_id: string`, `session_id: string`, `text: string`

#### Scenario: cancel_session command shape

WHEN a `CompanionCommand` with `cmd: 'cancel_session'` is constructed
THEN it MUST have fields: `req_id: string`, `session_id: string`

#### Scenario: list_sessions command shape

WHEN a `CompanionCommand` with `cmd: 'list_sessions'` is constructed
THEN it MUST have fields: `req_id: string`, `cwd: string`, `provider: 'claude' | 'book'`

#### Scenario: browse_directory command shape

WHEN a `CompanionCommand` with `cmd: 'browse_directory'` is constructed
THEN it MUST have fields: `req_id: string`, `path: string`

#### Scenario: discriminated union narrows correctly

WHEN code switches on `command.cmd`
THEN TypeScript narrows the type so that `command.prompt` is only accessible when `cmd === 'create_session'`
AND accessing `command.prompt` when `cmd === 'cancel_session'` produces a type error

#### Scenario: unknown command variant is rejected

WHEN a value `{ cmd: 'unknown_cmd', req_id: '...' }` is assigned to `CompanionCommand`
THEN TypeScript compilation fails

---

### Requirement: CompanionMessage Types

The wire package SHALL export a `CompanionMessage` discriminated union for companion-to-relay messages.

#### Scenario: ack success message shape

WHEN a `CompanionMessage` with `type: 'ack'` and `status: 'ok'` is constructed
THEN it MUST have fields: `req_id: string`, `status: 'ok'`
AND it MAY have optional field: `data?: unknown`

#### Scenario: ack error message shape

WHEN a `CompanionMessage` with `type: 'ack'` and `status: 'error'` is constructed
THEN it MUST have fields: `req_id: string`, `status: 'error'`, `error_code: string`, `message: string`

#### Scenario: event message shape

WHEN a `CompanionMessage` with `type: 'event'` is constructed
THEN it MUST have fields: `session_id: string`, `event_type: EventType`, `payload: unknown`

#### Scenario: heartbeat message shape

WHEN a `CompanionMessage` with `type: 'heartbeat'` is constructed
THEN it MUST have fields: `host_id: string`, `providers: string[]`, `uptime: number`

#### Scenario: discriminated union narrows on type field

WHEN code checks `msg.type === 'heartbeat'`
THEN TypeScript narrows so `msg.host_id` and `msg.uptime` are accessible
AND `msg.req_id` is not accessible (only on ack type)

---

### Requirement: ServerMessage Types

The wire package SHALL export a `ServerMessage` discriminated union for relay-to-android (server-to-client) WebSocket messages.

#### Scenario: event message shape

WHEN a `ServerMessage` with `type: 'event'` is constructed
THEN it MUST have fields: `session_id: string`, `seq: number`, `event_type: EventType`, `payload: unknown`, `timestamp: string`

#### Scenario: status message shape

WHEN a `ServerMessage` with `type: 'status'` is constructed
THEN it MUST have fields: `session_id: string`, `status: SessionStatus`

#### Scenario: host_status message shape

WHEN a `ServerMessage` with `type: 'host_status'` is constructed
THEN it MUST have fields: `host_id: string`, `status: 'online' | 'offline'`

#### Scenario: error message shape

WHEN a `ServerMessage` with `type: 'error'` is constructed
THEN it MUST have fields: `code: string`, `message: string`

#### Scenario: pong message shape

WHEN a `ServerMessage` with `type: 'pong'` is constructed
THEN it has only the `type` field with value `'pong'`

---

### Requirement: ClientMessage Types

The wire package SHALL export a `ClientMessage` discriminated union for android-to-relay (client-to-server) WebSocket messages.

#### Scenario: auth message shape

WHEN a `ClientMessage` with `action: 'auth'` is constructed
THEN it MUST have fields: `action: 'auth'`, `token: string`

#### Scenario: subscribe message shape

WHEN a `ClientMessage` with `action: 'subscribe'` is constructed
THEN it MUST have fields: `action: 'subscribe'`, `session_id: string`

#### Scenario: unsubscribe message shape

WHEN a `ClientMessage` with `action: 'unsubscribe'` is constructed
THEN it MUST have fields: `action: 'unsubscribe'`, `session_id: string`

#### Scenario: ping message shape

WHEN a `ClientMessage` with `action: 'ping'` is constructed
THEN it has only the `action` field with value `'ping'`

#### Scenario: discriminated union narrows on action field

WHEN code checks `msg.action === 'subscribe'`
THEN TypeScript narrows so `msg.session_id` is accessible
AND accessing `msg.token` produces a type error (only on auth)

---

### Requirement: ErrorCode Enum

The wire package SHALL export an `ErrorCode` string literal union covering all machine-readable error codes.

#### Scenario: all error codes are defined

WHEN the `ErrorCode` type is inspected
THEN it includes exactly:
- `unauthenticated`
- `forbidden`
- `not_found`
- `invalid_request`
- `state_conflict`
- `host_offline`
- `provider_unreachable`
- `directory_not_found`
- `session_not_resumable`
- `command_timeout`
- `seq_gap_detected`

#### Scenario: error code to HTTP status mapping

WHEN `ERROR_HTTP_STATUS` map is imported
THEN `ERROR_HTTP_STATUS['unauthenticated']` equals `401`
AND `ERROR_HTTP_STATUS['not_found']` equals `404`
AND `ERROR_HTTP_STATUS['host_offline']` equals `502`
AND `ERROR_HTTP_STATUS['command_timeout']` equals `504`
AND `ERROR_HTTP_STATUS['invalid_request']` equals `400`

#### Scenario: unknown error code fails type check

WHEN a variable typed as `ErrorCode` is assigned `'unknown_error'`
THEN TypeScript compilation fails

---

### Requirement: Shared Model Types

The wire package SHALL export TypeScript interfaces for shared domain models used across relay and companion.

#### Scenario: Session model fields

WHEN the `Session` interface is inspected
THEN it MUST have fields:
- `id: string`
- `provider: Provider`
- `provider_session_id: string | null`
- `host_id: string`
- `workspace_root: string | null`
- `workspace_cwd: string`
- `initial_prompt: string | null`
- `model: string | null`
- `permission_mode: string`
- `status: SessionStatus`
- `error_message: string | null`
- `error_code: string | null`
- `created_at: string`
- `updated_at: string`
- `last_active_at: string`

#### Scenario: Host model fields

WHEN the `Host` interface is inspected
THEN it MUST have fields:
- `id: string`
- `name: string`
- `type: 'macbook' | 'relay_local'`
- `status: 'online' | 'offline'`
- `last_heartbeat_at: string | null`
- `created_at: string`
- `updated_at: string`

#### Scenario: WorkspaceRoot model fields

WHEN the `WorkspaceRoot` interface is inspected
THEN it MUST have fields:
- `id: string`
- `host_id: string`
- `provider: Provider`
- `path: string`
- `label: string | null`
- `created_at: string`

#### Scenario: SessionEvent model fields

WHEN the `SessionEvent` interface is inspected
THEN it MUST have fields:
- `id: string`
- `session_id: string`
- `seq: number`
- `type: EventType`
- `payload: unknown`
- `created_at: string`

#### Scenario: model types are JSON-serializable

WHEN a `Session` object is passed through `JSON.parse(JSON.stringify(session))`
THEN the result is structurally identical
AND all date fields are ISO 8601 strings (not Date objects)

---

### Requirement: Package Exports Configuration

The wire package `package.json` SHALL be configured so that `@imbot/wire` is importable by other workspace packages with full type information.

#### Scenario: main and types fields are set

WHEN `packages/wire/package.json` is read
THEN `"main"` is `"dist/index.js"`
AND `"types"` is `"dist/index.d.ts"`

#### Scenario: index.ts re-exports all public types

WHEN `packages/wire/src/index.ts` is read
THEN it re-exports: `EventType`, `EVENT_TYPES`, `SessionStatus`, `VALID_TRANSITIONS`, `Provider`, `PROVIDERS`, `ErrorCode`, `ERROR_HTTP_STATUS`, `CompanionCommand`, `CompanionMessage`, `ServerMessage`, `ClientMessage`, `Session`, `Host`, `WorkspaceRoot`, `SessionEvent`

#### Scenario: importing from @imbot/wire in relay succeeds

WHEN `packages/relay/src/test.ts` contains `import { EventType, Session } from '@imbot/wire'`
AND `npm run build` is executed
THEN compilation succeeds without errors

#### Scenario: wire package has no runtime dependencies

WHEN `packages/wire/package.json` `dependencies` field is inspected
THEN it is empty or absent (wire is types-only, no runtime deps)
