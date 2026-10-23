import { useEffect, useState } from "react";
import {
  INVOICE_ISSUE_STATUSES,
  computeInvoicePreview,
  emptyInvoiceForm,
  invoiceToForm,
} from "../invoiceForm";
import { IconClose } from "./Icons";
import { formatMoney } from "../format";

function clientLabel(client) {
  return client.companyName
    ? `${client.name} — ${client.companyName}`
    : client.name;
}

function projectLabel(project) {
  if (project.projectCode) {
    return project.projectName
      ? `${project.projectCode} — ${project.projectName}`
      : project.projectCode;
  }
  return project.projectName;
}

export default function InvoiceFormDialog({
  invoice,
  clients,
  projects,
  isSaving,
  error,
  fieldErrors,
  onClose,
  onSubmit,
}) {
  const isEdit = Boolean(invoice);
  const [form, setForm] = useState(() =>
    isEdit ? invoiceToForm(invoice) : emptyInvoiceForm()
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

  function setLineItem(index, field, value) {
    setForm((previous) => {
      const next = previous.lineItems.map((item, i) =>
        i === index ? { ...item, [field]: value } : item
      );
      return { ...previous, lineItems: next };
    });
  }

  function addLineItem() {
    setForm((previous) => ({
      ...previous,
      lineItems: [...previous.lineItems, emptyInvoiceForm().lineItems[0]],
    }));
  }

  function removeLineItem(index) {
    setForm((previous) => ({
      ...previous,
      lineItems: previous.lineItems.filter((_, i) => i !== index),
    }));
  }

  function handleSubmit(event) {
    event.preventDefault();
    onSubmit(form);
  }

  function errorFor(name) {
    return fieldErrors && fieldErrors[name];
  }

  const preview = computeInvoicePreview(form);
  const hasClients = clients.length > 0;

  return (
    <div className="dash-modal-backdrop" role="presentation">
      <div
        className="dash-modal wide"
        role="dialog"
        aria-modal="true"
        aria-label={isEdit ? "Edit invoice" : "New invoice"}
      >
        <div className="dash-modal-head">
          <div>
            <span className="dash-eyebrow">Finance · Invoices</span>
            <h3>{isEdit ? "Edit invoice" : "New invoice"}</h3>
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
              <span>Client *</span>
              <select
                value={form.clientId}
                onChange={(event) => setField("clientId", event.target.value)}
                disabled={isSaving}
                required
              >
                <option value="">Select client</option>
                {clients.map((client) => (
                  <option key={client.id} value={client.id}>
                    {clientLabel(client)}
                  </option>
                ))}
              </select>
              {errorFor("clientId") && (
                <em className="dash-field-error">{errorFor("clientId")}</em>
              )}
              {!hasClients && (
                <small className="dash-form-hint">
                  Create a client first to issue invoices.
                </small>
              )}
            </label>

            <label className="dash-field">
              <span>Project (optional)</span>
              <select
                value={form.projectId}
                onChange={(event) => setField("projectId", event.target.value)}
                disabled={isSaving}
              >
                <option value="">No project</option>
                {projects.map((project) => (
                  <option key={project.id} value={project.id}>
                    {projectLabel(project)}
                  </option>
                ))}
              </select>
              {errorFor("projectId") && (
                <em className="dash-field-error">{errorFor("projectId")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Issue date *</span>
              <input
                type="date"
                value={form.issueDate}
                onChange={(event) => setField("issueDate", event.target.value)}
                disabled={isSaving}
                required
              />
              {errorFor("issueDate") && (
                <em className="dash-field-error">{errorFor("issueDate")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Due date *</span>
              <input
                type="date"
                value={form.dueDate}
                onChange={(event) => setField("dueDate", event.target.value)}
                disabled={isSaving}
                required
              />
              {errorFor("dueDate") && (
                <em className="dash-field-error">{errorFor("dueDate")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Status (on issue)</span>
              <select
                value={form.status}
                onChange={(event) => setField("status", event.target.value)}
                disabled={isSaving}
              >
                {INVOICE_ISSUE_STATUSES.map((status) => (
                  <option key={status} value={status}>
                    {status.replace(/_/g, " ")}
                  </option>
                ))}
              </select>
              <small className="dash-form-hint">
                Payments automatically derive PAID/PARTIALLY PAID/OVERDUE.
              </small>
              {errorFor("status") && (
                <em className="dash-field-error">{errorFor("status")}</em>
              )}
            </label>
          </div>

          <section className="dash-section">
            <div className="dash-section-head">
              <h4>Line items *</h4>
              <button
                type="button"
                className="dash-link-button"
                onClick={addLineItem}
                disabled={isSaving}
              >
                Add line
              </button>
            </div>

            {form.lineItems.map((item, index) => (
              <div key={index} className="dash-form-grid">
                <label className="dash-field dash-field-wide">
                  <span>Description *</span>
                  <input
                    value={item.description}
                    onChange={(event) =>
                      setLineItem(index, "description", event.target.value)
                    }
                    placeholder="Design & wireframing"
                    maxLength={300}
                    disabled={isSaving}
                    required
                  />
                </label>

                <label className="dash-field">
                  <span>Quantity *</span>
                  <input
                    type="number"
                    min="0.0001"
                    step="0.0001"
                    value={item.quantity}
                    onChange={(event) =>
                      setLineItem(index, "quantity", event.target.value)
                    }
                    disabled={isSaving}
                    required
                  />
                </label>

                <label className="dash-field">
                  <span>Unit price (₹) *</span>
                  <input
                    type="number"
                    min="0"
                    step="0.01"
                    value={item.unitPrice}
                    onChange={(event) =>
                      setLineItem(index, "unitPrice", event.target.value)
                    }
                    disabled={isSaving}
                    required
                  />
                </label>

                <label className="dash-field">
                  <span>Amount</span>
                  <input
                    value={formatMoney(
                      (Number(item.quantity) || 0) * (Number(item.unitPrice) || 0)
                    )}
                    readOnly
                    disabled
                  />
                </label>

                {form.lineItems.length > 1 && (
                  <button
                    type="button"
                    className="dash-link-button danger"
                    onClick={() => removeLineItem(index)}
                    disabled={isSaving}
                  >
                    Remove
                  </button>
                )}
              </div>
            ))}
            {errorFor("lineItems") && (
              <em className="dash-field-error">{errorFor("lineItems")}</em>
            )}
          </section>

          <div className="dash-form-grid">
            <label className="dash-field">
              <span>Tax rate (%)</span>
              <input
                type="number"
                min="0"
                max="100"
                step="0.01"
                value={form.taxRate}
                onChange={(event) => setField("taxRate", event.target.value)}
                placeholder="18"
                disabled={isSaving}
              />
              {errorFor("taxRate") && (
                <em className="dash-field-error">{errorFor("taxRate")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Discount (₹)</span>
              <input
                type="number"
                min="0"
                step="0.01"
                value={form.discount}
                onChange={(event) => setField("discount", event.target.value)}
                placeholder="0.00"
                disabled={isSaving}
              />
              {errorFor("discount") && (
                <em className="dash-field-error">{errorFor("discount")}</em>
              )}
            </label>
          </div>

          <label className="dash-field dash-field-wide">
            <span>Notes</span>
            <textarea
              rows="2"
              value={form.notes}
              onChange={(event) => setField("notes", event.target.value)}
              maxLength={2000}
              disabled={isSaving}
            />
            {errorFor("notes") && (
              <em className="dash-field-error">{errorFor("notes")}</em>
            )}
          </label>

          <div className="dash-detail-grid">
            <article className="dash-card">
              <span className="dash-eyebrow">Preview</span>
              <div className="dash-detail-body">
                <dl className="dash-detail-grid">
                  <div>
                    <dt>Subtotal</dt>
                    <dd>₹ {formatMoney(preview.subtotal)}</dd>
                  </div>
                  <div>
                    <dt>Discount</dt>
                    <dd>- ₹ {formatMoney(preview.discount)}</dd>
                  </div>
                  <div>
                    <dt>Tax ({preview.taxRate || 0}%)</dt>
                    <dd>₹ {formatMoney(preview.tax)}</dd>
                  </div>
                  <div>
                    <dt>Total</dt>
                    <dd>
                      <strong>₹ {formatMoney(preview.total)}</strong>
                    </dd>
                  </div>
                </dl>
              </div>
            </article>
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
              {isSaving ? "Saving…" : isEdit ? "Save changes" : "Create invoice"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
