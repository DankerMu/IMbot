# Design: Session 上下文用量实时展示

## 数据源调研

### Claude Code stream-json 输出中的 usage 信息

Claude Code CLI 在 stream-json 模式下输出以下与 usage 相关的消息类型（需实验验证）：

**type: "system" (session 启动时)**:
```json
{
  "type": "system",
  "session_id": "abc-123",
  "model": "claude-sonnet-4-6",
  "tools": [...]
}
```

可能包含 `model` 字段（标识模型名），但不一定包含 context window 大小。

**type: "result" (turn 结束时)**:
```json
{
  "type": "result",
  "result": "...",
  "usage": {
    "input_tokens": 12500,
    "output_tokens": 3200,
    "cache_creation_input_tokens": 0,
    "cache_read_input_tokens": 8900
  },
  "model": "claude-sonnet-4-6"
}
```

**type: "usage" (独立 usage 事件，可能不存在)**:
```json
{
  "type": "usage",
  "input_tokens": 12500,
  "output_tokens": 3200
}
```

**前置实验**（需人工执行）：
```bash
echo '{"type":"user","text":"hello"}' | book --input-format stream-json --output-format stream-json --verbose 2>/dev/null | jq 'select(.type == "result" or .type == "system" or .type == "usage")'
```

观察实际输出中是否包含 usage 字段以及具体结构。

### Context Window 大小映射

Claude 模型的 context window 大小是已知常量：

| 模型 | Context Window |
|------|---------------|
| claude-opus-4-6 | 200,000 |
| claude-sonnet-4-6 | 200,000 |
| claude-haiku-4-5 | 200,000 |
| claude-opus-4-5-20250414 | 200,000 |
| 其他 / 未知 | 200,000 (默认) |

可以硬编码在 Android 端，或通过 companion 从 `system` 消息中传递。

## 全栈数据流

```
Claude CLI stdout                    Companion                     Relay                   Android
─────────────────               ─────────────────             ─────────────              ────────────
type: "system"          →   event-mapper 提取          →  广播 session_started   →  记录 model 名
  session_id, model         model → session_started        payload.model              存入 uiState
                            payload

type: "result"          →   event-mapper 提取          →  广播 session_usage     →  更新用量 UI
  usage: {                  usage → 新事件类型              payload: {                 进度条 + 数字
    input_tokens,           "session_usage"                  input_tokens,
    output_tokens,                                           output_tokens,
    cache_read...                                            model
  }                                                        }
```

## 改动详解

### 1. Wire 协议 — 新增 usage 事件类型

**文件**: `packages/wire/src/enums.ts`

```typescript
export const EVENT_TYPES = [
  // ... 现有类型 ...
  "session_usage",    // ← 新增
] as const;
```

**文件**: `packages/wire/src/models.ts`

```typescript
// 新增 usage payload 类型（文档用途，运行时为 unknown）
export interface SessionUsagePayload {
  input_tokens: number;
  output_tokens: number;
  cache_creation_input_tokens?: number;
  cache_read_input_tokens?: number;
  model?: string;
}
```

### 2. Companion event-mapper 提取 usage

**文件**: `packages/companion/src/runtime/event-mapper.ts`

在 `map()` 方法中新增处理：

```typescript
// ── system (提取 model) ─────────────────────────────────
if (type === "system" && typeof record.session_id === "string") {
  return {
    kind: "provider_session_id",
    providerSessionId: record.session_id,
    // 新增: 透传 model
    model: getString(record.model)
  };
}

// ── result (提取 usage) ─────────────────────────────────
if (type === "result") {
  const messages: RuntimeMappedMessage[] = [];

  // 现有: session_result 事件
  messages.push({
    kind: "event",
    eventType: "session_result",
    payload: { result: record.result ?? record.output ?? null }
  });

  // 新增: 如果有 usage 字段，额外发射 session_usage 事件
  if (record.usage && typeof record.usage === "object") {
    const usage = record.usage as Record<string, unknown>;
    messages.push({
      kind: "event",
      eventType: "session_usage",
      payload: {
        input_tokens: typeof usage.input_tokens === "number" ? usage.input_tokens : 0,
        output_tokens: typeof usage.output_tokens === "number" ? usage.output_tokens : 0,
        cache_creation_input_tokens: typeof usage.cache_creation_input_tokens === "number"
          ? usage.cache_creation_input_tokens : undefined,
        cache_read_input_tokens: typeof usage.cache_read_input_tokens === "number"
          ? usage.cache_read_input_tokens : undefined,
        model: getString(record.model)
      }
    });
  }

  return messages;  // 需要修改 map() 返回类型支持数组
}
```

**注意**: `map()` 当前返回 `RuntimeMappedMessage | null`。为了支持 `result` 类型产生两个事件，有两个选择：

- **选项 A**: `map()` 返回 `RuntimeMappedMessage | RuntimeMappedMessage[] | null`，调用方遍历数组
- **选项 B**: 在 `claude-adapter.ts` 的 `handleStdoutLine()` 中单独处理 usage 提取（在 map 之后）

**推荐选项 B**：改动更小，不影响 map 函数签名。

```typescript
// claude-adapter.ts handleStdoutLine() 中:
private handleStdoutLine(session: RuntimeSession, line: string): void {
  const raw = safeJsonParse(line);
  // ... 现有 mapped = mapper.map(raw) 逻辑 ...

  // 新增: result 类型中提取 usage
  if (raw && typeof raw === "object" && (raw as any).type === "result") {
    this.extractAndEmitUsage(session, raw as Record<string, unknown>);
  }
}

private extractAndEmitUsage(session: RuntimeSession, record: Record<string, unknown>): void {
  const usage = record.usage;
  if (!usage || typeof usage !== "object") return;

  const u = usage as Record<string, unknown>;
  this.emitSessionEvent(session, "session_usage", {
    input_tokens: typeof u.input_tokens === "number" ? u.input_tokens : 0,
    output_tokens: typeof u.output_tokens === "number" ? u.output_tokens : 0,
    cache_creation_input_tokens: typeof u.cache_creation_input_tokens === "number"
      ? u.cache_creation_input_tokens : undefined,
    cache_read_input_tokens: typeof u.cache_read_input_tokens === "number"
      ? u.cache_read_input_tokens : undefined,
    model: typeof record.model === "string" ? record.model : session.model ?? undefined
  });
}
```

### 3. Companion — 传递 model 信息

**文件**: `packages/companion/src/runtime/claude-adapter.ts`

在 `session_started` 事件中包含 model:

```typescript
// registerProviderSessionId 或 session 启动时
// 从 system 消息中提取的 model 存入 session
session.model = providerModel;  // 需要在 RuntimeSession 增加 model 字段

// session_started 事件中包含 model
this.emitSessionEvent(session, "session_started", {
  provider_session_id: providerSessionId,
  model: session.model
});
```

### 4. Relay — 广播 session_usage 事件

**Relay 不需要特殊处理**：`session_usage` 事件通过现有的 event broadcast 管道自动广播给所有订阅者（包括 Android WS 连接）。Relay 不持久化 usage 事件（仅广播）。

但需要确认 relay 的事件写入是否会因为新的 event type 而报错。查看 event 写入逻辑是否有类型白名单检查。

**文件**: `packages/relay/src/session/orchestrator.ts` 的 `insertAndBroadcastEvent()`

如果有 `EVENT_TYPES` 校验，需要确保新增的 `session_usage` 在白名单中（已在 wire enums 中添加）。

### 5. Android — UI 展示

#### 5.1 新增 UsageState 数据模型

**文件**: `packages/android/app/src/main/kotlin/com/imbot/android/ui/detail/DetailViewModel.kt`

```kotlin
data class SessionUsageState(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cacheReadTokens: Int = 0,
    val model: String? = null,
) {
    val totalTokens: Int get() = inputTokens + outputTokens
    val contextWindowSize: Int get() = modelContextWindow(model)
    val usagePercent: Float get() =
        if (contextWindowSize > 0) totalTokens.toFloat() / contextWindowSize else 0f
}

private fun modelContextWindow(model: String?): Int = when {
    model == null -> 200_000
    model.contains("opus") -> 200_000
    model.contains("sonnet") -> 200_000
    model.contains("haiku") -> 200_000
    else -> 200_000
}
```

#### 5.2 DetailUiState 扩展

```kotlin
internal data class DetailUiState(
    // ... 现有字段 ...
    val usage: SessionUsageState = SessionUsageState(),
)
```

#### 5.3 EventProcessor 处理 session_usage

**文件**: `packages/android/app/src/main/kotlin/com/imbot/android/ui/detail/EventProcessor.kt`

```kotlin
// 在 processEvent() 中新增 session_usage 处理
"session_usage" -> {
    // 不添加到 messages 列表（不显示在 timeline 中）
    // 返回 usage 数据供 ViewModel 更新
    return ProcessResult(usageUpdate = extractUsage(payload))
}
```

#### 5.4 DetailViewModel 更新 usage

```kotlin
// handleSessionEvent 中接收 usage 更新
if (result.usageUpdate != null) {
    _uiState.update { current ->
        current.copy(usage = result.usageUpdate)
    }
}
```

#### 5.5 Detail 页面顶部 Usage 展示

**文件**: `packages/android/app/src/main/kotlin/com/imbot/android/ui/detail/SessionDetailScreen.kt`

在 TopAppBar 区域添加 usage 显示：

```kotlin
@Composable
private fun UsageIndicator(
    usage: SessionUsageState,
    isActive: Boolean,  // running or idle
    modifier: Modifier = Modifier,
) {
    if (!isActive || usage.totalTokens == 0) return

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 紧凑文字: "12.5k / 200k"
        Text(
            text = formatTokenCount(usage.totalTokens) + " / " + formatTokenCount(usage.contextWindowSize),
            style = MaterialTheme.typography.labelSmall,
            color = usageColor(usage.usagePercent),
        )

        // 微型进度条
        LinearProgressIndicator(
            progress = { usage.usagePercent.coerceIn(0f, 1f) },
            modifier = Modifier
                .width(40.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp)),
            color = usageColor(usage.usagePercent),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

private fun usageColor(percent: Float): Color = when {
    percent > 0.9f -> Color(0xFFE53935)   // 红色 >90%
    percent > 0.8f -> Color(0xFFFFA726)   // 橙色 >80%
    else -> Color(0xFF66BB6A)             // 绿色
}

private fun formatTokenCount(count: Int): String = when {
    count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000f)
    count >= 1_000 -> String.format("%.1fk", count / 1_000f)
    else -> count.toString()
}
```

**布局位置**：在 TopAppBar 的 subtitle 行下方，或替换 subtitle 位置当 session 处于 running/idle 状态时。

```
┌────────────────────────────────────────────┐
│ [CC] Claude Code                    [运行中] │
│      IMbot    12.5k / 200k ████░░░░░  6%   │
└────────────────────────────────────────────┘
```

## 影响范围

| 组件 | 改动 | 风险 |
|------|------|------|
| `packages/wire/src/enums.ts` | 新增 `session_usage` 事件类型 | 低 |
| `packages/wire/src/models.ts` | 新增 `SessionUsagePayload` 接口 | 低 |
| `packages/companion/src/runtime/claude-adapter.ts` | 提取 usage + 发射事件 | 中（需验证 stream-json 实际输出） |
| `packages/companion/src/runtime/claude-adapter.ts` | RuntimeSession 增加 model 字段 | 低 |
| `packages/android/.../DetailViewModel.kt` | 新增 SessionUsageState | 低 |
| `packages/android/.../EventProcessor.kt` | 处理 session_usage 事件 | 低 |
| `packages/android/.../SessionDetailScreen.kt` | UsageIndicator composable | 低 |
| `packages/android/.../DetailUtils.kt` | formatTokenCount + usageColor | 低 |
| `tests/unit/control-protocol.test.mjs` | session_usage 事件测试 | — |
| Android unit tests | usage 状态更新测试 | — |

## 依赖和风险

1. **最大风险**：Claude Code stream-json 输出中可能不包含 usage 字段。如果 `type: "result"` 消息没有 `usage` 对象，则整个功能无法工作。**必须先实验验证**。
2. **累计 usage**：usage 数据每 turn 更新一次（result 事件触发时）。mid-turn 没有 usage 更新。用户可能觉得数据不够实时。
3. **Context window 硬编码**：模型的 context window 大小硬编码在 Android 端。如果未来模型 context window 变化，需要更新。可以考虑从 companion 传递，但增加复杂度。
4. **idle session**：idle 状态的 session（用户完成一轮对话后等待下一轮）应该保留最后一次的 usage 显示，不清零。
