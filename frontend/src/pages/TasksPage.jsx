import { useEffect, useState } from "react";
import {
  createTask,
  deleteTask,
  listEmployees,
  listProjects,
  listTasks,
  updateTask,
} from "../api";
import {
  TASK_PRIORITIES,
  TASK_STATUSES,
  taskFormToPayload,
} from "../taskForm";
import TaskFormDialog from "../components/TaskFormDialog";
import TaskDetailDialog from "../components/TaskDetailDialog";
import StatusPanel from "../components/StatusPanel";
import { IconRefresh } from "../components/Icons";
import { formatDate, formatStatus, priorityTone, statusTone } from "../format";
import { Notice } from "../components/WorkspaceUI";
import { useRecordDialog } from "../useRecordDialog";
import BackgroundRefreshStatus from "../components/BackgroundRefreshStatus";
import { useRetainedRefresh } from "../useRetainedRefresh";

const PAGE_SIZE = 10;

/**
 * Tasks page.
 *
 * Loads real tasks from GET /api/tasks with the stored JWT. Project and
 * assignee selectors are populated from live records, and overdue tasks are
 * flagged from the server-computed flag. No demo data is shown.
 */
export default function TasksPage({ session }) {
  const isAdmin = (session.role || "").toUpperCase() === "ADMIN";

  const [tasks, setTasks] = useState([]);
  const [listState, setListState] = useState("loading");
  const [listError, setListError] = useState("");
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [priorityFilter, setPriorityFilter] = useState("");
  const [projectFilter, setProjectFilter] = useState("");
  const [assigneeFilter, setAssigneeFilter] = useState("");
  const [overdueOnly, setOverdueOnly] = useState(false);
  const [page, setPage] = useState(1);
  const [meta, setMeta] = useState({ total: 0, totalPages: 0 });
  const [reloadKey, setReloadKey] = useState(0);
  const { refreshing, beginRefresh, settleRefresh, cancelRefresh } =
    useRetainedRefresh();

  const [projects, setProjects] = useState([]);
  const [employees, setEmployees] = useState([]);
  const [lookupsState, setLookupsState] = useState("loading");

  const { dialog, setDialog, detailError, detailLoading } = useRecordDialog("/api/tasks", "task");
  const [isSaving, setIsSaving] = useState(false);
  const [formError, setFormError] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});
  const [actionMessage, setActionMessage] = useState("");

  // Project + employee lookups for selectors and filters.
  useEffect(() => {
    let cancelled = false;

    Promise.all([
      listProjects(session.token, { page: 1, size: 100 }),
      listEmployees(session.token),
    ])
      .then(([projectData, employeeData]) => {
        if (cancelled) return;
        setProjects(projectData.items);
        setEmployees(employeeData);
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

  // Debounced task list fetch.
  useEffect(() => {
    let cancelled = false;

    const timer = setTimeout(() => {
      listTasks(session.token, {
        search,
        status: statusFilter,
        priority: priorityFilter,
        projectId: projectFilter,
        assigneeId: assigneeFilter,
        overdue: overdueOnly,
        page,
        size: PAGE_SIZE,
      })
        .then((data) => {
          if (cancelled) return;
          setTasks(data.items);
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
    projectFilter,
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
  }

  function handleSearchChange(event) {
    setSearch(event.target.value);
    resetToFirstPage();
  }

  function openCreate() {
    setFormError("");
    setFieldErrors({});
    setDialog({ mode: "form", task: null });
  }

  function openEdit(task) {
    setFormError("");
    setFieldErrors({});
    setDialog({ mode: "form", task });
  }

  function openDetail(task) {
    setDialog({ mode: "detail", task });
  }

  function openDelete(task) {
    setFormError("");
    setDialog({ mode: "delete", task });
  }

  function closeDialog() {
    if (isSaving) return;
    setDialog(null);
    setFormError("");
    setFieldErrors({});
  }

  function handleTaskChanged(updated) {
    setTasks((previous) =>
      previous.map((item) => (item.id === updated.id ? updated : item))
    );
  }

  async function handleSubmit(form) {
    const editing = dialog && dialog.task;
    setIsSaving(true);
    setFormError("");
    setFieldErrors({});

    try {
      if (editing) {
        const updated = await updateTask(
          session.token,
          editing.id,
          taskFormToPayload(form)
        );
        handleTaskChanged(updated);
        setActionMessage(`Updated ${form.title.trim()}.`);
      } else {
        await createTask(session.token, taskFormToPayload(form));
        setActionMessage(`Created ${form.title.trim()}.`);
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

  async function handleDelete(task) {
    setIsSaving(true);
    setFormError("");

    try {
      await deleteTask(session.token, task.id);
      setActionMessage(`Deleted ${task.title}.`);
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

  const hasTasks = tasks.length > 0;
  const hasFilters =
    search || statusFilter || priorityFilter || projectFilter ||
    assigneeFilter || overdueOnly;

  return (
    <div className="dash-page">
      <section className="dash-hero">
        <div>
          <span className="dash-eyebrow">Tasks</span>
          <h2>Tasks</h2>
          <p>Assign work, track progress and manage subtasks.</p>
        </div>

        <button type="button" className="dash-button" onClick={openCreate}>
          New task
        </button>
      </section>

      <div className="dash-toolbar">
        <label className="dash-search">
          <span className="dash-visually-hidden">Search tasks</span>
          <input
            type="search"
            value={search}
            onChange={handleSearchChange}
            placeholder="Search by title, code or description"
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
            {TASK_STATUSES.map((status) => (
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
            {TASK_PRIORITIES.map((priority) => (
              <option key={priority} value={priority}>
                {priority}
              </option>
            ))}
          </select>
        </label>

        <label className="dash-filter">
          <span className="dash-visually-hidden">Filter by project</span>
          <select
            value={projectFilter}
            onChange={(event) => {
              setProjectFilter(event.target.value);
              resetToFirstPage();
            }}
          >
            <option value="">All projects</option>
            {projects.map((project) => (
              <option key={project.id} value={project.id}>
                {project.projectCode}
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
          aria-label={refreshing ? "Refreshing tasks" : "Refresh tasks"}
          aria-busy={refreshing}
          title={refreshing ? "Refreshing tasks" : "Refresh"}
          disabled={refreshing || listState === "loading"}
        >
          <IconRefresh />
        </button>

        <span className="dash-toolbar-count">
          {listState === "ready" && `${meta.total} task(s)`}
        </span>
      </div>

      <BackgroundRefreshStatus
        refreshing={refreshing}
        error={listState === "ready" ? listError : ""}
        label="tasks"
      />

      <Notice error>{detailError}</Notice>
      {detailLoading && <p className="workspace-help" role="status">Loading requested record…</p>}
      {actionMessage && listState !== "error" && (
        <p className="dash-success" role="status">
          {actionMessage}
        </p>
      )}

      {lookupsState === "error" && (
        <p className="dash-form-hint">
          Project or employee lookups could not be loaded, so some selectors
          may be empty.
        </p>
      )}

      {listState === "loading" && (
        <StatusPanel
          variant="loading"
          title="Loading tasks…"
          description="Fetching records from the backend."
        />
      )}

      {listState === "error" && (
        <StatusPanel
          variant="error"
          title="Could not load tasks"
          description={listError}
          actionLabel="Retry"
          onAction={reload}
        />
      )}

      {listState === "ready" && !hasTasks && (
        <StatusPanel
          variant="empty"
          title={hasFilters ? "No matching tasks" : "No tasks yet"}
          description={
            hasFilters
              ? "Try different filters, or clear them to see everything."
              : "Create the first task to get started."
          }
        />
      )}

      {listState === "ready" && hasTasks && (
        <>
          <div className="dash-table-wrap">
            <table className="dash-table">
              <thead>
                <tr>
                  <th>Task</th>
                  <th>Project</th>
                  <th>Assignee</th>
                  <th>Status</th>
                  <th>Priority</th>
                  <th>Due</th>
                  <th>Progress</th>
                  <th className="dash-table-actions-head">Actions</th>
                </tr>
              </thead>
              <tbody>
                {tasks.map((task) => (
                  <tr
                    key={task.id}
                    className={task.overdue ? "is-overdue-row" : ""}
                  >
                    <td>
                      <button
                        type="button"
                        className="dash-link-button strong"
                        onClick={() => openDetail(task)}
                      >
                        {task.title}
                      </button>
                      <em className="dash-cell-sub">{task.taskCode}</em>
                    </td>
                    <td>{task.projectCode || "—"}</td>
                    <td>{task.assignee ? task.assignee.fullName : "Unassigned"}</td>
                    <td>
                      <span
                        className={`dash-status-pill status-${statusTone(task.status)}`}
                      >
                        {formatStatus(task.status)}
                      </span>
                    </td>
                    <td>
                      <span
                        className={`dash-priority-pill priority-${priorityTone(task.priority)}`}
                      >
                        {task.priority}
                      </span>
                    </td>
                    <td>
                      {formatDate(task.dueDate)}
                      {task.overdue && (
                        <span className="dash-overdue-badge">Overdue</span>
                      )}
                    </td>
                    <td>
                      <div className="dash-mini-progress">
                        <span style={{ width: `${task.progress}%` }} />
                      </div>
                      <em className="dash-cell-sub">{task.progress}%</em>
                    </td>
                    <td>
                      <div className="dash-row-actions">
                        <button
                          type="button"
                          className="dash-link-button"
                          onClick={() => openDetail(task)}
                        >
                          View
                        </button>
                        <button
                          type="button"
                          className="dash-link-button"
                          onClick={() => openEdit(task)}
                        >
                          Edit
                        </button>
                        {isAdmin && (
                          <button
                            type="button"
                            className="dash-link-button danger"
                            onClick={() => openDelete(task)}
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
        <TaskFormDialog
          task={dialog.task}
          projects={projects}
          employees={employees}
          defaultProjectId={projectFilter}
          isSaving={isSaving}
          error={formError}
          fieldErrors={fieldErrors}
          onClose={closeDialog}
          onSubmit={handleSubmit}
        />
      )}

      {dialog && dialog.mode === "detail" && (
        <TaskDetailDialog
          key={dialog.task.id}
          task={dialog.task}
          token={session.token}
          isAdmin={isAdmin}
          onClose={closeDialog}
          onEdit={(task) => openEdit(task)}
          onDelete={(task) => openDelete(task)}
          onChanged={handleTaskChanged}
        />
      )}

      {dialog && dialog.mode === "delete" && (
        <div className="dash-modal-backdrop" role="presentation">
          <div
            className="dash-modal narrow"
            role="dialog"
            aria-modal="true"
            aria-label="Confirm delete task"
          >
            <div className="dash-modal-head">
              <div>
                <span className="dash-eyebrow">Confirm</span>
                <h3>Delete task</h3>
              </div>
            </div>

            <p className="dash-modal-text">
              Delete <strong>{dialog.task.title}</strong> (
              {dialog.task.taskCode})? This action cannot be undone and is
              restricted to administrators.
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
                onClick={() => handleDelete(dialog.task)}
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
