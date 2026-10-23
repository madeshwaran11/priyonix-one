import assert from "node:assert/strict";
import { test } from "node:test";
import { createSessionController } from "../sessionController.js";

const account = { token: "access-one", email: "member@example.test", role: "EMPLOYEE" };
const deferred = () => {
  let resolve;
  let reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
};

function setup(overrides = {}) {
  const saved = [];
  let clears = 0;
  const controller = createSessionController({
    readSession: () => null,
    storeSession: (value) => { saved.push(value); return value; },
    clearSession: () => { clears += 1; },
    refresh: async () => account,
    verify: async () => ({ email: account.email, role: account.role }),
    login: async () => account,
    logout: async () => {},
    ...overrides,
  });
  return { controller, saved, get clears() { return clears; } };
}

test("startup restores a remembered session once even when started twice", async () => {
  const pending = deferred();
  let calls = 0;
  const { controller } = setup({ refresh: () => { calls += 1; return pending.promise; } });
  assert.equal(controller.getSnapshot().status, "restoring");
  const first = controller.start();
  const second = controller.start();
  pending.resolve(account);
  await Promise.all([first, second]);
  assert.equal(calls, 1);
  assert.equal(controller.getSnapshot().status, "authenticated");
  assert.deepEqual(controller.getSnapshot().session, account);
});

test("stored access is verified before exposing its role", async () => {
  const pending = deferred();
  let refreshes = 0;
  const { controller } = setup({
    readSession: () => ({ ...account, role: "ADMIN" }),
    verify: () => pending.promise,
    refresh: () => { refreshes += 1; return account; },
  });
  const started = controller.start();
  assert.equal(controller.getSnapshot().session, null);
  pending.resolve({ email: account.email, role: "EMPLOYEE" });
  await started;
  assert.equal(controller.getSnapshot().session.role, "EMPLOYEE");
  assert.equal(refreshes, 0);
});

test("concurrent refresh requests share one exchange", async () => {
  const pending = deferred();
  let calls = 0;
  const { controller } = setup({ refresh: () => { calls += 1; return pending.promise; } });
  const first = controller.refresh();
  const second = controller.refresh();
  pending.resolve(account);
  assert.deepEqual(await Promise.all([first, second]), [account.token, account.token]);
  assert.equal(calls, 1);
});

test("logout prevents a late refresh from restoring access and revokes its cookie afterward", async () => {
  const pending = deferred();
  const events = [];
  const { controller, saved } = setup({
    refresh: () => pending.promise,
    logout: async () => { events.push("logout"); },
  });
  const refreshing = controller.refresh();
  const rejected = assert.rejects(refreshing, { name: "AbortError" });
  const signingOut = controller.signOut();
  assert.equal(controller.getSnapshot().status, "anonymous");
  assert.equal(controller.getSnapshot().signingOut, true);
  assert.deepEqual(events, []);
  pending.resolve(account);
  await Promise.all([rejected, signingOut]);
  assert.equal(controller.getSnapshot().session, null);
  assert.equal(controller.getSnapshot().signingOut, false);
  assert.deepEqual(saved, []);
  assert.deepEqual(events, ["logout"]);
});

test("sign-in waits for logout and submits no client-selected role", async () => {
  const pending = deferred();
  const calls = [];
  const { controller } = setup({
    logout: () => pending.promise,
    login: async (credentials) => { calls.push(credentials); return account; },
  });
  controller.signOut();
  const signingIn = controller.signIn({ email: " member@example.test ", password: "example", role: "ADMIN", rememberMe: true });
  await Promise.resolve();
  assert.deepEqual(calls, []);
  pending.resolve();
  await signingIn;
  assert.deepEqual(calls, [{ email: "member@example.test", password: "example", rememberMe: true }]);
  assert.equal(controller.getSnapshot().session.role, "EMPLOYEE");
});

test("network failure during restoration is recoverable without discarding stored access", async () => {
  let attempts = 0;
  const fixture = setup({
    readSession: () => account,
    verify: async () => {
      attempts += 1;
      if (attempts === 1) throw new TypeError("Failed to fetch");
      return { email: account.email, role: account.role };
    },
  });
  await fixture.controller.start();
  assert.equal(fixture.controller.getSnapshot().status, "recoverable");
  assert.equal(fixture.clears, 0);
  await fixture.controller.retryRestore();
  assert.equal(fixture.controller.getSnapshot().status, "authenticated");
});

test("an absent refresh cookie becomes anonymous without claiming session expiry", async () => {
  const { controller } = setup({ refresh: async () => { throw Object.assign(new Error("Missing cookie"), { status: 401 }); } });
  await controller.start();
  assert.equal(controller.getSnapshot().status, "anonymous");
  assert.equal(controller.getSnapshot().sessionExpired, false);
});

test("an invalid stored session becomes anonymous with expiry feedback", async () => {
  const { controller } = setup({
    readSession: () => account,
    verify: async () => { throw Object.assign(new Error("Expired"), { status: 401 }); },
  });
  await controller.start();
  assert.equal(controller.getSnapshot().status, "anonymous");
  assert.equal(controller.getSnapshot().sessionExpired, true);
});

test("late startup verification cannot replace a newly signed-in account", async () => {
  const pending = deferred();
  const admin = { token: "new-admin", email: "admin@example.test", role: "ADMIN" };
  const { controller } = setup({ readSession: () => account, verify: () => pending.promise, login: async () => admin });
  const starting = controller.start();
  await controller.signIn({ email: admin.email, password: "example" });
  pending.resolve({ email: account.email, role: account.role });
  await starting;
  assert.deepEqual(controller.getSnapshot().session, admin);
});

test("repeated sign-in submissions share the pending operation", async () => {
  const pending = deferred();
  let calls = 0;
  const { controller } = setup({ login: () => { calls += 1; return pending.promise; } });
  const first = controller.signIn({ email: account.email, password: "example" });
  const second = controller.signIn({ email: account.email, password: "example" });
  pending.resolve(account);
  await Promise.all([first, second]);
  assert.equal(calls, 1);
});
