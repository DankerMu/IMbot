import fs from "node:fs";
import path from "node:path";

import Database from "better-sqlite3";

export type RelayDatabase = Database.Database;

const SESSION_INDEXES_SQL = `
CREATE INDEX IF NOT EXISTS idx_sessions_provider ON sessions(provider);
CREATE INDEX IF NOT EXISTS idx_sessions_status ON sessions(status);
CREATE INDEX IF NOT EXISTS idx_sessions_host ON sessions(host_id);
CREATE INDEX IF NOT EXISTS idx_sessions_cwd ON sessions(workspace_cwd);
CREATE INDEX IF NOT EXISTS idx_sessions_last_active ON sessions(last_active_at);
`;

const SCHEMA_SQL = `
CREATE TABLE IF NOT EXISTS hosts (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  type TEXT NOT NULL CHECK (type IN ('macbook', 'relay_local')),
  status TEXT NOT NULL DEFAULT 'offline' CHECK (status IN ('online', 'offline')),
  last_heartbeat_at TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS workspace_roots (
  id TEXT PRIMARY KEY,
  host_id TEXT NOT NULL REFERENCES hosts(id),
  provider TEXT NOT NULL CHECK (provider IN ('claude', 'book', 'openclaw')),
  path TEXT NOT NULL,
  label TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE (host_id, provider, path)
);

CREATE INDEX IF NOT EXISTS idx_roots_host ON workspace_roots(host_id);
CREATE INDEX IF NOT EXISTS idx_roots_provider ON workspace_roots(provider);

CREATE TABLE IF NOT EXISTS sessions (
  id TEXT PRIMARY KEY,
  provider TEXT NOT NULL CHECK (provider IN ('claude', 'book', 'openclaw')),
  provider_session_id TEXT,
  host_id TEXT NOT NULL REFERENCES hosts(id),
  workspace_root TEXT,
  workspace_cwd TEXT NOT NULL,
  initial_prompt TEXT,
  model TEXT,
  permission_mode TEXT NOT NULL DEFAULT 'bypassPermissions',
  status TEXT NOT NULL DEFAULT 'queued'
    CHECK (status IN ('queued', 'running', 'idle', 'completed', 'failed', 'cancelled')),
  error_message TEXT,
  error_code TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now')),
  last_active_at TEXT NOT NULL DEFAULT (datetime('now'))
);

${SESSION_INDEXES_SQL}

CREATE TABLE IF NOT EXISTS session_events (
  id TEXT PRIMARY KEY,
  session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  seq INTEGER NOT NULL,
  type TEXT NOT NULL,
  payload TEXT NOT NULL DEFAULT '{}',
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE (session_id, seq)
);

CREATE INDEX IF NOT EXISTS idx_events_session_seq ON session_events(session_id, seq);

CREATE TABLE IF NOT EXISTS approvals (
  id TEXT PRIMARY KEY,
  session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  tool_name TEXT NOT NULL,
  tool_input TEXT,
  status TEXT NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'approved', 'denied', 'expired')),
  decision_at TEXT,
  expires_at TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_approvals_session ON approvals(session_id);
CREATE INDEX IF NOT EXISTS idx_approvals_status ON approvals(status);

CREATE TABLE IF NOT EXISTS push_subscriptions (
  id TEXT PRIMARY KEY,
  fcm_token TEXT NOT NULL UNIQUE,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS audit_logs (
  id TEXT PRIMARY KEY,
  action TEXT NOT NULL,
  session_id TEXT,
  host_id TEXT,
  detail TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_logs(created_at);
`;

function supportsIdleSessionStatus(db: RelayDatabase): boolean {
  db.exec("SAVEPOINT migrate_sessions_idle_check");

  try {
    db.prepare(
      `
      INSERT OR IGNORE INTO hosts (id, name, type, status)
      VALUES ('__migrate_probe_host__', '__migrate_probe_host__', 'relay_local', 'online')
      `
    ).run();
    db.prepare(
      `
      INSERT INTO sessions (id, provider, host_id, workspace_cwd, status)
      VALUES ('__migrate_probe_session__', 'claude', '__migrate_probe_host__', '/tmp', 'idle')
      `
    ).run();
    return true;
  } catch {
    return false;
  } finally {
    db.exec("ROLLBACK TO migrate_sessions_idle_check");
    db.exec("RELEASE migrate_sessions_idle_check");
  }
}

function migrateSchema(db: RelayDatabase): void {
  if (supportsIdleSessionStatus(db)) {
    return;
  }

  const foreignKeysEnabled = db.pragma("foreign_keys", { simple: true }) === 1;
  if (foreignKeysEnabled) {
    db.pragma("foreign_keys = OFF");
  }

  try {
    db.exec(`
      BEGIN IMMEDIATE;

      CREATE TABLE sessions_new (
        id TEXT PRIMARY KEY,
        provider TEXT NOT NULL CHECK (provider IN ('claude', 'book', 'openclaw')),
        provider_session_id TEXT,
        host_id TEXT NOT NULL REFERENCES hosts(id),
        workspace_root TEXT,
        workspace_cwd TEXT NOT NULL,
        initial_prompt TEXT,
        model TEXT,
        permission_mode TEXT NOT NULL DEFAULT 'bypassPermissions',
        status TEXT NOT NULL DEFAULT 'queued'
          CHECK (status IN ('queued', 'running', 'idle', 'completed', 'failed', 'cancelled')),
        error_message TEXT,
        error_code TEXT,
        created_at TEXT NOT NULL DEFAULT (datetime('now')),
        updated_at TEXT NOT NULL DEFAULT (datetime('now')),
        last_active_at TEXT NOT NULL DEFAULT (datetime('now'))
      );

      INSERT INTO sessions_new (
        id,
        provider,
        provider_session_id,
        host_id,
        workspace_root,
        workspace_cwd,
        initial_prompt,
        model,
        permission_mode,
        status,
        error_message,
        error_code,
        created_at,
        updated_at,
        last_active_at
      )
      SELECT
        id,
        provider,
        provider_session_id,
        host_id,
        workspace_root,
        workspace_cwd,
        initial_prompt,
        model,
        permission_mode,
        status,
        error_message,
        error_code,
        created_at,
        updated_at,
        last_active_at
      FROM sessions;

      DROP TABLE sessions;
      ALTER TABLE sessions_new RENAME TO sessions;
      ${SESSION_INDEXES_SQL}

      COMMIT;
    `);
  } catch (error) {
    try {
      db.exec("ROLLBACK");
    } catch {
      // Ignore rollback failures and surface the original migration error.
    }
    throw error;
  } finally {
    if (foreignKeysEnabled) {
      db.pragma("foreign_keys = ON");
    }
  }
}

export function initializeDatabase(dbPath: string): RelayDatabase {
  const parentDir = path.dirname(dbPath);
  fs.mkdirSync(parentDir, { recursive: true });

  const db = new Database(dbPath);
  db.pragma("journal_mode = WAL");
  db.pragma("foreign_keys = ON");
  db.exec(SCHEMA_SQL);
  migrateSchema(db);
  db
    .prepare(
      `
      INSERT OR IGNORE INTO hosts (id, name, type, status)
      VALUES ('relay-local', 'Relay VPS', 'relay_local', 'online')
      `
    )
    .run();

  return db;
}

export function getDatabaseStatus(db: RelayDatabase): "ok" | "error" {
  try {
    db.prepare("SELECT 1 AS ok").get();
    return "ok";
  } catch {
    return "error";
  }
}
