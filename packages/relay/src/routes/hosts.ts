import { randomUUID } from "node:crypto";
import { promises as fs } from "node:fs";
import path from "node:path";

import { PROVIDERS, type Host, type Provider, type WorkspaceRoot } from "@imbot/wire";
import type { FastifyInstance } from "fastify";

import { AuditLogger } from "../audit/logger";
import { CompanionManager, type BrowseDirectoryResult } from "../companion/manager";
import type { RelayDatabase } from "../db/init";
import { RelayError } from "../errors";
import { hasPathTraversal, validateWorkspacePath } from "../util/path-security";

type HostSummary = Host & {
  readonly providers: Provider[];
};

const hostIdParamsSchema = {
  type: "object",
  required: ["hostId"],
  properties: {
    hostId: { type: "string", minLength: 1 }
  }
} as const;

const rootIdParamsSchema = {
  type: "object",
  required: ["hostId", "rootId"],
  properties: {
    hostId: { type: "string", minLength: 1 },
    rootId: { type: "string", minLength: 1 }
  }
} as const;

const createRootBodySchema = {
  type: "object",
  required: ["provider", "path"],
  additionalProperties: false,
  properties: {
    provider: { type: "string" },
    path: { type: "string" },
    label: { type: "string" }
  }
} as const;

const browseQuerySchema = {
  type: "object",
  required: ["path"],
  additionalProperties: false,
  properties: {
    path: { type: "string" }
  }
} as const;

export function registerHostRoutes(
  app: FastifyInstance,
  deps: {
    readonly db: RelayDatabase;
    readonly companionManager: CompanionManager;
    readonly auditLogger: AuditLogger;
  }
): void {
  app.get("/hosts", async () => {
    const hosts = listHosts(deps.db, deps.companionManager);
    return { hosts };
  });

  app.get("/hosts/:hostId/roots", { schema: { params: hostIdParamsSchema } }, async (request) => {
    const { hostId } = request.params as { hostId: string };
    requireHost(deps.db, hostId);

    const roots = deps.db
      .prepare(
        `
        SELECT *
        FROM workspace_roots
        WHERE host_id = ?
        ORDER BY created_at ASC, path ASC
        `
      )
      .all(hostId) as WorkspaceRoot[];

    return { roots };
  });

  app.post(
    "/hosts/:hostId/roots",
    {
      schema: {
        params: hostIdParamsSchema,
        body: createRootBodySchema
      }
    },
    async (request, reply) => {
      const { hostId } = request.params as { hostId: string };
      const host = requireHost(deps.db, hostId);
      const body = (request.body ?? {}) as Record<string, unknown>;
      const provider = parseProvider(body.provider);
      const requestedPath = normalizeAbsolutePath(body.path);

      assertProviderMatchesHost(host, provider);
      const target = await assertRootTargetExists(host, requestedPath, deps.companionManager);
      const rootPath = target.path;
      const label = normalizeRootLabel(body.label, rootPath);

      const root: WorkspaceRoot = {
        id: randomUUID(),
        host_id: hostId,
        provider,
        path: rootPath,
        label,
        created_at: new Date().toISOString()
      };

      try {
        deps.db
          .prepare(
            `
            INSERT INTO workspace_roots (id, host_id, provider, path, label, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            `
          )
          .run(root.id, root.host_id, root.provider, root.path, root.label, root.created_at);
      } catch (error) {
        if (isUniqueConstraintError(error)) {
          throw new RelayError("state_conflict", "Root already exists for this host, provider, and path");
        }

        throw error;
      }

      deps.auditLogger.write("root.add", {
        host_id: hostId,
        detail: {
          root_id: root.id,
          provider: root.provider,
          path: root.path,
          label: root.label
        }
      });

      reply.code(201).send({ root });
    }
  );

  app.delete(
    "/hosts/:hostId/roots/:rootId",
    {
      schema: {
        params: rootIdParamsSchema
      }
    },
    async (request, reply) => {
      const { hostId, rootId } = request.params as { hostId: string; rootId: string };
      requireHost(deps.db, hostId);

      const root =
        (deps.db
          .prepare(
            `
            SELECT *
            FROM workspace_roots
            WHERE host_id = ? AND id = ?
            `
          )
          .get(hostId, rootId) as WorkspaceRoot | undefined) ?? null;

      if (!root) {
        throw new RelayError("not_found", `Workspace root ${rootId} not found`);
      }

      deps.db.prepare("DELETE FROM workspace_roots WHERE id = ? AND host_id = ?").run(rootId, hostId);

      deps.auditLogger.write("root.remove", {
        host_id: hostId,
        detail: {
          root_id: root.id,
          provider: root.provider,
          path: root.path
        }
      });

      reply.code(204).send();
    }
  );

  app.get(
    "/hosts/:hostId/browse",
    {
      schema: {
        params: hostIdParamsSchema,
        querystring: browseQuerySchema
      }
    },
    async (request) => {
      const { hostId } = request.params as { hostId: string };
      const host = requireHost(deps.db, hostId);
      const query = request.query as Record<string, unknown>;
      const requestedPath = normalizeAbsolutePath(query.path);
      const roots = deps.db
        .prepare("SELECT * FROM workspace_roots WHERE host_id = ?")
        .all(hostId) as WorkspaceRoot[];

      if (hasPathTraversal(requestedPath)) {
        throw new RelayError("forbidden", "Path is not under any workspace root");
      }

      if (host.type === "relay_local") {
        const canonicalPath = await resolveLocalDirectoryPath(requestedPath);
        assertPathWithinRoots(canonicalPath, roots);
        const result = await browseLocalDirectory(canonicalPath);
        assertBrowseResultWithinRoots(result, roots);
        return result;
      }

      const result = await deps.companionManager.browseDirectory(hostId, requestedPath);
      assertBrowseResultWithinRoots(result, roots);
      return result;
    }
  );
}

function listHosts(db: RelayDatabase, companionManager: CompanionManager): HostSummary[] {
  const hosts = db
    .prepare(
      `
      SELECT *
      FROM hosts
      ORDER BY CASE WHEN id = 'relay-local' THEN 0 ELSE 1 END, created_at ASC, id ASC
      `
    )
    .all() as Host[];
  const providersByHost = new Map<string, Set<Provider>>();
  const providerRows = db
    .prepare(
      `
      SELECT host_id, provider
      FROM workspace_roots
      GROUP BY host_id, provider
      `
    )
    .all() as Array<{ host_id: string; provider: Provider }>;

  for (const row of providerRows) {
    const providers = providersByHost.get(row.host_id) ?? new Set<Provider>();
    providers.add(row.provider);
    providersByHost.set(row.host_id, providers);
  }

  if (!hosts.some((host) => host.id === "relay-local")) {
    const now = new Date().toISOString();
    hosts.unshift({
      id: "relay-local",
      name: "Relay VPS",
      type: "relay_local",
      status: "online",
      last_heartbeat_at: null,
      created_at: now,
      updated_at: now
    });
  }

  return hosts.map((host) => ({
    ...host,
    status: host.type === "relay_local" ? "online" : host.status,
    last_heartbeat_at: host.type === "relay_local" ? null : host.last_heartbeat_at,
    providers:
      host.type === "relay_local"
        ? ["openclaw"]
        : sortProviders(
            companionManager.getDeclaredProviders(host.id).length > 0
              ? companionManager.getDeclaredProviders(host.id)
              : [...(providersByHost.get(host.id) ?? new Set<Provider>())]
          )
  }));
}

function requireHost(db: RelayDatabase, hostId: string): Host {
  const host = db.prepare("SELECT * FROM hosts WHERE id = ?").get(hostId) as Host | undefined;
  if (!host) {
    throw new RelayError("not_found", `Host ${hostId} not found`);
  }

  return host;
}

function parseProvider(value: unknown): Provider {
  if (typeof value !== "string" || !PROVIDERS.includes(value as Provider)) {
    throw new RelayError("invalid_request", "provider must be claude, book, or openclaw");
  }

  return value as Provider;
}

function normalizeAbsolutePath(value: unknown): string {
  if (typeof value !== "string") {
    throw new RelayError("invalid_request", "path is required");
  }

  const trimmed = value.trim();
  if (!trimmed) {
    throw new RelayError("invalid_request", "path must not be empty");
  }

  if (!path.isAbsolute(trimmed)) {
    throw new RelayError("invalid_request", "path must be absolute");
  }

  return trimmed;
}

function normalizeRootLabel(value: unknown, rootPath: string): string | null {
  if (typeof value === "string" && value.trim()) {
    return value.trim();
  }

  const basename = path.basename(rootPath);
  return basename || null;
}

function assertProviderMatchesHost(host: Host, provider: Provider): void {
  if (host.type === "relay_local" && provider !== "openclaw") {
    throw new RelayError("invalid_request", "relay-local roots only support openclaw");
  }

  if (host.type === "macbook" && provider === "openclaw") {
    throw new RelayError("invalid_request", "macbook roots only support claude or book");
  }
}

async function assertRootTargetExists(
  host: Host,
  targetPath: string,
  companionManager: CompanionManager
): Promise<BrowseDirectoryResult> {
  if (host.type === "relay_local") {
    return {
      path: await resolveLocalDirectoryPath(targetPath),
      directories: []
    };
  }

  return await companionManager.browseDirectory(host.id, targetPath);
}

async function resolveLocalDirectoryPath(targetPath: string): Promise<string> {
  try {
    const canonicalPath = await fs.realpath(targetPath);
    const stats = await fs.stat(canonicalPath);
    if (!stats.isDirectory()) {
      throw new RelayError("not_found", `Directory ${canonicalPath} not found`);
    }

    return canonicalPath;
  } catch (error) {
    if (error instanceof RelayError) {
      throw error;
    }

    if (error && typeof error === "object" && "code" in error) {
      const code = (error as { code?: string }).code;
      if (code === "ENOENT" || code === "ENOTDIR") {
        throw new RelayError("not_found", `Directory ${targetPath} not found`);
      }
    }

    throw error;
  }
}

async function browseLocalDirectory(targetPath: string): Promise<BrowseDirectoryResult> {
  try {
    const entries = await fs.readdir(targetPath, {
      withFileTypes: true
    });

    return {
      path: targetPath,
      directories: entries
        .filter((entry) => entry.isDirectory())
        .map((entry) => ({
          name: entry.name,
          path: path.join(targetPath, entry.name)
        }))
        .sort((left, right) => left.name.localeCompare(right.name))
    };
  } catch (error) {
    if (error && typeof error === "object" && "code" in error) {
      const code = (error as { code?: string }).code;
      if (code === "ENOENT" || code === "ENOTDIR") {
        throw new RelayError("not_found", `Directory ${targetPath} not found`);
      }
    }

    throw error;
  }
}

function assertPathWithinRoots(targetPath: string, roots: readonly WorkspaceRoot[]): void {
  const validation = validateWorkspacePath(
    targetPath,
    roots.map((root) => root.path)
  );
  if (!validation.ok) {
    throw new RelayError("forbidden", "Path is not under any workspace root");
  }
}

function assertBrowseResultWithinRoots(
  result: BrowseDirectoryResult,
  roots: readonly WorkspaceRoot[]
): void {
  assertPathWithinRoots(result.path, roots);

  for (const directory of result.directories) {
    assertPathWithinRoots(directory.path, roots);
  }
}

function sortProviders(providers: readonly Provider[]): Provider[] {
  const unique = new Set(providers);
  return PROVIDERS.filter((provider) => unique.has(provider));
}

function isUniqueConstraintError(error: unknown): boolean {
  return Boolean(
    error &&
      typeof error === "object" &&
      "code" in error &&
      (error as { code?: string }).code?.startsWith("SQLITE_CONSTRAINT")
  );
}
