import StatusPanel from "../components/StatusPanel";

// Generic content page for modules that do not have a backend API yet.
export default function ModulePage({ item }) {
  const Icon = item.icon;

  return (
    <div className="dash-page">
      <section className="dash-hero">
        <div>
          <span className="dash-eyebrow">Module</span>
          <h2>{item.heading}</h2>
          <p>{item.description}</p>
        </div>

        <div className="dash-module-icon" aria-hidden="true">
          <Icon width={26} height={26} />
        </div>
      </section>

      <StatusPanel
        variant="empty"
        title={`No ${item.label} data yet`}
        description="This module has no connected API, so there is nothing to show. Once the backend exposes an endpoint for it, live data will replace this placeholder."
      />

      {item.highlights && (
        <section className="dash-section">
          <div className="dash-section-head">
            <h3>Planned capabilities</h3>
            <span className="dash-demo-notice">Roadmap</span>
          </div>

          <ul className="dash-highlights">
            {item.highlights.map((highlight) => (
              <li key={highlight}>{highlight}</li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
