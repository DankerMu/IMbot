# IMbot 端到端测试方案 v2

> **Provider**: 全部使用 `book`（claude 额度耗尽）。book CLI 行为与 claude 相同（stream-json 协议），差异仅在工作目录约束和 provider 名。
>
> **实现目标**: 本文档将交给 Codex 实现为自动化测试脚本。每个 test case 包含精确步骤和断言。Codex 应将每个 section 实现为 `tests/e2e/*.test.mjs` 文件，使用 Node 内置 test runner。

---

## 环境

| 组件 | 配置 |
|------|------|
| Relay | `https://imbot.23-95-164-218.sslip.io`（VPS pm2） |
| Companion | MacBook launchd `com.imbot.companion`，host_id: `macbook-1` |
| Token | `$IMBOT_TOKEN`（从 `~/.imbot/companion.json` 读取） |
| Provider | `book`（MacBook 本地 CLI） |
| Book CWD | companion 上已配置的 book workspace root 下的某个目录 |
| Android | 模拟器 `emulator-5554`（IMbot_API_35, API 35） |
| ADB | `$HOME/Library/Android/sdk/platform-tools/adb` |

### 公共 Helper

所有测试文件共享以下 helper：

```javascript
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { homedir } from "node:os";

function loadConfig() {
  const raw = JSON.parse(readFileSync(join(homedir(), ".imbot", "companion.json"), "utf8"));
  return {
    baseUrl: raw.relay_url.replace(/\/+$/, "").replace("/v1/ws", "").replace("/v1", ""),
    token: raw.token
  };
}

function api(method, path, body) {
  const { baseUrl, token } = loadConfig();
  const url = `${baseUrl}/v1${path}`;
  const options = {
    method,
    headers: {
      "Authorization": `Bearer ${token}`,
      "Content-Type": "application/json"
    }
  };
  if (body) options.body = JSON.stringify(body);
  return fetch(url, options);
}

function apiGet(path) { return api("GET", path); }
function apiPost(path, body) { return api("POST", path, body); }
function apiDelete(path) { return api("DELETE", path); }

// 等待 session 到达目标 status，轮询间隔 1s，超时 timeoutMs
async function waitForStatus(sessionId, targetStatuses, timeoutMs = 60000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    const res = await apiGet(`/sessions/${sessionId}`);
    const session = await res.json();
    if (targetStatuses.includes(session.status)) return session;
    await new Promise(r => setTimeout(r, 1000));
  }
  throw new Error(`Session ${sessionId} did not reach ${targetStatuses} within ${timeoutMs}ms`);
}

// WebSocket helper - 连接 relay WS 并订阅 session
import WebSocket from "ws";
function connectWs(sessionId) {
  const { baseUrl, token } = loadConfig();
  const wsUrl = baseUrl.replace(/^http/, "ws") + `/v1/ws?token=${encodeURIComponent(token)}`;
  const ws = new WebSocket(wsUrl);
  const events = [];
  return new Promise((resolve, reject) => {
    ws.on("open", () => {
      ws.send(JSON.stringify({ action: "auth", token }));
      if (sessionId) {
        ws.send(JSON.stringify({ action: "subscribe", session_id: sessionId }));
      }
      resolve({ ws, events });
    });
    ws.on("message", (data) => {
      try { events.push(JSON.parse(data.toString())); } catch {}
    });
    ws.on("error", reject);
  });
}

function waitForWsEvent(events, predicate, timeoutMs = 30000) {
  return new Promise((resolve, reject) => {
    const start = Date.now();
    const check = () => {
      const found = events.find(predicate);
      if (found) return resolve(found);
      if (Date.now() - start > timeoutMs) return reject(new Error("WS event timeout"));
      setTimeout(check, 200);
    };
    check();
  });
}
```

---

## 1. 基础设施

### E2E-01: Health 端点

**步骤**:
1. `GET /healthz`（无 auth header）

**断言**:
- HTTP 200
- body.status === "ok"
- body.db === "ok"
- body.companion === "online"
- body.uptime > 0

### E2E-02: 认证拒绝

**步骤**:
1. `GET /v1/sessions`，不带 Authorization header
2. `GET /v1/sessions`，带 `Authorization: Bearer wrong-token`

**断言**:
- 两次均返回 HTTP 401
- body.error === "unauthenticated"

---

## 2. Host 与 Workspace 管理

### E2E-03: 列出 Host

**步骤**:
1. `GET /v1/hosts`

**断言**:
- HTTP 200
- hosts 数组包含 host_id === "relay-local"（status: "online"）
- hosts 数组包含 host_id === "macbook-1"（status: "online"，providers 包含 "book"）

### E2E-04: 列出 Workspace Root

**步骤**:
1. `GET /v1/hosts/macbook-1/roots`

**断言**:
- HTTP 200
- roots 数组中至少有一个 provider === "book" 的 root
- 每个 root 有 id、host_id、provider、path、label、created_at 字段
- 记录一个 book root 的 path 为 `BOOK_ROOT_PATH`，后续测试使用

### E2E-05: 添加和删除 Workspace Root

**前置**: 准备一个真实存在的目录路径（如 `/tmp/imbot-e2e-root-test`，先创建）

**步骤**:
1. `POST /v1/hosts/macbook-1/roots` body: `{ "provider": "book", "path": "/tmp/imbot-e2e-root-test" }`
2. 记录返回的 root.id
3. `GET /v1/hosts/macbook-1/roots` 确认新 root 在列表中
4. `DELETE /v1/hosts/macbook-1/roots/{root_id}`
5. `GET /v1/hosts/macbook-1/roots` 确认已删除

**断言**:
- POST 返回 201，root.provider === "book"，root.path 包含 "/tmp/imbot-e2e-root-test"
- 中间 GET 包含该 root
- DELETE 返回 204
- 最终 GET 不包含该 root

**清理**: 删除 `/tmp/imbot-e2e-root-test` 目录

### E2E-06: Root Provider-Host 类型校验

**步骤**:
1. `POST /v1/hosts/relay-local/roots` body: `{ "provider": "book", "path": "/tmp" }`
2. `POST /v1/hosts/macbook-1/roots` body: `{ "provider": "openclaw", "path": "/tmp" }`

**断言**:
- 两次均返回 HTTP 400
- body.error 包含 "invalid_request"

### E2E-07: 目录浏览

**前置**: 已知 `BOOK_ROOT_PATH` 来自 E2E-04

**步骤**:
1. `GET /v1/hosts/macbook-1/browse?path={BOOK_ROOT_PATH}`

**断言**:
- HTTP 200
- body.path 为绝对路径
- body.directories 是数组，每项有 name 和 path 字段
- 每个 directory.path 以 body.path 为前缀

### E2E-08: 目录浏览安全 - 路径穿越拒绝

**步骤**:
1. `GET /v1/hosts/macbook-1/browse?path={BOOK_ROOT_PATH}/../../../etc`

**断言**:
- HTTP 403
- body.error === "forbidden"

### E2E-09: 不存在的 Host

**步骤**:
1. `GET /v1/hosts/nonexistent/roots`
2. `GET /v1/hosts/nonexistent/browse?path=/tmp`

**断言**:
- 两次均返回 HTTP 404

---

## 3. Session 生命周期 - 基本流程（book provider）

### E2E-10: 创建 Book Session → 运行 → 空闲

**前置**: 已知 `BOOK_ROOT_PATH` 和该 root 下的一个有效 cwd

**步骤**:
1. `POST /v1/sessions` body:
   ```json
   {
     "provider": "book",
     "host_id": "macbook-1",
     "cwd": "{BOOK_CWD}",
     "prompt": "说一句话就好",
     "permission_mode": "bypassPermissions"
   }
   ```
2. 记录返回的 session.id
3. 轮询 `GET /v1/sessions/{id}` 等待 status 变为 "idle"（超时 60s）

**断言**:
- POST 返回 201
- session.provider === "book"
- session.status 初始为 "queued" 或 "running"
- 最终 session.status === "idle"
- session.provider_session_id 非空（book CLI 分配的）
- session.initial_prompt === "说一句话就好"

### E2E-11: 空闲 Session 发送消息 → 再次空闲

**前置**: E2E-10 的 session 处于 idle 状态

**步骤**:
1. `POST /v1/sessions/{id}/message` body: `{ "text": "再说一句" }`
2. 立即 `GET /v1/sessions/{id}` 检查 status（应为 "running"）
3. 轮询等待 status 变为 "idle"（超时 60s）

**断言**:
- POST /message 返回 200，body.ok === true
- 中间 GET 的 status === "running"（如果轮询够快能抓到）
- 最终 status === "idle"

### E2E-12: 空闲 Session 主动完成

**前置**: E2E-11 后 session 处于 idle 状态

**步骤**:
1. `POST /v1/sessions/{id}/complete`
2. 轮询等待 status 变为 "completed"（超时 30s）

**断言**:
- POST /complete 返回 200
- 最终 session.status === "completed"

### E2E-13: 已完成 Session 的事件流

**前置**: E2E-12 完成

**步骤**:
1. `GET /v1/sessions/{id}/events?since_seq=0`

**断言**:
- HTTP 200
- events 数组非空，按 seq 升序排列
- 包含至少这些 event_type（按顺序）：
  - `session_started`
  - `assistant_delta` 或 `assistant_message`（首轮回复）
  - `session_idle`（首轮完成）
  - `user_message`（text === "再说一句"）
  - `assistant_delta` 或 `assistant_message`（第二轮回复）
  - `session_idle`（第二轮完成）
  - `session_result`（complete 后）
- 每个 event 有 id、session_id、seq、type、payload、created_at
- seq 从 1 开始单调递增，无断裂

### E2E-14: Resume 已完成的 Session

**前置**: E2E-12 完成的 session

**步骤**:
1. `POST /v1/sessions/{id}/resume`
2. 轮询等待 status 变为 "idle"（超时 60s，book 会启动新进程）

**断言**:
- POST /resume 返回 200
- 最终 status === "idle"（resume 后 book 进入 idle，等待输入）

**注意**: resume 后是新进程，之前的 provider_session_id 可能变化

**清理**: `POST /v1/sessions/{id}/complete` 完成 session

---

## 4. Session 生命周期 - 取消与删除

### E2E-15: 运行中 Session 取消

**步骤**:
1. 创建新 book session，prompt 使用一个会产生较长回复的内容（如 "写一段500字的故事"）
2. 轮询直到 status === "running"
3. 立即 `POST /v1/sessions/{id}/cancel`
4. 轮询等待 status 为终态（"cancelled" 或 "completed" 或 "failed"）

**断言**:
- POST /cancel 返回 200
- 最终 status 为 "cancelled"（或 provider 先完成则为 "completed"）
- 如果 cancelled，后续 resume 应返回 409 state_conflict

### E2E-16: 空闲 Session 取消

**步骤**:
1. 创建新 book session，prompt: "hi"
2. 等待 idle
3. `POST /v1/sessions/{id}/cancel`

**断言**:
- 返回 200
- session.status === "cancelled"

### E2E-17: 删除终态 Session

**前置**: 有一个 completed 或 cancelled 的 session

**步骤**:
1. `DELETE /v1/sessions/{id}`
2. `GET /v1/sessions/{id}`

**断言**:
- DELETE 返回 204
- GET 返回 404

### E2E-18: 拒绝删除活跃 Session

**步骤**:
1. 创建新 book session，等待 idle
2. `DELETE /v1/sessions/{id}`

**断言**:
- DELETE 返回 409 state_conflict

**清理**: complete 后 delete

---

## 5. Session API - 列表与过滤

### E2E-19: Session 列表 - 基本查询

**前置**: 至少已有 2 个 session（不同 status）

**步骤**:
1. `GET /v1/sessions`
2. `GET /v1/sessions?provider=book`
3. `GET /v1/sessions?status=completed`
4. `GET /v1/sessions?limit=1&offset=0`
5. `GET /v1/sessions?limit=1&offset=1`

**断言**:
- 各次返回 200
- 默认查询返回 sessions 数组、total、limit、offset
- provider 过滤后所有 session.provider === "book"
- status 过滤后所有 session.status === "completed"
- 分页 limit=1 只返回 1 条
- offset=1 返回不同的 session

### E2E-20: Session 列表 - 无效过滤

**步骤**:
1. `GET /v1/sessions?provider=invalid_provider`
2. `GET /v1/sessions?status=invalid_status`

**断言**:
- 两次均返回 400 invalid_request

---

## 6. Session 状态机边界

### E2E-21: 对 completed session 发消息

**步骤**:
1. 创建 session → idle → complete → completed
2. `POST /v1/sessions/{id}/message` body: `{ "text": "hello" }`

**断言**:
- 返回 409 state_conflict

### E2E-22: 对 running session 执行 complete

**步骤**:
1. 创建 session，prompt 使用会产生较长回复的内容
2. 等待 status === "running"
3. `POST /v1/sessions/{id}/complete`

**断言**:
- 返回 409 state_conflict（complete 只接受 idle）

### E2E-23: 对 cancelled session 执行 resume

**步骤**:
1. 用 E2E-16 产生的 cancelled session
2. `POST /v1/sessions/{id}/resume`

**断言**:
- 返回 409（cancelled 是终态，不可 resume）

### E2E-24: Session 不存在

**步骤**:
1. `GET /v1/sessions/nonexistent-id`
2. `POST /v1/sessions/nonexistent-id/message` body: `{ "text": "x" }`
3. `POST /v1/sessions/nonexistent-id/complete`
4. `POST /v1/sessions/nonexistent-id/cancel`
5. `POST /v1/sessions/nonexistent-id/resume`
6. `DELETE /v1/sessions/nonexistent-id`

**断言**:
- 全部返回 404

### E2E-25: Host Offline 时创建 Session

**步骤**:
1. `POST /v1/sessions` body: `{ "provider": "book", "host_id": "nonexistent-host", "cwd": "/tmp", "prompt": "test" }`

**断言**:
- 返回 404（host 不存在）或 502 host_offline（host 存在但离线）

---

## 7. 事件 Catch-up API

### E2E-26: 事件分页和 has_more

**前置**: 一个有多个事件的 completed session

**步骤**:
1. `GET /v1/sessions/{id}/events?since_seq=0&limit=2`
2. 记录返回的最后一个 seq
3. `GET /v1/sessions/{id}/events?since_seq={last_seq}&limit=2`
4. 重复直到 has_more === false

**断言**:
- 每次返回的 events 数量 ≤ limit
- seq 严格递增
- 最终拼接后的 events 与 `since_seq=0&limit=500` 一次性获取的结果一致
- 不存在 seq 断裂

### E2E-27: 事件 since_seq 边界

**步骤**:
1. `GET /v1/sessions/{id}/events?since_seq=999999`（超出实际范围）
2. `GET /v1/sessions/{id}/events?since_seq=0`

**断言**:
- since_seq=999999 返回空 events 数组，has_more === false
- since_seq=0 返回完整事件

### E2E-28: 不存在 session 的事件

**步骤**:
1. `GET /v1/sessions/nonexistent-id/events?since_seq=0`

**断言**:
- 返回 404

---

## 8. WebSocket 实时流

### E2E-29: WS 连接、认证、事件订阅

**步骤**:
1. 建立 WS 连接到 `wss://relay/v1/ws?token={TOKEN}`
2. 发送 `{ "action": "auth", "token": "{TOKEN}" }`
3. 创建新 book session
4. 发送 `{ "action": "subscribe", "session_id": "{session_id}" }`
5. 等待事件流

**断言**:
- WS 连接成功
- 收到 type === "event" 的消息，包含 session_id、seq、event_type、payload、timestamp
- 收到 type === "status" 的消息，包含 session_id 和 status
- 事件顺序与 REST API 查询一致
- 收到 session_idle 事件（表明进入 idle 状态）

**清理**: 关闭 WS，complete session

### E2E-30: WS 多 Session 订阅

**步骤**:
1. 建立 WS 连接并认证
2. 创建两个 book session（session_a 和 session_b）
3. subscribe 两个 session
4. 收集事件

**断言**:
- 收到来自两个 session_id 的事件
- 每个事件的 session_id 匹配对应的 session

**清理**: complete 两个 session

### E2E-31: WS Ping/Pong

**步骤**:
1. 建立 WS 连接并认证
2. 发送 `{ "action": "ping" }`

**断言**:
- 收到 `{ "type": "pong" }`

### E2E-32: WS 无效 Token

**步骤**:
1. 建立 WS 连接到 `wss://relay/v1/ws?token=invalid`
2. 发送 `{ "action": "auth", "token": "invalid" }`

**断言**:
- 收到 `{ "type": "error", "code": "unauthenticated" }`
- 连接被服务端关闭

---

## 9. 多轮持久会话 - 完整流程验证

### E2E-33: 三轮对话 + WS 实时验证

**此为核心 E2E 场景，验证 persistent-interactive-sessions 功能的完整性。**

**步骤**:
1. 建立 WS 连接并认证
2. `POST /v1/sessions` 创建 book session，prompt: "你好"
3. subscribe 该 session
4. 等待 WS 收到 `session_started` 事件
5. 等待 WS 收到 `assistant_delta` 或 `assistant_message` 事件（首轮回复）
6. 等待 WS 收到 `session_idle` 事件
7. 通过 REST 确认 session status === "idle"
8. `POST /v1/sessions/{id}/message` body: `{ "text": "第二轮" }`
9. 等待 WS 收到 `user_message` 事件（text === "第二轮"）
10. 等待 WS 收到 `assistant_delta` 或 `assistant_message`（第二轮回复）
11. 等待 WS 收到 `session_idle` 事件
12. 通过 REST 确认 status === "idle"
13. `POST /v1/sessions/{id}/message` body: `{ "text": "第三轮" }`
14. 等待 WS 收到第三轮事件和 session_idle
15. `POST /v1/sessions/{id}/complete`
16. 等待 WS 收到 `session_result` 事件
17. 等待 WS 收到 type === "status"，status === "completed"

**断言**:
- 所有 WS 事件的 session_id 一致
- seq 单调递增，无断裂
- 三轮各产生 assistant 回复内容
- user_message 事件的 payload.text 匹配发送的文本
- 每轮结束后 session_idle 先于下一轮开始
- complete 后收到 session_result
- REST API 的 events 结果与 WS 收到的事件一致（比对 seq 和 type）

**清理**: 关闭 WS

### E2E-34: 空闲超时验证

**注意**: 默认空闲超时为 30 分钟，E2E 测试中跳过此场景或使用环境变量缩短超时。本条仅记录预期行为：

**预期行为**:
- idle 状态 session 超过 `IDLE_TIMEOUT_MS` 未收到消息
- companion 向 CLI 进程发送 SIGTERM
- session 转为 completed
- 发出 session_result 事件

---

## 10. Push 注册

### E2E-35: 注册 FCM Token

**步骤**:
1. `POST /v1/push/register` body: `{ "fcm_token": "e2e-test-token-12345" }`
2. 再次 `POST /v1/push/register` body: `{ "fcm_token": "e2e-test-token-12345" }`

**断言**:
- 两次均返回 200，body.status === "ok"
- 幂等：相同 token 不会报错

### E2E-36: 无效 FCM Token

**步骤**:
1. `POST /v1/push/register` body: `{ "fcm_token": "" }`
2. `POST /v1/push/register` body: `{}`

**断言**:
- 返回 400

---

## 11. Viewer CLI

### E2E-37: Viewer 连接并格式化事件

**步骤**:
1. 创建 book session，等待 idle
2. 启动 `imbot-viewer --relay {WS_URL} --token {TOKEN} --session {session_id}`（子进程，捕获 stdout）
3. 发送消息触发新事件
4. 等待 viewer stdout 输出
5. 发送 SIGINT 关闭 viewer

**断言**:
- stdout 包含 ANSI 色彩格式化的事件输出
- 包含 `[tool]` 或 assistant 文本内容
- 包含 `--- session idle ---`（蓝色）
- 不包含 session_id 前缀（因为指定了 --session 过滤）

**清理**: complete session

---

## 12. Android E2E（adb 操作）

> 以下测试使用 adb 命令操作模拟器。输入中文使用 `adb shell am broadcast` + `ADB_INPUT_TEXT` 或 clipboard 方式。

### 公共 ADB Helper

```bash
ADB=$HOME/Library/Android/sdk/platform-tools/adb
screenshot() { $ADB exec-out screencap -p > "/tmp/imbot-e2e-$1.png"; }
find_element() {
  $ADB exec-out uiautomator dump /dev/tty 2>/dev/null | \
    grep -oE "text=\"$1\"[^]]*bounds=\"\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]\"" | \
    python3 -c "import re,sys; m=re.search(r'bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', sys.stdin.read()); print(f'{(int(m[1])+int(m[3]))//2},{(int(m[2])+int(m[4]))//2}') if m else None"
}
tap() { $ADB shell input tap $1 $2; }
tap_text() { local c=$(find_element "$1"); [[ -n "$c" ]] && tap ${c//,/ }; }
type_text() { $ADB shell input text "$1"; }
hide_kb() { $ADB shell input keyevent 111; }
back() { $ADB shell input keyevent 4; }
start_app() { $ADB shell am start -n com.imbot.android/.MainActivity; }
clear_app() { $ADB shell pm clear com.imbot.android; }
```

### E2E-A01: Onboarding 配置

**前置**: `clear_app` 清除数据

**步骤**:
1. `start_app`，等待 3s，截图确认 Onboarding
2. dump UI → 找到 "Relay URL" 输入框 → 点击 → 输入 `https://imbot.23-95-164-218.sslip.io` → hide_kb
3. dump UI → 找到 "Token" 输入框 → 点击 → 输入 token → hide_kb
4. dump UI → 找到 "测试连接" → 点击 → 等待 5s → 截图
5. dump UI → 确认 "连接成功" 文本存在
6. dump UI → 找到 "开始使用" → 点击 → 等待 3s → 截图

**验收**: 截图显示主页（会话列表页，有 "IMbot" 标题）

### E2E-A02: 创建 Book Session

**前置**: E2E-A01 完成

**步骤**:
1. dump UI → 找到 FAB 或 "+" 按钮 → 点击
2. 等待 3s → 截图确认 NewSession 界面
3. dump UI → 找到 "book" 或 "Book" provider → 点击
4. 截图确认目录选择步骤 → 选择 workspace root
5. 截图确认 Prompt 输入步骤
6. 输入 "简单测试"
7. 点击发送/创建按钮
8. 等待 5s → 截图确认进入 session detail

**验收**:
- 截图显示 session detail 界面
- 通过 REST `GET /v1/sessions?status=running` 或 `?status=idle` 确认 session 存在

### E2E-A03: Session Detail - Idle 状态下发消息

**前置**: E2E-A02 的 session 进入 idle（等待 REST 确认 status=idle）

**步骤**:
1. 截图确认 detail 界面有 assistant 回复内容
2. 截图确认底部输入框 placeholder 为 "继续对话..."
3. 确认输入框可编辑（idle 状态 enabled）
4. 点击输入框 → 输入 "追问" → 点击发送
5. 等待 REST 确认 status 再次变为 idle
6. 截图确认有新的 assistant 回复

**验收**: 多轮对话在 Android 界面正确展示

### E2E-A04: Session Detail - Running 状态输入框禁用

**前置**: E2E-A03 中刚发送消息后（status=running 的短暂窗口）

**说明**: 这个很难在真机上精确捕捉 running 窗口。改为通过单元测试验证 `canInputToSession("running") === false`。此处仅验证 idle 状态下 placeholder 文字。

**步骤**:
1. 等待 session idle
2. dump UI → 确认存在 "继续对话..." 文本

**验收**: placeholder 文字正确

### E2E-A05: 结束会话（Complete）

**前置**: session 处于 idle

**步骤**:
1. dump UI → 找到 overflow menu（⋮ 或 MoreVert）→ 点击
2. dump UI → 找到 "结束会话" → 点击
3. dump UI → 确认弹出确认对话框，找到 "确定" → 点击
4. 等待 5s
5. REST 确认 session status === "completed"
6. 截图确认 detail 界面显示 "已完成" 或类似终态标记

**验收**: session 通过 Android 界面成功 complete

### E2E-A06: Session 列表显示

**前置**: 至少 1 个 session 已完成

**步骤**:
1. `back()` 返回主页
2. 截图确认 session 列表非空
3. 确认 session card 显示 provider 标签（"BK" 或 "book"）、prompt 摘要、状态

**验收**: 主页正确显示 session card

### E2E-A07: Resume 已完成 Session

**前置**: E2E-A05 完成的 session，在 detail 页面

**步骤**:
1. 确认 detail 界面底部输入框 placeholder 为 "会话已结束"
2. dump UI → 确认存在 "恢复会话" 或输入框（completed 状态 Android 是否允许 resume？取决于 `canSendToSession`）

**说明**: `canSendToSession` 只接受 running 和 idle。completed 状态不能直接发消息。Resume 需要通过 REST API `POST /resume`。Android 端如果有 resume 按钮则点击，否则通过 REST 验证：
1. `POST /v1/sessions/{id}/resume`
2. 等待 idle
3. 回到 Android 确认界面更新为 idle 状态

**验收**: resume 后 session 回到 idle

### E2E-A08: Workspace 界面

**步骤**:
1. 从主页底部导航切换到 Workspace tab
2. 截图确认显示 host 信息（"MacBook" + "online"）
3. 确认显示 workspace root 列表
4. 点击一个 root → 截图确认子目录列表
5. 点击返回

**验收**: Workspace 界面正常导航

### E2E-A09: Settings 界面

**步骤**:
1. 从底部导航切换到 Settings tab
2. 截图确认显示 Relay URL、Token（masked）、主题选项

**验收**: Settings 界面正常显示

### E2E-A10: 断线重连 Banner

**步骤**:
1. `ssh root@23.95.164.218 "pm2 stop imbot-relay"`
2. 等待 30-60s
3. 截图确认 Android 显示红色 "无法连接服务器" banner
4. `ssh root@23.95.164.218 "pm2 start imbot-relay"`
5. 等待重连
6. 截图确认 banner 消失

**验收**: 错误 banner 正确显示和恢复

---

## 13. 完整流程 Smoke Test

### E2E-SMOKE: Node 侧完整链路

**此测试作为单一脚本，串行执行核心链路验证。适合 CI 或快速回归。**

**步骤**:
1. 确认 healthz 返回 companion=online
2. 列出 hosts，确认 macbook-1 online
3. 列出 macbook-1 roots，获取一个 book root 的 cwd
4. 创建 book session（prompt: "hi"）
5. 等待 idle
6. 发送消息 "second turn"
7. 等待 idle
8. 获取 events since_seq=0，验证 seq 连续、包含 session_started + session_idle × 2 + user_message
9. complete session
10. 等待 completed
11. resume session
12. 等待 idle
13. complete session
14. 等待 completed
15. delete session
16. 确认 404

**超时**: 总计 5 分钟

**输出**: 通过/失败 + 每步耗时

---

## 测试执行顺序

### Node E2E（自动化）

文件: `tests/e2e/e2e-infra.test.mjs` — E2E-01, E2E-02
文件: `tests/e2e/e2e-hosts.test.mjs` — E2E-03 ~ E2E-09
文件: `tests/e2e/e2e-session-lifecycle.test.mjs` — E2E-10 ~ E2E-18
文件: `tests/e2e/e2e-session-api.test.mjs` — E2E-19 ~ E2E-20
文件: `tests/e2e/e2e-session-edges.test.mjs` — E2E-21 ~ E2E-25
文件: `tests/e2e/e2e-events.test.mjs` — E2E-26 ~ E2E-28
文件: `tests/e2e/e2e-websocket.test.mjs` — E2E-29 ~ E2E-32
文件: `tests/e2e/e2e-multiturn.test.mjs` — E2E-33, E2E-34
文件: `tests/e2e/e2e-push.test.mjs` — E2E-35, E2E-36
文件: `tests/e2e/e2e-viewer.test.mjs` — E2E-37
文件: `tests/e2e/e2e-smoke.test.mjs` — E2E-SMOKE

执行: `npm run build && node --test tests/e2e/*.test.mjs`

在 `package.json` 中添加脚本:
```json
"test:e2e": "npm run build && node --test tests/e2e/*.test.mjs"
```

### Android E2E（半自动/手动）

按顺序: A01 → A02 → A03 → A04 → A05 → A06 → A07 → A08 → A09

独立: A10（需 ssh 权限）

---

## 注意事项

1. **book CLI 依赖**: book 必须在 companion 机器上可用且已配置 root。测试前 `GET /v1/hosts/macbook-1/roots` 确认有 book root。
2. **book 响应时间**: book CLI 响应通常 5-30s，比 claude 快但不确定。所有等待使用 60s 超时。
3. **并发安全**: E2E 测试会创建真实 session。测试结束后必须清理（complete + delete）。
4. **WS 重连**: WebSocket 测试中连接可能因 60s 空闲被关闭。测试中保持 ping 活性。
5. **中文输入 (adb)**: `adb shell input text` 不支持中文。使用 clipboard 方式: `adb shell am broadcast -a clipper.set -e text "中文内容"` + paste，或使用 base64 编码 intent。
6. **测试隔离**: 每个测试文件独立。跨文件的前置依赖通过在每个文件内自行创建/清理 session 实现，不依赖其他文件的副作用。
7. **CI 环境**: Node E2E 测试需要真实 relay 和 companion 在线。不适合无状态 CI runner。应在本机或专用环境运行。
