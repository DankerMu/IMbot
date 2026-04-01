## Why

当前 IMbot 使用 `claude -p` 单次模式：每次 create/resume 都 spawn 一个新进程，Claude 回复后进程退出，session 进入 `completed`/`failed` 终态。用户无法在手机上与 Mac 上的 Claude 持续对话——每轮交互都要重新 spawn、重新加载上下文（9-13s），体验割裂且延迟高。

Spike 验证了 Claude CLI 原生支持 `-p --input-format stream-json --output-format stream-json` 双向 JSON streaming 协议：进程常驻，通过 stdin 发送 JSON 格式消息，stdout 实时流式输出事件，每轮 ~2s，无需重新 spawn。

## What Changes

- **BREAKING**: `create_session` 改用 `-p --input-format stream-json --output-format stream-json --verbose` 常驻模式，进程不在首次回复后退出
- **BREAKING**: session 状态机新增 `idle` 状态，`session_result` 在进程存活时映射为 `session_idle`（不自动转 `completed`）
- `send_message` 在 `idle` 状态下将 JSON 消息写入进程 stdin（当前仅 `running` 时写 stdin）
- 新增 `complete_session` 命令，显式结束 session（SIGTERM 进程 → `completed`）
- 新增 idle timeout（默认 30min），防止进程泄漏
- `resume_session` 保留，用于 companion 重启后恢复已中断的 session
- Android 端 session detail 支持 `idle` 状态多轮连续输入
- 新增 Mac 端 CLI session viewer 实时展示事件流

## Capabilities

### New Capabilities
- `persistent-runtime`: companion 持久进程管理——stream-json 双向协议、进程常驻、idle 状态管理、idle timeout、进程健康检测
- `session-viewer`: Mac 端 CLI session viewer——连接 relay WS、实时渲染 session 事件流

### Modified Capabilities
- `session-lifecycle`: 状态机新增 `idle` 状态、transition 规则变更、`complete_session` 命令、`session_idle` 事件
- `android-session-detail`: detail 界面支持 `idle` 状态连续输入、实时多轮对话

## Impact

- **Wire protocol**: enums、commands、messages 新增，wire 包 minor version bump
- **Relay**: orchestrator 状态机扩展、transitions 表扩展、REST API 新增 `POST /sessions/:id/complete`
- **Companion**: `ClaudeRuntimeAdapter` 核心重构——spawn 参数改为 stream-json 协议、stdin JSON 写入、idle timer、`complete_session` 命令
- **Android**: session detail UI 支持 idle 状态连续输入
- **数据库**: sessions 表新增 `idle` 状态值
- **测试**: 单元/合约/集成测试全面更新，新增 multi-turn 场景
