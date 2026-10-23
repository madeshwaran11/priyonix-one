import { useEffect, useState } from "react";
import {
  emptyPaymentForm,
  paymentToForm,
  PAYMENT_METHODS,
} from "../paymentForm";
import { IconClose } from "./Icons";
import { formatMoney } from "../format";

function invoiceLabel(invoice) {
  const suffix = invoice.clientName ? ` — ${invoice.clientName}` : "";
  return `${invoice.invoiceNumber}${suffix}`;
}

export default function PaymentFormDialog({
  payment,
  invoices,
  lockedInvoice,
  isSaving,
  error,
  fieldErrors,
  onClose,
  onSubmit,
}) {
  const isEdit = Boolean(payment);
  const [form, setForm] = useState(() =>
    isEdit
      ? paymentToForm(payment)
      : emptyPaymentForm({
          invoiceId: lockedInvoice ? lockedInvoice.id : "",
          amount: lockedInvoice ? lockedInvoice.balance : "",
        })
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

  const invoiceOptions = lockedInvoice
    ? [lockedInvoice]
    : invoices;

  const selectedInvoice = invoiceOptions.find(
    (inv) => inv.id === form.invoiceId
  );

  return (
    <div className="dash-modal-backdrop" role="presentation">
      <div
        className="dash-modal"
        role="dialog"
        aria-modal="true"
        aria-label={isEdit ? "Edit payment" : "Record payment"}
      >
        <div className="dash-modal-head">
          <div>
            <span className="dash-eyebrow">Finance · Payments</span>
            <h3>{isEdit ? "Edit payment" : "Record payment"}</h3>
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
          <label className="dash-field">
            <span>Invoice *</span>
            {lockedInvoice ? (
              <input
                value={invoiceLabel(lockedInvoice)}
                readOnly
                disabled
              />
            ) : (
              <select
                value={form.invoiceId}
                onChange={(event) => setField("invoiceId", event.target.value)}
                disabled={isSaving}
                required
              >
                <option value="">Select invoice</option>
                {invoiceOptions.map((inv) => (
                  <option key={inv.id} value={inv.id}>
                    {invoiceLabel(inv)}
                  </option>
                ))}
              </select>
            )}
            {errorFor("invoiceId") && (
              <em className="dash-field-error">{errorFor("invoiceId")}</em>
            )}
          </label>

          <div className="dash-form-grid">
            <label className="dash-field">
              <span>Amount (₹) *</span>
              <input
                type="number"
                min="0.01"
                step="0.01"
                value={form.amount}
                onChange={(event) => setField("amount", event.target.value)}
                placeholder="0.00"
                disabled={isSaving}
                required
              />
              {selectedInvoice && (
                <small className="dash-form-hint">
                  Outstanding balance: ₹ {formatMoney(selectedInvoice.balance)}
                </small>
              )}
              {errorFor("amount") && (
                <em className="dash-field-error">{errorFor("amount")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Payment date *</span>
              <input
                type="date"
                value={form.paymentDate}
                onChange={(event) => setField("paymentDate", event.target.value)}
                disabled={isSaving}
                required
              />
              {errorFor("paymentDate") && (
                <em className="dash-field-error">{errorFor("paymentDate")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Method *</span>
              <select
                value={form.method}
                onChange={(event) => setField("method", event.target.value)}
                disabled={isSaving}
              >
                {PAYMENT_METHODS.map((method) => (
                  <option key={method} value={method}>
                    {method.replace(/_/g, " ")}
                  </option>
                ))}
              </select>
              {errorFor("method") && (
                <em className="dash-field-error">{errorFor("method")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Reference number</span>
              <input
                value={form.referenceNumber}
                onChange={(event) =>
                  setField("referenceNumber", event.target.value)
                }
                placeholder="UTR, cheque, receipt"
                maxLength={120}
                disabled={isSaving}
              />
              {errorFor("referenceNumber") && (
                <em className="dash-field-error">{errorFor("referenceNumber")}</em>
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
              {isSaving ? "Saving…" : isEdit ? "Save changes" : "Record payment"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
