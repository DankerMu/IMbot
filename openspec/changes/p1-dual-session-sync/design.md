# Design: 双端 Session 同步

## 问题全景

```
                    ┌── Android 创建 ──┐
                    │  POST /sessions   │
                    └───────┬───────────┘
                            ▼
                    ┌── Relay DB ──────┐
                    │ sessions table   │──── relay 知道所有 session
                    └───────┬──────────┘
                            ▼ WS: create_session
                    ┌── Companion ─────┐
                    │ spawns book      │──── companion 注册到 session-index
                    │ stream-json mode │
                    └───────┬──────────┘
                            ▼ book 进程
                    ┌── ~/.claude/ ────┐
                    │ projects/<cwd>/  │──── native book resume 扫描此处
                    │ <session>.jsonl  │     ★ stream-json 模式是否写文件？
                    └──────────────────┘
```

## Phase 1: 验证 stream-json 持久化行为

**前置实验**（需人工执行一次）：

```bash
# 终端 1: 用 stream-json 模式启动 book
echo '{"type":"user","text":"hello"}' | book --input-format stream-json --output-format stream-json --verbose

# 观察 ~/.claude/projects/ 是否生成新的 .jsonl 文件
ls -la ~/.claude/projects/-Users-danker-Desktop-AI-vault-IMbot/*.jsonl
```

**两种结果分支**：

### 分支 A: stream-json 模式写 JSONL 文件

如果 `book` 在 stream-json 模式下正常持久化 session，则问题纯粹是 discovery 的 cwd 过滤。

**修复方案**：修改 `discoverSessions()` 支持宽松模式

### 分支 B: stream-json 模式不写 JSONL 文件

如果 `book` 在 stream-json 模式下不持久化 session 文件：

1. Native `book resume` 永远无法直接发现这些 session
2. 需要 companion 在 session 结束时主动导出 session 数据
3. 或者让 `book resume` 支持通过 session ID 直接恢复（不依赖文件发现）

**修复方案**：companion 主动将 relay session ID ↔ provider session ID 映射暴露给 native CLI

**设计将覆盖两个分支。**

## Phase 2: 修复 Session Discovery

### 当前问题 — `session-discovery.ts`

```typescript
// 当前: resolveProjectCwd 要求 cwd 精确匹配或是子路径
const projectCwd = resolveProjectCwd(projectEntry.name, normalizedCwd);
if (!projectCwd) {
  continue;  // ← 跳过所有不匹配 cwd 的 session
}
```

### 方案：新增 `discoverAllSessions()` 全量扫描函数

**文件**: `packages/companion/src/runtime/session-discovery.ts`

```typescript
/**
 * 不带 cwd 过滤的全量 session 发现。
 * 扫描 projectsDir 下所有 project 目录的 .jsonl 文件。
 * 用于 reconciler 的全量同步场景。
 */
export async function discoverAllSessions(
  provider: InteractiveProvider,
  options: SessionDiscoveryOptions = {}
): Promise<LocalSessionInfo[]> {
  // 与 discoverSessions 相同逻辑，但跳过 resolveProjectCwd 过滤
  // 改为: projectCwd = decodeProjectDirectory(projectEntry.name) ?? projectEntry.name
}
```

**关键改动点**：

1. 新函数 `discoverAllSessions(provider, options)` — 不接受 cwd 参数，扫描所有 project 目录
2. `decodeProjectDirectory()` 仍用于恢复 cwd 路径（从目录编码名反解），但不再作为过滤条件
3. 原 `discoverSessions(cwd, provider, options)` 保持不变（按 cwd 过滤的行为是 `list_sessions` 命令的正确语义）

### 修改 `list_sessions` 命令行为

**文件**: `packages/companion/src/index.ts:121-134`

当前 `list_sessions` 只返回 cwd 匹配的 session。需要增加可选参数支持全量列举：

```typescript
dispatcher.register("list_sessions", async (command) => {
  const providerConfig = config.providers[command.provider];
  if (!providerConfig) { throw ... }

  // 新增: 如果 command.cwd 为空或 "*", 使用全量扫描
  if (!command.cwd || command.cwd === "*") {
    return await discoverAllSessions(command.provider, {
      logger,
      claudeProjectsDir: providerConfig.projectsDir
    });
  }

  return await discoverSessions(command.cwd, command.provider, {
    logger,
    claudeProjectsDir: providerConfig.projectsDir
  });
});
```

### 修改 Wire 协议 `ListSessionsCommand`

**文件**: `packages/wire/src/models.ts`

```typescript
export interface ListSessionsCommand {
  cmd: "list_sessions";
  req_id: string;
  provider: InteractiveProvider;
  cwd?: string;  // 改为 optional — 空值表示全量列举
}
```

## Phase 3: 修改 SessionReconciler

### 当前问题 — `session-reconciler.ts`

```typescript
// 当前: 用 root.path 作为 cwd，仅发现该 root 下的 session
discovered = await this.discoverSessionsFn(root.path, root.provider, { ... });
```

### 方案：使用 `discoverAllSessions` 全量扫描

**文件**: `packages/companion/src/runtime/session-reconciler.ts`

```typescript
private async doReconcile(): Promise<{ reported: number; skipped: number }> {
  // 改为: 按 provider 聚合，对每个配置的 provider 做一次全量扫描
  const providerSet = new Set(roots.map(r => r.provider));

  for (const provider of providerSet) {
    const providerConfig = this.options.providers[provider];
    if (!providerConfig) continue;

    // 全量扫描，不限制 cwd
    const discovered = await this.discoverAllSessionsFn(provider, {
      claudeProjectsDir: providerConfig.projectsDir,
      logger: this.logger,
      limit: MAX_REPORTED_SESSIONS
    });

    // 后续 filter + report 逻辑不变
  }
}
```

**好处**：
- 不再受 workspace root 配置精确度影响
- 用户在任意目录创建的 session 都能被发现
- 向后兼容：reconciler 只上报不在 session index 中的 session

## Phase 4: Relay 侧 Session 列表增强

### 当前问题

Relay 的 `GET /sessions` 返回所有 session，但 Android session list 的 session 可能是通过 relay 创建的（有 relay session ID）或通过 reconciler 上报的（有 provider session ID）。目前两种来源的 session 在 Android 端显示没有区别，但 resume 路径不同。

### 方案

确保 reconciler 上报的 session 在 relay DB 中也有 `provider_session_id` 字段，这样 relay 的 resume 接口可以找到对应的 companion session。

**检查点**：验证 `report_local_sessions` 消息处理路径（relay 侧）是否正确设置了 `provider_session_id`。

**文件**: 查看 relay WS 消息处理中 `report_local_sessions` 的 handler

## Phase 5: 端到端验证矩阵

| 场景 | 创建端 | 恢复端 | 预期行为 |
|------|--------|--------|----------|
| A | Android (book) | Mac `book resume` | session 出现在 resume 列表，可恢复 |
| B | Mac `book` | Android session list | session 出现在列表，可打开/恢复 |
| C | Android (claude) | Mac `claude resume` | 同 A |
| D | Mac `claude` | Android session list | 同 B |
| E | Android (book, 子目录) | Mac `book resume` (父目录) | session 出现在列表 |
| F | Android 创建 → companion 重启 | Android session list | session 仍可见（relay DB 保留） |

## 影响范围

| 组件 | 改动 |
|------|------|
| `packages/wire/src/models.ts` | `ListSessionsCommand.cwd` 改为 optional |
| `packages/companion/src/runtime/session-discovery.ts` | 新增 `discoverAllSessions()` |
| `packages/companion/src/index.ts` | `list_sessions` handler 支持全量模式 |
| `packages/companion/src/runtime/session-reconciler.ts` | 使用全量扫描替代 cwd 过滤 |
| `tests/unit/session-discovery.test.mjs` | 新增全量扫描测试 |
| `tests/unit/session-reconciler.test.mjs` | 更新 reconciler 测试 |

## 风险

1. **全量扫描性能**：如果 `~/.claude/projects/` 下有大量 project 目录（>100），全量扫描可能变慢。缓解：保留 `limit` 参数，按最近活跃时间排序，默认只同步最近 10 个，并允许 companion 配置覆盖。
2. **stream-json 不持久化**（分支 B）：需要额外的 session 导出机制。这是最大风险——如果上游 `book`/`claude` CLI 在 stream-json 模式下不写 JSONL，则需要 companion 自行模拟持久化，或接受 native resume 不可用。
3. **Reconciler 频率**：全量扫描增加 I/O，但只在 companion 连接/重连时触发，频率低。
