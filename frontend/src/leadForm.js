// Shared helpers and constants for the lead add/edit form.

export const LEAD_STATUSES = [
  "NEW",
  "CONTACTED",
  "QUALIFIED",
  "PROPOSAL",
  "WON",
  "LOST",
];

export const LEAD_PRIORITIES = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];

export const LEAD_SOURCES = [
  "Website",
  "Referral",
  "Cold Call",
  "Event",
  "Social Media",
  "Email Campaign",
  "Other",
];

export function emptyLeadForm(defaultCampaignId = "") {
  return {
    name: "",
    company: "",
    email: "",
    phone: "",
    source: "",
    status: "NEW",
    priority: "MEDIUM",
    assignedToId: "",
    campaignId: defaultCampaignId || "",
    estimatedValue: "",
    followUpDate: "",
    notes: "",
  };
}

export function leadToForm(lead) {
  return {
    name: lead.name || "",
    company: lead.company || "",
    email: lead.email || "",
    phone: lead.phone || "",
    source: lead.source || "",
    status: lead.status || "NEW",
    priority: lead.priority || "MEDIUM",
    assignedToId: lead.assignedToId || "",
    campaignId: lead.campaignId || "",
    estimatedValue:
      lead.estimatedValue === null || lead.estimatedValue === undefined
        ? ""
        : String(lead.estimatedValue),
    followUpDate: lead.followUpDate || "",
    notes: lead.notes || "",
  };
}

/** Converts lead form state into the JSON payload the backend expects. */
export function leadFormToPayload(form) {
  const value = form.estimatedValue === "" ? null : Number(form.estimatedValue);

  return {
    name: form.name.trim(),
    company: form.company.trim() || null,
    email: form.email.trim(),
    phone: form.phone.trim() || null,
    source: form.source.trim() || null,
    status: form.status,
    priority: form.priority,
    assignedToId: form.assignedToId || null,
    campaignId: form.campaignId || null,
    estimatedValue: Number.isNaN(value) ? null : value,
    followUpDate: form.followUpDate || null,
    notes: form.notes.trim() || null,
  };
}
