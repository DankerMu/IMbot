import { constants as fsConstants, promises as fs, type Dirent, type Stats } from "node:fs";
import os from "node:os";
import path from "node:path";

import type { InteractiveProvider, LocalSessionInfo } from "@imbot/wire";

import type { LoggerLike } from "../types";

export interface SessionDiscoveryOptions {
  readonly claudeProjectsDir?: string;
  readonly logger?: LoggerLike;
}

export async function discoverSessions(
  cwd: string,
  provider: InteractiveProvider,
  options: SessionDiscoveryOptions = {}
): Promise<LocalSessionInfo[]> {
  const logger = options.logger ?? console;
  const claudeProjectsDir =
    options.claudeProjectsDir ?? path.join(os.homedir(), ".claude", "projects");
  const normalizedCwd = normalizeComparablePath(cwd);

  // Claude and book currently share the same Claude Code history directory.
  void provider;

  let projectEntries: Dirent[];
  try {
    projectEntries = await fs.readdir(claudeProjectsDir, {
      withFileTypes: true
    });
  } catch (error) {
    if (isMissingPathError(error)) {
      return [];
    }

    logger.warn?.(
      `Failed to read Claude projects directory ${claudeProjectsDir}: ${formatErrorMessage(error)}`
    );
    return [];
  }

  const discovered: LocalSessionInfo[] = [];
  for (const projectEntry of projectEntries) {
    if (!projectEntry.isDirectory()) {
      continue;
    }

    const projectDirPath = path.join(claudeProjectsDir, projectEntry.name);
    const projectCwd = resolveProjectCwd(projectEntry.name, normalizedCwd);
    if (!projectCwd) {
      continue;
    }

    let sessionEntries: Dirent[];
    try {
      sessionEntries = await fs.readdir(projectDirPath, {
        withFileTypes: true
      });
    } catch (error) {
      logger.warn?.(`Skipping unreadable project directory ${projectDirPath}: ${formatErrorMessage(error)}`);
      continue;
    }

    for (const sessionEntry of sessionEntries) {
      if (!sessionEntry.isFile() || !sessionEntry.name.endsWith(".jsonl")) {
        continue;
      }

      const providerSessionId = sessionEntry.name.slice(0, -".jsonl".length);
      if (!providerSessionId) {
        logger.warn?.(`Skipping session file without session id: ${path.join(projectDirPath, sessionEntry.name)}`);
        continue;
      }

      const sessionFilePath = path.join(projectDirPath, sessionEntry.name);
      let stat: Stats;
      try {
        stat = await fs.stat(sessionFilePath);
      } catch (error) {
        logger.warn?.(`Skipping unreadable session file ${sessionFilePath}: ${formatErrorMessage(error)}`);
        continue;
      }

      let status: LocalSessionInfo["status"] = stat.size === 0 ? "unknown" : "completed";
      if (status === "completed") {
        try {
          await fs.access(sessionFilePath, fsConstants.R_OK);
        } catch (error) {
          logger.warn?.(`Session file ${sessionFilePath} is unreadable: ${formatErrorMessage(error)}`);
          status = "unknown";
        }
      }

      discovered.push({
        provider_session_id: providerSessionId,
        cwd: projectCwd,
        created_at: stat.mtime.toISOString(),
        status
      });
    }
  }

  discovered.sort((left, right) => Date.parse(right.created_at) - Date.parse(left.created_at));
  return discovered;
}

function resolveProjectCwd(projectDirName: string, normalizedCwd: string): string | null {
  const recoveredFromPrefix = recoverProjectPathFromPrefix(projectDirName, normalizedCwd);
  if (recoveredFromPrefix && isSameOrNestedPath(recoveredFromPrefix, normalizedCwd)) {
    return recoveredFromPrefix;
  }

  const decoded = decodeProjectDirectory(projectDirName);
  if (decoded && isSameOrNestedPath(decoded, normalizedCwd)) {
    return decoded;
  }

  return null;
}

function recoverProjectPathFromPrefix(projectDirName: string, normalizedCwd: string): string | null {
  const encodedCwd = encodeProjectDirectory(normalizedCwd);
  const lowerProjectDirName = projectDirName.toLowerCase();
  const lowerEncodedCwd = encodedCwd.toLowerCase();

  if (lowerProjectDirName === lowerEncodedCwd) {
    return normalizedCwd;
  }

  const prefix = `${lowerEncodedCwd}-`;
  if (!lowerProjectDirName.startsWith(prefix)) {
    return null;
  }

  const suffix = projectDirName.slice(encodedCwd.length + 1);
  if (!suffix) {
    return normalizedCwd;
  }

  return normalizeComparablePath(path.join(normalizedCwd, ...suffix.split("-").filter(Boolean)));
}

function decodeProjectDirectory(projectDirName: string): string | null {
  if (!projectDirName || projectDirName.includes(path.sep)) {
    return null;
  }

  return normalizeComparablePath(path.join(path.sep, ...projectDirName.split("-").filter(Boolean)));
}

function encodeProjectDirectory(projectPath: string): string {
  return normalizeComparablePath(projectPath).replace(/^\/+/, "").replace(/\//g, "-");
}

function isSameOrNestedPath(candidate: string, root: string): boolean {
  const relative = path.relative(root, candidate);
  return relative === "" || (!relative.startsWith("..") && !path.isAbsolute(relative));
}

function normalizeComparablePath(targetPath: string): string {
  const resolved = path.resolve(targetPath);
  const root = path.parse(resolved).root;
  if (resolved === root) {
    return resolved;
  }

  return resolved.replace(/[\\\/]+$/, "");
}

function isMissingPathError(error: unknown): boolean {
  return hasNodeErrorCode(error, "ENOENT") || hasNodeErrorCode(error, "ENOTDIR");
}

function hasNodeErrorCode(error: unknown, code: string): boolean {
  return typeof error === "object" && error != null && "code" in error && (error as { code?: string }).code === code;
}

function formatErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "unknown error";
}
