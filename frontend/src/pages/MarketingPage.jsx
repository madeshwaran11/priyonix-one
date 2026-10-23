import { useEffect, useState } from "react";
import {
  createCampaign,
  createLead,
  deleteCampaign,
  getCampaign,
  listCampaigns,
  listEmployees,
  updateCampaign,
} from "../api";
import {
  CAMPAIGN_CHANNELS,
  CAMPAIGN_STATUSES,
  campaignFormToPayload,
  validateCampaignForm,
} from "../campaignForm";
import { leadFormToPayload } from "../leadForm";
import CampaignFormDialog from "../components/CampaignFormDialog";
import CampaignDetailDialog from "../components/CampaignDetailDialog";
import LeadFormDialog from "../components/LeadFormDialog";
import StatusPanel from "../components/StatusPanel";
import { IconRefresh } from "../components/Icons";
import { formatDate, formatMoney, formatStatus, statusTone } from "../format";
import { Notice } from "../components/WorkspaceUI";
import { useRecordDialog } from "../useRecordDialog";
import BackgroundRefreshStatus from "../components/BackgroundRefreshStatus";
import { useRetainedRefresh } from "../useRetainedRefresh";

const PAGE_SIZE = 10;

export default function MarketingPage({ session }) {
  const isAdmin = (session.role || "").toUpperCase() === "ADMIN";

  const [campaigns, setCampaigns] = useState([]);
  const [listState, setListState] = useState("loading");
  const [listError, setListError] = useState("");
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [channelFilter, setChannelFilter] = useState("");
  const [page, setPage] = useState(1);
  const [meta, setMeta] = useState({ total: 0, totalPages: 0 });
  const [reloadKey, setReloadKey] = useState(0);
  const { refreshing, beginRefresh, settleRefresh, cancelRefresh } =
    useRetainedRefresh();

  const [employees, setEmployees] = useState([]);
  const [employeesState, setEmployeesState] = useState("loading");

  const { dialog, setDialog, updateRecord, detailError, detailLoading } = useRecordDialog("/api/campaigns", "campaign");
  const [isSaving, setIsSaving] = useState(false);
  const [formError, setFormError] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});
  const [actionMessage, setActionMessage] = useState("");
  const [detachLeads, setDetachLeads] = useState(false);

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

  useEffect(() => {
    let cancelled = false;

    const timer = setTimeout(() => {
      listCampaigns(session.token, {
        search,
        status: statusFilter,
        channel: channelFilter,
        page,
        size: PAGE_SIZE,
      })
        .then((data) => {
          if (cancelled) return;
          setCampaigns(data.items);
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
    channelFilter,
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

  function clearMessages() {
    setFormError("");
    setFieldErrors({});
  }

  function openCreate() {
    clearMessages();
    setDialog({ mode: "form", campaign: null });
  }

  function openEdit(campaign) {
    clearMessages();
    setDialog({ mode: "form", campaign });
  }

  function openDetail(campaign) {
    clearMessages();
    setDialog({ mode: "detail", campaign });
  }

  function openDelete(campaign) {
    clearMessages();
    setDetachLeads(false);
    setDialog({ mode: "delete", campaign });
  }

  function openAddLead(campaign) {
    clearMessages();
    setDialog({ mode: "lead", campaign });
  }

  function closeDialog() {
    if (isSaving) return;
    const wasDetail = dialog && dialog.mode === "detail";
    setDialog(null);
    clearMessages();
    if (wasDetail) reload();
  }

  async function handleSubmit(form) {
    const validation = validateCampaignForm(form);
    if (validation.message) {
      setFieldErrors(validation.fieldErrors);
      setFormError(validation.message);
      return;
    }

    const editing = dialog && dialog.campaign;
    setIsSaving(true);
    clearMessages();

    try {
      if (editing) {
        await updateCampaign(
          session.token,
          editing.id,
          campaignFormToPayload(form)
        );
        setActionMessage(`Updated ${form.name.trim()}.`);
      } else {
        await createCampaign(session.token, campaignFormToPayload(form));
        setActionMessage(`Created ${form.name.trim()}.`);
      }
      setDialog(null);
      reload();
    } catch (error) {
      setFormError(
        error.code === "UNAUTHORIZED"
          ? "Your session expired. Please sign in again."
          : error.message
      );
      setFieldErrors(error.fieldErrors || {});
    } finally {
      setIsSaving(false);
    }
  }

  async function handleDelete(campaign) {
    setIsSaving(true);
    clearMessages();

    try {
      await deleteCampaign(session.token, campaign.id, { detachLeads });
      setActionMessage(`Deleted ${campaign.name}.`);
      setDialog(null);
      reload();
    } catch (error) {
      setFormError(
        error.code === "UNAUTHORIZED"
          ? "Your session expired. Please sign in again."
          : error.message
      );
    } finally {
      setIsSaving(false);
    }
  }

  async function handleLeadSubmit(form) {
    const campaign = dialog && dialog.campaign;
    if (!campaign) return;

    setIsSaving(true);
    clearMessages();

    try {
      const payload = leadFormToPayload({
        ...form,
        campaignId: campaign.id,
      });
      const lead = await createLead(session.token, payload);
      const refreshed = await getCampaign(session.token, campaign.id);

      setActionMessage(
        `Added lead ${lead.name} to ${campaign.name}.`
      );
      setDialog({ mode: "detail", campaign: refreshed });
      reload();
    } catch (error) {
      setFormError(
        error.code === "UNAUTHORIZED"
          ? "Your session expired. Please sign in again."
          : error.message
      );
      setFieldErrors(error.fieldErrors || {});
    } finally {
      setIsSaving(false);
    }
  }

  function handleCampaignChanged(updated) {
    updateRecord(updated);
    reload();
  }

  const hasCampaigns = campaigns.length > 0;
  const hasFilters = search || statusFilter || channelFilter;

  return (
    <div className="dash-page">
      <section className="dash-hero">
        <div>
          <span className="dash-eyebrow">Marketing</span>
          <h2>Campaigns</h2>
          <p>
            Plan campaigns, attribute CRM leads and measure real conversion
            performance.
          </p>
        </div>

        <button type="button" className="dash-button" onClick={openCreate}>
          New campaign
        </button>
      </section>

      <div className="dash-toolbar">
        <label className="dash-search">
          <span className="dash-visually-hidden">Search campaigns</span>
          <input
            type="search"
            value={search}
            onChange={(event) => {
              setSearch(event.target.value);
              resetToFirstPage();
            }}
            placeholder="Search by name, description or channel"
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
            {CAMPAIGN_STATUSES.map((status) => (
              <option key={status} value={status}>
                {formatStatus(status)}
              </option>
            ))}
          </select>
        </label>

        <label className="dash-filter">
          <span className="dash-visually-hidden">Filter by channel</span>
          <select
            value={channelFilter}
            onChange={(event) => {
              setChannelFilter(event.target.value);
              resetToFirstPage();
            }}
          >
            <option value="">All channels</option>
            {CAMPAIGN_CHANNELS.map((channel) => (
              <option key={channel} value={channel}>
                {formatStatus(channel)}
              </option>
            ))}
          </select>
        </label>

        <button
          type="button"
          className="dash-icon-button"
          onClick={reload}
          aria-label={refreshing ? "Refreshing campaigns" : "Refresh campaigns"}
          aria-busy={refreshing}
          title={refreshing ? "Refreshing campaigns" : "Refresh"}
          disabled={refreshing || listState === "loading"}
        >
          <IconRefresh />
        </button>

        <span className="dash-toolbar-count">
          {listState === "ready" && `${meta.total} campaign(s)`}
        </span>
      </div>

      <BackgroundRefreshStatus
        refreshing={refreshing}
        error={listState === "ready" ? listError : ""}
        label="campaigns"
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
          The employee directory could not be loaded; lead assignment may be
          unavailable.
        </p>
      )}

      {listState === "loading" && (
        <StatusPanel
          variant="loading"
          title="Loading campaigns…"
          description="Fetching campaign records from the backend."
        />
      )}

      {listState === "error" && (
        <StatusPanel
          variant="error"
          title="Could not load campaigns"
          description={listError}
          actionLabel="Retry"
          onAction={reload}
        />
      )}

      {listState === "ready" && !hasCampaigns && (
        <StatusPanel
          variant="empty"
          title={hasFilters ? "No matching campaigns" : "No campaigns yet"}
          description={
            hasFilters
              ? "Try different filters, or clear them to see everything."
              : "Create your first campaign to start attributing leads."
          }
        />
      )}

      {listState === "ready" && hasCampaigns && (
        <>
          <div className="dash-table-wrap">
            <table className="dash-table">
              <thead>
                <tr>
                  <th>Campaign</th>
                  <th>Channel</th>
                  <th>Status</th>
                  <th>Dates</th>
                  <th>Leads</th>
                  <th>Conversions</th>
                  <th>Budget / spend</th>
                  <th className="dash-table-actions-head">Actions</th>
                </tr>
              </thead>
              <tbody>
                {campaigns.map((campaign) => (
                  <tr key={campaign.id}>
                    <td>
                      <button
                        type="button"
                        className="dash-link-button strong"
                        onClick={() => openDetail(campaign)}
                      >
                        {campaign.name}
                      </button>
                      {campaign.description && (
                        <em className="dash-cell-sub">
                          {campaign.description}
                        </em>
                      )}
                    </td>
                    <td>{formatStatus(campaign.channel)}</td>
                    <td>
                      <span
                        className={`dash-status-pill status-${statusTone(campaign.status)}`}
                      >
                        {formatStatus(campaign.status)}
                      </span>
                      {campaign.activeNow && (
                        <span className="dash-status-pill status-active">
                          Running
                        </span>
                      )}
                    </td>
                    <td>
                      {formatDate(campaign.startDate)}
                      <em className="dash-cell-sub">
                        to {formatDate(campaign.endDate)}
                      </em>
                    </td>
                    <td>{campaign.leadCount}</td>
                    <td>
                      {campaign.convertedLeadCount}
                      <em className="dash-cell-sub">
                        {campaign.conversionRate}% rate
                      </em>
                    </td>
                    <td>
                      ₹ {formatMoney(campaign.budget)}
                      <em className="dash-cell-sub">
                        spent ₹ {formatMoney(campaign.spend)}
                      </em>
                    </td>
                    <td>
                      <div className="dash-row-actions">
                        <button
                          type="button"
                          className="dash-link-button"
                          onClick={() => openDetail(campaign)}
                        >
                          View
                        </button>
                        <button
                          type="button"
                          className="dash-link-button"
                          onClick={() => openEdit(campaign)}
                        >
                          Edit
                        </button>
                        {isAdmin && (
                          <button
                            type="button"
                            className="dash-link-button danger"
                            onClick={() => openDelete(campaign)}
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
        <CampaignFormDialog
          campaign={dialog.campaign}
          isSaving={isSaving}
          error={formError}
          fieldErrors={fieldErrors}
          onClose={closeDialog}
          onSubmit={handleSubmit}
        />
      )}

      {dialog && dialog.mode === "detail" && (
        <CampaignDetailDialog
          key={dialog.campaign.id}
          campaign={dialog.campaign}
          token={session.token}
          isAdmin={isAdmin}
          onClose={closeDialog}
          onEdit={openEdit}
          onDelete={openDelete}
          onAddLead={openAddLead}
          onChanged={handleCampaignChanged}
        />
      )}

      {dialog && dialog.mode === "lead" && (
        <LeadFormDialog
          lead={null}
          campaigns={[dialog.campaign]}
          defaultCampaignId={dialog.campaign.id}
          employees={employees}
          isSaving={isSaving}
          error={formError}
          fieldErrors={fieldErrors}
          onClose={() => {
            setDialog({ mode: "detail", campaign: dialog.campaign });
            clearMessages();
          }}
          onSubmit={handleLeadSubmit}
        />
      )}

      {dialog && dialog.mode === "delete" && (
        <div className="dash-modal-backdrop" role="presentation">
          <div
            className="dash-modal narrow"
            role="dialog"
            aria-modal="true"
            aria-label="Confirm delete campaign"
          >
            <div className="dash-modal-head">
              <div>
                <span className="dash-eyebrow">Confirm</span>
                <h3>Delete campaign</h3>
              </div>
            </div>

            <p className="dash-modal-text">
              Delete <strong>{dialog.campaign.name}</strong>? This action cannot
              be undone and is restricted to administrators.
            </p>

            {dialog.campaign.leadCount > 0 && (
              <label className="dash-checkbox-inline">
                <input
                  type="checkbox"
                  checked={detachLeads}
                  onChange={(event) => setDetachLeads(event.target.checked)}
                  disabled={isSaving}
                />
                <span>
                  Detach {dialog.campaign.leadCount} attributed lead(s) instead
                  of blocking the delete
                </span>
              </label>
            )}

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
                onClick={() => handleDelete(dialog.campaign)}
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
