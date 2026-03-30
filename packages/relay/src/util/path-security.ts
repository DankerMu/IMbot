import path from "node:path";

const MACOS_PATH_ALIASES = [
  ["/private/var", "/var"],
  ["/private/tmp", "/tmp"],
  ["/private/etc", "/etc"]
] as const;

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
  const resolvedPaths = expandEquivalentWorkspacePaths(requestedPath);
  const resolvedRoots = expandEquivalentWorkspacePaths(rootPath);

  return resolvedPaths.some((resolvedPath) =>
    resolvedRoots.some((resolvedRoot) => isResolvedPathWithinRoot(resolvedPath, resolvedRoot))
  );
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

function expandEquivalentWorkspacePaths(inputPath: string): string[] {
  const resolvedPath = resolveWorkspacePath(inputPath);
  const variants = new Set<string>([resolvedPath]);

  for (const [canonicalPrefix, aliasPrefix] of MACOS_PATH_ALIASES) {
    const aliasVariant = rewritePathPrefix(resolvedPath, canonicalPrefix, aliasPrefix);
    if (aliasVariant) {
      variants.add(aliasVariant);
    }

    const canonicalVariant = rewritePathPrefix(resolvedPath, aliasPrefix, canonicalPrefix);
    if (canonicalVariant) {
      variants.add(canonicalVariant);
    }
  }

  return [...variants];
}

function rewritePathPrefix(
  resolvedPath: string,
  fromPrefix: string,
  toPrefix: string
): string | null {
  if (resolvedPath === fromPrefix) {
    return toPrefix;
  }

  if (!resolvedPath.startsWith(`${fromPrefix}${path.sep}`)) {
    return null;
  }

  return path.join(toPrefix, resolvedPath.slice(fromPrefix.length + 1));
}

function isResolvedPathWithinRoot(resolvedPath: string, resolvedRoot: string): boolean {
  const relative = path.relative(resolvedRoot, resolvedPath);
  return relative === "" || (!relative.startsWith("..") && !path.isAbsolute(relative));
}
