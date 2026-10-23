export default function SessionRecovery({ message, onRetry, onSignIn }) {
  return <main className="auth-recovery">
    <div className="auth-recovery-card">
      <img className="auth-recovery-mark" src="/brand/priyonix-mark.jpg" alt="PriyoniX One" width="64" height="64" />
      <h1 tabIndex={-1}>Let’s reconnect</h1>
      <p role="alert">{message}</p>
      <div className="auth-recovery-actions">
        <button type="button" className="dash-button" onClick={onRetry}>Try again</button>
        <button type="button" className="dash-button secondary" onClick={onSignIn}>Go to sign in</button>
      </div>
    </div>
  </main>;
}
