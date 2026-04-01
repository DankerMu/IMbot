import assert from "node:assert/strict";
import test from "node:test";

import {
  apiDelete,
  apiGet,
  apiPost,
  assertError,
  assertStatus,
  createTempDirectory,
  getRuntimeConfig,
  listRoots,
  removeDirectory
} from "./helpers.mjs";

test("E2E-03: host list exposes relay-local and macbook-1 as online", async () => {
  const response = await apiGet("/hosts");
  const body = assertStatus(response, 200);
  const relayHost = body.hosts.find((host) => host.id === "relay-local");
  const macbookHost = body.hosts.find((host) => host.id === getRuntimeConfig().hostId);

  assert.ok(relayHost, "Expected relay-local in host list");
  assert.equal(relayHost.status, "online");

  assert.ok(macbookHost, `Expected ${getRuntimeConfig().hostId} in host list`);
  assert.equal(macbookHost.status, "online");
  assert.ok(macbookHost.providers.includes("book"));
});

test("E2E-04: macbook roots include a book workspace root", async () => {
  const response = await listRoots();
  const body = assertStatus(response, 200);
  assert.ok(Array.isArray(body.roots));
  for (const root of body.roots) {
    assert.equal(typeof root.id, "string");
    assert.equal(typeof root.host_id, "string");
    assert.equal(typeof root.provider, "string");
    assert.equal(typeof root.path, "string");
    assert.equal(typeof root.created_at, "string");
    assert.ok("label" in root);
  }

  const bookRoot = body.roots.find((root) => root.provider === "book");
  assert.ok(bookRoot, "Expected at least one provider=book root on macbook-1");
});

test("E2E-05: roots can be added and deleted on macbook-1", async (t) => {
  const tempRoot = createTempDirectory("imbot-e2e-book-root-");
  t.after(() => {
    removeDirectory(tempRoot);
  });

  const createResponse = await apiPost(`/hosts/${getRuntimeConfig().hostId}/roots`, {
    provider: "book",
    path: tempRoot
  });
  const created = assertStatus(createResponse, 201).root;
  assert.equal(created.provider, "book");
  assert.ok(created.path.includes(tempRoot));

  const rootsAfterCreate = assertStatus(await listRoots(), 200).roots;
  assert.ok(rootsAfterCreate.some((root) => root.id === created.id));

  const deleteResponse = await apiDelete(`/hosts/${getRuntimeConfig().hostId}/roots/${created.id}`);
  assert.equal(deleteResponse.status, 204);

  const rootsAfterDelete = assertStatus(await listRoots(), 200).roots;
  assert.ok(!rootsAfterDelete.some((root) => root.id === created.id));
});

test("E2E-06: invalid provider-host combinations are rejected", async () => {
  const relayLocalBook = await apiPost("/hosts/relay-local/roots", {
    provider: "book",
    path: "/tmp"
  });
  assertError(relayLocalBook, 400, "invalid_request");

  const macbookOpenClaw = await apiPost(`/hosts/${getRuntimeConfig().hostId}/roots`, {
    provider: "openclaw",
    path: "/tmp"
  });
  assertError(macbookOpenClaw, 400, "invalid_request");
});

test("E2E-07: macbook browse returns directories under the selected book root", async (t) => {
  const roots = assertStatus(await listRoots(), 200).roots;
  const bookRoot = roots.find((root) => root.provider === "book");
  if (!bookRoot) {
    t.skip("No relay book root is configured on macbook-1.");
  }

  const response = await apiGet(
    `/hosts/${getRuntimeConfig().hostId}/browse?path=${encodeURIComponent(bookRoot.path)}`
  );
  const body = assertStatus(response, 200);

  assert.equal(typeof body.path, "string");
  assert.ok(body.path.startsWith("/"));
  assert.ok(Array.isArray(body.directories));
  for (const directory of body.directories) {
    assert.equal(typeof directory.name, "string");
    assert.equal(typeof directory.path, "string");
    assert.ok(directory.path.startsWith(body.path));
  }
});

test("E2E-08: macbook browse rejects traversal outside the selected book root", async (t) => {
  const roots = assertStatus(await listRoots(), 200).roots;
  const bookRoot = roots.find((root) => root.provider === "book");
  if (!bookRoot) {
    t.skip("No relay book root is configured on macbook-1.");
  }

  const response = await apiGet(
    `/hosts/${getRuntimeConfig().hostId}/browse?path=${encodeURIComponent(`${bookRoot.path}/../../../etc`)}`
  );
  assertError(response, 403, "forbidden");
});

test("E2E-09: unknown hosts return 404 for roots and browse", async () => {
  const rootsResponse = await apiGet("/hosts/nonexistent/roots");
  assertError(rootsResponse, 404, "not_found");

  const browseResponse = await apiGet("/hosts/nonexistent/browse?path=/tmp");
  assertError(browseResponse, 404, "not_found");
});
