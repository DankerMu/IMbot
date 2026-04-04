# Page: SessionDetailScreen

## 概述

| Key | Value |
|-----|-------|
| Route | `/session/{sessionId}` |
| ViewModel | `DetailViewModel` |
| PRD ref | FR-03, FR-04, FR-05, FR-06, FR-07 |

会话详情页。核心页面，展示消息流、工具调用、状态，支持继续对话，并在可恢复的终态会话上自动执行 resume。
顶栏采用高密度 developer header：首行只承载 provider 身份与右上状态/菜单，第二层只保留目录与 context usage 元信息，不再重复渲染目录尾段标题。
页面主体使用固定 top shell + 可伸缩消息区 + 固定 composer 的三段式结构，保证软键盘弹起时顶部状态栏不会被消息区顶掉。

## 布局

```
┌──────────────────────────────────────┐
│  ← [CC] Claude Code   [● running] [⋮]│  ← provider identity + right status/menu
│     /path/to/project  [12.5k/200k]   │  ← path + usage
├──────────────────────────────────────┤
│  [ConnectionBanner if disconnected]  │
├──────────────────────────────────────┤
│                                      │
│         ┌──────────────────────┐     │
│         │ User: "帮我看架构"    │     │  ← 右对齐
│         └──────────────────────┘     │
│                                      │
│  ┌──┐ ┌───────────────────────┐      │
│  │🤖│ │ Agent: markdown output │      │  ← 左对齐 + provider icon
│  └──┘ │ with **bold** and      │      │
│       │ ```code blocks```      │      │
│       └───────────────────────┘      │
│                                      │
│  ┌────────────────────────────┐      │
│  │ 🔧 grep "Searching..."  ▼ │      │  ← ToolCallCard (折叠)
│  └────────────────────────────┘      │
│                                      │
│  ┌──┐ ┌───────────────────────┐      │
│  │🤖│ │ More agent output...   │      │
│  └──┘ │ ▊                      │      │  ← 光标闪烁 (streaming)
│       └───────────────────────┘      │
│                                      │
│                          [↓ 2 new]   │  ← 回到底部 FAB (if scrolled up)
├──────────────────────────────────────┤
│  ┌─────────────────────┐  ┌────┐    │
│  │ 输入消息...          │  │ ➤  │    │  ← InputBar，IME 弹起后仍固定贴底
│  └─────────────────────┘  └────┘    │
└──────────────────────────────────────┘
```

## 状态机

```
         Enter page
             │
             ▼
      ┌─────────────┐
      │  Loading     │ ── 加载 session info + 历史 events
      └──────┬──────┘
             │
     ┌───────┴────────┐
     ▼                ▼
┌──────────┐   ┌──────────┐
│ Connected │   │ Error     │ ── 全屏错误 + 重试
│ (live)    │   └──────────┘
└─────┬────┘
      │
      ├── WS events arrive → append to list
      ├── User sends message → add to list + POST API
      ├── Session completes → status update + disable input
      ├── Session fails → status update + error message
      ├── WS disconnected → show banner + auto reconnect
      └── WS reconnected → catch-up + hide banner
```

## 数据契约

```kotlin
data class DetailUiState(
    val session: SessionDetail? = null,
    val messages: List<MessageItem> = emptyList(),
    val isLoading: Boolean = true,
    val isConnected: Boolean = true,
    val isCatchingUp: Boolean = false,
    val error: String? = null,
    val canSend: Boolean = false,       // true only when idle
    val isResuming: Boolean = false,
    val scrollToBottom: Boolean = true   // auto-scroll state
)

sealed class MessageItem {
    data class UserMessage(val text: String, val timestamp: Instant) : MessageItem()
    data class AgentMessage(
        val content: String,         // accumulated markdown
        val isStreaming: Boolean,
        val timestamp: Instant
    ) : MessageItem()
    data class ToolCall(
        val callId: String,
        val toolName: String,
        val title: String,
        val args: String?,
        val result: String?,
        val isRunning: Boolean
    ) : MessageItem()
    data class StatusChange(val status: SessionStatus, val message: String?) : MessageItem()
}
```

### Event → MessageItem 映射

| Event Type | → MessageItem |
|------------|---------------|
| `user_message` | `UserMessage` |
| `assistant_delta` | 追加到当前 `AgentMessage.content` |
| `assistant_message` | 替换当前 `AgentMessage`（完整版） |
| `tool_call_started` | 新增 `ToolCall(isRunning=true)` |
| `tool_call_completed` | 更新 `ToolCall(isRunning=false, result=...)` |
| `session_status_changed` | 新增 `StatusChange` |
| `session_error` | 新增 `StatusChange(failed, message)` |

### 增量追加逻辑

```
收到 assistant_delta:
  if 最后一条 message 是 AgentMessage && isStreaming:
    content += delta.text
  else:
    新增 AgentMessage(content=delta.text, isStreaming=true)

收到 assistant_message:
  替换最后一条 streaming AgentMessage → isStreaming=false
```

## Header 溢出菜单 (⋮)

| Action | Condition | Behavior |
|--------|-----------|----------|
| 取消会话 | status == running | 确认 Dialog → POST /cancel |
| 恢复会话 | status == completed/failed/cancelled | 手动重试 POST /resume |
| 删除会话 | any | 确认 Dialog → DELETE → 返回列表 |
| 复制全部输出 | any | 复制所有 agent messages 到剪贴板 |

## 验收标准

- [ ] 流式追加无闪烁，60fps。
- [ ] 代码块语法高亮正确，公式和表格渲染正确。
- [ ] 自动滚动 / 手动查看切换顺畅。
- [ ] 断线后 banner 出现，重连后消失，events 补拉完整。
- [ ] 顶栏可同时清晰展示 provider、目录、usage 与状态，不会因右上状态区而异常截断，也不会重复展示同一目录信息。
- [ ] 软键盘弹起后，顶栏仍保持可见，消息区只压缩中部可滚动区域，输入条保持贴底。
- [ ] Session idle 时 InputBar 可用，running 时 disabled，completed/failed/cancelled 进入页面时会自动尝试恢复。
- [ ] ToolCallCard 可折叠/展开。
- [ ] 长消息（10K chars）不卡。
- [ ] 回到底部 FAB 显示未读消息数。
