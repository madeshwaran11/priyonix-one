import { useEffect, useState } from "react";
import {
  CLIENT_STATUSES,
  clientToForm,
  emptyClientForm,
} from "../clientForm";
import { IconClose } from "./Icons";

function employeeLabel(employee) {
  return `${employee.fullName} (${employee.employeeId})`;
}

/**
 * Modal form used for both creating and editing a client.
 *
 * The owner selector is populated from real employee records; no free-text
 * employee names are ever submitted.
 */
export default function ClientFormDialog({
  client,
  employees,
  isSaving,
  error,
  fieldErrors,
  onClose,
  onSubmit,
}) {
  const isEdit = Boolean(client);
  const [form, setForm] = useState(() =>
    isEdit ? clientToForm(client) : emptyClientForm()
  );

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
        aria-label={isEdit ? "Edit client" : "New client"}
      >
        <div className="dash-modal-head">
          <div>
            <span className="dash-eyebrow">Clients</span>
            <h3>{isEdit ? "Edit client" : "New client"}</h3>
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
              <span>Client name *</span>
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
              <span>Company name</span>
              <input
                value={form.companyName}
                onChange={(event) => setField("companyName", event.target.value)}
                placeholder="Northwind Ltd"
                maxLength={160}
                disabled={isSaving}
              />
              {errorFor("companyName") && (
                <em className="dash-field-error">{errorFor("companyName")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Email *</span>
              <input
                type="email"
                value={form.email}
                onChange={(event) => setField("email", event.target.value)}
                placeholder="hello@northwind.com"
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
              <span>Industry</span>
              <input
                value={form.industry}
                onChange={(event) => setField("industry", event.target.value)}
                placeholder="Retail"
                maxLength={120}
                disabled={isSaving}
              />
              {errorFor("industry") && (
                <em className="dash-field-error">{errorFor("industry")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Status</span>
              <select
                value={form.status}
                onChange={(event) => setField("status", event.target.value)}
                disabled={isSaving}
              >
                {CLIENT_STATUSES.map((status) => (
                  <option key={status} value={status}>
                    {status}
                  </option>
                ))}
              </select>
            </label>

            <label className="dash-field">
              <span>Account owner</span>
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
              <span>Address</span>
              <input
                value={form.address}
                onChange={(event) => setField("address", event.target.value)}
                placeholder="12 Market Street, Bengaluru"
                maxLength={400}
                disabled={isSaving}
              />
              {errorFor("address") && (
                <em className="dash-field-error">{errorFor("address")}</em>
              )}
            </label>

            <label className="dash-field dash-field-wide">
              <span>Notes</span>
              <textarea
                value={form.notes}
                onChange={(event) => setField("notes", event.target.value)}
                placeholder="Account context, preferences, key contacts…"
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
              {isSaving
                ? "Saving…"
                : isEdit
                  ? "Save changes"
                  : "Create client"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
