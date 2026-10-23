import "../Auth.css";
import Brand from "./Brand";
import { IconClients, IconProjects, IconUsers } from "./Icons";

/**
 * The shared frame of every authentication screen.
 *
 * The sign-in, the forgot-password and the reset-password screens all use
 * this frame, so they look like one experience and only the card in the
 * right hand panel changes.
 */
export default function AuthLayout({ children, wide = false }) {
  return (
    <main className="login-page">
      <section className="login-visual">
        <Brand />

        <div className="visual-content">
          <h1>
            Your business,
            <br />
            <span>working together.</span>
          </h1>
          <p>
            A clear view of your people, projects, customers and operations.
          </p>

          <div className="feature-list">
            <div><IconUsers />People &amp; teams</div>
            <div><IconProjects />Projects &amp; delivery</div>
            <div><IconClients />Customers &amp; operations</div>
          </div>
        </div>

        <p className="visual-footer">One connected workspace. Every working day.</p>
      </section>

      <section className="login-panel">
        <div className={wide ? "login-card login-card-wide" : "login-card"}>
          {children}
        </div>

        <footer className="panel-footer">
          © 2026 PriyoniX One. All rights reserved.
        </footer>
      </section>
    </main>
  );
}
