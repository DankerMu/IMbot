# Design: p0-relay-minimal

## Key Decisions

### 1. Fastify over Express

**Decision**: Use Fastify as the HTTP/WebSocket framework.

**Rationale**: Fastify is 2-3x faster than Express in JSON serialization benchmarks. It has first-class TypeScript support, a plugin architecture that avoids middleware ordering bugs, and `@fastify/websocket` integrates cleanly without a separate server. Schema validation via JSON Schema is built-in, which we can leverage for request validation later.

**Trade-off**: Smaller ecosystem than Express, but every library we need (CORS, WebSocket, static serving) has official Fastify plugins.

### 2. better-sqlite3 with Synchronous API

**Decision**: Use `better-sqlite3` instead of `sqlite3` (async) or an ORM (Prisma/Drizzle/Knex).

**Rationale**: `better-sqlite3` is the fastest SQLite binding for Node.js. Its synchronous API eliminates callback/promise overhead and simplifies transaction logic -- critical for seq allocation where atomicity matters. SQLite's single-writer model means async concurrency provides no benefit; synchronous calls are actually simpler and faster.

**Trade-off**: Blocks the event loop during DB operations. Acceptable because: (a) all queries are fast (indexed lookups, single-row inserts), (b) single-user system with low QPS, (c) WAL mode allows concurrent reads during writes.

### 3. WAL Mode for SQLite

**Decision**: Enable WAL (Write-Ahead Logging) journal mode on database initialization.

**Rationale**: WAL mode allows concurrent read access while a write is in progress, which matters because WebSocket event broadcasting reads sessions while the orchestrator writes events. WAL also provides better crash recovery than the default rollback journal.

**Trade-off**: Creates `-wal` and `-shm` sidecar files that must be backed up together with the main `.db` file.

### 4. dotenv for Configuration

**Decision**: Use `dotenv` to load configuration from `.env` files with environment variable override.

**Rationale**: Standard Node.js practice. Environment variables take precedence over `.env` (12-factor app convention). Simple, zero-magic, and compatible with Docker/systemd deployment. No need for YAML/JSON config files at this stage.

### 5. Timing-Safe Token Comparison

**Decision**: Use `crypto.timingSafeEqual` for bearer token comparison.

**Rationale**: Prevents timing side-channel attacks. Even though this is a single-user system behind HTTPS, it's a zero-cost best practice.

### 6. UUID v4 for All IDs

**Decision**: Use UUID v4 (random) for session IDs, event IDs, and other primary keys.

**Rationale**: Avoids sequential ID guessing, works without coordination (no auto-increment needed), and is the standard in the engineering spec.

### 7. Single-Process Architecture

**Decision**: Run relay as a single Node.js process (no clustering, no worker threads).

**Rationale**: Single-user system. The VPS has 2 cores and 3.5GB RAM -- one Node.js process is sufficient. Clustering would complicate WebSocket state management (subscription maps, companion connections) and SQLite's single-writer model provides no benefit from multi-process writes.

## Module Structure

```
packages/relay/src/
  index.ts              # Entry point: load config, init DB, start Fastify
  config.ts             # Configuration loading and validation
  db/
    init.ts             # Database initialization, schema creation, WAL mode
    schema.sql          # Raw SQL DDL (embedded or loaded)
  auth/
    guard.ts            # Fastify preHandler for REST, WS auth functions
  routes/
    health.ts           # GET /healthz
    sessions.ts         # POST /sessions, GET /sessions, GET /sessions/:id
    events.ts           # GET /sessions/:id/events
  ws/
    hub.ts              # WsHub class: connection maps, subscription maps, broadcast
    android.ts          # Android WS message handler
    companion.ts        # Companion WS message handler
  session/
    orchestrator.ts     # SessionOrchestrator: create, handleEvent, transition
    seq.ts              # Seq allocation (MAX + 1)
  companion/
    manager.ts          # CompanionManager: send command, await ack, timeout
```

## Data Flow

```
Android HTTP request
    → Fastify route handler
    → auth guard (preHandler)
    → orchestrator.create()
        → db.insertSession()
        → companionManager.sendCommand()
        → await ack (30s timeout)
    → return HTTP 201

Companion event arrives (WS)
    → companion message handler
    → orchestrator.handleEvent()
        → seq.allocate()
        → db.insertEvent()
        → db.updateSessionLastActive()
        → wsHub.broadcastToSession()
        → if terminal: orchestrator.transition()
```
