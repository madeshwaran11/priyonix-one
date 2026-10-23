// Small display helpers shared by the project and task pages.

export function formatDate(value) {
  if (!value) return "—";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleDateString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

export function formatStatus(status) {
  return (status || "").replace(/_/g, " ");
}

/// Formats a money value (number or numeric string) with two decimals.
export function formatMoney(value) {
  if (value === null || value === undefined || value === "") return "—";
  const number = Number(value);
  if (Number.isNaN(number)) return "—";
  return number.toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

export function initials(name) {
  return (name || "?")
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join("");
}

/// Maps a status string to the CSS pill tone suffix.
export function statusTone(status) {
  return (status || "").toLowerCase().replace(/_/g, "-");
}

export function priorityTone(priority) {
  return (priority || "").toLowerCase();
}
