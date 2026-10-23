import { Component } from "react";

export default class AppErrorBoundary extends Component {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  componentDidCatch() {
    document.title = "Workspace unavailable | PriyoniX One";
    document.querySelector(".app-error-title")?.focus();
  }

  componentDidUpdate(previousProps) {
    if (this.state.failed && previousProps.resetKey !== this.props.resetKey) this.setState({ failed: false });
  }

  render() {
    if (!this.state.failed) return this.props.children;
    return <main className="auth-recovery app-error">
      <div className="auth-recovery-card">
        <img className="auth-recovery-mark" src="/brand/priyonix-mark.jpg" alt="PriyoniX One" width="64" height="64" />
        <h1 className="app-error-title" tabIndex={-1}>This view couldn’t open</h1>
        <p role="alert">Reload the workspace to try again. Your saved records are still available.</p>
        <div className="auth-recovery-actions">
          <button type="button" className="dash-button" onClick={() => window.location.reload()}>Reload workspace</button>
          <a className="dash-button secondary" href="/">Go to overview</a>
        </div>
      </div>
    </main>;
  }
}
