# Proposal: p0-monorepo-and-wire

## Why

The IMbot project comprises four packages (relay, companion, wire, android) that share TypeScript type definitions for the wire protocol. Without a monorepo structure and a single source of truth for protocol types, type drift between relay and companion is inevitable. Shared enums, message shapes, and model types must live in one package that both server-side packages import at build time.

## What Changes

1. **Monorepo scaffold** -- Initialize an npm-workspaces monorepo with `packages/relay`, `packages/companion`, `packages/wire`, and `packages/android` (Kotlin/Gradle). Create a shared `tsconfig.base.json` with strict mode that each TS package extends. Add root-level build scripts so that `wire` compiles before relay/companion can import it.
2. **Wire protocol types** -- Define all enums (`EventType`, `SessionStatus`, `Provider`, `ErrorCode`), command types (`CompanionCommand`), message types (`CompanionMessage`, `ServerMessage`, `ClientMessage`), and shared model types (`Session`, `Host`, `WorkspaceRoot`, `SessionEvent`) in `packages/wire/src/`. Configure `package.json` with correct `main`/`types` exports so downstream packages get full type-checking.

## Capabilities

- `monorepo-scaffold`
- `wire-protocol-types`

## Affected Areas

- Repository root (package.json, tsconfig.base.json, .gitignore)
- `packages/relay/` (package.json, tsconfig.json)
- `packages/companion/` (package.json, tsconfig.json)
- `packages/wire/` (package.json, tsconfig.json, src/*.ts)
- `packages/android/` (build.gradle.kts scaffold)

## Risks

- npm workspace hoisting may cause version conflicts -- mitigate with explicit dependency declarations.
- Android Gradle project inside an npm monorepo needs `.gitignore` tuning to avoid committing build artifacts.

## References

- docs/engineering-spec/02_Technical_Design/ARCHITECTURE.md
- docs/engineering-spec/02_Technical_Design/DATA_MODEL.md (EventType enum, SessionStatus)
- docs/engineering-spec/02_Technical_Design/API_SPEC.md (wire protocol definitions)
