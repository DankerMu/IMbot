# Tasks: Initial User Message Echo for Companion-backed Sessions

## 1. Relay

- [x] 在 `POST /sessions` 带初始 `prompt` 的成功启动路径补写 `user_message`
- [x] 在空 `idle` session 首条 `POST /sessions/:id/message` 的成功启动路径补写 `user_message`
- [x] 仅对 `claude` / `book` 启用，避免和 OpenClaw 现有回显重复

## 2. Tests

- [x] unit test：prompt create 后存在 `user_message`
- [x] unit test：empty idle first message 后存在 `user_message`
- [x] contract test：REST create with prompt 后 session_events 包含 `user_message`
- [x] contract test：empty idle first message 广播/存储 `user_message`

## 3. Docs

- [x] 更新 API spec
- [x] 更新 business logic
- [x] 更新 OpenSpec index
