import { promises as fs } from "node:fs";
import path from "node:path";

import { CompanionError } from "../types";

export interface BrowseDirectoryResult {
  readonly path: string;
  readonly directories: Array<{
    readonly name: string;
    readonly path: string;
  }>;
}

export async function browseDirectory(
  targetPath: string,
  options?: {
    readonly allowedRoots?: readonly string[];
  }
): Promise<BrowseDirectoryResult> {
  if (!targetPath || typeof targetPath !== "string") {
    throw new CompanionError("invalid_request", "path is required");
  }

  const trimmed = targetPath.trim();
  if (!trimmed) {
    throw new CompanionError("invalid_request", "path must not be empty");
  }

  if (!path.isAbsolute(trimmed)) {
    throw new CompanionError("invalid_request", "path must be absolute");
  }

  const normalizedPath = path.resolve(trimmed);

  try {
    const canonicalPath = await fs.realpath(normalizedPath);
    const allowedRoots = await resolveAllowedRoots(options?.allowedRoots);
    if (allowedRoots.length > 0 && !allowedRoots.some((rootPath) => isPathWithinRoot(canonicalPath, rootPath))) {
      throw new CompanionError("forbidden", `Directory ${normalizedPath} is not under any workspace root`);
    }
    const entries = await fs.readdir(canonicalPath, {
      withFileTypes: true
    });

    return {
      path: canonicalPath,
      directories: entries
        .filter((entry) => entry.isDirectory())
        .map((entry) => ({
          name: entry.name,
          path: path.join(canonicalPath, entry.name)
        }))
        .sort((left, right) => left.name.localeCompare(right.name))
    };
  } catch (error) {
    if (error instanceof CompanionError) {
      throw error;
    }

    if (error && typeof error === "object" && "code" in error) {
      const code = (error as { code?: string }).code;
      if (code === "ENOENT" || code === "ENOTDIR") {
        throw new CompanionError("not_found", `Directory ${normalizedPath} not found`);
      }
      if (code === "EACCES" || code === "EPERM") {
        throw new CompanionError("forbidden", `Directory ${normalizedPath} is not accessible`);
      }
    }

    throw new CompanionError("handler_failed", "Failed to browse directory");
  }
}

async function resolveAllowedRoots(allowedRoots: readonly string[] | undefined): Promise<string[]> {
  if (!allowedRoots || allowedRoots.length === 0) {
    return [];
  }

  const canonicalRoots = await Promise.all(
    allowedRoots.map(async (rootPath) => {
      const normalizedRootPath = path.resolve(rootPath);

      try {
        return await fs.realpath(normalizedRootPath);
      } catch (error) {
        if (error && typeof error === "object" && "code" in error) {
          const code = (error as { code?: string }).code;
          if (code === "ENOENT" || code === "ENOTDIR" || code === "EACCES" || code === "EPERM") {
            return normalizedRootPath;
          }
        }

        throw error;
      }
    })
  );

  return [...new Set(canonicalRoots)];
}

function isPathWithinRoot(requestedPath: string, rootPath: string): boolean {
  const relative = path.relative(rootPath, requestedPath);
  return relative === "" || (!relative.startsWith("..") && !path.isAbsolute(relative));
}
