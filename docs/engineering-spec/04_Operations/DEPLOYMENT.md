# Deployment

## Relay Server (RackNerd VPS)

### 环境

- OS: Ubuntu 22.04+ 或 Debian 12+
- Node.js: 22 LTS
- 反向代理: Caddy（自动 HTTPS / Let's Encrypt）
- 进程管理: pm2

### 部署步骤

```bash
# 1. 安装依赖
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo bash -
sudo apt install -y nodejs
sudo npm install -g pm2

# 2. 安装 Caddy
sudo apt install -y caddy

# 3. 部署 relay
mkdir -p /opt/imbot/relay
cd /opt/imbot/relay
# 拷贝构建产物到此目录
npm install --production

# 4. 配置环境变量
cat > .env << 'EOF'
RELAY_STATIC_TOKEN=<generated-token>
RELAY_DB_PATH=/opt/imbot/data/imbot.db
RELAY_FCM_PROJECT_ID=<project-id>
RELAY_FCM_SERVICE_ACCOUNT=/opt/imbot/fcm-sa.json
RELAY_LOG_LEVEL=info
EOF

# 5. 启动
pm2 start dist/index.js --name imbot-relay
pm2 save
pm2 startup
```

### Caddy 配置

```
your-relay.example.com {
  reverse_proxy localhost:3000
}
```

### SQLite 数据目录

```
/opt/imbot/
├── relay/           # 应用代码
│   ├── dist/
│   ├── node_modules/
│   └── .env
├── data/
│   └── imbot.db     # SQLite 数据文件
└── fcm-sa.json      # Firebase service account
```

### 备份

```bash
# SQLite 备份（每天一次，cron）
sqlite3 /opt/imbot/data/imbot.db ".backup /opt/imbot/backup/imbot-$(date +%Y%m%d).db"
# 保留最近 7 天
find /opt/imbot/backup -name "*.db" -mtime +7 -delete
```

## OpenClaw Gateway (同一 VPS)

```bash
# 安装 OpenClaw
npm install -g openclaw@latest

# 运行 gateway
pm2 start openclaw -- gateway --port 18789
```

## MacBook Companion

### 安装

```bash
# 1. 构建 companion
cd packages/companion
npm run build

# 2. 部署到本地
mkdir -p ~/.imbot/companion
cp -r dist/ ~/.imbot/companion/
cp node_modules/ ~/.imbot/companion/ # 或用 bundled build

# 3. 创建配置
cat > ~/.imbot/companion.json << 'EOF'
{
  "relay_url": "wss://your-relay.example.com/v1/companion",
  "token": "<RELAY_STATIC_TOKEN>",
  "host_id": "macbook-1",
  "host_name": "MacBook Pro",
  ...
}
EOF

# 4. 安装 launchd service
cp com.imbot.companion.plist ~/Library/LaunchAgents/
launchctl load ~/Library/LaunchAgents/com.imbot.companion.plist
```

### 日志

```
~/.imbot/logs/
├── companion.log
└── companion.error.log
```

## Android App

### 构建

```bash
cd packages/android
./gradlew assembleRelease
```

### 安装

- 直接 `adb install` 或通过内部分发。
- 首次启动后在 Settings 中配置 Relay URL 和 Token。

### 签名

- 使用本地 keystore（不上传到 Git）。
- debug build 使用 debug keystore。

## Health Check

### Relay

```bash
curl https://your-relay.example.com/healthz
# {"status":"ok","uptime":86400,"db":"ok","companion":"online","openclaw":"online"}
```

### Companion

- 通过 relay `/v1/hosts` 查看 MacBook companion 在线状态。
- 本地日志检查：`tail -f ~/.imbot/logs/companion.log`

## Rollback

- relay: `pm2 stop imbot-relay` → 恢复旧版本文件 → `pm2 start`
- companion: `launchctl unload` → 恢复旧文件 → `launchctl load`
- SQLite: 从备份恢复 `cp backup.db data/imbot.db`
