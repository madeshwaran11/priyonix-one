import assert from "node:assert/strict";
import { test } from "node:test";
import { safeReturnTo } from "../returnTo.js";

const sections = ["overview", "clients", "tasks", "attendance"];

test("return destination preserves a permitted record and uses the account workspace", () => {
  assert.equal(safeReturnTo("/admin?section=clients&record=client-42", "EMPLOYEE", sections), "/employee?section=clients&record=client-42");
});

test("legacy searchable section aliases keep their permitted deep links", () => {
  assert.equal(safeReturnTo("/admin?section=employees&record=employee-7", "ADMIN", [...sections, "people"]), "/admin?section=people&record=employee-7");
  assert.equal(safeReturnTo("/admin?section=campaigns&record=campaign-9", "ADMIN", [...sections, "marketing"]), "/admin?section=marketing&record=campaign-9");
});

test("forbidden or unknown sections return to the account home", () => {
  assert.equal(safeReturnTo("/admin?section=invoices&record=42", "EMPLOYEE", sections), "/employee");
  assert.equal(safeReturnTo("/employee?section=unknown", "ADMIN", sections), "/admin");
});

test("external, ambiguous, authentication and malformed URLs cannot become return targets", () => {
  for (const target of ["https://example.test", "//example.test", "/\\example.test", "/login", "/reset-password?token=abc", "/admin\n", "/admin/%2f%2fevil", { path: "/admin" }, null]) {
    assert.equal(safeReturnTo(target, "ADMIN", sections), "/admin");
  }
});

test("unrelated query parameters and fragments are discarded", () => {
  assert.equal(safeReturnTo("/admin?section=tasks&record=15&returnTo=https://example.test#anything", "ADMIN", sections), "/admin?section=tasks&record=15");
});

test("record IDs require a permitted section and a safe identifier", () => {
  assert.equal(safeReturnTo("/employee?record=15", "EMPLOYEE", sections), "/employee");
  assert.equal(safeReturnTo("/employee?section=tasks&record=..%2F..%2Fsecret", "EMPLOYEE", sections), "/employee?section=tasks");
});
