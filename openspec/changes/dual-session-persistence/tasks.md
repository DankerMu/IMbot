# Tasks: dual-session-persistence

## Task 0: Companion — `CompanionProviderConfig` 增加 `configDir` + 自动检测

**文件**: `packages/companion/src/config.ts`

### 0.1 扩展 `CompanionProviderConfig` interface

```typescript
export interface CompanionProviderConfig {
  readonly binary: string;
  readonly configDir: string;        // 新增: CLI 配置目录 (e.g. ~/.claude, ~/.claudebook)
  readonly projectsDir: string;      // 新增: 推导自 configDir + "/projects"
}
```

### 0.2 解析 `config_dir` 配置

在 `parseProviders()` 中，对每个 provider：

1. 如果 `rawProviderConfig.config_dir` 是字符串 → 用 `expandUserPath()` 展开
2. 否则 → 调用 `detectConfigDir(binary)` 自动检测
3. 最终回退 → `~/.claude`

```typescript
function detectConfigDir(binaryPath: string, env: NodeJS.ProcessEnv): string {
  const defaultDir = path.join(os.homedir(), ".claude");
  try {
    const content = fs.readFileSync(binaryPath, "utf8");
    // 只读前 10 行
    const lines = content.split("\n").slice(0, 10);
    for (const line of lines) {
      // 匹配: export CLAUDE_CONFIG_DIR="..." 或 CLAUDE_CONFIG_DIR="..."
      const match = line.match(/CLAUDE_CONFIG_DIR=["']?([^"'\s]+)["']?/);
      if (match) {
        return expandUserPath(match[1].replace("$HOME", "~"), env);
      }
    }
  } catch {
    // binary 不可读或不是文本文件 → 回退
  }
  return defaultDir;
}
```

### 0.3 构造 `projectsDir`

```typescript
const configDir = resolveConfigDir(rawProviderConfig, configuredBinary, env);
providers[provider] = {
  binary: resolveProviderBinary(configuredBinary, env),
  configDir,
  projectsDir: path.join(configDir, "projects")
};
```

### 测试要求

扩展 `tests/unit/companion-core.test.mjs` 或新建 `tests/unit/companion-config-dir.test.mjs`：
- 验证显式 `config_dir` 配置被正确解析和展开（`~/` → absolute）
- 验证 book binary wrapper（含 `CLAUDE_CONFIG_DIR`）被自动检测
- 验证 binary 不是 shell script 时回退到 `~/.claude`
- 验证 binary 不可读时回退到 `~/.claude`
- 验证 claude provider 默认 `configDir` 为 `~/.claude`
- 验证 `projectsDir` 为 `configDir + "/projects"`
- 验证 `$HOME` 在 `CLAUDE_CONFIG_DIR` 中被正确替换

---

## Task 1: Wire — 新增类型和扩展 Session model

**文件**: `packages/wire/src/messages.ts`, `packages/wire/src/models.ts`, `packages/wire/src/index.ts`

### 1.1 新增 `CompanionReportLocalSessionsMessage` 类型

在 `packages/wire/src/messages.ts` 中：

```typescript
export type CompanionReportLocalSessionsMessage = {
  type: "report_local_sessions";
  host_id: string;
  sessions: Array<{
    provider_session_id: string;
    provider: "claude" | "book";
    cwd: string;
    created_at: string;
  }>;
};
```

将该类型加入 `CompanionMessage` union：

```typescript
export type CompanionMessage =
  | CompanionAckOk
  | CompanionAckError
  | CompanionEventMessage
  | CompanionHeartbeatMessage
  | CompanionReportLocalSessionsMessage;
```

### 1.2 扩展 `Session` interface

在 `packages/wire/src/models.ts` 中，`Session` interface 增加：

```typescript
local_available: boolean;
```

### 1.3 确保 re-export

`packages/wire/src/index.ts` 已有 `export * from "./messages"` 和 `export * from "./models"`，无需额外操作，但需验证新类型在 `npm run build` 后可被 relay 和 companion import。

### 测试要求

在 `tests/unit/wire.test.mjs` 中新增：
- 验证 `CompanionReportLocalSessionsMessage` 类型结构（构造一个符合类型的对象，断言字段存在）
- 验证 `Session` interface 包含 `local_available` 字段

---

## Task 2: Relay — 数据库 migration 增加 `local_available` 列

**文件**: `packages/relay/src/db/init.ts`

### 2.1 修改 `SCHEMA_SQL` 中的 `sessions` 表定义

在 `CREATE TABLE sessions` 中 `error_code TEXT,` 后面加：

```sql
local_available INTEGER NOT NULL DEFAULT 0,
```

### 2.2 修改 migration 中的 `sessions_new` 表

在 `migrateSchema()` 函数的 `CREATE TABLE sessions_new` 中同步添加 `local_available` 列。

### 2.3 新增 `local_available` migration 函数

新增一个 migration 函数 `migrateLocalAvailable(db)`：

```typescript
function migrateLocalAvailable(db: RelayDatabase): void {
  // 检查列是否已存在
  const columns = db.pragma("table_info(sessions)") as Array<{ name: string }>;
  if (columns.some(c => c.name === "local_available")) {
    return;
  }

  db.exec(`
    ALTER TABLE sessions ADD COLUMN local_available INTEGER NOT NULL DEFAULT 0;
    UPDATE sessions SET local_available = 1
    WHERE provider IN ('claude', 'book') AND provider_session_id IS NOT NULL;
  `);
}
```

在 `initializeDatabase()` 中调用：在 `migrateSchema(db)` 之后调用 `migrateLocalAvailable(db)`。

### 2.4 修改 `SessionOrchestrator.create()`

在 `packages/relay/src/session/orchestrator.ts` 中，session 对象增加 `local_available` 字段，初始值保持 `false`：

```typescript
const session: Session = {
  // ... 现有字段 ...
  local_available: false
};
```

INSERT 语句增加 `local_available` 列。`markSessionStarted()` 在 session 真正进入 `running` 时，才将 `claude` / `book` session 更新为 `local_available = 1`；`openclaw` 保持 `0`。

### 测试要求

在 `tests/unit/relay-bootstrap.test.mjs` 或新文件中：
- 验证全新数据库 sessions 表包含 `local_available` 列
- 验证旧数据库（无 `local_available` 列）migration 后该列存在
- 验证 migration 将已有 claude/book session（有 provider_session_id）设为 `local_available = 1`
- 验证 migration 幂等（第二次启动不会重复执行 backfill）
- 验证 openclaw session 默认 `local_available = 0`

---

## Task 3: Relay — 处理 `report_local_sessions` 消息

**文件**: `packages/relay/src/companion/manager.ts`（或 handler 所在文件），`packages/relay/src/session/orchestrator.ts`

### 3.1 在 companion WebSocket 消息路由中识别新消息类型

找到 companion WebSocket 消息处理逻辑（`CompanionManager` 或 `WsHub` 中），在 `type` 分支中增加：

```typescript
case "report_local_sessions":
  await this.handleReportLocalSessions(message);
  break;
```

### 3.2 实现 `handleReportLocalSessions`

```typescript
async handleReportLocalSessions(message: CompanionReportLocalSessionsMessage): Promise<void> {
  // 1. 验证 host_id 存在
  // 2. 在事务中处理每个 session：
  //    - 查询 sessions 表是否已有 provider_session_id 匹配的记录
  //    - 如果没有：INSERT 新记录（status=completed, local_available=1）
  //    - 如果有且 local_available=0：UPDATE 设为 local_available=1
  //    - 如果有且 local_available=1：跳过
  // 3. 记录审计日志
}
```

### 3.3 幂等插入逻辑

使用 SQLite 的 `INSERT ... WHERE NOT EXISTS` 模式：

```sql
INSERT INTO sessions (id, provider, provider_session_id, host_id, workspace_cwd, status, local_available, permission_mode, created_at, updated_at, last_active_at)
SELECT ?, ?, ?, ?, ?, 'completed', 1, 'bypassPermissions', ?, ?, ?
WHERE NOT EXISTS (
  SELECT 1 FROM sessions WHERE provider_session_id = ?
);

UPDATE sessions SET local_available = 1
WHERE provider_session_id = ? AND local_available = 0;
```

### 测试要求

新建 `tests/unit/relay-local-session-sync.test.mjs`：
- 验证未知 session 被创建为 shadow record（status=completed, local_available=true）
- 验证已知 session（provider_session_id 重复）不被重复创建
- 验证已知 session 的 local_available 从 false 更新为 true
- 验证空 provider_session_id 被跳过
- 验证不存在的 host_id 被拒绝
- 验证批量 50 个 session 在单事务中处理
- 验证 shadow session 可通过 `GET /sessions` 查询到
- 验证审计日志被写入

在 `tests/contract/relay-session-flow.test.mjs` 中新增：
- 验证 companion 发送 `report_local_sessions` 后 relay 正确创建 shadow records
- 验证 shadow records 与正常 sessions 在 `GET /sessions` 混合排序

---

## Task 4: Companion — 扩展 SessionIndex

**文件**: `packages/companion/src/runtime/session-index.ts`

### 4.1 扩展 `SessionIndexEntry` 类型

```typescript
export interface SessionIndexEntry {
  readonly provider_session_id: string;
  readonly cwd: string;
  readonly provider: InteractiveProvider;
  readonly created_at: string;
  readonly source?: "remote" | "local";       // 新增
  readonly initial_prompt?: string | null;     // 新增
}
```

### 4.2 修改 `normalizeEntry()` 函数

解析新字段，向后兼容：

```typescript
function normalizeEntry(value: unknown): SessionIndexEntry | null {
  // ... 现有逻辑 ...
  const source = record.source === "local" ? "local" : "remote";
  const initialPrompt = typeof record.initial_prompt === "string" ? record.initial_prompt : null;

  return {
    provider_session_id: providerSessionId,
    cwd,
    provider,
    created_at: createdAt,
    source,
    initial_prompt: initialPrompt
  };
}
```

### 4.3 新增 `hasProviderSessionId()` 方法

用于快速查询某个 provider_session_id 是否已在 index 中：

```typescript
hasProviderSessionId(providerSessionId: string): boolean {
  for (const entry of this.entries.values()) {
    if (entry.provider_session_id === providerSessionId) {
      return true;
    }
  }
  return false;
}
```

### 4.4 修改 `registerProviderSessionId()`

在 `packages/companion/src/runtime/claude-adapter.ts` 中，`registerProviderSessionId()` 写入 SessionIndex 时增加 `source` 和 `initial_prompt` 字段。需要将 `initial_prompt` 从 `createSession()` 的 `command.prompt` 中截取前 200 字符传入。

---

## Task 4b: Companion — 修复 `list_sessions` handler 使用 per-provider projectsDir

**文件**: `packages/companion/src/index.ts`

### 4b.1 修改 `list_sessions` dispatcher

当前代码：
```typescript
dispatcher.register("list_sessions", async (command) => {
  return await discoverSessions(command.cwd, command.provider, { logger });
});
```

改为：
```typescript
dispatcher.register("list_sessions", async (command) => {
  const providerConfig = config.providers[command.provider];
  return await discoverSessions(command.cwd, command.provider, {
    logger,
    claudeProjectsDir: providerConfig?.projectsDir  // 使用 per-provider 路径
  });
});
```

### 测试要求

扩展 `tests/unit/companion-session-discovery.test.mjs`：
- 验证 book provider 使用 `~/.claudebook/projects/` 扫描（通过传入不同的 `claudeProjectsDir`）
- 验证 claude provider 使用 `~/.claude/projects/` 扫描
- 验证两个 provider 的 session 不会混在一起

### 测试要求

扩展 `tests/unit/companion-core.test.mjs` 中的 SessionIndex 相关测试：
- 验证新 entry 包含 `source: "remote"` 和 `initial_prompt`
- 验证旧 entry（无 source 字段）加载后默认 `source: "remote"`
- 验证 `source: "local"` 在 persist/load 后保留
- 验证 `hasProviderSessionId()` 正确返回 true/false
- 验证 `initial_prompt` 为 null 时正确序列化/反序列化
- 验证 `initial_prompt` 超过 200 字符时被截断

---

## Task 5: Companion — 实现 SessionReconciler

**文件**: 新建 `packages/companion/src/runtime/session-reconciler.ts`

### 5.1 SessionReconciler 类

```typescript
export interface SessionReconcilerOptions {
  readonly sessionIndex: SessionIndex;
  readonly configManager: ConfigManager;
  readonly sendMessage: (message: CompanionReportLocalSessionsMessage) => void;
  readonly hostId: string;
  readonly logger?: LoggerLike;
  readonly discoverSessionsFn?: typeof discoverSessions;  // DI for testing
}

export class SessionReconciler {
  private running = false;

  constructor(private readonly options: SessionReconcilerOptions) {}

  async reconcile(): Promise<{ reported: number; skipped: number }> {
    if (this.running) {
      return { reported: 0, skipped: 0 };
    }
    this.running = true;
    try {
      return await this.doReconcile();
    } finally {
      this.running = false;
    }
  }

  private async doReconcile(): Promise<{ reported: number; skipped: number }> {
    // 1. 获取所有 configured roots（claude + book）
    // 2. 对每个 root + provider 调用 discoverSessions()
    // 3. 过滤掉 SessionIndex 中已有的 provider_session_id
    // 4. 构造 CompanionReportLocalSessionsMessage 并发送
    // 5. 更新 SessionIndex（source: "local"）
    // 6. 返回统计
  }
}
```

### 5.2 关键实现细节

- **并发保护**：`this.running` flag 防止重复执行
- **错误容忍**：单个 root 扫描失败不影响其他 root
- **SessionIndex 更新**：使用 `local:<provider_session_id>` 作为 relay_session_id key
- **消息发送**：只在有新 session 时发送消息，空列表不发送

### 测试要求

新建 `tests/unit/companion-session-reconciler.test.mjs`：
- 验证扫描所有配置的 workspace roots（claude + book）
- 验证过滤掉 SessionIndex 中已有的 session
- 验证只上报差集 session
- 验证空 roots 配置时不扫描不发消息
- 验证单个 root 扫描失败时继续其他 root
- 验证并发保护（第二次调用被跳过，返回 reported=0）
- 验证上报后 SessionIndex 被更新，source="local"
- 验证空差集时不发送消息
- 验证 200 session 上限
- 验证发送的消息结构符合 `CompanionReportLocalSessionsMessage` 类型

---

## Task 6: Companion — 集成 SessionReconciler 到启动流程

**文件**: `packages/companion/src/index.ts`

### 6.1 在 `createCompanionRuntime()` 中实例化 SessionReconciler

```typescript
const reconciler = new SessionReconciler({
  sessionIndex,
  configManager,
  hostId: config.hostId,
  logger,
  sendMessage: (message) => {
    relayClient.send(message);
  }
});
```

### 6.2 在 `relayClient.on("connected")` 回调中触发

在现有的 `connected` 回调中，在 heartbeat 和 active session 上报之后，异步触发 reconciler：

```typescript
relayClient.on("connected", () => {
  heartbeat.start();

  for (const session of adapter.getActiveSessions()) {
    relayClient.send({ /* 现有逻辑 */ });
  }

  // 新增：异步触发对账，不阻塞连接
  void reconciler.reconcile().catch((error) => {
    logger.error?.("Session reconciliation failed", error);
  });
});
```

### 6.3 导出 SessionReconciler

在 `packages/companion/src/index.ts` 中 export `SessionReconciler`。

### 测试要求

扩展 `tests/integration/companion-runtime.test.mjs`：
- 验证 companion 连接 relay 后触发 reconciler
- 验证 reconciler 异步执行，不阻塞连接事件
- 验证断连重连后再次触发 reconciler

---

## Task 7: Relay — GET /sessions 返回 local_available 字段

**文件**: `packages/relay/src/routes/sessions.ts`

### 7.1 确保 SELECT 查询包含 local_available

当前 `GET /sessions` 使用 `SELECT *`，SQLite 新增列后自动包含，但需要验证 JSON 序列化正确。

### 7.2 SQLite INTEGER → JSON boolean 转换

SQLite 存储 `0/1`，但 API 返回应为 `true/false`。在 route handler 中做映射：

```typescript
const sessions = rawSessions.map(s => ({
  ...s,
  local_available: Boolean(s.local_available)
}));
```

或者在 SQL 中使用 `CASE WHEN local_available = 1 THEN json('true') ELSE json('false') END`。

### 测试要求

扩展 `tests/contract/relay-session-flow.test.mjs`：
- 验证 `GET /sessions` 返回的每个 session 包含 `local_available` 字段
- 验证 claude/book session 的 `local_available` 为 `true`
- 验证 openclaw session 的 `local_available` 为 `false`
- 验证 shadow session 的 `local_available` 为 `true`

扩展 `tests/unit/relay-session-route-errors.test.mjs`：
- 验证 `GET /sessions/:id` 返回 `local_available` 字段

---

## Task 8: Companion — 验证 stream-json session 持久化

**文件**: 新建 `tests/integration/session-persistence.test.mjs`

这是一个集成测试，验证 companion 通过 stream-json 模式创建的 CLI session 确实在本地留下 JSONL 文件。

### 8.1 测试场景

```javascript
test("companion-created session leaves a JSONL file in CLI projects directory", async () => {
  // 1. 创建 companion runtime（使用 mock spawn）
  // 2. 模拟 create_session command
  // 3. 模拟 CLI 输出 system.init 和 session result
  // 4. 验证 discoverSessions() 能扫描到该 session
});

test("resumed session JSONL file still exists after resume process exits", async () => {
  // 1. 在临时目录中预创建一个 JSONL 文件
  // 2. 模拟 resume_session command
  // 3. 验证 JSONL 文件仍然存在
});
```

### 测试要求

- 验证 create 后 JSONL 文件存在
- 验证 resume 后 JSONL 文件存在
- 验证 cancel 后 JSONL 文件存在（CLI 被 SIGINT 不应删除文件）
- 验证 complete 后 JSONL 文件存在

---

## Task 9: 端到端集成验证

**文件**: 扩展 `tests/e2e/e2e-session-lifecycle.test.mjs` 或新建 `tests/e2e/e2e-session-sync.test.mjs`

### 9.1 E2E 测试场景

```javascript
test("local sessions are visible via GET /sessions after companion connects", async () => {
  // 1. 在 companion 的 Claude projects 目录中预创建 JSONL 文件
  // 2. 配置 workspace root
  // 3. 启动 relay + companion
  // 4. 等待 companion connected
  // 5. GET /sessions 验证 shadow record 存在
  // 6. 验证 local_available = true
});

test("companion-created session is locally discoverable", async () => {
  // 1. 启动 relay + companion
  // 2. POST /sessions 创建一个 session（使用 mock CLI）
  // 3. 等待 session 完成
  // 4. 验证 discoverSessions() 能扫描到该 session
  // 5. 验证 SessionIndex 包含 source: "remote"
});

test("GET /sessions returns local_available for all session types", async () => {
  // 1. 创建 claude session（应该 local_available = true）
  // 2. 创建 openclaw session（应该 local_available = false）
  // 3. 预创建本地 session + trigger reconciliation
  // 4. GET /sessions 验证三种 session 的 local_available 值
});

test("reconciler is idempotent across reconnections", async () => {
  // 1. 预创建本地 session
  // 2. 启动 companion，验证 shadow record 被创建
  // 3. 断开 companion
  // 4. 重连 companion
  // 5. 验证不会创建重复 shadow record
});
```

### 测试要求

- 覆盖完整的 Android → local 可见性路径
- 覆盖完整的 local → Android 可见性路径
- 覆盖 reconnection 幂等性
- 覆盖 provider 隔离（claude vs book vs openclaw 的 local_available）
- 覆盖空 workspace roots 场景

---

## 执行顺序

```
Task 0 (configDir) ───┐
                       ├── Task 1 (Wire types) ──┐
                       │                          ├── Task 2 (DB migration) ──┐
                       │                          │                            ├── Task 3 (Relay handler) ──┐
                       ├── Task 4 (SessionIndex) ─┤                            │                            │
                       │                          ├── Task 4b (list_sessions) ─┤                            │
                       │                          │                            ├── Task 5 (Reconciler) ─────┤
                       │                          │                            │                            │
                       │                          │                            ├── Task 6 (Integration) ────┤
                       │                          │                            │                            │
                       │                          └── Task 7 (GET /sessions) ──┤                            │
                       │                                                       │                            │
                       └───────────────────────────── Task 8 (Persist test) ───┤                            │
                                                                               │                            │
                                                                               Task 9 (E2E) ───────────────┘
```

- **Task 0** 最先执行（其他 task 都依赖 `CompanionProviderConfig.projectsDir`）
- **Task 1** 和 **Task 4** 可并行（Wire types 和 SessionIndex 类型扩展，均依赖 Task 0）
- **Task 4b** 依赖 Task 0 + Task 4
- **Task 2** 依赖 Task 1（需要 `Session` interface 变更）
- **Task 3** 依赖 Task 1 + Task 2
- **Task 5** 依赖 Task 0 + Task 1 + Task 4（需要 projectsDir + Wire types + SessionIndex）
- **Task 6** 依赖 Task 5
- **Task 7** 依赖 Task 2
- **Task 8** 和 **Task 9** 在所有实现完成后执行
