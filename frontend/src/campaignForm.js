// Shared constants and validation for the campaign create/edit form.

export const CAMPAIGN_STATUSES = [
  "PLANNED",
  "ACTIVE",
  "PAUSED",
  "COMPLETED",
  "CANCELLED",
];

export const CAMPAIGN_CHANNELS = [
  "EMAIL",
  "SOCIAL",
  "SEARCH",
  "EVENTS",
  "CONTENT",
  "REFERRAL",
  "PAID_ADS",
  "OTHER",
];

function isoDate(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function emptyCampaignForm() {
  const start = new Date();
  const end = new Date();
  end.setDate(end.getDate() + 30);

  return {
    name: "",
    description: "",
    channel: "OTHER",
    status: "PLANNED",
    startDate: isoDate(start),
    endDate: isoDate(end),
    budget: "",
    spend: "",
  };
}

export function campaignToForm(campaign) {
  return {
    name: campaign.name || "",
    description: campaign.description || "",
    channel: campaign.channel || "OTHER",
    status: campaign.status || "PLANNED",
    startDate: campaign.startDate || "",
    endDate: campaign.endDate || "",
    budget:
      campaign.budget === null || campaign.budget === undefined
        ? ""
        : String(campaign.budget),
    spend:
      campaign.spend === null || campaign.spend === undefined
        ? ""
        : String(campaign.spend),
  };
}

/**
 * Client-side validation that mirrors the backend rules. Returns field errors
 * plus an optional summary message so the form can focus the first problem.
 */
export function validateCampaignForm(form) {
  const fieldErrors = {};

  if (!form.name || !form.name.trim()) {
    fieldErrors.name = "Campaign name is required";
  }

  if (!form.startDate) {
    fieldErrors.startDate = "Start date is required";
  }

  if (!form.endDate) {
    fieldErrors.endDate = "End date is required";
  }

  if (
    form.startDate &&
    form.endDate &&
    form.endDate < form.startDate
  ) {
    fieldErrors.endDate = "End date must be on or after the start date";
  }

  const budget = form.budget === "" ? null : Number(form.budget);
  if (budget !== null && (Number.isNaN(budget) || budget < 0)) {
    fieldErrors.budget = "Budget must be zero or a positive number";
  }

  const spend = form.spend === "" ? null : Number(form.spend);
  if (spend !== null && (Number.isNaN(spend) || spend < 0)) {
    fieldErrors.spend = "Spend must be zero or a positive number";
  }

  return {
    fieldErrors,
    message:
      Object.keys(fieldErrors).length > 0
        ? "Please fix the highlighted fields."
        : "",
  };
}

/** Converts campaign form state into the JSON payload the backend expects. */
export function campaignFormToPayload(form) {
  return {
    name: form.name.trim(),
    description: form.description.trim() || null,
    channel: form.channel,
    status: form.status,
    startDate: form.startDate,
    endDate: form.endDate,
    budget: form.budget === "" ? null : Number(form.budget),
    spend: form.spend === "" ? null : Number(form.spend),
  };
}
