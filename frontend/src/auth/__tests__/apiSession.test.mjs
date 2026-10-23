import assert from "node:assert/strict";
import { afterEach, beforeEach, test } from "node:test";
import { apiFetch, invalidateAuthRequests } from "../../api.js";
import { setAccessToken, setRefreshHandler } from "../../tokenStore.js";

const originalFetch = globalThis.fetch;
const originalWindow = globalThis.window;
const deferred = () => {
  let resolve;
  const promise = new Promise((yes) => { resolve = yes; });
  return { promise, resolve };
};

beforeEach(() => {
  globalThis.window = new EventTarget();
  setAccessToken("old-access");
  setRefreshHandler(null);
});
afterEach(() => {
  invalidateAuthRequests();
  setAccessToken(null);
  setRefreshHandler(null);
  globalThis.fetch = originalFetch;
  globalThis.window = originalWindow;
});

test("concurrent unauthorized requests share refresh and retry with the new access token", async () => {
  let refreshes = 0;
  const pending = deferred();
  setRefreshHandler(async () => { refreshes += 1; await pending.promise; setAccessToken("new-access"); return "new-access"; });
  globalThis.fetch = async (_url, options) => new Response(null, { status: options.headers.Authorization === "Bearer new-access" ? 200 : 401 });
  const first = apiFetch("/api/clients");
  const second = apiFetch("/api/projects");
  await new Promise((resolve) => setImmediate(resolve));
  pending.resolve();
  const responses = await Promise.all([first, second]);
  assert.equal(refreshes, 1);
  assert.deepEqual(responses.map((response) => response.status), [200, 200]);
});

test("late requests from a signed-out account never retry using a new account's token", async () => {
  const pending = deferred();
  let calls = 0;
  let unauthorized = 0;
  window.addEventListener("priyonix:unauthorized", () => { unauthorized += 1; });
  globalThis.fetch = () => { calls += 1; return pending.promise; };
  const request = apiFetch("/api/clients", { method: "POST" });
  invalidateAuthRequests();
  setAccessToken("different-account");
  pending.resolve(new Response(null, { status: 401 }));
  await assert.rejects(request, { name: "AbortError" });
  assert.equal(calls, 1);
  assert.equal(unauthorized, 0);
});

test("temporary refresh failure keeps the current session available for retry", async () => {
  let unauthorized = 0;
  window.addEventListener("priyonix:unauthorized", () => { unauthorized += 1; });
  setRefreshHandler(async () => { throw new TypeError("Network unavailable"); });
  globalThis.fetch = async () => new Response(null, { status: 401 });
  await assert.rejects(apiFetch("/api/clients"), /Network unavailable/);
  assert.equal(unauthorized, 0);
});

test("a permanently rejected refresh reports expiry once for concurrent requests", async () => {
  let unauthorized = 0;
  window.addEventListener("priyonix:unauthorized", () => { unauthorized += 1; invalidateAuthRequests(); setAccessToken(null); });
  setRefreshHandler(async () => { throw Object.assign(new Error("Expired"), { status: 401 }); });
  globalThis.fetch = async () => new Response(null, { status: 401 });
  await Promise.allSettled([apiFetch("/api/clients"), apiFetch("/api/projects")]);
  assert.equal(unauthorized, 1);
});
