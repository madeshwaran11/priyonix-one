// Shared helpers and constants for the expense add/edit form.

import {
  EXPENSE_CATEGORIES,
  EXPENSE_STATUSES,
  PAYMENT_METHODS,
} from "./finance";

export { EXPENSE_CATEGORIES, EXPENSE_STATUSES, PAYMENT_METHODS };

function todayIso() {
  const now = new Date();
  const offset = now.getTimezoneOffset();
  return new Date(now.getTime() - offset * 60000).toISOString().slice(0, 10);
}

export function emptyExpenseForm() {
  return {
    title: "",
    category: "OTHER",
    amount: "",
    expenseDate: todayIso(),
    vendor: "",
    paymentMethod: "CARD",
    receiptReference: "",
    notes: "",
  };
}

export function expenseToForm(expense) {
  return {
    title: expense.title || "",
    category: expense.category || "OTHER",
    amount:
      expense.amount === null || expense.amount === undefined
        ? ""
        : String(expense.amount),
    expenseDate: expense.expenseDate || todayIso(),
    vendor: expense.vendor || "",
    paymentMethod: expense.paymentMethod || "CARD",
    receiptReference: expense.receiptReference || "",
    notes: expense.notes || "",
  };
}

/** Converts expense form state into the JSON payload the backend expects. */
export function expenseFormToPayload(form) {
  return {
    title: form.title.trim(),
    category: form.category,
    amount: form.amount === "" ? 0 : Number(form.amount),
    expenseDate: form.expenseDate || null,
    vendor: form.vendor.trim() || null,
    paymentMethod: form.paymentMethod || null,
    receiptReference: form.receiptReference.trim() || null,
    notes: form.notes.trim() || null,
  };
}
