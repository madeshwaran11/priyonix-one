import { useEffect, useState } from "react";
import {
  createProject,
  deleteProject,
  listEmployees,
  listProjects,
  updateProject,
} from "../api";
import {
  PROJECT_PRIORITIES,
  PROJECT_STATUSES,
  projectFormToPayload,
} from "../projectForm";
import ProjectFormDialog from "../components/ProjectFormDialog";
import ProjectDetailDialog from "../components/ProjectDetailDialog";
import StatusPanel from "../components/StatusPanel";
import { IconRefresh } from "../components/Icons";
import { formatDate, formatStatus, priorityTone, statusTone } from "../format";
import { Notice } from "../components/WorkspaceUI";
import { useRecordDialog } from "../useRecordDialog";
import BackgroundRefreshStatus from "../components/BackgroundRefreshStatus";
import { useRetainedRefresh } from "../useRetainedRefresh";

const PAGE_SIZE = 10;

/**
 * Projects page.
 *
 * Loads real projects from GET /api/projects with the stored JWT. Manager and
 * team selectors are populated from the employee directory. No demo data is
 * shown: the page renders loading, error or empty states until the API
 * responds.
 */
export default function ProjectsPage({ session }) {
  const isAdmin = (session.role || "").toUpperCase() === "ADMIN";

  const [projects, setProjects] = useState([]);
  const [listState, setListState] = useState("loading");
  const [listError, setListError] = useState("");
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [priorityFilter, setPriorityFilter] = useState("");
  const [page, setPage] = useState(1);
  const [meta, setMeta] = useState({ total: 0, totalPages: 0 });
  const [reloadKey, setReloadKey] = useState(0);
  const { refreshing, beginRefresh, settleRefresh, cancelRefresh } =
    useRetainedRefresh();

  const [employees, setEmployees] = useState([]);
  const [employeesState, setEmployeesState] = useState("loading");

  const { dialog, setDialog, detailError, detailLoading } = useRecordDialog("/api/projects", "project");
  const [isSaving, setIsSaving] = useState(false);
  const [formError, setFormError] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});
  const [actionMessage, setActionMessage] = useState("");
  const [cascade, setCascade] = useState(false);

  // Employee directory for the manager/team selectors.
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

  // Debounced project list fetch.
  useEffect(() => {
    let cancelled = false;

    const timer = setTimeout(() => {
      listProjects(session.token, {
        search,
        status: statusFilter,
        priority: priorityFilter,
        page,
        size: PAGE_SIZE,
      })
        .then((data) => {
          if (cancelled) return;
          setProjects(data.items);
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

  function handleSearchChange(event) {
    cancelRefresh();
    setSearch(event.target.value);
    setPage(1);
    setListState("loading");
    setListError("");
  }

  function handleStatusChange(event) {
    cancelRefresh();
    setStatusFilter(event.target.value);
    setPage(1);
    setListState("loading");
  }

  function handlePriorityChange(event) {
    cancelRefresh();
    setPriorityFilter(event.target.value);
    setPage(1);
    setListState("loading");
  }

  function openCreate() {
    setFormError("");
    setFieldErrors({});
    setDialog({ mode: "form", project: null });
  }

  function openEdit(project) {
    setFormError("");
    setFieldErrors({});
    setDialog({ mode: "form", project });
  }

  function openDetail(project) {
    setDialog({ mode: "detail", project });
  }

  function openDelete(project) {
    setFormError("");
    setCascade(false);
    setDialog({ mode: "delete", project });
  }

  function closeDialog() {
    if (isSaving) return;
    setDialog(null);
    setFormError("");
    setFieldErrors({});
  }

  function handleProjectChanged(updated) {
    setProjects((previous) =>
      previous.map((item) => (item.id === updated.id ? updated : item))
    );
  }

  async function handleSubmit(form) {
    const editing = dialog && dialog.project;
    setIsSaving(true);
    setFormError("");
    setFieldErrors({});

    try {
      if (editing) {
        const updated = await updateProject(
          session.token,
          editing.id,
          projectFormToPayload(form)
        );
        handleProjectChanged(updated);
        setActionMessage(`Updated ${form.name.trim()}.`);
      } else {
        await createProject(session.token, projectFormToPayload(form));
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

  async function handleDelete(project) {
    setIsSaving(true);
    setFormError("");

    try {
      await deleteProject(session.token, project.id, { cascade });
      setActionMessage(`Deleted ${project.name}.`);
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

  const hasProjects = projects.length > 0;

  return (
    <div className="dash-page">
      <section className="dash-hero">
        <div>
          <span className="dash-eyebrow">Projects</span>
          <h2>Projects</h2>
          <p>Plan delivery, assign teams and track progress.</p>
        </div>

        <button type="button" className="dash-button" onClick={openCreate}>
          New project
        </button>
      </section>

      <div className="dash-toolbar">
        <label className="dash-search">
          <span className="dash-visually-hidden">Search projects</span>
          <input
            type="search"
            value={search}
            onChange={handleSearchChange}
            placeholder="Search by code, name, client or description"
          />
        </label>

        <label className="dash-filter">
          <span className="dash-visually-hidden">Filter by status</span>
          <select value={statusFilter} onChange={handleStatusChange}>
            <option value="">All statuses</option>
            {PROJECT_STATUSES.map((status) => (
              <option key={status} value={status}>
                {formatStatus(status)}
              </option>
            ))}
          </select>
        </label>

        <label className="dash-filter">
          <span className="dash-visually-hidden">Filter by priority</span>
          <select value={priorityFilter} onChange={handlePriorityChange}>
            <option value="">All priorities</option>
            {PROJECT_PRIORITIES.map((priority) => (
              <option key={priority} value={priority}>
                {priority}
              </option>
            ))}
          </select>
        </label>

        <button
          type="button"
          className="dash-icon-button"
          onClick={reload}
          aria-label={refreshing ? "Refreshing projects" : "Refresh projects"}
          aria-busy={refreshing}
          title={refreshing ? "Refreshing projects" : "Refresh"}
          disabled={refreshing || listState === "loading"}
        >
          <IconRefresh />
        </button>

        <span className="dash-toolbar-count">
          {listState === "ready" && `${meta.total} project(s)`}
        </span>
      </div>

      <BackgroundRefreshStatus
        refreshing={refreshing}
        error={listState === "ready" ? listError : ""}
        label="projects"
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
          The employee directory could not be loaded, so manager and team
          selectors may be empty.
        </p>
      )}

      {listState === "loading" && (
        <StatusPanel
          variant="loading"
          title="Loading projects…"
          description="Fetching records from the backend."
        />
      )}

      {listState === "error" && (
        <StatusPanel
          variant="error"
          title="Could not load projects"
          description={listError}
          actionLabel="Retry"
          onAction={reload}
        />
      )}

      {listState === "ready" && !hasProjects && (
        <StatusPanel
          variant="empty"
          title={search || statusFilter || priorityFilter
            ? "No matching projects"
            : "No projects yet"}
          description={
            search || statusFilter || priorityFilter
              ? "Try different filters, or clear them to see everything."
              : "Create the first project to get started."
          }
        />
      )}

      {listState === "ready" && hasProjects && (
        <>
          <div className="dash-table-wrap">
            <table className="dash-table">
              <thead>
                <tr>
                  <th>Project</th>
                  <th>Client</th>
                  <th>Manager</th>
                  <th>Status</th>
                  <th>Priority</th>
                  <th>Due</th>
                  <th className="dash-table-actions-head">Actions</th>
                </tr>
              </thead>
              <tbody>
                {projects.map((project) => (
                  <tr key={project.id}>
                    <td>
                      <button
                        type="button"
                        className="dash-link-button strong"
                        onClick={() => openDetail(project)}
                      >
                        {project.name}
                      </button>
                      <em className="dash-cell-sub">{project.projectCode}</em>
                    </td>
                    <td>{project.clientName || "—"}</td>
                    <td>
                      {project.manager ? project.manager.fullName : "—"}
                    </td>
                    <td>
                      <span
                        className={`dash-status-pill status-${statusTone(project.status)}`}
                      >
                        {formatStatus(project.status)}
                      </span>
                    </td>
                    <td>
                      <span
                        className={`dash-priority-pill priority-${priorityTone(project.priority)}`}
                      >
                        {project.priority}
                      </span>
                    </td>
                    <td>{formatDate(project.dueDate)}</td>
                    <td>
                      <div className="dash-row-actions">
                        <button
                          type="button"
                          className="dash-link-button"
                          onClick={() => openDetail(project)}
                        >
                          View
                        </button>
                        <button
                          type="button"
                          className="dash-link-button"
                          onClick={() => openEdit(project)}
                        >
                          Edit
                        </button>
                        {isAdmin && (
                          <button
                            type="button"
                            className="dash-link-button danger"
                            onClick={() => openDelete(project)}
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
                  setPage((current) =>
                    Math.min(meta.totalPages, current + 1)
                  )
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
        <ProjectFormDialog
          project={dialog.project}
          employees={employees}
          isSaving={isSaving}
          error={formError}
          fieldErrors={fieldErrors}
          onClose={closeDialog}
          onSubmit={handleSubmit}
        />
      )}

      {dialog && dialog.mode === "detail" && (
        <ProjectDetailDialog
          key={dialog.project.id}
          project={dialog.project}
          token={session.token}
          isAdmin={isAdmin}
          onClose={closeDialog}
          onEdit={(project) => openEdit(project)}
          onDelete={(project) => openDelete(project)}
          onChanged={handleProjectChanged}
        />
      )}

      {dialog && dialog.mode === "delete" && (
        <div className="dash-modal-backdrop" role="presentation">
          <div
            className="dash-modal narrow"
            role="dialog"
            aria-modal="true"
            aria-label="Confirm delete project"
          >
            <div className="dash-modal-head">
              <div>
                <span className="dash-eyebrow">Confirm</span>
                <h3>Delete project</h3>
              </div>
            </div>

            <p className="dash-modal-text">
              Delete <strong>{dialog.project.name}</strong> (
              {dialog.project.projectCode})? This action cannot be undone and
              is restricted to administrators.
            </p>

            <label className="dash-checkbox-inline">
              <input
                type="checkbox"
                checked={cascade}
                onChange={(event) => setCascade(event.target.checked)}
                disabled={isSaving}
              />
              <span>Also delete this project&apos;s tasks</span>
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
                onClick={() => handleDelete(dialog.project)}
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
