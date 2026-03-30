# Design: p1-companion-session-management

## Process Management

### Running Process Map

```typescript
// packages/companion/src/process-manager.ts
class ProcessManager {
  private processes: Map<string, ChildProcess> = new Map(); // session_id → ChildProcess

  spawn(sessionId: string, binary: string, args: string[], cwd: string): ChildProcess;
  sendMessage(sessionId: string, text: string): void;    // writes text + \n to stdin
  cancel(sessionId: string): void;                        // SIGINT, escalate to SIGKILL after 10s
  isRunning(sessionId: string): boolean;
  getProcess(sessionId: string): ChildProcess | undefined;
  cleanup(sessionId: string): void;                       // remove from map on exit
}
```

- On `spawn()`: add to map, attach stdout/stderr/exit listeners.
- On process exit: remove from map, emit event (session_result or session_error based on exit code).
- SIGINT timeout: 10s grace → SIGKILL escalation.

### Stdin/Stdout/Stderr Piping

- **stdout**: line-by-line buffered read. Each line parsed as JSON (stream-json format). Forwarded to relay as `{ type: "event", session_id, event_type, payload }`.
- **stderr**: collected into buffer. On process exit, if exit code != 0, included in error event payload.
- **stdin**: writable stream. `send_message` writes `text + "\n"`. Backpressure: if write returns false, queue and drain.

## Session History Discovery

### Directory Structure

Claude Code stores session data in `~/.claude/projects/<encoded-path>/`. The encoded path replaces `/` with a separator. Each project directory contains session files with metadata.

```typescript
// packages/companion/src/session-discovery.ts
interface LocalSession {
  provider_session_id: string;
  cwd: string;
  created_at: string;  // ISO 8601
  status: 'completed' | 'unknown';
}

async function discoverSessions(cwd: string, provider: 'claude' | 'book'): Promise<LocalSession[]>;
```

- Parse `~/.claude/projects/` directory tree.
- Match project directories whose decoded path matches or starts with the requested `cwd`.
- Read session metadata files to extract session IDs and timestamps.
- Sort by `created_at` descending.
- Return defensive partial results on parse errors.

## Config File

### Path: `~/.imbot/companion.json`

```json
{
  "workspace_roots": [
    {
      "provider": "claude",
      "path": "/Users/danker/Desktop/AI-vault",
      "label": "AI-vault",
      "added_at": "2026-03-28T10:00:00Z"
    },
    {
      "provider": "book",
      "path": "/Users/danker/Desktop/novel",
      "label": "novel",
      "added_at": "2026-03-28T10:00:00Z"
    }
  ],
  "binaries": {
    "claude": "claude",
    "book": "book"
  }
}
```

### Config Manager

```typescript
// packages/companion/src/config-manager.ts
class ConfigManager {
  private configPath: string;  // ~/.imbot/companion.json

  load(): CompanionConfig;
  save(config: CompanionConfig): void;
  addRoot(provider: string, path: string, label?: string): void;
  removeRoot(provider: string, path: string): boolean;
  getRoots(provider?: string): WorkspaceRoot[];
  getBinaryPath(provider: 'claude' | 'book'): string;
}
```

- Atomic writes: write to temp file, rename to target (prevents corruption).
- Create `~/.imbot/` directory on first write if missing.
- Lock: simple file lock or sequential write queue to prevent concurrent corruption.

## Command Dispatch Extensions

The existing command dispatcher from `p0-companion-minimal` is extended with new command handlers:

| Command | Handler | Module |
|---------|---------|--------|
| `list_sessions` | `handleListSessions()` | `session-discovery.ts` |
| `resume_session` | `handleResumeSession()` | `process-manager.ts` + `cli-adapter.ts` |
| `send_message` | `handleSendMessage()` | `process-manager.ts` |
| `cancel_session` | `handleCancelSession()` | `process-manager.ts` |
| `add_root` | `handleAddRoot()` | `config-manager.ts` |
| `remove_root` | `handleRemoveRoot()` | `config-manager.ts` |

## Book Provider Logic

### Binary Selection

```typescript
function getBinary(provider: 'claude' | 'book', config: CompanionConfig): string {
  return config.binaries[provider] ?? (provider === 'book' ? 'book' : 'claude');
}
```

### Directory Restriction

```typescript
function isAllowedForBook(cwd: string, config: CompanionConfig): boolean {
  const bookRoots = config.workspace_roots.filter(r => r.provider === 'book');
  return bookRoots.some(root => cwd.startsWith(root.path));
}
```

Enforced at:
1. `create_session` with `provider: "book"` — reject if cwd not under book root.
2. `resume_session` with `provider: "book"` — reject if cwd not under book root.

### Heartbeat Provider Detection

On companion startup, check binary availability:

```typescript
function detectProviders(config: CompanionConfig): string[] {
  const providers = ['claude']; // claude always expected
  try {
    execSync(`which ${config.binaries.book ?? 'book'}`, { stdio: 'ignore' });
    providers.push('book');
  } catch {
    logger.warn('Book binary not found, book provider unavailable');
  }
  return providers;
}
```

## File Layout

```
packages/companion/src/
├── index.ts                  // entry point (unchanged)
├── ws-client.ts              // relay WSS client (from p0)
├── command-dispatcher.ts     // extended with new handlers
├── cli-adapter.ts            // CLI spawn + stream parse (from p0, extended)
├── process-manager.ts        // NEW: running process map
├── session-discovery.ts      // NEW: ~/.claude/projects/ scanner
├── config-manager.ts         // NEW: companion.json CRUD
└── types.ts                  // local types
```
