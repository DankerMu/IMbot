# IMbot UI/UX 研发规格总纲

**Platform**: Android (API 26+)
**Stack**: Kotlin · Jetpack Compose · Material 3 · Material You
**PRD Source**: `docs/PRD.md` v1.0

## 约束

- 单用户产品，无注册/登录流程（首次配置 relay URL + token 即可）。
- 三种 Provider 平级：Claude Code / book / OpenClaw。
- 主题：System / Light / Dark，默认跟随系统。
- Markdown + 语法高亮渲染。
- 动画：Shared element transition、fade-in、color morph，目标 60fps。
- 无 Web 版，纯 Android 原生。

## 能力地图

```
┌────────────────────────────────────────────────────────────┐
│                     IMbot Android App                       │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  ┌──────────┐  ┌──────────────┐  ┌────────────────────┐   │
│  │ 首页      │  │ 会话详情      │  │ 新建会话            │   │
│  │ Session   │  │ Session      │  │ New Session        │   │
│  │ List      │  │ Detail       │  │ Flow               │   │
│  └──────────┘  └──────────────┘  └────────────────────┘   │
│                                                            │
│  ┌──────────┐  ┌──────────────┐  ┌────────────────────┐   │
│  │ Workspace │  │ Settings     │  │ 首次配置            │   │
│  │ Manager   │  │              │  │ Onboarding         │   │
│  └──────────┘  └──────────────┘  └────────────────────┘   │
│                                                            │
├────────────────────────────────────────────────────────────┤
│  Components: SessionCard · MessageBubble · ToolCallCard    │
│  MarkdownRenderer · DirectoryBrowser · ProviderChip        │
│  StatusIndicator · InputBar · EmptyState                   │
└────────────────────────────────────────────────────────────┘
```

## 导航结构

```
App Launch
    │
    ├── (首次) → OnboardingScreen (配置 relay URL + token)
    │
    └── (正常) → SessionListScreen (Home, Bottom Nav item 1)
                    │
                    ├── tap session card → SessionDetailScreen
                    │
                    ├── FAB (+) → NewSessionScreen
                    │       ├── Step 1: ProviderPicker
                    │       ├── Step 2: DirectoryBrowser
                    │       └── Step 3: PromptInput
                    │
                    ├── Bottom Nav item 2 → WorkspaceScreen
                    │
                    └── Bottom Nav item 3 → SettingsScreen
```

## 文档索引

| File | Description |
|------|-------------|
| [01_Foundation/FOUNDATION.md](01_Foundation/FOUNDATION.md) | 设计 tokens、主题、排版、动画基线 |
| [02_Components/](02_Components/) | 可复用组件规格 |
| [03_Patterns/PATTERNS.md](03_Patterns/PATTERNS.md) | 跨页面交互模式 |
| [04_Pages/](04_Pages/) | 每页复刻级规格 |
| [05_A11y/A11Y.md](05_A11y/A11Y.md) | 无障碍汇总 |
| [00_SourceInventory/COVERAGE.md](00_SourceInventory/COVERAGE.md) | PRD → UI 覆盖映射 |
