# Proposal: 远端 Transcript 增量同步

## Problem

当前 `p1-dual-session-sync` 只解决了 session 可见性和可恢复性，没有解决 session 内容同步。

这导致一个实际使用缺口：

1. Android 创建并打开一个 `book`/`claude` session
2. 用户在 Mac 原生 CLI 上对同一个 provider session 继续追加消息
3. 本机 transcript 已写入新增 user/assistant turn
4. relay 和 Android 详情页收不到这些新增 turn

根因是 companion 目前只做两件事：

- 运行自己拉起的 runtime 时，把 stdout 流式事件转发给 relay
- 连接/重连时做 `report_local_sessions` 元数据同步

它没有监听本机 transcript 文件的“新增内容”。

## Scope

1. companion 轮询已知 remote session 对应的 transcript JSONL
2. 将新增的 `user` / `assistant` 文本 turn 增量转发到 relay
3. relay 允许这类来自 transcript sync 的消息事件进入 `completed` / `failed` / `cancelled` 会话
4. 当同一 provider session 已被 companion resume 且保持活跃时，仍能补同步外部原生 CLI 追加的 turn
5. 保持现有 runtime 流式事件路径不变，并通过 relay `last_active_at` 截断避免重复发事件

## Out of Scope

1. 为纯本地 shadow session 做完整历史消息回填
2. 让“已经打开着的原生 `book --resume` 终端界面”实时订阅 Android 新消息
3. 对 transcript 内所有 tool/approval 结构做完整重建

## Success Criteria

1. Mac 原生 CLI 在已存在的 remote session 上追加消息后，Android 详情页能看到新增 user/assistant turn
2. companion 自己拉起并保持活跃的 session，不会因 transcript 轮询而产生重复消息，同时仍能补同步外部原生 CLI 追加的 turn
3. relay 仍然拒绝普通 late runtime events，但允许明确标记为 transcript sync 的 late message events
4. 新增 unit tests 覆盖 transcript 增量导入和 relay 终态会话接纳逻辑
