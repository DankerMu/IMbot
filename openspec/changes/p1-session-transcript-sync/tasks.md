# Tasks: 远端 Transcript 增量同步

## 1. Companion

- [ ] 新增 `TranscriptSyncer`，轮询 remote session transcript JSONL
- [ ] 初次同步前查询 relay `GET /sessions/:id`，取 `last_active_at` 作为截断点
- [ ] 解析 transcript 中新增的 `user` / `assistant` 文本行
- [ ] assistant usage 存在时额外发 `session_usage`
- [ ] 对当前由 `ClaudeRuntimeAdapter` 管理的活跃 provider session，改为按 relay `last_active_at` 动态截断去重，而不是整会话跳过
- [ ] relay 连接时启动轮询，断开时停止轮询

## 2. Relay

- [ ] 扩展 companion event message，支持标记 `source: "transcript_sync"`
- [ ] `SessionOrchestrator.handleEvent()` 仅对 transcript_sync 的 `user_message` / `assistant_message` / `session_usage` 放开终态会话接纳
- [ ] 其余 late runtime events 维持现有拒绝策略

## 3. Tests

- [ ] unit: transcript sync 首次扫描时只导入 `last_active_at` 之后的新增 transcript turn
- [ ] unit: transcript sync 后续轮询只导入追加行，不重复导入旧行
- [ ] unit: transcript sync 在当前活跃 provider session 上仍能补同步外部追加 turn，并过滤已由 runtime 转发的 turn
- [ ] unit: relay 对普通 late events 继续拒绝
- [ ] unit: relay 对 transcript_sync late message events 允许写入

## 4. Verification

- [ ] 重新构建并部署 companion 到 `~/.imbot/companion/dist`
- [ ] 在同一个 remote `book` session 上，Mac 原生 CLI 追加一轮消息
- [ ] 验证 Android 详情页收到新增 user/assistant turn
