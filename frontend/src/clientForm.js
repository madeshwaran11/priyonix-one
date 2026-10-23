// Shared helpers and constants for the client add/edit form.

export const CLIENT_STATUSES = ["ACTIVE", "PROSPECT", "INACTIVE", "CHURNED"];

export function emptyClientForm() {
  return {
    name: "",
    companyName: "",
    email: "",
    phone: "",
    address: "",
    industry: "",
    status: "ACTIVE",
    assignedToId: "",
    notes: "",
  };
}

export function clientToForm(client) {
  return {
    name: client.name || "",
    companyName: client.companyName || "",
    email: client.email || "",
    phone: client.phone || "",
    address: client.address || "",
    industry: client.industry || "",
    status: client.status || "ACTIVE",
    assignedToId: client.assignedToId || "",
    notes: client.notes || "",
  };
}

/** Converts client form state into the JSON payload the backend expects. */
export function clientFormToPayload(form) {
  return {
    name: form.name.trim(),
    companyName: form.companyName.trim() || null,
    email: form.email.trim(),
    phone: form.phone.trim() || null,
    address: form.address.trim() || null,
    industry: form.industry.trim() || null,
    status: form.status,
    assignedToId: form.assignedToId || null,
    notes: form.notes.trim() || null,
  };
}
