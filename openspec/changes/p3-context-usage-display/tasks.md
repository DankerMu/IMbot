# Tasks: Session 上下文用量实时展示

## 0. 前置验证（人工）

### 0.1 验证 stream-json usage 输出
- [ ] 运行 `echo '{"type":"user","text":"hello"}' | book --input-format stream-json --output-format stream-json --verbose 2>/dev/null | jq '.'` 捕获完整输出
- [ ] 检查 `type: "result"` 消息中是否包含 `usage` 对象
- [ ] 如果有：记录字段名和类型（`input_tokens`, `output_tokens`, `cache_creation_input_tokens`, `cache_read_input_tokens`）
- [ ] 检查 `type: "system"` 消息中是否包含 `model` 字段
- [ ] 如果 usage 不存在：整个 feature 需要重新评估（可能需要调用 Anthropic API 查询 usage，或等待 CLI 支持）

## 1. Wire 协议

### 1.1 新增 `session_usage` 事件类型
- [ ] `packages/wire/src/enums.ts` — `EVENT_TYPES` 数组增加 `"session_usage"`
- [ ] 确认 TypeScript 编译通过，`EventType` union type 自动包含新类型

### 1.2 新增 Usage Payload 类型（文档用途）
- [ ] `packages/wire/src/models.ts` — 新增 `SessionUsagePayload` 接口
- [ ] 字段：`input_tokens: number`, `output_tokens: number`, `cache_creation_input_tokens?: number`, `cache_read_input_tokens?: number`, `model?: string`

## 2. Companion

### 2.1 RuntimeSession 增加 model 字段
- [ ] `packages/companion/src/runtime/claude-adapter.ts` — `RuntimeSession` 类增加 `model?: string` 字段
- [ ] 在 `spawnSession()` 中从 command 参数初始化 model
- [ ] 在 `registerProviderSessionId()` 中从 system 消息更新 model（如果有更精确的值）

### 2.2 提取 model 信息
- [ ] `packages/companion/src/runtime/event-mapper.ts` — `map()` 处理 `type: "system"` 时，`RuntimeMappedMessage` 的 `provider_session_id` 分支增加可选 `model` 字段
- [ ] `claude-adapter.ts` — 处理 `provider_session_id` 映射结果时，将 model 存入 session

### 2.3 提取并发射 usage 事件
- [ ] `packages/companion/src/runtime/claude-adapter.ts` — `handleStdoutLine()` 中，在现有 `map()` 处理之后，检查原始 JSON 的 `type === "result"` 且包含 `usage` 字段
- [ ] 新增私有方法 `extractAndEmitUsage(session, record)`:
  - 从 `record.usage` 提取 `input_tokens`, `output_tokens`, `cache_creation_input_tokens`, `cache_read_input_tokens`
  - 从 `record.model` 或 `session.model` 获取模型名
  - 调用 `emitSessionEvent(session, "session_usage", payload)` 广播事件
- [ ] 确保 `session_usage` 事件在 `session_result` 事件之后发射（result 已经被 map() 处理）

### 2.4 session_started 事件包含 model
- [ ] `claude-adapter.ts` — `markSessionAsStarted()` 或 session_started 事件发射时，payload 增加 `model` 字段
- [ ] 来源：command.model（用户指定）或 system 消息中的 model（provider 返回）

## 3. Relay

### 3.1 事件广播验证
- [ ] 确认 `insertAndBroadcastEvent()` 对 `session_usage` 类型不做特殊拦截
- [ ] 确认 wire 的 `EVENT_TYPES` 校验通过
- [ ] 确认 WS 广播正确发送 `session_usage` 事件给所有订阅该 session 的客户端

### 3.2 事件持久化决策
- [ ] `session_usage` 事件 **不持久化** 到 events 表（它是高频临时数据）
- [ ] 检查 `insertAndBroadcastEvent()` 是否总是持久化——如果是，需要新增一个 `broadcastOnly` 路径或在事件类型上做分支
- [ ] 或者：简单地允许持久化（事件数量不多，每 turn 最多 1 条）—— 设计决策取决于性能考量

## 4. Android 数据层

### 4.1 新增 `SessionUsageState` 数据类
- [ ] `packages/android/app/src/main/kotlin/com/imbot/android/ui/detail/DetailUtils.kt` — 新增:
  ```kotlin
  data class SessionUsageState(
      val inputTokens: Int = 0,
      val outputTokens: Int = 0,
      val cacheReadTokens: Int = 0,
      val model: String? = null,
  )
  ```
- [ ] 新增计算属性: `totalTokens`, `contextWindowSize`, `usagePercent`
- [ ] 新增 `modelContextWindow(model: String?): Int` 函数，返回已知模型的 context window 大小

### 4.2 `DetailUiState` 扩展
- [ ] `packages/android/app/src/main/kotlin/com/imbot/android/ui/detail/DetailViewModel.kt` — `DetailUiState` 增加 `usage: SessionUsageState = SessionUsageState()`

### 4.3 EventProcessor 处理 session_usage
- [ ] `packages/android/app/src/main/kotlin/com/imbot/android/ui/detail/EventProcessor.kt` — `processEvent()` 增加 `"session_usage"` 分支
- [ ] 不添加到 messages 列表（usage 不在 timeline 中显示）
- [ ] 从 payload 提取 `input_tokens`, `output_tokens`, `cache_read_input_tokens`, `model`
- [ ] 返回 usage 数据供 ViewModel 使用（可能需要扩展 ProcessResult 或使用回调）

### 4.4 DetailViewModel 更新 usage state
- [ ] `handleSessionEvent()` 中接收 session_usage 事件处理结果
- [ ] 更新 `_uiState.value.usage`
- [ ] 在 `session_started` 事件中提取 model 信息初始化 usage.model
- [ ] session 结束（completed/failed/cancelled）时保留最后的 usage 值（不清零）
- [ ] `loadSession()` 重置时清零 usage

### 4.5 提取 model 从 session_started
- [ ] `session_started` 事件的 payload 中如果有 `model` 字段，存入 `usage.model`
- [ ] 如果 RelaySession 本身有 `model` 字段且非空，用作 fallback

## 5. Android UI 层

### 5.1 UsageIndicator Composable
- [ ] `packages/android/app/src/main/kotlin/com/imbot/android/ui/detail/SessionDetailScreen.kt` — 新增 `UsageIndicator` composable
- [ ] 参数：`usage: SessionUsageState`, `isActive: Boolean`
- [ ] isActive = false 或 totalTokens = 0 时不渲染（return 空）
- [ ] 显示：token 计数文字 + 微型进度条
- [ ] 格式：`"12.5k / 200k"` — 用 `formatTokenCount()` 格式化
- [ ] 进度条宽度 40dp，高度 3dp，圆角

### 5.2 颜色预警
- [ ] `usageColor(percent)` 函数：
  - \>90%: 红色 `#E53935`
  - \>80%: 橙色 `#FFA726`
  - ≤80%: 绿色 `#66BB6A`
- [ ] 文字和进度条同色

### 5.3 布局集成
- [ ] 将 `UsageIndicator` 放置在 TopAppBar 的 subtitle 区域旁边或下方
- [ ] 仅在 `effectiveStatus` 为 `running` 或 `idle` 时显示
- [ ] 确保不与现有 provider badge / status badge 冲突
- [ ] 深色/浅色主题适配

### 5.4 Token 格式化工具函数
- [ ] `packages/android/app/src/main/kotlin/com/imbot/android/ui/detail/DetailUtils.kt` — 新增 `formatTokenCount(count: Int): String`
- [ ] ≥1,000,000 → "1.0M"
- [ ] ≥1,000 → "12.5k"
- [ ] <1,000 → "123"

## 6. 测试

### 6.1 Unit Tests — Companion usage 提取

- [ ] stream-json `type: "result"` 含 `usage` 对象 → 发射 `session_usage` 事件，payload 包含 input_tokens/output_tokens
- [ ] stream-json `type: "result"` 无 `usage` 字段 → 不发射 `session_usage` 事件
- [ ] stream-json `type: "result"` usage 字段不完整（只有 input_tokens）→ 发射事件，output_tokens 默认 0
- [ ] stream-json `type: "system"` 含 `model` 字段 → model 存入 session
- [ ] session_started 事件包含 model payload

### 6.2 Unit Tests — Android SessionUsageState

- [ ] `SessionUsageState(inputTokens = 12500, outputTokens = 3200)` → totalTokens = 15700
- [ ] `SessionUsageState(model = "claude-sonnet-4-6")` → contextWindowSize = 200000
- [ ] `SessionUsageState(model = null)` → contextWindowSize = 200000（默认）
- [ ] `usagePercent` 计算：15700 / 200000 = 0.0785
- [ ] `usagePercent` 上界: totalTokens > contextWindowSize → coerce to 1.0

### 6.3 Unit Tests — formatTokenCount

- [ ] formatTokenCount(500) → "500"
- [ ] formatTokenCount(1000) → "1.0k"
- [ ] formatTokenCount(12500) → "12.5k"
- [ ] formatTokenCount(200000) → "200.0k"
- [ ] formatTokenCount(1500000) → "1.5M"
- [ ] formatTokenCount(0) → "0"

### 6.4 Unit Tests — usageColor

- [ ] usageColor(0.5f) → 绿色
- [ ] usageColor(0.8f) → 绿色（≤80%）
- [ ] usageColor(0.81f) → 橙色
- [ ] usageColor(0.91f) → 红色

### 6.5 Unit Tests — EventProcessor session_usage

- [ ] session_usage 事件不添加到 messages 列表
- [ ] session_usage 事件的 payload 正确提取为 SessionUsageState
- [ ] 连续两个 session_usage 事件 → 后者覆盖前者（取最新值）

### 6.6 Unit Tests — DetailViewModel usage state

- [ ] session_usage 事件更新 uiState.usage
- [ ] session_started 事件更新 uiState.usage.model
- [ ] loadSession 重置 usage 为默认值
- [ ] session 结束后 usage 保留最后值
