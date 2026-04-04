# Design: Design.md UI Refresh

## Summary

这次刷新不是新增页面，而是对现有 Android 主路径界面做一次统一的视觉系统收口。实现方式保持 Jetpack Compose + Material 3，但将 token、container、typography 和 shell 组合方式切换为更强设计约束的系统。

## Reference Extraction

从 `awesome-design-md` 中抽取的不是站点外观，而是以下可复用规则：

- `Claude`: warm neutral surfaces, editorial calmness, soft rings
- `Linear`: compact pills, subtle dark layering, precise density
- `Vercel`: built cards, shadow-as-border, restrained monochrome hierarchy
- `Warp`: warm dark mode, calm contrast, understated chrome
- `VoltAgent`: developer-native emphasis, monospace credibility, signal accents

## Token Strategy

### Color

- Light background 从冷灰改为暖纸色
- Light surface 使用象牙色而不是纯白
- Dark surface 采用带暖度的炭黑，不用蓝灰黑
- Outline 使用更可见但更柔和的暖灰线
- Provider 色保留独立身份，但整体饱和度收束

### Typography

- Headline 使用更紧的负字距和更短 line-height
- Title 与 Body 差异加大，避免“全部像正文”
- Label / Meta 明确压缩，适合状态 pill、路径标签、时间信息
- 代码与路径继续使用 monospace 作为开发者语义强调

### Shape / Depth

- Card radius 统一提升到更“container-first”的 20dp 左右
- Button 从纯 pill 改为更克制的 rounded-rect
- Light mode 卡片采用“轻阴影 + 细描边”
- Dark mode 容器主要依靠边界线和 surface 差异，不依赖大阴影

## Page Strategy

### Home

- 标题区改为“eyebrow + title + session summary”
- Provider filter 改为更紧凑的 segmented pills
- 列表按 Running / Recent 分节显示
- SessionCard 从“provider title + prompt subtitle”改为“provider/meta header + prompt title + workspace footer”

### New Session

- 顶部改为更强阶段感的启动流
- Step indicator 改为 stage-aware 的 capsule 式结构
- 每一步都在统一的 stage 容器内呈现
- 底部导航按钮改为固定容器，主次层级更明显

### Detail

- 顶栏标题结构重组为 provider 身份 + workspace 主标题 + 路径/usage 元信息
- 状态 badge 保持在右上角，但融入整体 chrome
- 不改消息区行为，只改 shell 和信息组织

### Bottom Navigation

- 统一为带边界的 surface 容器
- 选中态强调 indicator 与文字，而不是默认 Material 背板

## Docs Alignment

这次改动同时更新：

- `docs/engineering-spec/01_Requirements/REQUIREMENTS_MATRIX.md`
- `docs/specs/ui-ux-rd-spec/01_Foundation/FOUNDATION.md`
- `docs/specs/ui-ux-rd-spec/04_Pages/01_SessionListScreen.md`
- `docs/specs/ui-ux-rd-spec/04_Pages/02_SessionDetailScreen.md`
- `docs/specs/ui-ux-rd-spec/04_Pages/03_NewSessionScreen.md`

以保证 spec-first workflow 中的视觉基线和实际实现一致。
