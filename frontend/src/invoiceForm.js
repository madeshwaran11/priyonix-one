// Shared helpers and constants for the invoice add/edit form.
//
// Money is never calculated on the server-only basis: the dialog shows a live
// client-side preview, but the authoritative subtotal/tax/total always come
// back from the API.

import { INVOICE_ISSUE_STATUSES, PAYMENT_METHODS } from "./finance";

export { INVOICE_ISSUE_STATUSES, PAYMENT_METHODS };

function todayIso() {
  const now = new Date();
  const offset = now.getTimezoneOffset();
  return new Date(now.getTime() - offset * 60000).toISOString().slice(0, 10);
}

function addDaysIso(days) {
  const now = new Date();
  now.setDate(now.getDate() + days);
  const offset = now.getTimezoneOffset();
  return new Date(now.getTime() - offset * 60000).toISOString().slice(0, 10);
}

export function emptyLineItem() {
  return { description: "", quantity: "1", unitPrice: "" };
}

export function emptyInvoiceForm(clientId = "") {
  return {
    clientId,
    projectId: "",
    issueDate: todayIso(),
    dueDate: addDaysIso(30),
    status: "DRAFT",
    taxRate: "",
    discount: "",
    notes: "",
    lineItems: [emptyLineItem()],
  };
}

export function invoiceToForm(invoice) {
  const lineItems =
    Array.isArray(invoice.lineItems) && invoice.lineItems.length
      ? invoice.lineItems.map((item) => ({
          description: item.description || "",
          quantity: item.quantity === null || item.quantity === undefined
            ? ""
            : String(item.quantity),
          unitPrice: item.unitPrice === null || item.unitPrice === undefined
            ? ""
            : String(item.unitPrice),
        }))
      : [emptyLineItem()];

  return {
    clientId: invoice.clientId || "",
    projectId: invoice.projectId || "",
    issueDate: invoice.issueDate || todayIso(),
    dueDate: invoice.dueDate || addDaysIso(30),
    // Only the stored issue state (DRAFT/SENT) is editable.
    status: invoice.status === "DRAFT" ? "DRAFT" : "SENT",
    taxRate: invoice.taxRate === null || invoice.taxRate === undefined
      ? ""
      : String(invoice.taxRate),
    discount: invoice.discount === null || invoice.discount === undefined
      ? ""
      : String(invoice.discount),
    notes: invoice.notes || "",
    lineItems,
  };
}

/** Converts invoice form state into the JSON payload the backend expects. */
export function invoiceFormToPayload(form) {
  return {
    clientId: form.clientId,
    projectId: form.projectId || null,
    issueDate: form.issueDate || null,
    dueDate: form.dueDate || null,
    status: form.status,
    taxRate: form.taxRate === "" ? 0 : Number(form.taxRate),
    discount: form.discount === "" ? 0 : Number(form.discount),
    notes: form.notes.trim() || null,
    lineItems: form.lineItems.map((item) => ({
      description: item.description.trim(),
      quantity: item.quantity === "" ? 0 : Number(item.quantity),
      unitPrice: item.unitPrice === "" ? 0 : Number(item.unitPrice),
    })),
  };
}

function round2(value) {
  return Math.round((value + Number.EPSILON) * 100) / 100;
}

/**
 * Live preview of the totals, matching the backend rounding rules
 * (HALF_UP to 2 decimals) closely enough for an in-form preview.
 */
export function computeInvoicePreview(form) {
  const subtotal = form.lineItems.reduce((sum, item) => {
    const quantity = Number(item.quantity) || 0;
    const unitPrice = Number(item.unitPrice) || 0;
    return sum + round2(quantity * unitPrice);
  }, 0);

  const discount = Math.min(Number(form.discount) || 0, subtotal);
  const taxable = round2(subtotal - discount);
  const taxRate = Number(form.taxRate) || 0;
  const tax = round2(taxable * (taxRate / 100));
  const total = round2(taxable + tax);

  return {
    subtotal: round2(subtotal),
    discount: round2(discount),
    taxable,
    taxRate,
    tax,
    total,
  };
}
