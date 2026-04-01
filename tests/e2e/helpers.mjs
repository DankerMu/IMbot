import assert from "node:assert/strict";
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import { homedir, tmpdir } from "node:os";
import path from "node:path";
import WebSocket from "ws";

export const DEFAULT_TIMEOUT_MS = 60_000;
export const LONG_TIMEOUT_MS = 120_000;
export const SMOKE_TIMEOUT_MS = 300_000;
const REQUEST_RETRY_ATTEMPTS = 3;
const REQUEST_RETRY_DELAY_MS = 750;
const RETRYABLE_FETCH_ERROR_CODES = new Set([
  "UND_ERR_CONNECT_TIMEOUT",
  "UND_ERR_HEADERS_TIMEOUT",
  "UND_ERR_SOCKET",
  "ECONNRESET",
  "ETIMEDOUT",
  "EAI_AGAIN"
]);

function normalizePath(targetPath) {
  const resolved = path.resolve(targetPath);
  const root = path.parse(resolved).root;
  return resolved === root ? resolved : resolved.replace(/[\\/]+$/, "");
}

function pathsEqual(leftPath, rightPath) {
  return normalizePath(leftPath) === normalizePath(rightPath);
}

function isSameOrNestedPath(candidatePath, rootPath) {
  const relative = path.relative(normalizePath(rootPath), normalizePath(candidatePath));
  return relative === "" || (!relative.startsWith("..") && !path.isAbsolute(relative));
}

function toBaseUrl(relayUrl) {
  const trimmed = relayUrl.replace(/\/+$/, "");
  return trimmed
    .replace(/^wss:/, "https:")
    .replace(/^ws:/, "http:")
    .replace(/\/v1\/(ws|companion)$/, "")
    .replace(/\/v1$/, "");
}

function toWsUrl(baseUrl) {
  return `${baseUrl.replace(/^https:/, "wss:").replace(/^http:/, "ws:")}/v1/ws`;
}

function loadLocalCompanionConfig() {
  const configPath = process.env.IMBOT_E2E_CONFIG_PATH ?? path.join(homedir(), ".imbot", "companion.json");
  if (!existsSync(configPath)) {
    return null;
  }

  return JSON.parse(readFileSync(configPath, "utf8"));
}

function loadRuntimeConfig() {
  const localConfig = loadLocalCompanionConfig();
  const token = process.env.IMBOT_E2E_TOKEN ?? localConfig?.token ?? null;
  const relayUrl = process.env.IMBOT_E2E_BASE_URL ?? localConfig?.relay_url ?? null;
  if (!token || !relayUrl) {
    throw new Error(
      "Missing E2E configuration. Set IMBOT_E2E_BASE_URL and IMBOT_E2E_TOKEN, or ensure ~/.imbot/companion.json exists."
    );
  }

  const baseUrl = process.env.IMBOT_E2E_BASE_URL ?? toBaseUrl(relayUrl);
  return {
    baseUrl,
    wsUrl: process.env.IMBOT_E2E_WS_URL ?? toWsUrl(baseUrl),
    token,
    hostId: process.env.IMBOT_E2E_HOST_ID ?? localConfig?.host_id ?? "macbook-1",
    localConfig
  };
}

const runtimeConfig = loadRuntimeConfig();

export function getRuntimeConfig() {
  return runtimeConfig;
}

export function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function parseResponse(response) {
  const text = await response.text();
  if (!text) {
    return null;
  }

  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

function shouldRetryRequest(error) {
  if (!error || typeof error !== "object") {
    return false;
  }

  const causeCode =
    "cause" in error &&
    error.cause &&
    typeof error.cause === "object" &&
    "code" in error.cause &&
    typeof error.cause.code === "string"
      ? error.cause.code
      : null;
  const directCode = "code" in error && typeof error.code === "string" ? error.code : null;
  return RETRYABLE_FETCH_ERROR_CODES.has(causeCode ?? "") || RETRYABLE_FETCH_ERROR_CODES.has(directCode ?? "");
}

export async function request(method, route, options = {}) {
  const { auth = true, body, headers = {}, absolute = false } = options;
  const url = absolute ? `${runtimeConfig.baseUrl}${route}` : `${runtimeConfig.baseUrl}/v1${route}`;
  let lastError = null;

  for (let attempt = 1; attempt <= REQUEST_RETRY_ATTEMPTS; attempt += 1) {
    try {
      const response = await fetch(url, {
        method,
        headers: {
          ...(auth ? { Authorization: `Bearer ${runtimeConfig.token}` } : {}),
          ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
          ...headers
        },
        body: body !== undefined ? JSON.stringify(body) : undefined
      });

      return {
        status: response.status,
        body: await parseResponse(response)
      };
    } catch (error) {
      lastError = error;
      if (!shouldRetryRequest(error) || attempt === REQUEST_RETRY_ATTEMPTS) {
        throw error;
      }

      await sleep(REQUEST_RETRY_DELAY_MS * attempt);
    }
  }

  throw lastError;
}

export function apiGet(route, options) {
  return request("GET", route, options);
}

export function apiPost(route, body, options) {
  return request("POST", route, { ...options, body });
}

export function apiDelete(route, options) {
  return request("DELETE", route, options);
}

export async function getSession(sessionId) {
  return await apiGet(`/sessions/${sessionId}`);
}

export async function listRoots(hostId = runtimeConfig.hostId) {
  return await apiGet(`/hosts/${hostId}/roots`);
}

export async function waitForStatus(sessionId, targetStatuses, timeoutMs = LONG_TIMEOUT_MS) {
  const deadline = Date.now() + timeoutMs;
  let lastSession = null;

  while (Date.now() < deadline) {
    const response = await getSession(sessionId);
    if (response.status === 404) {
      throw new Error(`Session ${sessionId} disappeared while waiting for ${targetStatuses.join(", ")}`);
    }

    lastSession = response.body;
    if (targetStatuses.includes(lastSession.status)) {
      return lastSession;
    }

    await sleep(1000);
  }

  throw new Error(
    `Session ${sessionId} did not reach ${targetStatuses.join(", ")} within ${timeoutMs}ms; last status=${lastSession?.status ?? "unknown"}`
  );
}

export function assertStatus(response, expectedStatus) {
  assert.equal(
    response.status,
    expectedStatus,
    `Expected HTTP ${expectedStatus}, got ${response.status} with body ${JSON.stringify(response.body)}`
  );
  return response.body;
}

export function assertError(response, expectedStatus, expectedCode) {
  const body = assertStatus(response, expectedStatus);
  assert.deepEqual(body, { error: expectedCode });
}

export async function connectWs(options = {}) {
  const { token = runtimeConfig.token, includeQueryToken = true } = options;
  const wsUrl = includeQueryToken
    ? `${runtimeConfig.wsUrl}?token=${encodeURIComponent(token)}`
    : runtimeConfig.wsUrl;

  const messages = [];
  const closes = [];
  const ws = new WebSocket(wsUrl);

  await new Promise((resolve, reject) => {
    const cleanup = () => {
      ws.off("open", onOpen);
      ws.off("error", onError);
    };

    const onOpen = () => {
      cleanup();
      resolve();
    };

    const onError = (error) => {
      cleanup();
      reject(error);
    };

    ws.once("open", onOpen);
    ws.once("error", onError);
  });

  ws.on("message", (data) => {
    try {
      messages.push(JSON.parse(data.toString()));
    } catch {}
  });

  ws.on("close", (code, reason) => {
    closes.push({
      code,
      reason: reason.toString()
    });
  });

  return {
    ws,
    messages,
    closes
  };
}

export async function waitForWsMessage(connection, predicate, options = {}) {
  const { timeoutMs = DEFAULT_TIMEOUT_MS, label = "WebSocket message", fromIndex = 0 } = options;
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    const match = connection.messages.slice(fromIndex).find(predicate);
    if (match) {
      return match;
    }

    await sleep(200);
  }

  throw new Error(`Timed out waiting for ${label}`);
}

export async function waitForWsClose(connection, timeoutMs = DEFAULT_TIMEOUT_MS) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (connection.closes.length > 0) {
      return connection.closes[0];
    }

    await sleep(100);
  }

  throw new Error("Timed out waiting for WebSocket close");
}

export function collectSessionWsEvents(connection, sessionId) {
  return connection.messages.filter((message) => message.type === "event" && message.session_id === sessionId);
}

export function assertContinuousSeq(events) {
  assert.ok(events.length > 0, "Expected at least one event");
  for (let index = 0; index < events.length; index += 1) {
    assert.equal(events[index].seq, index + 1, `Expected seq ${index + 1}, got ${events[index].seq}`);
  }
}

export function assertOrderedEventTypes(events, expectedSteps) {
  let currentIndex = -1;

  for (const step of expectedSteps) {
    const allowedTypes = Array.isArray(step) ? step : [step];
    const nextIndex = events.findIndex((event, index) => index > currentIndex && allowedTypes.includes(event.type));
    assert.notEqual(
      nextIndex,
      -1,
      `Missing expected event after index ${currentIndex}: ${allowedTypes.join(" or ")}`
    );
    currentIndex = nextIndex;
  }
}

export async function cleanupSession(sessionId) {
  const current = await getSession(sessionId);
  if (current.status === 404) {
    return;
  }

  let session = current.body;

  if (session.status === "queued") {
    try {
      session = await waitForStatus(sessionId, ["running", "idle", "completed", "failed", "cancelled"], 15_000);
    } catch {
      session = (await getSession(sessionId)).body;
    }
  }

  if (session.status === "running" || session.status === "idle") {
    let response = await apiPost(`/sessions/${sessionId}/complete`);
    if (response.status === 409) {
      response = await apiPost(`/sessions/${sessionId}/cancel`);
    }

    if (response.status === 200) {
      session = await waitForStatus(sessionId, ["completed", "failed", "cancelled"], LONG_TIMEOUT_MS);
    } else {
      session = (await getSession(sessionId)).body;
    }
  }

  if (["completed", "failed", "cancelled"].includes(session.status)) {
    const deleteResponse = await apiDelete(`/sessions/${sessionId}`);
    assert.ok(
      deleteResponse.status === 204 || deleteResponse.status === 404,
      `Expected cleanup delete to succeed for ${sessionId}, got ${deleteResponse.status}`
    );
  }
}

export async function createBookSession(t, overrides = {}) {
  const bookRuntime = await requireBookRuntime(t);
  if (!bookRuntime) {
    return null;
  }

  const response = await apiPost("/sessions", {
    provider: "book",
    host_id: runtimeConfig.hostId,
    cwd: bookRuntime.cwd,
    prompt: overrides.prompt ?? "说一句话就好",
    permission_mode: "bypassPermissions",
    ...overrides
  });

  assertStatus(response, 201);
  const session = response.body.session;
  if (overrides.cleanup !== false) {
    t.after(async () => {
      await cleanupSession(session.id);
    });
  }

  return {
    bookRuntime,
    response,
    session
  };
}

export async function requireBookRuntime(t) {
  const state = await getBookRuntimeState();
  if (!state.ready) {
    t.skip(state.reason);
    return null;
  }

  return state;
}

export async function getBookRuntimeState() {
  const relayRootsResponse = await listRoots(runtimeConfig.hostId);
  const relayBookRoots =
    relayRootsResponse.status === 200 && Array.isArray(relayRootsResponse.body?.roots)
      ? relayRootsResponse.body.roots.filter((root) => root.provider === "book")
      : [];
  const localBookRoots = Array.isArray(runtimeConfig.localConfig?.workspace_roots)
    ? runtimeConfig.localConfig.workspace_roots
        .filter((root) => root?.provider === "book" && typeof root.path === "string")
        .map((root) => normalizePath(root.path))
    : [];

  if (localBookRoots.length === 0) {
    return {
      ready: false,
      reason:
        "Local companion config has no book workspace root. Add a book root to ~/.imbot/companion.json and restart the companion before running session E2E.",
      relayBookRoots
    };
  }

  const configuredCwd = process.env.IMBOT_E2E_BOOK_CWD ? normalizePath(process.env.IMBOT_E2E_BOOK_CWD) : null;
  const configuredRoot = process.env.IMBOT_E2E_BOOK_ROOT ? normalizePath(process.env.IMBOT_E2E_BOOK_ROOT) : null;
  const selectedRoot =
    configuredRoot && localBookRoots.some((rootPath) => pathsEqual(rootPath, configuredRoot))
      ? configuredRoot
      : localBookRoots[0];

  const cwd = configuredCwd ?? selectedRoot;
  if (!isSameOrNestedPath(cwd, selectedRoot)) {
    return {
      ready: false,
      reason: `Configured book cwd ${cwd} is not under the selected book root ${selectedRoot}.`,
      relayBookRoots
    };
  }

  return {
    ready: true,
    cwd,
    rootPath: selectedRoot,
    localBookRoots,
    relayBookRoots,
    hasRelayRoot: relayBookRoots.some((root) => pathsEqual(root.path, selectedRoot))
  };
}

export function createTempDirectory(prefix) {
  return mkdtempSync(path.join(tmpdir(), prefix));
}

export function ensureDirectory(dirPath) {
  mkdirSync(dirPath, { recursive: true });
  return dirPath;
}

export function removeDirectory(dirPath) {
  rmSync(dirPath, { recursive: true, force: true });
}
