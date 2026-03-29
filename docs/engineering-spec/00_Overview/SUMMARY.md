# IMbot Engineering Overview

## System Identity

IMbot 是一个三端系统（Android App + Relay Server + MacBook Companion），通过自建云端 relay 远程控制 MacBook 本地 Claude Code / book 和 relay 本机 OpenClaw gateway，提供多 provider 统一移动控制台。

## Decision Log

| ID | Decision | Options Considered | Chosen | Rationale |
|----|----------|-------------------|--------|-----------|
| D-001 | Database | PostgreSQL, SQLite | SQLite | 单用户、VPS 2C/3.5G、内存敏感 |
| D-002 | WebSocket | Socket.IO, native ws | native ws | 无 room 需求、减少依赖、OkHttp 原生支持 |
| D-003 | Auth | QR device bind, JWT, static token | Static token | 单用户、最简、安全性靠 HTTPS |
| D-004 | Runtime | Agent SDK, CLI wrapper | Claude Code CLI (`--output-format stream-json`) | 直接控制本地 claude/book CLI 进程，stream-json 输出格式提供结构化事件流 |
| D-005 | Approval | Full approval UI, bypass | Bypass default + code retained | 手机审批不便、配置可开启 |
| D-006 | Android UI | XML Views, Compose | Jetpack Compose + Material 3 | 现代、声明式、主题系统完善 |
| D-007 | Relay framework | Express, Fastify, Hono | Fastify | 高性能、TypeScript 原生、插件体系好 |
| D-008 | OpenClaw integration | SDK embed, gateway WS | Gateway WS (localhost) | OpenClaw 独立进程、协议隔离 |
| D-009 | Event sequencing | Global seq, per-session seq | Per-session monotonic seq | 简单、断线补拉直观 |
| D-010 | Companion daemon | launchd, pm2, systemd-like | launchd (macOS native) | MacBook 原生、开机自启 |

## Tech Stack Summary

```
┌──────────────────────────────────────────────────────┐
│                    Android App                        │
│  Kotlin · Jetpack Compose · Material 3 · Room         │
│  OkHttp · Coroutines/Flow · WorkManager · FCM         │
├──────────────────────────────────────────────────────┤
│                    Relay Server                        │
│  TypeScript · Fastify · ws · better-sqlite3           │
│  node-cron (cleanup) · firebase-admin (FCM)           │
├──────────────────────────────────────────────────────┤
│                  MacBook Companion                     │
│  TypeScript · ws · claude/book CLI process             │
│  launchd · chokidar (optional dir watch)              │
├──────────────────────────────────────────────────────┤
│                  OpenClaw Gateway                      │
│  独立进程 · ws://localhost:18789                       │
│  relay 通过 OpenClaw bridge 适配                       │
└──────────────────────────────────────────────────────┘
```

## Monorepo Structure

```
IMbot/
├── packages/
│   ├── relay/                 # Relay server
│   │   ├── src/
│   │   │   ├── index.ts       # Entry point
│   │   │   ├── app.ts         # Fastify app setup
│   │   │   ├── db/            # SQLite schema + migrations
│   │   │   ├── routes/        # REST API routes
│   │   │   ├── ws/            # WebSocket hub
│   │   │   ├── session/       # Session orchestrator + state machine
│   │   │   ├── companion/     # Companion connection manager
│   │   │   ├── openclaw/      # OpenClaw bridge
│   │   │   ├── push/          # FCM adapter
│   │   │   └── cleanup/       # 30-day purge job
│   │   ├── package.json
│   │   └── tsconfig.json
│   │
│   ├── companion/             # MacBook companion daemon
│   │   ├── src/
│   │   │   ├── index.ts       # Entry point
│   │   │   ├── relay-client.ts # WSS client to relay
│   │   │   ├── workspace/     # Directory catalog + browse
│   │   │   ├── runtime/       # Claude/book CLI adapter (stream-json)
│   │   │   ├── session/       # Local session index
│   │   │   └── commands/      # Command dispatcher
│   │   ├── package.json
│   │   └── tsconfig.json
│   │
│   ├── wire/                  # Shared protocol types
│   │   ├── src/
│   │   │   ├── events.ts      # Event type definitions
│   │   │   ├── commands.ts    # Command type definitions
│   │   │   ├── models.ts      # Shared data models
│   │   │   └── errors.ts      # Error code definitions
│   │   ├── package.json
│   │   └── tsconfig.json
│   │
│   └── android/               # Android app
│       ├── app/src/main/
│       │   ├── java/.../imbot/
│       │   │   ├── IMbotApp.kt
│       │   │   ├── ui/
│       │   │   │   ├── home/          # Session list
│       │   │   │   ├── detail/        # Session detail
│       │   │   │   ├── newsession/    # New session flow
│       │   │   │   ├── workspace/     # Workspace manager
│       │   │   │   ├── settings/      # Settings
│       │   │   │   └── theme/         # Theme system
│       │   │   ├── data/
│       │   │   │   ├── local/         # Room DB + DAOs
│       │   │   │   ├── remote/        # OkHttp REST + WS
│       │   │   │   └── repository/    # Repositories
│       │   │   ├── service/
│       │   │   │   ├── SessionService.kt    # Foreground service
│       │   │   │   └── FCMService.kt        # Push receiver
│       │   │   └── di/               # DI (Hilt)
│       │   └── res/
│       ├── build.gradle.kts
│       └── ...
│
├── docs/
│   ├── PRD.md
│   ├── engineering-spec/      # This spec
│   └── dr-workflow/           # Original design docs
│
├── package.json               # Workspace root
└── tsconfig.base.json
```
