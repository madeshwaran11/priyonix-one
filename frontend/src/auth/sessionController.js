function sessionChanged() {
  return Object.assign(new Error("The session changed. Please try again."), {
    name: "AbortError",
    code: "SESSION_CHANGED",
  });
}

function isUnauthorized(error) {
  return error?.status === 401 || error?.status === 403;
}

/** Owns session transitions independently of React's effect lifecycle. */
export function createSessionController({ readSession, storeSession, clearSession, refresh, verify, login, logout, onSessionChange = () => {} }) {
  let currentSession = readSession();
  let generation = 0;
  let startupPromise = null;
  let refreshOperation = null;
  let signInOperation = null;
  let logoutPromise = null;
  const listeners = new Set();
  let snapshot = {
    session: null,
    status: "restoring",
    recoveryError: "",
    sessionExpired: false,
    signingIn: false,
    signingOut: false,
  };

  function update(changes) {
    snapshot = { ...snapshot, ...changes };
    listeners.forEach((listener) => listener());
  }

  function acceptSession(data, expectedGeneration) {
    if (generation !== expectedGeneration) throw sessionChanged();
    currentSession = storeSession(data);
    update({ session: currentSession, status: "authenticated", recoveryError: "", sessionExpired: false });
    return currentSession;
  }

  function refreshSession() {
    if (refreshOperation?.generation === generation) return refreshOperation.promise;
    const expectedGeneration = generation;
    const operation = { generation: expectedGeneration };
    operation.promise = Promise.resolve()
      .then(refresh)
      .then((data) => acceptSession(data, expectedGeneration).token)
      .finally(() => { if (refreshOperation === operation) refreshOperation = null; });
    refreshOperation = operation;
    return operation.promise;
  }

  function start() {
    if (startupPromise) return startupPromise;
    const expectedGeneration = generation;
    const restoringSession = currentSession;
    const hadStoredSession = Boolean(restoringSession);
    update({ status: "restoring", recoveryError: "" });
    startupPromise = Promise.resolve().then(async () => {
      if (generation !== expectedGeneration) return;
      try {
        if (restoringSession?.token) {
          const profile = await verify(restoringSession.token);
          acceptSession({ ...currentSession, ...profile }, expectedGeneration);
        } else {
          await refreshSession();
        }
      } catch (error) {
        if (generation !== expectedGeneration || error?.code === "SESSION_CHANGED") return;
        if (isUnauthorized(error)) {
          currentSession = null;
          clearSession();
          update({ session: null, status: "anonymous", sessionExpired: hadStoredSession, recoveryError: "" });
        } else {
          update({
            session: null,
            status: "recoverable",
            recoveryError: "We couldn’t reconnect to your workspace. Check your connection and try again.",
          });
        }
      }
    });
    return startupPromise;
  }

  function retryRestore() {
    if (snapshot.status === "restoring") return start();
    generation += 1;
    onSessionChange();
    startupPromise = null;
    return start();
  }

  function clearLocalSession(expired = false) {
    generation += 1;
    onSessionChange();
    currentSession = null;
    clearSession();
    update({ session: null, status: "anonymous", recoveryError: "", sessionExpired: expired, signingIn: false });
  }

  function signIn({ email, password, rememberMe }) {
    if (signInOperation?.generation === generation) return signInOperation.promise;
    const expectedGeneration = ++generation;
    onSessionChange();
    currentSession = null;
    clearSession();
    const previousRefresh = refreshOperation?.promise;
    const pendingLogout = logoutPromise;
    const operation = { generation: expectedGeneration };
    update({ status: "anonymous", session: null, signingIn: true, recoveryError: "" });
    operation.promise = Promise.resolve().then(async () => {
      // Let older cookie writes settle before creating the next server session.
      await Promise.allSettled([previousRefresh, pendingLogout].filter(Boolean));
      if (generation !== expectedGeneration) throw sessionChanged();
      update({ signingOut: false });
      const data = await login({ email: (email || "").trim(), password, rememberMe: Boolean(rememberMe) });
      return acceptSession(data, expectedGeneration);
    }).finally(() => {
      if (signInOperation === operation) signInOperation = null;
      if (generation === expectedGeneration) update({ signingIn: false });
    });
    signInOperation = operation;
    return operation.promise;
  }

  function signOut() {
    if (snapshot.signingOut && logoutPromise) return logoutPromise;
    const pendingOperations = [refreshOperation?.promise, signInOperation?.promise, logoutPromise].filter(Boolean);
    clearLocalSession();
    const expectedGeneration = generation;
    update({ signingOut: true });
    // A refresh response can set a cookie even after local sign-out. Revoke it
    // after that request settles, while keeping the interface signed out now.
    logoutPromise = Promise.allSettled(pendingOperations).then(logout).finally(() => {
      if (generation === expectedGeneration) update({ signingOut: false });
    });
    return logoutPromise;
  }

  return {
    getSnapshot: () => snapshot,
    subscribe: (listener) => { listeners.add(listener); return () => listeners.delete(listener); },
    start,
    retryRestore,
    refresh: refreshSession,
    signIn,
    signOut,
    dismissRecovery: () => clearLocalSession(),
    unauthorized: () => clearLocalSession(Boolean(currentSession || snapshot.session)),
  };
}
