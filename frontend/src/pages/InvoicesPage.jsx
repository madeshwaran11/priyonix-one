import { useEffect, useState } from "react";
import {
  listInvoices,
  getInvoice,
  createInvoice,
  updateInvoice,
  deleteInvoice,
  downloadCsv,
  listClients,
  listProjects,
} from "../api";
import { formatDate, formatMoney } from "../format";
import { invoiceTone } from "../finance";
import { invoiceFormToPayload } from "../invoiceForm";
import InvoiceFormDialog from "../components/InvoiceFormDialog";
import InvoiceDetailDialog from "../components/InvoiceDetailDialog";
import StatusPanel from "../components/StatusPanel";
import { IconRefresh } from "../components/Icons";
import { Notice, Pagination } from "../components/WorkspaceUI";
import { useRecordDialog } from "../useRecordDialog";
import BackgroundRefreshStatus from "../components/BackgroundRefreshStatus";
import { useRetainedRefresh } from "../useRetainedRefresh";

export default function InvoicesPage({ session }) {
  const isAdmin = (session.role || "").toUpperCase() === "ADMIN";
  const token = session.token;

  const [invoices, setInvoices] = useState([]);
  const [listState, setListState] = useState("loading");
  const [listError, setListError] = useState("");
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [clientFilter, setClientFilter] = useState("");
  const [projectFilter, setProjectFilter] = useState("");
  const [overdueOnly, setOverdueOnly] = useState(false);
  const [page, setPage] = useState(1);
  const [meta, setMeta] = useState({ total: 0, totalPages: 0 });
  const [reloadKey, setReloadKey] = useState(0);
  const { refreshing, beginRefresh, settleRefresh, cancelRefresh } =
    useRetainedRefresh();
  const [clients, setClients] = useState([]);
  const [projects, setProjects] = useState([]);
  const [lookupsState, setLookupsState] = useState("loading");
  const { dialog, setDialog, updateRecord, detailError, detailLoading } = useRecordDialog("/api/invoices", "invoice");
  const [isSaving, setIsSaving] = useState(false);
  const [formError, setFormError] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});
  const [actionMessage, setActionMessage] = useState("");
  const selected = dialog?.mode === "detail" ? dialog.invoice : null;
  const [exporting, setExporting] = useState(false);

  useEffect(() => {
    let cancelled = false;
    Promise.all([
      listClients(token, { page: 1, size: 200 }),
      listProjects(token, { page: 1, size: 200 }),
    ])
      .then(([c, p]) => {
        if (cancelled) return;
        setClients(c.items || []);
        setProjects(p.items || []);
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
      listInvoices(token, {
        search,
        status: statusFilter,
        clientId: clientFilter,
        projectId: projectFilter,
        overdue: overdueOnly,
        page,
        size: 10,
      })
        .then((data) => {
          if (cancelled) return;
          setInvoices(data.items || []);
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
  }, [token, search, statusFilter, clientFilter, projectFilter, overdueOnly, page, reloadKey, settleRefresh]);

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
    setDialog({ mode: "form", invoice: null });
  }

  function openEdit(invoice) {
    if (Number(invoice.amountPaid) > 0) return;
    setFormError("");
    setFieldErrors({});
    setDialog({ mode: "form", invoice });
  }

  function openDelete(invoice) {
    if (Number(invoice.amountPaid) > 0) return;
    setFormError("");
    setDialog({ mode: "delete", invoice });
  }

  function openDetail(invoice) {
    setDialog({ mode: "detail", invoice });
  }

  function closeDialog() {
    if (isSaving) return;
    setDialog(null);
    setFormError("");
    setFieldErrors({});
  }

  async function handleSubmit(form) {
    const editing = dialog.invoice;
    setIsSaving(true);
    setFormError("");
    setFieldErrors({});
    try {
      const payload = invoiceFormToPayload(form);
      if (editing) {
        await updateInvoice(token, editing.id, payload);
        setActionMessage("Invoice updated.");
      } else {
        await createInvoice(token, payload);
        setActionMessage("Invoice created.");
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

  async function handleDelete(invoice) {
    setIsSaving(true);
    setFormError("");
    try {
      await deleteInvoice(token, invoice.id);
      setActionMessage("Invoice deleted.");
      setDialog(null);
      reload();
    } catch (error) {
      setFormError(error.code === "UNAUTHORIZED" ? "Session expired." : error.message);
    } finally {
      setIsSaving(false);
    }
  }

  async function exportInvoices() {
    setExporting(true);
    try {
      const query = [];
      if (search) query.push(`search=${encodeURIComponent(search)}`);
      if (statusFilter) query.push(`status=${encodeURIComponent(statusFilter)}`);
      if (clientFilter) query.push(`clientId=${encodeURIComponent(clientFilter)}`);
      if (projectFilter) query.push(`projectId=${encodeURIComponent(projectFilter)}`);
      if (overdueOnly) query.push("overdue=true");
      const qs = query.length ? `?${query.join("&")}` : "";
      await downloadCsv(token, `/api/invoices/export${qs}`, "invoices.csv");
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
          <h2>Invoices</h2>
          <p>Create, view and manage billing.</p>
        </div>
        <div className="dash-row-actions">
          <button type="button" className="dash-button ghost" onClick={exportInvoices} disabled={exporting}>
            {exporting ? "Exporting…" : "Export CSV"}
          </button>
          <button type="button" className="dash-button" onClick={openCreate}>
            New invoice
          </button>
        </div>
      </section>

      <div className="dash-toolbar">
        <label className="dash-search">
          <input
            type="search"
            value={search}
            onChange={(e) => { setSearch(e.target.value); resetToFirst(); }}
            placeholder="Search by invoice #, client"
          />
        </label>
        <label className="dash-filter">
          <select
            value={statusFilter}
            onChange={(e) => { setStatusFilter(e.target.value); resetToFirst(); }}
          >
            <option value="">All statuses</option>
            <option value="DRAFT">Draft</option>
            <option value="SENT">Sent</option>
            <option value="PARTIALLY_PAID">Partially Paid</option>
            <option value="PAID">Paid</option>
            <option value="OVERDUE">Overdue</option>
          </select>
        </label>
        <label className="dash-filter">
          <select
            value={clientFilter}
            onChange={(e) => { setClientFilter(e.target.value); resetToFirst(); }}
          >
            <option value="">All clients</option>
            {clients.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </label>
        <label className="dash-filter">
          <select
            value={projectFilter}
            onChange={(e) => { setProjectFilter(e.target.value); resetToFirst(); }}
          >
            <option value="">All projects</option>
            {projects.map((p) => <option key={p.id} value={p.id}>{p.projectName || p.projectCode}</option>)}
          </select>
        </label>
        <label className="dash-checkbox-inline">
          <input
            type="checkbox"
            checked={overdueOnly}
            onChange={(e) => { setOverdueOnly(e.target.checked); resetToFirst(); }}
          />
          <span>Overdue only</span>
        </label>
        <button type="button" className="dash-icon-button" onClick={reload} aria-label={refreshing ? "Refreshing invoices" : "Refresh invoices"} aria-busy={refreshing} disabled={refreshing || listState === "loading"}>
          <IconRefresh />
        </button>
        <span className="dash-toolbar-count">{meta.total} invoice(s)</span>
      </div>

      <BackgroundRefreshStatus refreshing={refreshing} error={listState === "ready" ? listError : ""} label="invoices" />

      <Notice error>{detailError}</Notice>
      {detailLoading && <p className="workspace-help" role="status">Loading requested record…</p>}
      {actionMessage && <p className="dash-success">{actionMessage}</p>}

      {lookupsState === "error" && (
        <p className="dash-form-hint">
          Some lookups (clients or projects) could not be loaded, so some
          filters may be empty.
        </p>
      )}

      {listState === "loading" && <StatusPanel variant="loading" title="Loading invoices…" />}
      {listState === "error" && <StatusPanel variant="error" title="Could not load invoices" description={listError} actionLabel="Retry" onAction={reload} />}
      {listState === "ready" && invoices.length === 0 && <StatusPanel variant="empty" title="No invoices found" description="Try different filters or create one." />}

      {listState === "ready" && invoices.length > 0 && (
        <div className="dash-table-wrap">
          <table className="dash-table">
            <thead>
              <tr>
                <th>Invoice #</th>
                <th>Client</th>
                <th>Project</th>
                <th>Issue</th>
                <th>Due</th>
                <th>Total</th>
                <th>Paid</th>
                <th>Balance</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {invoices.map((inv) => (
                <tr key={inv.id}>
                  <td>{inv.invoiceNumber}</td>
                  <td>{inv.clientName || "—"}</td>
                  <td>{inv.projectName || inv.projectCode || "—"}</td>
                  <td>{formatDate(inv.issueDate)}</td>
                  <td>{formatDate(inv.dueDate)}</td>
                  <td>₹ {formatMoney(inv.total)}</td>
                  <td>₹ {formatMoney(inv.amountPaid)}</td>
                  <td>₹ {formatMoney(inv.balance)}</td>
                  <td>
                    <span className={`dash-status-pill status-${invoiceTone(inv.status)}`}>
                      {inv.status}
                    </span>
                  </td>
                  <td>
                    <div className="dash-row-actions">
                      <button type="button" className="dash-link-button" onClick={() => openDetail(inv)}>View</button>
                      {!(Number(inv.amountPaid) > 0) && <button type="button" className="dash-link-button" onClick={() => openEdit(inv)}>Edit</button>}
                      {isAdmin && !(Number(inv.amountPaid) > 0) && <button type="button" className="dash-link-button danger" onClick={() => openDelete(inv)}>Delete</button>}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {listState === "ready" && <Pagination page={page} total={meta.total} totalPages={meta.totalPages} onPage={(value) => { setPage(value); setListState("loading"); }} />}

      {selected && (
        <InvoiceDetailDialog
          key={selected.id}
          invoice={selected}
          token={token}
          isAdmin={isAdmin}
          onClose={closeDialog}
          onEdit={() => openEdit(selected)}
          onDelete={() => openDelete(selected)}
          onChanged={async () => {
            reload();
            try { updateRecord(await getInvoice(token, selected.id)); }
            catch (error) { setListError(error.message); }
          }}
        />
      )}

      {dialog?.mode === "form" && (
        <InvoiceFormDialog
          invoice={dialog.invoice}
          clients={clients}
          projects={projects}
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
              <h3>Delete invoice</h3>
            </div>
            <p className="dash-modal-text">Delete invoice {dialog.invoice.invoiceNumber}? This action cannot be undone. Invoices with recorded payments cannot be deleted.</p>
            {formError && <p className="dash-form-error">{formError}</p>}
            <div className="dash-form-actions">
              <button type="button" className="dash-button ghost" onClick={closeDialog} disabled={isSaving}>Cancel</button>
              <button type="button" className="dash-button danger" onClick={() => handleDelete(dialog.invoice)} disabled={isSaving}>{isSaving ? "Deleting…" : "Delete"}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
