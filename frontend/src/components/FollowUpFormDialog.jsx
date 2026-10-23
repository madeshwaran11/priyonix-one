import { useEffect, useState } from "react";
import {
  FOLLOW_UP_ACTIVITY_TYPES,
  emptyFollowUpForm,
  followUpToForm,
} from "../followUpForm";
import { IconClose } from "./Icons";

function employeeLabel(employee) {
  return `${employee.fullName} (${employee.employeeId})`;
}

function leadLabel(lead) {
  return lead.company ? `${lead.name} — ${lead.company}` : lead.name;
}

function clientLabel(client) {
  return client.companyName
    ? `${client.name} — ${client.companyName}`
    : client.name;
}

/**
 * Modal form used for both creating and editing a follow-up.
 *
 * A follow-up must link to exactly one lead OR one client, so the form keeps
 * a link type selector and only ever submits the matching id.
 */
export default function FollowUpFormDialog({
  followUp,
  employees,
  leads,
  clients,
  defaultLeadId,
  defaultClientId,
  isSaving,
  error,
  fieldErrors,
  onClose,
  onSubmit,
}) {
  const isEdit = Boolean(followUp);
  const [form, setForm] = useState(() =>
    isEdit
      ? followUpToForm(followUp)
      : emptyFollowUpForm({ leadId: defaultLeadId, clientId: defaultClientId })
  );
  const [linkType, setLinkType] = useState(() => {
    if (isEdit) return followUp.clientId ? "client" : "lead";
    if (defaultClientId) return "client";
    return "lead";
  });

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

  function chooseLinkType(next) {
    setLinkType(next);
    setForm((previous) => ({
      ...previous,
      leadId: next === "lead" ? previous.leadId : "",
      clientId: next === "client" ? previous.clientId : "",
    }));
  }

  function handleSubmit(event) {
    event.preventDefault();
    onSubmit({
      ...form,
      leadId: linkType === "lead" ? form.leadId : "",
      clientId: linkType === "client" ? form.clientId : "",
    });
  }

  function errorFor(name) {
    return fieldErrors && fieldErrors[name];
  }

  const hasLeads = leads.length > 0;
  const hasClients = clients.length > 0;

  return (
    <div className="dash-modal-backdrop" role="presentation">
      <div
        className="dash-modal"
        role="dialog"
        aria-modal="true"
        aria-label={isEdit ? "Edit follow-up" : "New follow-up"}
      >
        <div className="dash-modal-head">
          <div>
            <span className="dash-eyebrow">Follow-ups</span>
            <h3>{isEdit ? "Edit follow-up" : "New follow-up"}</h3>
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
              <span>Activity type *</span>
              <select
                value={form.activityType}
                onChange={(event) =>
                  setField("activityType", event.target.value)
                }
                disabled={isSaving}
              >
                {FOLLOW_UP_ACTIVITY_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </select>
              {errorFor("activityType") && (
                <em className="dash-field-error">
                  {errorFor("activityType")}
                </em>
              )}
            </label>

            <label className="dash-field">
              <span>Due date *</span>
              <input
                type="date"
                value={form.dueDate}
                onChange={(event) => setField("dueDate", event.target.value)}
                required
                disabled={isSaving}
              />
              {errorFor("dueDate") && (
                <em className="dash-field-error">{errorFor("dueDate")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Linked to</span>
              <select
                value={linkType}
                onChange={(event) => chooseLinkType(event.target.value)}
                disabled={isSaving}
              >
                <option value="lead">Lead</option>
                <option value="client">Client</option>
              </select>
            </label>

            {linkType === "lead" ? (
              <label className="dash-field">
                <span>Lead *</span>
                <select
                  value={form.leadId}
                  onChange={(event) => setField("leadId", event.target.value)}
                  required
                  disabled={isSaving || !hasLeads}
                >
                  <option value="">
                    {hasLeads ? "Select a lead…" : "No leads available"}
                  </option>
                  {leads.map((lead) => (
                    <option key={lead.id} value={lead.id}>
                      {leadLabel(lead)}
                    </option>
                  ))}
                </select>
                {errorFor("leadId") && (
                  <em className="dash-field-error">{errorFor("leadId")}</em>
                )}
              </label>
            ) : (
              <label className="dash-field">
                <span>Client *</span>
                <select
                  value={form.clientId}
                  onChange={(event) => setField("clientId", event.target.value)}
                  required
                  disabled={isSaving || !hasClients}
                >
                  <option value="">
                    {hasClients ? "Select a client…" : "No clients available"}
                  </option>
                  {clients.map((client) => (
                    <option key={client.id} value={client.id}>
                      {clientLabel(client)}
                    </option>
                  ))}
                </select>
                {errorFor("clientId") && (
                  <em className="dash-field-error">{errorFor("clientId")}</em>
                )}
              </label>
            )}

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

            <label className="dash-field dash-field-wide">
              <span>Notes</span>
              <textarea
                value={form.notes}
                onChange={(event) => setField("notes", event.target.value)}
                placeholder="Agenda, call outcome, next steps…"
                rows={3}
                maxLength={2000}
                disabled={isSaving}
              />
              {errorFor("notes") && (
                <em className="dash-field-error">{errorFor("notes")}</em>
              )}
            </label>

            <label className="dash-checkbox-inline dash-field-wide">
              <input
                type="checkbox"
                checked={form.completed}
                onChange={(event) => setField("completed", event.target.checked)}
                disabled={isSaving}
              />
              <span>Completed</span>
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
              {isSaving
                ? "Saving…"
                : isEdit
                  ? "Save changes"
                  : "Create follow-up"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
