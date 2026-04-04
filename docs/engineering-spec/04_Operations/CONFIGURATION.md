# Configuration

## Relay Server

| Key | Type | Required | Default | Description |
|-----|------|----------|---------|-------------|
| `RELAY_PORT` | number | No | `3000` | HTTP/WS listen port |
| `RELAY_HOST` | string | No | `0.0.0.0` | Listen address |
| `RELAY_STATIC_TOKEN` | string | **Yes** | — | Bearer token for all auth |
| `RELAY_DB_PATH` | string | No | `./data/imbot.db` | SQLite file path |
| `RELAY_FCM_PROJECT_ID` | string | No | — | Firebase project ID (空则禁用 push) |
| `RELAY_FCM_SERVICE_ACCOUNT` | string | No | — | Firebase service account JSON path |
| `RELAY_OPENCLAW_URL` | string | No | `ws://127.0.0.1:18789` | OpenClaw gateway WS URL |
| `RELAY_OPENCLAW_TOKEN` | string | No | — | OpenClaw gateway auth token |
| `RELAY_LOG_LEVEL` | string | No | `info` | `debug \| info \| warn \| error` |
| `RELAY_COMPANION_TIMEOUT_MS` | number | No | `30000` | Companion command ack timeout |
| `RELAY_HEARTBEAT_INTERVAL_MS` | number | No | `60000` | Host heartbeat check interval |
| `RELAY_HEARTBEAT_STALE_MS` | number | No | `90000` | Heartbeat 过期阈值 |
| `RELAY_PURGE_DAYS` | number | No | `30` | 不活跃 session 保留天数 |
| `RELAY_WS_PING_INTERVAL_MS` | number | No | `30000` | WS ping 间隔 |

**启动失败条件**：`RELAY_STATIC_TOKEN` 未设置时，relay 必须拒绝启动。

### 配置加载顺序

1. 环境变量
2. `.env` 文件（dotenv）
3. 硬编码默认值

## MacBook Companion

配置文件：`~/.imbot/companion.json`

```json
{
  "relay_url": "wss://your-relay.example.com/v1/companion",
  "token": "<RELAY_STATIC_TOKEN>",
  "host_id": "macbook-1",
  "host_name": "MacBook Pro",
  "providers": {
    "claude": {
      "binary": "claude",
      "enabled": true
    },
    "book": {
      "binary": "book",
      "enabled": true
    }
  },
  "workspace_roots": [
    {
      "id": "uuid-1",
      "provider": "claude",
      "path": "/Users/danker/Desktop/AI-vault",
      "label": "AI-vault"
    },
    {
      "id": "uuid-2",
      "provider": "book",
      "path": "/Users/danker/Desktop/novel",
      "label": "novel"
    }
  ],
  "log_dir": "~/.imbot/logs",
  "log_level": "info",
  "heartbeat_interval_ms": 30000,
  "reconnect_max_delay_ms": 30000
}
```

**运行时修改**：`workspace_roots` 可通过 relay API 动态增删，companion 同步更新本地 JSON。

- `providers.*.binary` 可使用绝对路径，或保留 `claude` / `book` 这样的命令名；命令名会先按进程 `PATH` 查找，再补充常见 macOS 用户目录（`~/.local/bin`、`~/bin`、`/opt/homebrew/bin`、`/usr/local/bin`）。
- `idle_timeout_ms` 为 companion 的 idle session 自动结束阈值，默认 `1800000`（30 分钟）；它只在 session 已进入 `idle` 后生效。
- `interactive_tool_timeout_ms` 为交互工具等待超时，默认无限等待以对齐本机 `claude` / `book` CLI 行为；显式设置为正整数时才启用，设置为 `0` 等同于禁用超时。
- 兼容旧版 `workspace_roots`：如果条目缺少 `added_at`，companion 仍会加载，并在下次持久化时补齐该字段。
- `./scripts/install-companion.sh` 会把当前 shell 的关键运行环境写入 launchd plist（完整 `PATH`、代理变量、`NODE_EXTRA_CA_CERTS`，以及 Claude CLI 认证所需的基础用户终端变量），避免后台 companion 进程与交互 shell 的出站网络/认证行为不一致。

## Android App

首次启动配置（Settings screen）：

| Setting | Storage | Description |
|---------|---------|-------------|
| Relay URL | SharedPreferences | `https://your-relay.example.com` |
| Static Token | SharedPreferences | Bearer token |
| Theme | SharedPreferences | `system \| light \| dark` |

**BuildConfig 注入**（build.gradle.kts）：

```kotlin
buildConfigField("String", "DEFAULT_RELAY_URL", "\"\"")
buildConfigField("Int", "MIN_SDK", "26")
```

## launchd Plist (Companion)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "...">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>com.imbot.companion</string>
  <key>ProgramArguments</key>
  <array>
    <string>/usr/local/bin/node</string>
    <string>/Users/danker/.imbot/companion/dist/index.js</string>
  </array>
  <key>RunAtLoad</key>
  <true/>
  <key>KeepAlive</key>
  <true/>
  <key>StandardOutPath</key>
  <string>/Users/danker/.imbot/logs/companion.log</string>
  <key>StandardErrorPath</key>
  <string>/Users/danker/.imbot/logs/companion.error.log</string>
  <key>EnvironmentVariables</key>
  <dict>
    <key>NODE_ENV</key>
    <string>production</string>
  </dict>
</dict>
</plist>
```
