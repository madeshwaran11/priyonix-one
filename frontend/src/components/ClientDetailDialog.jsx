import { formatDate, formatStatus, statusTone } from "../format";
import { IconClose } from "./Icons";
import FollowUpSection from "./FollowUpSection";

/**
 * Client detail view: the full account record plus its follow-up activities.
 * Editing and deleting are delegated back to the page so a single dialog is
 * open at a time.
 */
export default function ClientDetailDialog({
  client,
  token,
  isAdmin,
  employees,
  onClose,
  onEdit,
  onDelete,
}) {
  return (
    <div className="dash-modal-backdrop" role="presentation">
      <div
        className="dash-modal wide"
        role="dialog"
        aria-modal="true"
        aria-label="Client details"
      >
        <div className="dash-modal-head">
          <div>
            <span className="dash-eyebrow">Client</span>
            <h3>{client.name}</h3>
            {client.companyName && (
              <p className="dash-modal-text">{client.companyName}</p>
            )}
          </div>
          <button
            type="button"
            className="dash-icon-button"
            onClick={onClose}
            aria-label="Close"
          >
            <IconClose />
          </button>
        </div>

        <div className="dash-detail-body">
          <div className="dash-detail-tags">
            <span
              className={`dash-status-pill status-${statusTone(client.status)}`}
            >
              {formatStatus(client.status)}
            </span>
            {client.overdueFollowUpCount > 0 && (
              <span className="dash-overdue-badge">
                {client.overdueFollowUpCount} overdue follow-up(s)
              </span>
            )}
          </div>

          <dl className="dash-detail-grid">
            <div>
              <dt>Email</dt>
              <dd>{client.email || "—"}</dd>
            </div>
            <div>
              <dt>Phone</dt>
              <dd>{client.phone || "—"}</dd>
            </div>
            <div>
              <dt>Industry</dt>
              <dd>{client.industry || "—"}</dd>
            </div>
            <div>
              <dt>Account owner</dt>
              <dd>{client.assignedTo ? client.assignedTo.fullName : "Unassigned"}</dd>
            </div>
            <div>
              <dt>Follow-ups</dt>
              <dd>
                {client.followUpCount} total · {client.overdueFollowUpCount}{" "}
                overdue
              </dd>
            </div>
            <div>
              <dt>Created</dt>
              <dd>{formatDate(client.createdAt)}</dd>
            </div>
            <div className="dash-detail-wide">
              <dt>Address</dt>
              <dd>{client.address || "—"}</dd>
            </div>
            <div className="dash-detail-wide">
              <dt>Notes</dt>
              <dd>{client.notes || "—"}</dd>
            </div>
          </dl>

          <FollowUpSection
            token={token}
            isAdmin={isAdmin}
            clientId={client.id}
            employees={employees}
          />
        </div>

        <div className="dash-form-actions">
          {isAdmin && (
            <button
              type="button"
              className="dash-button danger"
              onClick={() => onDelete(client)}
            >
              Delete
            </button>
          )}
          <button
            type="button"
            className="dash-button ghost"
            onClick={onClose}
          >
            Close
          </button>
          <button
            type="button"
            className="dash-button"
            onClick={() => onEdit(client)}
          >
            Edit client
          </button>
        </div>
      </div>
    </div>
  );
}
