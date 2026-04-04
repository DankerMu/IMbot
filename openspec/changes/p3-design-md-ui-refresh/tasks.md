# Tasks: Design.md UI Refresh

## T1: Design Token Refresh

- [x] 更新 `ui/theme/Color.kt`，切换为暖中性色 light/dark palette
- [x] 更新 `ui/theme/IMbotTheme.kt`，重组 surface / outline / container 关系
- [x] 更新 `ui/theme/Type.kt`，强化 headline/title/meta 层级
- [x] 更新 `ui/theme/Shape.kt` 与 `ThemePrimitives.kt`，统一容器圆角和 light-card depth

## T2: Home Refresh

- [x] 重构 `HomeScreen` 顶部标题区与筛选条
- [x] 为 Running / Recent 增加 section label
- [x] 重构 `SessionCard` 信息层级与状态表达

## T3: New Session Flow Refresh

- [x] 重构 `NewSessionScreen` 顶栏、step indicator、底部导航条
- [x] 重构 `ProviderPickerStep`
- [x] 重构 `DirectoryBrowserStep` 与共享 `DirectoryBrowser`
- [x] 重构 `PromptInputStep`

## T4: Detail Shell Refresh

- [x] 重构 `SessionDetailScreen` 顶栏标题结构与顶部 chrome
- [x] 保持状态 badge / usage / provider 信息清晰共存
- [x] 精修 `WorkspaceScreen` / `SettingsScreen` 的 editorial shell 与 summary pills
- [x] 修复 detail 页面软键盘弹起时的顶部状态栏遮挡问题

## T5: Spec Alignment

- [x] 更新 `openspec/README.md`
- [x] 更新 `docs/engineering-spec/01_Requirements/REQUIREMENTS_MATRIX.md`
- [x] 更新 `docs/specs/ui-ux-rd-spec/01_Foundation/FOUNDATION.md`
- [x] 更新相关 page docs

## T6: Verification

- [x] 运行 Android unit tests
- [x] 构建并安装 debug APK 到模拟器
- [x] 手动检查 Home / Workspace / Settings / Detail 关键路径视觉与功能
