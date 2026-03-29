# Tasks: p0-companion-minimal

## 1. Project Scaffold

- [ ] 1.1 Create `packages/companion/` with `package.json`, `tsconfig.json`, and entry point `src/index.ts`
- [ ] 1.2 Add dependencies: `ws`, `uuid`; devDependencies: `typescript`, `@types/ws`, `@types/node`, `vitest`
- [ ] 1.3 Create `companion.example.json` with all config fields documented

## 2. Configuration

- [ ] 2.1 Implement `src/config.ts`: load `companion.json` from `~/.imbot/companion.json` (or path from env `COMPANION_CONFIG`), validate required fields (`relay_url`, `token`, `host_id`, `providers`), exit with clear error if invalid

## 3. Relay Client (WSS)

- [ ] 3.1 Implement `src/relay-client.ts`: `RelayClient` class with `connect()`, `send(msg)`, `on('message', handler)`, `on('connected')`, `on('disconnected')` using EventEmitter
- [ ] 3.2 Implement auto-reconnect with exponential backoff (1s, 2s, 4s, 8s, 16s, 30s cap), backoff reset on successful connect
- [ ] 3.3 Implement connection-state-aware `send()` -- drop messages when disconnected with warning log
- [ ] 3.4 Write tests: connect success, reconnect on close, backoff timing, send-while-disconnected behavior

## 4. Heartbeat

- [ ] 4.1 Implement `src/heartbeat.ts`: `HeartbeatTimer` class that sends heartbeat every 30s via `RelayClient.send()`
- [ ] 4.2 Heartbeat includes `host_id`, `providers` (from config), `uptime` (seconds since process start)
- [ ] 4.3 Pause timer on disconnect, resume + immediate send on reconnect
- [ ] 4.4 Write tests: heartbeat content, timing, pause/resume on disconnect/reconnect

## 5. Command Dispatcher

- [ ] 5.1 Implement `src/dispatcher.ts`: `CommandDispatcher` class with `register(cmd, handler)` and `dispatch(message)` methods
- [ ] 5.2 Implement ack generation: ok ack with optional data, error ack with error_code and message
- [ ] 5.3 Handle edge cases: unknown cmd → error ack, missing cmd field → error ack (if req_id present) or log+discard, handler exception → error ack
- [ ] 5.4 Implement handler timeout (60s) -- send timeout error ack if handler does not resolve
- [ ] 5.5 Write tests: known command routing, unknown command, malformed message, handler error, handler timeout, concurrent dispatch

## 6. Claude CLI Adapter

- [ ] 6.1 Implement `src/runtime/claude-adapter.ts`: `ClaudeRuntimeAdapter` class with `createSession()`, `sendMessage()`, `cancel()` methods
- [ ] 6.2 Implement CLI spawn: `child_process.spawn(binary, args, { cwd, stdio: ['pipe','pipe','pipe'] })` with configurable binary path
- [ ] 6.3 Implement stdout line-by-line parsing via `readline.createInterface`
- [ ] 6.4 Implement `src/runtime/event-mapper.ts`: map CLI stream-json events to relay event types
- [ ] 6.5 Implement `provider_session_id` extraction from CLI output
- [ ] 6.6 Implement `cancel()`: SIGINT → wait 5s → SIGKILL fallback
- [ ] 6.7 Implement `sendMessage()`: write text + newline to stdin pipe; error if session not found
- [ ] 6.8 Implement crash detection: non-zero exit code → `session_error` event with stderr snippet
- [ ] 6.9 Maintain `ActiveSessions` map (providerSessionId → ChildProcess + metadata)

## 7. Local Session Index

- [ ] 7.1 Implement `src/runtime/session-index.ts`: read/write `~/.imbot/sessions.json` with atomic write (temp + rename)
- [ ] 7.2 Add entry on new session, read on startup for resume capability
- [ ] 7.3 Write tests: add/read/persist across restarts, atomic write, corrupt file handling

## 8. Integration Wiring

- [ ] 8.1 Wire all modules in `src/index.ts`: config → RelayClient → HeartbeatTimer → CommandDispatcher → ClaudeRuntimeAdapter
- [ ] 8.2 Register command handlers: `create_session`, `resume_session`, `send_message`, `cancel_session`
- [ ] 8.3 Add global unhandled rejection handler (log + continue)
- [ ] 8.4 Add graceful shutdown on SIGTERM/SIGINT: cancel active sessions, close WSS, exit

## 9. Integration Tests

- [ ] 9.1 Test end-to-end: mock relay WSS server → companion connects → send create_session command → companion spawns mock CLI → events flow back → completion
- [ ] 9.2 Test reconnect scenario: drop relay connection → companion reconnects → heartbeat sent
- [ ] 9.3 Test cancel flow: create session → cancel command → SIGINT sent → session cleaned up
