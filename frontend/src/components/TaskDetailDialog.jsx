import { useEffect, useState } from "react";
import {
  createSubtask,
  deleteSubtask,
  updateSubtask,
} from "../api";
import { formatDate, formatStatus, priorityTone, statusTone } from "../format";
import { IconClose } from "./Icons";

function ProgressBar({ value }) {
  const clamped = Math.max(0, Math.min(100, value || 0));
  return (
    <div className="dash-progress-block">
      <div className="dash-progress-head">
        <span>Progress</span>
        <strong>{clamped}%</strong>
      </div>
      <div
        className="dash-progress"
        role="progressbar"
        aria-valuenow={clamped}
        aria-valuemin={0}
        aria-valuemax={100}
      >
        <span style={{ width: `${clamped}%` }} />
      </div>
    </div>
  );
}

/**
 * Task detail view with full subtask management: create, edit, complete and
 * delete. Every subtask action goes through the dedicated nested endpoints and
 * the returned task replaces the local copy so the UI always reflects the
 * server state.
 */
export default function TaskDetailDialog({
  task,
  token,
  isAdmin,
  onClose,
  onEdit,
  onDelete,
  onChanged,
}) {
  const [localTask, setLocalTask] = useState(task);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [editForm, setEditForm] = useState({ title: "", dueDate: "" });
  const [newSubtask, setNewSubtask] = useState({ title: "", dueDate: "" });

  useEffect(() => {
    function onKeyDown(event) {
      if (event.key === "Escape") onClose();
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  function applyTask(updated) {
    setLocalTask(updated);
    onChanged(updated);
  }

  async function addSubtask(event) {
    event.preventDefault();
    if (!newSubtask.title.trim()) return;

    setBusy(true);
    setError("");
    try {
      const updated = await createSubtask(token, localTask.id, {
        title: newSubtask.title.trim(),
        dueDate: newSubtask.dueDate || null,
      });
      applyTask(updated);
      setNewSubtask({ title: "", dueDate: "" });
    } catch (err) {
      setError(err.message || "Could not create the subtask.");
    } finally {
      setBusy(false);
    }
  }

  async function toggleSubtask(subtask) {
    setBusy(true);
    setError("");
    try {
      const updated = await updateSubtask(token, localTask.id, subtask.id, {
        id: subtask.id,
        title: subtask.title,
        dueDate: subtask.dueDate || null,
        assigneeId: subtask.assigneeId || null,
        completed: !subtask.completed,
      });
      applyTask(updated);
    } catch (err) {
      setError(err.message || "Could not update the subtask.");
    } finally {
      setBusy(false);
    }
  }

  function startEdit(subtask) {
    setEditingId(subtask.id);
    setEditForm({
      title: subtask.title || "",
      dueDate: subtask.dueDate || "",
    });
  }

  async function saveEdit(subtask) {
    if (!editForm.title.trim()) return;

    setBusy(true);
    setError("");
    try {
      const updated = await updateSubtask(token, localTask.id, subtask.id, {
        id: subtask.id,
        title: editForm.title.trim(),
        dueDate: editForm.dueDate || null,
        assigneeId: subtask.assigneeId || null,
        completed: subtask.completed,
      });
      applyTask(updated);
      setEditingId(null);
    } catch (err) {
      setError(err.message || "Could not update the subtask.");
    } finally {
      setBusy(false);
    }
  }

  async function removeSubtask(subtask) {
    setBusy(true);
    setError("");
    try {
      const updated = await deleteSubtask(token, localTask.id, subtask.id);
      applyTask(updated);
    } catch (err) {
      setError(err.message || "Could not delete the subtask.");
    } finally {
      setBusy(false);
    }
  }

  const subtasks = localTask.subtasks || [];
  const assignee = localTask.assignee;

  return (
    <div className="dash-modal-backdrop" role="presentation">
      <div
        className="dash-modal wide"
        role="dialog"
        aria-modal="true"
        aria-label="Task detail"
      >
        <div className="dash-modal-head">
          <div>
            <span className="dash-eyebrow">{localTask.taskCode}</span>
            <h3>{localTask.title}</h3>
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
            <span className={`dash-status-pill status-${statusTone(localTask.status)}`}>
              {formatStatus(localTask.status)}
            </span>
            <span className={`dash-priority-pill priority-${priorityTone(localTask.priority)}`}>
              {localTask.priority}
            </span>
            {localTask.overdue && (
              <span className="dash-overdue-badge">Overdue</span>
            )}
          </div>

          {localTask.description && (
            <p className="dash-modal-text">{localTask.description}</p>
          )}

          <dl className="dash-detail-grid">
            <div>
              <dt>Project</dt>
              <dd>
                {localTask.projectCode
                  ? `${localTask.projectCode} — ${localTask.projectName}`
                  : "—"}
              </dd>
            </div>
            <div>
              <dt>Assignee</dt>
              <dd>{assignee ? assignee.fullName : "Unassigned"}</dd>
            </div>
            <div>
              <dt>Start date</dt>
              <dd>{formatDate(localTask.startDate)}</dd>
            </div>
            <div>
              <dt>Due date</dt>
              <dd>{formatDate(localTask.dueDate)}</dd>
            </div>
            <div>
              <dt>Estimated hours</dt>
              <dd>
                {localTask.estimatedHours === null ||
                localTask.estimatedHours === undefined
                  ? "—"
                  : localTask.estimatedHours}
              </dd>
            </div>
            <div>
              <dt>Actual hours</dt>
              <dd>
                {localTask.actualHours === null ||
                localTask.actualHours === undefined
                  ? "—"
                  : localTask.actualHours}
              </dd>
            </div>
            <div className="dash-detail-wide">
              <dt>Created by</dt>
              <dd>{localTask.createdBy || "—"}</dd>
            </div>
          </dl>

          <ProgressBar value={localTask.progress} />

          {error && (
            <p className="dash-form-error" role="alert">
              {error}
            </p>
          )}

          <section className="dash-section">
            <h4>
              Subtasks ({localTask.completedSubtaskCount}/
              {localTask.subtaskCount})
            </h4>

            {subtasks.length === 0 ? (
              <p className="dash-form-hint">No subtasks yet.</p>
            ) : (
              <ul className="dash-subtask-list">
                {subtasks.map((subtask) => (
                  <li key={subtask.id} className="dash-subtask-item">
                    {editingId === subtask.id ? (
                      <div className="dash-subtask-edit">
                        <input
                          value={editForm.title}
                          onChange={(event) =>
                            setEditForm((previous) => ({
                              ...previous,
                              title: event.target.value,
                            }))
                          }
                          maxLength={160}
                          disabled={busy}
                          aria-label="Subtask title"
                        />
                        <input
                          type="date"
                          value={editForm.dueDate}
                          onChange={(event) =>
                            setEditForm((previous) => ({
                              ...previous,
                              dueDate: event.target.value,
                            }))
                          }
                          disabled={busy}
                          aria-label="Subtask due date"
                        />
                        <button
                          type="button"
                          className="dash-link-button"
                          onClick={() => saveEdit(subtask)}
                          disabled={busy}
                        >
                          Save
                        </button>
                        <button
                          type="button"
                          className="dash-link-button"
                          onClick={() => setEditingId(null)}
                          disabled={busy}
                        >
                          Cancel
                        </button>
                      </div>
                    ) : (
                      <>
                        <label className="dash-checkbox-inline">
                          <input
                            type="checkbox"
                            checked={Boolean(subtask.completed)}
                            onChange={() => toggleSubtask(subtask)}
                            disabled={busy}
                          />
                          <span className={subtask.completed ? "is-done" : ""}>
                            {subtask.title}
                          </span>
                        </label>
                        <div className="dash-subtask-actions">
                          <span className="dash-subtask-due">
                            {formatDate(subtask.dueDate)}
                          </span>
                          <button
                            type="button"
                            className="dash-link-button"
                            onClick={() => startEdit(subtask)}
                            disabled={busy}
                          >
                            Edit
                          </button>
                          {isAdmin && (
                            <button
                              type="button"
                              className="dash-link-button danger"
                              onClick={() => removeSubtask(subtask)}
                              disabled={busy}
                            >
                              Delete
                            </button>
                          )}
                        </div>
                      </>
                    )}
                  </li>
                ))}
              </ul>
            )}

            <form className="dash-subtask-add" onSubmit={addSubtask}>
              <label className="dash-field">
                <span className="dash-visually-hidden">New subtask title</span>
                <input
                  value={newSubtask.title}
                  onChange={(event) =>
                    setNewSubtask((previous) => ({
                      ...previous,
                      title: event.target.value,
                    }))
                  }
                  placeholder="Add a subtask…"
                  maxLength={160}
                  disabled={busy}
                />
              </label>
              <label className="dash-field">
                <span className="dash-visually-hidden">New subtask due date</span>
                <input
                  type="date"
                  value={newSubtask.dueDate}
                  onChange={(event) =>
                    setNewSubtask((previous) => ({
                      ...previous,
                      dueDate: event.target.value,
                    }))
                  }
                  disabled={busy}
                />
              </label>
              <button
                type="submit"
                className="dash-button ghost small"
                disabled={busy || !newSubtask.title.trim()}
              >
                Add
              </button>
            </form>
          </section>
        </div>

        <div className="dash-form-actions">
          {isAdmin && (
            <button
              type="button"
              className="dash-button danger"
              onClick={() => onDelete(localTask)}
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
            onClick={() => onEdit(localTask)}
            disabled={busy}
          >
            Edit task
          </button>
        </div>
      </div>
    </div>
  );
}
