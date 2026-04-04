# Page: NewSessionScreen

## 概述

| Key | Value |
|-----|-------|
| Route | `/new-session` |
| ViewModel | `NewSessionViewModel` |
| PRD ref | FR-01, FR-02, FR-03 |

三步式新建会话流程：选 Provider → 选目录 → 输入 Prompt。

## 布局

使用 `HorizontalPager` 实现步骤切换，顶部 step indicator。

```
┌──────────────────────────────────────┐
│  ← 新建会话                          │  ← TopAppBar
├──────────────────────────────────────┤
│  ● ─ ─ ○ ─ ─ ○                      │  ← Step indicator (1/3)
│  Provider  目录  消息（可选）        │
├──────────────────────────────────────┤
│                                      │
│  Step 1: Provider Picker             │
│                                      │
│  ┌────────────────────┐              │
│  │  🟠 Claude Code     │ ← selected  │
│  └────────────────────┘              │
│  ┌────────────────────┐              │
│  │  🟣 book            │              │
│  └────────────────────┘              │
│  ┌────────────────────┐              │
│  │  🔴 OpenClaw        │              │
│  └────────────────────┘              │
│                                      │
│  Host status: MacBook online ✓       │  ← 底部提示
│               OpenClaw online ✓      │
│                                      │
├──────────────────────────────────────┤
│              [ 下一步 → ]            │
└──────────────────────────────────────┘
```

### Step 2: Directory Browser

```
┌──────────────────────────────────────┐
│  ← 新建会话                          │
├──────────────────────────────────────┤
│  ● ─ ─ ● ─ ─ ○                      │  ← Step 2/3
├──────────────────────────────────────┤
│                                      │
│  DirectoryBrowser component          │
│  (see C-05)                          │
│                                      │
│  当前选择: /Users/danker/.../IMbot   │
│                                      │
├──────────────────────────────────────┤
│    [ ← 上一步 ]    [ 下一步 → ]      │
└──────────────────────────────────────┘
```

### Step 3: Prompt Input (Optional)

```
┌──────────────────────────────────────┐
│  ← 新建会话                          │
├──────────────────────────────────────┤
│  ● ─ ─ ● ─ ─ ●                      │  ← Step 3/3
├──────────────────────────────────────┤
│                                      │
│  Provider: 🟠 Claude Code            │  ← 摘要
│  目录: /Users/.../IMbot              │
│                                      │
│  ┌─────────────────────────────┐     │
│  │ 输入你的首条消息（可选）...   │     │  ← 多行输入
│  │                             │     │
│  │                             │     │
│  └─────────────────────────────┘     │
│                                      │
│  Model:  [ sonnet ▼ ]               │  ← 可选 dropdown
│                                      │
├──────────────────────────────────────┤
│    [ ← 上一步 ]    [ 🚀 开始 ]       │
└──────────────────────────────────────┘
```

## 状态机

```
Step1 (Provider)
    │ select provider
    ▼
Step2 (Directory)
    │ select directory
    ├─ tap "开始" → 直接创建空 session
    ▼
Step3 (Prompt Optional)
    │ tap "开始"
    ▼
Creating ── loading overlay
    │
    ├─ success → navigate to SessionDetail (新 session)
    └─ error → Snackbar error + stay on Step3
```

## 数据契约

```kotlin
data class NewSessionUiState(
    val step: Int = 1,                    // 1, 2, 3
    val provider: Provider? = null,
    val hostId: String? = null,           // auto from provider
    val cwd: String? = null,
    val prompt: String = "",
    val model: String = "sonnet",         // default model
    val isCreating: Boolean = false,
    val error: String? = null,
    val hosts: List<HostInfo> = emptyList()
)
```

## 规则

- Step 1: provider 选择后自动确定 host（Claude/book → MacBook, OpenClaw → relay-local）。
- Step 1: 如果 host offline，对应 provider 显示为 disabled + "离线" 标签。
- Step 2: book provider 只能在 novel root 下浏览（过滤其他 root）。
- Step 2: 目录选定后即可直接点击 "开始" 创建空 session。
- Step 3: prompt / 首条消息为非必填。
- Step 3: model dropdown 选项来自 provider 支持的模型列表。
- "开始" 按钮点击后显示 loading overlay，阻止重复提交。

## 验收标准

- [ ] 三步流程顺畅，可前后切换。
- [ ] 离线 host 的 provider disabled。
- [ ] book 只能选 novel 目录。
- [ ] 创建成功后直接跳转 session detail。
- [ ] 创建失败有明确错误提示。
- [ ] Step indicator 正确反映当前步骤。
