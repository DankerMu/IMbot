# Proposal: p0-companion-minimal

## Why

The companion daemon is the execution plane of the IMbot system. It runs on the user's MacBook, maintains a persistent outbound WSS connection to the relay server, and spawns `claude` / `book` CLI processes on demand. Without the companion, the relay has no way to reach the local development environment or invoke AI agents on the MacBook.

Phase 0 requires a minimal but functional companion that can:
1. Connect to relay and stay connected (auto-reconnect, heartbeat).
2. Receive commands from relay and dispatch them.
3. Spawn Claude Code / book CLI in `--output-format stream-json` mode.
4. Parse the streaming JSON output and forward events back to relay.
5. Support cancel (SIGINT) and sendMessage (stdin write) for running sessions.

## What Changes

| Area | Change |
|------|--------|
| WSS client | Outbound WebSocket connection to `wss://{relay}/v1/companion?token=<TOKEN>&host_id=<HOST_ID>` with exponential backoff reconnect (1s to 30s max). |
| Heartbeat | Send `{ type: "heartbeat", host_id, providers, uptime }` every 30 seconds. |
| Command dispatcher | Parse incoming JSON commands by `cmd` field, route to handler, return `ack` with `req_id`. |
| Claude CLI adapter | Spawn `claude` or `book` binary with `--output-format stream-json --print-session-id -p "<prompt>" --permission-mode bypassPermissions`. Parse stdout line-by-line as JSON events. Extract `provider_session_id` from `--print-session-id` output. Forward events to relay. |
| Cancel / sendMessage | SIGINT to running CLI process; write text + newline to stdin for new turns. |
| Local session index | JSON file mapping `provider_session_id <-> relay_session_id <-> cwd`. |

## Capabilities

- `companion-relay-client` -- WSS connection with auth, reconnect, message send/receive.
- `companion-heartbeat` -- Periodic heartbeat emission with host metadata.
- `companion-command-dispatch` -- Incoming command routing and ack generation.
- `claude-cli-adapter` -- CLI process lifecycle: spawn, stream parse, cancel, sendMessage.

## Dependencies

- Relay server must expose `wss://{relay}/v1/companion` endpoint (from `p0-relay-minimal`).
- `claude` CLI binary installed on MacBook.
- Optional: `book` CLI binary for book provider.
- Node.js runtime (companion is a Node.js/TypeScript process).
- `ws` npm package for WebSocket client.

## Risk

| Risk | Mitigation |
|------|-----------|
| CLI binary path differs across machines | Configurable binary path in `companion.json` |
| stream-json format changes between CLI versions | Pin expected event schema, log unknown event types without crashing |
| Relay unreachable for extended period | Exponential backoff with 30s cap; running sessions continue locally and events buffer or drop |
