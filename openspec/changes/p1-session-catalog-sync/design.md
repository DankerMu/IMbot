# Design: 主动 Session Catalog 同步

## Overview

本变更把“本地 session 可见性”从一次性对账扩展为持续同步闭环：

1. companion 周期扫描本地 projects 目录
2. 新 session 或“本地活跃时间变新”的已知 session 都会重新上报 relay
3. relay 用上报的本地活跃时间刷新 `sessions.last_active_at`
4. relay 向 Android 广播 `sessions_changed`
5. Android Home 收到后触发一次轻量刷新

## Key Decisions

### D1. companion 使用固定周期轮询，而不是依赖文件系统 watcher

- Claude / Book projects 目录结构稳定，但不同 provider / shell / resume 路径下 watcher 行为容易有差异
- 周期对账复用现有 `SessionReconciler` 与 discovery 逻辑，最小改动即可覆盖 reconnect 之外的本地创建/活跃场景

### D2. 已知 session 是否需要重新上报，按“本地活跃时间”判断

- `session-discovery` 输出 `created_at` 与 `last_active_at`
- `SessionIndex` 记录 `last_observed_at`
- 当 discovery 发现 `last_active_at > last_observed_at` 时，视为 catalog freshness 变化，需要重新上报 relay

### D3. relay `/sessions` 改为按 `last_active_at DESC, created_at DESC` 排序

- PRD 明确要求首页默认按“最近活跃”排序
- Android 本地缓存虽然也会按 `last_active_at` 排序，但如果 relay 第 1 页仍按 `created_at` 返回，旧但最近活跃的 session 可能根本进不了第一页

### D4. Android 首页不做固定轮询，改为响应 relay 广播的 catalog hint

- 避免首页常驻定时拉取
- 只有 relay 确认本地 session catalog 发生变化时才触发刷新

## Impacted Modules

- `packages/companion/src/index.ts`
- `packages/companion/src/runtime/session-discovery.ts`
- `packages/companion/src/runtime/session-index.ts`
- `packages/companion/src/runtime/session-reconciler.ts`
- `packages/relay/src/session/orchestrator.ts`
- `packages/relay/src/routes/sessions.ts`
- `packages/android/app/src/main/kotlin/com/imbot/android/network/ServerMessage.kt`
- `packages/android/app/src/main/kotlin/com/imbot/android/ui/home/HomeViewModel.kt`
