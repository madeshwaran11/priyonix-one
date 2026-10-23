import { useEffect, useState } from "react";
import {
  listInvoices,
  listPayments,
  createPayment,
  updatePayment,
  deletePayment,
} from "../api";
import { downloadCsv } from "../api";
import { formatDate, formatMoney } from "../format";
import PaymentFormDialog from "../components/PaymentFormDialog";
import StatusPanel from "../components/StatusPanel";
import { IconRefresh } from "../components/Icons";
import { Pagination } from "../components/WorkspaceUI";
import BackgroundRefreshStatus from "../components/BackgroundRefreshStatus";
import { useRetainedRefresh } from "../useRetainedRefresh";

export default function PaymentsPage({ session }) {
  const isAdmin = (session.role || "").toUpperCase() === "ADMIN";
  const token = session.token;

  const [payments, setPayments] = useState([]);
  const [listState, setListState] = useState("loading");
  const [listError, setListError] = useState("");
  const [search, setSearch] = useState("");
  const [methodFilter, setMethodFilter] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [page, setPage] = useState(1);
  const [meta, setMeta] = useState({ total: 0, totalPages: 0 });
  const [reloadKey, setReloadKey] = useState(0);
  const { refreshing, beginRefresh, settleRefresh, cancelRefresh } =
    useRetainedRefresh();
  const [invoices, setInvoices] = useState([]);
  const [lookupsState, setLookupsState] = useState("loading");
  const [dialog, setDialog] = useState(null);
  const [isSaving, setIsSaving] = useState(false);
  const [formError, setFormError] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});
  const [actionMessage, setActionMessage] = useState("");
  const [exporting, setExporting] = useState(false);

  useEffect(() => {
    let cancelled = false;
    listInvoices(token, { page: 1, size: 200 })
      .then((data) => {
        if (cancelled) return;
        setInvoices(data.items || []);
        setLookupsState("ready");
      })
      .catch(() => {
        if (cancelled) return;
        setLookupsState("error");
      });
    return () => {
      cancelled = true;
    };
  }, [token]);

  useEffect(() => {
    let cancelled = false;
    const timer = setTimeout(() => {
      listPayments(token, {
        search,
        method: methodFilter,
        from,
        to,
        page,
        size: 10,
      })
        .then((data) => {
          if (cancelled) return;
          setPayments(data.items || []);
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
  }, [token, search, methodFilter, from, to, page, reloadKey, settleRefresh]);

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
    setDialog({ mode: "form", payment: null });
  }

  function openEdit(payment) {
    setFormError("");
    setFieldErrors({});
    setDialog({ mode: "form", payment });
  }

  function openDelete(payment) {
    setFormError("");
    setDialog({ mode: "delete", payment });
  }

  function closeDialog() {
    if (isSaving) return;
    setDialog(null);
    setFormError("");
    setFieldErrors({});
  }

  async function handleSubmit(form) {
    const editing = dialog.payment;
    setIsSaving(true);
    setFormError("");
    setFieldErrors({});
    try {
      const payload = {
        invoiceId: form.invoiceId,
        amount: Number(form.amount),
        paymentDate: form.paymentDate,
        method: form.method,
        referenceNumber: form.referenceNumber || null,
        notes: form.notes || null,
      };
      if (editing) {
        await updatePayment(token, editing.id, payload);
        setActionMessage("Payment updated.");
      } else {
        await createPayment(token, payload);
        setActionMessage("Payment recorded.");
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

  async function handleDelete(payment) {
    setIsSaving(true);
    setFormError("");
    try {
      await deletePayment(token, payment.id);
      setActionMessage("Payment deleted.");
      setDialog(null);
      reload();
    } catch (error) {
      setFormError(error.code === "UNAUTHORIZED" ? "Session expired." : error.message);
    } finally {
      setIsSaving(false);
    }
  }

  async function exportPayments() {
    setExporting(true);
    try {
      const query = [];
      if (from) query.push(`from=${from}`);
      if (to) query.push(`to=${to}`);
      if (methodFilter) query.push(`method=${encodeURIComponent(methodFilter)}`);
      const qs = query.length ? `?${query.join("&")}` : "";
      await downloadCsv(token, `/api/payments/export${qs}`, "payments.csv");
    } catch (error) {
      setActionMessage("");
      setListError(error.message);
      setListState("error");
    } finally {
      setExporting(false);
    }
  }

  return (
    <div className="dash-page">
      <section className="dash-hero">
        <div>
          <span className="dash-eyebrow">Finance</span>
          <h2>Payments</h2>
          <p>Record and track payments against invoices.</p>
        </div>
        <div className="dash-row-actions">
          <button type="button" className="dash-button ghost" onClick={exportPayments} disabled={exporting}>
            {exporting ? "Exporting…" : "Export CSV"}
          </button>
          <button type="button" className="dash-button" onClick={openCreate}>
            Record payment
          </button>
        </div>
      </section>

      <div className="dash-toolbar">
        <label className="dash-search">
          <input
            type="search"
            value={search}
            onChange={(e) => { setSearch(e.target.value); resetToFirst(); }}
            placeholder="Search by reference, invoice #, client"
          />
        </label>
        <label className="dash-filter">
          <select
            value={methodFilter}
            onChange={(e) => { setMethodFilter(e.target.value); resetToFirst(); }}
          >
            <option value="">All methods</option>
            <option value="CASH">Cash</option>
            <option value="BANK_TRANSFER">Bank Transfer</option>
            <option value="CARD">Card</option>
            <option value="UPI">UPI</option>
            <option value="CHEQUE">Cheque</option>
            <option value="OTHER">Other</option>
          </select>
        </label>
        <label className="dash-filter">
          <input type="date" value={from} onChange={(e) => { setFrom(e.target.value); resetToFirst(); }} />
        </label>
        <label className="dash-filter">
          <input type="date" value={to} onChange={(e) => { setTo(e.target.value); resetToFirst(); }} />
        </label>
        <button type="button" className="dash-icon-button" onClick={reload} aria-label={refreshing ? "Refreshing payments" : "Refresh payments"} aria-busy={refreshing} disabled={refreshing || listState === "loading"}>
          <IconRefresh />
        </button>
        <span className="dash-toolbar-count">{meta.total} payment(s)</span>
      </div>

      <BackgroundRefreshStatus refreshing={refreshing} error={listState === "ready" ? listError : ""} label="payments" />

      {actionMessage && <p className="dash-success">{actionMessage}</p>}

      {lookupsState === "error" && (
        <p className="dash-form-hint">
          Some lookups (invoices) could not be loaded, so the invoice selector
          may be empty.
        </p>
      )}

      {listState === "loading" && <StatusPanel variant="loading" title="Loading payments…" />}
      {listState === "error" && <StatusPanel variant="error" title="Could not load payments" description={listError} actionLabel="Retry" onAction={reload} />}
      {listState === "ready" && payments.length === 0 && <StatusPanel variant="empty" title="No payments found" description="Try different filters." />}

      {listState === "ready" && payments.length > 0 && (
        <div className="dash-table-wrap">
          <table className="dash-table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Invoice</th>
                <th>Client</th>
                <th>Amount</th>
                <th>Method</th>
                <th>Reference</th>
                <th>Recorded by</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {payments.map((p) => (
                <tr key={p.id}>
                  <td>{formatDate(p.paymentDate)}</td>
                  <td>{p.invoiceNumber}</td>
                  <td>{p.clientName || "—"}</td>
                  <td>₹ {formatMoney(p.amount)}</td>
                  <td>{(p.method || "").replace(/_/g, " ")}</td>
                  <td>{p.referenceNumber || "—"}</td>
                  <td>{p.recordedBy || "—"}</td>
                  <td>
                    <div className="dash-row-actions">
                      <button type="button" className="dash-link-button" onClick={() => openEdit(p)}>Edit</button>
                      {isAdmin && (
                        <button type="button" className="dash-link-button danger" onClick={() => openDelete(p)}>Delete</button>
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
        <PaymentFormDialog
          payment={dialog.payment}
          invoices={invoices}
          isSaving={isSaving}
          error={formError}
          fieldErrors={fieldErrors}
          onClose={closeDialog}
          onSubmit={handleSubmit}
        />
      )}

      {dialog?.mode === "delete" && (
        <div className="dash-modal-backdrop">
          <div className="dash-modal narrow">
            <div className="dash-modal-head">
              <h3>Delete payment</h3>
            </div>
            <p className="dash-modal-text">Delete this payment? This action cannot be undone.</p>
            {formError && <p className="dash-form-error">{formError}</p>}
            <div className="dash-form-actions">
              <button type="button" className="dash-button ghost" onClick={closeDialog} disabled={isSaving}>Cancel</button>
              <button type="button" className="dash-button danger" onClick={() => handleDelete(dialog.payment)} disabled={isSaving}>{isSaving ? "Deleting…" : "Delete"}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
