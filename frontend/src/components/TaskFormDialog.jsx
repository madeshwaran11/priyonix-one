import { useEffect, useState } from "react";
import {
  TASK_PRIORITIES,
  TASK_STATUSES,
  emptyTaskForm,
  taskToForm,
} from "../taskForm";
import { IconClose } from "./Icons";

function employeeLabel(employee) {
  return `${employee.fullName} (${employee.employeeId})`;
}

/**
 * Modal form used for both creating and editing a task.
 *
 * The project and assignee selectors are populated from real records. Server
 * rules mean a COMPLETED task is always stored at 100% and a TODO task at 0%.
 */
export default function TaskFormDialog({
  task,
  projects,
  employees,
  defaultProjectId,
  isSaving,
  error,
  fieldErrors,
  onClose,
  onSubmit,
}) {
  const isEdit = Boolean(task);
  const [form, setForm] = useState(() => {
    if (isEdit) return taskToForm(task);
    return { ...emptyTaskForm(), projectId: defaultProjectId || "" };
  });

  useEffect(() => {
    function onKeyDown(event) {
      if (event.key === "Escape") onClose();
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  function setField(name, value) {
    setForm((previous) => ({ ...previous, [name]: value }));
  }

  function handleSubmit(event) {
    event.preventDefault();
    onSubmit(form);
  }

  function errorFor(name) {
    return fieldErrors && fieldErrors[name];
  }

  return (
    <div className="dash-modal-backdrop" role="presentation">
      <div
        className="dash-modal wide"
        role="dialog"
        aria-modal="true"
        aria-label={isEdit ? "Edit task" : "New task"}
      >
        <div className="dash-modal-head">
          <div>
            <span className="dash-eyebrow">Tasks</span>
            <h3>{isEdit ? "Edit task" : "New task"}</h3>
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

        <form className="dash-form" onSubmit={handleSubmit}>
          <div className="dash-form-grid">
            <label className="dash-field">
              <span>Task code</span>
              <input
                value={form.taskCode}
                onChange={(event) => setField("taskCode", event.target.value)}
                placeholder="Auto-generated if blank"
                maxLength={40}
                disabled={isSaving}
              />
              {errorFor("taskCode") && (
                <em className="dash-field-error">{errorFor("taskCode")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Title *</span>
              <input
                value={form.title}
                onChange={(event) => setField("title", event.target.value)}
                placeholder="Build login page"
                maxLength={200}
                required
                disabled={isSaving}
              />
              {errorFor("title") && (
                <em className="dash-field-error">{errorFor("title")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Project *</span>
              <select
                value={form.projectId}
                onChange={(event) =>
                  setField("projectId", event.target.value)
                }
                required
                disabled={isSaving}
              >
                <option value="">Select a project…</option>
                {projects.map((project) => (
                  <option key={project.id} value={project.id}>
                    {project.projectCode} — {project.name}
                  </option>
                ))}
              </select>
              {errorFor("projectId") && (
                <em className="dash-field-error">{errorFor("projectId")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Assignee</span>
              <select
                value={form.assigneeId}
                onChange={(event) =>
                  setField("assigneeId", event.target.value)
                }
                disabled={isSaving}
              >
                <option value="">Unassigned</option>
                {employees.map((employee) => (
                  <option key={employee.id} value={employee.id}>
                    {employeeLabel(employee)}
                  </option>
                ))}
              </select>
              {errorFor("assigneeId") && (
                <em className="dash-field-error">{errorFor("assigneeId")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Start date *</span>
              <input
                type="date"
                value={form.startDate}
                onChange={(event) => setField("startDate", event.target.value)}
                required
                disabled={isSaving}
              />
              {errorFor("startDate") && (
                <em className="dash-field-error">{errorFor("startDate")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Due date *</span>
              <input
                type="date"
                value={form.dueDate}
                onChange={(event) => setField("dueDate", event.target.value)}
                required
                disabled={isSaving}
              />
              {errorFor("dueDate") && (
                <em className="dash-field-error">{errorFor("dueDate")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Priority</span>
              <select
                value={form.priority}
                onChange={(event) => setField("priority", event.target.value)}
                disabled={isSaving}
              >
                {TASK_PRIORITIES.map((priority) => (
                  <option key={priority} value={priority}>
                    {priority}
                  </option>
                ))}
              </select>
            </label>

            <label className="dash-field">
              <span>Status</span>
              <select
                value={form.status}
                onChange={(event) => setField("status", event.target.value)}
                disabled={isSaving}
              >
                {TASK_STATUSES.map((status) => (
                  <option key={status} value={status}>
                    {status.replace("_", " ")}
                  </option>
                ))}
              </select>
            </label>

            <label className="dash-field">
              <span>Progress ({form.progress}%)</span>
              <input
                type="range"
                min="0"
                max="100"
                step="5"
                value={form.progress}
                onChange={(event) =>
                  setField("progress", Number(event.target.value))
                }
                disabled={isSaving}
              />
              {errorFor("progress") && (
                <em className="dash-field-error">{errorFor("progress")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Estimated hours</span>
              <input
                type="number"
                min="0"
                step="0.5"
                value={form.estimatedHours}
                onChange={(event) =>
                  setField("estimatedHours", event.target.value)
                }
                disabled={isSaving}
              />
              {errorFor("estimatedHours") && (
                <em className="dash-field-error">
                  {errorFor("estimatedHours")}
                </em>
              )}
            </label>

            <label className="dash-field">
              <span>Actual hours</span>
              <input
                type="number"
                min="0"
                step="0.5"
                value={form.actualHours}
                onChange={(event) =>
                  setField("actualHours", event.target.value)
                }
                disabled={isSaving}
              />
              {errorFor("actualHours") && (
                <em className="dash-field-error">
                  {errorFor("actualHours")}
                </em>
              )}
            </label>

            <label className="dash-field dash-field-wide">
              <span>Description</span>
              <textarea
                value={form.description}
                onChange={(event) =>
                  setField("description", event.target.value)
                }
                rows={3}
                maxLength={2000}
                disabled={isSaving}
              />
              {errorFor("description") && (
                <em className="dash-field-error">{errorFor("description")}</em>
              )}
            </label>
          </div>

          <p className="dash-form-hint">
            Marking a task COMPLETED stores it at 100%; setting progress to
            100% marks it COMPLETED.
          </p>

          {error && (
            <p className="dash-form-error" role="alert">
              {error}
            </p>
          )}

          <div className="dash-form-actions">
            <button
              type="button"
              className="dash-button ghost"
              onClick={onClose}
              disabled={isSaving}
            >
              Cancel
            </button>
            <button type="submit" className="dash-button" disabled={isSaving}>
              {isSaving
                ? "Saving…"
                : isEdit
                  ? "Save changes"
                  : "Create task"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
