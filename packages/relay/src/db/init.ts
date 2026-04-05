import fs from "node:fs";
import path from "node:path";

import Database from "better-sqlite3";

export type RelayDatabase = Database.Database;

const PROVIDER_SESSION_ID_INDEX_SQL = `
CREATE UNIQUE INDEX IF NOT EXISTS idx_sessions_provider_session_id
ON sessions(provider_session_id) WHERE provider_session_id IS NOT NULL;
`;

const SESSION_INDEXES_SQL = `
CREATE INDEX IF NOT EXISTS idx_sessions_provider ON sessions(provider);
CREATE INDEX IF NOT EXISTS idx_sessions_status ON sessions(status);
CREATE INDEX IF NOT EXISTS idx_sessions_host ON sessions(host_id);
CREATE INDEX IF NOT EXISTS idx_sessions_cwd ON sessions(workspace_cwd);
CREATE INDEX IF NOT EXISTS idx_sessions_last_active ON sessions(last_active_at);
${PROVIDER_SESSION_ID_INDEX_SQL}
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
  input_tokens INTEGER NOT NULL DEFAULT 0,
  output_tokens INTEGER NOT NULL DEFAULT 0,
  context_window INTEGER,
  local_available INTEGER NOT NULL DEFAULT 0,
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
        input_tokens INTEGER NOT NULL DEFAULT 0,
        output_tokens INTEGER NOT NULL DEFAULT 0,
        context_window INTEGER,
        local_available INTEGER NOT NULL DEFAULT 0,
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
        input_tokens,
        output_tokens,
        context_window,
        local_available,
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
        0,
        0,
        NULL,
        CASE
          WHEN provider IN ('claude', 'book') AND provider_session_id IS NOT NULL THEN 1
          ELSE 0
        END,
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

function migrateLocalAvailable(db: RelayDatabase): void {
  const columns = db.pragma("table_info(sessions)") as Array<{ name: string }>;
  const hasLocalAvailable = columns.some((column) => column.name === "local_available");

  if (hasLocalAvailable) {
    return;
  }

  db.exec(`
    ALTER TABLE sessions ADD COLUMN local_available INTEGER NOT NULL DEFAULT 0;
    UPDATE sessions
    SET local_available = 1
    WHERE provider IN ('claude', 'book') AND provider_session_id IS NOT NULL;
  `);
}

function migrateSessionUsageSummaryColumns(db: RelayDatabase): void {
  const columns = db.pragma("table_info(sessions)") as Array<{ name: string }>;
  const hasInputTokens = columns.some((column) => column.name === "input_tokens");
  const hasOutputTokens = columns.some((column) => column.name === "output_tokens");
  const hasContextWindow = columns.some((column) => column.name === "context_window");

  const statements: string[] = [];
  if (!hasInputTokens) {
    statements.push("ALTER TABLE sessions ADD COLUMN input_tokens INTEGER NOT NULL DEFAULT 0");
  }
  if (!hasOutputTokens) {
    statements.push("ALTER TABLE sessions ADD COLUMN output_tokens INTEGER NOT NULL DEFAULT 0");
  }
  if (!hasContextWindow) {
    statements.push("ALTER TABLE sessions ADD COLUMN context_window INTEGER");
  }

  if (statements.length == 0) {
    return;
  }

  db.exec(`${statements.join(";\n")};`);
}

function hasTable(db: RelayDatabase, tableName: string): boolean {
  const row = db
    .prepare(
      `
      SELECT name
      FROM sqlite_master
      WHERE type = 'table' AND name = ?
      `
    )
    .get(tableName) as { name: string } | undefined;

  return row?.name === tableName;
}

function deduplicateProviderSessionIds(db: RelayDatabase): void {
  if (!hasTable(db, "sessions")) {
    return;
  }

  db.exec(`
    DELETE FROM sessions
    WHERE rowid NOT IN (
      SELECT MAX(rowid)
      FROM sessions
      WHERE provider_session_id IS NOT NULL
      GROUP BY provider_session_id
    ) AND provider_session_id IS NOT NULL;
  `);
}

function migrateProviderSessionIdIndex(db: RelayDatabase): void {
  try {
    db.exec(PROVIDER_SESSION_ID_INDEX_SQL);
  } catch {
    deduplicateProviderSessionIds(db);
    db.exec(PROVIDER_SESSION_ID_INDEX_SQL);
  }
}

function backfillSessionSummariesFromEvents(db: RelayDatabase): void {
  if (!hasTable(db, "sessions") || !hasTable(db, "session_events")) {
    return;
  }

  type UsageSummaryBackfillRow = {
    session_id: string;
    input_tokens: number | null;
    output_tokens: number | null;
    context_window: number | null;
    model: string | null;
  };

  const usageEvents = db
    .prepare(
      `
      SELECT
        events.session_id AS session_id,
        events.seq AS seq,
        CAST(json_extract(events.payload, '$.input_tokens') AS INTEGER) AS input_tokens,
        CAST(json_extract(events.payload, '$.output_tokens') AS INTEGER) AS output_tokens,
        CAST(json_extract(events.payload, '$.context_window') AS INTEGER) AS context_window,
        NULLIF(TRIM(COALESCE(json_extract(events.payload, '$.model'), '')), '') AS model
      FROM session_events AS events
      WHERE type = 'session_usage'
      ORDER BY events.session_id ASC, events.seq DESC
      `
    )
    .all() as Array<{
      session_id: string;
      seq: number;
      input_tokens: number | null;
      output_tokens: number | null;
      context_window: number | null;
      model: string | null;
    }>;

  const latestUsageRows = new Map<string, UsageSummaryBackfillRow>();

  for (const event of usageEvents) {
    const summary = latestUsageRows.get(event.session_id) ?? {
      session_id: event.session_id,
      input_tokens: null,
      output_tokens: null,
      context_window: null,
      model: null
    };

    if (summary.input_tokens === null && event.input_tokens !== null) {
      summary.input_tokens = event.input_tokens;
    }
    if (summary.output_tokens === null && event.output_tokens !== null) {
      summary.output_tokens = event.output_tokens;
    }
    if (summary.context_window === null && event.context_window !== null) {
      summary.context_window = event.context_window;
    }
    if (summary.model === null && event.model !== null) {
      summary.model = event.model;
    }

    latestUsageRows.set(event.session_id, summary);
  }

  const applyUsageSummary = db.prepare(
    `
    UPDATE sessions
    SET
      input_tokens = COALESCE(?, input_tokens),
      output_tokens = COALESCE(?, output_tokens),
      context_window = COALESCE(?, context_window),
      model = COALESCE(?, model)
    WHERE id = ?
    `
  );

  db.transaction((rows: UsageSummaryBackfillRow[]) => {
    for (const row of rows) {
      applyUsageSummary.run(
        row.input_tokens,
        row.output_tokens,
        row.context_window,
        row.model,
        row.session_id
      );
    }
  })(Array.from(latestUsageRows.values()));

  const latestStartedRows = db
    .prepare(
      `
      SELECT
        events.session_id AS session_id,
        NULLIF(TRIM(COALESCE(json_extract(events.payload, '$.model'), '')), '') AS model
      FROM session_events AS events
      INNER JOIN (
        SELECT session_id, MAX(seq) AS max_seq
        FROM session_events
        WHERE type = 'session_started'
        GROUP BY session_id
      ) AS latest
        ON latest.session_id = events.session_id
       AND latest.max_seq = events.seq
      WHERE NULLIF(TRIM(COALESCE(json_extract(events.payload, '$.model'), '')), '') IS NOT NULL
      `
    )
    .all() as Array<{
      session_id: string;
      model: string;
    }>;

  const applyStartedModel = db.prepare(
    `
    UPDATE sessions
    SET model = ?
    WHERE id = ? AND (model IS NULL OR TRIM(model) = '')
    `
  );

  db.transaction((rows: typeof latestStartedRows) => {
    for (const row of rows) {
      applyStartedModel.run(row.model, row.session_id);
    }
  })(latestStartedRows);
}

export function initializeDatabase(dbPath: string): RelayDatabase {
  const parentDir = path.dirname(dbPath);
  fs.mkdirSync(parentDir, { recursive: true });

  const db = new Database(dbPath);
  db.pragma("journal_mode = WAL");
  db.pragma("foreign_keys = ON");
  deduplicateProviderSessionIds(db);
  db.exec(SCHEMA_SQL);
  migrateSchema(db);
  migrateLocalAvailable(db);
  migrateSessionUsageSummaryColumns(db);
  migrateProviderSessionIdIndex(db);
  backfillSessionSummariesFromEvents(db);
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
