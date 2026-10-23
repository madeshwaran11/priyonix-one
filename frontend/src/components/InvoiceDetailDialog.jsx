import { useEffect, useState } from "react";
import {
  createPayment,
  listInvoicePayments,
  deletePayment,
} from "../api";
import { formatDate, formatMoney } from "../format";
import PaymentFormDialog from "./PaymentFormDialog";
import StatusPanel from "./StatusPanel";
import { IconClose } from "./Icons";

export default function InvoiceDetailDialog({
  invoice,
  token,
  isAdmin,
  onClose,
  onEdit,
  onDelete,
  onChanged,
}) {
  const [payments, setPayments] = useState([]);
  const [payState, setPayState] = useState("loading");
  const [payError, setPayError] = useState("");
  const [showPayForm, setShowPayForm] = useState(false);
  const [paySaving, setPaySaving] = useState(false);
  const [payFormError, setPayFormError] = useState("");
  const [payFieldErrors, setPayFieldErrors] = useState({});
  const [busy, setBusy] = useState("");
  const [message, setMessage] = useState("");
  const [paymentsKey, setPaymentsKey] = useState(0);

  useEffect(() => {
    function onKeyDown(event) {
      if (event.key === "Escape") onClose();
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  useEffect(() => {
    let cancelled = false;
    listInvoicePayments(token, invoice.id)
      .then((data) => {
        if (cancelled) return;
        setPayments(data);
        setPayError("");
        setPayState("ready");
      })
      .catch((error) => {
        if (cancelled) return;
        setPayError(error.message);
        setPayState("error");
      });
    return () => {
      cancelled = true;
    };
  }, [token, invoice.id, paymentsKey]);

  function reloadPayments() {
    setPayState("loading");
    setPayError("");
    setPaymentsKey((key) => key + 1);
  }

  async function handleDeletePayment(payment) {
    setBusy(payment.id);
    setMessage("");
    setPayError("");
    try {
      await deletePayment(token, payment.id);
      setMessage("Payment deleted.");
      reloadPayments();
      if (onChanged) onChanged();
    } catch (error) {
      setPayError(error.message);
      setPayState("error");
    } finally {
      setBusy("");
    }
  }

  async function handleSubmitPayment(form) {
    setPaySaving(true);
    setPayFormError("");
    setPayFieldErrors({});
    try {
      await createPayment(token, {
        invoiceId: form.invoiceId,
        amount: Number(form.amount),
        paymentDate: form.paymentDate,
        method: form.method,
        referenceNumber: form.referenceNumber || null,
        notes: form.notes || null,
      });
      setShowPayForm(false);
      setMessage("Payment recorded.");
      reloadPayments();
      if (onChanged) onChanged();
    } catch (error) {
      if (error.code === "UNAUTHORIZED") {
        setPayFormError("Your session expired. Please sign in again.");
      } else {
        setPayFormError(error.message);
        setPayFieldErrors(error.fieldErrors || {});
      }
    } finally {
      setPaySaving(false);
    }
  }

  return (
    <div className="dash-modal-backdrop" role="presentation">
      <div
        className="dash-modal wide"
        role="dialog"
        aria-modal="true"
        aria-label="Invoice details"
      >
        <div className="dash-modal-head">
          <div>
            <span className="dash-eyebrow">Finance · Invoice</span>
            <h3>{invoice.invoiceNumber}</h3>
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

        <section className="dash-section">
          <div className="dash-section-head">
            <h4>Summary</h4>
            <div className="dash-row-actions">
              {!(Number(invoice.amountPaid) > 0) && <button
                type="button"
                className="dash-link-button"
                onClick={onEdit}
              >
                Edit
              </button>}
              {isAdmin && !(Number(invoice.amountPaid) > 0) && (
                <button
                  type="button"
                  className="dash-link-button danger"
                  onClick={onDelete}
                >
                  Delete
                </button>
              )}
            </div>
          </div>

          <div className="dash-detail-grid">
            <div>
              <dt className="dash-eyebrow">Client</dt>
              <dd>{invoice.clientName || invoice.clientId}</dd>
            </div>
            <div>
              <dt className="dash-eyebrow">Project</dt>
              <dd>
                {invoice.projectName ||
                  invoice.projectCode ||
                  "—"}
              </dd>
            </div>
            <div>
              <dt className="dash-eyebrow">Issue date</dt>
              <dd>{formatDate(invoice.issueDate)}</dd>
            </div>
            <div>
              <dt className="dash-eyebrow">Due date</dt>
              <dd>{formatDate(invoice.dueDate)}</dd>
            </div>
            <div>
              <dt className="dash-eyebrow">Status</dt>
              <dd>
                <span className={`dash-status-pill status-${(invoice.status || "").toLowerCase().replace(/_/g, "-")}`}>
                  {invoice.status}
                </span>
              </dd>
            </div>
            <div>
              <dt className="dash-eyebrow">Notes</dt>
              <dd>{invoice.notes || "—"}</dd>
            </div>
          </div>

          <div className="dash-detail-grid">
            <article className="dash-card">
              <span className="dash-eyebrow">Totals</span>
              <div className="dash-detail-body">
                <dl className="dash-detail-grid">
                  <div>
                    <dt>Subtotal</dt>
                    <dd>₹ {formatMoney(invoice.subtotal)}</dd>
                  </div>
                  <div>
                    <dt>Discount</dt>
                    <dd>- ₹ {formatMoney(invoice.discount)}</dd>
                  </div>
                  <div>
                    <dt>Tax ({invoice.taxRate || 0}%)</dt>
                    <dd>₹ {formatMoney(invoice.taxAmount)}</dd>
                  </div>
                  <div>
                    <dt>Total</dt>
                    <dd>
                      <strong>₹ {formatMoney(invoice.total)}</strong>
                    </dd>
                  </div>
                  <div>
                    <dt>Amount paid</dt>
                    <dd>₹ {formatMoney(invoice.amountPaid)}</dd>
                  </div>
                  <div>
                    <dt>Balance</dt>
                    <dd>
                      <strong>₹ {formatMoney(invoice.balance)}</strong>
                    </dd>
                  </div>
                </dl>
              </div>
            </article>
          </div>
        </section>

        <section className="dash-section">
          <div className="dash-section-head">
            <h4>Line items</h4>
          </div>
          <div className="dash-table-wrap">
            <table className="dash-table">
              <thead>
                <tr>
                  <th>Description</th>
                  <th>Quantity</th>
                  <th>Unit price</th>
                  <th>Amount</th>
                </tr>
              </thead>
              <tbody>
                {Array.isArray(invoice.lineItems) &&
                  invoice.lineItems.map((item, index) => (
                    <tr key={index}>
                      <td>{item.description}</td>
                      <td>{item.quantity}</td>
                      <td>₹ {formatMoney(item.unitPrice)}</td>
                      <td>₹ {formatMoney(item.amount)}</td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
        </section>

        <section className="dash-section">
          <div className="dash-section-head">
            <h4>Payments</h4>
            <div className="dash-row-actions">
              <button
                type="button"
                className="dash-button small"
                onClick={() => setShowPayForm(true)}
                disabled={paySaving}
              >
                Record payment
              </button>
            </div>
          </div>

          {message && (
            <p className="dash-success" role="status">
              {message}
            </p>
          )}

          {payState === "loading" && (
            <StatusPanel
              variant="loading"
              title="Loading payments…"
              description="Fetching payment history."
            />
          )}

          {payState === "error" && (
            <StatusPanel
              variant="error"
              title="Could not load payments"
              description={payError}
            />
          )}

          {payState === "ready" && payments.length === 0 && (
            <StatusPanel
              variant="empty"
              title="No payments yet"
              description="Record a payment to reduce the outstanding balance."
            />
          )}

          {payState === "ready" && payments.length > 0 && (
            <div className="dash-table-wrap">
              <table className="dash-table">
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Amount</th>
                    <th>Method</th>
                    <th>Reference</th>
                    <th>Recorded by</th>
                    <th className="dash-table-actions-head">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {payments.map((payment) => (
                    <tr key={payment.id}>
                      <td>{formatDate(payment.paymentDate)}</td>
                      <td>₹ {formatMoney(payment.amount)}</td>
                      <td>{(payment.method || "").replace(/_/g, " ")}</td>
                      <td>{payment.referenceNumber || "—"}</td>
                      <td>{payment.recordedBy || "—"}</td>
                      <td>
                        <div className="dash-row-actions">
                          {isAdmin && (
                            <button
                              type="button"
                              className="dash-link-button danger"
                              onClick={() => handleDeletePayment(payment)}
                              disabled={busy === payment.id}
                            >
                              {busy === payment.id ? "Deleting…" : "Delete"}
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        {showPayForm && (
          <PaymentFormDialog
            payment={null}
            invoices={[]}
            lockedInvoice={invoice}
            isSaving={paySaving}
            error={payFormError}
            fieldErrors={payFieldErrors}
            onClose={() => setShowPayForm(false)}
            onSubmit={handleSubmitPayment}
          />
        )}

        <div className="dash-form-actions">
          <button
            type="button"
            className="dash-button ghost"
            onClick={onClose}
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
