# Proposal: Idle Session Delete Without State Conflict

## Why

当前 Android 详情页和列表都暴露了“删除会话”入口，但 relay 仍对 `idle` 且已有 `provider_session_id` 的交互式 session 返回 `409 state_conflict`。

这会造成明显的 UX 断裂：

1. 用户在模拟器/真机里点击“删除会话”
2. relay 直接报 `state_conflict`
3. Android 只能弹错误，用户无法完成清理

而 relay 对 `running` session 已经支持“先 cancel，再 delete”，`idle` session 没有技术上必须单独报错的理由。

## What Changes

1. `DELETE /v1/sessions/:id` 对 `idle` 且带 `provider_session_id` 的 session，复用现有 running-delete 逻辑：
   - 先尝试 `cancel_session`
   - 再转为 `cancelled`
   - 最后删除 relay session 记录
2. 如果 companion 已离线，或该 `idle` session 已无活跃进程，relay 仍继续完成删除
3. 保留现有约束：
   - `queued` 仍不可删除
   - create/resume/delete 并发冲突仍返回 `409`

## Impact

- `packages/relay/src/session/orchestrator.ts`
- `tests/unit/relay-lifecycle.test.mjs`
- `tests/contract/relay-session-lifecycle.contract.test.mjs`
- `docs/engineering-spec/02_Technical_Design/API_SPEC.md`
