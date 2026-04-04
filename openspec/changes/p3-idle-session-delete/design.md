# Design: Idle Session Delete Without State Conflict

## Background

当前 relay 删除逻辑分成三段：

1. `queued` → 直接拒绝
2. `running` + `provider_session_id` → 尝试 `cancel_session`，再删除
3. `idle` + `provider_session_id` → 直接拒绝

问题在于第 3 段与当前产品面完全不一致。对用户来说，`idle` 就是“空闲可操作”，不该比 `running` 更难清理。

## Decision

将 `idle` interactive session 的删除路径并入现有 running-delete 逻辑：

- 条件：`status in ('running', 'idle') && provider_session_id != null`
- 动作：
  - `dispatchCancel(session)` best-effort 执行
  - 无论 companion 是否在线、或该 idle 进程是否还存在，都继续 `transitionWithConflictTolerance(..., 'cancelled')`
  - 最后删除 relay session 记录

## Rationale

1. 复用现有 `running` delete 语义，改动最小。
2. 对 `idle` session，`cancel_session` 是最接近“结束本地等待进程”的已有命令。
3. 若 companion 侧已经没有活跃进程，relay 也不该因此拒绝用户删除请求。

## Non-Goals

1. 本次不新增“删除本地 transcript 文件”的 companion 命令。
2. 本次不改变 native CLI 本地历史的保留策略。
3. 本次不改 `queued` delete 的保护规则。
