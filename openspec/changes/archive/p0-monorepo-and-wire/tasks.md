# Tasks: p0-monorepo-and-wire

## Monorepo Scaffold

- [x] 1.1 Create root `package.json` with `"private": true`, npm workspaces listing `packages/relay`, `packages/companion`, `packages/wire`, and shared devDependencies (`typescript >= 5.4`)
- [x] 1.2 Create `tsconfig.base.json` at root with `strict: true`, `target: ES2022`, `module: Node16`, `moduleResolution: Node16`, `declaration: true`, `esModuleInterop: true`, `skipLibCheck: true`
- [x] 1.3 Create `packages/wire/package.json` (`@imbot/wire`), `tsconfig.json` extending base, with `outDir: dist`, `rootDir: src`; set `main: dist/index.js`, `types: dist/index.d.ts`
- [x] 1.4 Create `packages/relay/package.json` (`@imbot/relay`), `tsconfig.json` extending base; add `@imbot/wire` as workspace dependency
- [x] 1.5 Create `packages/companion/package.json` (`@imbot/companion`), `tsconfig.json` extending base; add `@imbot/wire` as workspace dependency
- [x] 1.6 Scaffold `packages/android/` with `settings.gradle.kts`, root `build.gradle.kts`, `app/build.gradle.kts` (compileSdk 34, minSdk 26, Jetpack Compose BOM, Material 3 dependency)
- [x] 1.7 Create root `.gitignore` covering `node_modules/`, `dist/`, `.gradle/`, `build/`, `*.db`, `*.db-wal`, `*.db-shm`, `.env`, `.env.*`, `.DS_Store`
- [x] 1.8 Add root `scripts` in package.json: `build` (compile wire then relay + companion), `clean` (remove dist dirs), `typecheck` (tsc --noEmit across all TS packages)

## Wire Protocol Types

- [x] 2.1 Create `packages/wire/src/enums.ts`: `EventType` union (11 values) + `EVENT_TYPES` const array; `SessionStatus` union (5 values) + `VALID_TRANSITIONS` map; `Provider` union (3 values) + `PROVIDERS` const array; `ErrorCode` union (11 values) + `ERROR_HTTP_STATUS` map
- [x] 2.2 Create `packages/wire/src/commands.ts`: `CompanionCommand` discriminated union on `cmd` field (6 variants: `create_session`, `resume_session`, `send_message`, `cancel_session`, `list_sessions`, `browse_directory`)
- [x] 2.3 Create `packages/wire/src/messages.ts`: `CompanionMessage` discriminated union on `type` field (4 variants: ack-ok, ack-error, event, heartbeat); `ServerMessage` discriminated union on `type` field (5 variants: event, status, host_status, error, pong); `ClientMessage` discriminated union on `action` field (4 variants: auth, subscribe, unsubscribe, ping)
- [x] 2.4 Create `packages/wire/src/models.ts`: interfaces for `Session`, `Host`, `WorkspaceRoot`, `SessionEvent` with all fields per DATA_MODEL.md
- [x] 2.5 Create `packages/wire/src/index.ts`: barrel re-export of all public types and constants from enums, commands, messages, models
- [x] 2.6 Run `npm run build` from root -- verify wire compiles, relay/companion can import `@imbot/wire` types without errors
