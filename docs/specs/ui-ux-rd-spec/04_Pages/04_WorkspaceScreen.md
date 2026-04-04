# Page: WorkspaceScreen

## 概述

| Key | Value |
|-----|-------|
| Route | Bottom Nav tab 2 |
| ViewModel | `WorkspaceViewModel` |
| PRD ref | FR-02, FR-04 |

管理 workspace 根目录 + 浏览目录下的历史 session。
视觉上与首页保持一致：使用 editorial header、summary pills 与按 host 分组的紧凑卡片。

## 布局

```
┌──────────────────────────────────────┐
│  WORKSPACE INDEX                    │  ← Eyebrow
│  Roots      [2 online] [2 hosts]    │  ← 标题 + 摘要 pill
│             [2 roots]               │
├──────────────────────────────────────┤
│                                      │
│  MacBook Pro   [在线]                │  ← Host section
│  2 roots                              │
│  ┌──────────────────────────────┐    │
│  │ 🟠 AI-vault      [Claude Code]│ ✕ │  ← Claude root
│  │    /Users/danker/Desktop/...  │    │
│  └──────────────────────────────┘    │
│  ┌──────────────────────────────┐    │
│  │ 🟣 novel            [book]   │ ✕ │  ← book root
│  │    /Users/danker/Desktop/...  │    │
│  └──────────────────────────────┘    │
│                                      │
│  Relay VPS     [在线]                │  ← Host section
│  0 roots                              │
│  ┌──────────────────────────────┐    │
│  │ 🔴 projects                   │ ✕ │  ← OpenClaw root
│  │    /opt/projects              │    │
│  └──────────────────────────────┘    │
│                                      │
├──────────────────────────────────────┤
│  🏠 会话  │  📁 目录  │  ⚙ 设置      │
└──────────────────────────────────────┘
```

### Tap root → 展开目录 + session 列表

```
┌──────────────────────────────────────┐
│  ← AI-vault                         │  ← 进入 root 详情
├──────────────────────────────────────┤
│  📁 IMbot                           │
│  📁 projects                        │
│  📁 tools                           │
├──────────────────────────────────────┤
│  该目录下的会话                       │  ← Section header
│  ┌──────────────────────────────┐    │
│  │ SessionCard (completed)      │    │
│  └──────────────────────────────┘    │
│  ┌──────────────────────────────┐    │
│  │ SessionCard (failed)         │    │
│  └──────────────────────────────┘    │
└──────────────────────────────────────┘
```

## 数据契约

```kotlin
data class WorkspaceUiState(
    val hosts: List<HostWithRoots> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

data class HostWithRoots(
    val host: HostInfo,
    val roots: List<WorkspaceRoot>
)

// 目录详情页
data class RootDetailUiState(
    val root: WorkspaceRoot,
    val directories: List<DirectoryEntry> = emptyList(),
    val sessions: List<SessionSummary> = emptyList(),
    val currentPath: String,
    val isLoading: Boolean = true
)
```

## 添加根目录 Flow

点击 "+" 按钮 → Bottom Sheet:

```
┌──────────────────────────────────────┐
│  添加根目录                           │
├──────────────────────────────────────┤
│  Provider: [ 🟠 Claude ▼ ]          │
│  Host: MacBook Pro (auto)            │
│                                      │
│  路径: [ DirectoryBrowser ]          │
│                                      │
│  标签: [ AI-vault ]                  │  ← 可选
│                                      │
│        [ 取消 ]  [ 添加 ]            │
└──────────────────────────────────────┘
```

## 验收标准

- [ ] 根目录列表按 host 分组显示。
- [ ] 顶部以 editorial header + summary pills 概览 host/root 状态。
- [ ] 点击 ✕ 可删除根目录（确认弹窗）。
- [ ] 点击根目录进入目录浏览 + session 列表。
- [ ] 添加根目录流程完整可用。
- [ ] Host offline 时有明确标识。
