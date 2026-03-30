# API Specification

> This document describes the target relay API surface across the full delivery plan. The currently implemented slice for any milestone is defined by the active OpenSpec change and its task list.

## Authentication

所有请求必须携带 `Authorization: Bearer <RELAY_STATIC_TOKEN>`。
缺失或无效时返回 `401 { "error": "unauthenticated" }`。

---

## REST Endpoints

### GET /v1/hosts

列出所有已注册 host。

**Response 200**:
```json
{
  "hosts": [
    {
      "id": "macbook-1",
      "name": "MacBook Pro",
      "type": "macbook",
      "status": "online",
      "last_heartbeat_at": "2026-03-28T13:00:00Z",
      "providers": ["claude", "book"]
    },
    {
      "id": "relay-local",
      "name": "Relay VPS",
      "type": "relay_local",
      "status": "online",
      "last_heartbeat_at": null,
      "providers": ["openclaw"]
    }
  ]
}
```

### GET /v1/hosts/:hostId/roots

列出 host 的根目录。

**Response 200**:
```json
{
  "roots": [
    {
      "id": "uuid-1",
      "host_id": "macbook-1",
      "provider": "claude",
      "path": "/Users/danker/Desktop/AI-vault",
      "label": "AI-vault",
      "created_at": "2026-03-28T10:00:00Z"
    },
    {
      "id": "uuid-2",
      "host_id": "macbook-1",
      "provider": "book",
      "path": "/Users/danker/Desktop/novel",
      "label": "novel",
      "created_at": "2026-03-28T10:00:00Z"
    }
  ]
}
```

### POST /v1/hosts/:hostId/roots

添加根目录。

**Request**:
```json
{
  "provider": "claude",
  "path": "/Users/danker/Projects",
  "label": "Projects"
}
```

**Response 201**:
```json
{
  "root": { "id": "uuid-3", "host_id": "macbook-1", "provider": "claude", "path": "/Users/danker/Projects", "label": "Projects", "created_at": "..." }
}
```

**Errors**:
- `400 invalid_request`: path 为空或格式错误。
- `404 not_found`: hostId 不存在。
- `409 state_conflict`: 同一 host+provider+path 已存在。
- `502 host_offline`: companion 离线，无法验证路径（仅 macbook host）。

### DELETE /v1/hosts/:hostId/roots/:rootId

移除根目录。不影响已有 session。

**Response 204**: 无 body。
**Errors**: `404 not_found`。

### GET /v1/hosts/:hostId/browse

浏览目录，返回子目录列表。

**Query params**: `path=/Users/danker/Desktop/AI-vault`

**Response 200**:
```json
{
  "path": "/Users/danker/Desktop/AI-vault",
  "directories": [
    { "name": "IMbot", "path": "/Users/danker/Desktop/AI-vault/IMbot" },
    { "name": "projects", "path": "/Users/danker/Desktop/AI-vault/projects" }
  ]
}
```

**Errors**:
- `400 invalid_request`: path 为空。
- `403 forbidden`: path 不在任何 workspace root 下。
- `404 not_found`: 目录不存在。
- `502 host_offline`: companion 离线。

### GET /v1/sessions

会话列表。

**Query params**:
- `provider`: 可选过滤（`claude`, `book`, `openclaw`）。
- `status`: 可选过滤。
- `host_id`: 可选过滤。
- `limit`: 默认 50，最大 200。
- `offset`: 默认 0。

**Response 200**:
```json
{
  "sessions": [
    {
      "id": "sess-uuid-1",
      "provider": "claude",
      "host_id": "macbook-1",
      "workspace_cwd": "/Users/danker/Desktop/AI-vault/IMbot",
      "initial_prompt": "帮我看一下这个项目的架构",
      "model": "opus",
      "status": "running",
      "created_at": "2026-03-28T13:00:00Z",
      "updated_at": "2026-03-28T13:05:00Z",
      "last_active_at": "2026-03-28T13:05:00Z"
    }
  ],
  "total": 12,
  "limit": 50,
  "offset": 0
}
```

### GET /v1/sessions/:id

会话详情。

**Response 200**: 单个 session 对象（同上 + `provider_session_id`, `permission_mode`, `error_message`, `error_code`）。

**Errors**: `404 not_found`。

### POST /v1/sessions

创建会话。

**Request**:
```json
{
  "provider": "claude",
  "host_id": "macbook-1",
  "cwd": "/Users/danker/Desktop/AI-vault/IMbot",
  "prompt": "帮我看一下这个项目的架构",
  "model": "opus",
  "permission_mode": "bypassPermissions"
}
```

**Response 201**:
```json
{
  "session": {
    "id": "sess-uuid-new",
    "provider": "claude",
    "host_id": "macbook-1",
    "workspace_cwd": "/Users/danker/Desktop/AI-vault/IMbot",
    "initial_prompt": "帮我看一下这个项目的架构",
    "model": "opus",
    "status": "queued",
    "created_at": "2026-03-28T13:10:00Z"
  }
}
```

**Side effects**: 立即向 companion（或 OpenClaw bridge）发送 `create_session` 命令。

**Errors**:
- `400 invalid_request`: 缺少必填字段。
- `502 host_offline`: target host 不在线。
- `502 provider_unreachable`: OpenClaw gateway 不可用。

### POST /v1/sessions/:id/resume

恢复会话。

**Request**: `{}`（无 body，session 信息从 DB 读取）

**Response 200**: session 对象（status 变为 `running`）。

**Errors**:
- `404 not_found`。
- `409 state_conflict`: session 已在 running 状态。
- `502 host_offline`。

### POST /v1/sessions/:id/message

继续对话，发送新消息。

**Request**:
```json
{ "text": "接下来帮我重构这个模块" }
```

**Response 200**: `{ "ok": true }`

**Errors**:
- `404 not_found`。
- `409 state_conflict`: session 不在 running 状态。
- `502 host_offline`。

### POST /v1/sessions/:id/cancel

取消正在运行的会话。

**Response 200**: session 对象（status 变为 `cancelled`）。

**Errors**: `404`, `409` (session 不在 running/queued 状态)。

### DELETE /v1/sessions/:id

归档/删除会话及其所有 events。

**Response 204**: 无 body。
**Errors**:
- `404`。
- `409 state_conflict`: session 仍处于 `queued` 或 `running`，必须先进入终态后再删除。

### GET /v1/sessions/:id/events

补拉事件。

**Query params**:
- `since_seq`: 必填，返回 seq > since_seq 的事件。
- `limit`: 可选，默认 500。

**Response 200**:
```json
{
  "events": [
    {
      "id": "evt-uuid",
      "session_id": "sess-uuid",
      "seq": 184,
      "type": "assistant_delta",
      "payload": { "text": "让我来看看..." },
      "created_at": "2026-03-28T13:05:01Z"
    }
  ],
  "has_more": false
}
```

### POST /v1/push/register

注册 FCM token。

**Request**: `{ "fcm_token": "..." }`
**Response 200**: `{ "ok": true }`

### GET /healthz

**Response 200**: `{ "status": "ok", "uptime": 86400, "db": "ok", "companion": "online", "openclaw": "online" }`

---

## WebSocket Protocol

### Endpoint

`wss://{relay}/v1/ws?token=<STATIC_TOKEN>`

或首条消息认证：`{ "action": "auth", "token": "<STATIC_TOKEN>" }`

### Server → Client Messages

```typescript
// 统一信封
type ServerMessage =
  | { type: 'event'; session_id: string; seq: number; event_type: EventType; payload: any; timestamp: string }
  | { type: 'status'; session_id: string; status: SessionStatus }
  | { type: 'host_status'; host_id: string; status: 'online' | 'offline' }
  | { type: 'error'; code: string; message: string }
  | { type: 'pong' };
```

### Client → Server Messages

```typescript
type ClientMessage =
  | { action: 'auth'; token: string }
  | { action: 'subscribe'; session_id: string }
  | { action: 'unsubscribe'; session_id: string }
  | { action: 'ping' };
```

### Connection Lifecycle

1. 连接建立 → 发送 `auth`（如未用 query param）。
2. 认证通过 → 服务端无响应（静默成功），失败 → `{ type: 'error', code: 'unauthenticated' }` + 关闭。
3. 客户端发送 `subscribe` → 开始接收对应 session 的 events。
4. 客户端发送 `ping` → 服务端回 `pong`。
5. 服务端每 30s 发送 `ping` frame（WebSocket 协议层），客户端自动回 `pong`。
6. 60s 无活动 → 服务端关闭连接。

### Multi-Session Subscription

Android 可同时 subscribe 多个 session。每个 event 消息含 `session_id`，Android 端按 session 分发。

---

## Companion ↔ Relay Internal Protocol

### Transport

Companion 出站连接：`wss://{relay}/v1/companion?token=<TOKEN>&host_id=<HOST_ID>`

### Relay → Companion Commands

```typescript
type CompanionCommand =
  | { cmd: 'create_session'; req_id: string; session_id: string; provider: 'claude' | 'book'; cwd: string; prompt: string; model?: string; permission_mode: string }
  | { cmd: 'resume_session'; req_id: string; session_id: string; provider_session_id: string; cwd: string }
  | { cmd: 'send_message'; req_id: string; session_id: string; text: string }
  | { cmd: 'cancel_session'; req_id: string; session_id: string }
  | { cmd: 'list_sessions'; req_id: string; cwd: string; provider: 'claude' | 'book' }
  | { cmd: 'browse_directory'; req_id: string; path: string };
```

### Companion → Relay Messages

```typescript
type CompanionMessage =
  | { type: 'ack'; req_id: string; status: 'ok'; data?: any }
  | { type: 'ack'; req_id: string; status: 'error'; error_code: string; message: string }
  | { type: 'event'; session_id: string; event_type: EventType; payload: any }
  | { type: 'heartbeat'; host_id: string; providers: string[]; uptime: number };
```

### Command/Ack 关联

- 每个 command 含 `req_id`（uuid）。
- Companion 返回 `ack` 时必须包含对应 `req_id`。
- relay 端对每个 `req_id` 设置 30s 超时。超时视为 `error`。

### Heartbeat

- Companion 每 30s 发送 `heartbeat`。
- relay 收到后更新 `hosts.last_heartbeat_at` 和 `hosts.status = 'online'`。
- relay 每 60s 检查 heartbeat 时间戳，超过 90s 未收到则标记 `offline` 并广播。

---

## Error Codes

| Code | HTTP | Description |
|------|------|-------------|
| `unauthenticated` | 401 | token 无效或缺失 |
| `forbidden` | 403 | 路径不在允许范围内 |
| `not_found` | 404 | 资源不存在 |
| `invalid_request` | 400 | 参数缺失或格式错误 |
| `state_conflict` | 409 | 操作与当前状态冲突 |
| `host_offline` | 502 | 目标 host companion 离线 |
| `provider_unreachable` | 502 | OpenClaw gateway 或 Claude upstream 不可用 |
| `directory_not_found` | 400 | 目录不存在 |
| `session_not_resumable` | 409 | session 无法恢复（状态不允许） |
| `command_timeout` | 504 | companion 命令超时 |
| `seq_gap_detected` | 500 | 事件序号出现断裂 |
