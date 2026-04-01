# Tasks: Mobile Skill & Tool Interaction

## 1. Slash Command Sheet

### 1.1 SkillItem 数据模型
- [ ] 在 `ui/detail/` 下定义 `SkillItem` data class（command, label, description, category, icon）
- [ ] 定义 `SkillCategory` enum（BUILT_IN, AGENT_SKILL, SLASH_COMMAND）
- [ ] 创建 hardcoded skill 列表（commit, review, test, help, compact, clear, init 等常用命令）

### 1.2 SlashCommandSheet composable
- [ ] 新建 `ui/detail/SlashCommandSheet.kt`
- [ ] 实现 `ModalBottomSheet`，最大高度 50% 屏幕
- [ ] 顶部搜索框（`OutlinedTextField`，实时过滤 skill 列表）
- [ ] 分组列表（LazyColumn，按 category 分组，带 header）
- [ ] 每行布局：icon + command name（Medium）+ description（LabelSecondary, ellipsis）
- [ ] 点击行 → 回调 `onSkillSelected(SkillItem)` → 关闭 sheet

### 1.3 InputBar `/` 检测
- [ ] 修改 `InputBar.kt`：监听文本变化，当输入 `/` 作为首字符时 emit `onSlashTrigger()`
- [ ] 添加 `commandChip: SkillItem?` 参数，非 null 时在输入栏上方显示 CommandChip
- [ ] 命令模式下 placeholder 文字切换为命令描述

### 1.4 CommandChip composable
- [ ] 新建 `ui/detail/CommandChip.kt`
- [ ] AssistChip 样式：`/ <command>` + description + ✕ 关闭按钮
- [ ] ✕ 按钮回调 `onDismissCommand()`

### 1.5 SessionDetailScreen 集成
- [ ] 在 DetailViewModel 中新增 `commandChip` 和 `showSlashSheet` 状态
- [ ] SessionDetailScreen 集成 SlashCommandSheet + CommandChip
- [ ] 发送消息时：有 commandChip → 组装 `/<command> <args>`；无 → 原样发送

## 2. Interactive Tool Cards

### 2.1 Tool call 类型识别
- [ ] 在 `DetailUtils.kt` 新增 `isInteractiveToolCall(toolName)` 函数
- [ ] 在 `DetailViewModel` 的消息解析逻辑中，将交互式 tool_call 映射为 `MessageItem.InteractiveToolCall`

### 2.2 InteractiveToolCard composable
- [ ] 新建 `ui/detail/InteractiveToolCard.kt`
- [ ] AskUserQuestion 卡片布局：问题标题 + 问题文本 + 可选选项按钮 + 自由输入框 + 提交按钮
- [ ] 解析 tool_call input JSON：提取 `question` 字段和可选 `options` 字段
- [ ] 提交按钮回调 `onSubmitAnswer(sessionId, answer)`
- [ ] 已回答状态：卡片变为只读（灰色背景，显示已提交的答案）

### 2.3 Approval 卡片增强
- [ ] 修改 StatusChangeBubble 或新增 ApprovalCard
- [ ] approval_required：显示 tool name + description + approve/deny 按钮
- [ ] approval_resolved：显示结果（approved/denied），只读状态
- [ ] 按钮回调通过 `POST /sessions/:id/input` 发送

### 2.4 DetailViewModel 集成
- [ ] 新增 `submitToolAnswer(sessionId, answer)` 方法
- [ ] 新增 `approveToolCall(sessionId)` / `denyToolCall(sessionId)` 方法
- [ ] 处理提交后状态更新（tool_call_completed 事件到达 → 更新卡片状态）

## 3. Tests

> **测试环境**：使用 book CLI 作为 provider 进行端到端验证（book 与 Claude Code 同源二进制，支持所有 tool/skill 事件）。

### 3.1 Slash Command — Unit Tests

**过滤逻辑**：
- [ ] 输入 "com" → 匹配 "commit" / "compact"，不匹配 "review"
- [ ] 输入 "COM"（大写）→ 大小写不敏感匹配 "commit" / "compact"
- [ ] 输入 "" 空串 → 返回全部 skill 列表
- [ ] 输入 "zzz" 无匹配项 → 返回空列表
- [ ] 输入含特殊字符 "co+" → 不崩溃，按字面匹配

**`/` 前缀检测**：
- [ ] 输入 `/` 作为首字符 → 触发 onSlashTrigger
- [ ] 输入 `hello /` 中间出现 `/` → 不触发
- [ ] 输入空字符串 → 不触发
- [ ] 输入 `//` 双斜杠 → 触发一次（不重复）
- [ ] 已有 CommandChip 时输入 `/` → 不触发（避免重入）

**CommandChip 组装**：
- [ ] chip="commit" + args="fix typo" → "/commit fix typo"
- [ ] chip="help" + args="" 空参数 → "/help"（不带尾随空格）
- [ ] chip="test" + args="  spaced  " → "/test   spaced  "（保留原始空格）

**SkillItem 数据模型**：
- [ ] hardcoded 列表至少包含 commit/review/test/help/compact/clear
- [ ] 每个 SkillItem 的 command 非空、label 非空
- [ ] SkillCategory 枚举序列化/反序列化正确

### 3.2 Interactive Tool Cards — Unit Tests

**isInteractiveToolCall**：
- [ ] "AskUserQuestion" → true
- [ ] "askuserquestion"（全小写）→ true
- [ ] "Read" → false
- [ ] "Bash" → false
- [ ] "" 空串 → false
- [ ] null → false（防空安全）

**AskUserQuestion JSON 解析**：
- [ ] 完整 JSON `{"question":"选哪个?","options":["A","B"]}` → question="选哪个?", options=["A","B"]
- [ ] 仅有 question `{"question":"你确定吗?"}` → question="你确定吗?", options=null
- [ ] 缺少 question 字段 `{"text":"hello"}` → 回退到整个 input 作为问题文本
- [ ] 空 JSON `{}` → 显示 "Agent is asking for input"（兜底文案）
- [ ] 非法 JSON `{broken` → 不崩溃，显示兜底文案
- [ ] options 为空数组 `{"question":"Q","options":[]}` → 仅显示自由输入，不显示选项按钮

**ViewModel 状态**：
- [ ] `submitToolAnswer` 调用后：对应 InteractiveToolCard 立即进入 "submitting" 状态（按钮禁用）
- [ ] `submitToolAnswer` 成功 → tool_call_completed 事件到达 → 卡片变只读
- [ ] `submitToolAnswer` 网络失败 → 卡片显示错误提示 "发送失败，点击重试"，按钮恢复可用
- [ ] `approveToolCall` 调用后 → 卡片显示 "已批准"
- [ ] `denyToolCall` 调用后 → 卡片显示 "已拒绝"

### 3.3 状态转换 & 竞态 Tests

- [ ] CommandChip 显示中 → 用户点 ✕ 关闭 → 输入框文本保留（不清空已输入的参数文本）
- [ ] CommandChip 显示中 → 切换到另一个 session → CommandChip 自动清除
- [ ] AskUserQuestion 卡片待回答 → session 被 cancel → 卡片变灰禁用，提交按钮不可用
- [ ] AskUserQuestion 卡片待回答 → session 变为 completed → 同上
- [ ] 多个连续 AskUserQuestion tool_call → 每个独立渲染为单独卡片
- [ ] 用户正在打字回答 → 新的 tool_call_completed 到达（来自另一个 tool）→ 当前编辑不受影响
- [ ] SlashCommandSheet 打开中 → 收到 session_status_changed → sheet 不自动关闭

### 3.4 Integration Tests (book CLI)

- [ ] **Slash command 端到端**：启动 book session → 输入 `/` → sheet 弹出 → 选择 "help" → chip 显示 → 发送 → 消息内容为 "/help" → book agent 收到并处理
- [ ] **AskUserQuestion 端到端**：通过 book session 触发 AskUserQuestion tool_call → 手机端渲染交互卡片 → 输入回答 → 提交 → book agent 收到答案 → tool_call_completed → 卡片变只读
- [ ] **Approval 端到端**：book session 配置 non-bypass 模式 → 触发 approval_required → 手机端显示审批卡片 → 点击 "批准" → book agent 继续执行
- [ ] **混合流程**：一个 session 内先触发 slash command，再触发 AskUserQuestion，两者独立工作不冲突
