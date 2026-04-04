# Design: Initial User Message Echo for Companion-backed Sessions

## Problem

Companion-backed provider 在 `create_session` 启动时，ack 只告诉 relay `provider_session_id`，并不会自动回放首个 prompt 的 `user_message`。

Android 详情页虽然有本地 optimistic user message，但它只在当前进程内有效；一旦页面重载或依赖历史 events 重建 timeline，这条消息就不存在了。

## Decision

relay 在以下两个启动路径上主动补写 `user_message` event：

1. `POST /sessions` 且带初始 `prompt`
2. 空 `idle` session 的首条 `POST /sessions/:id/message`

仅对 `claude` / `book` 启用，不对 `openclaw` 启用，避免和 OpenClaw gateway 可能已有的用户消息回显重复。

## Event Ordering

补写发生在 provider ack 成功后、会话进入稳定运行前。这样可以让第一条 `user_message` 尽量早于后续 assistant events 进入 relay timeline。

## File Changes

| File | Change |
|------|--------|
| `packages/relay/src/session/orchestrator.ts` | 启动路径补写 synthetic `user_message` |
| `tests/unit/relay-lifecycle.test.mjs` | 覆盖创建/首发消息两条路径 |
| `tests/contract/relay-session-lifecycle.contract.test.mjs` | 验证实际 REST/WS 行为 |
| `docs/engineering-spec/02_Technical_Design/API_SPEC.md` | 记录初始 prompt 会写入 timeline |
| `docs/engineering-spec/02_Technical_Design/BUSINESS_LOGIC.md` | 记录 companion-backed 首消息回显规则 |
