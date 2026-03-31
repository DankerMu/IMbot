# IMbot 端到端测试方案

## 环境信息

| 组件 | 地址/配置 |
|------|----------|
| Relay | `https://imbot.23-95-164-218.sslip.io` (VPS: 23.95.164.218:3000, pm2) |
| Companion | MacBook launchd `com.imbot.companion` (host_id: macbook-1) |
| Android | 模拟器 `emulator-5554` (IMbot_API_35, API 35) |
| Token | `c8892c7cba3b5392d9d450b55e47c81ac69cb629d8ec35cd765bd71770ff6ef2` |
| Providers | claude (已验证), book (已验证), openclaw (gateway handshake 失败，跳过) |
| ADB | `$HOME/Library/Android/sdk/platform-tools/adb` |
| Emulator | `$HOME/Library/Android/sdk/emulator/emulator` |

## 前置条件检查

每次测试前确认：

```bash
# 1. Relay 健康
curl -sf https://imbot.23-95-164-218.sslip.io/healthz | python3 -m json.tool
# 期望: {"status":"ok","db":"ok","companion":"online"}

# 2. Companion 在线
launchctl list | grep imbot
# 期望: PID + 0 + com.imbot.companion

# 3. 模拟器在线
$HOME/Library/Android/sdk/platform-tools/adb devices
# 期望: emulator-5554 device

# 4. App 已安装
$HOME/Library/Android/sdk/platform-tools/adb shell pm list packages | grep imbot
# 期望: package:com.imbot.android
```

如果模拟器未启动：
```bash
$HOME/Library/Android/sdk/emulator/emulator -avd IMbot_API_35 -no-snapshot-load &
sleep 30
```

如果 App 未安装：
```bash
cd /Users/danker/Desktop/AI-vault/IMbot/packages/android
./gradlew installDebug
```

## 测试用例

### T1: Onboarding 配置并进入主页

**目标**: 首次配置 relay 连接并进入会话列表

**步骤**:
1. 清除 app 数据: `adb shell pm clear com.imbot.android`
2. 启动 app: `adb shell am start -n com.imbot.android/.MainActivity`
3. 等待 3 秒, 截图确认显示 Onboarding 界面（有 "Relay URL" 和 "Token" 输入框）
4. 通过 uiautomator dump 获取输入框精确坐标:
   ```bash
   adb exec-out uiautomator dump /dev/tty 2>/dev/null | grep -oE 'text="(Relay URL|Token|测试连接|开始使用)"[^]]*bounds="\[[0-9,]*\]"'
   ```
5. 点击 Relay URL 输入框中心, 输入 `https://imbot.23-95-164-218.sslip.io`
6. 隐藏键盘 (keyevent 111)
7. 点击 Token 输入框中心, 输入 token
8. 隐藏键盘
9. 截图确认两个字段都有内容
10. 点击 "测试连接" 按钮
11. 等待 5 秒, 截图确认显示 "✓ 连接成功" + "MacBook: online"
12. 重新 dump UI 获取 "开始使用" 按钮坐标
13. 点击 "开始使用" 按钮
14. 等待 3 秒, 截图确认进入主页（应看到 "IMbot" 标题栏 + "暂无会话" 或会话列表）

**验收**: 截图显示主页界面（非 Onboarding、非桌面）

### T2: 创建 Claude Session 端到端

**目标**: 从 Android 创建一个 Claude session，验证整条链路

**前置**: T1 已完成（app 在主页）

**步骤**:
1. 截图确认在主页
2. 通过 uiautomator dump 找到 FAB (FloatingActionButton) 或 "新建会话" 按钮坐标, 点击
3. 等待 3 秒, 截图确认进入 NewSession 界面（应看到 Provider 选择）
4. dump UI, 找到 "Claude Code" 或 "claude" provider 选项, 点击
5. 截图确认进入目录选择步骤
6. 选择一个 workspace root (如果有列出的话), 或选择默认目录
7. 截图确认进入 Prompt 输入步骤
8. 输入简单 prompt: `echo hello`
9. 点击发送/创建按钮
10. 等待 5 秒, 截图确认进入 session detail 界面

**同时验证后端**:
```bash
# 检查 relay 是否收到 session
curl -sf -H "Authorization: Bearer c8892c7cba3b5392d9d450b55e47c81ac69cb629d8ec35cd765bd71770ff6ef2" \
  https://imbot.23-95-164-218.sslip.io/v1/sessions | python3 -m json.tool
# 期望: 至少 1 个 session, status 为 "queued" 或 "running"

# 检查 companion 日志
tail -20 ~/.imbot/logs/companion.log
# 期望: 看到 create_session 命令
```

11. 等待 10-30 秒让 Claude 执行
12. 截图确认 session detail 界面有 assistant 消息或 tool call
13. 等 session 完成 (status → completed)

**验收**:
- Relay 有一个 status=completed 的 session
- Android detail 界面显示 assistant 回复
- Companion 日志显示 CLI 进程启动和退出

### T3: Session 列表显示和刷新

**目标**: 验证主页 session 列表正确显示

**前置**: T2 已完成（至少有 1 个 session）

**步骤**:
1. 按返回键 (keyevent 4) 回到主页
2. 截图确认 session 列表显示（非 "暂无会话"）
3. 下拉刷新（swipe down: `adb shell input swipe 540 800 540 1600 300`）
4. 等待 2 秒, 截图确认列表刷新完成
5. 验证 session card 显示: provider icon, prompt 摘要, 状态

**验收**: 主页显示至少 1 个 session card

### T4: Session Detail 查看

**目标**: 验证 session 详情时间线正确渲染

**步骤**:
1. 点击某个 session card 进入详情
2. 等待 3 秒, 截图确认 timeline 渲染（消息气泡、tool call 等）
3. 滚动查看历史 (`adb shell input swipe 540 1600 540 400 300`)
4. 截图确认无崩溃

**验收**: Detail 界面正常渲染，无空白、无崩溃

### T5: 发送追加消息 (Resume)

**目标**: 对已完成 session 发送追加消息验证 resume 功能

**前置**: T2 已完成，session 已 completed

**步骤**:
1. 进入已完成 session 的 detail 界面
2. 在底部输入框输入: `what did you just do?`
3. 点击发送按钮
4. 等待 5 秒
5. 截图确认 session 重新变为 running, 有新的 assistant 回复

**同时验证**:
```bash
# Session 状态应该从 completed → running → completed
curl -sf -H "Authorization: Bearer <token>" \
  https://imbot.23-95-164-218.sslip.io/v1/sessions/<session-id> | python3 -c "
import json,sys; s=json.load(sys.stdin); print(f'status={s[\"status\"]}')"
```

**验收**: Session resume 成功，新消息出现在时间线

### T6: Workspace 界面

**目标**: 验证 workspace 目录浏览功能

**步骤**:
1. 从主页导航到 Workspace tab（底部导航）
2. 截图确认显示 host 信息和 workspace roots
3. 点击某个 root 进入目录浏览
4. 截图确认目录列表正确渲染
5. 点击某个子目录，验证导航

**验收**: Workspace 界面正常显示目录，可导航

### T7: Settings 界面

**目标**: 验证设置界面显示和编辑

**步骤**:
1. 从主页导航到 Settings tab
2. 截图确认显示 Relay URL, Token (masked), Theme 选项
3. 验证当前 relay URL 和 token 与配置一致

**验收**: Settings 界面正常显示，信息正确

### T8: 错误状态测试

**目标**: 验证 relay 不可达时的 error banner

**步骤**:
1. 临时停止 relay: `ssh root@23.95.164.218 "pm2 stop imbot-relay"`
2. 在 Android 等待 30-60 秒（等 ping timeout）
3. 截图确认显示红色 ConnectionBanner "无法连接服务器"
4. 恢复 relay: `ssh root@23.95.164.218 "pm2 start imbot-relay"`
5. 等待重连
6. 截图确认 banner 消失或显示绿色 "已恢复"

**验收**: Error banner 正确显示和恢复

### T9: Companion 离线测试

**目标**: 验证 companion 离线时的 host offline banner

**步骤**:
1. 临时停止 companion: `launchctl unload ~/Library/LaunchAgents/com.imbot.companion.plist`
2. 等待 relay 检测到 companion offline（90秒内）
3. 在 Android workspace 界面截图确认显示橙色 "MacBook 离线" banner
4. 恢复 companion: `launchctl load ~/Library/LaunchAgents/com.imbot.companion.plist`
5. 等待恢复
6. 截图确认 banner 消失

**验收**: Host offline banner 正确显示和恢复

### T10: book Provider Session (可选)

**目标**: 验证 book provider 端到端

**步骤**: 同 T2, 但选择 "book" provider, prompt 改为 `简单测试一下`

**验收**: book session 创建成功, 有回复

## 工具函数

测试中复用的 adb 操作：

```bash
ADB=$HOME/Library/Android/sdk/platform-tools/adb

# 截图并保存
take_screenshot() { $ADB exec-out screencap -p > "/tmp/imbot-e2e-$1.png"; }

# 获取 UI 元素坐标 (返回 center x,y)
find_element() {
  local text="$1"
  $ADB exec-out uiautomator dump /dev/tty 2>/dev/null | \
    grep -oE "text=\"${text}\"[^]]*bounds=\"\\[([0-9]+),([0-9]+)\\]\\[([0-9]+),([0-9]+)\\]\"" | \
    python3 -c "
import re,sys
m=re.search(r'bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', sys.stdin.read())
if m: print(f'{(int(m[1])+int(m[3]))//2},{(int(m[2])+int(m[4]))//2}')
"
}

# 点击文本元素
tap_text() {
  local coords=$(find_element "$1")
  if [[ -n "$coords" ]]; then
    $ADB shell input tap ${coords//,/ }
  else
    echo "Element '$1' not found" >&2
    return 1
  fi
}

# 输入文本到当前焦点
type_text() { $ADB shell input text "$1"; }

# 隐藏键盘
hide_keyboard() { $ADB shell input keyevent 111; }

# 返回
go_back() { $ADB shell input keyevent 4; }

# Relay API 调用
relay_api() {
  local path="$1"
  curl -sf -H "Authorization: Bearer c8892c7cba3b5392d9d450b55e47c81ac69cb629d8ec35cd765bd71770ff6ef2" \
    "https://imbot.23-95-164-218.sslip.io/v1${path}"
}
```

## 测试执行顺序

必须按顺序执行: T1 → T2 → T3 → T4 → T5 → T6 → T7

独立测试: T8, T9 (可在任意时间点执行，但需要 T1 先完成)

可选: T10

## 注意事项

1. **adb input text 特殊字符**: URL 中的 `://` 和 `.` 可以正常输入, 但 `&` 等需要转义
2. **键盘遮挡**: 输入文本后必须 `keyevent 111` 隐藏键盘, 否则下方元素点不到
3. **uiautomator dump**: 每次 UI 变化后需要重新 dump 获取最新坐标
4. **等待时间**: session 创建后 Claude CLI 需要 10-30 秒执行, 不要太快截图
5. **Companion 日志**: `~/.imbot/logs/companion.log` 记录所有命令, 用于 debug
6. **Relay 日志**: `ssh root@23.95.164.218 "pm2 logs imbot-relay --nostream --lines 30"`
7. **模拟器分辨率**: 1080x2400, 所有坐标基于此分辨率
8. **App 回到桌面**: 如果 app 意外退回桌面, 用 `adb shell am start -n com.imbot.android/.MainActivity` 重启
