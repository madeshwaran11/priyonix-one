import { useEffect, useState } from "react";
import {
  listExpenses,
  createExpense,
  updateExpense,
  updateExpenseStatus,
  deleteExpense,
} from "../api";
import { formatDate, formatMoney } from "../format";
import { EXPENSE_STATUSES } from "../finance";
import ExpenseFormDialog from "../components/ExpenseFormDialog";
import StatusPanel from "../components/StatusPanel";
import { IconRefresh } from "../components/Icons";
import { Pagination } from "../components/WorkspaceUI";
import BackgroundRefreshStatus from "../components/BackgroundRefreshStatus";
import { useRetainedRefresh } from "../useRetainedRefresh";

const EXPENSE_TRANSITIONS = { PENDING: ["APPROVED", "REJECTED"], APPROVED: ["REIMBURSED"], REJECTED: ["PENDING"], REIMBURSED: [] };

export default function ExpensesPage({ session }) {
  const isAdmin = (session.role || "").toUpperCase() === "ADMIN";
  const token = session.token;

  const [expenses, setExpenses] = useState([]);
  const [listState, setListState] = useState("loading");
  const [listError, setListError] = useState("");
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [page, setPage] = useState(1);
  const [meta, setMeta] = useState({ total: 0, totalPages: 0 });
  const [reloadKey, setReloadKey] = useState(0);
  const { refreshing, beginRefresh, settleRefresh, cancelRefresh } =
    useRetainedRefresh();
  const [dialog, setDialog] = useState(null);
  const [isSaving, setIsSaving] = useState(false);
  const [formError, setFormError] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});
  const [actionMessage, setActionMessage] = useState("");

  useEffect(() => {
    let cancelled = false;
    const timer = setTimeout(() => {
      listExpenses(token, {
        search,
        status: statusFilter,
        category: categoryFilter,
        from,
        to,
        page,
        size: 10,
      })
        .then((data) => {
          if (cancelled) return;
          setExpenses(data.items || []);
          setMeta({ total: data.total || 0, totalPages: data.totalPages || 0 });
          setListError("");
          settleRefresh();
          setListState("ready");
        })
        .catch((error) => {
          if (cancelled) return;
          setListError(error.code === "UNAUTHORIZED" ? "Session expired." : error.message);
          setListState(settleRefresh() ? "ready" : "error");
        });
    }, 250);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [token, search, statusFilter, categoryFilter, from, to, page, reloadKey, settleRefresh]);

  function reload() {
    if (listState === "ready") {
      if (!beginRefresh()) return;
    } else {
      cancelRefresh();
      setListState("loading");
    }
    setListError("");
    setReloadKey((k) => k + 1);
  }

  function resetToFirst() {
    cancelRefresh();
    setPage(1);
    setListState("loading");
  }

  function openCreate() {
    setFormError("");
    setFieldErrors({});
    setDialog({ mode: "form", expense: null });
  }

  function openEdit(expense) {
    setFormError("");
    setFieldErrors({});
    setDialog({ mode: "form", expense });
  }

  function openDelete(expense) {
    setFormError("");
    setDialog({ mode: "delete", expense });
  }

  function openStatus(expense) {
    setFormError("");
    setDialog({ mode: "status", expense });
  }

  function closeDialog() {
    if (isSaving) return;
    setDialog(null);
    setFormError("");
    setFieldErrors({});
  }

  async function handleSubmit(form) {
    const editing = dialog.expense;
    setIsSaving(true);
    setFormError("");
    setFieldErrors({});
    try {
      const payload = {
        title: form.title,
        category: form.category,
        amount: Number(form.amount),
        expenseDate: form.expenseDate,
        vendor: form.vendor || null,
        paymentMethod: form.paymentMethod || null,
        receiptReference: form.receiptReference || null,
        notes: form.notes || null,
      };
      if (editing) {
        await updateExpense(token, editing.id, payload);
        setActionMessage("Expense updated.");
      } else {
        await createExpense(token, payload);
        setActionMessage("Expense created.");
      }
      setDialog(null);
      reload();
    } catch (error) {
      setFormError(error.code === "UNAUTHORIZED" ? "Session expired." : error.message);
      setFieldErrors(error.fieldErrors || {});
    } finally {
      setIsSaving(false);
    }
  }

  async function handleStatusChange(status) {
    setIsSaving(true);
    setFormError("");
    try {
      await updateExpenseStatus(token, dialog.expense.id, status);
      setActionMessage("Expense status updated.");
      setDialog(null);
      reload();
    } catch (error) {
      setFormError(error.code === "UNAUTHORIZED" ? "Session expired." : error.message);
    } finally {
      setIsSaving(false);
    }
  }

  async function handleDelete(expense) {
    setIsSaving(true);
    setFormError("");
    try {
      await deleteExpense(token, expense.id);
      setActionMessage("Expense deleted.");
      setDialog(null);
      reload();
    } catch (error) {
      setFormError(error.code === "UNAUTHORIZED" ? "Session expired." : error.message);
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <div className="dash-page">
      <section className="dash-hero">
        <div>
          <span className="dash-eyebrow">Finance</span>
          <h2>Expenses</h2>
          <p>Track and approve business expenses.</p>
        </div>
        <button type="button" className="dash-button" onClick={openCreate}>
          New expense
        </button>
      </section>

      <div className="dash-toolbar">
        <label className="dash-search">
          <input
            type="search"
            value={search}
            onChange={(e) => { setSearch(e.target.value); resetToFirst(); }}
            placeholder="Search by title, vendor, notes"
          />
        </label>
        <label className="dash-filter">
          <select
            value={statusFilter}
            onChange={(e) => { setStatusFilter(e.target.value); resetToFirst(); }}
          >
            <option value="">All statuses</option>
            {EXPENSE_STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
        </label>
        <label className="dash-filter">
          <select
            value={categoryFilter}
            onChange={(e) => { setCategoryFilter(e.target.value); resetToFirst(); }}
          >
            <option value="">All categories</option>
            <option value="TRAVEL">Travel</option>
            <option value="EQUIPMENT">Equipment</option>
            <option value="SOFTWARE">Software</option>
            <option value="OFFICE">Office</option>
            <option value="MARKETING">Marketing</option>
            <option value="UTILITIES">Utilities</option>
            <option value="SALARY">Salary</option>
            <option value="CONSULTING">Consulting</option>
            <option value="OTHER">Other</option>
          </select>
        </label>
        <label className="dash-filter">
          <input type="date" value={from} onChange={(e) => { setFrom(e.target.value); resetToFirst(); }} />
        </label>
        <label className="dash-filter">
          <input type="date" value={to} onChange={(e) => { setTo(e.target.value); resetToFirst(); }} />
        </label>
        <button type="button" className="dash-icon-button" onClick={reload} aria-label={refreshing ? "Refreshing expenses" : "Refresh expenses"} aria-busy={refreshing} disabled={refreshing || listState === "loading"}>
          <IconRefresh />
        </button>
        <span className="dash-toolbar-count">{meta.total} expense(s)</span>
      </div>

      <BackgroundRefreshStatus refreshing={refreshing} error={listState === "ready" ? listError : ""} label="expenses" />

      {actionMessage && <p className="dash-success">{actionMessage}</p>}

      {listState === "loading" && <StatusPanel variant="loading" title="Loading expenses…" />}
      {listState === "error" && <StatusPanel variant="error" title="Could not load expenses" description={listError} actionLabel="Retry" onAction={reload} />}
      {listState === "ready" && expenses.length === 0 && <StatusPanel variant="empty" title="No expenses found" description="Try different filters." />}

      {listState === "ready" && expenses.length > 0 && (
        <div className="dash-table-wrap">
          <table className="dash-table">
            <thead>
              <tr>
                <th>Title</th>
                <th>Category</th>
                <th>Amount</th>
                <th>Date</th>
                <th>Vendor</th>
                <th>Method</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {expenses.map((exp) => (
                <tr key={exp.id}>
                  <td>{exp.title}</td>
                  <td>{(exp.category || "").replace(/_/g, " ")}</td>
                  <td>₹ {formatMoney(exp.amount)}</td>
                  <td>{formatDate(exp.expenseDate)}</td>
                  <td>{exp.vendor || "—"}</td>
                  <td>{(exp.paymentMethod || "").replace(/_/g, " ")}</td>
                  <td>
                    <span className={`dash-status-pill status-${(exp.status || "").toLowerCase().replace(/_/g, "-")}`}>
                      {exp.status}
                    </span>
                  </td>
                  <td>
                    <div className="dash-row-actions">
                      {["PENDING", "REJECTED"].includes(exp.status) && <button type="button" className="dash-link-button" onClick={() => openEdit(exp)}>Edit</button>}
                      {isAdmin && (
                        <>
                          {EXPENSE_TRANSITIONS[exp.status]?.length > 0 && <button type="button" className="dash-link-button" onClick={() => openStatus(exp)}>Status</button>}
                          {["PENDING", "REJECTED"].includes(exp.status) && <button type="button" className="dash-link-button danger" onClick={() => openDelete(exp)}>Delete</button>}
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {listState === "ready" && <Pagination page={page} total={meta.total} totalPages={meta.totalPages} onPage={(value) => { setPage(value); setListState("loading"); }} />}

      {dialog?.mode === "form" && (
        <ExpenseFormDialog
          expense={dialog.expense}
          isSaving={isSaving}
          error={formError}
          fieldErrors={fieldErrors}
          onClose={closeDialog}
          onSubmit={handleSubmit}
        />
      )}

      {dialog?.mode === "status" && (
        <div className="dash-modal-backdrop">
          <div className="dash-modal narrow">
            <div className="dash-modal-head">
              <h3>Update expense status</h3>
            </div>
            {formError && <p className="dash-form-error">{formError}</p>}
            <div className="dash-form-actions">
              <button type="button" className="dash-button ghost" onClick={closeDialog} disabled={isSaving}>Cancel</button>
              {(EXPENSE_TRANSITIONS[dialog.expense.status] || []).map((s) => (
                <button
                  key={s}
                  type="button"
                  className="dash-button"
                  onClick={() => handleStatusChange(s)}
                  disabled={isSaving || s === dialog.expense.status}
                >
                  {s}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}

      {dialog?.mode === "delete" && (
        <div className="dash-modal-backdrop">
          <div className="dash-modal narrow">
            <div className="dash-modal-head">
              <h3>Delete expense</h3>
            </div>
            <p className="dash-modal-text">Delete this expense? This action cannot be undone.</p>
            {formError && <p className="dash-form-error">{formError}</p>}
            <div className="dash-form-actions">
              <button type="button" className="dash-button ghost" onClick={closeDialog} disabled={isSaving}>Cancel</button>
              <button type="button" className="dash-button danger" onClick={() => handleDelete(dialog.expense)} disabled={isSaving}>{isSaving ? "Deleting…" : "Delete"}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
