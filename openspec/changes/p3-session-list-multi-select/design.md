# Design: Android 会话列表多选批量删除

## Interaction Model

### 进入选择模式

- 用户长按任意 `SessionCard`
- 页面进入 selection mode
- 当前长按的会话立即进入已选中状态
- 顶部区域从 provider filter 切换为批量操作栏

### 选择模式中的卡片行为

- 点击卡片：切换该会话的选中状态
- 再次长按卡片：等同于切换选中状态
- 左滑删除：禁用，避免与批量操作冲突
- 普通跳转详情：禁用，避免误入详情

### 批量操作栏

顶部栏展示：

- 已选数量
- `全选` / `取消全选`
- `删除`
- `完成`

### 批量删除

- 点击 `删除` 后弹出确认框
- 文案显示待删除会话数量
- Android 端顺序调用现有 `sessionRepository.deleteSession(sessionId)`
- 删除成功的会话由现有 Room/Flow 列表更新机制自动移除
- 若存在失败项，保留失败会话的选中状态，方便用户重试

## State Changes

`HomeUiState` 增加：

```kotlin
val selectedSessionIds: Set<String> = emptySet()
val isDeletingSelection: Boolean = false
```

并通过计算属性表示是否处于选择模式：

```kotlin
val isSelectionMode: Boolean get() = selectedSessionIds.isNotEmpty()
```

## Delete Strategy

本次不改 relay API，批量删除只在 Android 端组合已有单删能力：

```kotlin
selectedIds.forEach { id ->
    sessionRepository.deleteSession(id)
}
```

这样可以避免跨端协议变更，同时与当前服务端生命周期约束保持一致。

## File Changes

| File | Change |
|------|--------|
| `packages/android/app/src/main/kotlin/com/imbot/android/ui/home/HomeViewModel.kt` | 增加选择模式状态与批量删除逻辑 |
| `packages/android/app/src/main/kotlin/com/imbot/android/ui/home/HomeScreen.kt` | 顶部批量操作栏、批量删除确认框、选择模式联动 |
| `packages/android/app/src/main/kotlin/com/imbot/android/ui/home/SessionCard.kt` | 长按进入选择、多选视觉态、选择模式禁用 swipe delete |
| `packages/android/app/src/test/kotlin/com/imbot/android/ui/home/` | 补充选择逻辑与删除相关测试 |

## Constraints

- 不新增 bulk delete 后端接口
- 不改变现有单条左滑删除能力
- 会话列表排序和 provider filter 规则保持不变
