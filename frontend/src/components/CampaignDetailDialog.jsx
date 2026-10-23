import { useEffect, useState } from "react";
import { listLeads, updateCampaignStatus } from "../api";
import {
  formatDate,
  formatMoney,
  formatStatus,
  statusTone,
} from "../format";
import StatCard from "./StatCard";
import StatusPanel from "./StatusPanel";
import { IconClose } from "./Icons";

/**
 * Campaign detail view: lifecycle status controls, derived performance
 * metrics, and the real leads attributed to this campaign.
 */
export default function CampaignDetailDialog({
  campaign,
  token,
  isAdmin,
  onClose,
  onEdit,
  onDelete,
  onAddLead,
  onChanged,
}) {
  const [current, setCurrent] = useState(campaign);
  const [status, setStatus] = useState(campaign.status || "PLANNED");
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);
  const [statusError, setStatusError] = useState("");
  const [statusMessage, setStatusMessage] = useState("");

  const [leadsState, setLeadsState] = useState("loading");
  const [leads, setLeads] = useState([]);
  const [leadsError, setLeadsError] = useState("");

  useEffect(() => {
    let cancelled = false;

    listLeads(token, { campaignId: current.id, page: 1, size: 100 })
      .then((data) => {
        if (cancelled) return;
        setLeads(data.items);
        setLeadsState("ready");
      })
      .catch((error) => {
        if (cancelled) return;
        setLeadsError(error.message || "Could not load attributed leads.");
        setLeadsState("error");
      });

    return () => {
      cancelled = true;
    };
  }, [token, current.id]);

  async function handleStatusSave() {
    setIsUpdatingStatus(true);
    setStatusError("");
    setStatusMessage("");

    try {
      const updated = await updateCampaignStatus(token, current.id, status);
      setCurrent(updated);
      setStatus(updated.status);
      setStatusMessage(`Status changed to ${formatStatus(updated.status)}.`);
      if (onChanged) onChanged(updated);
    } catch (error) {
      setStatusError(error.message || "Could not update the campaign status.");
    } finally {
      setIsUpdatingStatus(false);
    }
  }

  return (
    <div className="dash-modal-backdrop" role="presentation">
      <div
        className="dash-modal wide"
        role="dialog"
        aria-modal="true"
        aria-label="Campaign details"
      >
        <div className="dash-modal-head">
          <div>
            <span className="dash-eyebrow">Marketing campaign</span>
            <h3>{current.name}</h3>
            <p className="dash-modal-text">
              {formatStatus(current.channel)} · {formatDate(current.startDate)} –{" "}
              {formatDate(current.endDate)}
            </p>
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
            {current.activeNow && (
              <span className="dash-status-pill status-active">Running now</span>
            )}
          </div>

          {statusMessage && (
            <p className="dash-success" role="status">
              {statusMessage}
            </p>
          )}
          {statusError && (
            <p className="dash-form-error" role="alert">
              {statusError}
            </p>
          )}

          <div className="dash-stat-grid">
            <StatCard label="Attributed leads" value={current.leadCount} />
            <StatCard label="Won leads" value={current.wonLeadCount} />
            <StatCard label="Conversions" value={current.convertedLeadCount} />
            <StatCard
              label="Conversion rate"
              value={`${current.conversionRate ?? 0}%`}
            />
            <StatCard
              label="Pipeline value"
              value={`₹ ${formatMoney(current.pipelineValue)}`}
            />
            <StatCard
              label="Won value"
              value={`₹ ${formatMoney(current.wonValue)}`}
            />
          </div>

          <dl className="dash-detail-grid">
            <div>
              <dt>Budget</dt>
              <dd>{current.budget == null ? "—" : `₹ ${formatMoney(current.budget)}`}</dd>
            </div>
            <div>
              <dt>Spend</dt>
              <dd>{current.spend == null ? "—" : `₹ ${formatMoney(current.spend)}`}</dd>
            </div>
            <div className="dash-detail-wide">
              <dt>Description</dt>
              <dd>{current.description || "—"}</dd>
            </div>
          </dl>

          <div className="dash-form-block">
            <h4>Status management</h4>
            <div className="dash-followup-add">
              <label className="dash-field">
                <span>Lifecycle status</span>
                <select
                  value={status}
                  onChange={(event) => setStatus(event.target.value)}
                  disabled={isUpdatingStatus}
                >
                  <option value="PLANNED">Planned</option>
                  <option value="ACTIVE">Active</option>
                  <option value="PAUSED">Paused</option>
                  <option value="COMPLETED">Completed</option>
                  <option value="CANCELLED">Cancelled</option>
                </select>
              </label>
              <button
                type="button"
                className="dash-button"
                onClick={handleStatusSave}
                disabled={isUpdatingStatus || status === current.status}
              >
                {isUpdatingStatus ? "Updating…" : "Update status"}
              </button>
            </div>
          </div>

          <div className="dash-form-block">
            <div className="dash-section-head">
              <h4>Attributed leads</h4>
              <span className="dash-live-badge">Live from CRM</span>
            </div>

            {leadsState === "loading" && (
              <StatusPanel
                variant="loading"
                title="Loading attributed leads…"
              />
            )}

            {leadsState === "error" && (
              <p className="dash-form-error" role="alert">
                {leadsError}
              </p>
            )}

            {leadsState === "ready" && leads.length === 0 && (
              <StatusPanel
                variant="empty"
                title="No leads attributed yet"
                description="Add a lead to this campaign to start measuring performance."
              />
            )}

            {leadsState === "ready" && leads.length > 0 && (
              <div className="dash-table-wrap">
                <table className="dash-table">
                  <thead>
                    <tr>
                      <th>Lead</th>
                      <th>Status</th>
                      <th>Est. value</th>
                      <th>Converted client</th>
                    </tr>
                  </thead>
                  <tbody>
                    {leads.map((lead) => (
                      <tr key={lead.id}>
                        <td>
                          <strong>{lead.name}</strong>
                          <em className="dash-cell-sub">{lead.email}</em>
                        </td>
                        <td>
                          <span
                            className={`dash-status-pill status-${statusTone(lead.status)}`}
                          >
                            {formatStatus(lead.status)}
                          </span>
                        </td>
                        <td>
                          {lead.estimatedValue == null
                            ? "—"
                            : `₹ ${formatMoney(lead.estimatedValue)}`}
                        </td>
                        <td>{lead.convertedClientName || "—"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
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
            className="dash-button ghost"
            onClick={() => onAddLead(current)}
          >
            Add lead
          </button>
          <button
            type="button"
            className="dash-button"
            onClick={() => onEdit(current)}
          >
            Edit campaign
          </button>
        </div>
      </div>
    </div>
  );
}
