# Business Logic

## 1. Session Lifecycle State Machine

### Transition Table

| Current State | Event | New State | Guard | Side Effects |
|---------------|-------|-----------|-------|-------------|
| — | `POST /sessions` | `queued` | host online + provider available | 插入 DB, 发送 command |
| `queued` | companion ack ok | `running` | — | emit `session_started`, 更新 DB |
| `queued` | companion ack error | `failed` | — | emit `session_error`, FCM push |
| `queued` | 30s timeout | `failed` | — | emit `session_error` (timeout), FCM push |
| `running` | runtime 正常结束 | `completed` | — | emit `session_result`, FCM push |
| `running` | runtime error | `failed` | — | emit `session_error`, FCM push |
| `running` | `POST /cancel` | `cancelled` / provider terminal (`completed` or `failed`) | — | send cancel command; if provider terminal event wins the race, preserve provider terminal state |
| `running` | companion 断开 | `failed` | — | emit `session_error` (host_disconnected) |
| `completed` | `POST /resume` | `running` | host online | 发送 resume command |
| `failed` | `POST /resume` | `running` | host online + error 可恢复 | 发送 resume command |
| `cancelled` | — | — | 终态，不可转换 | — |
| `*` (inactive 30d) | purge job | 删除 | — | CASCADE 删除 events |

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
  │                         │                          │ spawn claude          │
  │                         │                          │ --output-format       │
  │                         │                          │ stream-json -p prompt │
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

Companion 在执行 `browse_directory` 和 `create_session` 时必须验证路径合法性：

```typescript
function validatePath(requestedPath: string, roots: WorkspaceRoot[]): boolean {
  const resolved = path.resolve(requestedPath);
  // 拒绝路径遍历
  if (resolved !== requestedPath && requestedPath.includes('..')) return false;
  // 必须在某个 root 下
  return roots.some(root => resolved.startsWith(root.path));
}
```
