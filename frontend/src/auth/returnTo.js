import { homePathForRole } from "../roles.js";

/** Keep only supported workspace destinations and the account's permitted sections. */
export function safeReturnTo(candidate, role, allowedSections = []) {
  const home = homePathForRole(role);
  if (typeof candidate !== "string" || !candidate.startsWith("/") || candidate.startsWith("//")) return home;
  if ([...candidate].some((character) => character === "\\" || character.charCodeAt(0) <= 32 || character.charCodeAt(0) === 127)) return home;
  let url;
  try { url = new URL(candidate, "https://workspace.invalid"); }
  catch { return home; }
  if (url.origin !== "https://workspace.invalid" || !["/admin", "/employee"].includes(url.pathname)) return home;
  const requestedSection = url.searchParams.get("section");
  const section = requestedSection === "employees" ? "people"
    : requestedSection === "campaigns" ? "marketing"
    : requestedSection;
  if (!section || !new Set(allowedSections).has(section)) return home;
  const query = new URLSearchParams({ section });
  const record = url.searchParams.get("record");
  if (record && /^[a-zA-Z0-9][a-zA-Z0-9_-]{0,127}$/.test(record)) query.set("record", record);
  return `${home}?${query}`;
}
