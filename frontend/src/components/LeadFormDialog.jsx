import { useEffect, useState } from "react";
import {
  LEAD_PRIORITIES,
  LEAD_SOURCES,
  LEAD_STATUSES,
  emptyLeadForm,
  leadToForm,
} from "../leadForm";
import { IconClose } from "./Icons";

function employeeLabel(employee) {
  return `${employee.fullName} (${employee.employeeId})`;
}

/**
 * Modal form used for both creating and editing a lead.
 *
 * Source is a free-text field with suggestions (a native datalist), so any
 * channel can be recorded while still guiding the common values. The assignee
 * selector is populated from real employee records.
 */
export default function LeadFormDialog({
  lead,
  employees,
  campaigns = [],
  defaultCampaignId = "",
  isSaving,
  error,
  fieldErrors,
  onClose,
  onSubmit,
}) {
  const isEdit = Boolean(lead);
  const [form, setForm] = useState(() =>
    isEdit ? leadToForm(lead) : emptyLeadForm(defaultCampaignId)
  );
  const campaignIsMissing =
    form.campaignId &&
    !campaigns.some((campaign) => campaign.id === form.campaignId);

  useEffect(() => {
    function onKeyDown(event) {
      if (event.key === "Escape") onClose();
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  function setField(name, value) {
    setForm((previous) => ({ ...previous, [name]: value }));
  }

  function handleSubmit(event) {
    event.preventDefault();
    onSubmit(form);
  }

  function errorFor(name) {
    return fieldErrors && fieldErrors[name];
  }

  return (
    <div className="dash-modal-backdrop" role="presentation">
      <div
        className="dash-modal wide"
        role="dialog"
        aria-modal="true"
        aria-label={isEdit ? "Edit lead" : "New lead"}
      >
        <div className="dash-modal-head">
          <div>
            <span className="dash-eyebrow">Leads</span>
            <h3>{isEdit ? "Edit lead" : "New lead"}</h3>
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

        <form className="dash-form" onSubmit={handleSubmit}>
          <div className="dash-form-grid">
            <label className="dash-field">
              <span>Lead name *</span>
              <input
                value={form.name}
                onChange={(event) => setField("name", event.target.value)}
                placeholder="Priya Sharma"
                maxLength={160}
                required
                disabled={isSaving}
              />
              {errorFor("name") && (
                <em className="dash-field-error">{errorFor("name")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Company</span>
              <input
                value={form.company}
                onChange={(event) => setField("company", event.target.value)}
                placeholder="Northwind Traders"
                maxLength={160}
                disabled={isSaving}
              />
              {errorFor("company") && (
                <em className="dash-field-error">{errorFor("company")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Email *</span>
              <input
                type="email"
                value={form.email}
                onChange={(event) => setField("email", event.target.value)}
                placeholder="priya@northwind.com"
                maxLength={254}
                required
                disabled={isSaving}
              />
              {errorFor("email") && (
                <em className="dash-field-error">{errorFor("email")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Phone</span>
              <input
                value={form.phone}
                onChange={(event) => setField("phone", event.target.value)}
                placeholder="+91 90000 00000"
                maxLength={30}
                disabled={isSaving}
              />
              {errorFor("phone") && (
                <em className="dash-field-error">{errorFor("phone")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Source</span>
              <input
                list="lead-sources"
                value={form.source}
                onChange={(event) => setField("source", event.target.value)}
                placeholder="Website"
                maxLength={80}
                disabled={isSaving}
              />
              <datalist id="lead-sources">
                {LEAD_SOURCES.map((source) => (
                  <option key={source} value={source} />
                ))}
              </datalist>
              {errorFor("source") && (
                <em className="dash-field-error">{errorFor("source")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Campaign</span>
              <select
                value={form.campaignId}
                onChange={(event) => setField("campaignId", event.target.value)}
                disabled={isSaving}
              >
                <option value="">Direct / no campaign</option>
                {campaignIsMissing && (
                  <option value={form.campaignId}>
                    {lead && lead.campaignName
                      ? lead.campaignName
                      : "Current campaign"}
                  </option>
                )}
                {campaigns.map((campaign) => (
                  <option key={campaign.id} value={campaign.id}>
                    {campaign.name}
                  </option>
                ))}
              </select>
              {errorFor("campaignId") && (
                <em className="dash-field-error">{errorFor("campaignId")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Status</span>
              <select
                value={form.status}
                onChange={(event) => setField("status", event.target.value)}
                disabled={isSaving}
              >
                {LEAD_STATUSES.map((status) => (
                  <option key={status} value={status}>
                    {status}
                  </option>
                ))}
              </select>
            </label>

            <label className="dash-field">
              <span>Priority</span>
              <select
                value={form.priority}
                onChange={(event) => setField("priority", event.target.value)}
                disabled={isSaving}
              >
                {LEAD_PRIORITIES.map((priority) => (
                  <option key={priority} value={priority}>
                    {priority}
                  </option>
                ))}
              </select>
            </label>

            <label className="dash-field">
              <span>Assigned to</span>
              <select
                value={form.assignedToId}
                onChange={(event) => setField("assignedToId", event.target.value)}
                disabled={isSaving}
              >
                <option value="">Unassigned</option>
                {employees.map((employee) => (
                  <option key={employee.id} value={employee.id}>
                    {employeeLabel(employee)}
                  </option>
                ))}
              </select>
              {errorFor("assignedToId") && (
                <em className="dash-field-error">{errorFor("assignedToId")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Estimated value</span>
              <input
                type="number"
                min="0"
                step="0.01"
                value={form.estimatedValue}
                onChange={(event) =>
                  setField("estimatedValue", event.target.value)
                }
                placeholder="50000"
                disabled={isSaving}
              />
              {errorFor("estimatedValue") && (
                <em className="dash-field-error">
                  {errorFor("estimatedValue")}
                </em>
              )}
            </label>

            <label className="dash-field">
              <span>Follow-up date</span>
              <input
                type="date"
                value={form.followUpDate}
                onChange={(event) => setField("followUpDate", event.target.value)}
                disabled={isSaving}
              />
              {errorFor("followUpDate") && (
                <em className="dash-field-error">{errorFor("followUpDate")}</em>
              )}
            </label>

            <label className="dash-field dash-field-wide">
              <span>Notes</span>
              <textarea
                value={form.notes}
                onChange={(event) => setField("notes", event.target.value)}
                placeholder="Requirements, budget, decision makers…"
                rows={3}
                maxLength={2000}
                disabled={isSaving}
              />
              {errorFor("notes") && (
                <em className="dash-field-error">{errorFor("notes")}</em>
              )}
            </label>
          </div>

          {error && (
            <p className="dash-form-error" role="alert">
              {error}
            </p>
          )}

          <div className="dash-form-actions">
            <button
              type="button"
              className="dash-button ghost"
              onClick={onClose}
              disabled={isSaving}
            >
              Cancel
            </button>
            <button type="submit" className="dash-button" disabled={isSaving}>
              {isSaving ? "Saving…" : isEdit ? "Save changes" : "Create lead"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
