import { useEffect, useState } from "react";
import {
  createLead,
  deleteLead,
  listCampaigns,
  listEmployees,
  listLeads,
  updateLead,
} from "../api";
import {
  LEAD_PRIORITIES,
  LEAD_STATUSES,
  leadFormToPayload,
} from "../leadForm";
import LeadFormDialog from "../components/LeadFormDialog";
import LeadDetailDialog from "../components/LeadDetailDialog";
import StatusPanel from "../components/StatusPanel";
import { IconRefresh } from "../components/Icons";
import { formatStatus, priorityTone, statusTone } from "../format";
import { Notice } from "../components/WorkspaceUI";
import { useRecordDialog } from "../useRecordDialog";
import BackgroundRefreshStatus from "../components/BackgroundRefreshStatus";
import { useRetainedRefresh } from "../useRetainedRefresh";

const PAGE_SIZE = 10;

function formatValue(value) {
  if (value === null || value === undefined || value === "") return "—";
  const numeric = Number(value);
  if (Number.isNaN(numeric)) return value;
  return numeric.toLocaleString(undefined, {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  });
}

/**
 * Leads page.
 *
 * Loads real leads from GET /api/leads with the stored JWT. Assignee selectors
 * come from the employee directory. Conversion of a WON lead happens in the
 * detail dialog and is guarded by the backend against duplicates.
 */
export default function LeadsPage({ session }) {
  const isAdmin = (session.role || "").toUpperCase() === "ADMIN";

  const [leads, setLeads] = useState([]);
  const [listState, setListState] = useState("loading");
  const [listError, setListError] = useState("");
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [priorityFilter, setPriorityFilter] = useState("");
  const [assigneeFilter, setAssigneeFilter] = useState("");
  const [campaignFilter, setCampaignFilter] = useState("");
  const [page, setPage] = useState(1);
  const [meta, setMeta] = useState({ total: 0, totalPages: 0 });
  const [reloadKey, setReloadKey] = useState(0);
  const { refreshing, beginRefresh, settleRefresh, cancelRefresh } =
    useRetainedRefresh();

  const [employees, setEmployees] = useState([]);
  const [employeesState, setEmployeesState] = useState("loading");

  const [campaigns, setCampaigns] = useState([]);
  const [campaignsState, setCampaignsState] = useState("loading");

  const { dialog, setDialog, detailError, detailLoading } = useRecordDialog("/api/leads", "lead");
  const [isSaving, setIsSaving] = useState(false);
  const [formError, setFormError] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});
  const [actionMessage, setActionMessage] = useState("");
  const [cascade, setCascade] = useState(false);

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

    listCampaigns(session.token, { page: 1, size: 100 })
      .then((data) => {
        if (cancelled) return;
        setCampaigns(data.items);
        setCampaignsState("ready");
      })
      .catch(() => {
        if (cancelled) return;
        setCampaignsState("error");
      });

    return () => {
      cancelled = true;
    };
  }, [session.token]);

  useEffect(() => {
    let cancelled = false;

    const timer = setTimeout(() => {
      listLeads(session.token, {
        search,
        status: statusFilter,
        priority: priorityFilter,
        assignedToId: assigneeFilter,
        campaignId: campaignFilter,
        page,
        size: PAGE_SIZE,
      })
        .then((data) => {
          if (cancelled) return;
          setLeads(data.items);
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
    statusFilter,
    priorityFilter,
    assigneeFilter,
    campaignFilter,
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
    setDialog({ mode: "form", lead: null });
  }

  function openEdit(lead) {
    setFormError("");
    setFieldErrors({});
    setDialog({ mode: "form", lead });
  }

  function openDetail(lead) {
    setDialog({ mode: "detail", lead });
  }

  function openDelete(lead) {
    setFormError("");
    setCascade(false);
    setDialog({ mode: "delete", lead });
  }

  function closeDialog() {
    if (isSaving) return;
    const wasDetail = dialog && dialog.mode === "detail";
    setDialog(null);
    setFormError("");
    setFieldErrors({});
    if (wasDetail) reload();
  }

  async function handleSubmit(form) {
    const editing = dialog && dialog.lead;
    setIsSaving(true);
    setFormError("");
    setFieldErrors({});

    try {
      if (editing) {
        await updateLead(session.token, editing.id, leadFormToPayload(form));
        setActionMessage(`Updated ${form.name.trim()}.`);
      } else {
        await createLead(session.token, leadFormToPayload(form));
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

  async function handleDelete(lead) {
    setIsSaving(true);
    setFormError("");

    try {
      await deleteLead(session.token, lead.id, { cascade });
      setActionMessage(`Deleted ${lead.name}.`);
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

  const hasLeads = leads.length > 0;
  const hasFilters =
    search || statusFilter || priorityFilter || assigneeFilter || campaignFilter;

  return (
    <div className="dash-page">
      <section className="dash-hero">
        <div>
          <span className="dash-eyebrow">CRM</span>
          <h2>Leads</h2>
          <p>Track the pipeline and convert won leads into clients.</p>
        </div>

        <button type="button" className="dash-button" onClick={openCreate}>
          New lead
        </button>
      </section>

      <div className="dash-toolbar">
        <label className="dash-search">
          <span className="dash-visually-hidden">Search leads</span>
          <input
            type="search"
            value={search}
            onChange={(event) => {
              setSearch(event.target.value);
              resetToFirstPage();
            }}
            placeholder="Search by name, company, email or source"
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
            {LEAD_STATUSES.map((status) => (
              <option key={status} value={status}>
                {formatStatus(status)}
              </option>
            ))}
          </select>
        </label>

        <label className="dash-filter">
          <span className="dash-visually-hidden">Filter by priority</span>
          <select
            value={priorityFilter}
            onChange={(event) => {
              setPriorityFilter(event.target.value);
              resetToFirstPage();
            }}
          >
            <option value="">All priorities</option>
            {LEAD_PRIORITIES.map((priority) => (
              <option key={priority} value={priority}>
                {priority}
              </option>
            ))}
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

        <label className="dash-filter">
          <span className="dash-visually-hidden">Filter by campaign</span>
          <select
            value={campaignFilter}
            onChange={(event) => {
              setCampaignFilter(event.target.value);
              resetToFirstPage();
            }}
          >
            <option value="">All campaigns</option>
            {campaigns.map((campaign) => (
              <option key={campaign.id} value={campaign.id}>
                {campaign.name}
              </option>
            ))}
          </select>
        </label>

        <button
          type="button"
          className="dash-icon-button"
          onClick={reload}
          aria-label={refreshing ? "Refreshing leads" : "Refresh leads"}
          aria-busy={refreshing}
          title={refreshing ? "Refreshing leads" : "Refresh"}
          disabled={refreshing || listState === "loading"}
        >
          <IconRefresh />
        </button>

        <span className="dash-toolbar-count">
          {listState === "ready" && `${meta.total} lead(s)`}
        </span>
      </div>

      <BackgroundRefreshStatus
        refreshing={refreshing}
        error={listState === "ready" ? listError : ""}
        label="leads"
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
          The employee directory could not be loaded, so the assignee selector
          may be empty.
        </p>
      )}

      {campaignsState === "error" && (
        <p className="dash-form-hint">
          Campaigns could not be loaded, so campaign attribution may be
          unavailable.
        </p>
      )}

      {listState === "loading" && (
        <StatusPanel
          variant="loading"
          title="Loading leads…"
          description="Fetching records from the backend."
        />
      )}

      {listState === "error" && (
        <StatusPanel
          variant="error"
          title="Could not load leads"
          description={listError}
          actionLabel="Retry"
          onAction={reload}
        />
      )}

      {listState === "ready" && !hasLeads && (
        <StatusPanel
          variant="empty"
          title={hasFilters ? "No matching leads" : "No leads yet"}
          description={
            hasFilters
              ? "Try different filters, or clear them to see everything."
              : "Create the first lead to get started."
          }
        />
      )}

      {listState === "ready" && hasLeads && (
        <>
          <div className="dash-table-wrap">
            <table className="dash-table">
              <thead>
                <tr>
                  <th>Lead</th>
                  <th>Email</th>
                  <th>Source</th>
                  <th>Campaign</th>
                  <th>Owner</th>
                  <th>Status</th>
                  <th>Priority</th>
                  <th>Est. value</th>
                  <th>Follow-ups</th>
                  <th className="dash-table-actions-head">Actions</th>
                </tr>
              </thead>
              <tbody>
                {leads.map((lead) => (
                  <tr key={lead.id}>
                    <td>
                      <button
                        type="button"
                        className="dash-link-button strong"
                        onClick={() => openDetail(lead)}
                      >
                        {lead.name}
                      </button>
                      {lead.company && (
                        <em className="dash-cell-sub">{lead.company}</em>
                      )}
                    </td>
                    <td>{lead.email}</td>
                    <td>{lead.source || "—"}</td>
                    <td>{lead.campaignName || "—"}</td>
                    <td>{lead.assignedTo ? lead.assignedTo.fullName : "—"}</td>
                    <td>
                      <span
                        className={`dash-status-pill status-${statusTone(lead.status)}`}
                      >
                        {formatStatus(lead.status)}
                      </span>
                    </td>
                    <td>
                      <span
                        className={`dash-priority-pill priority-${priorityTone(lead.priority)}`}
                      >
                        {lead.priority}
                      </span>
                    </td>
                    <td>{formatValue(lead.estimatedValue)}</td>
                    <td>
                      {lead.followUpCount}
                      {lead.overdueFollowUpCount > 0 && (
                        <span className="dash-overdue-badge">
                          {lead.overdueFollowUpCount} overdue
                        </span>
                      )}
                    </td>
                    <td>
                      <div className="dash-row-actions">
                        <button
                          type="button"
                          className="dash-link-button"
                          onClick={() => openDetail(lead)}
                        >
                          View
                        </button>
                        <button
                          type="button"
                          className="dash-link-button"
                          onClick={() => openEdit(lead)}
                        >
                          Edit
                        </button>
                        {isAdmin && (
                          <button
                            type="button"
                            className="dash-link-button danger"
                            onClick={() => openDelete(lead)}
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
        <LeadFormDialog
          lead={dialog.lead}
          employees={employees}
          campaigns={campaigns}
          isSaving={isSaving}
          error={formError}
          fieldErrors={fieldErrors}
          onClose={closeDialog}
          onSubmit={handleSubmit}
        />
      )}

      {dialog && dialog.mode === "detail" && (
        <LeadDetailDialog
          key={dialog.lead.id}
          lead={dialog.lead}
          token={session.token}
          isAdmin={isAdmin}
          employees={employees}
          onClose={closeDialog}
          onEdit={(lead) => openEdit(lead)}
          onDelete={(lead) => openDelete(lead)}
        />
      )}

      {dialog && dialog.mode === "delete" && (
        <div className="dash-modal-backdrop" role="presentation">
          <div
            className="dash-modal narrow"
            role="dialog"
            aria-modal="true"
            aria-label="Confirm delete lead"
          >
            <div className="dash-modal-head">
              <div>
                <span className="dash-eyebrow">Confirm</span>
                <h3>Delete lead</h3>
              </div>
            </div>

            <p className="dash-modal-text">
              Delete <strong>{dialog.lead.name}</strong>? This action cannot be
              undone and is restricted to administrators.
            </p>

            <label className="dash-checkbox-inline">
              <input
                type="checkbox"
                checked={cascade}
                onChange={(event) => setCascade(event.target.checked)}
                disabled={isSaving}
              />
              <span>Also delete this lead&apos;s follow-ups</span>
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
                onClick={() => handleDelete(dialog.lead)}
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
