import { useEffect, useState } from "react";
import { EMPLOYMENT_STATUSES, employeeToForm, emptyEmployeeForm } from "../employeeForm";
import { IconClose } from "./Icons";

/**
 * Modal form used for both creating and editing an employee.
 * Field labels, errors and the saved payload follow the backend contract.
 */
export default function EmployeeFormDialog({
  employee,
  isSaving,
  error,
  fieldErrors,
  onClose,
  onSubmit,
}) {
  const isEdit = Boolean(employee);
  const [form, setForm] = useState(() =>
    isEdit ? employeeToForm(employee) : emptyEmployeeForm()
  );

  // Close the dialog on Escape.
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
        className="dash-modal"
        role="dialog"
        aria-modal="true"
        aria-label={isEdit ? "Edit employee" : "Add employee"}
      >
        <div className="dash-modal-head">
          <div>
            <span className="dash-eyebrow">People &amp; HR</span>
            <h3>{isEdit ? "Edit employee" : "Add employee"}</h3>
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
              <span>Employee ID *</span>
              <input
                value={form.employeeId}
                onChange={(event) =>
                  setField("employeeId", event.target.value)
                }
                placeholder="EMP-001"
                maxLength={40}
                required
                disabled={isSaving}
              />
              {errorFor("employeeId") && (
                <em className="dash-field-error">{errorFor("employeeId")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Full name *</span>
              <input
                value={form.fullName}
                onChange={(event) => setField("fullName", event.target.value)}
                placeholder="Asha Menon"
                maxLength={120}
                required
                disabled={isSaving}
              />
              {errorFor("fullName") && (
                <em className="dash-field-error">{errorFor("fullName")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Work email *</span>
              <input
                type="email"
                value={form.workEmail}
                onChange={(event) => setField("workEmail", event.target.value)}
                placeholder="asha@company.com"
                maxLength={254}
                required
                disabled={isSaving}
              />
              {errorFor("workEmail") && (
                <em className="dash-field-error">{errorFor("workEmail")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Phone</span>
              <input
                value={form.phone}
                onChange={(event) => setField("phone", event.target.value)}
                placeholder="+91 98765 43210"
                maxLength={30}
                disabled={isSaving}
              />
              {errorFor("phone") && (
                <em className="dash-field-error">{errorFor("phone")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Department *</span>
              <input
                value={form.department}
                onChange={(event) => setField("department", event.target.value)}
                placeholder="Engineering"
                maxLength={80}
                required
                disabled={isSaving}
              />
              {errorFor("department") && (
                <em className="dash-field-error">
                  {errorFor("department")}
                </em>
              )}
            </label>

            <label className="dash-field">
              <span>Designation *</span>
              <input
                value={form.designation}
                onChange={(event) =>
                  setField("designation", event.target.value)
                }
                placeholder="Senior Developer"
                maxLength={80}
                required
                disabled={isSaving}
              />
              {errorFor("designation") && (
                <em className="dash-field-error">
                  {errorFor("designation")}
                </em>
              )}
            </label>

            <label className="dash-field">
              <span>Joining date *</span>
              <input
                type="date"
                value={form.joiningDate}
                onChange={(event) =>
                  setField("joiningDate", event.target.value)
                }
                required
                disabled={isSaving}
              />
              {errorFor("joiningDate") && (
                <em className="dash-field-error">
                  {errorFor("joiningDate")}
                </em>
              )}
            </label>

            <label className="dash-field">
              <span>Reporting manager</span>
              <input
                value={form.reportingManager}
                onChange={(event) =>
                  setField("reportingManager", event.target.value)
                }
                placeholder="Ravi Kumar"
                maxLength={120}
                disabled={isSaving}
              />
              {errorFor("reportingManager") && (
                <em className="dash-field-error">
                  {errorFor("reportingManager")}
                </em>
              )}
            </label>

            <label className="dash-field">
              <span>Employment status</span>
              <select
                value={form.employmentStatus}
                onChange={(event) =>
                  setField("employmentStatus", event.target.value)
                }
                disabled={isSaving}
              >
                {EMPLOYMENT_STATUSES.map((status) => (
                  <option key={status} value={status}>
                    {status.replace("_", " ")}
                  </option>
                ))}
              </select>
              {errorFor("employmentStatus") && (
                <em className="dash-field-error">
                  {errorFor("employmentStatus")}
                </em>
              )}
            </label>

            <label className="dash-field dash-field-wide">
              <span>Skills (comma separated)</span>
              <input
                value={form.skills}
                onChange={(event) => setField("skills", event.target.value)}
                placeholder="Java, Spring Boot, MongoDB"
                disabled={isSaving}
              />
              {errorFor("skills") && (
                <em className="dash-field-error">{errorFor("skills")}</em>
              )}
            </label>
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
                  : "Add employee"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
