import { useEffect, useState } from "react";
import { Link, Navigate } from "react-router-dom";
import AuthLayout from "../components/AuthLayout";
import { useAuth } from "../auth/useAuth";
import { homePathForRole } from "../roles";
import { apiFetch } from "../api";

/**
 * Starts a password reset.
 *
 * The answer is the same for a known and an unknown address, so the page
 * never reveals whether an account exists.
 */
export default function ForgotPasswordPage() {
  const { session } = useAuth();
  const [email, setEmail] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    document.title = "Reset your password | PriyoniX One";
  }, []);

  if (session) {
    return <Navigate to={homePathForRole(session.role)} replace />;
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setError("");

    const trimmedEmail = email.trim();

    if (!trimmedEmail) {
      setError("Please enter the email address of your account.");
      return;
    }

    setIsLoading(true);

    try {
      const response = await apiFetch("/api/auth/forgot-password", {
        method: "POST",
        skipAuth: true,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: trimmedEmail }),
      });

      const data = await response.json().catch(() => null);

      if (!response.ok) {
        if (response.status === 429) {
          setError(
            "Too many requests. Please wait a moment and try again."
          );
          return;
        }

        setError(
          (data && data.message) ||
            "Password reset is temporarily unavailable. Please try again later."
        );
        return;
      }

      setSent(true);
    } catch {
      setError(
        "Unable to reach the server. Please check your connection and try again."
      );
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <AuthLayout>
      <div className="mobile-brand">
        PriyoniX <strong>One</strong>
      </div>

      <span className="welcome-label">ACCOUNT RECOVERY</span>
      <h2>Forgot your password?</h2>
      <p className="login-description">
        Enter the email address of your account and we will send you a secure
        link to choose a new password. The link can be used once and expires
        after 15 minutes.
      </p>

      {sent ? (
        <div className="auth-success" role="status">
          <h3>Check your inbox</h3>
          <p>
            If an account exists for this email, password reset instructions
            will be sent.
          </p>
          <p className="auth-success-hint">
            Nothing arrived? Check the spam folder, or ask your
            administrator to confirm that the address matches your account.
          </p>

          <Link className="login-button auth-button-link" to="/login">
            Back to sign in <span>→</span>
          </Link>
        </div>
      ) : (
        <form onSubmit={handleSubmit} noValidate>
          <label htmlFor="forgot-email">Work email</label>
          <input
            id="forgot-email"
            name="email"
            type="email"
            placeholder="you@company.com"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            autoComplete="username"
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
            {isLoading ? "Sending…" : "Send reset link"} <span>→</span>
          </button>

          <p className="login-hint">
            Remembered it?{" "}
            <Link className="login-link" to="/login">
              Back to sign in
            </Link>
          </p>
        </form>
      )}
    </AuthLayout>
  );
}
