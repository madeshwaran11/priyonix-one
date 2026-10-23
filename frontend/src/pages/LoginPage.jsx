import { useState } from "react";
import { Link, Navigate, useLocation } from "react-router-dom";
import AuthLayout from "../components/AuthLayout";
import { useAuth } from "../auth/useAuth";
import { safeReturnTo } from "../auth/returnTo";
import { navigationForRole } from "../nav";

function describeError(error) {
  if (error.status === 401) return "That email and password don’t match. Please try again.";
  if (error.status === 403) return error.message || "This account cannot sign in. Contact your administrator.";
  if (error.status === 429) return "Too many attempts. Please wait a moment and try again.";
  if (error.name === "TimeoutError" || error instanceof TypeError) return "We couldn’t reach your workspace. Check your connection and try again.";
  return error.message || "Sign-in is temporarily unavailable. Please try again.";
}

export default function LoginPage() {
  const { session, sessionExpired, signIn, signingIn, signingOut } = useAuth();
  const location = useLocation();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [rememberMe, setRememberMe] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const busy = signingIn || signingOut;
  const returnTo = location.state?.from || new URLSearchParams(location.search).get("returnTo");

  if (session) {
    return <Navigate to={safeReturnTo(returnTo, session.role, navigationForRole(session.role).map((item) => item.id))} replace />;
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (busy) return;
    setError("");
    try {
      await signIn({ email, password, rememberMe });
    } catch (failure) {
      if (failure.code === "SESSION_CHANGED") return;
      setError(describeError(failure));
      setPassword("");
    }
  }

  return <AuthLayout>
    <div className="mobile-brand">PriyoniX <strong>One</strong></div>
    <span className="welcome-label">WELCOME BACK</span>
    <h2>Welcome to your workspace</h2>
    <p className="login-description">Sign in with your work email to continue.</p>
    {sessionExpired && <p className="login-notice" role="status">Your session has ended. Sign in again to pick up where you left off.</p>}
    <form onSubmit={handleSubmit} aria-busy={busy}>
      <label htmlFor="email">Work email</label>
      <input id="email" name="email" type="email" placeholder="you@company.com" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="username" disabled={busy} required />
      <label htmlFor="password">Password</label>
      <div className="password-field">
        <input id="password" name="password" type={showPassword ? "text" : "password"} placeholder="Enter your password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" disabled={busy} required />
        <button type="button" className="show-password" onClick={() => setShowPassword((visible) => !visible)} aria-pressed={showPassword} aria-label={showPassword ? "Hide password" : "Show password"} disabled={busy}>{showPassword ? "Hide" : "Show"}</button>
      </div>
      <div className="login-row">
        <label className="checkbox-field"><input type="checkbox" name="rememberMe" checked={rememberMe} onChange={(event) => setRememberMe(event.target.checked)} disabled={busy} /><span>Keep me signed in</span></label>
        <Link className="login-link" to="/forgot-password">Forgot password?</Link>
      </div>
      {error && <p className="login-error" role="alert">{error}</p>}
      <button type="submit" className="login-button" disabled={busy} aria-busy={busy}>
        {busy && <span className="button-spinner" aria-hidden="true" />}
        {signingOut ? "Finishing sign out…" : signingIn ? "Signing in…" : "Sign in"}
        {!busy && <span aria-hidden="true">→</span>}
      </button>
    </form>
    <p className="login-hint">Your account opens the workspace assigned to you.</p>
  </AuthLayout>;
}
