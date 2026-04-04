# Tasks: 双端 Session 同步

## 0. 前置验证（人工）

### 0.1 验证 stream-json session 持久化
- [ ] 在 Mac 终端用 stream-json 模式启动 book：`echo '{"type":"user","text":"hello"}' | book --input-format stream-json --output-format stream-json --verbose`
- [ ] 检查 `~/.claude/projects/` 下是否生成新的 `.jsonl` session 文件
- [ ] 如果生成：记录文件路径和命名格式，确认与交互模式一致
- [ ] 如果不生成：标记为"分支 B"，需要 companion 自行持久化

### 0.2 验证 native resume 可见性
- [ ] 从 Android 创建一个 book session（确认 companion 已连接）
- [ ] 在 Mac 终端进入相同 cwd 运行 `book resume`
- [ ] 记录是否能看到该 session
- [ ] 如果看不到：运行 `ls -la ~/.claude/projects/-Users-danker-Desktop-AI-vault-IMbot/*.jsonl` 检查文件是否存在

## 1. Wire 协议

### 1.1 `ListSessionsCommand.cwd` 改为 optional
- [ ] `packages/wire/src/models.ts` — `ListSessionsCommand` 的 `cwd` 字段改为 `cwd?: string`
- [ ] 确认 relay 侧的 WS 消息 schema 不会因为缺少 cwd 而拒绝

## 2. Session Discovery

### 2.1 新增 `discoverAllSessions()` 函数
- [ ] `packages/companion/src/runtime/session-discovery.ts` — 新增导出函数 `discoverAllSessions(provider, options)`
- [ ] 逻辑与 `discoverSessions()` 相同，但不调用 `resolveProjectCwd()` 过滤
- [ ] 对每个 project 目录直接用 `decodeProjectDirectory()` 恢复 cwd，如果解码失败则使用目录名作为 fallback cwd
- [ ] 保留 `limit`、`logger`、`claudeProjectsDir` 参数
- [ ] 保留按 mtime 降序排序 + 截断到 limit

### 2.2 `list_sessions` 命令支持全量模式
- [ ] `packages/companion/src/index.ts:121-134` — 当 `command.cwd` 为空/未定义/"*" 时调用 `discoverAllSessions()`
- [ ] 其余情况保持现有 `discoverSessions(command.cwd, ...)` 行为

## 3. Session Reconciler

### 3.1 使用全量扫描替代 cwd 过滤
- [ ] `packages/companion/src/runtime/session-reconciler.ts` — `doReconcile()` 中将 `discoverSessionsFn(root.path, ...)` 替换为 `discoverAllSessionsFn(root.provider, ...)`
- [ ] 按 provider 聚合，每个 provider 只做一次全量扫描（避免重复扫描同一 projectsDir）
- [ ] 构造函数增加可选的 `discoverAllSessionsFn` 依赖注入参数
- [ ] 保留现有 `status !== "completed"` 过滤和 `sessionIndex.hasProviderSessionId()` 去重

### 3.2 Reconciler 触发时机
- [ ] 确认 reconciler 在 companion 连接/重连时自动触发
- [ ] 确认 reconciler 在 workspace root 增删时重新触发

## 4. Relay 侧验证

### 4.1 `report_local_sessions` 处理
- [ ] 审查 relay WS 消息处理中 `report_local_sessions` 的 handler
- [ ] 确认本地 session 在 relay DB 中正确创建/更新，包括 `provider_session_id` 字段
- [ ] 确认 `local_available` 标志正确设置

### 4.2 session list API
- [ ] 确认 `GET /sessions` 返回的 session 列表包含 reconciler 上报的 session
- [ ] 确认这些 session 可以通过 `POST /sessions/:id/resume` 恢复

## 5. 测试

### 5.1 Unit Tests — discoverAllSessions

**正常路径**：
- [ ] 有多个 project 目录，每个有若干 .jsonl 文件 → 全部返回，不过滤 cwd
- [ ] 返回结果按 mtime 降序排序
- [ ] limit 参数截断结果数量

**边界情况**：
- [ ] projectsDir 不存在 → 返回空数组
- [ ] projectsDir 为空 → 返回空数组
- [ ] project 目录名无法 decode → 使用目录名作为 fallback cwd（或跳过并 warn）
- [ ] .jsonl 文件大小为 0 → status = "unknown"
- [ ] .jsonl 文件不可读 → 跳过并 warn

**与 discoverSessions 对比**：
- [ ] 同一 projectsDir 下，discoverAllSessions 返回的结果 ≥ discoverSessions(特定cwd) 返回的结果
- [ ] discoverSessions 的现有行为无回归

### 5.2 Unit Tests — list_sessions 全量模式

- [ ] command.cwd 为 undefined → 调用 discoverAllSessions
- [ ] command.cwd 为 "*" → 调用 discoverAllSessions
- [ ] command.cwd 为具体路径 → 调用 discoverSessions（现有行为）

### 5.3 Unit Tests — SessionReconciler 全量模式

- [ ] 配置 2 个 workspace root (同 provider) → 只调用 discoverAllSessions 1 次
- [ ] 配置 2 个不同 provider 的 root → 分别调用 discoverAllSessions 各 1 次
- [ ] 发现的 session 中，已在 sessionIndex 中的被 skip
- [ ] 发现的 session 中，status ≠ "completed" 的被 skip
- [ ] 全量扫描发现的新 session 正确通过 sendMessage 上报

### 5.4 集成测试 — 端到端同步

- [ ] Companion 连接后，reconciler 上报本地 session → relay 可通过 GET /sessions 返回
- [ ] Relay 创建的 session → companion session-index 有记录 → reconciler 不重复上报
