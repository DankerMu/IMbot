# Design: p0-monorepo-and-wire

## Key Decisions

### 1. npm Workspaces over Turborepo/Lerna

**Decision**: Use native npm workspaces (npm 7+) without a build orchestrator.

**Rationale**: The project has only 3 TS packages. Turborepo/Lerna add complexity for negligible benefit at this scale. npm workspaces handle dependency hoisting and cross-package linking natively. Build order is controlled by a simple script that compiles wire first.

**Trade-off**: No parallel build orchestration, no remote caching. Acceptable for 3 packages.

### 2. Scoped Package Names (@imbot/*)

**Decision**: Use `@imbot/relay`, `@imbot/companion`, `@imbot/wire` as package names.

**Rationale**: Scoped names prevent collision with public npm packages, make import paths self-documenting, and are standard practice for monorepo workspaces.

### 3. Wire Package is Types-Only (Zero Runtime Dependencies)

**Decision**: `@imbot/wire` contains only TypeScript type definitions, const arrays, and pure mapping objects. No runtime dependencies.

**Rationale**: Keeps the wire package trivially simple and fast to compile. Relay and companion each handle their own validation/serialization logic -- wire only defines the contract.

### 4. Shared tsconfig.base.json with Strict Mode

**Decision**: Root `tsconfig.base.json` enforces `strict: true`, `ES2022` target, `Node16` module resolution, and `declaration: true`.

**Rationale**: Strict mode catches null/undefined bugs at compile time. Node16 module resolution matches the Fastify/Node.js runtime. Declarations are needed so wire exports `.d.ts` files for downstream packages.

### 5. Android as Gradle Project (Not npm Workspace)

**Decision**: `packages/android/` is a standalone Kotlin/Gradle project, not listed in npm workspaces.

**Rationale**: Android uses Gradle for build, Kotlin for code. Including it in npm workspaces would cause confusion (npm install would fail looking for package.json in android). The wire protocol types are documented in spec; Android mirrors them in Kotlin data classes via Room entities.

### 6. Jetpack Compose + Material 3

**Decision**: Android project uses Jetpack Compose for UI with Material 3 (Material Design 3) theming.

**Rationale**: Per PRD requirements -- modern declarative UI, dark/light theme support, animation support. Material 3 is the current Google design standard.

## Directory Layout

```
IMbot/
  package.json              # workspaces: [packages/relay, packages/companion, packages/wire]
  tsconfig.base.json        # strict, ES2022, Node16, declaration
  .gitignore
  packages/
    wire/
      package.json          # @imbot/wire, main: dist/index.js, types: dist/index.d.ts
      tsconfig.json          # extends ../../tsconfig.base.json
      src/
        index.ts            # barrel re-export
        enums.ts            # EventType, SessionStatus, Provider, ErrorCode
        commands.ts         # CompanionCommand
        messages.ts         # CompanionMessage, ServerMessage, ClientMessage
        models.ts           # Session, Host, WorkspaceRoot, SessionEvent
    relay/
      package.json          # @imbot/relay, depends on @imbot/wire
      tsconfig.json
      src/
    companion/
      package.json          # @imbot/companion, depends on @imbot/wire
      tsconfig.json
      src/
    android/
      build.gradle.kts
      settings.gradle.kts
      app/
        build.gradle.kts
        src/main/
```
