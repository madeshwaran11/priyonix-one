export default function BackgroundRefreshStatus({
  refreshing,
  error,
  label = "records",
}) {
  if (refreshing) {
    return (
      <p className="dash-form-hint" role="status" aria-live="polite">
        Refreshing {label}… Current results remain available.
      </p>
    );
  }

  if (error) {
    return (
      <p className="dash-form-error" role="alert">
        Could not refresh {label}. {error}
      </p>
    );
  }

  return null;
}
