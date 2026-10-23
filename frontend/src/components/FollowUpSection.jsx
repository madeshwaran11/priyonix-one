import { useCallback, useEffect, useState } from "react";
import {
  createFollowUp,
  deleteFollowUp,
  listClientFollowUps,
  listLeadFollowUps,
  updateFollowUp,
} from "../api";
import { FOLLOW_UP_ACTIVITY_TYPES } from "../followUpForm";
import { formatDate, formatStatus } from "../format";

function employeeLabel(employee) {
  return `${employee.fullName} (${employee.employeeId})`;
}

/**
 * Follow-up management embedded inside a client or lead detail view.
 *
 * Loads the activities linked to the given lead/client, lets the user add a
 * new one, tick it off and (for administrators) delete it. All state comes
 * from the backend — the section only ever shows live rows.
 */
export default function FollowUpSection({
  token,
  isAdmin,
  leadId,
  clientId,
  employees = [],
}) {
  const [items, setItems] = useState([]);
  const [state, setState] = useState("loading");
  const [error, setError] = useState("");
  const [reloadKey, setReloadKey] = useState(0);
  const [busyId, setBusyId] = useState("");
  const [confirmingId, setConfirmingId] = useState("");

  const [form, setForm] = useState({
    activityType: "CALL",
    dueDate: "",
    notes: "",
    assignedToId: "",
  });
  const [isSaving, setIsSaving] = useState(false);
  const [formError, setFormError] = useState("");
  const [message, setMessage] = useState("");

  const load = useCallback(() => {
    const request = leadId
      ? listLeadFollowUps(token, leadId)
      : listClientFollowUps(token, clientId);

    return request;
  }, [token, leadId, clientId]);

  useEffect(() => {
    let cancelled = false;

    load()
      .then((data) => {
        if (cancelled) return;
        setItems(data);
        setState("ready");
      })
      .catch((err) => {
        if (cancelled) return;
        setError(err.message || "Could not load follow-ups.");
        setState("error");
      });

    return () => {
      cancelled = true;
    };
  }, [load, reloadKey]);

  function reload() {
    setState("loading");
    setError("");
    setReloadKey((key) => key + 1);
  }

  async function handleAdd(event) {
    event.preventDefault();
    setIsSaving(true);
    setFormError("");
    setMessage("");

    try {
      await createFollowUp(token, {
        activityType: form.activityType,
        dueDate: form.dueDate || null,
        notes: form.notes.trim() || null,
        completed: false,
        assignedToId: form.assignedToId || null,
        leadId: leadId || null,
        clientId: clientId || null,
      });
      setForm({
        activityType: "CALL",
        dueDate: "",
        notes: "",
        assignedToId: "",
      });
      setMessage("Follow-up added.");
      reload();
    } catch (err) {
      setFormError(err.message || "Could not add the follow-up.");
    } finally {
      setIsSaving(false);
    }
  }

  async function toggleCompleted(item) {
    setBusyId(item.id);
    setFormError("");
    setMessage("");

    try {
      await updateFollowUp(token, item.id, {
        activityType: item.activityType,
        dueDate: item.dueDate,
        notes: item.notes,
        completed: !item.completed,
        assignedToId: item.assignedToId,
        leadId: item.leadId,
        clientId: item.clientId,
      });
      reload();
    } catch (err) {
      setFormError(err.message || "Could not update the follow-up.");
    } finally {
      setBusyId("");
    }
  }

  async function handleDelete(item) {
    setBusyId(item.id);
    setFormError("");
    setMessage("");

    try {
      await deleteFollowUp(token, item.id);
      setConfirmingId("");
      setMessage("Follow-up deleted.");
      reload();
    } catch (err) {
      setFormError(err.message || "Could not delete the follow-up.");
    } finally {
      setBusyId("");
    }
  }

  return (
    <div className="dash-form-block">
      <h4>Follow-ups</h4>

      {message && (
        <p className="dash-success" role="status">
          {message}
        </p>
      )}

      {state === "loading" && <p className="dash-form-hint">Loading…</p>}

      {state === "error" && (
        <p className="dash-form-error" role="alert">
          {error}
        </p>
      )}

      {state === "ready" && items.length === 0 && (
        <p className="dash-form-hint">No follow-ups yet.</p>
      )}

      {state === "ready" && items.length > 0 && (
        <ul className="dash-mini-list">
          {items.map((item) => (
            <li key={item.id}>
              <div className="dash-followup-row">
                <label className="dash-checkbox-inline">
                  <input
                    type="checkbox"
                    checked={item.completed}
                    onChange={() => toggleCompleted(item)}
                    disabled={busyId === item.id}
                  />
                  <span className={item.completed ? "is-done" : ""}>
                    {formatStatus(item.activityType)} · {formatDate(item.dueDate)}
                  </span>
                </label>

                {item.overdue && !item.completed && (
                  <span className="dash-overdue-badge">Overdue</span>
                )}
              </div>

              {item.notes && <em className="dash-mini-meta">{item.notes}</em>}

              <div className="dash-followup-meta">
                <span>
                  {item.assignedTo
                    ? item.assignedTo.fullName
                    : "Unassigned"}
                </span>
                {isAdmin &&
                  (confirmingId === item.id ? (
                    <span className="dash-followup-actions">
                      <button
                        type="button"
                        className="dash-link-button danger"
                        onClick={() => handleDelete(item)}
                        disabled={busyId === item.id}
                      >
                        Confirm
                      </button>
                      <button
                        type="button"
                        className="dash-link-button"
                        onClick={() => setConfirmingId("")}
                        disabled={busyId === item.id}
                      >
                        Cancel
                      </button>
                    </span>
                  ) : (
                    <button
                      type="button"
                      className="dash-link-button danger"
                      onClick={() => setConfirmingId(item.id)}
                      disabled={busyId === item.id}
                    >
                      Delete
                    </button>
                  ))}
              </div>
            </li>
          ))}
        </ul>
      )}

      <form className="dash-followup-add" onSubmit={handleAdd}>
        <label className="dash-field">
          <span>Type</span>
          <select
            value={form.activityType}
            onChange={(event) =>
              setForm((previous) => ({
                ...previous,
                activityType: event.target.value,
              }))
            }
            disabled={isSaving}
          >
            {FOLLOW_UP_ACTIVITY_TYPES.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </select>
        </label>

        <label className="dash-field">
          <span>Due date *</span>
          <input
            type="date"
            value={form.dueDate}
            onChange={(event) =>
              setForm((previous) => ({
                ...previous,
                dueDate: event.target.value,
              }))
            }
            required
            disabled={isSaving}
          />
        </label>

        <label className="dash-field">
          <span>Assigned to</span>
          <select
            value={form.assignedToId}
            onChange={(event) =>
              setForm((previous) => ({
                ...previous,
                assignedToId: event.target.value,
              }))
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
        </label>

        <label className="dash-field dash-field-wide">
          <span>Notes</span>
          <input
            value={form.notes}
            onChange={(event) =>
              setForm((previous) => ({
                ...previous,
                notes: event.target.value,
              }))
            }
            placeholder="What needs to happen?"
            maxLength={2000}
            disabled={isSaving}
          />
        </label>

        {formError && (
          <p className="dash-form-error" role="alert">
            {formError}
          </p>
        )}

        <button
          type="submit"
          className="dash-button ghost small"
          disabled={isSaving}
        >
          {isSaving ? "Adding…" : "Add follow-up"}
        </button>
      </form>
    </div>
  );
}
