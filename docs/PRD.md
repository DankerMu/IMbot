# IMbot — Product Requirements Document

**Version**: 1.0
**Date**: 2026-03-28
**Author**: danker
**Status**: Draft

---

## 1. Executive Summary

### Problem Statement

用户（独立开发者）日常在 MacBook 上使用 Claude Code、book（Claude Code 同源二进制）和 OpenClaw 进行 AI 辅助开发。离开电脑后，无法从手机查看任务状态、发起新任务或恢复旧会话。官方 Remote Control 受限于网络环境（手机无法直连 Claude 服务）、无法自定义链路与会话控制，不满足需求。

### Solution Overview

IMbot 是一个 Android 原生应用 + 自建云端 relay + MacBook 本地 companion 的三端系统。用户通过手机连接自有 relay 服务器，relay 分别将指令转发到 MacBook 本地 companion（控制 Claude Code / book）和 relay 本机 OpenClaw gateway，实现对三种 AI agent 的统一远程控制、多会话管理和流式输出查看。

### Success Metrics

| Metric | Target |
|--------|--------|
| 端到端操作延迟（发送指令 → 首个流式事件到达 Android） | < 1s |
| 断线恢复后事件完整性 | 100%（无丢失事件） |
| WSS 连接稳定性（24h 内无需手动重连） | > 99% |
| 日常使用频率 | ≥ 3 次/天 |
| 支持的并发活跃 session 数 | 无硬上限 |

---

## 2. Background & Context

### Business Context

- **单用户产品**：仅供项目作者个人使用，不涉及多租户、注册流程或公开发布。
- **自建基础设施**：relay 部署在 RackNerd VPS（2C/3.5G RAM, 1GB 带宽, 纽约节点）。
- **MacBook 24h 在线**：不涉及远程唤醒，companion 可假设持续可用。

### 网络硬约束

```
Android 手机 ──HTTPS/WSS──► relay VPS（纽约）
                                │
                    ┌───────────┴───────────┐
                    │                       │
              outbound WSS            localhost WS
                    │                       │
                    ▼                       ▼
            MacBook companion      OpenClaw gateway
            (Claude Code / book)   (relay 本机)
                    │
                    ▼
            Anthropic upstream
            (MacBook 网络发起)
```

- Android **无法**直连 Anthropic / Claude 服务。
- Android **无法**直连 MacBook。
- MacBook **可以**访问 Anthropic upstream 和 relay。
- relay VPS 上的 OpenClaw gateway 可由 relay 直接 localhost 访问。

### 竞品对比

| 能力 | Anthropic Remote Control | IMbot |
|------|-------------------------|-------|
| 继续本地 Claude Code 会话 | ✓ | ✓ |
| 自有 relay / 自建链路 | ✗ | ✓ |
| CLI 远控 / 自定义链路 | ✗ | ✓ |
| 新建指定目录会话 | ✗ | ✓ |
| 按目录列出并恢复旧会话 | ✗ | ✓ |
| 多 provider（Claude Code / book / OpenClaw） | ✗ | ✓ |
| 自定义审计 / 审批 | ✗ | ✓（代码保留，默认关闭） |
| 无 Claude 网络要求 | ✗ | ✓（手机只需连 relay） |

### 参考项目

- **happy**（`../reference//happy` 目录）：四平面架构、session protocol、wire 协议包、Socket.IO realtime 设计均可参考。
- **OpenClaw**：开源 AI 助手网关，WebSocket 协议，原生支持 24+ 聊天平台。本项目直接在 relay VPS 上运行其 gateway。

---

## 3. Goals & Non-Goals

### Goals

1. 在 Android 手机上对 **Claude Code**、**book**、**OpenClaw** 三种 provider 进行统一远程控制。
2. 支持在指定本地目录 **创建新会话**。
3. 支持 **列出并恢复** 指定目录下的历史会话。
4. 支持 **多会话并发运行**，可在 session 之间快速切换。
5. **低延迟流式输出**：指令到首个事件 < 1 秒。
6. **断线无感恢复**：自动重连 + `since_seq` 补拉，不丢事件。
7. **FCM 推送**：session 完成/失败时推送通知。
8. **美观 UI**：Markdown + 语法高亮渲染，深色/浅色主题跟随系统可切换，动画流畅。
9. 形成可独立演进的三端代码库（Android / relay / companion）。

### Non-Goals

1. **不做多用户/多租户**：无注册、无 RBAC、无组织管理。
2. **不做完整 IDE**：无本地 Git 编辑器、无大型 diff 工具链。
3. **不做 iOS 版本**。
4. **不做浏览器 Web 版**（MVP 阶段）。
5. **不把 Claude Runtime 搬到云端**：Claude Code / book 只在 MacBook 本地运行。
6. **不依赖 Anthropic 私有接口**。
7. **不做端到端加密**（MVP 阶段，relay 可见会话内容；Phase 2+ 评估）。

---

## 4. User Persona

### The Solo Developer

- **身份**：独立开发者，日常重度使用 AI agent 辅助编程。
- **设备**：MacBook（24h 开机） + Android 手机。
- **网络**：手机网络无法直连 Claude 服务。
- **使用场景**：
  - 离开电脑后，手机查看正在运行的 Claude 任务输出。
  - 手机上对新项目目录发起 Claude / book / OpenClaw 会话。
  - 手机上恢复昨天的会话继续工作。
  - 收到推送通知后查看任务结果。
- **技术水平**：高。可以接受开发者级别的错误提示和配置方式。
- **审美要求**：高。要求 UI 美观、动画丝滑、操作响应快。

---

## 5. Functional Requirements

### FR-01: Provider 管理

**描述**：系统支持三种 provider，每种 provider 独立管理会话。

| Provider | 标识 | 运行位置 | 执行面 |
|----------|------|----------|--------|
| Claude Code | `claude` | MacBook 本地 | companion → `claude` CLI (stream-json) |
| book | `book` | MacBook 本地 | companion → 同一 Claude Code 二进制 |
| OpenClaw | `openclaw` | relay VPS 本机 | relay → localhost OpenClaw gateway WS |

**规则**：
- Android UI 中三种 provider 平级展示，各自会话独立。
- 创建会话时必须选择 provider。
- book 与 Claude Code 虽然是同一二进制，但在 UI、session 存储和 workspace 中**完全独立**。
- book **仅使用 novel 目录**（如 `~/Desktop/novel`），不共享 Claude Code 的 workspace root。

**接受标准**：
- [ ] 会话列表可按 provider 过滤。
- [ ] 创建会话时 provider 选择明确可见。
- [ ] 三种 provider 的会话互不干扰。

### FR-02: Workspace 管理

**描述**：用户可以在手机端动态管理"根目录"，并浏览其下任意深度的子目录，选择某个目录作为新会话的工作目录。

**规则**：
- 用户可动态添加/移除根目录（如 `~/Desktop/AI-vault`、`~/Desktop/novel`）。
- 根目录列表持久化在 companion 配置中，由 relay 同步到 Android。
- 浏览子目录时**无深度限制**。
- 对 Claude Code / book：根目录在 MacBook 本地文件系统上。
- 对 OpenClaw：根目录在 relay VPS 文件系统上。

**数据**：

| Field | Type | Description |
|-------|------|-------------|
| `root_id` | uuid | 根目录唯一标识 |
| `host_id` | string | 所属 host（MacBook 或 relay） |
| `provider` | enum | `claude \| book \| openclaw` |
| `path` | string | 绝对路径 |
| `label` | string? | 用户自定义显示名称 |
| `created_at` | timestamp | 添加时间 |

**接受标准**：
- [ ] 手机端可添加新根目录。
- [ ] 手机端可移除根目录（不影响已有会话）。
- [ ] 浏览子目录响应 < 500ms。
- [ ] 目录列表正确反映 host 文件系统实际状态。

### FR-03: 会话创建

**描述**：用户在手机上选择 provider + 工作目录，输入 prompt，创建新会话。

**流程**（Happy Path）：
1. 用户打开 App → 点击"新建会话"。
2. 选择 provider（Claude Code / book / OpenClaw）。
3. 选择或浏览工作目录。
4. 输入初始 prompt。
5. 可选：选择 model（如 `opus`、`sonnet`）。
6. 点击"开始"。
7. App 跳转到会话详情页，开始接收流式输出。

**Sad Paths**：

| Failure | User Sees | System Behavior |
|---------|-----------|-----------------|
| MacBook companion 离线 | "MacBook offline" 明确提示 | 阻止创建 Claude/book 会话 |
| OpenClaw gateway 不可用 | "OpenClaw gateway unreachable" | 阻止创建 OpenClaw 会话 |
| Claude upstream 不可达 | 会话创建成功但立即进入 `failed` 状态 | companion 报告 upstream error |
| 目录不存在 | "Directory not found" | companion 验证后拒绝 |
| 网络中断（Android → relay） | 自动重试，超时后提示 | 指数退避重连 |

**接受标准**：
- [ ] 从点击"开始"到首个流式事件 < 1s（网络正常时）。
- [ ] 创建后 session 出现在会话列表中。
- [ ] 所有 sad path 有明确且可区分的错误提示。

### FR-04: 会话恢复

**描述**：用户可以列出某个工作目录下的历史会话，选择一个恢复。

**流程**：
1. 用户浏览到某个工作目录。
2. 看到该目录下的历史 session 列表（按时间倒序）。
3. 每个 session 显示：创建时间、初始 prompt 摘要、状态、provider。
4. 用户选择一个 session → 点击"恢复"。
5. App 进入会话详情页，加载历史消息，可继续对话。

**规则**：
- 参考 Claude Code 原生的 session 列表设计。
- 只显示最近 30 天内活跃的 session（不活跃 session 自动清理）。
- 恢复时使用 `provider_session_id` 在本地 runtime 恢复上下文。

**接受标准**：
- [ ] 历史 session 列表正确反映本地 runtime 的真实 session 数据。
- [ ] 恢复后可正常继续对话。
- [ ] 已完成的 session 可查看历史但不可恢复。

### FR-05: 多会话并发

**描述**：支持同时运行多个活跃 session，用户可在它们之间快速切换。

**规则**：
- 不设硬性并发上限。
- 每个 session 独立维护 WSS 订阅或共享一条 WSS 连接做多路复用。
- 切换 session 时保留当前 session 运行状态，不中断。
- 会话列表实时更新每个 session 的状态（running / completed / failed）。

**状态机**：

```
               create
    ┌──────────────────────► running ◄─── resume
    │                          │
    │                    ┌─────┼──────┐
    │                    │     │      │
    │                    ▼     ▼      ▼
    │              completed failed cancelled
    │                    │     │      │
    │                    └─────┴──────┘
    │                          │
    │                     (30 days)
    │                          │
    │                          ▼
    │                       purged
    │
 queued ──(host offline)──► failed
```

**状态定义**：

| Status | Description |
|--------|-------------|
| `queued` | 命令已发送，等待 host 响应 |
| `running` | 正在执行，流式事件持续到达 |
| `completed` | 正常完成 |
| `failed` | 执行出错（含 upstream 错误） |
| `cancelled` | 用户主动取消 |
| `purged` | 超过 30 天不活跃，已清理 |

**接受标准**：
- [ ] 3 个 session 同时 running 时 UI 不卡顿。
- [ ] 切换 session 时上一个 session 输出不中断。
- [ ] 状态变更实时反映在会话列表中。

### FR-06: 流式输出与实时渲染

**描述**：session 的 agent 输出以流式方式实时传输到 Android 端并渲染。

**渲染要求**：
- **Markdown 渲染**：完整支持 GFM（标题、列表、表格、代码块、链接等）。
- **语法高亮**：代码块根据语言标注高亮。
- **增量渲染**：`assistant_delta` 事件逐字/逐块追加，不等完整消息。
- **工具调用展示**：tool call 名称、参数和结果可折叠查看。
- **自动滚动**：新内容到达时自动滚动到底部；用户手动上滑时暂停自动滚动。

**事件类型**：

| Event | Description | 渲染方式 |
|-------|-------------|----------|
| `session_started` | 会话启动 | 状态标记 |
| `assistant_delta` | 增量文本 | 追加到消息气泡 |
| `assistant_message` | 完整消息 | 替换当前增量内容 |
| `tool_call_started` | 工具调用开始 | 显示工具名称 + spinner |
| `tool_call_completed` | 工具调用完成 | 显示结果，移除 spinner |
| `session_status_changed` | 状态变更 | 更新顶部状态栏 |
| `session_result` | 最终结果 | 标记完成 |
| `session_error` | 错误 | 显示错误信息 |

**接受标准**：
- [ ] 代码块语法高亮正常。
- [ ] 流式追加无闪烁。
- [ ] 自动滚动 / 手动查看模式切换顺畅。
- [ ] 长消息（> 10000 字符）渲染不卡顿。

### FR-07: 断线恢复

**描述**：Android 网络中断后自动重连，并无感补拉断线期间的所有事件。

**流程**：
1. WSS 连接断开。
2. Foreground service 指数退避重连（1s, 2s, 4s, 8s, ... 最大 30s）。
3. 重连成功后，带上最后一个 `seq` 请求 `GET /v1/sessions/{id}/events?since_seq=...`。
4. 补拉的事件按 `seq` 顺序合并到本地状态。
5. UI 恢复到最新状态，用户无感知。

**规则**：
- `seq` 必须单 session 单调递增。
- 补拉的事件量无上限（relay 负责归档但保证可查）。
- 如果补拉发现 `seq` 有 gap，标记为 `seq_gap_detected` 错误并提示用户。

**接受标准**：
- [ ] 断线 60 秒内恢复后，所有事件完整补拉。
- [ ] 断线 10 分钟恢复后，仍可完整补拉。
- [ ] 补拉期间 UI 显示"正在同步..."状态。
- [ ] 补拉完成后 UI 无跳变、无重复消息。

### FR-08: FCM 推送通知

**描述**：当 session 完成或失败时，即使 App 不在前台也能收到推送。

**通知触发条件**：

| Condition | Notification |
|-----------|--------------|
| Session 完成 | "✓ {session_name} 已完成" |
| Session 失败 | "✗ {session_name} 执行失败: {error_summary}" |
| Host 离线 | "MacBook 已离线" |

**规则**：
- 点击通知直接跳转到对应 session 详情页。
- App 在前台时不弹推送，使用 in-app 提示。
- push token 刷新由 WorkManager 负责。

**接受标准**：
- [ ] App 在后台时收到完成/失败推送。
- [ ] 点击推送正确跳转。
- [ ] 前台不重复弹通知。

### FR-09: 主题与外观

**描述**：支持深色/浅色主题，默认跟随系统设置。

**规则**：
- 默认跟随系统。
- 用户可在 Settings 中手动切换：System / Light / Dark。
- 主题切换即时生效，无需重启。
- 代码高亮配色跟随主题。

**接受标准**：
- [ ] 三种主题模式均正常渲染。
- [ ] 切换时无白闪/黑闪。
- [ ] 代码高亮在两种主题下均清晰可读。

### FR-10: 权限与审批（保留能力，默认关闭）

**描述**：系统保留完整的工具调用审批能力，但 MVP 默认使用 `--permission-mode bypassPermissions`。

**规则**：
- 默认所有 provider session 以 `bypassPermissions` 模式运行。
- companion 配置中可按 workspace 覆盖 permission mode。
- 审批相关的数据模型、事件类型和 API 保留实现，但 UI 中不主动展示。
- 后续如需开启审批，修改配置即可，无需改代码。

**接受标准**：
- [ ] 默认模式下不弹出任何审批请求。
- [ ] 配置切换到 `default` 模式后，审批流程可正常工作。

---

## 6. User Experience

### 6.1 Screen Map

```
App Launch
    │
    ├── Session List (Home)
    │       ├── Filter: All / Claude / book / OpenClaw
    │       ├── Each card: provider icon + workspace + prompt summary + status + time
    │       └── FAB: New Session
    │
    ├── New Session Flow
    │       ├── Step 1: Select Provider
    │       ├── Step 2: Browse Workspace (root → subdirectory tree)
    │       ├── Step 3: Input Prompt + optional model selection
    │       └── → Redirect to Session Detail
    │
    ├── Session Detail
    │       ├── Top bar: session name + status + provider badge
    │       ├── Message stream (Markdown + syntax highlight)
    │       ├── Tool call collapsible cards
    │       ├── Input bar (continue conversation)
    │       └── Actions: Cancel / Archive
    │
    ├── Workspace Manager
    │       ├── Root directory list (per host)
    │       ├── Add / Remove root
    │       └── Browse subdirectories
    │
    └── Settings
            ├── Theme: System / Light / Dark
            ├── Relay URL
            ├── Connection status
            ├── Provider status (MacBook online? OpenClaw online?)
            └── About / Version
```

### 6.2 关键交互规格

#### 会话列表 (Home)

- 默认按"最近活跃"排序。
- Running session 顶部置顶，带动画脉冲指示。
- 左滑可归档/删除。
- 下拉刷新。
- 空状态："无会话，点击 + 开始"。

#### 会话详情

- 消息流仿 IM 气泡布局：
  - 用户消息：右对齐。
  - Agent 输出：左对齐，provider 头像标识。
  - Tool call：独立可折叠卡片。
- 输入框：
  - 支持多行文本。
  - 发送按钮 + 快捷操作（取消当前 session）。
- 状态指示：
  - Running：顶栏绿色脉冲。
  - Completed：顶栏静态绿色 ✓。
  - Failed：顶栏红色 ✗ + 错误摘要。

#### 目录浏览

- 树形导航，点击目录进入下一层。
- 面包屑导航：可快速回到任意上层目录。
- 只显示目录，不显示文件。
- 当前选中目录高亮，底部"确认选择"按钮。

### 6.3 动画与过渡

| 场景 | 动画 |
|------|------|
| 页面切换 | Shared element transition（session card → detail） |
| 新消息追加 | Fade in + slide up |
| 状态变更 | Color morph animation |
| 下拉刷新 | Material 3 pull-to-refresh |
| 主题切换 | Cross-fade（无闪烁） |

---

## 7. System Architecture

### 7.1 High-Level Topology

```
┌─────────────────────────┐
│     Android App         │
│  ┌───────────────────┐  │     HTTPS / WSS
│  │ Compose UI        │  │◄────────────────────┐
│  │ ViewModel + Flow  │  │                     │
│  │ Room cache        │  │                     │
│  │ Foreground Service │  │                     │
│  │ FCM receiver      │  │                     │
│  └───────────────────┘  │                     │
└─────────────────────────┘                     │
                                                │
                                    ┌───────────┴───────────┐
                                    │    Relay Server        │
                                    │  ┌─────────────────┐  │
                                    │  │ Fastify REST     │  │
                                    │  │ WebSocket hub    │  │
                                    │  │ Session store    │  │
                                    │  │ Event store      │  │
                                    │  │ Push adapter     │  │
                                    │  │ OpenClaw bridge  │──┼──► OpenClaw gateway
                                    │  └─────────────────┘  │    (ws://localhost:18789)
                                    └───────────┬───────────┘
                                                │
                                          outbound WSS
                                                │
                                    ┌───────────┴───────────┐
                                    │  MacBook Companion     │
                                    │  ┌─────────────────┐  │
                                    │  │ WSS client       │  │
                                    │  │ Workspace catalog │  │
                                    │  │ Session index    │  │
                                    │  │ Claude adapter   │  │
                                    │  │ book adapter     │  │
                                    │  └────────┬────────┘  │
                                    │           │           │
                                    │    claude / book CLI  │
                                    │           │           │
                                    │    Anthropic upstream  │
                                    └───────────────────────┘
```

### 7.2 Provider 执行路径

```
Claude Code / book:
  Android → relay (command) → companion (WSS) → local claude/book CLI → Anthropic
  Anthropic → local claude/book CLI (stream-json) → companion → relay (events) → Android

OpenClaw:
  Android → relay (command) → OpenClaw gateway (localhost WS)
  OpenClaw gateway → relay (events) → Android
```

### 7.3 组件职责

| Component | Responsibilities |
|-----------|-----------------|
| **Android App** | UI 渲染、本地缓存、foreground service、WSS 管理、FCM、主题 |
| **Relay Server** | 认证、REST/WSS API、会话状态机、事件持久化、push 分发、OpenClaw bridge、host 在线探测 |
| **MacBook Companion** | 出站 WSS 连接 relay、workspace 目录服务、本地 Claude/book session 管理、事件转发 |
| **Local Claude Runtime** | `claude` / `book` CLI 进程（`--output-format stream-json`） |
| **OpenClaw Gateway** | 独立进程，relay 通过 localhost WS 对接 |

---

## 8. Data Requirements

### 8.1 Core Data Model

#### sessions

| Field | Type | Description |
|-------|------|-------------|
| `id` | uuid | Relay 侧主键 |
| `provider` | enum | `claude \| book \| openclaw` |
| `provider_session_id` | string | 底层 runtime 返回的 session 标识 |
| `host_id` | string | 所属 host |
| `workspace_root` | string | 根目录 path |
| `workspace_cwd` | string | 实际工作目录 path |
| `initial_prompt` | text | 首条用户输入（用于摘要显示） |
| `model` | string? | 使用的模型标识 |
| `permission_mode` | string | 默认 `bypassPermissions` |
| `status` | enum | `queued \| running \| completed \| failed \| cancelled` |
| `created_at` | timestamp | |
| `updated_at` | timestamp | |
| `last_active_at` | timestamp | 用于 30 天清理 |

#### session_events

| Field | Type | Description |
|-------|------|-------------|
| `id` | uuid | 事件唯一标识 |
| `session_id` | uuid | 所属 session |
| `seq` | integer | 单 session 单调递增 |
| `type` | string | 事件类型 |
| `payload` | jsonb | 事件内容 |
| `timestamp` | timestamp | |

#### workspace_roots

| Field | Type | Description |
|-------|------|-------------|
| `id` | uuid | |
| `host_id` | string | MacBook 或 relay |
| `provider` | enum | |
| `path` | string | 绝对路径 |
| `label` | string? | 显示名 |
| `created_at` | timestamp | |

#### hosts

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | host 唯一标识 |
| `name` | string | 显示名（如 "MacBook Pro"） |
| `type` | enum | `macbook \| relay_local` |
| `status` | enum | `online \| offline` |
| `last_heartbeat` | timestamp | |

#### push_subscriptions

| Field | Type | Description |
|-------|------|-------------|
| `id` | uuid | |
| `fcm_token` | string | |
| `updated_at` | timestamp | |

### 8.2 数据生命周期

| Data | Retention | Cleanup |
|------|-----------|---------|
| Session（活跃） | 永久 | N/A |
| Session（不活跃：`completed` / `failed` / `cancelled`） | 30 天 | 自动 purge |
| Session events | 跟随 session | 跟随 session purge |
| Workspace roots | 永久 | 用户手动删除 |
| Host records | 永久 | N/A |

### 8.3 存储选型

- **relay**: SQLite（单用户场景完全够用，内存占用远低于 PostgreSQL，适合 2C/3.5G VPS）。
- **Android 本地**: Room（SQLite），缓存 session summary + 最近事件用于离线查看。

---

## 9. API Design

### 9.1 认证

**方式**：静态 token，通过 `Authorization: Bearer <STATIC_TOKEN>` 传递。

- relay 配置文件中设置 `RELAY_STATIC_TOKEN`。
- companion 配置文件中设置同一 token。
- Android App 中配置同一 token。
- 所有 REST / WSS 请求均需携带此 token，否则返回 `401`。

### 9.2 REST API

**Base**: `POST/GET https://{relay}/v1/...`

#### Host & Workspace

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/v1/hosts` | 列出所有 host 及在线状态 |
| `GET` | `/v1/hosts/{hostId}/roots` | 列出 host 的根目录 |
| `POST` | `/v1/hosts/{hostId}/roots` | 添加根目录 |
| `DELETE` | `/v1/hosts/{hostId}/roots/{rootId}` | 移除根目录 |
| `GET` | `/v1/hosts/{hostId}/browse?path=...` | 浏览目录（返回子目录列表） |

#### Session Lifecycle

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/v1/sessions` | 会话列表（支持 `?provider=` `?status=` 过滤） |
| `GET` | `/v1/sessions/{id}` | 会话详情 |
| `POST` | `/v1/sessions` | 创建会话 |
| `POST` | `/v1/sessions/{id}/resume` | 恢复会话 |
| `POST` | `/v1/sessions/{id}/message` | 发送消息（继续对话） |
| `POST` | `/v1/sessions/{id}/cancel` | 取消会话 |
| `DELETE` | `/v1/sessions/{id}` | 归档/删除会话 |

#### Events

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/v1/sessions/{id}/events?since_seq=N` | 补拉事件 |

#### System

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/healthz` | 健康检查 |

### 9.3 WebSocket

**Endpoint**: `wss://{relay}/v1/ws`

**握手**：`Authorization: Bearer <token>` 在 header 或首条消息中传递。

**Server → Client messages**:

```jsonc
// 事件推送（所有订阅 session 的事件）
{
  "type": "event",
  "session_id": "uuid",
  "seq": 183,
  "event_type": "assistant_delta",
  "payload": { "text": "Hello..." },
  "timestamp": "2026-03-28T13:07:10Z"
}

// 状态变更
{
  "type": "status",
  "session_id": "uuid",
  "status": "completed"
}

// Host 状态
{
  "type": "host_status",
  "host_id": "macbook-1",
  "status": "online"
}
```

**Client → Server messages**:

```jsonc
// 订阅 session 事件
{ "action": "subscribe", "session_id": "uuid" }

// 取消订阅
{ "action": "unsubscribe", "session_id": "uuid" }

// Ping
{ "action": "ping" }
```

**设计决策**：使用原生 WebSocket 而非 Socket.IO。原因：单用户场景不需要 room 机制；减少 VPS 上的依赖和内存占用；OkHttp 原生支持 WebSocket。

### 9.4 Companion ↔ Relay 内部协议

Companion 通过出站 WSS 连接 relay。

**Relay → Companion (commands)**:

```jsonc
{ "cmd": "create_session", "req_id": "uuid", "session_id": "uuid", "provider": "claude", "cwd": "/path", "prompt": "...", "model": "opus", "permission_mode": "bypassPermissions" }
{ "cmd": "resume_session", "req_id": "uuid", "session_id": "uuid", "provider_session_id": "...", "cwd": "/path" }
{ "cmd": "send_message", "req_id": "uuid", "session_id": "uuid", "text": "..." }
{ "cmd": "cancel_session", "req_id": "uuid", "session_id": "uuid" }
{ "cmd": "list_sessions", "req_id": "uuid", "cwd": "/path", "provider": "claude" }
{ "cmd": "browse_directory", "req_id": "uuid", "path": "/path" }
```

**Companion → Relay (responses & events)**:

```jsonc
{ "type": "ack", "req_id": "uuid", "status": "ok", "data": { ... } }
{ "type": "ack", "req_id": "uuid", "status": "error", "error_code": "directory_not_found", "message": "..." }
{ "type": "event", "session_id": "uuid", "seq": 183, "event_type": "assistant_delta", "payload": { ... } }
{ "type": "heartbeat", "host_id": "macbook-1", "providers": ["claude", "book"], "uptime": 86400 }
```

### 9.5 Relay ↔ OpenClaw 内部协议

Relay 通过 `ws://localhost:18789` 连接本机 OpenClaw gateway。

具体协议遵循 OpenClaw 原生 WebSocket 协议，relay 内部 OpenClaw bridge 负责：
- 将 relay 的统一命令格式转换为 OpenClaw session 操作。
- 将 OpenClaw 的事件流（transcript events、session lifecycle events）转换为 relay 统一事件格式。
- 维护 OpenClaw `session_key` 到 relay `session_id` 的映射。

---

## 10. Non-Functional Requirements

### NFR-01: Performance

| Metric | Target | Measurement |
|--------|--------|-------------|
| 指令到首个事件延迟 | < 1s | P95 |
| 目录浏览响应 | < 500ms | P95 |
| 会话列表加载 | < 300ms | 本地缓存命中 |
| 补拉 1000 条事件 | < 3s | P95 |
| App 冷启动 | < 2s | 到会话列表可见 |
| 内存占用（Android） | < 200MB | 前台运行时 |

### NFR-02: Reliability

| Metric | Target |
|--------|--------|
| WSS 连接稳定性（24h） | > 99% uptime |
| 事件完整性（断线恢复） | 100% |
| relay 可用性 | 不做 HA，接受 VPS 重启导致的短暂中断 |

### NFR-03: Security

- 所有外部链路 HTTPS / WSS（TLS 1.2+）。
- Android 禁止 cleartext traffic（`network_security_config`）。
- 静态 token 不硬编码在 APK 中，通过首次配置输入。
- MacBook 本地凭据（Claude API key 等）不上传到 relay。
- relay 只存储会话元数据和事件，不存储 MacBook 本地文件内容。
- MVP 不做 E2E 加密（relay 可见会话内容），Phase 2+ 评估。
- MVP 不做 certificate pinning。

### NFR-04: Compatibility

- Android: API 26+（Android 8.0）。
- 目标 SDK: 最新稳定版。
- relay: Node.js 22+ / TypeScript。
- companion: Node.js 22+ / TypeScript。

---

## 11. Dependencies & Integrations

### External Dependencies

| Dependency | Used By | Purpose | Failure Impact |
|------------|---------|---------|----------------|
| Anthropic API | MacBook local runtime | Claude 模型调用 | Claude/book session 创建失败 |
| OpenClaw gateway | relay VPS | OpenClaw session 管理 | OpenClaw session 不可用 |
| FCM (Firebase) | relay → Android | 推送通知 | 无后台推送，不影响核心功能 |
| SQLite | relay | 数据持久化 | relay 不可用 |

### Tech Stack

| Layer | Technology |
|-------|-----------|
| Android UI | Kotlin + Jetpack Compose + Material 3 |
| Android state | ViewModel + Coroutines + Flow |
| Android local DB | Room |
| Android networking | OkHttp (REST + WebSocket) |
| Android push | Firebase Cloud Messaging |
| Relay server | TypeScript + Fastify + ws |
| Relay DB | SQLite (via better-sqlite3 或 drizzle-orm) |
| Companion | TypeScript + ws client |
| Claude runtime | `claude` / `book` CLI (`--output-format stream-json`) |
| OpenClaw runtime | OpenClaw gateway (独立进程) |

---

## 12. Timeline & Milestones

### Phase 0: Feasibility Spike（~2 周）

**目标**：验证核心链路可通。

- [ ] MacBook companion 与 relay 建立稳定 WSS 连接。
- [ ] 通过 relay 在指定目录创建 Claude Code session。
- [ ] 流式事件从 MacBook → relay → 简易 Android 原型。
- [ ] OpenClaw gateway 可通过 relay localhost WS 创建 session。
- [ ] 端到端延迟实测 < 1s。

**交付物**：可运行的 demo，三端最小可通代码。

### Phase 1: Core MVP（~4 周）

**目标**：功能可用的完整链路。

- [ ] Relay: 完整 REST/WSS API、session 状态机、event store、static token auth。
- [ ] Companion: host 注册、heartbeat、workspace 目录服务、Claude/book session adapter。
- [ ] Android: Session List + Detail + New Session flow + Foreground Service + WSS + Room cache。
- [ ] OpenClaw bridge: session 创建/恢复/事件转换。
- [ ] 断线恢复（`since_seq`）。
- [ ] FCM 推送。

### Phase 2: Polish（~2 周）

**目标**：达到日常使用品质。

- [ ] Material 3 主题系统（System / Light / Dark）。
- [ ] 动画与过渡效果。
- [ ] Markdown + 语法高亮渲染优化。
- [ ] 30 天数据清理。
- [ ] 错误提示优化（三层故障区分）。
- [ ] Workspace 动态管理 UI。

### Phase 3: Hardening（持续）

- [ ] 连接稳定性优化。
- [ ] 内存与性能调优。
- [ ] E2E 加密评估。
- [ ] 审批功能 UI（如需开启）。

---

## 13. Risks & Mitigations

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| VPS 资源不足（2C/3.5G 同时跑 relay + SQLite + OpenClaw） | Low | Medium | SQLite 内存开销极小；Phase 0 压测；必要时升配 |
| OpenClaw gateway 协议变更 | Low | Medium | bridge 层做适配隔离 |
| Android 不同 ROM 后台策略杀 foreground service | Medium | Medium | 真机测试主流 ROM；补充 FCM 兜底 |
| 跨太平洋延迟（中国手机 → 纽约 VPS） | High | Low | 已确认可接受 ~500ms-1s 延迟 |
| Claude Code CLI 输出格式变更 | Low | Medium | companion 的 adapter 层做版本隔离 |

---

## 14. Open Questions（已闭合）

| Question | Decision |
|----------|----------|
| 支持几台 MacBook？ | 单 host，数据模型兼容多 host |
| Workspace 发现策略？ | 手动动态添加根目录，浏览子目录无深度限制 |
| Session 恢复粒度？ | 全列表 + 选择恢复 |
| 需要 diff 预览？ | MVP 不做 |
| 审批模式？ | 代码保留，默认 bypassPermissions |
| 认证方式？ | 静态 token |
| E2E 加密？ | MVP 不做，Phase 2+ 评估 |
| 多 provider？ | 三种平级：Claude Code / book / OpenClaw |

---

## Appendix

### A. Glossary

| Term | Definition |
|------|-----------|
| **relay** | 云端中继服务器，承担控制面、事件持久化和消息转发 |
| **companion** | MacBook 本地守护进程，管理 Claude/book session |
| **provider** | AI agent 类型（Claude Code / book / OpenClaw） |
| **host** | 一台已注册的执行机器（MacBook 或 relay 本机） |
| **workspace root** | 用户配置的项目根目录 |
| **cwd** | 会话的具体工作目录 |
| **seq** | 单 session 单调递增的事件序号 |
| **provider_session_id** | 底层 runtime 返回的 session 标识 |
| **catch-up** | 断线恢复时按 `since_seq` 补拉遗漏事件 |

### B. Reference Documents

- `docs/dr-workflow/claude-code-android-relay/final/` — 原始技术设计文档包
- `happy/` — 参考项目（四平面架构、session protocol）
- OpenClaw 官方文档：https://docs.openclaw.ai

### C. Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-03-28 | danker | Initial PRD based on interactive discovery |
