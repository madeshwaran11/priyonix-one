import { useEffect, useState } from "react";
import {
  createFollowUp,
  deleteFollowUp,
  listClients,
  listEmployees,
  listFollowUps,
  listLeads,
  updateFollowUp,
} from "../api";
import { followUpFormToPayload } from "../followUpForm";
import FollowUpFormDialog from "../components/FollowUpFormDialog";
import StatusPanel from "../components/StatusPanel";
import { IconRefresh } from "../components/Icons";
import { formatDate, formatStatus } from "../format";
import BackgroundRefreshStatus from "../components/BackgroundRefreshStatus";
import { useRetainedRefresh } from "../useRetainedRefresh";

const PAGE_SIZE = 10;

/**
 * Follow-ups page.
 *
 * Loads real activities from GET /api/follow-ups with the stored JWT. Each row
 * links to a lead or a client, and overdue activities (due before today and not
 * completed) are highlighted using the server-computed flag.
 */
export default function FollowUpsPage({ session }) {
  const isAdmin = (session.role || "").toUpperCase() === "ADMIN";

  const [followUps, setFollowUps] = useState([]);
  const [listState, setListState] = useState("loading");
  const [listError, setListError] = useState("");
  const [search, setSearch] = useState("");
  const [completedFilter, setCompletedFilter] = useState("");
  const [assigneeFilter, setAssigneeFilter] = useState("");
  const [overdueOnly, setOverdueOnly] = useState(false);
  const [page, setPage] = useState(1);
  const [meta, setMeta] = useState({ total: 0, totalPages: 0 });
  const [reloadKey, setReloadKey] = useState(0);
  const { refreshing, beginRefresh, settleRefresh, cancelRefresh } =
    useRetainedRefresh();

  const [employees, setEmployees] = useState([]);
  const [leads, setLeads] = useState([]);
  const [clients, setClients] = useState([]);
  const [lookupsState, setLookupsState] = useState("loading");

  const [dialog, setDialog] = useState(null);
  const [isSaving, setIsSaving] = useState(false);
  const [formError, setFormError] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});
  const [actionMessage, setActionMessage] = useState("");
  const [busyId, setBusyId] = useState("");

  // Employee, lead and client lookups for selectors and filters.
  useEffect(() => {
    let cancelled = false;

    Promise.all([
      listEmployees(session.token),
      listLeads(session.token, { page: 1, size: 100 }),
      listClients(session.token, { page: 1, size: 100 }),
    ])
      .then(([employeeData, leadData, clientData]) => {
        if (cancelled) return;
        setEmployees(employeeData);
        setLeads(leadData.items);
        setClients(clientData.items);
        setLookupsState("ready");
      })
      .catch(() => {
        if (cancelled) return;
        setLookupsState("error");
      });

    return () => {
      cancelled = true;
    };
  }, [session.token, reloadKey]);

  // Debounced follow-up list fetch.
  useEffect(() => {
    let cancelled = false;

    const timer = setTimeout(() => {
      listFollowUps(session.token, {
        search,
        completed: completedFilter,
        assignedToId: assigneeFilter,
        overdue: overdueOnly,
        page,
        size: PAGE_SIZE,
      })
        .then((data) => {
          if (cancelled) return;
          setFollowUps(data.items);
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
  }, [
    session.token,
    search,
    completedFilter,
    assigneeFilter,
    overdueOnly,
    page,
    reloadKey,
    settleRefresh,
  ]);

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

  function openCreate() {
    setFormError("");
    setFieldErrors({});
    setDialog({ mode: "form", followUp: null });
  }

  function openEdit(followUp) {
    setFormError("");
    setFieldErrors({});
    setDialog({ mode: "form", followUp });
  }

  function openDelete(followUp) {
    setFormError("");
    setDialog({ mode: "delete", followUp });
  }

  function closeDialog() {
    if (isSaving) return;
    setDialog(null);
    setFormError("");
    setFieldErrors({});
  }

  async function handleSubmit(form) {
    const editing = dialog && dialog.followUp;
    setIsSaving(true);
    setFormError("");
    setFieldErrors({});

    try {
      if (editing) {
        await updateFollowUp(
          session.token,
          editing.id,
          followUpFormToPayload(form)
        );
        setActionMessage("Follow-up updated.");
      } else {
        await createFollowUp(session.token, followUpFormToPayload(form));
        setActionMessage("Follow-up created.");
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

  async function toggleCompleted(followUp) {
    setBusyId(followUp.id);
    setFormError("");

    try {
      await updateFollowUp(session.token, followUp.id, {
        activityType: followUp.activityType,
        dueDate: followUp.dueDate,
        notes: followUp.notes,
        completed: !followUp.completed,
        assignedToId: followUp.assignedToId,
        leadId: followUp.leadId,
        clientId: followUp.clientId,
      });
      reload();
    } catch (error) {
      setActionMessage("");
      setListError(error.message);
      setListState("error");
    } finally {
      setBusyId("");
    }
  }

  async function handleDelete(followUp) {
    setIsSaving(true);
    setFormError("");

    try {
      await deleteFollowUp(session.token, followUp.id);
      setActionMessage("Follow-up deleted.");
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

  const hasFollowUps = followUps.length > 0;
  const hasFilters = search || completedFilter || assigneeFilter || overdueOnly;

  return (
    <div className="dash-page">
      <section className="dash-hero">
        <div>
          <span className="dash-eyebrow">CRM</span>
          <h2>Follow-ups</h2>
          <p>Schedule and complete activities linked to leads or clients.</p>
        </div>

        <button type="button" className="dash-button" onClick={openCreate}>
          New follow-up
        </button>
      </section>

      <div className="dash-toolbar">
        <label className="dash-search">
          <span className="dash-visually-hidden">Search follow-ups</span>
          <input
            type="search"
            value={search}
            onChange={(event) => {
              setSearch(event.target.value);
              resetToFirstPage();
            }}
            placeholder="Search by notes or activity type"
          />
        </label>

        <label className="dash-filter">
          <span className="dash-visually-hidden">Filter by status</span>
          <select
            value={completedFilter}
            onChange={(event) => {
              setCompletedFilter(event.target.value);
              resetToFirstPage();
            }}
          >
            <option value="">All</option>
            <option value="false">Open</option>
            <option value="true">Completed</option>
          </select>
        </label>

        <label className="dash-filter">
          <span className="dash-visually-hidden">Filter by assignee</span>
          <select
            value={assigneeFilter}
            onChange={(event) => {
              setAssigneeFilter(event.target.value);
              resetToFirstPage();
            }}
          >
            <option value="">All assignees</option>
            {employees.map((employee) => (
              <option key={employee.id} value={employee.id}>
                {employee.fullName}
              </option>
            ))}
          </select>
        </label>

        <label className="dash-checkbox-inline">
          <input
            type="checkbox"
            checked={overdueOnly}
            onChange={(event) => {
              setOverdueOnly(event.target.checked);
              resetToFirstPage();
            }}
          />
          <span>Overdue only</span>
        </label>

        <button
          type="button"
          className="dash-icon-button"
          onClick={reload}
          aria-label={refreshing ? "Refreshing follow-ups" : "Refresh follow-ups"}
          aria-busy={refreshing}
          title={refreshing ? "Refreshing follow-ups" : "Refresh"}
          disabled={refreshing || listState === "loading"}
        >
          <IconRefresh />
        </button>

        <span className="dash-toolbar-count">
          {listState === "ready" && `${meta.total} follow-up(s)`}
        </span>
      </div>

      <BackgroundRefreshStatus
        refreshing={refreshing}
        error={listState === "ready" ? listError : ""}
        label="follow-ups"
      />

      {actionMessage && listState !== "error" && (
        <p className="dash-success" role="status">
          {actionMessage}
        </p>
      )}

      {lookupsState === "error" && (
        <p className="dash-form-hint">
          Some lookups (employees, leads or clients) could not be loaded, so
          some selectors may be empty.
        </p>
      )}

      {listState === "loading" && (
        <StatusPanel
          variant="loading"
          title="Loading follow-ups…"
          description="Fetching records from the backend."
        />
      )}

      {listState === "error" && (
        <StatusPanel
          variant="error"
          title="Could not load follow-ups"
          description={listError}
          actionLabel="Retry"
          onAction={reload}
        />
      )}

      {listState === "ready" && !hasFollowUps && (
        <StatusPanel
          variant="empty"
          title={hasFilters ? "No matching follow-ups" : "No follow-ups yet"}
          description={
            hasFilters
              ? "Try different filters, or clear them to see everything."
              : "Schedule the first follow-up to get started."
          }
        />
      )}

      {listState === "ready" && hasFollowUps && (
        <>
          <div className="dash-table-wrap">
            <table className="dash-table">
              <thead>
                <tr>
                  <th>Activity</th>
                  <th>Linked to</th>
                  <th>Due</th>
                  <th>Assignee</th>
                  <th>Status</th>
                  <th className="dash-table-actions-head">Actions</th>
                </tr>
              </thead>
              <tbody>
                {followUps.map((followUp) => (
                  <tr
                    key={followUp.id}
                    className={followUp.overdue ? "is-overdue-row" : ""}
                  >
                    <td>
                      <strong>{formatStatus(followUp.activityType)}</strong>
                      {followUp.notes && (
                        <em className="dash-cell-sub">{followUp.notes}</em>
                      )}
                    </td>
                    <td>
                      {followUp.leadId
                        ? `Lead · ${followUp.leadName || "—"}`
                        : `Client · ${followUp.clientName || "—"}`}
                    </td>
                    <td>
                      {formatDate(followUp.dueDate)}
                      {followUp.overdue && (
                        <span className="dash-overdue-badge">Overdue</span>
                      )}
                    </td>
                    <td>
                      {followUp.assignedTo
                        ? followUp.assignedTo.fullName
                        : "Unassigned"}
                    </td>
                    <td>
                      <span
                        className={`dash-status-pill status-${
                          followUp.completed ? "completed" : "todo"
                        }`}
                      >
                        {followUp.completed ? "Completed" : "Open"}
                      </span>
                    </td>
                    <td>
                      <div className="dash-row-actions">
                        <button
                          type="button"
                          className="dash-link-button"
                          onClick={() => toggleCompleted(followUp)}
                          disabled={busyId === followUp.id}
                        >
                          {followUp.completed ? "Reopen" : "Complete"}
                        </button>
                        <button
                          type="button"
                          className="dash-link-button"
                          onClick={() => openEdit(followUp)}
                        >
                          Edit
                        </button>
                        {isAdmin && (
                          <button
                            type="button"
                            className="dash-link-button danger"
                            onClick={() => openDelete(followUp)}
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
        <FollowUpFormDialog
          followUp={dialog.followUp}
          employees={employees}
          leads={leads}
          clients={clients}
          isSaving={isSaving}
          error={formError}
          fieldErrors={fieldErrors}
          onClose={closeDialog}
          onSubmit={handleSubmit}
        />
      )}

      {dialog && dialog.mode === "delete" && (
        <div className="dash-modal-backdrop" role="presentation">
          <div
            className="dash-modal narrow"
            role="dialog"
            aria-modal="true"
            aria-label="Confirm delete follow-up"
          >
            <div className="dash-modal-head">
              <div>
                <span className="dash-eyebrow">Confirm</span>
                <h3>Delete follow-up</h3>
              </div>
            </div>

            <p className="dash-modal-text">
              Delete this{" "}
              <strong>{formatStatus(dialog.followUp.activityType)}</strong>{" "}
              follow-up? This action cannot be undone and is restricted to
              administrators.
            </p>

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
                onClick={() => handleDelete(dialog.followUp)}
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
