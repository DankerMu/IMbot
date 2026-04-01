# Data Model

## Storage: SQLite (relay) + Room/SQLite (Android)

relay 使用 `better-sqlite3`（同步、零配置、单文件）。Android 使用 Room。

## Relay SQL Schema

### hosts

```sql
CREATE TABLE hosts (
  id          TEXT PRIMARY KEY,              -- e.g. "macbook-1", "relay-local"
  name        TEXT NOT NULL,                 -- display name: "MacBook Pro"
  type        TEXT NOT NULL CHECK (type IN ('macbook', 'relay_local')),
  status      TEXT NOT NULL DEFAULT 'offline' CHECK (status IN ('online', 'offline')),
  last_heartbeat_at TEXT,                    -- ISO 8601
  created_at  TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

-- relay 启动时自动 upsert relay-local host
INSERT OR IGNORE INTO hosts (id, name, type, status)
VALUES ('relay-local', 'Relay VPS', 'relay_local', 'online');
```

### workspace_roots

```sql
CREATE TABLE workspace_roots (
  id          TEXT PRIMARY KEY,              -- uuid
  host_id     TEXT NOT NULL REFERENCES hosts(id),
  provider    TEXT NOT NULL CHECK (provider IN ('claude', 'book', 'openclaw')),
  path        TEXT NOT NULL,                 -- absolute path on host
  label       TEXT,                          -- user display name
  created_at  TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE (host_id, provider, path)
);

CREATE INDEX idx_roots_host ON workspace_roots(host_id);
CREATE INDEX idx_roots_provider ON workspace_roots(provider);
```

### sessions

```sql
CREATE TABLE sessions (
  id                   TEXT PRIMARY KEY,     -- uuid
  provider             TEXT NOT NULL CHECK (provider IN ('claude', 'book', 'openclaw')),
  provider_session_id  TEXT,                 -- runtime-level session id
  host_id              TEXT NOT NULL REFERENCES hosts(id),
  workspace_root       TEXT,                 -- root path
  workspace_cwd        TEXT NOT NULL,        -- actual working directory
  initial_prompt       TEXT,                 -- first user message (for summary)
  model                TEXT,                 -- e.g. "opus", "sonnet"
  permission_mode      TEXT NOT NULL DEFAULT 'bypassPermissions',
  status               TEXT NOT NULL DEFAULT 'queued'
                       CHECK (status IN ('queued', 'running', 'idle', 'completed', 'failed', 'cancelled')),
  error_message        TEXT,                 -- populated on failure
  error_code           TEXT,                 -- machine-readable error
  created_at           TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at           TEXT NOT NULL DEFAULT (datetime('now')),
  last_active_at       TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_sessions_provider ON sessions(provider);
CREATE INDEX idx_sessions_status ON sessions(status);
CREATE INDEX idx_sessions_host ON sessions(host_id);
CREATE INDEX idx_sessions_cwd ON sessions(workspace_cwd);
CREATE INDEX idx_sessions_last_active ON sessions(last_active_at);
```

### session_events

```sql
CREATE TABLE session_events (
  id          TEXT PRIMARY KEY,              -- uuid
  session_id  TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  seq         INTEGER NOT NULL,              -- per-session monotonic, starts at 1
  type        TEXT NOT NULL,                 -- event type enum
  payload     TEXT NOT NULL DEFAULT '{}',    -- JSON string
  created_at  TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE (session_id, seq)
);

CREATE INDEX idx_events_session_seq ON session_events(session_id, seq);
```

### approvals (保留，MVP 不主动使用)

```sql
CREATE TABLE approvals (
  id          TEXT PRIMARY KEY,              -- uuid
  session_id  TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  tool_name   TEXT NOT NULL,
  tool_input  TEXT,                          -- JSON
  status      TEXT NOT NULL DEFAULT 'pending'
              CHECK (status IN ('pending', 'approved', 'denied', 'expired')),
  decision_at TEXT,
  expires_at  TEXT,
  created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_approvals_session ON approvals(session_id);
CREATE INDEX idx_approvals_status ON approvals(status);
```

### push_subscriptions

```sql
CREATE TABLE push_subscriptions (
  id          TEXT PRIMARY KEY,              -- uuid
  fcm_token   TEXT NOT NULL UNIQUE,
  created_at  TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at  TEXT NOT NULL DEFAULT (datetime('now'))
);
```

### audit_logs (保留，MVP 最小写入)

```sql
CREATE TABLE audit_logs (
  id          TEXT PRIMARY KEY,
  action      TEXT NOT NULL,                 -- 'session.create', 'session.cancel', etc.
  session_id  TEXT,
  host_id     TEXT,
  detail      TEXT,                          -- JSON
  created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_audit_created ON audit_logs(created_at);
```

## Event Type Enum

```typescript
// packages/wire/src/events.ts
export type EventType =
  | 'session_started'
  | 'assistant_delta'
  | 'assistant_message'
  | 'tool_call_started'
  | 'tool_call_completed'
  | 'approval_required'     // 保留
  | 'approval_resolved'     // 保留
  | 'session_idle'            // CLI turn 完成，进程存活等待输入
  | 'session_status_changed'
  | 'session_result'
  | 'session_error'
  | 'user_message';         // 用户发送的消息回显
```

## Session Status Transitions

```
queued ──────► running ◄─────► idle
  │              │               │
  │              ├──────────► completed ◄── idle (timeout/complete)
  │              │               │
  │              ├──────────► failed ◄──── idle (crash)
  │              │
  │              └──────────► cancelled
  │
  └─(host offline / timeout)─► failed

completed ──(resume)──► running
failed ────(resume)──► running
```

**Transition rules**:

| From | To | Trigger | Side Effects |
|------|----|---------|-------------|
| `queued` | `running` | companion ack 成功 | emit `session_started` event |
| `queued` | `failed` | companion ack 失败 / timeout 30s | emit `session_error` event, FCM push |
| `running` | `idle` | CLI turn 完成，进程存活 | emit `session_idle` event |
| `running` | `completed` | runtime 正常结束（进程退出） | emit `session_result` event, FCM push |
| `running` | `failed` | runtime error / upstream error | emit `session_error` event, FCM push |
| `running` | `cancelled` / provider terminal | 用户取消；若 provider 先结束，保留 provider 终态 | send cancel command |
| `idle` | `running` | `POST /message` | 通过 stdin JSON 发送消息 |
| `idle` | `completed` | `POST /complete` 或 idle timeout (30min) | SIGTERM → emit `session_result` |
| `idle` | `failed` | companion 断开 | emit `session_error` (companion_restart) |
| `idle` | `cancelled` | `POST /cancel` | send cancel command |
| `completed` | `running` | `POST /resume` | 重新 spawn 进程 |
| `failed` | `running` | `POST /resume` | 重新 spawn 进程 |

> **idle vs completed**: `idle` = 进程存活，等待下一条消息（stream-json 模式）。`completed` = 进程已退出。`idle` 下发消息 ~2s 响应；`completed` 下 resume 需要重新 spawn（~9-13s）。

## Seq Allocation

- `seq` 从 1 开始，每个 session 独立。
- relay 负责分配 `seq`（不由 companion 分配），保证单调递增。
- companion 上报的 event 不含 `seq`，relay 收到后分配 `seq` 再存储和广播。

## 30-Day Purge Logic

```sql
-- 每天运行一次（node-cron: '0 3 * * *'）
DELETE FROM sessions
WHERE status IN ('completed', 'failed', 'cancelled')
  AND last_active_at < datetime('now', '-30 days');

-- session_events 通过 ON DELETE CASCADE 自动清理
```

## Android Room Schema (Mirror)

Android 本地缓存 relay 数据的子集：

```kotlin
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val provider: String,
    val providerSessionId: String?,
    val hostId: String,
    val workspaceCwd: String,
    val initialPrompt: String?,
    val model: String?,
    val status: String,
    val errorMessage: String?,
    val createdAt: String,
    val updatedAt: String,
    val lastActiveAt: String
)

@Entity(
    tableName = "session_events",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId", "seq")]
)
data class SessionEventEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val seq: Int,
    val type: String,
    val payload: String,    // JSON string
    val createdAt: String
)
```

Room 缓存策略：
- 打开 session detail 时拉取该 session 的所有 events 并缓存。
- session list 页仅缓存 session summary（不含 events）。
- App 启动时从 relay 全量刷新 session list。
- 离线时从本地缓存读取。
