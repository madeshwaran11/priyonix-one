// End-to-end check of the PriyoniX One authentication screens.
//
// The script drives a headless Chrome through the DevTools Protocol: it
// checks both legacy login aliases, signs in through the shared form, and
// verifies automatic workspace selection, restoration, safe returns, guards,
// and logout.
//
// The credentials are read from the environment and are never printed.

const CDP_PORT = process.env.CDP_PORT || "9222";
const APP_URL = process.env.APP_URL || "http://localhost:4174";
const API_BASE = process.env.API_BASE || "http://localhost:8093";
const EMAIL = process.env.ADMIN_EMAIL;
const PASSWORD = process.env.ADMIN_PASSWORD;

if (!EMAIL || !PASSWORD) {
  console.error("ADMIN_EMAIL and ADMIN_PASSWORD must be set");
  process.exit(1);
}

let nextId = 1;
const pending = new Map();
let socket;
let closed = false;

// A hung check is worse than a failed one: the whole harness waits on this
// process, so the run is abandoned when it takes too long.
const watchdog = setTimeout(() => {
  console.error("RESULT the check did not finish in time and was stopped");
  process.exit(1);
}, 180000);

watchdog.unref?.();

function failPending(error) {
  for (const [, entry] of pending) {
    entry.reject(error);
  }
  pending.clear();
}

function send(method, params = {}) {
  if (closed) {
    return Promise.reject(new Error("the browser connection was closed"));
  }

  const id = nextId++;
  return new Promise((resolve, reject) => {
    pending.set(id, { resolve, reject });
    socket.send(JSON.stringify({ id, method, params }));
  });
}

async function evaluate(expression) {
  const result = await send("Runtime.evaluate", {
    expression,
    awaitPromise: true,
    returnByValue: true,
  });

  if (result.exceptionDetails) {
    throw new Error(
      "evaluate failed: " +
        JSON.stringify(result.exceptionDetails.exception?.description ||
          result.exceptionDetails.text)
    );
  }

  return result.result.value;
}

async function waitFor(expression, label, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    if (await evaluate(expression)) {
      return true;
    }

    await new Promise((resolve) => setTimeout(resolve, 200));
  }

  throw new Error("timed out waiting for " + label);
}

function report(label, value) {
  console.log(`RESULT ${label}: ${value}`);
}

function step(label) {
  console.log(`STEP ${label}`);
  process.stdout.write("");
}

function check(label, actual, expected) {
  const ok = actual === expected;
  console.log(
    `RESULT ${label}: ${actual}${ok ? "" : ` (expected ${expected})`}`
  );
  if (!ok) {
    process.exitCode = 1;
  }
}

function checkCondition(label, condition, detail = "") {
  report(label, condition ? "YES" : `NO${detail ? ` (${detail})` : ""}`);
  if (!condition) process.exitCode = 1;
}

async function getRefreshCookie() {
  const cookies = await send("Network.getCookies", {
    urls: [`${API_BASE}/api/auth/refresh`],
  });
  return cookies.cookies.find((cookie) => cookie.name === "px_refresh");
}

async function waitForRefreshCookie({ present, timeoutMs = 15000 }) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const cookie = await getRefreshCookie();
    if (Boolean(cookie) === present) return cookie;
    await new Promise((resolve) => setTimeout(resolve, 200));
  }
  return getRefreshCookie();
}

async function fillField(selector, value) {
  await evaluate(`
    (() => {
      const input = document.querySelector(${JSON.stringify(selector)});
      if (!input) return false;
      const setter = Object.getOwnPropertyDescriptor(
        window.HTMLInputElement.prototype, "value"
      ).set;
      setter.call(input, ${JSON.stringify(value)});
      input.dispatchEvent(new Event("input", { bubbles: true }));
      return true;
    })()
  `);
}

async function main() {
  const targets = await (
    await fetch(`http://127.0.0.1:${CDP_PORT}/json/list`)
  ).json();

  const page = targets.find((target) => target.type === "page");

  if (!page) {
    throw new Error("no page target found");
  }

  socket = new WebSocket(page.webSocketDebuggerUrl);

  await new Promise((resolve, reject) => {
    socket.addEventListener("open", resolve);
    socket.addEventListener("error", reject);
  });

  socket.addEventListener("message", (event) => {
    const message = JSON.parse(event.data);

    if (message.id && pending.has(message.id)) {
      const { resolve, reject } = pending.get(message.id);
      pending.delete(message.id);

      if (message.error) {
        reject(new Error(message.error.message));
      } else {
        resolve(message.result);
      }
    }
  });

  socket.addEventListener("close", () => {
    closed = true;
    failPending(new Error("the browser closed the connection"));
  });

  socket.addEventListener("error", (event) => {
    closed = true;
    failPending(new Error("the browser connection failed: " + (event.message || "unknown")));
  });

  await send("Page.enable");
  await send("Runtime.enable");
  await send("Network.enable");

  // Start from an anonymous browser even when the CDP profile has been used
  // by an earlier check. The production code, not this harness, owns session
  // persistence after the login below.
  await send("Network.clearBrowserCookies");
  await send("Storage.clearDataForOrigin", {
    origin: new URL(APP_URL).origin,
    storageTypes: "all",
  });

  step("the legacy login aliases");
  // ----- both role-specific aliases render the same role-free form -------
  const aliasSignatures = [];
  for (const alias of ["admin", "employee"]) {
    await send("Page.navigate", { url: `${APP_URL}/login/${alias}` });
    await waitFor(
      "document.querySelector('#email') !== null && document.querySelector('#password') !== null",
      `the ${alias} alias sign-in form`
    );
    aliasSignatures.push(await evaluate(`
      (() => {
        const form = document.querySelector("form");
        const normalize = (value) => (value || "").replace(/\\s+/g, " ").trim();
        return JSON.stringify({
          forms: document.querySelectorAll("form").length,
          heading: normalize(document.querySelector("h2")?.textContent),
          labels: [...form.querySelectorAll("label")].map((label) => normalize(label.textContent)),
          submit: normalize(form.querySelector("button[type=submit]")?.textContent),
          roleControls: document.querySelectorAll(
            "input[name=role], select[name=role], [data-role-selector], .role-selector, .login-role-options"
          ).length
        });
      })()
    `));
  }

  const adminAlias = JSON.parse(aliasSignatures[0]);
  const employeeAlias = JSON.parse(aliasSignatures[1]);
  checkCondition(
    "the admin and employee aliases show the same single form",
    aliasSignatures[0] === aliasSignatures[1] && adminAlias.forms === 1,
    `admin=${aliasSignatures[0]} employee=${aliasSignatures[1]}`
  );
  checkCondition(
    "the shared sign-in form has no workspace selector",
    adminAlias.roleControls === 0 && employeeAlias.roleControls === 0
  );

  step("automatic workspace selection");
  // ----- the backend role chooses the workspace; unsafe returns are dropped
  const unsafeReturn = encodeURIComponent("//attacker.invalid/collect");
  await send("Page.navigate", { url: `${APP_URL}/login?returnTo=${unsafeReturn}` });
  await waitFor("document.querySelector('#email') !== null", "the shared sign-in form");

  // The remembered session is requested, so the refresh cookie is issued.
  await evaluate(`
    (() => {
      const box = document.querySelector('input[name=rememberMe]');
      if (!box.checked) {
        box.click();
      }
      return true;
    })()
  `);

  await fillField("#email", EMAIL);
  await fillField("#password", PASSWORD);
  await evaluate(
    "document.querySelector('form button[type=submit]').click(); true"
  );

  await waitFor(
    "location.pathname === '/admin' && location.search === ''",
    "the automatically selected admin workspace"
  );

  const redirectedLocation = await evaluate(`({
    origin: location.origin,
    pathname: location.pathname,
    search: location.search
  })`);
  checkCondition(
    "the unsafe return destination stays inside the application",
    redirectedLocation.origin === new URL(APP_URL).origin &&
      redirectedLocation.pathname === "/admin" &&
      redirectedLocation.search === "",
    JSON.stringify(redirectedLocation)
  );

  // The workspace is loaded on demand, so the header only exists once the
  // bundle of the dashboard has arrived.
  await waitFor(
    "document.querySelector('.sidebar-account-role') !== null",
    "the workspace header"
  );

  const authenticatedRole = await evaluate(`
    (() => {
      const user = JSON.parse(sessionStorage.getItem("priyonix_user") || "null");
      return user?.role || "";
    })()
  `);
  check("the server-returned role stored for the session", authenticatedRole, "ADMIN");
  checkCondition(
    "the ADMIN account opens the administrator workspace automatically",
    redirectedLocation.pathname === "/admin"
  );

  const sessionToken = await evaluate(`
    window.sessionStorage.getItem("priyonix_token")
  `);
  checkCondition(
    "the access token is stored in the session storage",
    Boolean(sessionToken)
  );

  const localToken = await evaluate(
    "window.localStorage.getItem('priyonix_token')"
  );
  checkCondition(
    "the access token is never stored in the local storage",
    !localToken
  );

  // The refresh cookie is scoped to the authentication endpoints, so it is
  // looked up with an address below that path.
  const refresh = await waitForRefreshCookie({ present: true });
  report(
    "the refresh cookie is HttpOnly and scoped to the auth endpoints",
    refresh
      ? `httpOnly=${refresh.httpOnly} path=${refresh.path} sameSite=${refresh.sameSite}`
      : "MISSING"
  );

  if (!refresh || !refresh.httpOnly || refresh.path !== "/api/auth") {
    console.error(
      "RESULT the refresh cookie is missing or has the wrong attributes"
    );
    process.exitCode = 1;
  }

  step("remembered-session restoration");
  // ----- an empty tab session is restored from the HttpOnly cookie -------
  await evaluate(`
    sessionStorage.removeItem("priyonix_token");
    sessionStorage.removeItem("priyonix_user");
    true
  `);
  await send("Page.reload", { ignoreCache: true });
  await waitFor(
    "location.pathname === '/admin' && Boolean(sessionStorage.getItem('priyonix_token'))",
    "the remembered admin session",
    30000
  );
  const restored = await evaluate(`
    (() => {
      const user = JSON.parse(sessionStorage.getItem("priyonix_user") || "null");
      return {
        hasToken: Boolean(sessionStorage.getItem("priyonix_token")),
        role: user?.role || "",
        pathname: location.pathname
      };
    })()
  `);
  checkCondition(
    "the remembered cookie restores the tab session after reload",
    restored.hasToken && restored.role === "ADMIN" && restored.pathname === "/admin",
    JSON.stringify(restored)
  );

  step("safe permitted return");
  // ----- an allowed deep link survives but uses the account's workspace --
  const permittedReturn = "/employee?section=clients&record=regression-client";
  await send("Page.navigate", {
    url: `${APP_URL}/login?returnTo=${encodeURIComponent(permittedReturn)}`,
  });
  await waitFor(
    `location.pathname === "/admin" &&
      new URLSearchParams(location.search).get("section") === "clients" &&
      new URLSearchParams(location.search).get("record") === "regression-client"`,
    "the normalized permitted return"
  );
  checkCondition(
    "a permitted deep link is preserved in the administrator workspace",
    await evaluate(`
      location.pathname === "/admin" &&
      new URLSearchParams(location.search).get("section") === "clients" &&
      new URLSearchParams(location.search).get("record") === "regression-client"
    `)
  );

  step("the guard of the other workspace");
  // ----- the workspace of the other role is not reachable ----------------
  await send("Page.navigate", {
    url: `${APP_URL}/employee?section=tasks&record=regression-task`,
  });
  await waitFor(
    `location.pathname === "/admin" &&
      new URLSearchParams(location.search).get("section") === "tasks" &&
      new URLSearchParams(location.search).get("record") === "regression-task"`,
    "the role guard"
  );

  report(
    "the other workspace is refused and its permitted deep link is normalized",
    await evaluate("location.pathname + location.search")
  );

  step("signing out");
  // ----- signing out ends the session ----------------------------------
  await waitFor(
    "document.querySelector('.dash-signout') !== null",
    "the sign-out button"
  );

  await evaluate(`
    (() => {
      const button = document.querySelector(".dash-signout");
      if (!button) return false;
      button.click();
      return true;
    })()
  `);

  await waitFor("location.pathname === '/login'", "the sign-in screen");
  report("signing out returns to the sign-in screen", "YES");

  const clearedToken = await evaluate(
    "window.sessionStorage.getItem('priyonix_token')"
  );
  report(
    "the access token is removed from the session storage",
    clearedToken ? "NO" : "YES"
  );
  if (clearedToken) process.exitCode = 1;

  const clearedUser = await evaluate(
    "window.sessionStorage.getItem('priyonix_user')"
  );
  checkCondition(
    "the signed-in user is removed from the session storage",
    !clearedUser
  );

  const cookieAfterLogout = await waitForRefreshCookie({ present: false });
  checkCondition(
    "logout revokes the refresh cookie",
    !cookieAfterLogout,
    cookieAfterLogout ? "px_refresh is still present" : ""
  );

  socket.close();
  closed = true;
  clearTimeout(watchdog);
  console.log("RESULT the check finished");
}

main()
  .catch((error) => {
    console.error("RESULT the check failed:", error.message);
    process.exitCode = 1;
    if (socket) {
      closed = true;
      socket.close();
    }
  })
  .finally(() => {
    clearTimeout(watchdog);
    // The websocket keeps the event loop alive, so the process is closed
    // explicitly once the check is done.
    process.exit(process.exitCode || 0);
  });
