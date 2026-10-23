import { useEffect, useState } from "react";
import {
  createEmployee,
  deleteEmployee,
  listEmployees,
  updateEmployee,
} from "../api";
import EmployeeFormDialog from "../components/EmployeeFormDialog";
import { formToPayload } from "../employeeForm";
import StatusPanel from "../components/StatusPanel";
import { IconRefresh } from "../components/Icons";
import { Modal, Notice } from "../components/WorkspaceUI";
import { useRecordDialog } from "../useRecordDialog";
import BackgroundRefreshStatus from "../components/BackgroundRefreshStatus";
import { useRetainedRefresh } from "../useRetainedRefresh";

function formatStatus(status) {
  return (status || "ACTIVE").replace("_", " ");
}

function formatDate(value) {
  if (!value) return "—";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleDateString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

function initials(name) {
  return (name || "?")
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join("");
}

/**
 * Employee & HR page.
 *
 * Loads real records from GET /api/employees with the stored JWT. No demo
 * or placeholder employees are ever shown: the page renders a loading,
 * error or empty state until the backend returns data.
 */
export default function EmployeesPage({ session }) {
  const isAdmin = (session.role || "").toUpperCase() === "ADMIN";

  const [employees, setEmployees] = useState([]);
  const [listState, setListState] = useState("loading"); // loading|ready|error
  const [listError, setListError] = useState("");
  const [search, setSearch] = useState("");
  const [reloadKey, setReloadKey] = useState(0);
  const { refreshing, beginRefresh, settleRefresh, cancelRefresh } =
    useRetainedRefresh();

  const { dialog, setDialog, detailError, detailLoading } = useRecordDialog("/api/employees", "employee");
  const [isSaving, setIsSaving] = useState(false);
  const [formError, setFormError] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});
  const [actionMessage, setActionMessage] = useState("");

  useEffect(() => {
    let cancelled = false;

    // Debounced so typing in the search box does not spam the API.
    const timer = setTimeout(() => {
      listEmployees(session.token, search)
        .then((data) => {
          if (cancelled) return;
          setEmployees(data);
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
  }, [session.token, search, reloadKey, settleRefresh]);

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
    setListState("loading");
    setListError("");
  }

  function openCreate() {
    setFormError("");
    setFieldErrors({});
    setDialog({ mode: "form", employee: null });
  }

  function openEdit(employee) {
    setFormError("");
    setFieldErrors({});
    setDialog({ mode: "form", employee });
  }

  function closeDialog() {
    if (isSaving) return;
    setDialog(null);
    setFormError("");
    setFieldErrors({});
  }

  async function handleSubmit(form) {
    const editing = dialog && dialog.employee;
    setIsSaving(true);
    setFormError("");
    setFieldErrors({});

    try {
      if (editing) {
        await updateEmployee(session.token, editing.id, formToPayload(form));
        setActionMessage(`Updated ${form.fullName.trim()}.`);
      } else {
        await createEmployee(session.token, formToPayload(form));
        setActionMessage(`Added ${form.fullName.trim()}.`);
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

  async function handleDelete(employee) {
    setIsSaving(true);
    setFormError("");

    try {
      await deleteEmployee(session.token, employee.id);
      setActionMessage(`Deleted ${employee.fullName}.`);
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

  const hasEmployees = employees.length > 0;

  return (
    <div className="dash-page">
      <section className="dash-hero">
        <div>
          <span className="dash-eyebrow">People &amp; HR</span>
          <h2>Employees</h2>
          <p>
            Manage employee records, departments and employment status.
          </p>
        </div>

        <button type="button" className="dash-button" onClick={openCreate}>
          Add employee
        </button>
      </section>

      <div className="dash-toolbar">
        <label className="dash-search">
          <span className="dash-visually-hidden">Search employees</span>
          <input
            type="search"
            value={search}
            onChange={handleSearchChange}
            placeholder="Search by name, ID, email, department or skill"
          />
        </label>

        <button
          type="button"
          className="dash-icon-button"
          onClick={reload}
          aria-label={refreshing ? "Refreshing employees" : "Refresh employees"}
          aria-busy={refreshing}
          title={refreshing ? "Refreshing employees" : "Refresh"}
          disabled={refreshing || listState === "loading"}
        >
          <IconRefresh />
        </button>

        <span className="dash-toolbar-count">
          {listState === "ready" && `${employees.length} record(s)`}
        </span>
      </div>

      <BackgroundRefreshStatus
        refreshing={refreshing}
        error={listState === "ready" ? listError : ""}
        label="employees"
      />

      <Notice error>{detailError}</Notice>
      {detailLoading && <p className="workspace-help" role="status">Loading requested record…</p>}
      {actionMessage && listState !== "error" && (
        <p className="dash-success" role="status">
          {actionMessage}
        </p>
      )}

      {listState === "loading" && (
        <StatusPanel
          variant="loading"
          title="Loading employees…"
          description="Fetching records from the backend."
        />
      )}

      {listState === "error" && (
        <StatusPanel
          variant="error"
          title="Could not load employees"
          description={listError}
          actionLabel="Retry"
          onAction={reload}
        />
      )}

      {listState === "ready" && !hasEmployees && (
        <StatusPanel
          variant="empty"
          title={search ? "No matching employees" : "No employees yet"}
          description={
            search
              ? "Try a different search term, or clear the search box."
              : "Add the first employee to start building your directory."
          }
        />
      )}

      {listState === "ready" && hasEmployees && (
        <div className="dash-table-wrap">
          <table className="dash-table">
            <thead>
              <tr>
                <th>Employee</th>
                <th>Work email</th>
                <th>Department</th>
                <th>Designation</th>
                <th>Joined</th>
                <th>Status</th>
                <th className="dash-table-actions-head">Actions</th>
              </tr>
            </thead>
            <tbody>
              {employees.map((employee) => (
                <tr key={employee.id || employee.employeeId}>
                  <td>
                    <div className="dash-employee-cell">
                      <span className="dash-avatar small" aria-hidden="true">
                        {initials(employee.fullName)}
                      </span>
                      <span>
                        <button type="button" className="dash-link-button" onClick={() => setDialog({ mode: "detail", employee })}>{employee.fullName}</button>
                        <em>{employee.employeeId}</em>
                      </span>
                    </div>
                  </td>
                  <td>{employee.workEmail}</td>
                  <td>{employee.department}</td>
                  <td>{employee.designation}</td>
                  <td>{formatDate(employee.joiningDate)}</td>
                  <td>
                    <span
                      className={`dash-status-pill status-${(
                        employee.employmentStatus || "ACTIVE"
                      ).toLowerCase()}`}
                    >
                      {formatStatus(employee.employmentStatus)}
                    </span>
                  </td>
                  <td>
                    <div className="dash-row-actions">
                      <button type="button" className="dash-link-button" onClick={() => setDialog({ mode: "detail", employee })}>View</button>
                      <button
                        type="button"
                        className="dash-link-button"
                        onClick={() => openEdit(employee)}
                      >
                        Edit
                      </button>
                      {isAdmin && (
                        <button
                          type="button"
                          className="dash-link-button danger"
                          onClick={() => {
                            setFormError("");
                            setDialog({ mode: "delete", employee });
                          }}
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
      )}

      {dialog?.mode === "detail" && (
        <Modal title={`Employee · ${dialog.employee.fullName}`} onClose={closeDialog}>
          <dl className="workspace-detail-grid">
            {[
              ["Employee ID", dialog.employee.employeeId],
              ["Work email", dialog.employee.workEmail],
              ["Phone", dialog.employee.phone],
              ["Department", dialog.employee.department],
              ["Designation", dialog.employee.designation],
              ["Joined", formatDate(dialog.employee.joiningDate)],
              ["Status", formatStatus(dialog.employee.employmentStatus)],
              ["Reporting manager", dialog.employee.reportingManager],
              ["Skills", dialog.employee.skills?.join(", ")],
            ].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value || "—"}</dd></div>)}
          </dl>
          <div className="dash-form-actions">
            <button type="button" className="dash-button secondary" onClick={closeDialog}>Close</button>
            <button type="button" className="dash-button" onClick={() => openEdit(dialog.employee)}>Edit employee</button>
          </div>
        </Modal>
      )}

      {dialog && dialog.mode === "form" && (
        <EmployeeFormDialog
          employee={dialog.employee}
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
            aria-label="Confirm delete"
          >
            <div className="dash-modal-head">
              <div>
                <span className="dash-eyebrow">Confirm</span>
                <h3>Delete employee</h3>
              </div>
            </div>

            <p className="dash-modal-text">
              Delete <strong>{dialog.employee.fullName}</strong> (
              {dialog.employee.employeeId})? This action cannot be undone and
              is restricted to administrators.
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
                onClick={() => handleDelete(dialog.employee)}
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
