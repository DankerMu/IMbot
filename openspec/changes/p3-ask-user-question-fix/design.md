# Design: AskUserQuestion 显示修复

## Root Cause Analysis

### 数据流断裂点

```
Claude Code stdout (stream-json):
  { "type": "tool_use", "tool": "AskUserQuestion", "input": { "questions": [...] } }
      ↓
event-mapper.ts (line 64-72): 正确透传 input 为 { tool: "AskUserQuestion", input: { questions: [...] } }
      ↓
Relay: 正确存储并广播 payload
      ↓
Android DetailViewModel: 检测到 isInteractiveToolCall("AskUserQuestion") → true
      ↓ ★ 断裂点
DetailUtils.parseAskUserQuestion(inputJson):
  - json.optString("question") → "" (不存在顶层 question)
  - json.optJSONArray("options") → null (不存在顶层 options)
  - 结果: question = inputJson 原文, options = null
      ↓
InteractiveToolCard: 显示原始 JSON 文本作为问题，没有选项按钮
```

### AskUserQuestion 实际输入结构

Claude Code 的 AskUserQuestion 工具 input 有两种格式：

**格式 A（标准）**：
```json
{
  "questions": [
    {
      "question": "Which library should we use?",
      "header": "Library",
      "options": [
        { "label": "Option A", "description": "Fast but limited" },
        { "label": "Option B", "description": "Full-featured" }
      ],
      "multiSelect": false
    }
  ]
}
```

**格式 B（旧格式/简化）**：
```json
{
  "question": "你希望怎么处理？",
  "options": ["选项1", "选项2", "选项3"]
}
```

## Solution

### 1. 重写 `parseAskUserQuestion()` — `DetailUtils.kt`

```kotlin
/**
 * 解析 AskUserQuestion 工具调用的 input JSON。
 *
 * 支持两种格式：
 * - 标准格式：{ "questions": [{ "question": "...", "options": [{ "label": "...", "description": "..." }], "multiSelect": bool }] }
 * - 简化格式：{ "question": "...", "options": ["str1", "str2"] }
 *
 * 返回解析后的问题列表。如果只有一个问题，UI 直接渲染；多个问题顺序渲染。
 */
data class ParsedQuestion(
    val question: String,
    val header: String?,
    val options: List<ParsedOption>?,
    val multiSelect: Boolean,
)

data class ParsedOption(
    val label: String,
    val description: String?,
)

internal fun parseAskUserQuestionV2(inputJson: String?): List<ParsedQuestion> {
    if (inputJson.isNullOrBlank()) {
        return listOf(ParsedQuestion(
            question = DEFAULT_ASK_USER_QUESTION_MESSAGE,
            header = null,
            options = null,
            multiSelect = false,
        ))
    }

    return try {
        val json = JSONObject(inputJson)

        // 标准格式：questions 数组
        val questionsArray = json.optJSONArray("questions")
        if (questionsArray != null && questionsArray.length() > 0) {
            return (0 until questionsArray.length()).map { i ->
                val q = questionsArray.getJSONObject(i)
                val questionText = q.optString("question", DEFAULT_ASK_USER_QUESTION_MESSAGE)
                val header = q.optString("header", "").takeIf { it.isNotBlank() }
                val multiSelect = q.optBoolean("multiSelect", false)
                val options = q.optJSONArray("options")?.let { optArr ->
                    (0 until optArr.length().coerceAtMost(MAX_ASK_USER_QUESTION_OPTIONS)).mapNotNull { j ->
                        val optItem = optArr.opt(j)
                        when (optItem) {
                            is JSONObject -> {
                                val label = optItem.optString("label", "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                                val description = optItem.optString("description", "").takeIf { it.isNotBlank() }
                                ParsedOption(label = label, description = description)
                            }
                            is String -> ParsedOption(label = optItem, description = null)
                            else -> optItem?.toString()?.takeIf { it.isNotBlank() }?.let { ParsedOption(label = it, description = null) }
                        }
                    }
                }?.takeIf { it.isNotEmpty() }

                ParsedQuestion(
                    question = questionText,
                    header = header,
                    options = options,
                    multiSelect = multiSelect,
                )
            }
        }

        // 简化格式：顶层 question + options
        val question = json.optString("question", "").takeIf { it.isNotBlank() }
        val options = json.optJSONArray("options")?.let { array ->
            (0 until array.length().coerceAtMost(MAX_ASK_USER_QUESTION_OPTIONS)).mapNotNull { index ->
                val item = array.opt(index)
                when (item) {
                    is JSONObject -> {
                        val label = item.optString("label", "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        ParsedOption(label = label, description = item.optString("description", "").takeIf { it.isNotBlank() })
                    }
                    is String -> ParsedOption(label = item, description = null)
                    else -> null
                }
            }
        }?.takeIf { it.isNotEmpty() }

        listOf(ParsedQuestion(
            question = question ?: DEFAULT_ASK_USER_QUESTION_MESSAGE,
            header = null,
            options = options,
            multiSelect = false,
        ))
    } catch (_: Exception) {
        listOf(ParsedQuestion(
            question = inputJson,
            header = null,
            options = null,
            multiSelect = false,
        ))
    }
}
```

### 2. 更新 `MessageItem.InteractiveToolCall` 数据结构

```kotlin
// 当前：
data class InteractiveToolCall(
    val id: String,
    val question: String,           // 单个问题
    val options: List<String>?,     // 纯字符串选项
    val answer: String?,
    val isAnswered: Boolean,
    val errorMessage: String?,
)

// 改为：
data class InteractiveToolCall(
    val id: String,
    val questions: List<ParsedQuestion>,  // 支持多问题
    val answer: String?,
    val isAnswered: Boolean,
    val errorMessage: String?,
) {
    // 便利属性：单问题情况
    val primaryQuestion: ParsedQuestion get() = questions.firstOrNull()
        ?: ParsedQuestion(DEFAULT_ASK_USER_QUESTION_MESSAGE, null, null, false)
}
```

### 3. 更新 `InteractiveToolCard.kt` 渲染

**选项按钮升级**：每个选项显示 `label`，长按或副文本显示 `description`。

```kotlin
// 单选模式（multiSelect = false）：
OutlinedButton(onClick = { answerDraft = option.label }) {
    Text(option.label)
}

// 多选模式（multiSelect = true）：
// 使用 FilterChip 带勾选状态
FilterChip(
    selected = selectedOptions.contains(option.label),
    onClick = { toggleOption(option.label) },
    label = { Text(option.label) },
)
```

**多问题渲染**：`questions.forEach` 循环渲染，每个问题独立区块。

### 4. 提交逻辑调整

**单问题**：提交 `answerDraft` 文本（与现有逻辑一致）。

**多问题**：拼接所有问题的答案为 JSON 格式：
```json
{
  "answers": {
    "Which library?": "Option A",
    "Include tests?": "Yes"
  }
}
```

但目前 Claude Code 的 AskUserQuestion 实际上几乎都是单问题（`questions` 数组长度 = 1），所以初版只需要正确渲染第一个问题即可。

## File Changes

| File | Change |
|------|--------|
| `ui/detail/DetailUtils.kt` | 新增 `ParsedQuestion`, `ParsedOption`, `parseAskUserQuestionV2()` |
| `ui/detail/DetailViewModel.kt` | 更新 `InteractiveToolCall` 构造，使用 `parseAskUserQuestionV2` |
| `ui/detail/InteractiveToolCard.kt` | 使用 `ParsedQuestion`/`ParsedOption` 渲染选项（含 label + description） |
| `ui/detail/SessionDetailScreen.kt` | 无改动（已通过 InteractiveToolCard 间接更新） |

## Constraints

- 不修改 relay/companion 协议
- 向后兼容旧格式（扁平 question + string[] options）
- 多问题暂只渲染第一个（未来可扩展）
