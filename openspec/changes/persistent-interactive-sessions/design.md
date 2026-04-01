## Context

IMbot 当前的 session 生命周期是 one-shot：`create_session` spawn `claude -p <prompt>`，Claude 回复后进程退出，session 进入 `completed`。后续交互需要 `resume_session` 重新 spawn 进程（`-r <id>`），每次重新加载上下文 9-13s。

### Spike 验证结论（docs/spike-interactive-cli.md）

Claude CLI 原生支持双向 JSON streaming 协议：

```
claude -p --input-format stream-json --output-format stream-json --verbose --permission-mode <mode>
```

- stdin 写 `{"type":"user","message":{"role":"user","content":"..."}}\n`
- stdout 读标准 stream-json 事件
- **进程常驻**，每轮 ~2s（vs one-shot 的 9-13s）
- Context 完全保持，Session ID 一致
- 不需要 PTY，标准 pipe stdio 即可

### 目标进程生命周期

```
create_session → spawn("claude -p --input-format stream-json --output-format stream-json --verbose ...")
                     ↓
              stdin: {"type":"user","message":{"role":"user","content":"<first prompt>"}}\n
                     ↓
              stdout: stream-json events → result → session → idle（进程存活，等待输入）
                     ↓
send_message  → stdin: {"type":"user","message":{"role":"user","content":"<next prompt>"}}\n
                     ↓
              stdout: stream-json events → result → session → idle
                     ↓
              ... (N turns, 单进程常驻)
                     ↓
complete_session → SIGTERM → process exits → session → completed
```

## Goals / Non-Goals

**Goals:**
- 进程常驻，多轮对话无需重新 spawn，每轮 ~2s
- 手机发消息 → Mac Claude 处理 → 手机实时看到流式回复
- Mac 端 CLI viewer 实时看到同一个 session 的事件流
- 状态机平滑升级，不破坏已有 session 的 resume 能力

**Non-Goals:**
- 不做 Mac 终端内的完整交互式 TUI
- 不做多用户并发控制（单用户系统）
- 不做 openclaw provider 的持久化（仅 claude/book）
- 不做 Android 端离线消息缓存
- session-viewer 本次只做 CLI 纯文本只读模式

## Decisions

### D1: 使用 `-p --input-format stream-json --output-format stream-json` 持久进程

**选择**: 单进程常驻，通过 JSON stdin/stdout 双向通信

**否决方案 1**: 不用 `-p`，裸交互模式 → Spike 证实需要 TTY，pipe 不工作
**否决方案 2**: `-p` + `-r` 顺序 spawn → 可行但每轮 9-13s，体验差
**否决方案 3**: PTY → 过于复杂，且 spike 证实不需要
**否决方案 4**: Claude SDK/API → 不支持完整 tool use

### D2: 新增 `idle` 状态

**选择**: `idle` 表示"本轮完成、进程存活、等待输入"

```
queued     → running, failed
running    → idle, completed, failed, cancelled
idle       → running, completed, cancelled
completed  → running  (resume，用于进程已死的恢复)
failed     → running  (resume)
cancelled  → (terminal)
```

`idle` 与 `completed` 的区别：
- `idle`: 进程存活，用户可直接发消息（companion 写 stdin）
- `completed`: 进程已退出，需要显式 resume（重新 spawn）

### D3: `send_message` 在 `idle` 时直接写 stdin

**选择**: relay 收到 `send_message` 且 session 在 `idle` 状态 → transition 到 `running` → dispatch `send_message` 给 companion → companion 用 JSON 格式写 stdin

与 `running` 状态下的 `send_message` 行为一致，只是 `idle` 时需要先 transition 到 `running`。不需要新命令类型，复用现有 `send_message`。

### D4: `session_result` 在持久 session 中映射为 `session_idle`

**选择**: companion 收到 CLI 的 `{ type: "result" }` 事件后：
- 如果进程仍存活（`child.exitCode === null`）→ emit `session_idle`，relay 转为 `idle`
- 如果进程已退出 → emit `session_result`，relay 转为 `completed`

### D5: 新增 `complete_session` 命令

**选择**: 客户端发 `complete_session` → relay → companion → SIGTERM 进程 → 进程退出 → `session_result` → `completed`

与 `cancel_session` 的区别：`cancel` → `cancelled`（不可 resume），`complete` → `completed`（可 resume）

### D6: idle timeout 自动结束

**选择**: companion 对 idle session 设置定时器（默认 30min），超时自动 SIGTERM → `completed`

避免进程泄漏（用户忘记 complete），timer 在每次 `send_message` 时重置。

### D7: companion 重启恢复

**选择**: companion 启动时检查 sessionIndex 中的活跃 session → emit `session_error`（`companion_restart`）→ relay 标记为 `failed`。用户可从 Android resume。

### D8: session-viewer 作为独立 CLI 包

`packages/viewer/`，连接 relay WebSocket `/v1/ws`，实时格式化输出 session 事件。

## Risks / Trade-offs

- **[进程泄漏]** idle session 进程常驻 → 缓解：idle timeout 30min 自动 SIGTERM
- **[状态机复杂度]** `idle` 新增 transition edge → 缓解：显式枚举 + 全量测试
- **[进程意外退出]** idle 状态下进程崩溃 → 缓解：`handleClose` 检测到非预期退出后 emit `session_error`
- **[stdin 消息格式]** 依赖 Claude CLI 的 stream-json input 协议稳定性 → 缓解：Happy 项目已验证该协议
- **[DB 迁移]** sessions 表 CHECK 约束需添加 `idle` → 缓解：改用 application-level 校验或重建表

## Migration Plan

1. Wire 包先发：新增 `idle` status、`session_idle` event、`complete_session` command
2. Relay 升级：状态机扩展、新 route、DB schema migration
3. Companion 升级：`ClaudeRuntimeAdapter` 重构（stream-json 双向协议、idle 管理、timeout）
4. Android 升级：session detail UI 支持 idle 状态连续输入
5. Viewer 包新增
