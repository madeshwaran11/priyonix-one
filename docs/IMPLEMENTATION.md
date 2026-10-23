# ERP completion plan — 22 October 2026

Goal: extend the supplied React/Spring/MongoDB application with persistent, permission-checked workflows for every functional area listed in the assignment. Preserve existing working modules and validate the combined application against an isolated MongoDB replica set.

## Architecture and implementation workstreams

- [x] Inspect the assignment, archive, existing endpoints and automated tests; identify missing modules and integrity defects.
- [x] Business workspace: explicit server-side schemas and validators for contacts, opportunities, quotations, timesheets, attendance, leave, procurement, assets, training, placement, budgets and departments. Versioned Mongo records, validated relationships, transaction-protected workflows, controlled state transitions.
- [x] Platform: organization, users and permissions, append-only audit, personal notifications, authorized search, bounded attachments and service health.
- [x] Frontend: extend existing CRUD pages with schema-driven workspace pages, grouped navigation, native accessible dialogs, keyboard interaction, search, filters, pagination, exports and responsive layout.
- [x] Existing finance: reject edits to paid invoices and approved expenses, guard concurrent payments and invoice numbering, neutralize CSV formulas, preserve posted history.
- [x] Verification: Java tests, frontend lint/build, real Mongo API lifecycle checks, invalid input and role checks, desktop/mobile browser workflows and persistence after restart.
- [x] Handover: requirements/acceptance matrix, data model, API contracts, local launch and backup/restore scripts, user/admin guide, UI research, test evidence and clean source archive.

## Boundaries and decisions

Keep Java 21, Spring Boot 4.1 and React 19 from the ZIP. Use MongoDB replica-set transactions for changes spanning records. The original PDF suggests relational modelling; existing Mongo collections are retained with explicit reference checks and unique indexes, and this architectural choice is documented for supervisor review. ADMIN manages users/HR/finance and approves business requests. EMPLOYEE handles shared customer/project work and their own HR requests. Forms use real APIs and persisted data; synthetic data is seeded only in the isolated local verification database.

Local completion is distinct from company sign-off, an approved repository/PR, live deployment, SMTP, external integrations, operational ownership and approved tax/payroll rules. No production credentials or external messaging are used. Procurement/quotation line items use the documented supported model; payroll/tax compliance is outside the supplied concrete scope.

## UI direction and research

Readable light content area, dark navigation, restrained blue/teal actions, grouped business modules, tabular data, visible lifecycle actions, consistent loading/error/empty states. Carbon table guidance: https://carbondesignsystem.com/components/data-table/usage/ . W3C modal keyboard/focus pattern: https://www.w3.org/WAI/ARIA/apg/patterns/dialog-modal/ . ERP purchase receipt workflow reference: https://docs.frappe.io/erpnext/purchase-receipt .

The built-in image generation tool is unavailable in this session. Existing code-native enterprise controls are extended; no generated concept or pixel-match claim is made.

