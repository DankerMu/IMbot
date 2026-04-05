# Tasks: 主动 Session Catalog 同步

## 1. Companion

- [x] 1.1 `session-discovery` 输出 `last_active_at`，并尽量保留真实 `created_at`
- [x] 1.2 `SessionIndexEntry` 增加 `last_observed_at`
- [x] 1.3 `SessionReconciler` 对“已知但本地更活跃”的 session 重新上报
- [x] 1.4 `CompanionRuntime` 在 relay 连接期间周期执行 `reconcile()`

## 2. Relay

- [x] 2.1 `handleReportLocalSessions()` 对已存在 session 刷新 `last_active_at`
- [x] 2.2 shadow session 初次插入时使用本地上报的 `last_active_at`
- [x] 2.3 local sync 造成 create/update 时，广播 `sessions_changed`
- [x] 2.4 `/v1/sessions` 改为 `ORDER BY last_active_at DESC, created_at DESC`

## 3. Android

- [x] 3.1 解析新的 `sessions_changed` WS 消息
- [x] 3.2 Home 收到 `sessions_changed` 后自动调用 `refreshFromApi()`
- [x] 3.3 本地分页查询顺序与 relay 对齐，避免 stale-page 计算失真

## 4. Tests

- [x] 4.1 companion unit: 已知 session 本地活跃时间变新时会重新上报
- [x] 4.2 relay unit: local sync 会刷新 `last_active_at` 并广播 `sessions_changed`
- [x] 4.3 relay unit: `/v1/sessions` 按最近活跃排序
- [x] 4.4 Android unit: `sessions_changed` 触发 Home 自动刷新
