# Capability: monorepo-scaffold

## ADDED Requirements

### Requirement: Workspace Root Configuration

The repository root SHALL contain a `package.json` with an npm `workspaces` field that lists `packages/relay`, `packages/companion`, and `packages/wire`. The root package MUST be marked `"private": true` and MUST NOT declare its own runtime dependencies.

#### Scenario: npm workspaces resolve correctly

WHEN a developer runs `npm install` from the repository root
THEN npm creates a single root `node_modules` with hoisted dependencies
AND each workspace package can import its declared dependencies
AND `packages/relay` can import from `@imbot/wire`
AND `packages/companion` can import from `@imbot/wire`

#### Scenario: root package.json is private

WHEN the root `package.json` is inspected
THEN `"private": true` is set
AND no `dependencies` or `devDependencies` with runtime libraries exist at root level (only shared tooling like `typescript`)

#### Scenario: workspace list is complete

WHEN the `workspaces` field is read
THEN it contains exactly `["packages/relay", "packages/companion", "packages/wire"]`
AND `packages/android` is NOT listed (it is a Gradle project, not an npm package)

---

### Requirement: Package Directory Structure

Each TypeScript package (`relay`, `companion`, `wire`) SHALL have its own `package.json` with a scoped name (`@imbot/relay`, `@imbot/companion`, `@imbot/wire`) and its own `tsconfig.json` that extends the shared base. The `packages/android` directory SHALL contain a Kotlin/Gradle project skeleton.

#### Scenario: relay package structure

WHEN `packages/relay/` is listed
THEN it contains `package.json` with `"name": "@imbot/relay"`
AND it contains `tsconfig.json` that has `"extends": "../../tsconfig.base.json"`
AND it contains a `src/` directory

#### Scenario: companion package structure

WHEN `packages/companion/` is listed
THEN it contains `package.json` with `"name": "@imbot/companion"`
AND it contains `tsconfig.json` that has `"extends": "../../tsconfig.base.json"`
AND it contains a `src/` directory

#### Scenario: wire package structure

WHEN `packages/wire/` is listed
THEN it contains `package.json` with `"name": "@imbot/wire"`
AND it contains `tsconfig.json` that has `"extends": "../../tsconfig.base.json"`
AND it contains a `src/` directory with TypeScript source files
AND `package.json` has `"main": "dist/index.js"` and `"types": "dist/index.d.ts"`

#### Scenario: android package structure

WHEN `packages/android/` is listed
THEN it contains `build.gradle.kts` (or `build.gradle`)
AND it contains `settings.gradle.kts`
AND it declares Jetpack Compose and Material 3 dependencies
AND it targets `compileSdk = 34` and `minSdk = 26`

#### Scenario: missing package directory causes npm install failure

WHEN any directory listed in `workspaces` does not exist or lacks a `package.json`
THEN `npm install` fails with a workspace resolution error

---

### Requirement: Shared TypeScript Configuration

A `tsconfig.base.json` at the repository root SHALL define strict TypeScript compiler options shared across all TS packages.

#### Scenario: strict mode is enabled

WHEN `tsconfig.base.json` is read
THEN `"strict": true` is set
AND `"skipLibCheck": true` is set
AND `"esModuleInterop": true` is set
AND `"target"` is `"ES2022"` or later
AND `"module"` is `"Node16"` or `"NodeNext"`
AND `"declaration": true` is set

#### Scenario: package tsconfig extends base

WHEN `packages/wire/tsconfig.json` is read
THEN it contains `"extends": "../../tsconfig.base.json"`
AND it may override `"outDir"` and `"rootDir"` for its own build output

#### Scenario: conflicting compiler options in child are detected

WHEN a child `tsconfig.json` sets `"strict": false`
THEN the child override takes effect (standard TS behavior)
AND code review MUST reject any child that weakens strict mode

---

### Requirement: Build Order and Scripts

The root `package.json` SHALL include build scripts that compile `wire` first, then `relay` and `companion` in parallel or sequence.

#### Scenario: build wire before dependents

WHEN `npm run build` is executed from the root
THEN `packages/wire` is compiled first
AND `packages/wire/dist/` is generated with `.js` and `.d.ts` files
AND only after wire succeeds do `packages/relay` and `packages/companion` compile

#### Scenario: wire build output is importable

WHEN wire build completes
THEN `packages/relay/src/*.ts` can import `@imbot/wire` without errors
AND TypeScript resolves types from `packages/wire/dist/index.d.ts`

#### Scenario: clean script removes all build artifacts

WHEN `npm run clean` is executed from the root
THEN `dist/` directories in `packages/wire`, `packages/relay`, and `packages/companion` are removed

---

### Requirement: Gitignore Configuration

A `.gitignore` at the repository root SHALL exclude build artifacts, dependency directories, and platform-specific files.

#### Scenario: standard ignores are present

WHEN `.gitignore` is read
THEN it contains entries for: `node_modules/`, `dist/`, `.gradle/`, `build/`, `*.db`, `*.db-wal`, `*.db-shm`, `.env`, `.env.*`, `.DS_Store`

#### Scenario: committed files do not include ignored patterns

WHEN `git status` is run after a clean build
THEN no files matching the gitignore patterns appear as untracked
