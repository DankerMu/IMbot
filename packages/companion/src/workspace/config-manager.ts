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

    let document: Record<string, unknown>;
    try {
      document = this.readConfigDocument(true);
    } catch (error) {
      if (error instanceof CompanionError && error.code === "invalid_config") {
        this.logger.warn?.(`Failed to load workspace roots from ${this.options.configPath}: ${error.message}`);
        return;
      }

      throw error;
    }

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

    const canonicalPath = canonicalizeExistingPath(normalizedPath);
    if (!canonicalPath) {
      throw new CompanionError("not_found", `Workspace root ${normalizedPath} not found`);
    }

    if (this.roots.some((root) => root.provider === provider && arePathsEquivalent(root.path, canonicalPath))) {
      return;
    }

    const previous = this.roots;
    const updated = [...this.roots, {
      provider,
      path: canonicalPath,
      label: normalizeLabel(label),
      added_at: new Date().toISOString()
    }];

    this.roots = updated;
    try {
      this.persist();
    } catch (error) {
      this.roots = previous;
      throw error;
    }
  }

  removeRoot(provider: InteractiveProvider, rootPath: string): void {
    const normalizedPath = normalizeComparablePath(rootPath);
    const updated = this.roots.filter(
      (root) => root.provider !== provider || !arePathsEquivalent(root.path, normalizedPath)
    );
    if (updated.length === this.roots.length) {
      return;
    }

    const previous = this.roots;
    this.roots = updated;
    try {
      this.persist();
    } catch (error) {
      this.roots = previous;
      throw error;
    }
  }

  isPathUnderRoot(provider: InteractiveProvider, cwd: string): boolean {
    const canonicalCwd = canonicalizeExistingPath(cwd);
    if (!canonicalCwd) {
      return false;
    }

    return this.roots.some((root) => {
      if (root.provider !== provider) {
        return false;
      }

      const canonicalRoot = canonicalizeExistingPath(root.path);
      return canonicalRoot != null && isSameOrNestedPath(canonicalCwd, canonicalRoot);
    });
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
  const addedAt =
    normalizeRootTimestamp(record.added_at) ??
    normalizeRootTimestamp(record.created_at) ??
    LEGACY_ROOT_ADDED_AT;

  if ((provider !== "claude" && provider !== "book") || !rootPath) {
    return null;
  }

  return {
    provider,
    path: normalizeComparablePath(rootPath),
    label: normalizeLabel(record.label),
    added_at: addedAt
  };
}

function canonicalizeExistingPath(targetPath: string): string | null {
  try {
    return normalizeComparablePath(fs.realpathSync(targetPath));
  } catch {
    return null;
  }
}

function arePathsEquivalent(leftPath: string, rightPath: string): boolean {
  const normalizedLeft = normalizeComparablePath(leftPath);
  const normalizedRight = normalizeComparablePath(rightPath);

  if (normalizedLeft === normalizedRight) {
    return true;
  }

  const canonicalLeft = canonicalizeExistingPath(normalizedLeft);
  const canonicalRight = canonicalizeExistingPath(normalizedRight);
  return canonicalLeft != null && canonicalRight != null && canonicalLeft === canonicalRight;
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

function normalizeRootTimestamp(value: unknown): string | null {
  if (typeof value !== "string") {
    return null;
  }

  const trimmed = value.trim();
  return trimmed === "" ? null : trimmed;
}

function formatErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "unknown error";
}

const LEGACY_ROOT_ADDED_AT = "1970-01-01T00:00:00.000Z";
