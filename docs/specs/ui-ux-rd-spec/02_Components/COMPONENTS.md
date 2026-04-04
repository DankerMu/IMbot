# 可复用组件规格

## C-01: SessionCard

**用途**：会话列表中的单个会话卡片。

### 布局

```
┌─────────────────────────────────────────────┐
│  [Provider Icon]  Session Title        12:30 │
│                   /path/to/project           │
│                                              │
│  "帮我看一下这个项目的架构..."     [Status●]  │
└─────────────────────────────────────────────┘
```

### Props

| Prop | Type | Description |
|------|------|-------------|
| `session` | `SessionSummary` | 会话摘要数据 |
| `onClick` | `() -> Unit` | 点击跳转详情 |
| `onLongPress` | `() -> Unit` | 长按进入选择模式 |
| `selected` | `Boolean` | 当前是否被选中 |
| `selectionMode` | `Boolean` | 当前列表是否处于多选模式 |
| `onSwipeDismiss` | `() -> Unit` | 左滑删除 |

### 数据契约

```kotlin
data class SessionSummary(
    val id: String,
    val provider: Provider,      // CLAUDE, BOOK, OPENCLAW
    val workspaceCwd: String,
    val initialPrompt: String?,
    val status: SessionStatus,
    val createdAt: Instant,
    val lastActiveAt: Instant
)
```

### 视觉状态

| State | Visual |
|-------|--------|
| `running` | 状态圆点绿色脉冲 |
| `completed` | 状态圆点静态绿色 |
| `failed` | 状态圆点红色 |
| `queued` | 状态圆点灰色 |
| `cancelled` | 状态圆点灰色 |

### 交互

- Tap → `onClick`
- Long press → 进入选择模式并选中当前卡片
- Selection mode 中 Tap → 切换勾选
- Swipe left → reveal delete action → `onSwipeDismiss`

### 选择模式视觉

- 选中卡片：显示强调色描边/底色
- 选中卡片：显示勾选指示器
- 选择模式中禁用 swipe-to-delete，避免与多选手势冲突

---

## C-02: MessageBubble

**用途**：会话详情中的单条消息。

### 布局

```
Agent 消息（左对齐）:
┌──┐ ┌──────────────────────────────┐
│🤖│ │  Markdown rendered content    │
└──┘ │  with **bold**, `code`,       │
     │  and syntax highlighted       │
     │  code blocks                  │
     └──────────────────────────────┘
                                12:30

User 消息（右对齐）:
     ┌──────────────────────────────┐
     │  User input text             │
     └──────────────────────────────┘
     12:31
```

### Props

| Prop | Type | Description |
|------|------|-------------|
| `role` | `"user" \| "agent"` | 消息角色 |
| `content` | `String` | 消息文本（Markdown） |
| `provider` | `Provider` | Agent 头像用 |
| `timestamp` | `Instant` | 时间戳 |
| `isStreaming` | `Boolean` | 是否正在流式追加 |

### 渲染规则

- `role == "user"`: 右对齐，Primary container color 背景。
- `role == "agent"`: 左对齐，Surface color 背景，左侧 provider icon。
- `isStreaming == true`: 末尾显示光标闪烁动画 `▊`。
- Markdown: GFM 完整支持（heading、list、table、code block、link）。
- Code block: 语法高亮 + 圆角背景 + copy 按钮。
- 长消息（> 5000 chars）: 启用 lazy rendering（分段加载）。

---

## C-03: ToolCallCard

**用途**：显示 agent 的工具调用。

### 布局

```
┌──────────────────────────────────────┐
│  🔧 grep  "Searching for TODO"   ▼  │
├──────────────────────────────────────┤  ← 折叠/展开
│  args: { pattern: "TODO" }           │
│  result: (3 matches found)           │
└──────────────────────────────────────┘
```

### Props

| Prop | Type | Description |
|------|------|-------------|
| `toolName` | `String` | 工具名 |
| `title` | `String` | 短描述 |
| `args` | `String?` | JSON 参数（折叠区） |
| `result` | `String?` | 执行结果（折叠区） |
| `isRunning` | `Boolean` | 是否正在执行 |

### 视觉状态

| State | Visual |
|-------|--------|
| Running | 工具名旁 CircularProgressIndicator (small) |
| Completed | 静态工具图标 |
| Error | 红色错误图标 |

### 交互

- 默认折叠（只显示 title 行）。
- Tap → 展开显示 args + result。
- `isRunning == true` 时自动展开。

---

## C-04: MarkdownRenderer

**用途**：渲染 Markdown 文本，支持语法高亮。

### 技术选型

当前实现：**Compose 原生块级渲染 + 本地离线 KaTeX WebView**。普通 Markdown 保持 Compose 渲染，公式片段使用 APK 内置 KaTeX 资源离线渲染。

### 必须支持

| Feature | Priority |
|---------|----------|
| Headings (h1-h6) | P0 |
| Bold / Italic / Strikethrough | P0 |
| Inline code | P0 |
| Code blocks + language hint | P0 |
| Syntax highlighting | P0 |
| Links (clickable) | P0 |
| Unordered / Ordered lists | P0 |
| Tables | P1 |
| Blockquotes | P1 |
| Inline / Display math | P1 |
| Images (URL) | P2 |

### 性能约束

- 增量追加：`assistant_delta` 事件逐块追加时不重渲染整条消息。
- 10K+ 字符消息：不卡（< 16ms per frame）。

---

## C-05: DirectoryBrowser

**用途**：目录浏览器，用于选择工作目录。

### 布局

```
┌──────────────────────────────────┐
│ ← AI-vault / IMbot / packages    │  ← 面包屑
├──────────────────────────────────┤
│  📁 android                      │
│  📁 companion                    │
│  📁 relay                        │
│  📁 wire                         │
├──────────────────────────────────┤
│       [ 选择此目录 ]              │  ← 底部按钮
└──────────────────────────────────┘
```

### Props

| Prop | Type | Description |
|------|------|-------------|
| `hostId` | `String` | 目标 host |
| `initialPath` | `String` | 起始路径（root path） |
| `onSelect` | `(String) -> Unit` | 选择目录回调 |

### 交互

- Tap 目录 → 进入下一层，面包屑追加。
- Tap 面包屑某层 → 回到该层。
- "选择此目录" → 返回当前 path。
- Loading 状态：目录加载中显示 shimmer。
- 空目录：显示"此目录下无子目录"。
- 错误：显示 inline error + 重试按钮。

---

## C-06: ProviderChip

**用途**：Provider 选择器和标签。

### 布局

```
[ 🟠 Claude Code ]  [ 🟣 book ]  [ 🔴 OpenClaw ]
```

### Props

| Prop | Type | Description |
|------|------|-------------|
| `provider` | `Provider` | provider 类型 |
| `selected` | `Boolean` | 是否选中 |
| `onClick` | `() -> Unit` | 点击回调 |
| `showLabel` | `Boolean` | 是否显示文字（卡片中可能只显示 icon） |

### 视觉

- 选中：filled chip，provider color 背景。
- 未选中：outlined chip，provider color 边框。
- Compact 模式（showLabel=false）：只显示 icon circle。

---

## C-07: StatusIndicator

**用途**：会话状态指示器。

### Variants

| Variant | Visual | Usage |
|---------|--------|-------|
| `dot` | 8dp 圆点 + 状态色 | SessionCard 中 |
| `badge` | 圆角矩形 + 文字 + 状态色 | Detail 顶栏 |
| `bar` | 顶部 2dp 色条 | Detail 页面顶部 |

### Running 动画

```kotlin
// Pulse animation for running status
val alpha by rememberInfiniteTransition().animateFloat(
    initialValue = 0.3f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
        animation = tween(750, easing = LinearEasing),
        repeatMode = RepeatMode.Reverse
    )
)
```

---

## C-08: InputBar

**用途**：会话详情底部输入栏。

### 布局

```
┌──────────────────────────────────────────┐
│  ┌─────────────────────────┐  ┌────┐    │
│  │ 输入消息...              │  │ ➤  │    │
│  │                         │  └────┘    │
│  └─────────────────────────┘            │
└──────────────────────────────────────────┘
```

### Props

| Prop | Type | Description |
|------|------|-------------|
| `onSend` | `(String) -> Unit` | 发送回调 |
| `enabled` | `Boolean` | session running 时可发 |
| `placeholder` | `String` | 占位文本 |

### 交互

- 支持多行输入（自动增高，最多 4 行后滚动）。
- 空文本时发送按钮 disabled。
- Session 不在 running 状态时整个 bar disabled + 提示文字。
- 发送后清空输入框。

---

## C-09: EmptyState

**用途**：各页面的空状态。

### Variants

| Context | Illustration | Text | Action |
|---------|-------------|------|--------|
| Session list empty | 聊天气泡图标 | "暂无会话" | "新建会话" 按钮 |
| Directory empty | 空文件夹图标 | "此目录下无子目录" | 无 |
| Events loading | Shimmer | — | — |
| Connection lost | 云断开图标 | "正在重连..." | 无（自动重试） |
| Host offline | 电脑断开图标 | "MacBook 离线" | "查看历史" 按钮 |

---

## C-10: ConnectionBanner

**用途**：全局连接状态横幅。

### 布局

```
┌──────────────────────────────────────┐
│  ⚠ 连接中断，正在重连...              │  ← 黄色背景
└──────────────────────────────────────┘
```

### 规则

- WSS 连接正常时：不显示。
- WSS 断开重连中：显示黄色横幅 + 动画 spinner。
- WSS 断开超过 30s：显示红色横幅"连接失败"。
- 恢复后：绿色"已恢复" 2s → 消失。
- 位于 TopAppBar 下方，覆盖在内容上方。
