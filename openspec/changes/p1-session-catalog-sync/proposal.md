# Proposal: 主动 Session Catalog 同步

## Problem

当前双端 session 同步只覆盖“companion 连接 relay 时做一次对账”。

这导致两个实际问题：

1. 用户在 Mac 原生 `claude` / `book` 中新建 session 后，如果 companion 没有重连，relay 永远不会知道这个 session。
2. 已存在的本地 session 在 Mac 侧再次活跃后，relay 不会刷新它的 `last_active_at`，Android 首页也不会主动刷新列表，因此这些 session 不会按“最近活跃”浮到前面。

## Scope

1. companion 在连接期间周期执行本地 session 对账
2. 对已知本地 session，如果 transcript 文件活跃时间变新，relay 刷新其 `last_active_at`
3. relay session list API 改为按 `last_active_at DESC` 排序，符合 PRD
4. relay 在本地 session catalog 发生变化时向 Android 广播轻量提示
5. Android Home 收到提示后自动刷新 session list

## Out of Scope

- session transcript 内容同步（由 `p1-session-transcript-sync` 负责）
- 变更 Android detail 页的事件订阅模型
- 多用户/多 host 策略调整

## Success Criteria

1. Mac 原生新建的 claude/book session 在 companion 已连接的前提下，无需手动下拉即可出现在 Android 首页
2. Mac 侧再次活跃的旧 session 会更新 relay `last_active_at`，并在 Android 首页按“最近活跃”重新排序
3. `/v1/sessions` 的第一页能返回最近活跃的 session，而不是仅按创建时间排序
4. 新增回归测试覆盖周期对账、freshness 刷新、Android 首页自动刷新
