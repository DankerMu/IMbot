# Proposal: p1-companion-session-management

## Why

The minimal companion (p0-companion-minimal) only supports creating new sessions and forwarding their events. Full companion functionality requires listing historical sessions from the local Claude Code runtime, resuming old sessions, sending follow-up messages to running sessions, cancelling sessions, dynamically managing workspace roots, and supporting the book provider (same Claude Code binary under a different name, restricted to novel directories).

Without these capabilities, the Android user cannot browse past work, continue interrupted conversations, or manage their development workspace remotely — all core use cases defined in FR-01 through FR-05 of the PRD.

## What Changes

| Area | Change |
|------|--------|
| `list_sessions` command | Read local `~/.claude/projects/` directory structure to enumerate historical sessions. Return `{provider_session_id, cwd, created_at, status}` per session. Filter by provider (claude vs book uses different binary history paths). |
| `resume_session` command | Spawn `claude --resume --session-id <id> --output-format stream-json` in the original cwd. Parse and forward events identically to create_session. Support `provider=book` via configurable binary. |
| `send_message` command | Write `text + newline` to the stdin of a running CLI process tracked by session_id. |
| `cancel_session` command | Send SIGINT to a running CLI process tracked by session_id. Detect exit and emit completed/cancelled event. |
| Workspace root sync | Receive `add_root` / `remove_root` commands from relay. Validate path exists on local filesystem. Persist to `~/.imbot/companion.json`. |
| Book provider | Configurable binary name (default: `book`). Workspace roots restricted to novel directories only. Session create/resume uses book binary. All other behavior identical to claude provider. |

## Capabilities

- `companion-list-sessions` — Enumerate local session history from CLI project directories.
- `companion-resume-session` — Resume an existing session via CLI `--resume` flag.
- `companion-send-cancel` — Send messages to stdin and cancel via SIGINT for running sessions.
- `companion-workspace-sync` — Dynamic add/remove workspace roots with local filesystem validation.
- `companion-book-provider` — Book provider binary selection and novel-directory restriction.

## Dependencies

- `p0-companion-minimal` must be complete (WSS client, command dispatch, CLI adapter infrastructure).
- `p0-monorepo-and-wire` must define `list_sessions`, `resume_session`, `add_root`, `remove_root` in `CompanionCommand` type.
- `claude` CLI binary installed and supporting `--resume --session-id`.
- Optional: `book` CLI binary (same source, different name).

## Risk

| Risk | Mitigation |
|------|-----------|
| `~/.claude/projects/` directory structure changes between CLI versions | Abstract session discovery behind an adapter; log unknown structures without crashing |
| Session history files lack consistent schema | Parse defensively; return partial results with warnings |
| book binary not installed on all machines | Configurable binary path; graceful error if missing |
| Workspace root validation race (path deleted after check) | Validate at add time only; session creation re-validates at spawn time |
| Multiple running processes consume excessive memory | No hard cap (single user), but log resource warnings |
