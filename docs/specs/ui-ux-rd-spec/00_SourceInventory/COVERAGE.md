# PRD → UI/UX 覆盖映射

> Note: 本文档只覆盖 Android/UI 责任面。跨层要求仍以 `docs/PRD.md`、`docs/engineering-spec/` 与 `openspec/README.md` 为准。

## Functional Requirements Coverage

| PRD Req | UI Spec Location | Status |
|---------|-----------------|--------|
| FR-01 Provider 管理 | C-06 ProviderChip, P-03 NewSession Step 1, SessionCard provider icon | ✅ |
| FR-02 Workspace 管理 | C-05 DirectoryBrowser, P-04 WorkspaceScreen, P-03 NewSession Step 2 | ✅ |
| FR-03 会话创建 | P-03 NewSessionScreen (3-step flow) | ✅ |
| FR-04 会话恢复 | P-04 WorkspaceScreen (session list per dir), P-02 SessionDetail | ✅ |
| FR-05 多会话并发 | P-01 SessionListScreen (multi-card, real-time status) | ✅ |
| FR-06 流式渲染 | C-02 MessageBubble, C-04 MarkdownRenderer, C-03 ToolCallCard, P-02 Detail | ✅ |
| FR-07 断线恢复 | C-10 ConnectionBanner, P-02 SessionDetailScreen (`isCatchingUp`), P-05 Error Handling Pattern | ✅ |
| FR-08 FCM 推送 | P-08 Deep Link pattern | ✅ |
| FR-09 主题 | Foundation §Color/Theme, P-05 SettingsScreen theme toggle | ✅ |
| FR-10 审批保留 | P-02 SessionDetail（ApprovalCard, AskUserQuestion 交互卡片, slash command 输入增强） | ✅ |

## Non-Functional Requirements Coverage

| PRD Req | UI Spec Location | Status |
|---------|-----------------|--------|
| NFR-01 Performance | P-01 SessionListScreen（冷启动/加载态）, P-02 SessionDetailScreen（流式与 catch-up）, P-06 Loading Pattern | ◐ UI 侧覆盖 |
| NFR-02 Reliability | C-10 ConnectionBanner, P-02 SessionDetailScreen, P-05 Error Handling Pattern | ◐ UI 侧覆盖 |
| NFR-03 Security | P-00 OnboardingScreen（token 密码输入、首次配置）, Settings connection management | ◐ UI 侧覆盖 |
| NFR-04 Compatibility | OVERVIEW（Android API 26+）, Foundation（Material 3 / Compose 基线） | ✅ |

## Component → Page Usage Matrix

| Component | SessionList | Detail | NewSession | Workspace | Settings | Onboarding |
|-----------|:-----------:|:------:|:----------:|:---------:|:--------:|:----------:|
| C-01 SessionCard | ✓ | | | ✓ | | |
| C-02 MessageBubble | | ✓ | | | | |
| C-03 ToolCallCard | | ✓ | | | | |
| C-04 MarkdownRenderer | | ✓ | | | | |
| C-05 DirectoryBrowser | | | ✓ | ✓ | | |
| C-06 ProviderChip | ✓ | ✓ | ✓ | ✓ | | |
| C-07 StatusIndicator | ✓ | ✓ | | | ✓ | |
| C-08 InputBar | | ✓ | | | | |
| C-09 EmptyState | ✓ | | | ✓ | | |
| C-10 ConnectionBanner | ✓ | ✓ | ✓ | ✓ | | |

## 未覆盖项（后续版本）

| Item | Reason | Target Version |
|------|--------|----------------|
| Diff/Patch 预览 | MVP 不做 | Phase 3+ |
| 多 host 管理 UI | 首版单 host | Phase 3+ |
| Web 版 | 非目标 | 无计划 |
| 动画减弱模式 | 单用户 | Phase 2+ |
