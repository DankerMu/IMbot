# Capability: relay-server-bootstrap

## ADDED Requirements

### Requirement: Fastify Server Initialization

The relay server SHALL use Fastify as its HTTP framework. It MUST listen on a configurable host and port, register CORS and WebSocket plugins, and log startup information.

#### Scenario: successful startup with default config

WHEN the relay server starts with `RELAY_STATIC_TOKEN` set and no other env overrides
THEN it listens on `0.0.0.0:3000`
AND it logs `Relay server listening on 0.0.0.0:3000`
AND `GET /healthz` returns `200`

#### Scenario: startup with custom port

WHEN `RELAY_PORT=8080` is set in environment
AND the relay server starts
THEN it listens on port `8080`
AND `GET /healthz` on port `8080` returns `200`

#### Scenario: startup with custom host

WHEN `RELAY_HOST=127.0.0.1` is set in environment
AND the relay server starts
THEN it listens on `127.0.0.1` only (not `0.0.0.0`)

#### Scenario: missing RELAY_STATIC_TOKEN prevents startup

WHEN `RELAY_STATIC_TOKEN` is not set in environment or `.env` file
AND the relay server attempts to start
THEN it exits with a non-zero exit code
AND it logs an error message containing `RELAY_STATIC_TOKEN`

#### Scenario: .env file is loaded automatically

WHEN a `.env` file exists in the relay package or project root containing `RELAY_STATIC_TOKEN=test123`
AND no `RELAY_STATIC_TOKEN` environment variable is set
THEN the server starts successfully using the token from `.env`

---

### Requirement: Graceful Shutdown

The relay server MUST handle SIGTERM and SIGINT signals by closing the HTTP server, closing all WebSocket connections, and closing the SQLite database connection before exiting.

#### Scenario: SIGTERM triggers graceful shutdown

WHEN the relay server is running
AND a SIGTERM signal is sent to the process
THEN the server stops accepting new connections
AND existing HTTP requests complete (up to 10s timeout)
AND all WebSocket connections are closed with code 1001 (Going Away)
AND the SQLite database connection is closed
AND the process exits with code 0

#### Scenario: SIGINT triggers graceful shutdown

WHEN the relay server is running
AND a SIGINT signal is sent (Ctrl+C)
THEN the same graceful shutdown sequence as SIGTERM executes

#### Scenario: forced exit after shutdown timeout

WHEN the graceful shutdown takes longer than 10 seconds
THEN the process force-exits with code 1

---

### Requirement: CORS Configuration

The relay server MUST enable CORS to allow cross-origin requests during development.

#### Scenario: CORS headers are present

WHEN a browser sends an OPTIONS preflight request to any route
THEN the response includes `Access-Control-Allow-Origin` header
AND the response includes `Access-Control-Allow-Methods` with at least `GET, POST, DELETE, OPTIONS`
AND the response includes `Access-Control-Allow-Headers` with at least `Authorization, Content-Type`

---

### Requirement: Health Check Endpoint

The relay server SHALL expose a `GET /healthz` endpoint that returns server status without requiring authentication.

#### Scenario: healthz returns OK

WHEN `GET /healthz` is called (no auth required)
THEN it returns HTTP 200
AND the body contains `{ "status": "ok", "uptime": <number> }`

#### Scenario: healthz includes DB status

WHEN the SQLite database is accessible
THEN the healthz response includes `"db": "ok"`

#### Scenario: healthz includes component status

WHEN a companion is connected
THEN the healthz response includes `"companion": "online"`
WHEN no companion is connected
THEN the healthz response includes `"companion": "offline"`

---

### Requirement: Configuration Loading

The relay server SHALL load configuration from environment variables with dotenv fallback, applying defaults for optional values as specified in CONFIGURATION.md.

#### Scenario: all defaults are applied when only token is set

WHEN only `RELAY_STATIC_TOKEN` is configured
THEN `RELAY_PORT` defaults to `3000`
AND `RELAY_HOST` defaults to `0.0.0.0`
AND `RELAY_DB_PATH` defaults to `./data/imbot.db`
AND `RELAY_LOG_LEVEL` defaults to `info`
AND `RELAY_COMPANION_TIMEOUT_MS` defaults to `30000`
AND `RELAY_HEARTBEAT_INTERVAL_MS` defaults to `60000`
AND `RELAY_HEARTBEAT_STALE_MS` defaults to `90000`

#### Scenario: environment variables override defaults

WHEN `RELAY_PORT=9000` and `RELAY_LOG_LEVEL=debug` are set
THEN the server uses port `9000` and debug-level logging

#### Scenario: invalid port value is handled

WHEN `RELAY_PORT=abc` is set
THEN the server logs an error about invalid port configuration
AND exits with non-zero code
