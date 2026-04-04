# Tasks: 空 Session 创建

## 1. Wire 协议

### 1.1 状态转换扩展
- [ ] `packages/wire/src/enums.ts` — `VALID_TRANSITIONS.queued` 增加 `"idle"`：`queued: ["running", "idle", "failed"]`

## 2. Relay API

### 2.1 Schema 修改
- [ ] `packages/relay/src/routes/sessions.ts:16-28` — `createSessionBodySchema.required` 从 `["provider", "host_id", "cwd", "prompt"]` 改为 `["provider", "host_id", "cwd"]`
- [ ] `prompt` property 保留在 `properties` 中但变为 optional

### 2.2 Orchestrator `create()` 修改
- [ ] `packages/relay/src/session/orchestrator.ts:73-76` — 移除 `!input.prompt` 检查
- [ ] 验证消息改为 `"provider, host_id, cwd are required"`
- [ ] `hasPrompt` 分支判断：`const hasPrompt = !!input.prompt?.trim()`
- [ ] 有 prompt → 现有路径：dispatchCreate → markSessionStarted（queued → running）
- [ ] 无 prompt → 新路径：
  - `initial_prompt` 写入 `null`
  - 跳过 `dispatchCreate()`
  - 直接转为 `idle` 状态（`queued → idle`）
  - 广播 `session_idle` 事件，payload: `{ reason: "awaiting_first_message" }`
- [ ] `activeLifecycleMutations` 的 try/finally 逻辑确保两个分支都正确清理

### 2.3 Orchestrator `sendMessage()` 修改
- [ ] `packages/relay/src/session/orchestrator.ts` — `sendMessage()` 增加 idle + 无 provider 进程检测
- [ ] 检测条件：`session.status === "idle" && !session.provider_session_id`
- [ ] 命中时调用新方法 `startIdleSession(session, text)`
- [ ] `startIdleSession()` 实现：
  - 更新 DB `initial_prompt = text, updated_at = now()`
  - 构造带 prompt 的临时 session 对象
  - 调用 `dispatchCreate()` 启动 companion 进程
  - 调用 `markSessionStarted()` 转为 running（idle → running）
- [ ] 将 `startIdleSession` 包装在 `runWithLifecycleLock` 中防止并发
- [ ] 现有 idle + 有 provider 的 sendMessage 路径不变（正常的 idle 多轮对话）

### 2.4 Relay HTTP 响应
- [ ] 确认空 session 创建返回 201，response body 中 status = "idle"
- [ ] 确认 GET /sessions/:id 对空 session 返回正确数据（initial_prompt = null, status = "idle"）

## 3. Android 网络层

### 3.1 `RelayHttpClient.createSession()` 修改
- [ ] `packages/android/app/src/main/kotlin/com/imbot/android/network/RelayHttpClient.kt` — `prompt` 参数类型从 `String` 改为 `String?`
- [ ] JSON body 构建：`prompt` 非空非 blank 时才包含在 body 中
- [ ] 保持其他字段不变

## 4. Android 新建 Session UI

### 4.1 `canCreate()` 修改
- [ ] `packages/android/app/src/main/kotlin/com/imbot/android/ui/newsession/NewSessionScreen.kt:359-363` — 移除 `state.prompt.trim().isNotBlank()` 条件
- [ ] 新条件：`!state.provider.isNullOrBlank() && !state.hostId.isNullOrBlank() && !state.cwd.isNullOrBlank()`

### 4.2 Step 流程调整
- [ ] Step 2（Prompt 输入）保留但变为非必填
- [ ] `STEP_TITLES` 第三项从 "开始" 改为 "消息（可选）"
- [ ] Step 1（目录选择）完成后启用 "开始" 按钮（canCreate 已不要求 prompt）
- [ ] 用户在 Step 2 输入了 prompt → 带 prompt 创建
- [ ] 用户在 Step 2 留空 → 空 session 创建
- [ ] "开始" 按钮在 step ≥ 1 且 canCreate == true 时始终可见（不仅在 step 2）

### 4.3 `NewSessionViewModel` 修改
- [ ] `packages/android/app/src/main/kotlin/com/imbot/android/ui/newsession/NewSessionViewModel.kt` — `createSession()` 中 prompt 传值改为 `state.prompt.trim().takeIf { it.isNotBlank() }`
- [ ] 创建成功后导航到 detail 页面的逻辑不变

### 4.4 Detail 页面兼容
- [ ] 空 session 到达 detail 页面，status = "idle" → 输入框启用（已有逻辑支持）
- [ ] 确认 `canInputToSession("idle")` 返回 true
- [ ] 确认发送第一条消息走 `POST /sessions/:id/message` 路径
- [ ] 确认 session 转为 running 后 UI 正确更新

## 5. 测试

### 5.1 Unit Tests — Orchestrator 空 session 创建

**正常路径**：
- [ ] `create({ provider, host_id, cwd })` 无 prompt → session 创建成功，status = "idle"
- [ ] 空 session `sendMessage(id, "hello")` → session 转为 running，companion 启动
- [ ] 空 session sendMessage 后，provider_session_id 不为 null
- [ ] 带 prompt `create({ provider, host_id, cwd, prompt })` → 现有路径不变，status = "running"

**边界情况**：
- [ ] `create({ prompt: "" })` → 视为无 prompt，创建空 session
- [ ] `create({ prompt: "  " })` → 视为无 prompt（trim 后为空）
- [ ] 空 session 直接 cancel → idle → cancelled，无 companion 进程需清理
- [ ] 空 session 直接 delete → 直接删除
- [ ] 空 session 直接 complete → idle → completed（应该允许？或拒绝？设计决策：允许，与现有 idle complete 一致）
- [ ] 空 session + companion 离线时 sendMessage → host_offline 错误
- [ ] 两个客户端同时对空 session sendMessage → lifecycle lock 保护，第二个等待或失败

**状态转换**：
- [ ] `queued → idle` 转换在 VALID_TRANSITIONS 中合法
- [ ] `idle → running` 转换在第一条消息后触发
- [ ] session_idle 事件正确广播（payload 包含 reason）
- [ ] session_started 事件在第一条消息后正确广播

### 5.2 Contract Tests — Relay API

- [ ] `POST /sessions` 无 prompt 字段 → 201，response.status = "idle"
- [ ] `POST /sessions` 有 prompt 字段 → 201，response.status = "running"（现有行为）
- [ ] `POST /sessions` 无 provider → 400（现有行为不变）
- [ ] `POST /sessions/:id/message` 对 idle 空 session → 200，session 转 running
- [ ] `POST /sessions/:id/message` 对 idle 有 provider 的 session → 200（现有多轮行为不变）

### 5.3 Android Unit Tests

- [ ] `canCreate` 无 prompt → true（provider + hostId + cwd 齐全）
- [ ] `canCreate` 无 cwd → false
- [ ] `canCreate` 无 provider → false
- [ ] ViewModel createSession 调用 relayHttpClient.createSession 时 prompt 为 null
- [ ] ViewModel createSession 调用 relayHttpClient.createSession 时 prompt 为 "hello"
