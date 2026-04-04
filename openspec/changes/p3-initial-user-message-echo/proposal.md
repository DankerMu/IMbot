# Proposal: Initial User Message Echo for Companion-backed Sessions

## Why

当前 `claude` / `book` 会话从首个 prompt 启动时，relay 只会记录 `initial_prompt` 和 `session_started`，却不会写入对应的 `user_message` event。

这会造成两个直接问题：

1. Android 详情页当前页的乐观消息在重进页面后消失
2. 已完成会话回看历史时，看不到第一条用户消息，像是被“吞掉”了

## Scope

- `POST /sessions` 直接携带 `prompt` 启动时，为 `claude` / `book` 补写 `user_message`
- 空 `idle` session 的首条 `POST /sessions/:id/message` 启动 provider 时，为 `claude` / `book` 补写 `user_message`
- 补 unit / contract test 和规格文档

## Out of Scope

- OpenClaw 首条消息回显语义重构
- Android 侧额外的本地补丁或持久化 hack
