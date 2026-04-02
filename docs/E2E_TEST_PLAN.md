# IMbot 端到端测试方案 v3

> **Provider**: 全部使用 `book`（Claude Code 同源二进制，支持 stream-json 协议、tool call、skill 调用，行为一致）。
>
> **实现目标**: 本文档将交给 Codex 实现为自动化测试脚本。每个 test case 包含精确步骤和断言。Codex 应将每个 section 实现为 `tests/e2e/*.test.mjs` 文件，使用 Node 内置 test runner。
>
> **v3 增量说明**: 在 v2 基础上新增 Section 14-19，覆盖 PR #96-99 引入的全部新功能（消息复制、Slash Command、AskUserQuestion、Approval、Markdown 渲染增强、Apple 美学视觉重设计）。v2 的 Section 1-13 保持不变。
>
> **v4 增量说明**: 新增 Section 21，覆盖 PR #111 (Issue #106) 引入的 UI 美化第二轮功能（去气泡、深色反转、间距系统、代码块折叠/终端样式、状态合并、输入栏 Pill 化、FAB 缩小）。

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

// 等待 WS 事件流中出现特定 event_type
async function waitForWsEventType(events, eventType, timeoutMs = 30000) {
  return waitForWsEvent(events, e => e.event_type === eventType, timeoutMs);
}

// 创建 book session 并等待 idle（常用前置操作）
async function createBookSessionAndWaitIdle(prompt = "hi") {
  const rootsRes = await apiGet("/hosts/macbook-1/roots");
  const roots = await rootsRes.json();
  const bookRoot = roots.find(r => r.provider === "book");
  if (!bookRoot) throw new Error("No book root found");

  const createRes = await apiPost("/sessions", {
    provider: "book",
    host_id: "macbook-1",
    cwd: bookRoot.path,
    prompt
  });
  const session = await createRes.json();
  await waitForStatus(session.id, ["idle"], 120000);
  return session;
}

// 清理 session: complete → delete
async function cleanupSession(sessionId) {
  try {
    const session = await (await apiGet(`/sessions/${sessionId}`)).json();
    if (["running", "idle"].includes(session.status)) {
      await apiPost(`/sessions/${sessionId}/complete`);
      await waitForStatus(sessionId, ["completed"], 30000);
    }
    await apiDelete(`/sessions/${sessionId}`);
  } catch {}
}
```

### ADB Helper（Android 测试用）

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
long_press() { $ADB shell input swipe $1 $2 $1 $2 800; }
```

---

## 1-13: v2 测试用例（保持不变）

> Section 1-13 完整内容见 v2 版本（37 个 Node E2E + 10 个 Android E2E + 1 个 Smoke）。
> 以下 Section 14-19 为 v3 新增，覆盖 PR #96-#99 引入的新功能。

---

## 14. 消息复制与文本选择（PR #96, Issue #94）

> 文件: `tests/e2e/e2e-message-copy.test.mjs`
>
> 测试 Android 端的消息长按操作菜单、单消息复制、文本选择模式。所有测试使用 book session。

### E2E-A11: Agent 消息长按复制

**前置**: 已创建 book session 并进入 idle（有至少一条 agent 回复）

**步骤**:
1. 截图确认 session detail 界面有 agent 回复气泡
2. dump UI → 找到第一条 agent 消息气泡的中心坐标
3. 在该坐标执行长按（`long_press x y`，800ms 持续）
4. 等待 1s → dump UI → 截图
5. 确认 ModalBottomSheet 出现，包含 "复制消息" 和 "选择文本" 两个选项
6. 点击 "复制消息"
7. 等待 1s → 截图

**断言**:
- 长按后弹出 ModalBottomSheet（UI 树中包含 "复制消息" 文本节点）
- 点击后 Sheet 消失
- 出现 Snackbar "已复制到剪贴板"（或 UI 树中包含该文本）
- 通过 `adb shell am broadcast -a clipper.get` 或等价方式验证剪贴板内容非空

**清理**: 无（session 保留供后续测试）

### E2E-A12: 用户消息长按复制

**前置**: E2E-A11 同一 session

**步骤**:
1. dump UI → 找到用户消息气泡（右对齐的消息）的中心坐标
2. 长按 800ms
3. 等待 1s → dump UI
4. 确认 "复制消息" 和 "选择文本" 选项出现
5. 点击 "复制消息"
6. 截图确认 Snackbar 出现

**断言**:
- 用户消息同样支持长按复制
- Snackbar 显示

### E2E-A13: 状态气泡长按无响应

**前置**: E2E-A11 同一 session（状态气泡如 "运行中" / "空闲" 存在）

**步骤**:
1. dump UI → 找到状态气泡（居中的小文字，如 "空闲"）的中心坐标
2. 长按 800ms
3. 等待 1s → dump UI

**断言**:
- 不弹出 ModalBottomSheet
- UI 树中不包含 "复制消息" 文本

### E2E-A14: 文本选择模式进入与退出

**前置**: E2E-A11 同一 session

**步骤**:
1. 长按 agent 消息 → 弹出 Sheet
2. 点击 "选择文本"
3. 等待 1s → 截图
4. 确认消息背景高亮（视觉验证通过截图）
5. 按返回键（`back`）
6. 等待 1s → 截图
7. 确认背景恢复正常

**断言**:
- 点击 "选择文本" 后 Sheet 消失
- 消息进入选择模式（截图可见背景色变化）
- 按 Back 退出选择模式而不是返回上一页（仍在 detail 页面）

### E2E-A15: 流式消息不可操作

**前置**: E2E-A11 同一 session，处于 idle

**步骤**:
1. 在输入栏输入 "用一段话解释什么是递归" → 发送
2. 立即（发送后 1s 内）dump UI → 找到正在流式输出的 agent 消息
3. 在流式消息上长按 800ms
4. dump UI

**断言**:
- 不弹出 ModalBottomSheet（流式中 isStreaming=true，不允许操作）

**说明**: 此测试的时间窗口很短（book 通常 5-30s 回复）。如果无法可靠捕捉 running 状态，可标记为 `skip` 并在单元测试中覆盖（`availableActions` 对 streaming=true 返回空列表）。

**清理**: 等待 session 回到 idle

---

## 15. Slash Command（PR #97, Issue #95）

> 文件: `tests/e2e/e2e-slash-command.test.mjs`
>
> 测试 Android 端的 `/` 命令触发、命令列表、CommandChip、命令组装发送。

### E2E-A16: Slash 输入触发命令列表

**前置**: 已创建 book session 并进入 idle

**步骤**:
1. 截图确认 detail 界面
2. 点击输入栏 → 输入 `/`（使用 `type_text "/"` — 注意 adb input text 对 `/` 需要转义为 `%s`，实测可能需要 `adb shell input text '/'`）
3. 等待 1s → dump UI → 截图
4. 确认 ModalBottomSheet 出现，包含命令列表

**断言**:
- UI 树中出现 SlashCommandSheet（包含 "commit" 或 "help" 等命令文本）
- 列表中至少有 5 个可选命令
- 列表有搜索框（placeholder 文本）

### E2E-A17: 命令搜索过滤

**前置**: E2E-A16 Sheet 已打开

**步骤**:
1. 在搜索框中输入 "com"
2. 等待 1s → dump UI
3. 确认过滤后列表包含 "commit" 和/或 "compact"
4. 确认列表不包含 "help"、"test" 等不匹配项

**断言**:
- 搜索 "com" 后列表仅显示匹配项
- 大小写不敏感（"com" 匹配 "Commit"）

### E2E-A18: 选择命令 → CommandChip 显示

**前置**: E2E-A17 已过滤列表

**步骤**:
1. 清空搜索框
2. dump UI → 点击 "help" 命令行
3. 等待 1s → dump UI → 截图
4. 确认 Sheet 关闭
5. 确认输入栏上方出现 CommandChip（包含 "/ help" 或 "help" 文本）
6. 确认输入框 placeholder 变为命令描述（如 "获取帮助"）

**断言**:
- Sheet 关闭
- CommandChip 显示在输入栏上方
- 输入框 placeholder 反映所选命令

### E2E-A19: CommandChip 关闭

**前置**: E2E-A18 CommandChip 已显示

**步骤**:
1. dump UI → 找到 CommandChip 上的关闭按钮（✕ 图标）
2. 点击关闭
3. dump UI

**断言**:
- CommandChip 消失
- 输入框 placeholder 恢复为 "继续对话..."

### E2E-A20: 带命令发送消息

**前置**: 同一 book session，idle 状态

**步骤**:
1. 输入 `/` → 选择 "help" → CommandChip 显示
2. 在输入框输入 "how to commit"
3. 点击发送按钮
4. 等待 3s
5. dump UI → 确认用户消息气泡显示

**断言**:
- 发送后 CommandChip 消失
- 通过 REST API 获取 events：最新 user_message 的 text 为 "/help how to commit"（完整 slash command 格式）
- book agent 收到并处理了该消息（有后续 assistant_delta 事件）

**验证步骤**:
```javascript
const eventsRes = await apiGet(`/sessions/${sessionId}/events?since_seq=0`);
const events = await eventsRes.json();
const userMsg = events.events.findLast(e => e.event_type === "user_message");
assert.ok(userMsg.payload.text.startsWith("/help"));
```

**清理**: 等待 session idle

---

## 16. AskUserQuestion 交互卡片（PR #97, Issue #95）

> 文件: `tests/e2e/e2e-interactive-tools.test.mjs`
>
> 测试 Android 端的 AskUserQuestion 工具调用渲染为交互式卡片。由于触发 AskUserQuestion 需要 agent 主动调用该工具，使用 book session 并发送特定提示词来引导 agent 调用。

### E2E-A21: AskUserQuestion 卡片渲染

**前置**: 创建 book session

**步骤**:
1. 发送 prompt 引导 book 调用 AskUserQuestion 工具。示例 prompt：
   ```
   I need you to call the AskUserQuestion tool with question "选择你喜欢的颜色" and options ["红色","蓝色","绿色"]. Just call the tool, nothing else.
   ```
2. 等待 WS 事件流出现 `tool_call_started` 且 `tool_name === "AskUserQuestion"`（超时 60s）
3. 截图确认 Android 界面显示 InteractiveToolCard

**断言**:
- WS 事件中出现 AskUserQuestion tool_call_started
- Android UI 中出现交互卡片（包含问题文本）
- 卡片包含选项按钮（如果 agent 传了 options）或自由输入框
- 卡片包含 "提交回答" 按钮

**说明**: AskUserQuestion 是否被调用取决于 agent 行为。如果 book CLI 不支持该工具调用或拒绝执行，此测试应 skip 并注明原因。可改用 WS mock 事件注入方式（通过直接 POST 事件到 relay 的内部 API）。

### E2E-A22: 提交回答 → 卡片变只读

**前置**: E2E-A21 卡片已显示且待回答

**步骤**:
1. dump UI → 找到自由输入框 → 输入 "蓝色"
2. 点击 "提交回答" 按钮
3. 等待 3s → dump UI → 截图

**断言**:
- 提交后卡片变为只读状态（背景变灰）
- 卡片标题变为 "已提交回答" 或类似
- 输入框和按钮不可编辑
- 显示提交的答案文本 "蓝色"
- REST API 验证：events 中出现相应的 tool_call_completed

### E2E-A23: 过期卡片只读

**说明**: 当多个 AskUserQuestion 连续到达时，仅最新一个可操作，旧的显示 "已过期"。此场景在正常 book 会话中难以自然触发（agent 通常串行调用 tool）。

**替代验证**: 通过单元测试验证 `isLatestPending` 逻辑。此 E2E 测试 skip，标注：
```javascript
test.skip("older AskUserQuestion cards render as expired", () => {
  // Covered by DetailViewModelTest: submitToolAnswer ignores non latest pending
  // Hard to trigger naturally with book CLI (sequential tool calls)
});
```

---

## 17. Approval 审批卡片（PR #97, Issue #95）

> 文件: `tests/e2e/e2e-approval.test.mjs`
>
> 测试 approval_required / approval_resolved 事件的交互卡片。需要 companion 在 non-bypass 模式下运行。

### E2E-A24: Approval 卡片渲染

**前置**: companion 配置为 non-bypass permission 模式（默认是 bypassPermissions，需要临时切换）

**说明**: 当前 IMbot 默认 `bypassPermissions` 模式，approval 事件不会自然触发。此测试有两种实现路径：

**路径 A**（推荐）: 直接通过 WS 模拟注入 approval_required 事件
```javascript
// 创建 session 后，通过 WS 发送模拟事件
ws.send(JSON.stringify({
  action: "inject_event",  // 如果 relay 支持
  session_id: sessionId,
  event: {
    event_type: "approval_required",
    payload: {
      call_id: "test-appr-1",
      tool_name: "bash",
      description: "rm -rf /tmp/test"
    }
  }
}));
```

**路径 B**: 临时修改 companion 配置，但这会影响其他 session，不推荐在 E2E 中使用。

**如果 relay 不支持事件注入**: 此测试应 skip 并标注：
```javascript
test.skip("approval card rendering requires non-bypass mode or event injection", () => {
  // Default bypassPermissions mode does not trigger approval events
  // Covered by unit tests: EventProcessorTest + DetailViewModelTest
});
```

**步骤**（如果可以注入事件）:
1. 创建 book session
2. 通过 WS 注入 approval_required 事件
3. 截图确认 Android 显示审批卡片
4. 确认卡片包含 "需要审批" 标题、tool name、description
5. 确认有 "批准" 和 "拒绝" 两个按钮

**断言**:
- 审批卡片正确渲染
- approve/deny 按钮可见且可点击

### E2E-A25: Approval 批准 → 卡片更新

**前置**: E2E-A24 审批卡片已显示

**步骤**:
1. 点击 "批准" 按钮
2. 等待 3s → dump UI → 截图
3. REST 验证 events

**断言**:
- 按钮变为不可用
- 卡片显示 "已批准" 或类似
- `POST /sessions/:id/input` 被调用（body 包含 "approve"）

---

## 18. Markdown 渲染增强（PR #99, Issue #93）

> 文件: `tests/e2e/e2e-markdown-rendering.test.mjs`
>
> 测试 Android 端的 Markdown 渲染质量。使用 book session 发送包含各种 Markdown 元素的消息，通过 REST 验证 events + 截图验证渲染。

### E2E-A26: 综合 Markdown 渲染

**前置**: 创建 book session，等待 idle

**步骤**:
1. 发送以下消息给 book：
   ```
   请你原样输出以下 Markdown 内容（不要解释，不要修改，直接输出）：

   # 大标题

   ## 二级标题

   正常段落，包含 **粗体** 和 *斜体* 和 ~~删除线~~ 和 `inline code` 和 [链接](https://example.com)。

   > 这是引用
   > > 这是嵌套引用

   - 第一级列表
     - 第二级列表
       - 第三级列表
   1. 有序列表 1
   2. 有序列表 2

   ```kotlin
   fun greet() {
       println("hello")
   }
   ```

   | 列 A | 列 B | 列 C |
   |------|------|------|
   | 1    | 2    | 3    |
   | 4    | 5    | 6    |
   ```
2. 等待 session idle（超时 120s，book 处理长内容较慢）
3. 截图

**断言**:
- REST events 中 assistant_message 包含完整 Markdown 源码
- 截图中可见：
  - 大标题和二级标题有明显的字号/字重差异
  - 粗体/斜体/删除线/inline code 各有对应样式
  - 引用块有左边框竖线
  - 嵌套引用有更深的缩进
  - 列表有不同层级的 bullet 符号
  - 代码块有语言标签 "kotlin" 和行号
  - 表格有表头高亮和斑马条纹

### E2E-A27: 代码块语言标签和行号

**前置**: E2E-A26 同一 session

**步骤**:
1. 发送消息要求 book 输出一段 50 行以上的代码：
   ```
   输出一段 Python 代码，至少 50 行，包含一个完整的类定义。用 ```python 代码块包裹。
   ```
2. 等待 idle → 截图

**断言**:
- 代码块左上角显示 "python" 语言标签
- 代码块左侧显示行号（1, 2, 3...）
- 行号右对齐，与代码之间有竖线分隔
- 代码块右上角有复制按钮

### E2E-A28: 代码块复制按钮反馈

**前置**: E2E-A27 代码块可见

**步骤**:
1. dump UI → 找到代码块右上角的复制按钮图标
2. 点击复制按钮
3. 立即截图（1s 内）
4. 等待 3s → 再次截图

**断言**:
- 第一张截图：复制按钮图标变为 ✓（Check 图标）
- 第二张截图：图标恢复为复制图标（2s 后自动恢复）
- 剪贴板包含代码内容

### E2E-A29: 宽表格水平滚动

**前置**: 同一 book session

**步骤**:
1. 发送消息要求 book 输出一个 8 列的宽表格：
   ```
   输出一个 Markdown 表格，8 列（A/B/C/D/E/F/G/H），3 行数据，每个单元格填数字。
   ```
2. 等待 idle → 截图
3. 在表格区域水平滑动（`adb shell input swipe 800 Y 200 Y 300`，Y 为表格纵坐标）
4. 截图

**断言**:
- 表格渲染正确（列对齐、有边框）
- 水平滑动后可看到更多列（表格可横向滚动）
- 两张截图对比，第二张显示了不同的列

### E2E-A30: KaTeX 公式渲染

**前置**: 同一 book session

**步骤**:
1. 发送消息：
   ```
   输出一个行内公式 $E=mc^2$ 和一个块级公式：
   $$\int_0^1 x^2 dx = \frac{1}{3}$$
   ```
2. 等待 idle → 截图

**断言**:
- 行内公式 E=mc² 渲染为数学排版（非纯文本）
- 块级公式渲染为居中的积分表达式
- 不显示 LaTeX 源码（`\int`, `\frac` 等不可见）
- 不崩溃（WebView 正常加载 KaTeX）

### E2E-A31: 空消息和极端输入

**前置**: 同一 book session

**步骤**:
1. 发送仅包含空白符的消息（如果输入栏允许——实际上 `canSubmit` 要求 `isNotBlank`，此步应失败）
2. 验证输入栏的发送按钮是否 disabled

**断言**:
- 输入纯空白时发送按钮不可点击（灰色状态）
- 不产生空消息

**清理**: complete + delete session

---

## 19. 视觉重设计回归验证（PR #98, Issue #92）

> 文件: `tests/e2e/e2e-visual-redesign.test.mjs`
>
> 验证 Apple 美学重设计后的功能回归和视觉完整性。不测试具体像素值，仅确认功能不被视觉改动破坏。

### E2E-A32: Onboarding 视觉 + 功能完整

**前置**: `clear_app` 清除数据

**步骤**:
1. `start_app` → 等待 3s → 截图 "redesign-onboarding"
2. 确认 Onboarding 页面元素存在：logo/标题文字、Relay URL 输入框、Token 输入框、"测试连接" 按钮
3. 输入 Relay URL 和 Token
4. 点击 "测试连接" → 等待 5s → 截图 "redesign-onboarding-connected"
5. 确认 "连接成功" 文字可见
6. 确认 "开始使用" 按钮可见 → 点击
7. 等待 3s → 截图 "redesign-home-after-onboarding"

**断言**:
- Onboarding 所有元素可见且可交互
- 连接成功后显示 host 状态
- 点击 "开始使用" 后进入 Home 页面
- 截图中输入框为 filled 样式（无明显边框，有背景色）
- 截图中按钮为 pill 形状（大圆角）

### E2E-A33: Home 页面功能回归

**前置**: E2E-A32 已完成 Onboarding

**步骤**:
1. 截图 "redesign-home-empty"（如果无 session）
2. 点击 FAB (+) → 截图 "redesign-new-session"
3. 选择 book provider → 选择目录 → 输入 prompt "视觉测试" → 创建
4. 等待 session idle → 返回 Home
5. 截图 "redesign-home-with-session"
6. 确认 SessionCard 显示：provider badge、标题、workspace path、prompt 预览、状态、时间戳

**断言**:
- FAB 功能正常
- NewSession 三步流程完整
- SessionCard 渲染正确
- 底部导航三个 tab 可切换
- 截图中 SessionCard 有阴影和 16dp 圆角（视觉验证）

### E2E-A34: Session Detail 新输入栏

**前置**: E2E-A33 的 session 处于 idle

**步骤**:
1. 点击 SessionCard 进入 detail → 截图 "redesign-detail"
2. 确认输入栏样式：pill 形状、发送按钮为圆形蓝色
3. 输入 "测试新输入栏" → 点击发送
4. 等待 idle → 截图 "redesign-detail-after-send"
5. 确认消息渲染正常
6. 点击 ⋯ 菜单 → 截图 "redesign-detail-menu"
7. 确认菜单包含选项

**断言**:
- 输入栏可正常输入和发送
- 消息气泡渲染正确（非对称圆角）
- 状态气泡文字较小（11sp）
- 顶栏精简（provider 图标 + 标题 + 状态点 + 菜单）

### E2E-A35: 底部导航三 tab 切换

**前置**: E2E-A32 已完成 Onboarding

**步骤**:
1. 确认当前在 "会话" tab → 截图
2. 点击 "目录" tab → 等待 2s → 截图 "redesign-workspace"
3. 确认 Workspace 界面显示 host 列表
4. 点击 "设置" tab → 等待 2s → 截图 "redesign-settings"
5. 确认 Settings 界面显示配置项
6. 点击 "会话" tab → 确认返回 Home

**断言**:
- 三个 tab 切换功能正常
- 每个 tab 有正确的图标和标签
- 选中态为品牌蓝色
- Workspace 显示 host status（online/offline 小圆点）
- Settings 显示 Relay URL、Token（masked）

### E2E-A36: Dark Mode 切换验证

**前置**: E2E-A35 在 Settings 页面

**步骤**:
1. 找到主题/Dark Mode 切换控件 → 切换为深色模式
2. 等待 1s → 截图 "redesign-settings-dark"
3. 切换到 "会话" tab → 截图 "redesign-home-dark"
4. 进入一个 session detail → 截图 "redesign-detail-dark"
5. 切换回浅色模式
6. 截图 "redesign-home-light-restored"

**断言**:
- Dark 模式下背景为深色（接近黑色）
- Dark 模式下文字为浅色
- 所有页面在 Dark 模式下可正常使用
- 切换回 Light 模式后恢复

### E2E-A37: Provider Filter Chip

**前置**: 至少有一个 book session

**步骤**:
1. 在 Home 页面，找到 provider filter chip row
2. 截图确认 "全部" chip 默认选中
3. 点击 "book" chip → 等待 1s → 截图
4. 确认列表仅显示 book provider 的 session
5. 点击 "全部" → 确认恢复显示所有

**断言**:
- Filter chip 水平可滚动
- 选中态为品牌蓝 filled
- 过滤逻辑正确

**清理**: complete + delete 测试 session

---

## 20. 完整流程 Smoke Test v3

### E2E-SMOKE-V3: 全链路回归

**此测试作为单一脚本，串行执行核心链路 + 新功能验证。适合 CI 或快速回归。**

**步骤**:
1. 确认 healthz 返回 companion=online
2. 列出 hosts，确认 macbook-1 online + 有 book root
3. 创建 book session（prompt: "say hello"）
4. 等待 idle
5. 获取 events，验证包含 session_started + assistant_delta/assistant_message + session_idle
6. 发送 "what is 2+2?"
7. 等待 idle
8. 获取 events，验证第二轮 user_message + assistant 回复
9. 发送包含 Markdown 的消息："explain `git rebase` vs `git merge` with a code example"
10. 等待 idle
11. 获取 events，验证 assistant 回复中包含 code block（检查 ``` 标记）
12. 发送 slash command "/help" 格式的消息
13. 等待 idle
14. 验证 user_message text === "/help"
15. complete session → 等待 completed
16. resume session → 等待 idle
17. complete → 等待 completed
18. delete → 确认 404

**超时**: 总计 8 分钟

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
文件: `tests/e2e/e2e-smoke.test.mjs` — E2E-SMOKE, E2E-SMOKE-V3
文件: `tests/e2e/e2e-visual-polish-v2.test.mjs` — E2E-A38 ~ E2E-A46

执行: `npm run build && node --test tests/e2e/*.test.mjs`

### Android E2E（adb 半自动）

**v2 用例（保持不变）**:
A01 → A02 → A03 → A04 → A05 → A06 → A07 → A08 → A09 → A10

**v3 新增用例**:
A11 → A12 → A13 → A14 → A15（消息复制/选择）
A16 → A17 → A18 → A19 → A20（Slash Command）
A21 → A22 → A23（AskUserQuestion）
A24 → A25（Approval）
A26 → A27 → A28 → A29 → A30 → A31（Markdown 渲染）
A32 → A33 → A34 → A35 → A36 → A37（视觉回归）
A38 → A39 → A40 → A41（UI 美化 v2：气泡 + 间距 + 状态）
A42 → A43 → A44（代码块折叠/终端/小块）
A45 → A46（输入栏 + FAB）

---

## 注意事项

1. **book CLI 依赖**: book 必须在 companion 机器上可用且已配置 root。测试前 `GET /v1/hosts/macbook-1/roots` 确认有 book root。
2. **book 响应时间**: book CLI 响应通常 5-30s，比 claude 快但不确定。所有等待使用 120s 超时（v3 调高，因为 Markdown 综合测试的 prompt 较复杂）。
3. **并发安全**: E2E 测试会创建真实 session。测试结束后必须清理（complete + delete）。
4. **WS 重连**: WebSocket 测试中连接可能因 60s 空闲被关闭。测试中保持 ping 活性。
5. **中文输入 (adb)**: `adb shell input text` 不支持中文。使用 clipboard 方式: `adb shell am broadcast -a clipper.set -e text "中文内容"` + paste，或使用 base64 编码 intent。
6. **测试隔离**: 每个测试文件独立。跨文件的前置依赖通过在每个文件内自行创建/清理 session 实现，不依赖其他文件的副作用。
7. **AskUserQuestion / Approval**: 这些工具调用依赖 agent 行为，可能无法可靠触发。如果 book 不配合，标记 skip 并在单元测试中覆盖逻辑。
8. **截图命名**: 所有截图统一前缀 `imbot-e2e-`，按功能区分后缀，存入 `/tmp/` 目录。
9. **长按操作**: adb 长按使用 `input swipe x y x y 800`（同一坐标，持续 800ms）模拟。
10. **Slash 输入**: adb 中 `/` 字符可能需要转义。先测试 `adb shell input text '/'` 是否生效，不行改用 keyevent 或 clipboard。

---

## 21. UI 美化第二轮（PR #111, Issue #106）

> 文件: `tests/e2e/e2e-visual-polish-v2.test.mjs`
>
> 验证去气泡、深色反转、动态间距、代码块折叠/终端样式、状态合并、Pill 输入栏、FAB 缩小。

### E2E-A38: 助手消息去气泡化

**前置**: 已创建 book session 并进入 idle（有至少一条 agent 回复）

**步骤**:
1. 截图 "polish-v2-assistant-bubble"
2. dump UI → 定位 agent 消息文本区域
3. 验证 agent 消息左侧有 provider 头像 badge（36dp 圆形）

**断言**:
- 截图中 agent 消息无灰色气泡背景（内容直接渲染在页面底色上）
- provider 头像 badge 可见（圆形彩色背景 + 首字母缩写）
- 消息文本与头像之间有间距（~12dp）
- 时间戳在消息下方，小字号灰色

### E2E-A39: 用户消息深色反转

**前置**: E2E-A38 同一 session

**步骤**:
1. dump UI → 定位用户消息气泡
2. 截图 "polish-v2-user-bubble-light"
3. 切换深色模式 → 截图 "polish-v2-user-bubble-dark"
4. 切换回浅色模式

**断言**:
- 亮色模式：用户气泡为深色背景（Gray-800）+ 白色文字
- 暗色模式：用户气泡为浅灰背景（Gray-200）+ 深色文字
- 气泡右下角圆角较小（4dp，呈尾巴感），其余三角为 16dp
- 气泡右对齐
- 最大宽度约为屏幕宽度的 80%
- 时间戳在气泡外下方

### E2E-A40: 消息组间距

**前置**: 同一 session，至少有 2 条 agent 回复 + 1 条用户消息

**步骤**:
1. dump UI → 获取相邻 agent 消息的坐标
2. 计算两条连续 agent 消息的间距 → 应为 ~8dp
3. 计算 agent 消息与紧接其后的 user 消息的间距 → 应为 ~24dp
4. 截图 "polish-v2-spacing"

**断言**:
- 同一发送者连续消息间距明显小于跨发送者间距
- 视觉上消息有"呼吸感"（组间距 ≥ 20dp）

### E2E-A41: 状态气泡极简化 + 连续合并

**前置**: 同一 session

**步骤**:
1. 发送一条消息 → 等待 idle（产生 running → idle 状态变更）
2. dump UI → 定位状态指示器
3. 截图 "polish-v2-status-minimal"

**断言**:
- 状态文字为极小字号（~11sp），灰色，居中
- 状态左侧有小圆点（~6dp），颜色与状态语义对应（running=绿色, idle=蓝色）
- 无独立 pill 背景（不是圆角标签样式）
- 如果连续产生了多个相同状态（如多个 running），只显示一个（合并验证）

### E2E-A42: 代码块折叠 — 大代码块

**前置**: 同一 session

**步骤**:
1. 发送消息要求 book 输出 30+ 行代码：
   ```
   输出一段 Python 代码，至少 30 行，包含完整的类定义。用 ```python 代码块包裹。
   ```
2. 等待 idle → dump UI → 截图 "polish-v2-code-collapsed"
3. 确认代码块默认折叠（仅显示前 ~10 行）
4. 确认底部有渐变遮罩和 "展开 (N 行)" 按钮
5. 点击 "展开" 按钮 → 等待 1s → dump UI → 截图 "polish-v2-code-expanded"
6. 确认代码全部可见
7. 确认按钮变为 "收起"

**断言**:
- 折叠态：仅显示 10 行代码 + 渐变遮罩 + "展开 (N 行)" 按钮
- 展开态：显示全部代码 + "收起" 按钮
- 代码块头部有语言标签 badge（如 "python"）
- 代码块头部有复制按钮

### E2E-A43: 代码块 — 终端暗色样式

**前置**: 同一 session

**步骤**:
1. 发送消息要求 book 输出 bash 代码块：
   ```
   输出一段 bash 脚本（用 ```bash 包裹），包含 5 行命令。
   ```
2. 等待 idle → 截图 "polish-v2-terminal-block"

**断言**:
- 终端代码块背景为近纯黑（不随主题变化）
- 头部语言标签 "bash" 为绿色字体
- 代码文字为浅灰色
- 与普通代码块（如 python）样式明显不同

### E2E-A44: 小代码块不折叠

**前置**: 同一 session

**步骤**:
1. 发送消息要求输出短代码：
   ```
   输出一段 3 行的 JavaScript 代码。
   ```
2. 等待 idle → dump UI

**断言**:
- 代码块不折叠，全部可见
- 无 "展开" 按钮
- 头部仍有语言标签 + 复制按钮

### E2E-A45: Pill 输入栏

**前置**: 任意 session detail 页面

**步骤**:
1. 截图 "polish-v2-input-bar"
2. dump UI → 定位输入栏

**断言**:
- 输入框为 Pill 形状（大圆角，接近胶囊型）
- 输入框背景为半透明灰色
- 发送按钮为 36dp 圆形
- 不可发送时发送按钮为浅灰色
- 输入文字后发送按钮变为品牌蓝色
- 整个输入栏容器有半透明背景效果

### E2E-A46: FAB 缩小 + Badge

**前置**: session detail 页面，消息列表较长（滚动到非底部位置）

**步骤**:
1. 向上滚动消息列表（使 FAB 出现）
2. 截图 "polish-v2-fab"
3. 发送一条消息（不滚动到底部）→ 等待回复
4. 截图 "polish-v2-fab-badge"

**断言**:
- FAB 为 36dp 小圆形按钮（非之前的 56dp 大按钮）
- FAB 图标为向下箭头
- FAB 背景为半透明白/灰色 + 细边框
- 有新消息时 FAB 右上角显示未读 badge（蓝色小圆 + 白色数字）
- 点击 FAB → 自动滚动到底部

**清理**: complete + delete session

---

### Android E2E v4 新增用例

A38 → A39 → A40 → A41（气泡 + 间距 + 状态）
A42 → A43 → A44（代码块折叠/终端/小块）
A45 → A46（输入栏 + FAB）

---

## 测试统计

| Section | 范围 | 用例数 | 实现方式 |
|---------|------|--------|----------|
| 1-11 (v2) | 后端 API + WebSocket + Viewer | 37 | Node 自动化 |
| 12 (v2) | Android 基础功能 | 10 | adb 半自动 |
| 13 (v2) | Smoke Test | 1 | Node 自动化 |
| **14** (v3 新增) | **消息复制/选择** | **5** | **adb 半自动** |
| **15** (v3 新增) | **Slash Command** | **5** | **adb 半自动** |
| **16** (v3 新增) | **AskUserQuestion** | **3** | **adb 半自动** |
| **17** (v3 新增) | **Approval** | **2** | **adb 半自动** |
| **18** (v3 新增) | **Markdown 渲染** | **6** | **adb 半自动** |
| **19** (v3 新增) | **视觉回归** | **6** | **adb 半自动** |
| **20** (v3 新增) | **Smoke Test v3** | **1** | **Node 自动化** |
| **21** (v4 新增) | **UI 美化第二轮** | **9** | **adb 半自动** |
| **总计** | | **85** | |
