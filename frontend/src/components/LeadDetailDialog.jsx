import { useState } from "react";
import { convertLead } from "../api";
import { formatDate, formatStatus, priorityTone, statusTone } from "../format";
import { IconClose } from "./Icons";
import FollowUpSection from "./FollowUpSection";

function formatValue(value) {
  if (value === null || value === undefined || value === "") return "—";
  const numeric = Number(value);
  if (Number.isNaN(numeric)) return value;
  return numeric.toLocaleString(undefined, {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  });
}

/**
 * Lead detail view: the full pipeline record, its follow-up activities and the
 * one-click conversion into a client. Conversion is refused by the backend
 * unless the lead is WON and has not already been converted; existing clients
 * with the same email are reused rather than duplicated.
 */
export default function LeadDetailDialog({
  lead,
  token,
  isAdmin,
  employees,
  onClose,
  onEdit,
  onDelete,
  onChanged,
}) {
  const [current, setCurrent] = useState(lead);
  const [isConverting, setIsConverting] = useState(false);
  const [convertError, setConvertError] = useState("");
  const [convertMessage, setConvertMessage] = useState("");

  const canConvert = current.status === "WON" && !current.convertedClientId;
  const isConverted = Boolean(current.convertedClientId);

  async function handleConvert() {
    setIsConverting(true);
    setConvertError("");
    setConvertMessage("");

    try {
      const result = await convertLead(token, current.id);
      setCurrent(result.lead);
      setConvertMessage(
        result.created
          ? `Client created: ${result.client.name}.`
          : `Linked to the existing client ${result.client.name}.`
      );
      if (onChanged) onChanged(result.lead);
    } catch (error) {
      setConvertError(error.message || "Could not convert the lead.");
    } finally {
      setIsConverting(false);
    }
  }

  return (
    <div className="dash-modal-backdrop" role="presentation">
      <div
        className="dash-modal wide"
        role="dialog"
        aria-modal="true"
        aria-label="Lead details"
      >
        <div className="dash-modal-head">
          <div>
            <span className="dash-eyebrow">Lead</span>
            <h3>{current.name}</h3>
            {current.company && (
              <p className="dash-modal-text">{current.company}</p>
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
              className={`dash-status-pill status-${statusTone(current.status)}`}
            >
              {formatStatus(current.status)}
            </span>
            <span
              className={`dash-priority-pill priority-${priorityTone(current.priority)}`}
            >
              {current.priority}
            </span>
            {current.overdueFollowUpCount > 0 && (
              <span className="dash-overdue-badge">
                {current.overdueFollowUpCount} overdue follow-up(s)
              </span>
            )}
          </div>

          <dl className="dash-detail-grid">
            <div>
              <dt>Email</dt>
              <dd>{current.email || "—"}</dd>
            </div>
            <div>
              <dt>Phone</dt>
              <dd>{current.phone || "—"}</dd>
            </div>
            <div>
              <dt>Source</dt>
              <dd>{current.source || "—"}</dd>
            </div>
            <div>
              <dt>Campaign</dt>
              <dd>{current.campaignName || "Direct"}</dd>
            </div>
            <div>
              <dt>Assigned to</dt>
              <dd>
                {current.assignedTo ? current.assignedTo.fullName : "Unassigned"}
              </dd>
            </div>
            <div>
              <dt>Estimated value</dt>
              <dd>{formatValue(current.estimatedValue)}</dd>
            </div>
            <div>
              <dt>Follow-up date</dt>
              <dd>{formatDate(current.followUpDate)}</dd>
            </div>
            <div>
              <dt>Follow-ups</dt>
              <dd>
                {current.followUpCount} total · {current.overdueFollowUpCount}{" "}
                overdue
              </dd>
            </div>
            <div>
              <dt>Converted client</dt>
              <dd>{current.convertedClientName || "Not converted"}</dd>
            </div>
            <div className="dash-detail-wide">
              <dt>Notes</dt>
              <dd>{current.notes || "—"}</dd>
            </div>
          </dl>

          <div className="dash-form-block">
            <h4>Conversion</h4>

            {convertMessage && (
              <p className="dash-success" role="status">
                {convertMessage}
              </p>
            )}
            {convertError && (
              <p className="dash-form-error" role="alert">
                {convertError}
              </p>
            )}

            {isConverted && !convertMessage && (
              <p className="dash-form-hint">
                This lead has already been converted to a client.
              </p>
            )}

            {!isConverted && !canConvert && (
              <p className="dash-form-hint">
                Only leads marked <strong>WON</strong> can be converted into a
                client.
              </p>
            )}

            {canConvert && (
              <button
                type="button"
                className="dash-button"
                onClick={handleConvert}
                disabled={isConverting}
              >
                {isConverting ? "Converting…" : "Convert to client"}
              </button>
            )}
          </div>

          <FollowUpSection
            token={token}
            isAdmin={isAdmin}
            leadId={current.id}
            employees={employees}
          />
        </div>

        <div className="dash-form-actions">
          {isAdmin && (
            <button
              type="button"
              className="dash-button danger"
              onClick={() => onDelete(current)}
            >
              Delete
            </button>
          )}
          <button type="button" className="dash-button ghost" onClick={onClose}>
            Close
          </button>
          <button
            type="button"
            className="dash-button"
            onClick={() => onEdit(current)}
          >
            Edit lead
          </button>
        </div>
      </div>
    </div>
  );
}
