# Design: Mobile Skill & Tool Interaction

## Architecture Overview

```
用户输入 "/" → SlashCommandSheet 弹窗
    ├── 静态命令列表（hardcoded skills metadata）
    └── 动态命令列表（从 relay GET /hosts/:id/skills 获取，未来扩展）

选中命令 → InputBar 上方显示 CommandChip
    └── 用户输入参数 + 发送 → 组装 "/<command> <args>" 发送

AskUserQuestion tool_call 事件到达 → 特殊渲染卡片
    └── 用户填写回答 → POST /sessions/:id/input → companion 接收
```

## Component Design

### 1. SlashCommandSheet (新增)

**触发**：用户在 InputBar 输入 `/` 作为首字符时，自动弹出 ModalBottomSheet。

**数据源**：
```kotlin
data class SkillItem(
    val command: String,       // e.g. "commit"
    val label: String,         // e.g. "Git Commit"
    val description: String,   // e.g. "创建一个 git commit"
    val category: SkillCategory, // BUILT_IN / AGENT_SKILL / SLASH_COMMAND
    val icon: ImageVector?,
)

enum class SkillCategory { BUILT_IN, AGENT_SKILL, SLASH_COMMAND }
```

**初始版本**：hardcoded 常用命令列表（commit, review, test, help 等），从 companion 的已知 skills 提取。

**未来扩展**：relay 增加 `GET /hosts/:hostId/skills` API，companion 启动时上报 skill manifest。

**UI 布局**：
- ModalBottomSheet，最大高度 50% 屏幕
- 顶部搜索框（实时过滤）
- 分组列表：按 category 分组，每组带 header
- 每行：icon + command name + description（单行 ellipsis）
- 点击行 → 关闭 sheet → InputBar 上方插入 CommandChip

### 2. CommandChip (新增)

**位置**：InputBar 上方，与输入框之间 8dp 间距。

**布局**：
```
┌──────────────────────────────┐
│  / commit  ·  Git Commit  ✕ │
└──────────────────────────────┘
┌──────────────────────────────┐
│  输入提交信息...              │  ← InputBar placeholder 动态切换
└──────────────────────────────┘
```

- Chip 使用 `AssistChip` 样式，primary 色边框
- ✕ 按钮取消命令模式
- 输入内容作为命令参数，发送时组装为 `/<command> <args>`

### 3. InteractiveToolCard (新增)

处理需要用户响应的 tool_call 事件，目前聚焦 `AskUserQuestion`。

**识别规则**：
```kotlin
fun isInteractiveToolCall(toolName: String): Boolean =
    toolName in setOf("AskUserQuestion", "askuserquestion")
```

**AskUserQuestion 卡片**：
```
┌─────────────────────────────────────┐
│  ❓ Agent 提问                       │
│                                     │
│  你希望使用哪种数据库？                 │
│                                     │
│  ┌─────────┐ ┌─────────┐           │
│  │ SQLite  │ │ Postgres│           │  ← 如果有预设选项
│  └─────────┘ └─────────┘           │
│                                     │
│  ┌──────────────────────── ┐        │
│  │ 输入自由回答...          │        │  ← 自由输入
│  └────────────────────────┘         │
│                    [ 提交回答 ]       │
└─────────────────────────────────────┘
```

**数据解析**：从 tool_call 的 `input` JSON 中提取 `question` 字段和可选的 `options` 字段。

**提交**：调用 `POST /sessions/:id/input` body `{ "text": "<answer>" }`。

### 4. ApprovalCard (增强)

当前 `approval_required` 事件仅渲染为 StatusChangeBubble 文本。增强为可操作卡片：

```
┌─────────────────────────────────────┐
│  ⚠️ 需要审批                         │
│                                     │
│  Tool: bash                         │
│  Command: rm -rf /tmp/build         │
│                                     │
│  ┌─────────┐  ┌─────────┐          │
│  │  批准 ✓  │  │  拒绝 ✗  │          │
│  └─────────┘  └─────────┘          │
└─────────────────────────────────────┘
```

## Data Flow

### Slash Command Flow
```
1. User types "/" in InputBar
2. InputBar detects "/" prefix → emit onSlashTrigger()
3. SessionDetailScreen shows SlashCommandSheet
4. User selects command → CommandChip shown above InputBar
5. User types args + sends → "/<command> <args>" sent via existing sendMessage API
6. Agent receives and processes the slash command
```

### Tool Interaction Flow
```
1. WS event arrives: tool_call_started with toolName="AskUserQuestion"
2. DetailViewModel maps to MessageItem.InteractiveToolCall
3. SessionDetailScreen renders InteractiveToolCard
4. User types answer + taps submit
5. POST /sessions/:id/input { text: "<answer>" }
6. Companion forwards to agent → tool_call_completed event
7. Card transitions to answered state (read-only, gray background)
```

## File Changes

| File | Change |
|------|--------|
| `ui/detail/InputBar.kt` | 检测 `/` 前缀，emit onSlashTrigger；支持 CommandChip 上方 slot |
| `ui/detail/SlashCommandSheet.kt` | 新增 ModalBottomSheet composable |
| `ui/detail/CommandChip.kt` | 新增 AssistChip composable |
| `ui/detail/InteractiveToolCard.kt` | 新增 AskUserQuestion / Approval 交互卡片 |
| `ui/detail/SessionDetailScreen.kt` | 集成上述组件到消息列表和输入栏 |
| `ui/detail/DetailViewModel.kt` | 新增 MessageItem.InteractiveToolCall 类型，解析 tool_call input |
| `ui/detail/DetailUtils.kt` | 新增 isInteractiveToolCall() 判断函数 |

## Constraints

- 不修改 relay/companion 协议——所有交互通过现有 `sendInput` API
- 初始版本 skill 列表 hardcoded，不需要 relay 新 API
- Approval 功能受限于当前 `p3-approval-path-reserved` 的预留设计，默认 bypassPermissions 模式下不会触发
