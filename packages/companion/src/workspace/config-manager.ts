import fs from "node:fs";
import path from "node:path";

import type { InteractiveProvider } from "@imbot/wire";

import { CompanionError } from "../types";
import type { LoggerLike } from "../types";

export interface WorkspaceRootEntry {
  readonly provider: InteractiveProvider;
  readonly path: string;
  readonly label: string | null;
  readonly added_at: string;
}

export interface ConfigManagerOptions {
  readonly configPath: string;
  readonly logger?: LoggerLike;
}

export class ConfigManager {
  private readonly logger: LoggerLike;
  private roots: WorkspaceRootEntry[] = [];

  constructor(private readonly options: ConfigManagerOptions) {
    this.logger = options.logger ?? console;
    this.load();
  }

  load(): void {
    this.roots = [];

    const document = this.readConfigDocument(true);
    const rawRoots = document.workspace_roots;
    if (!Array.isArray(rawRoots)) {
      return;
    }

    const loadedRoots: WorkspaceRootEntry[] = [];
    for (const rawRoot of rawRoots) {
      const normalized = normalizeWorkspaceRootEntry(rawRoot);
      if (!normalized) {
        this.logger.warn?.(`Skipping invalid workspace root entry in ${this.options.configPath}`);
        continue;
      }

      loadedRoots.push(normalized);
    }

    this.roots = loadedRoots;
  }

  getRoots(provider?: InteractiveProvider): WorkspaceRootEntry[] {
    return this.roots
      .filter((root) => provider == null || root.provider === provider)
      .map((root) => ({ ...root }));
  }

  addRoot(provider: InteractiveProvider, rootPath: string, label?: string): void {
    const normalizedPath = normalizeComparablePath(rootPath);

    let stat: fs.Stats;
    try {
      stat = fs.statSync(normalizedPath);
    } catch {
      throw new CompanionError("not_found", `Workspace root ${normalizedPath} not found`);
    }

    if (!stat.isDirectory()) {
      throw new CompanionError("not_found", `Workspace root ${normalizedPath} not found`);
    }

    if (this.roots.some((root) => root.provider === provider && root.path === normalizedPath)) {
      return;
    }

    this.roots.push({
      provider,
      path: normalizedPath,
      label: normalizeLabel(label),
      added_at: new Date().toISOString()
    });
    this.persist();
  }

  removeRoot(provider: InteractiveProvider, rootPath: string): void {
    const normalizedPath = normalizeComparablePath(rootPath);
    const rootIndex = this.roots.findIndex((root) => root.provider === provider && root.path === normalizedPath);
    if (rootIndex === -1) {
      throw new CompanionError("not_found", "Workspace root not found");
    }

    this.roots.splice(rootIndex, 1);
    this.persist();
  }

  isPathUnderRoot(provider: InteractiveProvider, cwd: string): boolean {
    const normalizedCwd = normalizeComparablePath(cwd);
    return this.roots.some(
      (root) => root.provider === provider && isSameOrNestedPath(normalizedCwd, root.path)
    );
  }

  private persist(): void {
    const document = this.readConfigDocument(true);
    document.workspace_roots = this.roots.map((root) => ({ ...root }));

    fs.mkdirSync(path.dirname(this.options.configPath), {
      recursive: true
    });

    const tmpPath = `${this.options.configPath}.${process.pid}.tmp`;
    fs.writeFileSync(tmpPath, `${JSON.stringify(document, null, 2)}\n`, "utf8");
    fs.renameSync(tmpPath, this.options.configPath);
  }

  private readConfigDocument(allowMissing: boolean): Record<string, unknown> {
    if (!fs.existsSync(this.options.configPath)) {
      if (allowMissing) {
        return {};
      }

      throw new CompanionError("invalid_config", `Companion config ${this.options.configPath} does not exist`);
    }

    let rawText: string;
    try {
      rawText = fs.readFileSync(this.options.configPath, "utf8");
    } catch (error) {
      throw new CompanionError(
        "invalid_config",
        `Failed to read companion config ${this.options.configPath}: ${formatErrorMessage(error)}`
      );
    }

    let parsed: unknown;
    try {
      parsed = JSON.parse(rawText) as unknown;
    } catch (error) {
      throw new CompanionError(
        "invalid_config",
        `Failed to parse companion config ${this.options.configPath}: ${formatErrorMessage(error)}`
      );
    }

    if (parsed == null || typeof parsed !== "object" || Array.isArray(parsed)) {
      throw new CompanionError("invalid_config", `Companion config ${this.options.configPath} must be a JSON object`);
    }

    return { ...(parsed as Record<string, unknown>) };
  }
}

function normalizeWorkspaceRootEntry(value: unknown): WorkspaceRootEntry | null {
  if (value == null || typeof value !== "object" || Array.isArray(value)) {
    return null;
  }

  const record = value as Record<string, unknown>;
  const provider = record.provider;
  const rootPath = typeof record.path === "string" ? record.path : null;
  const addedAt = typeof record.added_at === "string" ? record.added_at : null;

  if ((provider !== "claude" && provider !== "book") || !rootPath || !addedAt) {
    return null;
  }

  return {
    provider,
    path: normalizeComparablePath(rootPath),
    label: normalizeLabel(record.label),
    added_at: addedAt
  };
}

function normalizeComparablePath(targetPath: string): string {
  const resolved = path.resolve(targetPath);
  const root = path.parse(resolved).root;
  if (resolved === root) {
    return resolved;
  }

  return resolved.replace(/[\\\/]+$/, "");
}

function isSameOrNestedPath(candidate: string, root: string): boolean {
  const relative = path.relative(root, candidate);
  return relative === "" || (!relative.startsWith("..") && !path.isAbsolute(relative));
}

function normalizeLabel(label: unknown): string | null {
  if (typeof label !== "string") {
    return null;
  }

  const trimmed = label.trim();
  return trimmed === "" ? null : trimmed;
}

function formatErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "unknown error";
}
