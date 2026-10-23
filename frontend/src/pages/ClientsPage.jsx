import { useEffect, useState } from "react";
import {
  createClient,
  deleteClient,
  listClients,
  listEmployees,
  updateClient,
} from "../api";
import {
  CLIENT_STATUSES,
  clientFormToPayload,
} from "../clientForm";
import ClientFormDialog from "../components/ClientFormDialog";
import ClientDetailDialog from "../components/ClientDetailDialog";
import StatusPanel from "../components/StatusPanel";
import { IconRefresh } from "../components/Icons";
import { formatStatus, statusTone } from "../format";
import { Notice } from "../components/WorkspaceUI";
import { useRecordDialog } from "../useRecordDialog";
import BackgroundRefreshStatus from "../components/BackgroundRefreshStatus";
import { useRetainedRefresh } from "../useRetainedRefresh";

const PAGE_SIZE = 10;

/**
 * Clients page.
 *
 * Loads real clients from GET /api/clients with the stored JWT. Owner
 * selectors are populated from the employee directory. No demo data is shown:
 * loading, error and empty states render until the API responds.
 */
export default function ClientsPage({ session }) {
  const isAdmin = (session.role || "").toUpperCase() === "ADMIN";

  const [clients, setClients] = useState([]);
  const [listState, setListState] = useState("loading");
  const [listError, setListError] = useState("");
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [page, setPage] = useState(1);
  const [meta, setMeta] = useState({ total: 0, totalPages: 0 });
  const [reloadKey, setReloadKey] = useState(0);
  const { refreshing, beginRefresh, settleRefresh, cancelRefresh } =
    useRetainedRefresh();

  const [employees, setEmployees] = useState([]);
  const [employeesState, setEmployeesState] = useState("loading");

  const { dialog, setDialog, detailError, detailLoading } = useRecordDialog("/api/clients", "client");
  const [isSaving, setIsSaving] = useState(false);
  const [formError, setFormError] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});
  const [actionMessage, setActionMessage] = useState("");
  const [cascade, setCascade] = useState(false);

  // Employee directory for the owner selector.
  useEffect(() => {
    let cancelled = false;

    listEmployees(session.token)
      .then((data) => {
        if (cancelled) return;
        setEmployees(data);
        setEmployeesState("ready");
      })
      .catch(() => {
        if (cancelled) return;
        setEmployeesState("error");
      });

    return () => {
      cancelled = true;
    };
  }, [session.token]);

  // Debounced client list fetch.
  useEffect(() => {
    let cancelled = false;

    const timer = setTimeout(() => {
      listClients(session.token, {
        search,
        status: statusFilter,
        page,
        size: PAGE_SIZE,
      })
        .then((data) => {
          if (cancelled) return;
          setClients(data.items);
          setMeta({ total: data.total, totalPages: data.totalPages });
          setListError("");
          settleRefresh();
          setListState("ready");
        })
        .catch((error) => {
          if (cancelled) return;
          setListError(
            error.code === "UNAUTHORIZED"
              ? "Your session expired. Please sign in again."
              : error.message
          );
          setListState(settleRefresh() ? "ready" : "error");
        });
    }, 250);

    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [session.token, search, statusFilter, page, reloadKey, settleRefresh]);

  function reload() {
    if (listState === "ready") {
      if (!beginRefresh()) return;
    } else {
      cancelRefresh();
      setListState("loading");
    }
    setListError("");
    setReloadKey((key) => key + 1);
  }

  function resetToFirstPage() {
    cancelRefresh();
    setPage(1);
    setListState("loading");
    setListError("");
  }

  function handleSearchChange(event) {
    setSearch(event.target.value);
    resetToFirstPage();
  }

  function openCreate() {
    setFormError("");
    setFieldErrors({});
    setDialog({ mode: "form", client: null });
  }

  function openEdit(client) {
    setFormError("");
    setFieldErrors({});
    setDialog({ mode: "form", client });
  }

  function openDetail(client) {
    setDialog({ mode: "detail", client });
  }

  function openDelete(client) {
    setFormError("");
    setCascade(false);
    setDialog({ mode: "delete", client });
  }

  function closeDialog() {
    if (isSaving) return;
    const wasDetail = dialog && dialog.mode === "detail";
    setDialog(null);
    setFormError("");
    setFieldErrors({});
    // Follow-up changes made inside the detail view affect the row counts.
    if (wasDetail) reload();
  }

  async function handleSubmit(form) {
    const editing = dialog && dialog.client;
    setIsSaving(true);
    setFormError("");
    setFieldErrors({});

    try {
      if (editing) {
        await updateClient(
          session.token,
          editing.id,
          clientFormToPayload(form)
        );
        setActionMessage(`Updated ${form.name.trim()}.`);
      } else {
        await createClient(session.token, clientFormToPayload(form));
        setActionMessage(`Created ${form.name.trim()}.`);
      }
      setDialog(null);
      reload();
    } catch (error) {
      if (error.code === "UNAUTHORIZED") {
        setFormError("Your session expired. Please sign in again.");
      } else {
        setFormError(error.message);
        setFieldErrors(error.fieldErrors || {});
      }
    } finally {
      setIsSaving(false);
    }
  }

  async function handleDelete(client) {
    setIsSaving(true);
    setFormError("");

    try {
      await deleteClient(session.token, client.id, { cascade });
      setActionMessage(`Deleted ${client.name}.`);
      setDialog(null);
      reload();
    } catch (error) {
      if (error.code === "UNAUTHORIZED") {
        setFormError("Your session expired. Please sign in again.");
      } else {
        setFormError(error.message);
      }
    } finally {
      setIsSaving(false);
    }
  }

  const hasClients = clients.length > 0;
  const hasFilters = search || statusFilter;

  return (
    <div className="dash-page">
      <section className="dash-hero">
        <div>
          <span className="dash-eyebrow">CRM</span>
          <h2>Clients</h2>
          <p>Manage client accounts, owners and follow-ups.</p>
        </div>

        <button type="button" className="dash-button" onClick={openCreate}>
          New client
        </button>
      </section>

      <div className="dash-toolbar">
        <label className="dash-search">
          <span className="dash-visually-hidden">Search clients</span>
          <input
            type="search"
            value={search}
            onChange={handleSearchChange}
            placeholder="Search by name, company, email or industry"
          />
        </label>

        <label className="dash-filter">
          <span className="dash-visually-hidden">Filter by status</span>
          <select
            value={statusFilter}
            onChange={(event) => {
              setStatusFilter(event.target.value);
              resetToFirstPage();
            }}
          >
            <option value="">All statuses</option>
            {CLIENT_STATUSES.map((status) => (
              <option key={status} value={status}>
                {formatStatus(status)}
              </option>
            ))}
          </select>
        </label>

        <button
          type="button"
          className="dash-icon-button"
          onClick={reload}
          aria-label={refreshing ? "Refreshing clients" : "Refresh clients"}
          aria-busy={refreshing}
          title={refreshing ? "Refreshing clients" : "Refresh"}
          disabled={refreshing || listState === "loading"}
        >
          <IconRefresh />
        </button>

        <span className="dash-toolbar-count">
          {listState === "ready" && `${meta.total} client(s)`}
        </span>
      </div>

      <BackgroundRefreshStatus
        refreshing={refreshing}
        error={listState === "ready" ? listError : ""}
        label="clients"
      />

      <Notice error>{detailError}</Notice>
      {detailLoading && <p className="workspace-help" role="status">Loading requested record…</p>}
      {actionMessage && listState !== "error" && (
        <p className="dash-success" role="status">
          {actionMessage}
        </p>
      )}

      {employeesState === "error" && (
        <p className="dash-form-hint">
          The employee directory could not be loaded, so the owner selector may
          be empty.
        </p>
      )}

      {listState === "loading" && (
        <StatusPanel
          variant="loading"
          title="Loading clients…"
          description="Fetching records from the backend."
        />
      )}

      {listState === "error" && (
        <StatusPanel
          variant="error"
          title="Could not load clients"
          description={listError}
          actionLabel="Retry"
          onAction={reload}
        />
      )}

      {listState === "ready" && !hasClients && (
        <StatusPanel
          variant="empty"
          title={hasFilters ? "No matching clients" : "No clients yet"}
          description={
            hasFilters
              ? "Try different filters, or clear them to see everything."
              : "Create the first client to get started."
          }
        />
      )}

      {listState === "ready" && hasClients && (
        <>
          <div className="dash-table-wrap">
            <table className="dash-table">
              <thead>
                <tr>
                  <th>Client</th>
                  <th>Email</th>
                  <th>Industry</th>
                  <th>Owner</th>
                  <th>Status</th>
                  <th>Follow-ups</th>
                  <th className="dash-table-actions-head">Actions</th>
                </tr>
              </thead>
              <tbody>
                {clients.map((client) => (
                  <tr key={client.id}>
                    <td>
                      <button
                        type="button"
                        className="dash-link-button strong"
                        onClick={() => openDetail(client)}
                      >
                        {client.name}
                      </button>
                      {client.companyName && (
                        <em className="dash-cell-sub">{client.companyName}</em>
                      )}
                    </td>
                    <td>{client.email}</td>
                    <td>{client.industry || "—"}</td>
                    <td>
                      {client.assignedTo ? client.assignedTo.fullName : "—"}
                    </td>
                    <td>
                      <span
                        className={`dash-status-pill status-${statusTone(client.status)}`}
                      >
                        {formatStatus(client.status)}
                      </span>
                    </td>
                    <td>
                      {client.followUpCount}
                      {client.overdueFollowUpCount > 0 && (
                        <span className="dash-overdue-badge">
                          {client.overdueFollowUpCount} overdue
                        </span>
                      )}
                    </td>
                    <td>
                      <div className="dash-row-actions">
                        <button
                          type="button"
                          className="dash-link-button"
                          onClick={() => openDetail(client)}
                        >
                          View
                        </button>
                        <button
                          type="button"
                          className="dash-link-button"
                          onClick={() => openEdit(client)}
                        >
                          Edit
                        </button>
                        {isAdmin && (
                          <button
                            type="button"
                            className="dash-link-button danger"
                            onClick={() => openDelete(client)}
                          >
                            Delete
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {meta.totalPages > 1 && (
            <div className="dash-pagination">
              <button
                type="button"
                className="dash-button ghost small"
                onClick={() => setPage((current) => Math.max(1, current - 1))}
                disabled={page <= 1}
              >
                Previous
              </button>
              <span>
                Page {page} of {meta.totalPages}
              </span>
              <button
                type="button"
                className="dash-button ghost small"
                onClick={() =>
                  setPage((current) => Math.min(meta.totalPages, current + 1))
                }
                disabled={page >= meta.totalPages}
              >
                Next
              </button>
            </div>
          )}
        </>
      )}

      {dialog && dialog.mode === "form" && (
        <ClientFormDialog
          client={dialog.client}
          employees={employees}
          isSaving={isSaving}
          error={formError}
          fieldErrors={fieldErrors}
          onClose={closeDialog}
          onSubmit={handleSubmit}
        />
      )}

      {dialog && dialog.mode === "detail" && (
        <ClientDetailDialog
          key={dialog.client.id}
          client={dialog.client}
          token={session.token}
          isAdmin={isAdmin}
          employees={employees}
          onClose={closeDialog}
          onEdit={(client) => openEdit(client)}
          onDelete={(client) => openDelete(client)}
        />
      )}

      {dialog && dialog.mode === "delete" && (
        <div className="dash-modal-backdrop" role="presentation">
          <div
            className="dash-modal narrow"
            role="dialog"
            aria-modal="true"
            aria-label="Confirm delete client"
          >
            <div className="dash-modal-head">
              <div>
                <span className="dash-eyebrow">Confirm</span>
                <h3>Delete client</h3>
              </div>
            </div>

            <p className="dash-modal-text">
              Delete <strong>{dialog.client.name}</strong>? This action cannot
              be undone and is restricted to administrators.
            </p>

            <label className="dash-checkbox-inline">
              <input
                type="checkbox"
                checked={cascade}
                onChange={(event) => setCascade(event.target.checked)}
                disabled={isSaving}
              />
              <span>Also delete this client&apos;s follow-ups</span>
            </label>

            {formError && (
              <p className="dash-form-error" role="alert">
                {formError}
              </p>
            )}

            <div className="dash-form-actions">
              <button
                type="button"
                className="dash-button ghost"
                onClick={closeDialog}
                disabled={isSaving}
              >
                Cancel
              </button>
              <button
                type="button"
                className="dash-button danger"
                onClick={() => handleDelete(dialog.client)}
                disabled={isSaving}
              >
                {isSaving ? "Deleting…" : "Delete"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
