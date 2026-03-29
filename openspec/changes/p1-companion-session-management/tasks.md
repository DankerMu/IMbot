# Tasks: p1-companion-session-management

## 1. Process Manager

- [ ] 1.1 Implement `ProcessManager` class with `Map<sessionId, ChildProcess>` tracking
- [ ] 1.2 Implement `spawn()` method with stdout/stderr/exit listeners and auto-cleanup
- [ ] 1.3 Implement `sendMessage()` method (stdin write with backpressure handling)
- [ ] 1.4 Implement `cancel()` method (SIGINT with 10s SIGKILL escalation)
- [ ] 1.5 Write unit tests for ProcessManager: spawn tracking, cleanup on exit, concurrent sessions

## 2. Session Discovery

- [ ] 2.1 Implement `~/.claude/projects/` directory scanner with path decoding
- [ ] 2.2 Implement session metadata file parser (defensive JSON parsing, skip corrupted entries)
- [ ] 2.3 Implement `list_sessions` command handler with cwd matching and provider filtering
- [ ] 2.4 Write unit tests: happy path (3 sessions), empty dir, non-existent dir, corrupted metadata, provider filter, sort order

## 3. Resume Session

- [ ] 3.1 Implement `resume_session` command handler: validate cwd, resolve binary, spawn with `--resume --session-id`
- [ ] 3.2 Wire resume process into ProcessManager (event forwarding, exit detection)
- [ ] 3.3 Write unit tests: valid resume, non-existent session-id, wrong cwd, book binary selection, session_id mapping

## 4. Send Message & Cancel

- [ ] 4.1 Register `send_message` command handler in dispatcher: validate running, write to stdin
- [ ] 4.2 Register `cancel_session` command handler in dispatcher: validate running, send SIGINT
- [ ] 4.3 Write unit tests: send to running, send to non-running, empty text, cancel running, cancel finished, SIGKILL escalation

## 5. Config Manager

- [ ] 5.1 Implement `ConfigManager` class with atomic file writes (temp + rename)
- [ ] 5.2 Implement `addRoot()` with filesystem validation (exists, is directory)
- [ ] 5.3 Implement `removeRoot()` with not-found error handling
- [ ] 5.4 Implement auto-create `~/.imbot/` directory on first write
- [ ] 5.5 Write unit tests: add root, add non-existent path, remove root, remove missing, persistence across reload, idempotent add

## 6. Workspace Sync Commands

- [ ] 6.1 Register `add_root` and `remove_root` command handlers in dispatcher
- [ ] 6.2 Wire handlers to ConfigManager methods
- [ ] 6.3 Write integration test: add root → restart companion → root still present

## 7. Book Provider

- [ ] 7.1 Implement binary selection logic (read from config, default fallback)
- [ ] 7.2 Implement novel-directory restriction check (`isAllowedForBook`)
- [ ] 7.3 Enforce restriction in create_session and resume_session handlers
- [ ] 7.4 Implement provider detection for heartbeat (binary availability check)
- [ ] 7.5 Write unit tests: book binary spawn, non-novel dir rejection, heartbeat with/without book, custom binary path

## 8. Wire Protocol Extension

- [ ] 8.1 Add `list_sessions`, `resume_session`, `add_root`, `remove_root` to `CompanionCommand` type in `@imbot/wire`
- [ ] 8.2 Add response data types for list_sessions result in wire package

## 9. Integration

- [ ] 9.1 Update command dispatcher switch/case to route new commands to handlers
- [ ] 9.2 End-to-end test: list → resume → send_message → cancel flow via mock relay
