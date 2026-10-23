import { useEffect, useState } from "react";
import { getProjectSummary, listProjectTasks, updateProject } from "../api";
import { projectFormToPayload, projectToForm } from "../projectForm";
import { formatDate, formatStatus, priorityTone, statusTone } from "../format";
import { IconClose } from "./Icons";
import StatusPanel from "./StatusPanel";

function ProgressBar({ value, label }) {
  const clamped = Math.max(0, Math.min(100, value || 0));
  return (
    <div className="dash-progress-block">
      <div className="dash-progress-head">
        <span>{label}</span>
        <strong>{clamped}%</strong>
      </div>
      <div className="dash-progress" role="progressbar" aria-valuenow={clamped} aria-valuemin={0} aria-valuemax={100}>
        <span style={{ width: `${clamped}%` }} />
      </div>
    </div>
  );
}

/**
 * Read-mostly project detail view.
 *
 * Loads the live progress summary and the project's tasks from the backend,
 * and lets an authenticated user tick milestones off (persisted through the
 * normal project update endpoint).
 */
export default function ProjectDetailDialog({
  project,
  token,
  isAdmin,
  onClose,
  onEdit,
  onDelete,
  onChanged,
}) {
  const [localProject, setLocalProject] = useState(project);
  const [summary, setSummary] = useState(null);
  const [tasks, setTasks] = useState([]);
  const [state, setState] = useState("loading"); // loading|ready|error
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    function onKeyDown(event) {
      if (event.key === "Escape") onClose();
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  useEffect(() => {
    let cancelled = false;

    Promise.all([
      getProjectSummary(token, localProject.id),
      listProjectTasks(token, localProject.id),
    ])
      .then(([summaryData, taskData]) => {
        if (cancelled) return;
        setSummary(summaryData);
        setTasks(taskData);
        setState("ready");
      })
      .catch((err) => {
        if (cancelled) return;
        setError(err.message || "Could not load the project detail.");
        setState("error");
      });

    return () => {
      cancelled = true;
    };
  }, [token, localProject.id, reloadKey]);

  function refresh() {
    setState("loading");
    setError("");
    setReloadKey((key) => key + 1);
  }

  async function toggleMilestone(milestone) {
    setBusy(true);
    setError("");

    try {
      const milestones = (localProject.milestones || []).map((item) =>
        item.id === milestone.id
          ? { ...item, completed: !item.completed }
          : item
      );
      const payload = projectFormToPayload(
        projectToForm({ ...localProject, milestones })
      );
      const updated = await updateProject(token, localProject.id, payload);
      setLocalProject(updated);
      onChanged(updated);
      setReloadKey((key) => key + 1);
    } catch (err) {
      setError(err.message || "Could not update the milestone.");
    } finally {
      setBusy(false);
    }
  }

  const manager = localProject.manager;
  const teamMembers = localProject.teamMembers || [];
  const milestones = localProject.milestones || [];

  return (
    <div className="dash-modal-backdrop" role="presentation">
      <div
        className="dash-modal wide"
        role="dialog"
        aria-modal="true"
        aria-label="Project detail"
      >
        <div className="dash-modal-head">
          <div>
            <span className="dash-eyebrow">{localProject.projectCode}</span>
            <h3>{localProject.name}</h3>
          </div>
          <button
            type="button"
            className="dash-icon-button"
            onClick={onClose}
            aria-label="Close"
          >
            <IconClose />
          </button>
        </div>

        <div className="dash-detail-body">
          <div className="dash-detail-tags">
            <span className={`dash-status-pill status-${statusTone(localProject.status)}`}>
              {formatStatus(localProject.status)}
            </span>
            <span className={`dash-priority-pill priority-${priorityTone(localProject.priority)}`}>
              {localProject.priority}
            </span>
          </div>

          {localProject.description && (
            <p className="dash-modal-text">{localProject.description}</p>
          )}

          <dl className="dash-detail-grid">
            <div>
              <dt>Client</dt>
              <dd>{localProject.clientName || "—"}</dd>
            </div>
            <div>
              <dt>Project manager</dt>
              <dd>{manager ? manager.fullName : "—"}</dd>
            </div>
            <div>
              <dt>Start date</dt>
              <dd>{formatDate(localProject.startDate)}</dd>
            </div>
            <div>
              <dt>Due date</dt>
              <dd>{formatDate(localProject.dueDate)}</dd>
            </div>
            <div className="dash-detail-wide">
              <dt>Team</dt>
              <dd>
                {teamMembers.length === 0
                  ? "No team members"
                  : teamMembers.map((member) => member.fullName).join(", ")}
              </dd>
            </div>
          </dl>

          {error && (
            <p className="dash-form-error" role="alert">
              {error}
            </p>
          )}

          {state === "loading" && (
            <StatusPanel
              variant="loading"
              title="Loading progress…"
              description="Fetching the project summary and tasks."
            />
          )}

          {state === "error" && (
            <StatusPanel
              variant="error"
              title="Could not load the summary"
              description={error}
              actionLabel="Retry"
              onAction={refresh}
            />
          )}

          {state === "ready" && summary && (
            <>
              <section className="dash-section">
                <h4>Progress</h4>
                <ProgressBar value={summary.progressPercent} label="Task progress" />
                <ProgressBar
                  value={summary.milestoneProgressPercent}
                  label="Milestones"
                />
                <div className="dash-metric-row">
                  <span>
                    <strong>{summary.totalTasks}</strong> tasks
                  </span>
                  <span>
                    <strong>{summary.completedTasks}</strong> completed
                  </span>
                  <span>
                    <strong>{summary.inProgressTasks}</strong> in progress
                  </span>
                  <span className={summary.overdueTasks > 0 ? "is-overdue" : ""}>
                    <strong>{summary.overdueTasks}</strong> overdue
                  </span>
                </div>
              </section>

              <section className="dash-section">
                <h4>Milestones</h4>
                {milestones.length === 0 ? (
                  <p className="dash-form-hint">
                    No milestones yet. Add them from the edit form.
                  </p>
                ) : (
                  <ul className="dash-milestone-list plain">
                    {milestones.map((milestone) => (
                      <li key={milestone.id} className="dash-milestone-item">
                        <label className="dash-checkbox-inline">
                          <input
                            type="checkbox"
                            checked={Boolean(milestone.completed)}
                            onChange={() => toggleMilestone(milestone)}
                            disabled={busy}
                          />
                          <span
                            className={milestone.completed ? "is-done" : ""}
                          >
                            {milestone.title}
                          </span>
                        </label>
                        <span className="dash-milestone-due">
                          {formatDate(milestone.dueDate)}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </section>

              <section className="dash-section">
                <h4>Tasks</h4>
                {tasks.length === 0 ? (
                  <p className="dash-form-hint">
                    No tasks have been created for this project yet.
                  </p>
                ) : (
                  <ul className="dash-mini-list">
                    {tasks.map((task) => (
                      <li key={task.id}>
                        <div>
                          <strong>{task.title}</strong>
                          <em>{task.taskCode}</em>
                        </div>
                        <div className="dash-mini-meta">
                          <span
                            className={`dash-status-pill status-${statusTone(task.status)}`}
                          >
                            {formatStatus(task.status)}
                          </span>
                          <span>{task.progress}%</span>
                          {task.overdue && (
                            <span className="dash-overdue-badge">Overdue</span>
                          )}
                        </div>
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            </>
          )}
        </div>

        <div className="dash-form-actions">
          {isAdmin && (
            <button
              type="button"
              className="dash-button danger"
              onClick={() => onDelete(localProject)}
              disabled={busy}
            >
              Delete
            </button>
          )}
          <button
            type="button"
            className="dash-button ghost"
            onClick={onClose}
            disabled={busy}
          >
            Close
          </button>
          <button
            type="button"
            className="dash-button"
            onClick={() => onEdit(localProject)}
            disabled={busy}
          >
            Edit project
          </button>
        </div>
      </div>
    </div>
  );
}
