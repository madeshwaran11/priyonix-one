// Holds the access token of the running session.
//
// The access token is short lived (15 minutes). It is kept in memory and,
// for convenience, in sessionStorage so a reload inside the same tab keeps
// the user signed in.
//
// It is deliberately never written to localStorage: a remembered session is
// backed by the HttpOnly refresh cookie that the server controls, never by a
// long lived token that any script running on the page could read.

let accessToken = null;
let refreshHandler = null;

export function getAccessToken() {
  return accessToken;
}

export function setAccessToken(token) {
  accessToken = token || null;
  return accessToken;
}

/**
 * Registers the callback that exchanges the refresh cookie for a new access
 * token. The API layer calls it when a request comes back with 401.
 */
export function setRefreshHandler(handler) {
  refreshHandler = handler || null;
}

export function getRefreshHandler() {
  return refreshHandler;
}
