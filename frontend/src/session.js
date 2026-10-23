// Session persistence for PriyoniX One.
//
// The session is written only after a successful login response from the
// backend.
//
// Storage rules:
//   - The access token is short lived (15 minutes) and lives in
//     sessionStorage, so it disappears when the browser tab is closed.
//   - A remembered session ("Remember me") is backed by an HttpOnly refresh
//     cookie that the server sets and controls. The token is never written
//     to localStorage, where any script on the page could read it.

import { canonicalRole } from "./roles";
import { getAccessToken, setAccessToken } from "./tokenStore";

const TOKEN_STORAGE_KEY = "priyonix_token";
const USER_STORAGE_KEY = "priyonix_user";

function parseUser(raw) {
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

/**
 * Reads the session of the current tab.
 *
 * @returns {{token: string, email: string, role: string} | null}
 */
export function readStoredSession() {
  try {
    const token = window.sessionStorage.getItem(TOKEN_STORAGE_KEY);
    const user = parseUser(window.sessionStorage.getItem(USER_STORAGE_KEY));

    if (token && user && user.email) {
      setAccessToken(token);
      return { token, email: user.email, role: canonicalRole(user.role) };
    }
  } catch {
    // Storage unavailable (private mode, disabled cookies): treat as
    // signed out.
  }

  setAccessToken(null);
  return null;
}

/**
 * Persists a session that was just created by the backend.
 *
 * @param {{token: string, email: string, role: string}} session
 */
export function storeSession({ token, email, role }) {
  const next = { token, email, role: canonicalRole(role) };

  try {
    window.sessionStorage.setItem(TOKEN_STORAGE_KEY, next.token);
    window.sessionStorage.setItem(
      USER_STORAGE_KEY,
      JSON.stringify({ email: next.email, role: next.role })
    );
  } catch {
    // Storage unavailable: the session still works in memory.
  }

  setAccessToken(next.token);
  return next;
}

/**
 * Rewrites the stored role, for example after the backend reported a
 * different role for the account.
 */
export function updateStoredRole(role) {
  const canonical = canonicalRole(role);

  try {
    const raw = window.sessionStorage.getItem(USER_STORAGE_KEY);
    const user = parseUser(raw);

    if (user) {
      window.sessionStorage.setItem(
        USER_STORAGE_KEY,
        JSON.stringify({ ...user, role: canonical })
      );
    }
  } catch {
    // Nothing to update.
  }

  return canonical;
}

/** Removes the session of the current tab. */
export function clearSession() {
  try {
    window.sessionStorage.removeItem(TOKEN_STORAGE_KEY);
    window.sessionStorage.removeItem(USER_STORAGE_KEY);
  } catch {
    // Nothing to remove.
  }

  setAccessToken(null);
}

export { getAccessToken };
