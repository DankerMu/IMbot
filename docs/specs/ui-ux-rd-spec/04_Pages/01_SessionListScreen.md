# Page: SessionListScreen (Home)

## 概述

| Key | Value |
|-----|-------|
| Route | `/` (default) |
| ViewModel | `HomeViewModel` |
| PRD ref | FR-01, FR-05 |

App 的主页面。展示所有会话列表，支持按 provider 过滤，可快速进入详情或创建新会话。
视觉上采用“editorial header + grouped list”的结构：顶部只保留 eyebrow、主标题、summary pills 与 filter，不再加入冗余说明文案。
长按任意会话可进入多选模式，批量删除多个会话。

## 布局

```
┌──────────────────────────────────────┐
│  Workspace Console                  │  ← Eyebrow
│  Sessions        [online] [3 running]│  ← 标题 + 摘要 pill
│  [全部] [Claude] [book] [OpenClaw]   │  ← segmented filters
├──────────────────────────────────────┤
│  [ConnectionBanner if disconnected]  │
├──────────────────────────────────────┤
│  Running now                         │  ← section label
├──────────────────────────────────────┤
│                                      │
│  ┌──────────────────────────────┐    │
│  │ provider/meta                │    │
│  │ Prompt summary               │    │  ← Running sessions 置顶
│  │ /workspace/path              │    │  ← 卡片压缩到高密度节奏
│  └──────────────────────────────┘    │
│                                      │
│  Recent                              │  ← section label
├──────────────────────────────────────┤
│  ┌──────────────────────────────┐    │
│  │ provider/meta                │    │  ← 按 lastActiveAt 倒序
│  │ Prompt summary               │    │
│  │ /workspace/path              │    │
│  └──────────────────────────────┘    │
│  ...                                 │
│                                      │
│                              [+ FAB] │  ← 新建会话
├──────────────────────────────────────┤
│  🏠 会话  │  📁 目录  │  ⚙ 设置      │  ← Bottom Nav
└──────────────────────────────────────┘
```

选择模式：

```
┌──────────────────────────────────────┐
│  已选 3 项      [全选] [删除] [完成] │
├──────────────────────────────────────┤
│  [✓] SessionCard                     │
│  [ ] SessionCard                     │
│  [✓] SessionCard                     │
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
    val isConnected: Boolean = true,
    val selectedSessionIds: Set<String> = emptySet(),
    val isDeletingSelection: Boolean = false
)
```

### 排序规则

1. `running` sessions 在最前（按 `lastActiveAt` 倒序）。
2. 其余 sessions 按 `lastActiveAt` 倒序。
3. 同一 status 内按时间倒序。

### 过滤

顶部 filter 使用横向 segmented pills：
- 全部（默认）
- Claude Code
- book
- OpenClaw

标题区摘要 pills：
- relay 在线状态
- running session 数
- 当前列表总量

## 事件

| UI Event | Handler | Side Effect |
|----------|---------|-------------|
| Tap SessionCard | `navigateToDetail(id)` | Shared element transition |
| Long press SessionCard | `enterSelectionMode(id)` | 进入多选模式并选中当前会话 |
| Tap SessionCard in selection mode | `toggleSelection(id)` | 勾选/取消勾选 |
| Tap FAB | `navigateToNewSession()` | — |
| Pull to refresh | `viewModel.refresh()` | API call |
| Swipe left on card | `viewModel.deleteSession(id)` | Confirmation → API |
| Filter change | `viewModel.setFilter(provider)` | 本地过滤 |
| Tap bulk delete | `viewModel.deleteSelectedSessions()` | Confirmation → 顺序调用单删 API |

## 验收标准

- [ ] App 启动后 < 2s 看到列表或 loading。
- [ ] Running session 置顶且有脉冲动画。
- [ ] 顶部标题区能清晰展示连接状态与会话摘要，且不出现解释性冗余句子。
- [ ] 常见大屏手机上一屏可稳定看到至少 3 张 session card。
- [ ] 过滤器切换即时生效。
- [ ] 下拉刷新正常。
- [ ] 空状态展示正确。
- [ ] FAB 点击进入新建流程。
- [ ] 左滑删除有确认弹窗。
- [ ] 长按可进入多选模式。
- [ ] 多选模式下可批量删除多个会话。
- [ ] 断线时显示 ConnectionBanner。
