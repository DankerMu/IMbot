# 跨页面交互模式

## P-01: Pull-to-Refresh

适用页面：SessionListScreen、WorkspaceScreen。

- 使用 Material 3 `PullToRefreshBox`。
- 下拉触发刷新 → 重新请求 API。
- 刷新期间显示 indicator，完成后自动隐藏。
- 刷新失败：Snackbar 提示 + 保留旧数据。

## P-02: Swipe Actions

适用：SessionCard。

- 左滑 reveal delete action（红色背景 + 删除图标）。
- 松手后弹出确认 Dialog："确认归档此会话？"
- 确认 → 调用 API 删除 → 列表动画移除。

## P-03: Shared Element Transition

适用：SessionCard → SessionDetailScreen。

```kotlin
// Session card 中
SharedTransitionLayout {
    SessionCard(
        modifier = Modifier.sharedElement(
            state = rememberSharedContentState("session-${session.id}"),
            animatedVisibilityScope = this
        )
    )
}
```

- Card 的 provider icon + title 过渡到 Detail 的 TopAppBar。
- Duration: 400ms, Emphasized easing。

## P-04: Auto-Scroll + Manual Override

适用：SessionDetailScreen 消息流。

### 规则

| User Action | Behavior |
|-------------|----------|
| 在底部 | 新消息自动滚动到底 |
| 手动上滑 > 100dp | 暂停自动滚动，显示"↓ 回到底部" FAB |
| 点击"回到底部" | 平滑滚动到底 + 恢复自动滚动 |
| 新消息到达且已暂停 | 不滚动，"↓" FAB 显示未读消息数 |

## P-05: Error Handling Pattern

三层故障的 UI 表达：

| Fault Layer | UI Element | Color | Message |
|-------------|-----------|-------|---------|
| `relay_unreachable` | ConnectionBanner | Red | "无法连接服务器" |
| `host_offline` | Inline banner (page level) | Orange | "MacBook 离线" |
| `provider_unreachable` | Inline banner (session level) | Orange | "Claude upstream 不可用" |
| `command_timeout` | Snackbar | Red | "命令超时，请重试" |
| `network_error` | ConnectionBanner | Yellow | "网络不稳定，正在重连..." |

### Error 优先级

ConnectionBanner > Page-level banner > Session-level banner > Snackbar。
高优先级错误覆盖低优先级。

## P-06: Loading Pattern

| Type | Component | Usage |
|------|-----------|-------|
| 首次加载 | Shimmer (skeleton) | Session list、Directory list |
| 操作中 | Button loading state | 创建会话按钮 |
| 流式中 | Cursor blink `▊` | Message bubble |
| 后台同步 | 无可见指示 | Catch-up events |
| 重连中 | ConnectionBanner + spinner | 全局 |

## P-07: Bottom Navigation

```
┌──────────┬──────────┬──────────┐
│  🏠 会话  │  📁 目录  │  ⚙ 设置  │
└──────────┴──────────┴──────────┘
```

- 3 个 tab，使用 Material 3 `NavigationBar`。
- 选中 tab：filled icon + label。
- 未选中：outlined icon + label。
- Session list 上有 running session 时，tab 上显示小圆点。

## P-08: Deep Link

FCM 通知点击的 deep link scheme：

```
imbot://session/{sessionId}
```

- 打开 App → 直接导航到 SessionDetailScreen。
- 如果 App 已在前台：navigate 到对应 session（不重建 stack）。

## P-09: Confirmation Pattern

需要确认的操作：

| Action | Confirmation |
|--------|-------------|
| 删除 session | AlertDialog: "确认删除此会话？此操作不可撤销。" |
| 取消 running session | AlertDialog: "确认取消此会话？" |
| 移除 workspace root | AlertDialog: "确认移除此根目录？已有会话不受影响。" |
| 清除本地缓存 | AlertDialog: "确认清除本地缓存？" |

不需要确认的操作：创建 session、恢复 session、发送消息、切换主题。
