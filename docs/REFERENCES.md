# 参考项目索引

## happy 项目

**路径**: `../../happy` (即 `/Users/danker/Desktop/AI-vault/happy`)

Happy 是一个开源的"本地代理执行 + 云端同步 + 移动/Web 控制"系统，IMbot 的架构设计部分参考了该项目。

### 开发时值得查阅的文件

| 开发任务 | 查阅路径 | 参考内容 |
|---------|---------|---------|
| Companion 的 Claude CLI 集成 | `happy/packages/happy-cli/src/claude/` | CLI session 管理、stream-json event 流转换、session protocol mapping |
| Companion 的 Claude session 协议映射 | `happy/docs/session-protocol-claude.md` | CLI stream-json output → 统一 session envelope 的映射规则 |
| OpenClaw bridge 实现 | `happy/packages/happy-cli/src/openclaw/` | OpenClaw WebSocket 连接、session 管理、集成测试 |
| Relay 的 wire 协议设计 | `happy/packages/happy-wire/src/` | 共享事件类型、消息信封、协议版本化 |
| Relay 的 Fastify 后端结构 | `happy/packages/happy-server/sources/` | Fastify + Socket.IO 装配、路由模块化、auth decorator |
| 统一 session 协议 | `happy/docs/session-protocol.md` | 9 种事件类型、envelope 格式、turn 管理、subagent 处理 |
| 后端架构概览 | `happy/docs/backend-architecture.md` | 系统拓扑、进程生命周期、数据流 |
| 工程规格（如需深入） | `happy/spec/` | 按 00-09 分类的完整实现级规格 |
