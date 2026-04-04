# Design: dual-session-persistence

## Context

IMbot 当前存在三套独立的 session 存储，彼此零同步：

| 存储 | 位置 | 内容 | 谁读 |
|------|------|------|------|
| **Relay SQLite** | VPS `relay.db` | `sessions` 表（id, provider_session_id, status, cwd, prompt, events…） | Android app、viewer |
| **Companion SessionIndex** | MacBook `~/.imbot/sessions.json` | relay_session_id → {provider_session_id, cwd, provider, created_at} 映射 | companion（resume 时查询） |
| **Claude CLI 本地 JSONL** | MacBook `~/.claude/projects/<encoded-cwd>/<session_id>.jsonl` | 完整 conversation history | `claude --resume`、`discoverSessions()` |
| **Book CLI 本地 JSONL** | MacBook `~/.claudebook/projects/<encoded-cwd>/<session_id>.jsonl` | 完整 conversation history | `book --resume` |

### 关键根因发现

`book` binary 是一个 shell wrapper：
```bash
#!/bin/bash
export CLAUDE_CONFIG_DIR="$HOME/.claudebook"
exec claude "$@"
```

Book 通过 `CLAUDE_CONFIG_DIR` 将所有数据（包括 session JSONL）重定向到 `~/.claudebook/`。
而 `discoverSessions()` 硬编码了 `path.join(os.homedir(), ".claude", "projects")` 作为扫描路径，
**完全无法发现 book 的 session**。

经验证：
- **Claude session**：companion 通过 stream-json 模式创建后，JSONL 正确写入 `~/.claude/projects/` ✓
- **Book session**：companion 通过 stream-json 模式创建后，JSONL 写入 `~/.claudebook/projects/` ✓，但 `discoverSessions()` 找不到 ✗

现状问题：

1. **Book session 本地完全不可见**：`discoverSessions()` 只扫描 `~/.claude/projects/`，book 的 session 在 `~/.claudebook/projects/` 中，永远不会被发现。
2. **本地 → Android 不可见**：用户在 MacBook 上直接运行 `claude` 或 `book` 创建的 session 不存在于 relay 的 `sessions` 表中，Android app 完全看不到。
3. **Session 映射单向**：`~/.imbot/sessions.json` 只在 companion 创建 session 时写入，本地创建的 session 没有 relay_session_id 映射。
4. **Provider 配置缺失**：`CompanionProviderConfig` 只有 `binary` 字段，没有 `configDir`/`projectsDir`，无法知道每个 provider 的 session 存储路径。

## Goals / Non-Goals

**Goals:**

1. **Android 端 session 本地可发现**：companion 创建的 session 在 CLI 原生的 `~/.claude/projects/` 中有 JSONL 文件（已确认有），companion 的 SessionIndex 提供 relay ↔ provider_session_id 映射，使本地工具能关联两端。
2. **本地 session 上报 relay**：companion 启动（或定期心跳）时扫描本地 CLI session 目录，将 relay 未知的 session 上报到 relay，使 Android 端能看到本地创建的 session 列表（只读，不可远程控制）。
3. **Provider 隔离**：Claude 的 session 扫描 `~/.claude/projects/`，Book 的 session 扫描 `~/.claudebook/projects/`（通过 `configDir` 配置推导）。修复当前 `discoverSessions()` 对 provider 参数 `void` 的问题，使其根据 provider 使用正确的 projects 目录。
4. **Relay `GET /sessions` 增加本地可用性标记**：返回 `local_available: boolean`，标识该 session 在当前 companion 主机上是否有 CLI 持久化数据。

**Non-Goals:**

- **实时双向同步**：不做 push-based 的实时 session 同步，只在 companion 启动/重连/心跳时做对账。
- **Session 内容同步**：不同步 conversation history 内容，只同步 session 元数据（id, cwd, provider, status, created_at）。
- **本地 session 远程控制**：本地直接创建的 session 上报到 relay 后只能查看，不能从 Android 端 resume/cancel/send_message（因为没有 companion 代理的进程管理）。
- **跨主机 session 同步**：只同步当前 companion 主机与 relay 之间的 session，不处理多台 MacBook 的场景。
- **Android app UI 改动**：本期不改 Android session 列表 UI，`local_available` 字段留给后续迭代。

## Decisions

### D1: `discoverSessions()` 支持 per-provider 的 projects 目录

**选择**：扩展 `discoverSessions()` 的 `claudeProjectsDir` 参数，让调用方根据 provider 传入正确的 projects 路径。

**理由**：
- 该函数已经正确处理了路径编码、mtime 排序、错误容忍、限制条数等所有边界情况（9 个测试）。
- 参数 `claudeProjectsDir` 已存在，只是默认值硬编码了 `~/.claude/projects/`。
- 调用方（`list_sessions` handler、`SessionReconciler`）需要根据 provider 传入 `~/.claude/projects/`（claude）或 `~/.claudebook/projects/`（book）。

**关键改动**：不改 `discoverSessions()` 本身的实现，而是在调用方根据 provider 确定正确的 `claudeProjectsDir`。

### D1b: `CompanionProviderConfig` 增加 `configDir` 字段

**选择**：在 companion 配置中为每个 provider 添加可选的 `configDir` 字段，用于推导 projects 目录路径。

**理由**：
- `book` wrapper 通过 `CLAUDE_CONFIG_DIR` 改变了整个配置目录，不仅仅是 binary 路径。
- companion 需要知道每个 provider 的 `configDir` 才能找到正确的 session 存储位置。
- `configDir` → `${configDir}/projects/` 是 session JSONL 的存储路径。
- 默认值：`claude` → `~/.claude`，`book` → `~/.claudebook`。

**配置示例**：
```json
{
  "providers": {
    "claude": { "binary": "claude" },
    "book": { "binary": "book", "config_dir": "~/.claudebook" }
  }
}
```

如果不配置 `config_dir`，companion SHALL 通过解析 binary wrapper 脚本自动检测 `CLAUDE_CONFIG_DIR`（读取 shell script 的前 10 行，regex 匹配 `CLAUDE_CONFIG_DIR=`），或回退到 `~/.claude`。

**替代方案被否**：硬编码 `book → ~/.claudebook` — 不可维护，用户可能自定义 CLAUDE_CONFIG_DIR。

### D2: Session 对账（Reconciliation）在 companion 启动时和 relay 重连时执行

**选择**：在 `relayClient.on("connected")` 回调中触发对账，复用已有的连接生命周期。

**理由**：
- companion 启动时必然连接 relay，这是天然的对账触发点。
- relay 重连（网络恢复）后也需要对账，避免断连期间创建的本地 session 丢失。
- 不引入独立的定时器或 cron 机制，KISS。

**对账流程**：
1. companion 调用 `discoverSessions()` 扫描所有配置的 workspace roots 下的本地 session。
2. 与 `SessionIndex` 中已有映射做差集 — 找出「本地有 JSONL 但没有 relay_session_id 映射」的 session。
3. 将这些 session 通过新的 `report_local_sessions` 事件/命令上报 relay。
4. Relay 收到后为每个 session 创建一条 `status=completed, provider_session_id=<id>` 的记录（因为本地 session 已经结束）。

### D3: 新增 `report_local_sessions` companion → relay 消息

**选择**：新增一个专用的 companion message type `report_local_sessions`，不复用 `event` 消息。

**理由**：
- 这是一个批量操作（可能一次上报几十个 session），不适合逐个发 event。
- 语义不同于 session 生命周期事件，不应混入 `CompanionEventMessage`。
- Relay 收到后需要幂等处理（如果 provider_session_id 已存在则跳过）。

**消息格式**：
```typescript
type CompanionReportLocalSessionsMessage = {
  type: "report_local_sessions";
  host_id: string;
  sessions: Array<{
    provider_session_id: string;
    provider: InteractiveProvider;
    cwd: string;
    created_at: string;
  }>;
};
```

### D4: Relay 为上报的本地 session 创建 shadow record

**选择**：在 `sessions` 表中创建 `status=completed` 的记录，`initial_prompt` 设为 `null`（本地 session 的 prompt 不容易从 JSONL 中提取）。

**理由**：
- 复用现有的 `sessions` 表和查询逻辑，Android app 的 `GET /sessions` 不需要改动。
- `status=completed` 是安全的默认值（本地 CLI session 已经退出）。
- 使用 `INSERT OR IGNORE` 按 `provider_session_id` 去重，幂等安全。

**替代方案被否**：新建一个 `local_sessions` 表 — 会导致 Android 端需要查两个表然后合并，增加不必要的复杂度。

### D5: `GET /sessions` 增加 `local_available` 字段

**选择**：relay 在查询 sessions 时，对每个 session 标记 `local_available`。

**实现方式**：companion 在 `report_local_sessions` 中上报的 session 设置 `local_available = true`。Relay 新创建的 session 先以 `local_available = false` 落库；只有在收到 companion 的 `session_started` 确认后，才把 `claude` / `book` session 更新为 `true`。Relay 在 `sessions` 表新增 `local_available` 列（默认 `false`）。

**理由**：这样 Android app 未来可以在 UI 上区分「可在 MacBook 上直接 resume 的 session」vs「只在 relay 有记录的 session」。

### D6: SessionIndex 扩展 — 增加 `source` 字段

**选择**：`SessionIndexEntry` 增加 `source: "remote" | "local"` 字段。

**理由**：
- `remote`：通过 Android/relay 创建的 session（当前所有 session）。
- `local`：companion 对账时发现的本地 session（反向上报到 relay 后写入）。
- 用于区分 session 的创建来源，避免重复上报。

### D7: 对账时不扫描所有 cwd — 只扫描已配置的 workspace roots

**选择**：对账只扫描 `ConfigManager` 中已配置的 workspace roots，不扫描整个 `~/.claude/projects/`。

**理由**：
- `~/.claude/projects/` 可能有成百上千个目录（用户在各种项目中用过 Claude Code），全扫描太慢且噪音太多。
- Workspace roots 是用户明确配置的「我想通过 Android 管理的目录」，只扫描这些是合理的范围限定。
- 保持 KISS，避免上报用户不关心的 session。

## Risks / Trade-offs

| Risk | Impact | Mitigation |
|------|--------|-----------|
| 本地 session JSONL 文件可能被用户删除 | 上报的 session 在本地不可 resume | `local_available` 是快照值；Android 端尝试 resume 时 companion 会返回 `session_not_resumable` 错误，前端已有处理 |
| `discoverSessions()` 扫描大量文件慢 | companion 启动或重连延迟 | 限制只扫描 workspace roots，加 limit 参数，async 不阻塞主连接 |
| 多个 relay 实例同时连接同一 companion | session 被重复上报 | 当前架构是单 relay 单 companion，暂不处理 |
| `sessions` 表 `provider_session_id` 没有唯一索引 | shadow record 可能重复创建 | 使用 `INSERT ... WHERE NOT EXISTS (SELECT 1 FROM sessions WHERE provider_session_id = ?)` 做幂等插入 |
| 本地 session 的 `created_at` 来自 mtime，不精确 | 排序可能不完美 | 可接受 — 这是展示用的，不影响功能 |
| Book 的 `configDir` 可能被用户自定义 | companion 找不到正确的 projects 目录 | 支持 `config_dir` 配置字段 + 自动检测 binary wrapper 中的 `CLAUDE_CONFIG_DIR` |
| 自动检测 `CLAUDE_CONFIG_DIR` 可能误判 | 解析 shell script 不够健壮 | 只读前 10 行做 regex 匹配，失败则回退 `~/.claude`；用户可通过显式配置 `config_dir` 覆盖 |

## Migration Plan

1. **Wire 先行**：新增 `CompanionReportLocalSessionsMessage` 类型，`sessions` 表增加 `local_available` 列。
2. **Companion 实现**：扩展 `SessionIndex` 增加 `source` 字段；新增 `SessionReconciler` 类执行对账逻辑；在 `connected` 回调中触发。
3. **Relay 实现**：处理 `report_local_sessions` 消息，创建 shadow session records；`GET /sessions` 返回 `local_available` 字段。
4. **回滚**：所有改动向后兼容 — `local_available` 默认 `false`，`source` 默认 `"remote"`，新消息类型被旧版忽略。

## Open Questions

1. ~~Claude Code 在 stream-json 模式下是否持久化 session？~~ **已确认：是的。** `discoverSessions()` 能扫描到这些文件，且 resume 测试通过。
2. 本地 session 的 `initial_prompt` 是否需要从 JSONL 文件第一行提取？**暂定不提取** — JSONL 格式可能变化，且解析成本高。如果后续需要，可以在 `discoverSessions()` 中增加 JSONL 首行解析。
3. 是否需要在 companion 心跳中携带本地 session 数量/最新变化？**暂定不做** — 对账只在启动/重连时执行已足够。
