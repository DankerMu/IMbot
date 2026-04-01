# Business Logic

## 1. Session Lifecycle State Machine

### Transition Table

| Current State | Event | New State | Guard | Side Effects |
|---------------|-------|-----------|-------|-------------|
| — | `POST /sessions` | `queued` | host online + provider available | 插入 DB, 发送 command |
| `queued` | companion ack ok | `running` | — | emit `session_started`, 更新 DB |
| `queued` | companion ack error | `failed` | — | emit `session_error`, FCM push |
| `queued` | 30s timeout | `failed` | — | emit `session_error` (timeout), FCM push |
| `running` | CLI turn 完成（进程仍存活） | `idle` | — | emit `session_idle` event |
| `running` | runtime 正常结束 | `completed` | — | emit `session_result`, FCM push |
| `running` | runtime error | `failed` | — | emit `session_error`, FCM push |
| `running` | `POST /cancel` | `cancelled` / provider terminal | — | send cancel command; if provider terminal event wins the race, preserve provider terminal state |
| `running` | companion 断开 | `failed` | — | emit `session_error` (host_disconnected) |
| `idle` | `POST /message` | `running` | — | 通过 stdin 发送 JSON 消息，transition → running |
| `idle` | `POST /complete` | `completed` | — | send complete_session → SIGTERM → emit session_result |
| `idle` | `POST /cancel` | `cancelled` | — | send cancel command |
| `idle` | idle timeout (30min) | `completed` | — | SIGTERM process, emit session_result |
| `idle` | companion 断开 | `failed` | — | emit `session_error` (companion_restart) |
| `completed` | `POST /resume` | `running` | host online | 发送 resume command（进程已死，重新 spawn） |
| `failed` | `POST /resume` | `running` | host online + error 可恢复 | 发送 resume command |
| `cancelled` | `POST /resume` | `running` | host online + `provider_session_id` 仍存在 | 发送 resume command |
| `*` (inactive 30d) | purge job | 删除 | — | CASCADE 删除 events |

> **Note**: `idle` 表示 CLI 进程存活但当前 turn 已完成，等待下一条用户消息。与 `completed`（进程已退出）不同。`cancelled` 表示当前本地进程已被用户停止，但如果 `provider_session_id` 仍保留，仍可重新 `resume`。Companion 使用 `--input-format stream-json --output-format stream-json` 模式实现持久双向交互。

### Implementation Pseudocode

Cancel 特殊规则：
- relay 在 `POST /cancel` 窗口内仍然接受 provider 终态事件。
- 如果 provider 在 cancel ack 完成前先发送 `session_result` / `session_error`，则 provider 终态胜出，API 返回该终态 session。
- 只有 relay 实际把 session 转成 `cancelled` 时，才写入 `session.cancel` audit。

```typescript
async function transitionSession(sessionId: string, newStatus: SessionStatus, context?: any) {
  const session = db.getSession(sessionId);
  if (!session) throw NotFound;

  // Validate transition
  const allowed = TRANSITIONS[session.status];
  if (!allowed?.includes(newStatus)) throw StateConflict;

  // Update
  db.updateSession(sessionId, {
    status: newStatus,
    updated_at: now(),
    last_active_at: now(),
    error_message: context?.error_message,
    error_code: context?.error_code,
  });

  // Emit status event
  const seq = allocateSeq(sessionId);
  db.insertEvent({
    id: uuid(),
    session_id: sessionId,
    seq,
    type: 'session_status_changed',
    payload: { status: newStatus, ...context },
  });

  // Broadcast
  wsHub.broadcastToSession(sessionId, { type: 'status', session_id: sessionId, status: newStatus });

  // FCM push for terminal states
  if (['completed', 'failed'].includes(newStatus)) {
    pushAdapter.notify(sessionId, newStatus, context?.error_message);
  }
}
```

## 2. Seq Allocation

```typescript
// 原子操作：读取当前 max seq + 1
function allocateSeq(sessionId: string): number {
  // SQLite 单写者模型保证原子性
  const result = db.prepare(
    `SELECT COALESCE(MAX(seq), 0) + 1 as next_seq
     FROM session_events WHERE session_id = ?`
  ).get(sessionId);
  return result.next_seq;
}
```

保证：
- 同一 session 的 seq 从 1 开始单调递增。
- relay 是唯一 seq 分配者。
- SQLite 的 WAL 模式下单写者保证无冲突。

## 3. Event Processing Pipeline

```
Companion/OpenClaw event arrives
        │
        ▼
  ┌─────────────┐
  │ Validate     │ ─── session exists? session running?
  └──────┬──────┘
         │
         ▼
  ┌─────────────┐
  │ Allocate seq │ ─── MAX(seq) + 1
  └──────┬──────┘
         │
         ▼
  ┌─────────────┐
  │ Store event  │ ─── INSERT INTO session_events
  └──────┬──────┘
         │
         ▼
  ┌─────────────┐
  │ Update       │ ─── sessions.last_active_at = now()
  │ session      │
  └──────┬──────┘
         │
         ▼
  ┌─────────────┐
  │ Broadcast    │ ─── WsHub.broadcastToSession()
  └──────┬──────┘
         │
         ▼
  ┌─────────────┐
  │ Check if     │ ─── session_result/session_error → transition
  │ terminal     │
  └─────────────┘
```

## 4. Reconnect & Catch-up

### Android 端

```
WSS 断开
    │
    ▼
指数退避重连: 1s, 2s, 4s, 8s, 16s, 30s (max)
    │
    ▼
重连成功
    │
    ▼
对每个已订阅的 session:
    GET /v1/sessions/{id}/events?since_seq={lastKnownSeq}
    │
    ▼
按 seq 顺序合并到本地 Room cache
    │
    ▼
通知 ViewModel 刷新 UI
```

### Companion 端

```
WSS 断开
    │
    ▼
指数退避重连: 1s, 2s, 4s, 8s, 16s, 30s (max)
    │
    ▼
重连成功
    │
    ▼
发送 heartbeat (重新注册)
    │
    ▼
正在运行的 session 继续上报事件
    │
    ▼
断线期间的事件:
    - 如果 companion 本地有缓冲 → 重连后补发
    - 如果 companion 也断了（进程重启）→ session 已丢失 → 标记 failed
```

## 5. Create Session Flow (Detailed)

### Claude/book via Companion

```
Android                    Relay                     Companion              CLI Process
  │                         │                          │                      │
  │ POST /sessions          │                          │                      │
  │ {provider:"claude",     │                          │                      │
  │  cwd:"/path",           │                          │                      │
  │  prompt:"..."}          │                          │                      │
  │────────────────────────►│                          │                      │
  │                         │ INSERT session (queued)   │                      │
  │                         │                          │                      │
  │                         │ cmd:create_session        │                      │
  │                         │─────────────────────────►│                      │
  │                         │                          │ spawn claude -p       │
  │                         │                          │ --input-format        │
  │                         │                          │ stream-json           │
  │                         │                          │ --output-format       │
  │                         │                          │ stream-json           │
  │                         │                          │ (prompt via stdin)    │
  │                         │                          │─────────────────────►│
  │                         │                          │                      │
  │                         │ ack:ok                    │                      │
  │                         │ {provider_session_id}    │                      │
  │◄────────────────────────│◄─────────────────────────│                      │
  │ 201 {session}           │                          │                      │
  │                         │ transition → running      │                      │
  │                         │                          │                      │
  │ ◄──── WS: event         │◄── event: session_started│                      │
  │ ◄──── WS: event         │◄── event: assistant_delta│◄─────────────────────│
  │ ◄──── WS: event         │◄── event: assistant_delta│◄─────────────────────│
  │ ...                     │                          │                      │
  │ ◄──── WS: event         │◄── event: session_idle   │◄── CLI turn done     │
  │                         │ transition → idle         │    (process alive)   │
  │                         │                          │                      │
  │ POST /message {text}    │                          │                      │
  │────────────────────────►│ transition → running      │                      │
  │                         │ cmd:send_message          │                      │
  │                         │─────────────────────────►│ stdin JSON message   │
  │                         │                          │─────────────────────►│
  │ ◄──── WS: event         │◄── event: assistant_delta│◄─────────────────────│
  │ ...                     │                          │                      │
  │ ◄──── WS: event         │◄── event: session_idle   │◄── turn done again  │
  │                         │                          │                      │
  │ POST /complete          │                          │                      │
  │────────────────────────►│ cmd:complete_session      │                      │
  │                         │─────────────────────────►│ SIGTERM → exit       │
  │ ◄──── WS: event         │◄── event: session_result │◄─────────────────────│
  │                         │ transition → completed    │                      │
  │ ◄──── WS: status        │                          │                      │
  │ ◄──── FCM push          │                          │                      │
```

### OpenClaw via Bridge

```
Android                    Relay                     OpenClaw GW
  │                         │                          │
  │ POST /sessions          │                          │
  │ {provider:"openclaw"}   │                          │
  │────────────────────────►│                          │
  │                         │ INSERT session (queued)   │
  │                         │                          │
  │                         │ bridge.createSession()    │
  │                         │─────────────────────────►│
  │                         │                          │
  │                         │ ack                       │
  │                         │◄─────────────────────────│
  │◄────────────────────────│                          │
  │ 201                     │ transition → running      │
  │                         │                          │
  │ ◄──── WS events         │◄── translated events     │
  │ ...                     │                          │
```

## 6. OpenClaw Event Translation

| OpenClaw Event | → Relay Event | Notes |
|---------------|---------------|-------|
| `transcript.text` (agent) | `assistant_delta` | 逐块追加 |
| `transcript.text` (complete) | `assistant_message` | 完整消息 |
| `tool.start` | `tool_call_started` | 工具调用开始 |
| `tool.end` | `tool_call_completed` | 工具调用完成 |
| `session.ready` | `session_started` | 会话就绪 |
| `session.complete` | `session_result` | 会话完成 |
| `session.error` | `session_error` | 错误 |
| `message.user` | `user_message` | 用户消息回显 |

具体映射需在 Phase 0 验证时对照 OpenClaw gateway 实际事件格式调整。

## 7. FCM Push Logic

```typescript
async function notifyPush(sessionId: string, status: string, errorMessage?: string) {
  const session = db.getSession(sessionId);
  const subs = db.getAllPushSubscriptions();
  if (subs.length === 0) return;

  const title = status === 'completed'
    ? `✓ ${truncate(session.initial_prompt, 30)} 已完成`
    : `✗ ${truncate(session.initial_prompt, 30)} 失败`;

  const body = status === 'failed' ? (errorMessage || '未知错误') : '';

  for (const sub of subs) {
    await fcm.send({
      token: sub.fcm_token,
      notification: { title, body },
      data: { session_id: sessionId, action: 'open_session' },
    });
  }
}
```

## 8. 30-Day Purge Job

```typescript
// 每天 UTC 03:00 执行
cron.schedule('0 3 * * *', () => {
  const cutoff = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString();
  const result = db.prepare(
    `DELETE FROM sessions
     WHERE status IN ('completed', 'failed', 'cancelled')
       AND last_active_at < ?`
  ).run(cutoff);
  console.log(`Purged ${result.changes} inactive sessions`);
});
```

## 9. Directory Security Validation

Phase 1 目录安全校验的策略边界如下：

- relay 是 browse allowlist 的执行点：先拒绝任何包含 `..` 的 path，再在本地读取或 companion 代理前，用 root allowlist 校验请求 path；受控 macOS 别名等价只用于 `macbook` host，或运行在 macOS 上的 `relay-local`。
- relay 在本地读取或 companion 返回后，会对 canonical path 和返回的子目录 path 再次校验其仍然落在已登记 workspace roots 之下。
- 如果一次成功 browse 命中的正是某个 legacy root 本身，而 canonical 返回路径与存储值不同，relay 会把该 root 持久化升级为 canonical path。
- companion 当前对 `browse_directory` 负责本机文件系统校验：path 必须为绝对路径、目标必须存在且是目录、响应只返回 canonical 目录路径下的子目录。
- 当 relay 为 `macbook` browse 显式传入 roots 时，companion 也会在 canonical 化后执行一次 root allowlist 校验，并在列目录前拒绝 symlink/canonical escape。

```typescript
function validateBrowsePathAtRelay(requestedPath: string, roots: WorkspaceRoot[]): boolean {
  if (requestedPath.split(/[\\/]+/).includes('..')) return false;
  return roots.some(root => isPathWithinRootWithControlledAliases(requestedPath, root.path));
}

async function canonicalBrowseResultStillUnderRoots(
  canonicalPath: string,
  roots: WorkspaceRoot[]
): Promise<boolean> {
  return validateBrowsePathAtRelay(canonicalPath, roots);
}

async function companionBrowseWithRoots(
  requestedPath: string,
  roots: WorkspaceRoot[]
): Promise<BrowseDirectoryResult> {
  return companion.browseDirectory(requestedPath, {
    allowedRoots: roots.map(root => root.path)
  });
}
```
