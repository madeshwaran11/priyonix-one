import { useEffect, useState } from "react";
import {
  PROJECT_PRIORITIES,
  PROJECT_STATUSES,
  emptyProjectForm,
  milestoneToFormRow,
  projectToForm,
} from "../projectForm";
import { IconClose } from "./Icons";

function employeeLabel(employee) {
  return `${employee.fullName} (${employee.employeeId})`;
}

/**
 * Modal form used for both creating and editing a project.
 *
 * The manager and team selectors are populated from real employee records;
 * no free-text employee names are ever submitted.
 */
export default function ProjectFormDialog({
  project,
  employees,
  isSaving,
  error,
  fieldErrors,
  onClose,
  onSubmit,
}) {
  const isEdit = Boolean(project);
  const [form, setForm] = useState(() =>
    isEdit ? projectToForm(project) : emptyProjectForm()
  );

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

  function toggleTeamMember(id) {
    setForm((previous) => {
      const members = new Set(previous.teamMemberIds);
      if (members.has(id)) {
        members.delete(id);
      } else {
        members.add(id);
      }
      return { ...previous, teamMemberIds: [...members] };
    });
  }

  function updateMilestone(index, name, value) {
    setForm((previous) => {
      const milestones = previous.milestones.map((milestone, position) =>
        position === index ? { ...milestone, [name]: value } : milestone
      );
      return { ...previous, milestones };
    });
  }

  function addMilestone() {
    setForm((previous) => ({
      ...previous,
      milestones: [...previous.milestones, milestoneToFormRow()],
    }));
  }

  function removeMilestone(index) {
    setForm((previous) => ({
      ...previous,
      milestones: previous.milestones.filter(
        (_, position) => position !== index
      ),
    }));
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
        aria-label={isEdit ? "Edit project" : "New project"}
      >
        <div className="dash-modal-head">
          <div>
            <span className="dash-eyebrow">Projects</span>
            <h3>{isEdit ? "Edit project" : "New project"}</h3>
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
              <span>Project code *</span>
              <input
                value={form.projectCode}
                onChange={(event) =>
                  setField("projectCode", event.target.value)
                }
                placeholder="PRJ-001"
                maxLength={40}
                required
                disabled={isSaving}
              />
              {errorFor("projectCode") && (
                <em className="dash-field-error">{errorFor("projectCode")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Project name *</span>
              <input
                value={form.name}
                onChange={(event) => setField("name", event.target.value)}
                placeholder="Website Redesign"
                maxLength={160}
                required
                disabled={isSaving}
              />
              {errorFor("name") && (
                <em className="dash-field-error">{errorFor("name")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Client</span>
              <input
                value={form.clientName}
                onChange={(event) => setField("clientName", event.target.value)}
                placeholder="Northwind Traders"
                maxLength={160}
                disabled={isSaving}
              />
              {errorFor("clientName") && (
                <em className="dash-field-error">{errorFor("clientName")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Project manager *</span>
              <select
                value={form.managerId}
                onChange={(event) =>
                  setField("managerId", event.target.value)
                }
                required
                disabled={isSaving}
              >
                <option value="">Select an employee…</option>
                {employees.map((employee) => (
                  <option key={employee.id} value={employee.id}>
                    {employeeLabel(employee)}
                  </option>
                ))}
              </select>
              {errorFor("managerId") && (
                <em className="dash-field-error">{errorFor("managerId")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Start date *</span>
              <input
                type="date"
                value={form.startDate}
                onChange={(event) =>
                  setField("startDate", event.target.value)
                }
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
                {PROJECT_PRIORITIES.map((priority) => (
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
                {PROJECT_STATUSES.map((status) => (
                  <option key={status} value={status}>
                    {status.replace("_", " ")}
                  </option>
                ))}
              </select>
            </label>

            <label className="dash-field dash-field-wide">
              <span>Description</span>
              <textarea
                value={form.description}
                onChange={(event) =>
                  setField("description", event.target.value)
                }
                placeholder="What is this project about?"
                rows={3}
                maxLength={2000}
                disabled={isSaving}
              />
              {errorFor("description") && (
                <em className="dash-field-error">{errorFor("description")}</em>
              )}
            </label>
          </div>

          <div className="dash-form-block">
            <h4>Team members</h4>
            <p className="dash-form-hint">
              Select employees from the directory. The manager is stored as a
              separate reference and can also be on the team.
            </p>
            {employees.length === 0 ? (
              <p className="dash-form-hint">
                No employees available yet. Add employees first.
              </p>
            ) : (
              <div className="dash-checkbox-list">
                {employees.map((employee) => (
                  <label key={employee.id} className="dash-checkbox-item">
                    <input
                      type="checkbox"
                      checked={form.teamMemberIds.includes(employee.id)}
                      onChange={() => toggleTeamMember(employee.id)}
                      disabled={isSaving}
                    />
                    <span>{employeeLabel(employee)}</span>
                  </label>
                ))}
              </div>
            )}
          </div>

          <div className="dash-form-block">
            <h4>Milestones</h4>
            {form.milestones.length === 0 && (
              <p className="dash-form-hint">No milestones yet.</p>
            )}

            <ul className="dash-milestone-list">
              {form.milestones.map((milestone, index) => (
                <li
                  key={milestone.id || `new-${index}`}
                  className="dash-milestone-row"
                >
                  <input
                    value={milestone.title}
                    onChange={(event) =>
                      updateMilestone(index, "title", event.target.value)
                    }
                    placeholder="Milestone title"
                    maxLength={160}
                    disabled={isSaving}
                    aria-label={`Milestone ${index + 1} title`}
                  />
                  <input
                    type="date"
                    value={milestone.dueDate}
                    onChange={(event) =>
                      updateMilestone(index, "dueDate", event.target.value)
                    }
                    disabled={isSaving}
                    aria-label={`Milestone ${index + 1} due date`}
                  />
                  <label className="dash-checkbox-inline">
                    <input
                      type="checkbox"
                      checked={milestone.completed}
                      onChange={(event) =>
                        updateMilestone(index, "completed", event.target.checked)
                      }
                      disabled={isSaving}
                    />
                    <span>Done</span>
                  </label>
                  <button
                    type="button"
                    className="dash-link-button danger"
                    onClick={() => removeMilestone(index)}
                    disabled={isSaving}
                  >
                    Remove
                  </button>
                </li>
              ))}
            </ul>

            <button
              type="button"
              className="dash-button ghost small"
              onClick={addMilestone}
              disabled={isSaving}
            >
              Add milestone
            </button>
          </div>

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
                  : "Create project"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
