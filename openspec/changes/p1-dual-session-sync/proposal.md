# Proposal: 双端 Session 同步

## Problem

从 Android 端创建的 session 在 Mac 本地 `book resume` 中找不到，反之亦然。这意味着用户无法跨设备无缝恢复会话——在手机上开始的对话无法在 Mac 终端继续。

**表现**：
1. Android → relay → companion 创建 book session 后，在 Mac 终端运行 `book resume` 列不到该 session
2. Mac 终端直接 `book` 创建的 session，Android session list 中不一定能看到（取决于 reconciler 的 cwd 过滤是否命中）

**根因分析**：

### 根因 1：Session Discovery 的 cwd 过滤过严

`session-discovery.ts:50-54` 的 `resolveProjectCwd()` 要求 `.claude/projects/<encoded-dir>` 的目录名必须匹配传入的 `cwd` 参数或是其父路径。两个使用点都受影响：

- `list_sessions` 命令（`index.ts:130`）：relay 发来的 cwd 必须精确匹配
- `SessionReconciler.doReconcile()`（`session-reconciler.ts:71`）：用配置的 workspace root path 作为 cwd

如果用户在 Android 创建 session 时选的 cwd 与配置的 workspace root 不完全匹配（例如子目录 vs 父目录），reconciler 就会跳过该 session。

### 根因 2：stream-json 模式的 session 持久化行为未验证

Companion 以 `book --input-format stream-json --output-format stream-json` 启动 provider 进程。stream-json 模式下 `book` 是否将 session 持久化到 `~/.claude/projects/<encoded-cwd>/<session-id>.jsonl`（与交互模式一致）尚未验证。如果 stream-json 模式不写 JSONL 文件，则 native `book resume` 根本无法发现这些 session。

### 根因 3：双向同步缺乏一致性保证

- Companion → Relay（reconciler）：仅上报 status="completed" 的 session，跳过 "unknown"
- Relay → Companion：无反向同步——relay 知道的 session 不会主动推送给 companion
- Session Index 使用 relay session ID 作 key，但 native `book resume` 使用 provider session ID

## Scope

1. 验证 stream-json 模式下 `book`/`claude` 的 session 文件持久化行为
2. 修复 `discoverSessions()` 的 cwd 过滤策略——支持列举指定目录下的所有 session 而不仅限于精确 cwd 匹配
3. 确保 Android 创建的 session 在 native CLI resume 中可见
4. 确保 native CLI 创建的 session 在 Android session list 中可见
5. 为 reconciler 增加必要的宽松匹配或全量扫描模式

## Out of Scope

- 跨 host 的 session 同步（仅同一 companion host）
- OpenClaw session（无 native resume 概念）
- Session 内容/消息的双向同步（仅 session 可见性和 resume 能力）

## Success Criteria

1. 从 Android 创建 book session → Mac 终端 `book resume` 可列出并恢复该 session
2. 从 Mac 终端 `book` 创建 session → Android session list 可看到并打开该 session
3. Reconciler 不因 cwd 编码差异而遗漏 session
4. 现有 session 发现和 resume 功能无回归
5. 新增完整 unit test 覆盖修改后的 discovery 和 reconciler 逻辑
