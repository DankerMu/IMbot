# Design: 远端 Transcript 增量同步

## 背景

现有链路分成两层：

1. `SessionReconciler` 只同步“有哪些本地 session”
2. `ClaudeRuntimeAdapter` 只转发“companion 自己当前拉起的 session 流式事件”

缺失的是第三层：

3. 本机已有 transcript 文件发生追加写入时，如何把新增内容补发给 relay

## 设计目标

- 不改现有 runtime 主路径
- 不依赖上游 CLI 提供额外 API
- 能覆盖 companion 在线期间以及 companion 重启后的“漏同步补偿”
- 避免与 companion 自己活跃 runtime 的事件重复

## 方案概览

### D1. 新增 `TranscriptSyncer`

companion 新增一个后台组件，职责：

1. 遍历 `SessionIndex` 中的已知 remote session
2. 找到对应 transcript 文件
3. 读取新增 JSONL 行
4. 仅提取：
   - `user` → `user_message`
   - `assistant` 文本块 → `assistant_message`
   - assistant usage → `session_usage`
5. 通过 WS 再次发给 relay，但打上 `source: "transcript_sync"`

### D2. 只同步“已有 relay session id”的 remote session

`SessionIndex` 当前同时包含两类 key：

- remote session：`<relay_session_id>`
- local shadow 索引：`local:<provider_session_id>`

本次仅处理前者，因为只有前者可以直接作为 relay `session_id` 发事件。

这样可以直接解决“Android 已知 session 与 Mac 原生 CLI 双端继续对话”的问题。

### D3. 初次同步使用 relay `last_active_at` 作为截断点

对于一个 remote session，如果 companion 是首次开始轮询它：

1. 先请求 relay `GET /sessions/:id`
2. 读取 `last_active_at`
3. 扫描 transcript 全文件
4. 只导入 `timestamp > last_active_at` 的 transcript entries

这样可以补回：

- companion 重启期间 Mac 原生 CLI 追加的 turn

同时避免重复导入：

- 先前已经通过 runtime 正常进 relay 的旧 turn

### D4. 活跃 runtime session 改为“动态截断去重”

如果某个 provider session 当前正由 `ClaudeRuntimeAdapter` 管理，不能再整会话跳过 transcript 导入。

否则会漏掉一种真实场景：

- Android 详情页已经 `resume` 了该 session
- companion 本地也持有这个活跃 runtime
- 用户又在 Mac 原生命令行 `book --resume` / `claude --resume` 上继续追加消息

此时新增 turn 已写入 transcript，但不会走 companion runtime stdout，因此“整会话跳过”会把这些外部 turn 一并漏掉。

新的策略是：

- 活跃 session 仍允许扫描 transcript 增量
- 但每次扫描前，都重新读取 relay `last_active_at`
- 只导入 `timestamp > last_active_at` 的 transcript entries

这样：

- companion 自己已实时转发到 relay 的 runtime turn，会被新的截断点过滤掉
- 外部原生 CLI 追加的 turn，因为 relay 尚未见过，仍会被补同步进来

### D5. relay 仅为 transcript_sync 开 late-message 口子

当前 relay 会丢弃 `completed` / `failed` / `cancelled` session 的普通 late events。

本次只放开一小类事件：

- `source === "transcript_sync"`
- `event_type ∈ { user_message, assistant_message, session_usage }`

这样：

- 现有 runtime 状态机不变
- 普通异常 late events 依旧被拒绝
- transcript 补发的内容能进入已经结束的 session

## 轮询节奏

- 在 relay WS `connected` 后启动轮询
- 在 `disconnected` 时停止轮询
- 默认 1.5s 一次
- 每轮有互斥保护，避免重入

## 风险

1. 只能保证 Android 看到 Mac 原生 CLI 的新增消息；无法反向让一个已经打开着的原生 CLI TUI 热更新。
2. transcript 里只可靠提取文本消息和 usage，tool/approval 的完整结构仍以 runtime 直连路径为准。
3. 如果 relay `last_active_at` 与 transcript 实际时间戳极端接近，理论上存在边界丢一条的风险；但当前 runtime 会在消息后继续写状态事件，实践上截断点通常晚于最后一条 transcript 文本。
4. 活跃 session 每次有 transcript 增量时都要多做一次 relay session metadata 查询，但频率只发生在“文件确实变更”时，成本可控。
