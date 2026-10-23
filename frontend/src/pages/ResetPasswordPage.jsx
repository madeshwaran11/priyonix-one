import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import AuthLayout from "../components/AuthLayout";
import { apiFetch } from "../api";
import { useAuth } from "../auth/useAuth";
import {
  PASSWORD_RULES,
  isStrongEnough,
  passwordViolations,
  satisfiesRule,
} from "../passwordPolicy";

/**
 * Sets a new password with a single use reset link.
 *
 * The user is not signed in automatically: after the password is changed the
 * screen asks for a fresh sign-in, so a link that was opened on a shared
 * machine never leaves a session behind.
 */
export default function ResetPasswordPage() {
  const { signOut } = useAuth();
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token") || "";

  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    document.title = "Choose a new password | PriyoniX One";
  }, []);

  const violations = useMemo(
    () => passwordViolations(password),
    [password]
  );

  async function handleSubmit(event) {
    event.preventDefault();
    setError("");

    if (!token) {
      setError("This password reset link is invalid or has expired.");
      return;
    }

    if (!isStrongEnough(password)) {
      setError(
        "The new password does not meet the requirements listed below."
      );
      return;
    }

    if (password !== confirmPassword) {
      setError("The new password and its confirmation do not match.");
      return;
    }

    setIsLoading(true);

    try {
      const response = await apiFetch("/api/auth/reset-password", {
        method: "POST",
        skipAuth: true,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          token,
          password,
          confirmPassword,
        }),
      });

      const data = await response.json().catch(() => null);

      if (!response.ok) {
        setError(
          (data && data.message) ||
            "This password reset link is invalid or has expired."
        );
        return;
      }

      // The backend revokes every access token after a password change. Clear
      // this tab immediately as well so the sign-in link cannot briefly route
      // through a now-invalid remembered workspace.
      void signOut();
      setDone(true);
    } catch {
      setError(
        "Unable to reach the server. Please check your connection and try again."
      );
    } finally {
      setIsLoading(false);
    }
  }

  if (!token) {
    return (
      <AuthLayout>
        <div className="mobile-brand">
          PriyoniX <strong>One</strong>
        </div>

        <span className="welcome-label">ACCOUNT RECOVERY</span>
        <h2>This link cannot be used</h2>
        <p className="login-description">
          The password reset link is missing its security token. It may have
          been truncated by your mail client. Request a new link to continue.
        </p>

        <Link className="login-button auth-button-link" to="/forgot-password">
          Request a new link <span>→</span>
        </Link>
      </AuthLayout>
    );
  }

  if (done) {
    return (
      <AuthLayout>
        <div className="mobile-brand">
          PriyoniX <strong>One</strong>
        </div>

        <span className="welcome-label">ACCOUNT RECOVERY</span>
        <h2>Your password was updated</h2>

        <div className="auth-success" role="status">
          <p>
            The new password is active. For your safety you were not signed
            in automatically.
          </p>
          <p className="auth-success-hint">
            Any other session that was open on this account has been ended.
          </p>

          <Link className="login-button auth-button-link" to="/login">
            Sign in <span>→</span>
          </Link>
        </div>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout wide>
      <div className="mobile-brand">
        PriyoniX <strong>One</strong>
      </div>

      <span className="welcome-label">ACCOUNT RECOVERY</span>
      <h2>Choose a new password</h2>
      <p className="login-description">
        The link can only be used once and expires 15 minutes after it was
        sent.
      </p>

      <form onSubmit={handleSubmit} noValidate>
        <label htmlFor="new-password">New password</label>
        <div className="password-field">
          <input
            id="new-password"
            name="password"
            type={showPassword ? "text" : "password"}
            placeholder="Create a strong password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="new-password"
            disabled={isLoading}
            required
          />
          <button
            type="button"
            className="show-password"
            onClick={() => setShowPassword((visible) => !visible)}
            aria-pressed={showPassword}
            aria-label={showPassword ? "Hide password" : "Show password"}
            disabled={isLoading}
          >
            {showPassword ? "Hide" : "Show"}
          </button>
        </div>

        <ul className="policy-list">
          {PASSWORD_RULES.map((rule) => {
            const met = satisfiesRule(password, rule);

            return (
              <li
                key={rule.id}
                className={met ? "is-met" : ""}
                aria-live="polite"
              >
                <span aria-hidden="true">{met ? "✓" : "•"}</span> {rule.label}
              </li>
            );
          })}
        </ul>

        <label htmlFor="confirm-password">Confirm new password</label>
        <input
          id="confirm-password"
          name="confirmPassword"
          type={showPassword ? "text" : "password"}
          placeholder="Repeat the new password"
          value={confirmPassword}
          onChange={(event) => setConfirmPassword(event.target.value)}
          autoComplete="new-password"
          disabled={isLoading}
          required
        />

        {error && (
          <p className="login-error" role="alert">
            {error}
          </p>
        )}

        <button
          type="submit"
          className="login-button"
          disabled={isLoading}
          aria-busy={isLoading}
        >
          {isLoading ? "Updating…" : "Update password"} <span>→</span>
        </button>

        <p className="login-hint">
          {violations.length > 0
            ? `${violations.length} requirement${
                violations.length === 1 ? "" : "s"
              } left to meet.`
            : "The password meets every requirement."}
        </p>
      </form>
    </AuthLayout>
  );
}
