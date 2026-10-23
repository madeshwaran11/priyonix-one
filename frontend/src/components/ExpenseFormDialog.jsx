import { useEffect, useState } from "react";
import {
  emptyExpenseForm,
  expenseToForm,
  EXPENSE_CATEGORIES,
  PAYMENT_METHODS,
} from "../expenseForm";
import { IconClose } from "./Icons";

export default function ExpenseFormDialog({
  expense,
  isSaving,
  error,
  fieldErrors,
  onClose,
  onSubmit,
}) {
  const isEdit = Boolean(expense);
  const [form, setForm] = useState(() =>
    isEdit ? expenseToForm(expense) : emptyExpenseForm()
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
        className="dash-modal"
        role="dialog"
        aria-modal="true"
        aria-label={isEdit ? "Edit expense" : "New expense"}
      >
        <div className="dash-modal-head">
          <div>
            <span className="dash-eyebrow">Finance · Expenses</span>
            <h3>{isEdit ? "Edit expense" : "New expense"}</h3>
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
            <label className="dash-field dash-field-wide">
              <span>Title *</span>
              <input
                value={form.title}
                onChange={(event) => setField("title", event.target.value)}
                placeholder="Flights to Delhi"
                maxLength={200}
                disabled={isSaving}
                required
              />
              {errorFor("title") && (
                <em className="dash-field-error">{errorFor("title")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Category *</span>
              <select
                value={form.category}
                onChange={(event) => setField("category", event.target.value)}
                disabled={isSaving}
              >
                {EXPENSE_CATEGORIES.map((category) => (
                  <option key={category} value={category}>
                    {category.replace(/_/g, " ")}
                  </option>
                ))}
              </select>
              {errorFor("category") && (
                <em className="dash-field-error">{errorFor("category")}</em>
              )}
            </label>

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
              {errorFor("amount") && (
                <em className="dash-field-error">{errorFor("amount")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Expense date *</span>
              <input
                type="date"
                value={form.expenseDate}
                onChange={(event) => setField("expenseDate", event.target.value)}
                disabled={isSaving}
                required
              />
              {errorFor("expenseDate") && (
                <em className="dash-field-error">{errorFor("expenseDate")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Vendor</span>
              <input
                value={form.vendor}
                onChange={(event) => setField("vendor", event.target.value)}
                placeholder="Acme Travel"
                maxLength={160}
                disabled={isSaving}
              />
              {errorFor("vendor") && (
                <em className="dash-field-error">{errorFor("vendor")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Payment method</span>
              <select
                value={form.paymentMethod}
                onChange={(event) =>
                  setField("paymentMethod", event.target.value)
                }
                disabled={isSaving}
              >
                {PAYMENT_METHODS.map((method) => (
                  <option key={method} value={method}>
                    {method.replace(/_/g, " ")}
                  </option>
                ))}
              </select>
              {errorFor("paymentMethod") && (
                <em className="dash-field-error">{errorFor("paymentMethod")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Receipt reference</span>
              <input
                value={form.receiptReference}
                onChange={(event) =>
                  setField("receiptReference", event.target.value)
                }
                placeholder="RCPT-001"
                maxLength={120}
                disabled={isSaving}
              />
              {errorFor("receiptReference") && (
                <em className="dash-field-error">{errorFor("receiptReference")}</em>
              )}
            </label>

            {isEdit && (
              <label className="dash-field">
                <span>Status</span>
                <input
                  value={expense.status}
                  readOnly
                  disabled
                />
                <small className="dash-form-hint">
                  Use "Status" action to approve/reject/reimburse.
                </small>
              </label>
            )}
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
              {isSaving ? "Saving…" : isEdit ? "Save changes" : "Create expense"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
