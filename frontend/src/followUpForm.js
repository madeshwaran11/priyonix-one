// Shared helpers and constants for the follow-up add/edit form.

export const FOLLOW_UP_ACTIVITY_TYPES = [
  "CALL",
  "EMAIL",
  "MEETING",
  "DEMO",
  "REMINDER",
  "OTHER",
];

export function emptyFollowUpForm({ leadId, clientId } = {}) {
  return {
    activityType: "CALL",
    dueDate: "",
    notes: "",
    completed: false,
    assignedToId: "",
    leadId: leadId || "",
    clientId: clientId || "",
  };
}

export function followUpToForm(followUp) {
  return {
    activityType: followUp.activityType || "CALL",
    dueDate: followUp.dueDate || "",
    notes: followUp.notes || "",
    completed: Boolean(followUp.completed),
    assignedToId: followUp.assignedToId || "",
    leadId: followUp.leadId || "",
    clientId: followUp.clientId || "",
  };
}

/** Converts follow-up form state into the JSON payload the backend expects. */
export function followUpFormToPayload(form) {
  return {
    activityType: form.activityType,
    dueDate: form.dueDate || null,
    notes: form.notes.trim() || null,
    completed: Boolean(form.completed),
    assignedToId: form.assignedToId || null,
    // Exactly one of these is sent — the backend rejects both or neither.
    leadId: form.leadId || null,
    clientId: form.clientId || null,
  };
}
