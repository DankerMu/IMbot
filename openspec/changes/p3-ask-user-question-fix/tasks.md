# Tasks: AskUserQuestion 显示修复

## 1. 数据模型

### 1.1 新增 ParsedQuestion / ParsedOption 数据类
- [ ] 在 `DetailUtils.kt` 新增 `ParsedQuestion(question, header, options, multiSelect)` 和 `ParsedOption(label, description)`
- [ ] 保持 `DEFAULT_ASK_USER_QUESTION_MESSAGE` 和 `MAX_ASK_USER_QUESTION_OPTIONS` 常量不变

### 1.2 新增 parseAskUserQuestionV2()
- [ ] 实现标准格式解析：`{ "questions": [{ "question", "header", "options": [{ "label", "description" }], "multiSelect" }] }`
- [ ] 实现简化格式解析：`{ "question", "options": ["str1", "str2"] }`
- [ ] options 数组元素支持 JSONObject（提取 label/description）和 String 两种类型
- [ ] 异常情况 fallback：无法解析时返回原始 inputJson 作为 question 文本
- [ ] 保留 `MAX_ASK_USER_QUESTION_OPTIONS` 截断逻辑

### 1.3 更新 MessageItem.InteractiveToolCall
- [ ] `question: String` + `options: List<String>?` → `questions: List<ParsedQuestion>`
- [ ] 添加 `primaryQuestion` 便利属性
- [ ] 更新 DetailViewModel 中构造 InteractiveToolCall 的代码，调用 `parseAskUserQuestionV2`

## 2. UI 渲染

### 2.1 InteractiveToolCard 更新
- [ ] 使用 `item.primaryQuestion.question` 渲染问题文本
- [ ] 使用 `item.primaryQuestion.options` 渲染选项按钮
- [ ] 选项按钮显示 `option.label`，如果 `option.description` 非空则在 label 下方显示小字描述
- [ ] 单选模式（multiSelect=false）：OutlinedButton，点击设置 answerDraft = option.label
- [ ] 多选模式（multiSelect=true）：FilterChip 带勾选状态，答案用 ", " 拼接
- [ ] header 非空时在问题文本上方显示为 `labelMedium` 标签

### 2.2 提交逻辑
- [ ] 单选：提交 answerDraft（与现有一致）
- [ ] 多选：提交选中选项的 label 用 ", " 拼接的字符串

## 3. 测试

### 3.1 Unit Tests — parseAskUserQuestionV2

**标准格式**：
- [ ] 输入 `{ "questions": [{ "question": "Q?", "options": [{ "label": "A", "description": "desc" }], "multiSelect": false }] }` → 返回 1 个 ParsedQuestion，question="Q?", options=[ParsedOption("A", "desc")], multiSelect=false
- [ ] 输入含 2 个 questions → 返回 2 个 ParsedQuestion
- [ ] 输入 `multiSelect: true` → 对应 ParsedQuestion.multiSelect == true
- [ ] 输入 options 中混合 JSONObject 和 String → 全部正确解析为 ParsedOption

**简化格式**：
- [ ] 输入 `{ "question": "Q?", "options": ["A", "B"] }` → 返回 1 个 ParsedQuestion，options 为 [ParsedOption("A", null), ParsedOption("B", null)]
- [ ] 输入仅 `{ "question": "Q?" }` → options 为 null

**边界情况**：
- [ ] 输入 null → 返回 DEFAULT_ASK_USER_QUESTION_MESSAGE
- [ ] 输入 "" → 返回 DEFAULT_ASK_USER_QUESTION_MESSAGE
- [ ] 输入无效 JSON → 返回原始字符串作为 question
- [ ] 输入 `{}` → 返回 DEFAULT_ASK_USER_QUESTION_MESSAGE
- [ ] 输入 options 超过 10 个 → 截断到 10 个

### 3.2 功能回归
- [ ] 旧格式 `{ "question": "Q?", "options": ["A"] }` 仍能正确渲染
- [ ] 已回答状态的卡片正确显示 "已提交回答" + 回答内容
- [ ] 过期卡片正确显示 "已过期" 状态
- [ ] 提交回答后 session 继续正常运行
