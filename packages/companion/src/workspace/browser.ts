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

export async function browseDirectory(targetPath: string): Promise<BrowseDirectoryResult> {
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
    const entries = await fs.readdir(normalizedPath, {
      withFileTypes: true
    });

    return {
      path: normalizedPath,
      directories: entries
        .filter((entry) => entry.isDirectory())
        .map((entry) => ({
          name: entry.name,
          path: path.join(normalizedPath, entry.name)
        }))
        .sort((left, right) => left.name.localeCompare(right.name))
    };
  } catch (error) {
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
