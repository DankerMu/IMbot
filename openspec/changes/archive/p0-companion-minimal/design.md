# Design: p0-companion-minimal

## Architecture Overview

The companion is a single long-running Node.js/TypeScript process. It maintains one outbound WSS connection to the relay and spawns CLI child processes on demand. There is no HTTP server -- the companion is purely a WebSocket client and process manager.

```
companion process
├── RelayClient          (ws connection, reconnect, send/receive)
│   ├── HeartbeatTimer   (30s interval, sends heartbeat)
│   └── MessageParser    (JSON parse incoming frames)
├── CommandDispatcher    (cmd routing, ack generation)
└── ClaudeRuntimeAdapter (spawn, stream parse, cancel, sendMessage)
    ├── ActiveSessions   (Map<providerSessionId, ChildProcess>)
    └── SessionIndex     (JSON file persistence)
```

## Key Design Decisions

### 1. CLI Spawn Approach

Use `child_process.spawn()` with `stdio: ['pipe', 'pipe', 'pipe']`:
- **stdin (pipe)**: enables `sendMessage` by writing text + newline.
- **stdout (pipe)**: parsed line-by-line for stream-json events.
- **stderr (pipe)**: captured for error reporting (last 500 chars kept in ring buffer).

Why not `exec`: need streaming stdout, not buffered output.
Why not `fork`: CLI is a separate binary, not a Node.js module.

### 2. stdout Line-by-Line Parsing

Use `readline.createInterface({ input: childProcess.stdout })` for reliable line splitting. Each line is `JSON.parse()`d independently. This handles partial writes and buffer boundaries correctly.

### 3. Provider Binary Path

Configurable in `companion.json`:
```json
{
  "relay_url": "wss://relay.example.com",
  "token": "...",
  "host_id": "macbook-1",
  "providers": {
    "claude": { "binary": "claude" },
    "book": { "binary": "/usr/local/bin/book" }
  }
}
```

Default binary name is the provider name itself (relies on PATH). Explicit path overrides.

### 4. Local Session Index

JSON file at `~/.imbot/sessions.json`:
```json
{
  "relay-sess-uuid-1": {
    "provider_session_id": "cli-sess-abc",
    "cwd": "/Users/dev/project",
    "provider": "claude",
    "created_at": "2026-03-28T13:00:00Z"
  }
}
```

Read on startup, written atomically (write to temp file + rename) on each update. No complex DB needed -- expected entry count is <100.

### 5. Reconnect Strategy

Exponential backoff with jitter:
- Base delays: 1s, 2s, 4s, 8s, 16s, 30s (capped).
- Small random jitter (0-500ms) added to each delay to avoid thundering herd (not critical for single companion, but good practice).
- Backoff resets to 1s on successful connection.

### 6. Event Mapping (stream-json to relay event model)

| CLI stream-json `type` | Relay `event_type` |
|------------------------|--------------------|
| `system` (with session_id) | (internal -- extract provider_session_id) |
| `assistant` / text content | `assistant_delta` |
| `assistant` / complete message | `assistant_message` |
| `tool_use` start | `tool_call_started` |
| `tool_result` | `tool_call_completed` |
| `result` | `session_result` |
| `error` | `session_error` |

Exact field mapping will be finalized during implementation against actual CLI output.

### 7. Concurrency Model

- Single WSS connection, single event loop.
- Multiple CLI processes can run concurrently (one per session).
- Each CLI process has its own readline parser and event forwarding.
- `ActiveSessions` map keyed by `providerSessionId` for O(1) lookup on cancel/sendMessage.

### 8. Error Handling

- CLI spawn failure: immediate error ack to relay.
- CLI process crash: `session_error` event to relay + cleanup from ActiveSessions.
- Relay disconnect during active session: CLI processes continue running. Events are dropped (not buffered). On reconnect, relay may request session status.
- Unhandled promise rejection: global handler logs + continues (companion must not crash).

## File Structure

```
packages/companion/
├── src/
│   ├── index.ts              -- entry point, wires modules
│   ├── config.ts             -- load companion.json
│   ├── relay-client.ts       -- WSS client with reconnect
│   ├── heartbeat.ts          -- heartbeat timer
│   ├── dispatcher.ts         -- command dispatch + ack
│   ├── runtime/
│   │   ├── claude-adapter.ts -- CLI spawn + stream parse
│   │   ├── event-mapper.ts   -- stream-json → relay event
│   │   └── session-index.ts  -- JSON file persistence
│   └── types.ts              -- shared type definitions
├── companion.example.json    -- example config
├── package.json
└── tsconfig.json
```
