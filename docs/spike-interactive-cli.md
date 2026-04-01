# Spike: Claude CLI Interactive Mode Verification

Date: 2026-03-31

## Goal

验证 Claude CLI 是否支持持久交互式 session（进程常驻、多轮对话、双向流式）。

## 方案测试矩阵

| # | 方案 | 结果 |
|---|------|------|
| v1 | 不带 `-p`，pipe stdin/stdout | FAIL — 需要 TTY |
| v2a | `-p` 不带 prompt arg，stdin 提供 | FAIL — 无输出 |
| v2b | `-p` 带 prompt arg，stdin 追加第二轮 | 首轮 OK，第二轮无响应 |
| v2c | 不带 `-p`，立即 stdin | FAIL — 需要 TTY |
| v3 | `-p -r <id> -- <prompt>` 顺序 spawn | 可行但每轮 9-13s |
| **v4c** | **`-p --input-format stream-json --output-format stream-json`** | **完美 ✓** |

## 最终方案：stream-json 双向协议

```
claude -p --input-format stream-json --output-format stream-json --verbose --permission-mode <mode>
```

### stdin 消息格式

```json
{"type":"user","message":{"role":"user","content":"<prompt text>"}}\n
```

### stdout 事件格式

标准 stream-json 事件流，与现有 `--output-format stream-json` 完全兼容：
- `{type:"system", subtype:"init", session_id:"...", ...}` — 每轮开始
- `{type:"assistant", message:{content:[{type:"text",text:"..."}]}}` — 响应 delta
- `{type:"result", subtype:"success", ...}` — 本轮结束
- 进程**不退出**，等待下一条 stdin 消息

### 性能数据

| Turn | Duration | Response | Notes |
|------|----------|----------|-------|
| 1 | 2.2s | TURN1_OK_42 | 含初始化 |
| 2 | 2.5s | TURN2_42 | 记住 42 ✓ |
| 3 | 1.9s | TURN3_3 | 记住 3 轮 ✓ |

- **进程常驻**：3 轮后仍 alive（exit code = null）
- **Session ID 一致**：全程同一个 session
- **Context 完全保持**：跨轮记忆正常
- **无 spawn 开销**：后续轮次 ~2s（vs `-p -r` 方案的 9-13s）
- **无需 PTY**：标准 pipe stdio 即可

### 与 Happy 项目的关系

Happy 的 SDK 模式（`packages/happy-cli/src/claude/sdk/query.ts`）使用了完全相同的方案：
- `--input-format stream-json --output-format stream-json`
- `stdio: ['pipe', 'pipe', 'pipe']`
- stdin 写 JSON line，stdout 读 JSON line
