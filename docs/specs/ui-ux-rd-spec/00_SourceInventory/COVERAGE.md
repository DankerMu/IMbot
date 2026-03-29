# PRD → UI/UX 覆盖映射

## Functional Requirements Coverage

| PRD Req | UI Spec Location | Status |
|---------|-----------------|--------|
| FR-01 Provider 管理 | C-06 ProviderChip, P-03 NewSession Step 1, SessionCard provider icon | ✅ |
| FR-02 Workspace 管理 | C-05 DirectoryBrowser, P-04 WorkspaceScreen, P-03 NewSession Step 2 | ✅ |
| FR-03 会话创建 | P-03 NewSessionScreen (3-step flow) | ✅ |
| FR-04 会话恢复 | P-04 WorkspaceScreen (session list per dir), P-02 SessionDetail | ✅ |
| FR-05 多会话并发 | P-01 SessionListScreen (multi-card, real-time status) | ✅ |
| FR-06 流式渲染 | C-02 MessageBubble, C-04 MarkdownRenderer, C-03 ToolCallCard, P-02 Detail | ✅ |
| FR-07 断线恢复 | C-10 ConnectionBanner, P-04 Auto-scroll pattern, catch-up logic | ✅ |
| FR-08 FCM 推送 | P-08 Deep Link pattern | ✅ |
| FR-09 主题 | Foundation §Color/Theme, P-05 SettingsScreen theme toggle | ✅ |
| FR-10 审批保留 | 未做 UI（代码保留，默认关闭） | ⏭ 按设计跳过 |

## Non-Functional Requirements Coverage

| PRD Req | UI Spec Location | Status |
|---------|-----------------|--------|
| NFR-01 延迟 < 1s | P-02 streaming UX, P-06 Loading pattern | ✅ |
| NFR-03 Security | P-00 OnboardingScreen (token 输入), Foundation (no cleartext) | ✅ |
| NFR-04 Android API 26+ | Foundation (Material 3 compat) | ✅ |

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
| Approval Inbox UI | 默认 bypassPermissions | Phase 3+ |
| Diff/Patch 预览 | MVP 不做 | Phase 3+ |
| 多 host 管理 UI | 首版单 host | Phase 3+ |
| Web 版 | 非目标 | 无计划 |
| 动画减弱模式 | 单用户 | Phase 2+ |
