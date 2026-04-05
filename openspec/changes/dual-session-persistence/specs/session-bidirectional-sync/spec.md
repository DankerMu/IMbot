# Spec: session-bidirectional-sync

Companion 启动时和 relay 重连时执行双向对账 — 将本地 CLI session 目录中的 session 上报 relay，使 Android 端能看到本地创建的 session。

## ADDED Requirements

### Requirement: Wire defines CompanionReportLocalSessionsMessage type

`@imbot/wire` SHALL 导出 `CompanionReportLocalSessionsMessage` 类型，用于 companion 向 relay 批量上报本地发现的 session。

#### Scenario: Message type structure

- **WHEN** companion 构造一个 `CompanionReportLocalSessionsMessage`
- **THEN** 消息结构 SHALL 为：
  ```typescript
  {
    type: "report_local_sessions";
    host_id: string;
    sessions: Array<{
      provider_session_id: string;
      provider: "claude" | "book";
      cwd: string;
      created_at: string;
    }>;
  }
  ```

#### Scenario: CompanionMessage union includes the new type

- **WHEN** companion 通过 `relayClient.send()` 发送消息
- **THEN** `CompanionMessage` union type SHALL 接受 `CompanionReportLocalSessionsMessage`

---

### Requirement: SessionReconciler scans configured workspace roots on trigger

Companion SHALL 包含一个 `SessionReconciler` 类（或等效模块），在触发时扫描所有已配置的 workspace roots 下的本地 CLI session。

#### Scenario: Reconciler scans all configured roots for a provider

- **WHEN** reconciler 被触发，ConfigManager 有两个 claude roots `/Users/danker/Desktop/AI-vault` 和 `/Users/danker/Desktop/MyProject`
- **THEN** reconciler SHALL 对每个 root 调用 `discoverSessions(root, "claude", { claudeProjectsDir: "<claude_config_dir>/projects" })`，合并结果

#### Scenario: Reconciler scans both claude and book roots with correct projects dirs

- **WHEN** ConfigManager 配置了 claude roots 和 book roots
- **THEN** reconciler SHALL 分别扫描两个 provider 的所有 roots
- **AND** claude roots 使用 `~/.claude/projects/` 作为 projects dir
- **AND** book roots 使用 `~/.claudebook/projects/`（或配置的 config_dir + `/projects/`）作为 projects dir

#### Scenario: Reconciler filters out already-known sessions

- **WHEN** 本地扫描发现 sessions [A, B, C]，SessionIndex 已有 sessions [A, B] 的映射
- **THEN** reconciler SHALL 只上报 session [C] 到 relay（差集）

#### Scenario: Reconciler handles empty workspace roots gracefully

- **WHEN** ConfigManager 没有配置任何 workspace root
- **THEN** reconciler SHALL 不执行扫描，不发送 `report_local_sessions` 消息

#### Scenario: Reconciler handles scan errors without crashing

- **WHEN** `discoverSessions()` 在扫描某个 root 时抛出错误
- **THEN** reconciler SHALL 记录 warning 日志，继续扫描其他 roots
- **AND** companion 进程 SHALL 不崩溃

#### Scenario: Reconciler respects session limit per root

- **WHEN** 某个 root 下有 500 个本地 session
- **THEN** reconciler SHALL 只上报最近的 `local_session_sync_limit` 个
- **AND** 默认值为 10
- **AND** 排序按最近活跃时间降序

---

### Requirement: Reconciler triggers on relay connection and reconnection

Reconciler SHALL 在 companion 成功连接（首次或重连）relay 后自动触发。

#### Scenario: Reconciler runs on initial connection

- **WHEN** companion 首次启动并成功连接 relay（`relayClient.on("connected")` 触发）
- **THEN** reconciler SHALL 执行一次对账扫描

#### Scenario: Reconciler runs on reconnection after disconnect

- **WHEN** companion 断连后重新连接 relay
- **THEN** reconciler SHALL 再次执行对账扫描（因为断连期间可能有新的本地 session 创建）

#### Scenario: Reconciler does not block connection event handling

- **WHEN** reconciler 扫描正在执行
- **THEN** companion 的 heartbeat 和 active session 状态上报 SHALL 不被阻塞（reconciler 异步执行）

#### Scenario: Concurrent reconciler runs are deduplicated

- **WHEN** relay 连接快速断开重连，触发两次 reconciler
- **THEN** 如果前一次扫描尚未完成，新的触发 SHALL 被跳过（防止重复扫描）

---

### Requirement: Relay handles report_local_sessions message

Relay SHALL 处理来自 companion 的 `report_local_sessions` 消息，为每个未知 session 创建 shadow record。

#### Scenario: Shadow record creation for unknown session

- **WHEN** relay 收到 `report_local_sessions` 消息，其中包含一个 `provider_session_id` 为 `"abc-123"` 的 session，且 `sessions` 表中不存在 `provider_session_id = "abc-123"` 的记录
- **THEN** relay SHALL 在 `sessions` 表中创建一条新记录：
  - `id`: 新生成的 UUID
  - `provider_session_id`: `"abc-123"`
  - `provider`: 消息中的 provider 值
  - `host_id`: 消息中的 host_id
  - `workspace_cwd`: 消息中的 cwd
  - `status`: `"completed"`
  - `local_available`: `true`
  - `initial_prompt`: `null`
  - `model`: `null`
  - `permission_mode`: `"bypassPermissions"`

#### Scenario: Idempotent handling of duplicate provider_session_id

- **WHEN** relay 收到 `report_local_sessions` 消息，其中包含一个 `provider_session_id` 已存在于 `sessions` 表中的 session
- **THEN** relay SHALL 跳过该 session，不创建重复记录
- **AND** 如果已有记录的 `local_available` 为 `false`，SHALL 更新为 `true`

#### Scenario: Batch processing of multiple sessions

- **WHEN** relay 收到包含 50 个 sessions 的 `report_local_sessions` 消息
- **THEN** relay SHALL 在单个事务中处理所有 sessions（原子性）

#### Scenario: Invalid sessions in batch are skipped

- **WHEN** `report_local_sessions` 消息中某个 session 的 `provider_session_id` 为空字符串
- **THEN** relay SHALL 跳过该 session，记录 warning，继续处理其他 sessions

#### Scenario: Host validation

- **WHEN** `report_local_sessions` 消息的 `host_id` 不存在于 `hosts` 表中
- **THEN** relay SHALL 拒绝整个消息，记录 error

#### Scenario: Shadow sessions are queryable via GET /sessions

- **WHEN** relay 为本地 session 创建了 shadow record
- **THEN** `GET /sessions` SHALL 返回这些 shadow sessions，与正常 sessions 混合排序
- **AND** shadow sessions 的 `status` 为 `"completed"`，`local_available` 为 `true`

---

### Requirement: Companion updates SessionIndex after successful reconciliation

Reconciler 成功上报 session 到 relay 后，SHALL 在 SessionIndex 中创建映射。

#### Scenario: SessionIndex updated with relay-assigned session id

- **WHEN** relay 处理 `report_local_sessions` 并返回成功
- **THEN** companion SHALL 在 SessionIndex 中为每个新上报的 session 创建 entry：
  - `relay_session_id`: 可以是 `local:<provider_session_id>` 格式（因为 relay 分配的 UUID 不会返回给 companion，companion 只需要知道这个 session 已上报过）
  - `provider_session_id`: 本地 session id
  - `cwd`: 本地 session 的 cwd
  - `provider`: claude 或 book
  - `created_at`: 本地 session 的 created_at
  - `source`: `"local"`

#### Scenario: Already-indexed sessions are not duplicated

- **WHEN** reconciler 第二次运行，之前已上报的 session 已在 SessionIndex 中
- **THEN** 这些 session SHALL 不被重复上报

---

### Requirement: Relay companion message handler routes report_local_sessions

Relay 的 companion WebSocket 消息处理器 SHALL 识别 `type: "report_local_sessions"` 并路由到正确的处理逻辑。

#### Scenario: Message type recognized and handled

- **WHEN** companion 通过 WebSocket 发送 `{ type: "report_local_sessions", host_id: "...", sessions: [...] }`
- **THEN** relay SHALL 将消息路由到 session reconciliation handler
- **AND** 处理完成后 SHALL 不发送 ack（这是一个 fire-and-forget 消息，不需要 ack）

#### Scenario: Unknown message type backwards compatibility

- **WHEN** 旧版 relay 收到 `type: "report_local_sessions"` 消息（不认识该类型）
- **THEN** relay SHALL 忽略该消息而不崩溃（现有的 unknown message type 处理已覆盖此场景）

---

### Requirement: Wire SessionIndex type extensions

`@imbot/wire` 或 companion 内部类型 SHALL 扩展 `SessionIndexEntry` 以支持新字段。

#### Scenario: SessionIndexEntry type includes source field

- **WHEN** TypeScript 编译 companion 代码
- **THEN** `SessionIndexEntry` SHALL 包含 `source?: "remote" | "local"` 可选字段

#### Scenario: SessionIndexEntry type includes initial_prompt field

- **WHEN** TypeScript 编译 companion 代码
- **THEN** `SessionIndexEntry` SHALL 包含 `initial_prompt?: string | null` 可选字段

---

### Requirement: Relay database migration for local_available column

Relay 启动时 SHALL 检查并执行 `sessions` 表的 `local_available` 列迁移。

#### Scenario: Fresh database includes local_available column

- **WHEN** relay 首次启动，创建全新数据库
- **THEN** `sessions` 表 SHALL 包含 `local_available` 列，类型为 `INTEGER`（SQLite boolean），默认值为 `0`

#### Scenario: Existing database migrated to include local_available

- **WHEN** relay 启动并检测到 `sessions` 表缺少 `local_available` 列
- **THEN** migration SHALL 执行 `ALTER TABLE sessions ADD COLUMN local_available INTEGER NOT NULL DEFAULT 0`
- **AND** 已存在的 `provider IN ('claude', 'book') AND provider_session_id IS NOT NULL` 的 session SHALL 被更新为 `local_available = 1`

#### Scenario: Migration is idempotent

- **WHEN** relay 重启，`local_available` 列已存在
- **THEN** migration SHALL 不重复执行，不报错

---

### Requirement: Session model includes local_available field

`@imbot/wire` 的 `Session` interface SHALL 包含 `local_available` 字段。

#### Scenario: Session interface includes local_available

- **WHEN** TypeScript 编译任何使用 `Session` 类型的代码
- **THEN** `Session` interface SHALL 包含 `local_available: boolean` 字段

#### Scenario: Relay creates sessions with local_available default

- **WHEN** relay 通过 `SessionOrchestrator.create()` 创建新 session
- **THEN** `local_available` SHALL 初始为 `false`
- **AND** 在 session 成功进入 `running` 后，`provider = "claude"` 或 `"book"` 的 session SHALL 被更新为 `true`
- **AND** `provider = "openclaw"` 的 session SHALL 保持 `false`

#### Scenario: GET /sessions/:id returns local_available

- **WHEN** client 请求 `GET /sessions/:id`
- **THEN** 返回的 session 对象 SHALL 包含 `local_available` boolean 字段
