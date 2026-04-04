# Proposal: dual-session-persistence

## Why

Companion 用 `--input-format stream-json --output-format stream-json` 启动 Claude Code / Book CLI，这些 session 只存在于 relay SQLite 和 companion 的 `~/.imbot/sessions.json` 映射中。本地直接运行 `claude` 或 `book` 时无法发现、列出或恢复这些 session — 双端 session 存储完全隔离，零同步。

用户在 Android 端创建的 session 应该在 MacBook 上也能通过 `claude --resume` 直接恢复；反之，本地创建的 session 也应该能在 Android 端被发现。当前架构把 CLI 当作无状态 worker，丢失了 CLI 原生的 session 持久化能力。

## What Changes

| Area | Change |
|------|--------|
| Provider config_dir 支持 | `CompanionProviderConfig` 增加 `configDir` 字段。`book` binary 通过 `CLAUDE_CONFIG_DIR=~/.claudebook` 将 session 存到不同目录，companion 需要知道每个 provider 的 projects 路径 |
| discoverSessions per-provider 路径 | 修复 `discoverSessions()` 硬编码 `~/.claude/projects/` 的问题，根据 provider 使用正确的 projects 目录（claude → `~/.claude/projects/`，book → `~/.claudebook/projects/`） |
| Companion session-index 增强 | 在 `~/.imbot/sessions.json` 中记录足够的元数据（provider_session_id、cwd、provider、created_at、source、initial_prompt 摘要），使本地工具能查询 Android 端创建的 session |
| 本地 session 发现 | Companion 启动时扫描 CLI 原生 session 存储（按 provider 使用正确目录），与 relay 已知 session 做双向对账：本地有但 relay 没有的 → 上报；relay 有但本地没有的 → 标记为 remote-only |
| Relay session sync API | `GET /sessions` 返回结果增加 `local_available` 字段，标识该 session 是否在当前 companion 主机上有本地持久化数据 |
| Provider 隔离 | Claude 的 session 归 `~/.claude/projects/` 管理，Book 的 session 归 `~/.claudebook/projects/` 管理（book binary 通过 `CLAUDE_CONFIG_DIR` 环境变量重定向），companion 不混合两者的持久化路径 |

## Capabilities

### New Capabilities
- `session-local-persistence`: 确保 companion 通过 stream-json 模式创建的 session 在 CLI 原生存储中持久化，本地可直接 `--resume`
- `session-bidirectional-sync`: companion 启动时双向对账 — 本地 CLI session 目录与 relay session 列表互通

### Modified Capabilities
<!-- 无现有 spec 需要修改 -->

## Impact

- **packages/companion/**: `claude-adapter.ts` spawn 参数调整；`session-index.ts` 元数据扩展；新增 session 扫描/对账逻辑
- **packages/relay/**: `GET /sessions` 响应增加 `local_available` 字段；可能需要新的 companion→relay 上报接口
- **packages/wire/**: 可能新增 session sync 相关的 command/event 类型
- **packages/android/**: session 列表 UI 可展示 `local_available` 状态（低优先级，可后续迭代）
- **外部依赖**: 需确认 Claude Code CLI 在 stream-json 模式下的持久化行为（可能需要查阅 CLI 源码或实验验证）
