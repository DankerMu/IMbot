import path from "node:path";

export interface WorkspacePathValidationResult {
  readonly ok: boolean;
  readonly resolvedPath: string;
}

export function hasPathTraversal(requestedPath: string): boolean {
  return requestedPath
    .split(/[\\/]+/)
    .some((segment) => segment === "..");
}

export function resolveWorkspacePath(requestedPath: string): string {
  return path.resolve(requestedPath);
}

export function isPathWithinRoot(requestedPath: string, rootPath: string): boolean {
  const resolvedPath = resolveWorkspacePath(requestedPath);
  const resolvedRoot = resolveWorkspacePath(rootPath);
  const relative = path.relative(resolvedRoot, resolvedPath);

  return relative === "" || (!relative.startsWith("..") && !path.isAbsolute(relative));
}

export function validateWorkspacePath(
  requestedPath: string,
  roots: readonly string[]
): WorkspacePathValidationResult {
  const resolvedPath = resolveWorkspacePath(requestedPath);
  if (hasPathTraversal(requestedPath)) {
    return {
      ok: false,
      resolvedPath
    };
  }

  return {
    ok: roots.some((rootPath) => isPathWithinRoot(resolvedPath, rootPath)),
    resolvedPath
  };
}
