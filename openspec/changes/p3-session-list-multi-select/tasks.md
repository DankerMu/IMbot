# Tasks: Android 会话列表多选批量删除

## 1. Spec

- [ ] 新增 `p3-session-list-multi-select` OpenSpec change
- [ ] 更新会话列表与 SessionCard 的 UI/UX 规格
- [ ] 更新 `openspec/README.md` 变更索引

## 2. ViewModel

- [ ] `HomeUiState` 增加 `selectedSessionIds` 和 `isDeletingSelection`
- [ ] 增加 `enterSelectionMode(sessionId)`
- [ ] 增加 `toggleSessionSelection(sessionId)`
- [ ] 增加 `clearSelection()`
- [ ] 增加 `toggleSelectAllVisibleSessions()`
- [ ] 增加 `deleteSelectedSessions()`
- [ ] 会话列表刷新后自动清理已不存在的选中项

## 3. UI

- [ ] 长按 `SessionCard` 进入选择模式
- [ ] 选择模式下卡片显示已选视觉态与勾选指示
- [ ] 顶部区域切换为批量操作栏
- [ ] 选择模式下禁用 FAB
- [ ] 选择模式下禁用 swipe-to-delete
- [ ] 批量删除确认弹窗展示选中数量

## 4. Tests

- [ ] 补选择模式的纯逻辑测试
- [ ] 补全选/取消全选逻辑测试
- [ ] 补批量删除后选中集收敛逻辑测试
- [ ] 跑 Home 相关单测与 Android 构建验证
