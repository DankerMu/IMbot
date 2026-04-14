# Tasks: p3-detail-display-fixes

## Task 1: ToolCategory — 新增 SKILL 分类

**文件**: `packages/android/app/src/main/kotlin/com/imbot/android/ui/detail/ToolCategory.kt`

### 1.1 新增 `skillToolNames` 集合

在现有工具名集合（line 16-19）后新增：

```kotlin
private val skillToolNames = setOf("skill")
```

### 1.2 新增 `ToolCategory.SKILL` 枚举项

在 `OTHER` 之前（约 line 46）新增：

```kotlin
SKILL(
    icon = Icons.Outlined.Extension,  // 或 AutoAwesome
    accentColor = { MaterialTheme.colorScheme.tertiary },
    label = "技能",
),
```

需要新增 import：`import androidx.compose.material.icons.outlined.Extension`

### 1.3 扩展 `classifyTool()` 函数

在 `searchToolNames` 分支后、`else` 分支前新增：

```kotlin
name in skillToolNames -> ToolCategory.SKILL
```

---

## Task 2: ToolContentRenderers — 新增 SkillToolContent

**文件**: `packages/android/app/src/main/kotlin/com/imbot/android/ui/detail/ToolContentRenderers.kt`

### 2.1 新增 `SkillToolContent` composable

在 `GenericToolContent` 之后新增：

```kotlin
@Composable
internal fun SkillToolContent(item: MessageItem.ToolCall) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 只显示 skill 名称，不显示 args（args 是完整的技能定义 prompt）
        val skillName = extractSkillName(item.args)
        if (skillName != null) {
            Text(
                text = "/$skillName",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 结果简短展示（如有）
        item.result?.takeIf(String::isNotBlank)?.let { result ->
            val truncated = if (result.length > 200) result.take(200) + "…" else result
            ToolCallSection(
                title = "结果",
                content = truncated,
            )
        }
    }
}
```

### 2.2 新增 `extractSkillName` 辅助函数

```kotlin
private fun extractSkillName(args: String?): String? {
    if (args.isNullOrBlank()) return null
    return try {
        val json = org.json.JSONObject(args)
        json.optString("skill", "").takeIf(String::isNotBlank)
    } catch (_: Exception) {
        null
    }
}
```

---

## Task 3: ToolCallCard — 路由 SKILL + 自动折叠

**文件**: `packages/android/app/src/main/kotlin/com/imbot/android/ui/detail/ToolCallCard.kt`

### 3.1 在 `ToolCallContent` 中路由 SKILL 分类

在 `ToolCallContent` 函数（约 line 249-257）的 `when` 分支中，`ToolCategory.OTHER` 之前新增：

```kotlin
ToolCategory.SKILL -> SkillToolContent(item)
```

### 3.2 工具完成后自动折叠

修改 `LaunchedEffect(item.isRunning)` 块（line 82-86），增加 `else` 分支：

```kotlin
LaunchedEffect(item.isRunning) {
    if (item.isRunning) {
        expanded = true
    } else {
        expanded = false
    }
}
```

**注意**：这会在工具完成时触发折叠动画。首次渲染已完成的工具时，`rememberSaveable` 初始值已经是 `false`，`LaunchedEffect` 不会重复触发（key 未变化）。用户手动展开后，点击折叠不受影响——`LaunchedEffect` 只监听 `item.isRunning` 变化，不监听 `expanded`。

---

## Task 4: ToolCallUtils — Skill 摘要提取

**文件**: `packages/android/app/src/main/kotlin/com/imbot/android/ui/detail/ToolCallUtils.kt`

### 4.1 在 `extractToolSummaryDetail()` 中处理 SKILL

在 `when` 分支（约 line 52-62）中，`ToolCategory.OTHER` 之前新增：

```kotlin
ToolCategory.SKILL -> {
    extractSkillName(item.args)?.let { "/$it" }
}
```

**注意**：`extractSkillName` 在 Task 2 中定义于 `ToolContentRenderers.kt`，需要将其移至 `ToolCallUtils.kt` 或提取为 `internal` 函数在两处复用。建议放在 `ToolCallUtils.kt` 中，`ToolContentRenderers.kt` 调用它。

---

## Task 5: DetailUtils — 修正 totalTokens 计算

**文件**: `packages/android/app/src/main/kotlin/com/imbot/android/ui/detail/DetailUtils.kt`

### 5.1 修正 `SessionUsageState.totalTokens`

将 line 57-58 从：

```kotlin
val totalTokens: Int
    get() = inputTokens + outputTokens
```

改为：

```kotlin
val totalTokens: Int
    get() = inputTokens + outputTokens + cacheCreationTokens + cacheReadTokens
```

---

## Task 6: 测试覆盖

**文件**: `packages/android/app/src/test/kotlin/com/imbot/android/ui/detail/DetailUtilsTest.kt`

### 6.1 修正现有 `totalTokens` 测试

现有测试如果断言 `totalTokens == inputTokens + outputTokens`，需要更新为包含 cache tokens。

### 6.2 新增测试用例

```kotlin
@Test
fun `totalTokens includes cache tokens`() {
    val state = SessionUsageState(
        inputTokens = 500,
        outputTokens = 200,
        cacheCreationTokens = 8000,
        cacheReadTokens = 150000,
        contextWindow = 1000000,
    )
    assertEquals(158700, state.totalTokens)
}

@Test
fun `usagePercent reflects cache tokens`() {
    val state = SessionUsageState(
        inputTokens = 500,
        outputTokens = 200,
        cacheCreationTokens = 8000,
        cacheReadTokens = 150000,
        contextWindow = 1000000,
    )
    assertEquals(0.1587f, state.usagePercent, 0.001f)
}

@Test
fun `totalTokens with zero cache tokens unchanged`() {
    val state = SessionUsageState(
        inputTokens = 1000,
        outputTokens = 500,
    )
    assertEquals(1500, state.totalTokens)
}
```

### 6.3 Skill 分类测试

在 `DetailUtilsTest.kt` 或新建 `ToolCategoryTest.kt` 中：

```kotlin
@Test
fun `classifyTool identifies Skill tool`() {
    assertEquals(ToolCategory.SKILL, classifyTool("Skill"))
    assertEquals(ToolCategory.SKILL, classifyTool("skill"))
    assertEquals(ToolCategory.SKILL, classifyTool("SKILL"))
}

@Test
fun `classifyTool does not classify ToolSearch as SKILL`() {
    assertEquals(ToolCategory.OTHER, classifyTool("ToolSearch"))
}
```

### 6.4 extractSkillName 测试

```kotlin
@Test
fun `extractSkillName parses skill field from JSON args`() {
    assertEquals("commit", extractSkillName("""{"skill":"commit","args":"-m 'fix'"}"""))
}

@Test
fun `extractSkillName returns null for non-JSON args`() {
    assertNull(extractSkillName("not json"))
}

@Test
fun `extractSkillName returns null for missing skill field`() {
    assertNull(extractSkillName("""{"other":"value"}"""))
}

@Test
fun `extractSkillName returns null for blank args`() {
    assertNull(extractSkillName(null))
    assertNull(extractSkillName(""))
}
```

---

## 执行顺序

```
Task 1 (ToolCategory.SKILL) ──┐
                               ├── Task 4 (ToolCallUtils) ──┐
                               ├── Task 2 (SkillToolContent) ├── Task 3 (ToolCallCard routing + auto-collapse)
                               │                             │
Task 5 (totalTokens fix) ──────┴── Task 6 (Tests) ───────────┘
```

- Task 1 最先执行（其他 Task 依赖 `ToolCategory.SKILL` 枚举）
- Task 2 和 Task 4 可并行（但共享 `extractSkillName`，建议 Task 4 先写函数，Task 2 调用）
- Task 3 依赖 Task 1 + Task 2（需要 SKILL 分类 + SkillToolContent composable）
- Task 5 独立执行（与 Skill 修复无依赖）
- Task 6 在所有实现完成后执行
