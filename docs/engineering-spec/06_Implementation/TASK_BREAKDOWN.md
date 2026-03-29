# Task Breakdown & Milestones

## Phase 0: Feasibility Spike

### M0-1: Monorepo 脚手架
**Estimate**: 2h | **Blocks**: 所有后续任务
- 初始化 monorepo（`packages/relay`, `packages/companion`, `packages/wire`, `packages/android`）
- 配置 TypeScript、ESLint、tsconfig
- 配置 Android project（Gradle、Compose、Material 3）

### M0-2: Wire 协议包
**Estimate**: 3h | **Implements**: FR-06 | **Blocks**: M0-3, M0-4
- 定义 `events.ts`：所有 EventType
- 定义 `commands.ts`：所有 CompanionCommand
- 定义 `models.ts`：Session、Host、WorkspaceRoot 等
- 定义 `errors.ts`：错误码枚举

### M0-3: Relay 最小可用
**Estimate**: 8h | **Implements**: FR-03, FR-06 | **Deps**: M0-2
- Fastify 骨架 + static token auth
- SQLite schema 初始化（sessions, session_events, hosts）
- `POST /v1/sessions` + `GET /v1/sessions`
- WS hub（Android 连接 + 事件广播）
- Companion 连接管理（接收连接 + 心跳 + 命令分发）
- Session orchestrator（create → queued → running → completed 基础链路）

### M0-4: Companion 最小可用
**Estimate**: 6h | **Implements**: FR-03, FR-04 | **Deps**: M0-2
- WSS client 连接 relay
- 心跳发送
- `create_session` 命令 → CLI process spawn (`--output-format stream-json`)
- 事件流转发到 relay
- `browse_directory` 命令

### M0-5: Android 原型
**Estimate**: 6h | **Implements**: FR-03, FR-06 | **Deps**: M0-3
- OkHttp WS 连接 relay
- 简单 Compose UI：一个按钮创建 session + 一个 LazyColumn 显示事件流
- 验证端到端链路可通
- 延迟实测

### M0-6: OpenClaw Bridge 原型
**Estimate**: 4h | **Implements**: FR-01 | **Deps**: M0-3
- 连接本地 OpenClaw gateway
- 创建一个 session + 收到事件
- 基础事件翻译

**Phase 0 总估时**: ~29h (~2 周 part-time)

**验收**: 手机原型可以通过 relay 在 MacBook 上创建 Claude session 并看到流式输出。

---

## Phase 1: Core MVP

### M1-1: Relay 完整 REST API
**Estimate**: 6h | **Implements**: FR-02, FR-03, FR-04 | **Deps**: M0-3
- 补齐所有 REST endpoints（hosts, roots, browse, sessions CRUD, events catch-up）
- 请求验证（Fastify schema validation）
- 错误码体系

### M1-2: Session 状态机完善
**Estimate**: 4h | **Implements**: FR-05 | **Deps**: M0-3
- 完整状态转换表实现
- 所有边界情况（timeout, companion 断开, invalid transition）
- Seq 分配 + gap 检测

### M1-3: Companion 完善
**Estimate**: 6h | **Implements**: FR-02, FR-04, FR-10 | **Deps**: M0-4
- `list_sessions` 命令 → 读取本地 Claude session 历史
- `resume_session` 命令 → CLI `--resume --session-id`
- `send_message` + `cancel_session`
- Workspace root 动态增删（relay 同步）
- book provider 适配（不同二进制路径）
- Permission mode 配置传递

### M1-4: OpenClaw Bridge 完善
**Estimate**: 4h | **Implements**: FR-01, FR-04 | **Deps**: M0-6
- 完整事件翻译（所有 OpenClaw event → relay event）
- Session resume / continue / cancel
- 错误处理 + 自动重连
- session_key ↔ session_id 映射持久化

### M1-5: Android Session List + Detail
**Estimate**: 10h | **Implements**: FR-03, FR-04, FR-05, FR-06
- HomeScreen: session 列表 + provider 过滤 + 状态实时更新
- DetailScreen: 消息流 + Markdown 渲染 + 语法高亮 + tool call 折叠
- Room DB: session + event 缓存
- ViewModel + Repository 完整分层
- FAB 创建入口

### M1-6: Android New Session Flow
**Estimate**: 6h | **Implements**: FR-02, FR-03
- Provider 选择 UI
- 目录浏览 UI（树形 + 面包屑）
- Prompt 输入 + model 选择
- 调用 API 创建 session → 跳转 detail

### M1-7: Android Foreground Service + WSS
**Estimate**: 6h | **Implements**: FR-05, FR-07
- SessionService: foreground notification + WS 连接
- 多 session 订阅管理
- 指数退避重连
- Since_seq catch-up
- 生命周期管理（start/stop 策略）

### M1-8: FCM Push
**Estimate**: 4h | **Implements**: FR-08 | **Deps**: M1-2
- Relay: firebase-admin 集成 + push 发送
- Android: FCMService + notification channel + deep link
- Push token 注册 API + WorkManager 刷新

### M1-9: Relay Unit Tests
**Estimate**: 4h | **Deps**: M1-1, M1-2
- State machine tests
- Seq allocation tests
- Path validation tests
- Purge job tests

### M1-10: Integration Tests
**Estimate**: 4h | **Deps**: M1-3, M1-4
- Relay + mock companion 集成测试
- Relay + mock OpenClaw 集成测试
- API 端到端测试

**Phase 1 总估时**: ~54h (~4 周 part-time)

**验收**: 完整功能链路可用——创建、恢复、多会话、流式、断线恢复、推送。

---

## Phase 2: Polish

### M2-1: Material 3 Theme System
**Estimate**: 4h | **Implements**: FR-09
- Dynamic color / custom color scheme
- System / Light / Dark 切换
- 代码高亮配色适配两种主题

### M2-2: Animation & Transitions
**Estimate**: 4h
- Shared element transition (session card → detail)
- Message fade-in + slide-up
- Status color morph
- Pull-to-refresh
- Theme cross-fade

### M2-3: Markdown Rendering Optimization
**Estimate**: 4h | **Implements**: FR-06
- 选型：Markwon 或自定义 Compose Markdown renderer
- 语法高亮库集成（Prism/Highlight.js 端口或原生方案）
- 增量渲染性能优化（大消息不卡）
- 自动滚动 / 手动模式

### M2-4: Workspace Manager UI
**Estimate**: 3h | **Implements**: FR-02
- Root 列表管理（添加/删除/编辑 label）
- 与 Settings 集成

### M2-5: Error UX Polish
**Estimate**: 3h
- 三层故障区分的 UI 表达（host_offline / provider_unreachable / network_error）
- Snackbar / Banner 组件
- 空状态设计

### M2-6: 30-Day Purge + Cleanup
**Estimate**: 2h
- node-cron job 实现
- Android 端同步清理本地缓存

**Phase 2 总估时**: ~20h (~2 周 part-time)

**验收**: 日常使用品质——美观、流畅、错误可理解。

---

## Dependency Graph

```
M0-1 ──► M0-2 ──┬──► M0-3 ──┬──► M0-5
                 │           │
                 │           ├──► M0-6
                 │           │
                 └──► M0-4   │
                             │
                             ▼
                    ┌────────────────┐
                    │   Phase 0 Done  │
                    └───────┬────────┘
                            │
          ┌─────────────────┼─────────────────┐
          ▼                 ▼                 ▼
       M1-1              M1-3              M1-4
       M1-2                │                 │
          │                 │                 │
          ▼                 ▼                 ▼
       M1-5 ◄───────── M1-7              M1-8
       M1-6                │
          │                 │
          ▼                 ▼
       M1-9              M1-10
                            │
                            ▼
                   ┌────────────────┐
                   │  Phase 1 Done   │
                   └───────┬────────┘
                           │
             ┌─────────────┼─────────────┐
             ▼             ▼             ▼
          M2-1           M2-3          M2-5
          M2-2           M2-4          M2-6
                           │
                           ▼
                  ┌────────────────┐
                  │  Phase 2 Done   │
                  └────────────────┘
```

## Risk Register

| Risk | Prob | Impact | Mitigation | Contingency |
|------|------|--------|------------|-------------|
| Claude Code CLI stream-json 格式变更 | Low | Medium | adapter 层隔离 | 降级到 CLI fallback |
| OpenClaw gateway 协议不稳定 | Medium | Medium | bridge 翻译层 + Phase 0 验证 | 暂时只支持 Claude/book |
| Android Compose Markdown 库不成熟 | Medium | Low | 多候选评估 | 降级到 WebView 渲染 |
| VPS 2C/3.5G 资源紧张 | Low | Medium | Phase 0 压测 | 升配或 SQLite → 文件缓存 |
| Claude session 恢复语义不确定 | Medium | Medium | Phase 0 先验证 | 只做"查看历史"不做 resume |
