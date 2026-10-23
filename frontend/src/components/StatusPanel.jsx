import { IconInbox, IconRefresh } from "./Icons";

// Reusable loading / empty / error panel used across dashboard pages.
export default function StatusPanel({
  variant = "empty",
  title,
  description,
  actionLabel,
  onAction,
}) {
  return (
    <div className={`dash-status-panel is-${variant}`} role="status">
      <div className="dash-status-icon">
        {variant === "error" || variant === "loading" ? (
          <IconRefresh className="dash-status-spin" />
        ) : (
          <IconInbox />
        )}
      </div>

      <strong>{title}</strong>
      {description && <p>{description}</p>}

      {variant === "error" && actionLabel && onAction && (
        <button type="button" className="dash-button" onClick={onAction}>
          {actionLabel}
        </button>
      )}
    </div>
  );
}
