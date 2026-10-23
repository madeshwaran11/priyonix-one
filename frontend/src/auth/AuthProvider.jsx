import { useEffect, useMemo, useState, useSyncExternalStore } from "react";
import { AuthContext } from "./authContext";
import { apiFetch, ApiError, invalidateAuthRequests, refreshAccessToken, revokeServerSession } from "../api";
import { clearSession, readStoredSession, storeSession } from "../session";
import { setRefreshHandler } from "../tokenStore";
import { createSessionController } from "./sessionController";

async function login(credentials) {
  const response = await apiFetch("/api/auth/login", {
    method: "POST",
    skipAuth: true,
    signal: AbortSignal.timeout(15000),
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(credentials),
  });
  const data = await response.json().catch(() => null);
  if (!response.ok || !data?.token || !data?.email || !data?.role) {
    throw new ApiError(data?.message || "Sign-in is temporarily unavailable.", { status: response.ok ? 502 : response.status });
  }
  return data;
}

async function verify(token) {
  const response = await apiFetch("/api/auth/me", { token, signal: AbortSignal.timeout(15000) });
  const data = await response.json().catch(() => null);
  if (!response.ok || !data?.role || !data?.email) {
    throw new ApiError(data?.message || "Your workspace could not be reached.", { status: response.ok ? 502 : response.status });
  }
  return { email: data.email, role: data.role };
}

export default function AuthProvider({ children }) {
  const [controller] = useState(() => createSessionController({
    readSession: readStoredSession,
    storeSession,
    clearSession,
    refresh: refreshAccessToken,
    verify,
    login,
    logout: revokeServerSession,
    onSessionChange: invalidateAuthRequests,
  }));
  const snapshot = useSyncExternalStore(controller.subscribe, controller.getSnapshot);

  useEffect(() => {
    setRefreshHandler(controller.refresh);
    window.addEventListener("priyonix:unauthorized", controller.unauthorized);
    // The controller shares this operation across StrictMode's effect replay.
    controller.start();
    return () => {
      setRefreshHandler(null);
      window.removeEventListener("priyonix:unauthorized", controller.unauthorized);
    };
  }, [controller]);

  const value = useMemo(() => ({
    ...snapshot,
    signIn: controller.signIn,
    signOut: controller.signOut,
    retryRestore: controller.retryRestore,
    dismissRecovery: controller.dismissRecovery,
  }), [snapshot, controller]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
