# Design: 空 Session 创建

## 当前流程

```
Android NewSessionScreen          Relay API             Orchestrator              Companion
     canCreate() 检查 ─────→ POST /sessions ──────→ create() ──────────────→ createSession()
     prompt.isNotBlank()     required: ["prompt"]   !input.prompt → throw     spawn book + send prompt
           │                        │                      │                         │
           ▼                        ▼                      ▼                         ▼
     按钮禁用直到输入prompt    400 if no prompt      RelayError               book 进程启动并处理 prompt
```

## 目标流程

```
【带 prompt 创建】 (现有路径，不变)
Android → POST /sessions {prompt: "hello"} → orchestrator.create() → companion.createSession()
                                            → status: queued → running

【无 prompt 创建】 (新增路径)
Android → POST /sessions {无 prompt} → orchestrator.create() → 不启动 companion
                                      → status: idle → 等待首条消息
                                                         │
用户发消息 → POST /sessions/:id/message → orchestrator.sendMessage()
                                        → 检测 idle + 无 provider 进程
                                        → companion.createSession({prompt: text})
                                        → status: idle → running
```

## 改动详解

### 1. Wire 协议 — `CreateSessionCommand`

**文件**: `packages/wire/src/models.ts`

```typescript
// prompt 已经是 optional 类型，但实际被 relay schema 强制为 required
// 无需改动 wire 类型定义
```

### 2. Relay API Schema

**文件**: `packages/relay/src/routes/sessions.ts:16-28`

```typescript
const createSessionBodySchema = {
  type: "object",
  required: ["provider", "host_id", "cwd"],  // ← 移除 "prompt"
  additionalProperties: false,
  properties: {
    provider: { type: "string" },
    host_id: { type: "string" },
    cwd: { type: "string" },
    prompt: { type: "string" },       // 保留但 optional
    model: { type: "string" },
    permission_mode: { type: "string" }
  }
} as const;
```

### 3. Session Orchestrator

**文件**: `packages/relay/src/session/orchestrator.ts`

#### 3.1 `create()` 方法改动

```typescript
async create(input: CreateSessionInput): Promise<Session> {
  // 改: 移除 !input.prompt 检查
  if (!input.provider || !input.host_id || !input.cwd) {
    throw new RelayError("invalid_request", "provider, host_id, cwd are required");
  }

  // ... 现有验证 ...

  const hasPrompt = !!input.prompt?.trim();
  const session: Session = {
    // ... 现有字段 ...
    initial_prompt: hasPrompt ? input.prompt : null,
    status: "queued",  // 仍然以 queued 开始
  };

  // 写入 DB ...

  if (hasPrompt) {
    // 现有路径: 立即启动 companion，转为 running
    const providerSessionId = await this.dispatchCreate(session);
    await this.markSessionStarted(session.id, providerSessionId, "queued");
  } else {
    // 新增路径: 不启动 companion，直接转为 idle
    await this.transitionToIdle(session.id);
    this.insertAndBroadcastEvent(session.id, "session_idle", {
      reason: "awaiting_first_message"
    });
  }

  return this.getSession(session.id) ?? session;
}
```

#### 3.2 `sendMessage()` 方法改动

```typescript
async sendMessage(sessionId: string, text: string): Promise<void> {
  const session = this.requireSession(sessionId);

  if (session.status === "idle" && !session.provider_session_id) {
    // 空 session 的第一条消息 — 启动 provider 进程
    await this.startIdleSession(session, text);
    return;
  }

  // 现有逻辑: running 状态下发送消息
  if (session.status !== "running" && session.status !== "idle") {
    throw new RelayError("state_conflict", ...);
  }

  await this.dispatchSendMessage(session, text);
}

private async startIdleSession(session: Session, firstMessage: string): Promise<void> {
  // 更新 initial_prompt
  this.db.prepare(
    "UPDATE sessions SET initial_prompt = ?, updated_at = datetime('now') WHERE id = ?"
  ).run(firstMessage, session.id);

  // 构造一个临时 session 对象用于 dispatchCreate
  const sessionWithPrompt = { ...session, initial_prompt: firstMessage };
  const providerSessionId = await this.dispatchCreate(sessionWithPrompt);
  await this.markSessionStarted(session.id, providerSessionId, "idle");
}
```

#### 3.3 状态转换验证

当前 `VALID_TRANSITIONS`:
```typescript
queued: ["running", "failed"],
idle: ["running", "completed", "failed", "cancelled"],
```

新增路径需要 `queued → idle` 转换：

**文件**: `packages/wire/src/enums.ts`

```typescript
export const VALID_TRANSITIONS = {
  queued: ["running", "idle", "failed"],  // ← 新增 "idle"
  // ... 其余不变
};
```

### 4. Android 新建 Session 流程

#### 4.1 `canCreate()` 移除 prompt 要求

**文件**: `packages/android/app/src/main/kotlin/com/imbot/android/ui/newsession/NewSessionScreen.kt:359-363`

```kotlin
internal fun canCreate(state: NewSessionUiState): Boolean =
    !state.provider.isNullOrBlank() &&
    !state.hostId.isNullOrBlank() &&
    !state.cwd.isNullOrBlank()
    // 移除: state.prompt.trim().isNotBlank()
```

#### 4.2 步骤流程调整

当前三步: Provider → 目录 → Prompt
调整为: Provider → 目录 → 可选 Prompt

```kotlin
// STEP_TITLES 调整
private val STEP_TITLES = listOf("Provider", "目录", "消息（可选）")

// Step 2 UI: prompt input 保留但不强制
// "开始" 按钮在 step 1 完成后即可用
// Step 2 变为可选: 用户可以输入 prompt 也可以直接点"开始"
```

**具体 UI 改动**：

1. Step 1（目录选择）完成后，底部按钮从"下一步"变为"开始"+"下一步(可选)"双按钮
2. 或者更简洁: 移除 Step 2, 将 prompt 输入集成到 session detail 页面的输入框中
3. **推荐方案**: 保留 3 步但 Step 2 的"下一步"改为"开始"，同时 prompt 输入变为非必填。如果用户填了 prompt 就带 prompt 创建；没填就空创建。

#### 4.3 `NewSessionViewModel` 调整

**文件**: `packages/android/app/src/main/kotlin/com/imbot/android/ui/newsession/NewSessionViewModel.kt`

```kotlin
// createSession 调用中 prompt 改为 nullable
val prompt = state.prompt.trim().takeIf { it.isNotBlank() }
relayHttpClient.createSession(
    relayUrl = settings.relayUrl,
    token = settings.token,
    provider = state.provider!!,
    hostId = state.hostId!!,
    cwd = state.cwd!!,
    prompt = prompt,  // nullable
    model = state.model,
    permissionMode = null,
)
```

#### 4.4 `RelayHttpClient.createSession()` 调整

**文件**: `packages/android/app/src/main/kotlin/com/imbot/android/network/RelayHttpClient.kt`

```kotlin
// prompt 参数改为 nullable
suspend fun createSession(
    relayUrl: String,
    token: String,
    provider: String,
    hostId: String,
    cwd: String,
    prompt: String?,  // ← nullable
    model: String?,
    permissionMode: String?,
): Result<RelaySession> {
    // JSON body: 只在 prompt 非空时包含 prompt 字段
    val body = buildJsonObject {
        put("provider", provider)
        put("host_id", hostId)
        put("cwd", cwd)
        if (!prompt.isNullOrBlank()) {
            put("prompt", prompt)
        }
        // ...
    }
}
```

### 5. Detail 页面对空 Session 的兼容

空 session 到达 detail 页面时 status = `idle`，这已经被现有 UI 支持：
- 输入框启用（`canInputToSession("idle")` → true）
- "继续对话..." placeholder
- 发送消息走 `POST /sessions/:id/message`

**唯一改动**：空 session 的 detail 页面顶部可以显示一个提示文案如"发送第一条消息开始对话"，但这不是必须的。

## 影响范围

| 组件 | 改动 | 风险 |
|------|------|------|
| `packages/wire/src/enums.ts` | `queued` 增加 `idle` 转换 | 低 |
| `packages/relay/src/routes/sessions.ts` | schema 移除 prompt required | 低 |
| `packages/relay/src/session/orchestrator.ts` | `create()` 分支 + `sendMessage()` idle 启动 | 中 |
| `packages/android/.../NewSessionScreen.kt` | `canCreate()` + step 调整 | 低 |
| `packages/android/.../NewSessionViewModel.kt` | prompt nullable | 低 |
| `packages/android/.../RelayHttpClient.kt` | prompt nullable | 低 |
| `tests/unit/relay-core.test.mjs` | 新增空 session 测试 | — |
| `tests/contract/session-api.test.mjs` | 新增无 prompt 创建契约测试 | — |

## 边界情况

1. **空 session + companion 离线**：session 以 idle 创建后 companion 断线 → 用户发消息 → sendMessage 检测 companion 离线 → 返回 `host_offline` 错误 → 与现有 idle session 发消息时 companion 离线的行为一致
2. **空 session + 取消**：idle 状态可以 cancel → 直接转 cancelled → 无 provider 进程需要清理
3. **空 session + 删除**：idle 状态可以 delete → 直接删除 → 无 provider 进程需要清理
4. **空 session + resume**：idle 状态不是 resumable → resume 按钮不显示 → 正确
5. **并发创建**：两个客户端同时对同一个空 session 发送第一条消息 → orchestrator 的 lifecycle lock 保护 → 第二个请求等锁或失败 → 正确
