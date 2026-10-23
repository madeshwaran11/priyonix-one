// Shared Finance constants and helpers used by the finance pages, dialogs
// and form modules. Kept framework-free so it is easy to unit test.

export const INVOICE_ISSUE_STATUSES = ["DRAFT", "SENT"];

export const INVOICE_STATUSES = [
  "DRAFT",
  "SENT",
  "PARTIALLY_PAID",
  "PAID",
  "OVERDUE",
];

export const PAYMENT_METHODS = [
  "CASH",
  "BANK_TRANSFER",
  "CARD",
  "UPI",
  "CHEQUE",
  "OTHER",
];

export const EXPENSE_CATEGORIES = [
  "TRAVEL",
  "EQUIPMENT",
  "SOFTWARE",
  "OFFICE",
  "MARKETING",
  "UTILITIES",
  "SALARY",
  "CONSULTING",
  "OTHER",
];

export const EXPENSE_STATUSES = [
  "PENDING",
  "APPROVED",
  "REJECTED",
  "REIMBURSED",
];

export const DEFAULT_PAGE_SIZE = 10;

/// Human label for a machine enum (PARTIALLY_PAID -> Partially Paid).
export function label(value) {
  return (value || "").replace(/_/g, " ");
}

/// Builds a query string ignoring empty values.
export function toQuery(params) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      query.set(key, String(value));
    }
  });
  const asString = query.toString();
  return asString ? `?${asString}` : "";
}

/// The CSS tone suffix used by money/status pills on finance screens.
export function invoiceTone(status) {
  return (status || "").toLowerCase().replace(/_/g, "-");
}
