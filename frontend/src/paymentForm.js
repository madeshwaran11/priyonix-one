// Shared helpers and constants for the payment add/edit form.

import { PAYMENT_METHODS } from "./finance";

export { PAYMENT_METHODS };

function todayIso() {
  const now = new Date();
  const offset = now.getTimezoneOffset();
  return new Date(now.getTime() - offset * 60000).toISOString().slice(0, 10);
}

export function emptyPaymentForm({ invoiceId, amount } = {}) {
  return {
    invoiceId: invoiceId || "",
    amount: amount === undefined || amount === null ? "" : String(amount),
    paymentDate: todayIso(),
    method: "BANK_TRANSFER",
    referenceNumber: "",
    notes: "",
  };
}

export function paymentToForm(payment) {
  return {
    invoiceId: payment.invoiceId || "",
    amount:
      payment.amount === null || payment.amount === undefined
        ? ""
        : String(payment.amount),
    paymentDate: payment.paymentDate || todayIso(),
    method: payment.method || "BANK_TRANSFER",
    referenceNumber: payment.referenceNumber || "",
    notes: payment.notes || "",
  };
}

/** Converts payment form state into the JSON payload the backend expects. */
export function paymentFormToPayload(form) {
  return {
    invoiceId: form.invoiceId,
    amount: form.amount === "" ? 0 : Number(form.amount),
    paymentDate: form.paymentDate || null,
    method: form.method || null,
    referenceNumber: form.referenceNumber.trim() || null,
    notes: form.notes.trim() || null,
  };
}
