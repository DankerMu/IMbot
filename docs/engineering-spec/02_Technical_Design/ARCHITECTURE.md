# Architecture

## Component Diagram

```
┌───────────────────────────────────────────────────────────┐
│                      Android App                           │
│                                                           │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────────┐   │
│  │  UI Layer    │  │  Data Layer   │  │  Service Layer  │   │
│  │ (Compose)    │  │ (Repository)  │  │ (FG + FCM)     │   │
│  │             │  │              │  │                │   │
│  │ HomeScreen  │◄─┤ SessionRepo  │◄─┤ SessionService │   │
│  │ DetailScreen│  │ WorkspaceRepo│  │ FCMService     │   │
│  │ NewSession  │  │ HostRepo     │  │                │   │
│  │ Workspace   │  │              │  │                │   │
│  │ Settings    │  │ Room DB      │  │ OkHttp WS      │   │
│  └──────┬──────┘  │ OkHttp REST  │  └────────────────┘   │
│         │         └──────┬───────┘                         │
│    ViewModel              │                                │
│    + StateFlow            │                                │
└───────────────────────────┼────────────────────────────────┘
                            │ HTTPS / WSS
                            ▼
┌───────────────────────────────────────────────────────────┐
│                      Relay Server                          │
│                                                           │
│  ┌──────────┐  ┌──────────────┐  ┌───────────────────┐   │
│  │ Fastify   │  │ WS Hub       │  │ Session           │   │
│  │ Routes    │  │              │  │ Orchestrator      │   │
│  │          │  │ Android conn │  │                   │   │
│  │ /v1/*    │──┤ Companion    │──┤ State machine     │   │
│  │ /healthz │  │ conn         │  │ Seq allocator     │   │
│  └──────────┘  └──────────────┘  │ Event fanout      │   │
│                                   └─────────┬─────────┘   │
│  ┌──────────┐  ┌──────────────┐            │              │
│  │ SQLite   │  │ FCM Adapter  │    ┌───────┴───────┐     │
│  │ (store)  │  │              │    │               │     │
│  └──────────┘  └──────────────┘    ▼               ▼     │
│                              ┌──────────┐  ┌───────────┐ │
│                              │Companion │  │ OpenClaw   │ │
│                              │ Manager  │  │ Bridge     │ │
│                              └─────┬────┘  └─────┬─────┘ │
└────────────────────────────────────┼─────────────┼────────┘
                                     │             │
                              outbound WSS    localhost WS
                                     │             │
                                     ▼             ▼
                        ┌─────────────────┐  ┌──────────────┐
                        │ MacBook         │  │ OpenClaw     │
                        │ Companion       │  │ Gateway      │
                        │                 │  │ :18789       │
                        │ ┌─────────────┐ │  └──────────────┘
                        │ │ Command     │ │
                        │ │ Dispatcher  │ │
                        │ ├─────────────┤ │
                        │ │ Workspace   │ │
                        │ │ Catalog     │ │
                        │ ├─────────────┤ │
                        │ │ Runtime     │ │
                        │ │ Adapter     │ │
                        │ │ (CLI)       │ │
                        │ └──────┬──────┘ │
                        │        │        │
                        │  claude/book    │
                        │  CLI process    │
                        │        │        │
                        │  Anthropic API  │
                        └─────────────────┘
```

## Relay Server Internal Modules

### Module: `routes/` — REST API

| File | Responsibility |
|------|---------------|
| `hosts.ts` | `GET /v1/hosts`, host/workspace CRUD |
| `sessions.ts` | Session lifecycle endpoints |
| `events.ts` | `GET /v1/sessions/:id/events` |
| `push.ts` | `POST /v1/push/register` |
| `health.ts` | `GET /healthz` |

所有 route 通过 Fastify `preHandler` 做 static token 验证。

### Module: `ws/` — WebSocket Hub

```typescript
// ws/hub.ts
export class WsHub {
  // Android client connections (one or few)
  private androidClients: Map<string, WebSocket>;
  // Companion connection (one per host)
  private companionClients: Map<string, WebSocket>;

  // Android subscribes to sessions
  private subscriptions: Map<string/*sessionId*/, Set<WebSocket>>;

  handleAndroidMessage(ws, msg): void;
  handleCompanionMessage(ws, msg): void;
  broadcastToSession(sessionId, event): void;
  broadcastHostStatus(hostId, status): void;
}
```

关键行为：
- Android 连接时验证 token（首条消息或 URL query）。
- Companion 连接时验证 token + 注册 host。
- Android 发送 `subscribe` 后加入 session 的广播组。
- Companion 上报 event 时，hub 分配 seq → 存库 → 广播给订阅者。

### Module: `session/` — Session Orchestrator

```typescript
// session/orchestrator.ts
export class SessionOrchestrator {
  create(params: CreateSessionParams): Promise<Session>;
  resume(sessionId: string): Promise<Session>;
  sendMessage(sessionId: string, text: string): Promise<void>;
  cancel(sessionId: string): Promise<void>;
  handleEvent(sessionId: string, event: RawEvent): Promise<StoredEvent>;
  transition(sessionId: string, newStatus: SessionStatus): Promise<void>;
}
```

关键行为：
- `create`: 插入 sessions 记录 → 判断 provider → 走 companion 或 OpenClaw bridge → 等 ack。
- `handleEvent`: 分配 seq → 插入 session_events → 广播 → 如果是终态 event 则 transition。
- `transition`: 更新 status → 广播 status event → 触发 FCM push（如需）。

### Module: `companion/` — Companion Connection Manager

```typescript
// companion/manager.ts
export class CompanionManager {
  private connections: Map<string/*hostId*/, WebSocket>;

  registerCompanion(ws: WebSocket, hostId: string): void;
  sendCommand(hostId: string, command: Command): Promise<Ack>;
  handleHeartbeat(hostId: string, data: HeartbeatData): void;
  isOnline(hostId: string): boolean;
}
```

关键行为：
- Companion 连接时注册并更新 host status 为 `online`。
- 断开时更新为 `offline` 并广播。
- `sendCommand` 返回 Promise，等待 companion 的 `ack` 消息（timeout 30s）。

### Module: `openclaw/` — OpenClaw Bridge

```typescript
// openclaw/bridge.ts
export class OpenClawBridge {
  private ws: WebSocket | null;
  private sessionMap: Map<string/*relaySessionId*/, string/*openclawSessionKey*/>;

  connect(): void;   // ws://localhost:18789
  createSession(relaySessionId: string, cwd: string, prompt: string): Promise<void>;
  resumeSession(relaySessionId: string, openclawSessionKey: string): Promise<void>;
  sendMessage(relaySessionId: string, text: string): Promise<void>;
  cancelSession(relaySessionId: string): Promise<void>;

  // 内部：OpenClaw 事件 → relay 统一事件
  private translateEvent(openclawEvent: any): SessionEvent;
}
```

关键行为：
- relay 启动时尝试连接 OpenClaw gateway，失败则标记 openclaw 为不可用。
- 定时重连（30s interval）。
- 所有 OpenClaw 事件翻译为 relay 统一事件模型后交给 SessionOrchestrator。

## MacBook Companion Internal Modules

### Module: `relay-client.ts` — WSS Client

- 连接 relay WSS endpoint。
- 自动重连（指数退避 1s-30s）。
- 发送 heartbeat（每 30s）。
- 接收 commands，dispatch 到对应 handler。

### Module: `workspace/` — Directory Catalog

```typescript
// workspace/catalog.ts
export class WorkspaceCatalog {
  listRoots(): WorkspaceRoot[];
  addRoot(provider: string, path: string, label?: string): WorkspaceRoot;
  removeRoot(rootId: string): void;
  browse(path: string): DirectoryEntry[];  // 返回子目录列表
}
```

- 根目录列表持久化在本地 JSON 配置文件中。
- `browse` 直接读取文件系统，只返回目录（过滤文件）。
- 路径安全校验：拒绝 `..` 路径遍历，拒绝不在任何 root 下的路径。

### Module: `runtime/` — Claude/book Adapter

```typescript
// runtime/claude-adapter.ts
export class ClaudeRuntimeAdapter {
  async createSession(params: {
    cwd: string;
    prompt: string;
    model?: string;
    permissionMode: string;
    provider: 'claude' | 'book';
  }): Promise<{ providerSessionId: string; eventStream: AsyncIterable<RawEvent> }>;
  // Spawns: claude --output-format stream-json --print-session-id -p "prompt" --model opus --permission-mode bypassPermissions
  // or: book --output-format stream-json ... (same flags)

  async resumeSession(params: {
    cwd: string;
    providerSessionId: string;
    provider: 'claude' | 'book';
  }): Promise<{ eventStream: AsyncIterable<RawEvent> }>;
  // Spawns: claude --resume --session-id <id> --output-format stream-json

  async sendMessage(providerSessionId: string, text: string): Promise<void>;
  // Writes to stdin of running CLI process

  async cancel(providerSessionId: string): Promise<void>;
  // Sends SIGINT to CLI process

  listSessions(cwd: string): LocalSession[];
  // Reads ~/.claude/projects/ directory
}
```

关键行为：
- 通过 spawn `claude` / `book` CLI 进程创建 session，使用 `--output-format stream-json --print-session-id -p "prompt" --permission-mode bypassPermissions` 参数。
- `provider: 'book'` 时 spawn `book` 二进制（配置路径），CLI 参数相同。
- `eventStream` 从 CLI 进程的 stdout 逐行解析 stream-json 事件，companion 逐事件转发给 relay。
- `sendMessage` 写入运行中 CLI 进程的 stdin。
- `cancel` 向 CLI 进程发送 SIGINT 信号。
- `listSessions` 读取本地 `~/.claude/projects/` 目录获取历史 session。

### Module: `session/` — Local Session Index

维护 `provider_session_id ↔ cwd ↔ relay_session_id` 的映射关系。
持久化为本地 JSON 文件，companion 重启后可恢复映射。

## Android App Layers

### UI Layer (Compose)

| Screen | ViewModel | Key State |
|--------|-----------|-----------|
| `HomeScreen` | `HomeViewModel` | `sessions: List<SessionSummary>`, `filter: Provider?` |
| `SessionDetailScreen` | `DetailViewModel` | `events: List<SessionEvent>`, `status: SessionStatus`, `isConnected: Boolean` |
| `NewSessionScreen` | `NewSessionViewModel` | `step: Int`, `provider: Provider?`, `cwd: String?`, `prompt: String` |
| `WorkspaceScreen` | `WorkspaceViewModel` | `roots: List<Root>`, `currentPath: String`, `dirs: List<Dir>` |
| `SettingsScreen` | `SettingsViewModel` | `theme: ThemeMode`, `relayUrl: String`, `connectionStatus` |

### Data Layer

```
SessionRepository
├── RemoteDataSource (OkHttp REST)
├── WsDataSource (OkHttp WebSocket)
└── LocalDataSource (Room DAO)

WorkspaceRepository
├── RemoteDataSource (REST /browse)
└── (no local cache for directory listings)

HostRepository
├── RemoteDataSource (REST /hosts)
└── LocalDataSource (Room)
```

### Service Layer

**SessionService (Foreground Service)**:
- 生命周期：用户打开 App 时 start，所有 session 完成且 App 退到后台 5 分钟后 stop。
- 持有 WebSocket 连接。
- 分发事件到 Repository → ViewModel → UI。
- 管理多 session 订阅。

**FCMService**:
- 接收 push → 显示通知。
- 点击通知 → deep link 到 session detail。
