# Page: SettingsScreen

## 概述

| Key | Value |
|-----|-------|
| Route | Bottom Nav tab 3 |
| ViewModel | `SettingsViewModel` |
| PRD ref | FR-09 |

## 布局

```
┌──────────────────────────────────────┐
│  设置                                │
├──────────────────────────────────────┤
│                                      │
│  连接                                │  ← Section
│  ┌──────────────────────────────┐    │
│  │ Relay URL                     │    │
│  │ https://relay.example.com     │    │  ← tap 编辑
│  ├──────────────────────────────┤    │
│  │ 连接状态           已连接 ● │    │  ← 实时状态
│  ├──────────────────────────────┤    │
│  │ MacBook            在线 ●   │    │
│  ├──────────────────────────────┤    │
│  │ OpenClaw            在线 ●   │    │
│  └──────────────────────────────┘    │
│                                      │
│  外观                                │  ← Section
│  ┌──────────────────────────────┐    │
│  │ 主题                         │    │
│  │ ○ 跟随系统  ○ 浅色  ○ 深色  │    │  ← RadioGroup
│  └──────────────────────────────┘    │
│                                      │
│  数据                                │  ← Section
│  ┌──────────────────────────────┐    │
│  │ 清除本地缓存                  │    │  ← 确认弹窗
│  ├──────────────────────────────┤    │
│  │ 会话保留天数          30 天   │    │  ← 只读展示
│  └──────────────────────────────┘    │
│                                      │
│  关于                                │  ← Section
│  ┌──────────────────────────────┐    │
│  │ 版本               1.0.0     │    │
│  └──────────────────────────────┘    │
│                                      │
├──────────────────────────────────────┤
│  🏠 会话  │  📁 目录  │  ⚙ 设置      │
└──────────────────────────────────────┘
```

## 数据契约

```kotlin
data class SettingsUiState(
    val relayUrl: String = "",
    val isConnected: Boolean = false,
    val macbookOnline: Boolean = false,
    val openclawOnline: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val version: String = ""
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }
```

## 交互

| Action | Behavior |
|--------|----------|
| Tap Relay URL | 弹出 EditDialog，修改后保存并重连 |
| 切换主题 | 即时生效，cross-fade 过渡 |
| 清除缓存 | 确认 Dialog → 清除 Room DB → Snackbar "已清除" |

## 验收标准

- [ ] 连接状态实时更新。
- [ ] 主题切换即时生效无闪烁。
- [ ] Relay URL 可编辑并重连。
- [ ] 清除缓存有确认步骤。
