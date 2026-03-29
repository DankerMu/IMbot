# Page: SessionListScreen (Home)

## 概述

| Key | Value |
|-----|-------|
| Route | `/` (default) |
| ViewModel | `HomeViewModel` |
| PRD ref | FR-01, FR-05 |

App 的主页面。展示所有会话列表，支持按 provider 过滤，可快速进入详情或创建新会话。

## 布局

```
┌──────────────────────────────────────┐
│  IMbot                    [filter ▼] │  ← TopAppBar
├──────────────────────────────────────┤
│  [ConnectionBanner if disconnected]  │
├──────────────────────────────────────┤
│                                      │
│  ┌──────────────────────────────┐    │
│  │ SessionCard (running, pulse) │    │  ← Running sessions 置顶
│  └──────────────────────────────┘    │
│  ┌──────────────────────────────┐    │
│  │ SessionCard (running, pulse) │    │
│  └──────────────────────────────┘    │
│  ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─    │  ← 分隔线
│  ┌──────────────────────────────┐    │
│  │ SessionCard (completed)      │    │  ← 按 lastActiveAt 倒序
│  └──────────────────────────────┘    │
│  ┌──────────────────────────────┐    │
│  │ SessionCard (failed)         │    │
│  └──────────────────────────────┘    │
│  ...                                 │
│                                      │
│                              [+ FAB] │  ← 新建会话
├──────────────────────────────────────┤
│  🏠 会话  │  📁 目录  │  ⚙ 设置      │  ← Bottom Nav
└──────────────────────────────────────┘
```

## 状态机

```
         App Launch
             │
             ▼
      ┌─────────────┐
      │   Loading    │ ── shimmer skeleton (3 cards)
      └──────┬──────┘
             │
     ┌───────┴────────┐
     ▼                ▼
┌─────────┐    ┌────────────┐
│  Empty   │    │   Loaded    │
│  State   │    │   (list)    │
└─────────┘    └─────┬──────┘
                     │
              ┌──────┴──────┐
              ▼             ▼
       ┌──────────┐  ┌──────────┐
       │ Refreshing│  │  Error    │ ── Snackbar + 保留旧数据
       └──────────┘  └──────────┘
```

## 数据契约

```kotlin
data class HomeUiState(
    val sessions: List<SessionSummary> = emptyList(),
    val filter: Provider? = null,          // null = All
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val isConnected: Boolean = true
)
```

### 排序规则

1. `running` sessions 在最前（按 `lastActiveAt` 倒序）。
2. 其余 sessions 按 `lastActiveAt` 倒序。
3. 同一 status 内按时间倒序。

### 过滤

TopAppBar 右侧 filter dropdown：
- All（默认）
- Claude Code
- book
- OpenClaw

## 事件

| UI Event | Handler | Side Effect |
|----------|---------|-------------|
| Tap SessionCard | `navigateToDetail(id)` | Shared element transition |
| Tap FAB | `navigateToNewSession()` | — |
| Pull to refresh | `viewModel.refresh()` | API call |
| Swipe left on card | `viewModel.deleteSession(id)` | Confirmation → API |
| Filter change | `viewModel.setFilter(provider)` | 本地过滤 |

## 验收标准

- [ ] App 启动后 < 2s 看到列表或 loading。
- [ ] Running session 置顶且有脉冲动画。
- [ ] 过滤器切换即时生效。
- [ ] 下拉刷新正常。
- [ ] 空状态展示正确。
- [ ] FAB 点击进入新建流程。
- [ ] 左滑删除有确认弹窗。
- [ ] 断线时显示 ConnectionBanner。
