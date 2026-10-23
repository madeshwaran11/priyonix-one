// Glass-styled summary card used across the dashboard.
export default function StatCard({ label, value, meta, trend }) {
  return (
    <article className="dash-card dash-stat">
      <div className="dash-stat-top">
        <span className="dash-stat-label">{label}</span>
      </div>

      <strong className="dash-stat-value">{value}</strong>

      {meta && (
        <span className={`dash-stat-meta trend-${trend || "flat"}`}>{meta}</span>
      )}
    </article>
  );
}
