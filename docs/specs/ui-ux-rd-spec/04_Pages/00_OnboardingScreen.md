# Page: OnboardingScreen

## 概述

| Key | Value |
|-----|-------|
| Route | `/onboarding` (首次启动) |
| ViewModel | `OnboardingViewModel` |
| PRD ref | NFR-03 |

首次启动时配置 relay 连接信息。

## 触发条件

App 启动时检查 SharedPreferences 中是否有 relay URL + token。如果没有 → 跳转 Onboarding。

## 布局

```
┌──────────────────────────────────────┐
│                                      │
│             🤖                       │
│           IMbot                      │
│                                      │
│  连接你的 Relay 服务器                │
│                                      │
│  ┌─────────────────────────────┐     │
│  │ Relay URL                   │     │
│  │ https://                    │     │
│  └─────────────────────────────┘     │
│                                      │
│  ┌─────────────────────────────┐     │
│  │ Token                       │     │
│  │ ••••••••                    │     │  ← password field
│  └─────────────────────────────┘     │
│                                      │
│         [ 测试连接 ]                 │  ← 先测试
│                                      │
│  ✓ 连接成功！Relay v1.0              │  ← 测试结果
│    MacBook: online                   │
│    OpenClaw: online                  │
│                                      │
│         [ 开始使用 → ]               │  ← 测试通过后启用
│                                      │
└──────────────────────────────────────┘
```

## 状态机

```
Input → tap "测试连接"
    │
    ▼
Testing (loading spinner on button)
    │
    ├─ success → 显示连接信息 + 启用"开始使用"
    └─ error → 显示错误（URL 不可达 / token 无效）
    │
    ▼ tap "开始使用"
Save to SharedPreferences → navigate to SessionListScreen
```

## 验收标准

- [ ] 首次启动必须经过 Onboarding。
- [ ] 测试连接失败有明确错误提示。
- [ ] 测试成功后显示 host 状态。
- [ ] 保存后不再显示 Onboarding。
- [ ] Token 输入为密码模式。
