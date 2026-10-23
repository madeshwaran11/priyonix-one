import { lazy, Suspense, useEffect, useRef } from "react";
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import AuthProvider from "./auth/AuthProvider";
import RequireRole from "./auth/RequireRole";
import { useAuth } from "./auth/useAuth";
import { homePathForRole } from "./roles";
import { navigationForRole } from "./nav";
import AppErrorBoundary from "./components/AppErrorBoundary";
import BrandLoader from "./components/BrandLoader";
import SessionRecovery from "./components/SessionRecovery";
import ForgotPasswordPage from "./pages/ForgotPasswordPage";
import LoginPage from "./pages/LoginPage";
import ResetPasswordPage from "./pages/ResetPasswordPage";
import "./App.css";
import "./Auth.css";

const Dashboard = lazy(() => import("./Dashboard"));

function RoleLanding() {
  const { session } = useAuth();
  return <Navigate to={session ? homePathForRole(session.role) : "/login"} replace />;
}

function Workspace({ role }) {
  const { session, signOut } = useAuth();
  return <RequireRole role={role}>
    <Suspense fallback={<BrandLoader />}>
      <Dashboard session={session} onSignOut={signOut} />
    </Suspense>
  </RequireRole>;
}

function RouteFeedback() {
  const location = useLocation();
  const { session } = useAuth();
  const previousRoute = useRef(null);
  const section = new URLSearchParams(location.search).get("section") || "overview";
  const routeKey = location.pathname + ":" + section;
  const item = session && navigationForRole(session.role).find((entry) => entry.id === section);
  const title = location.pathname.startsWith("/login") ? "Sign in"
    : location.pathname === "/forgot-password" ? "Reset your password"
    : location.pathname === "/reset-password" ? "Choose a new password"
    : item ? (item.label === "Overview" && item.group !== "overview" ? item.groupLabel + " overview" : item.label)
    : "Workspace";

  useEffect(() => {
    document.title = title + " | PriyoniX One";
    const shouldFocus = previousRoute.current !== null && previousRoute.current !== routeKey;
    previousRoute.current = routeKey;
    if (!shouldFocus) return;
    function focusHeading() {
      if (document.querySelector("dialog[open]")) return true;
      const heading = document.querySelector(".login-card h2") || document.querySelector("#main-content h2") || document.querySelector("main h1, main#main-content");
      if (!heading) return false;
      heading.tabIndex = -1;
      heading.focus({ preventScroll: true });
      window.scrollTo({ top: 0, behavior: "instant" });
      return true;
    }
    let observer;
    let timeout;
    const frame = requestAnimationFrame(() => {
      if (focusHeading()) return;
      observer = new MutationObserver(() => {
        if (focusHeading()) { observer.disconnect(); clearTimeout(timeout); }
      });
      observer.observe(document.getElementById("root"), { childList: true, subtree: true });
      timeout = setTimeout(() => observer.disconnect(), 15000);
    });
    return () => { cancelAnimationFrame(frame); observer?.disconnect(); clearTimeout(timeout); };
  }, [routeKey, title]);

  return <span className="sr-only route-announcement" role="status" aria-live="polite" aria-atomic="true">{title}</span>;
}

function SessionGate({ children }) {
  const { status, recoveryError, retryRestore, dismissRecovery } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  if (status === "restoring") return <BrandLoader label="Restoring your workspace" detail="Checking your secure session…" />;
  if (status === "recoverable" && !["/forgot-password", "/reset-password"].includes(location.pathname)) {
    return <SessionRecovery message={recoveryError} onRetry={retryRestore} onSignIn={() => {
      const from = location.state?.from || (["/admin", "/employee"].includes(location.pathname) ? location.pathname + location.search : undefined);
      dismissRecovery();
      navigate("/login", { replace: true, state: from ? { from } : undefined });
    }} />;
  }
  return children;
}

function AppRoutes() {
  const location = useLocation();
  const section = new URLSearchParams(location.search).get("section") || "overview";
  return <>
    <RouteFeedback />
    <AppErrorBoundary resetKey={location.pathname + ":" + section}>
      <SessionGate>
        <Routes>
          <Route path="/" element={<RoleLanding />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/login/admin" element={<LoginPage />} />
          <Route path="/login/employee" element={<LoginPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route path="/admin" element={<Workspace role="ADMIN" />} />
          <Route path="/employee" element={<Workspace role="EMPLOYEE" />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </SessionGate>
    </AppErrorBoundary>
  </>;
}

export default function App() {
  return <BrowserRouter><AppErrorBoundary><AuthProvider><AppRoutes /></AuthProvider></AppErrorBoundary></BrowserRouter>;
}
