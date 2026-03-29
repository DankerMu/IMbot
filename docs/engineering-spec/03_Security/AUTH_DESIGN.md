# Security & Authentication Design

## Authentication: Static Bearer Token

### 机制

单一静态 token 用于所有认证场景：

```
Authorization: Bearer <RELAY_STATIC_TOKEN>
```

### Token 流转

| Client | How Token is Stored | How Token is Sent |
|--------|--------------------|--------------------|
| Android App | SharedPreferences (首次配置输入) | HTTP header / WS query param |
| Companion | 配置文件 `companion.json` | WS query param |
| OpenClaw bridge | relay 内部，无需外部传递 | N/A (localhost) |

### 验证逻辑

```typescript
// Fastify preHandler
function authGuard(request, reply) {
  const token = request.headers.authorization?.replace('Bearer ', '');
  if (token !== config.RELAY_STATIC_TOKEN) {
    reply.code(401).send({ error: 'unauthenticated' });
  }
}

// WebSocket
function wsAuthGuard(ws, request) {
  const token = new URL(request.url, 'http://localhost').searchParams.get('token');
  if (token !== config.RELAY_STATIC_TOKEN) {
    ws.close(4001, 'unauthenticated');
  }
}
```

### Token 安全要求

- Token 长度 ≥ 64 字符，建议 `openssl rand -base64 48` 生成。
- 不硬编码在 APK 中（Android 首次运行时用户手动输入，保存到 SharedPreferences）。
- 传输仅通过 HTTPS/WSS。
- Token 泄露时的补救：更换 relay 配置中的 token，三端同步更新。

## Network Security

### TLS

- 所有外部链路强制 HTTPS / WSS。
- relay VPS 使用 Let's Encrypt 证书（通过 nginx/caddy reverse proxy 或 Fastify 直接加载）。
- MVP 不做 certificate pinning（避免证书轮换事故）。

### Android Network Security Config

```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
  <base-config cleartextTrafficPermitted="false">
    <trust-anchors>
      <certificates src="system" />
    </trust-anchors>
  </base-config>
  <!-- Debug only: allow debug CA -->
  <debug-overrides>
    <trust-anchors>
      <certificates src="user" />
    </trust-anchors>
  </debug-overrides>
</network-security-config>
```

### Companion Security

- 仅出站连接，不开放入站端口。
- relay URL 和 token 配置在本地文件中。
- 本地 Claude API key 不上传到 relay。
- CLI 运行时的 cwd 受 workspace root allowlist 限制。

### OpenClaw Bridge Security

- localhost only（`ws://127.0.0.1:18789`）。
- OpenClaw gateway 的 pairing token 在 relay 本地配置。
- 不暴露给外部网络。

## Secret Boundary

```
┌─────────────────────────────────────────────┐
│              Android App                     │
│  Holds: RELAY_STATIC_TOKEN, FCM token        │
│  Does NOT hold: Claude API key, host secrets │
├─────────────────────────────────────────────┤
│              Relay VPS                        │
│  Holds: RELAY_STATIC_TOKEN, FCM creds,       │
│         OpenClaw pairing token               │
│  Does NOT hold: Claude API key               │
├─────────────────────────────────────────────┤
│              MacBook Companion               │
│  Holds: RELAY_STATIC_TOKEN, Claude API key,  │
│         local Git/dev credentials            │
│  Does NOT upload: API key, local secrets     │
└─────────────────────────────────────────────┘
```

## Audit (MVP Minimal)

写入 `audit_logs` 表的事件：

| Action | When |
|--------|------|
| `session.create` | 创建会话 |
| `session.resume` | 恢复会话 |
| `session.cancel` | 取消会话 |
| `session.delete` | 删除会话 |
| `host.online` | companion 上线 |
| `host.offline` | companion 离线 |
| `root.add` | 添加根目录 |
| `root.remove` | 移除根目录 |

MVP 阶段不做审计日志的 UI 或查询 API。
