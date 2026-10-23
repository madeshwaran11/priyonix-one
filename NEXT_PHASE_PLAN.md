# PriyoniX ONE — Next Phase Implementation Plan

This plan covers the remaining enterprise modules after Marketing & Campaigns:
procurement/vendor management, asset management, organization settings, audit
logs, notifications, backups and monitoring.

It is intentionally sequenced so cross-cutting controls (data integrity, audit,
permissions) land before modules that depend on them. No module should be marked
complete until it has backend tests, API workflow verification, lint/build
results and a documented RBAC matrix.

Status values: **Completed and verified**, **Implemented but not fully tested**,
**In progress**, **Blocked**, **Not started**.

---

## 1. Current baseline

- Core ERP modules are implemented and verified: auth, RBAC, HR, CRM, leads,
  follow-ups, projects/tasks/milestones, invoices/payments/expenses, finance,
  dashboard/reports and marketing campaigns.
- Backend: **322 tests passing**.
- HTTP E2E: **54/54 regression** and **55/55 marketing** checks passing.
- Frontend: lint clean; production build succeeds.
- Remaining gap: procurement, assets, settings, audit, notifications, backups
  automation and monitoring are **Not started**.
- Production Atlas startup remains **Blocked** until real credentials are
  supplied and verified in a deployment environment.

---

## 2. Guiding constraints

- Reuse the existing patterns: controller → service → repository, DTO records,
  Bean Validation, service-level business rules, `ApiExceptionHandler` errors.
- Keep API routes under `/api`, lowercase kebab-case, paginated list responses
  consistent with existing modules.
- Preserve the brown/gold glassmorphism UI and mobile layout.
- No demo data. Every dashboard/report tile must come from persisted records.
- Server-side RBAC is authoritative. Hiding UI controls is not authorization.
- Never store secrets in code, logs, audit entries or API responses.
- No commits, pushes or deployments without explicit approval.

---

## 3. Recommended sequence

| Order | Workstream | Why now | Indicative size |
| --- | --- | --- | --- |
| 0 | Data integrity + browser E2E hardening | Prevents duplicate/race defects before adding more records | 1–3 days |
| 1 | Organization settings foundation | Currency, timezone and numbering affect procurement/assets | 2–4 days |
| 2 | Audit log foundation | Cross-cutting; retrofitting later is costly | 3–5 days |
| 3 | Procurement / vendors | Highest business value and clear workflow | 8–12 days |
| 4 | Asset management | Depends on vendors/settings, simpler after procurement | 5–8 days |
| 5 | Notifications | Needs audit/event pipeline and workflow events | 4–6 days |
| 6 | Backups + monitoring | Operational readiness; partly scripts/infrastructure | 3–5 days |

Estimates are indicative and assume one experienced full-stack developer with
the existing test harness.

---

## 4. Phase 0 — Hardening before new modules

### 4.1 MongoDB unique indexes

**Problem:** uniqueness is service-level for employee code/email, client email,
project/task code and campaign name. Concurrent requests can race.

**Work:**

- Declare `@Indexed(unique = true)` on the natural keys, or create indexes
  explicitly in a migration/startup component.
- Prefer a deliberate `MongoIndexInitializer` over globally enabling
  `auto-index-creation` so index creation is explicit and testable.
- Add compound/index support for common filters:
  - leads: `campaignId`, `status`, `createdAt`
  - campaigns: `status`, `channel`, `createdAt`
  - follow-ups: `leadId`, `clientId`, `completed`, `dueDate`
- Verify existing data first; duplicate cleanup must be approved before a unique
  index is built.
- Document index inventory and rollback.

**Acceptance criteria:**

- Duplicate concurrent inserts return `409` via `DuplicateKeyException`.
- Existing test suite remains green.
- Index creation is idempotent and safe to re-run.

### 4.2 Browser E2E harness

- Add Playwright (or Cypress) as a dev dependency.
- Cover: login/logout, session expiry, marketing create/edit/status/quick-add
  lead, lead conversion, finance payment, RBAC 403 UX.
- Run against an isolated database, seed admin/user through environment or test
  fixtures.
- Capture screenshots on failure; do not commit secrets.

**Acceptance criteria:**

- One command starts/stops the test stack and runs all browser checks.
- Marketing workflow is covered end to end in a real browser.

---

## 5. Phase 1 — Organization settings

### 5.1 Scope

A singleton organization profile used by other modules:

- Company name, legal name, logo URL, address, phone, email, website, tax id.
- Base currency, locale/timezone, date format.
- Invoice prefix and next number, expense categories.
- Notification preferences (defaults only; secrets stay in environment).
- Fiscal year start month.

### 5.2 Data model

`OrganizationSettings` (`organization_settings`, singleton document):

```text
id, companyName, legalName, logoUrl, email, phone, website,
addressLine1, addressLine2, city, state, postalCode, country,
taxId, baseCurrency, timezone, locale, dateFormat,
invoicePrefix, nextInvoiceNumber, fiscalYearStartMonth,
updatedAt, updatedById
```

Do not store SMTP passwords, JWT secrets or Atlas URIs here.

### 5.3 API

| Method | Route | Access |
| --- | --- | --- |
| GET | `/api/settings/organization` | any authenticated |
| PUT | `/api/settings/organization` | ADMIN |

Rules:

- Validate currency as ISO 4217, timezone as IANA, month 1–12.
- Invoice prefix alphanumeric/dash, length bounded.
- Never allow decreasing `nextInvoiceNumber` below the highest issued invoice.
- `PUT` writes an audit event.

### 5.4 UI

- Settings page in the sidebar (replaces the placeholder if one exists).
- Sections: Company profile, Localization, Finance numbering, Preferences.
- ADMIN sees editable form; USER sees read-only values.
- Loading/error/saved states and field-level validation.

### 5.5 Tests

- Service validation for currency/timezone/prefix/number rules.
- API auth (401), RBAC (USER PUT = 403), round-trip GET/PUT.
- Invoice creation consumes the configured prefix/number after integration.

**Acceptance criteria:** settings persist, are RBAC-protected, used by at least
invoice numbering, and audited.

---

## 6. Phase 2 — Audit logs

### 6.1 Scope

Append-only record of security-relevant and business mutations:

- actor id/email/role
- action (`CREATE`, `UPDATE`, `DELETE`, `LOGIN`, `LOGOUT?`, `APPROVE`,
  `CONVERT`, `STATUS_CHANGE`)
- resource type and id
- timestamp, request id, IP/user agent (if available)
- safe change summary (field names and non-secret before/after values)

Never log passwords, JWT tokens, reset tokens, Mongo URIs or full request bodies
that may contain secrets.

### 6.2 Data model

`AuditEvent` (`audit_events`):

```text
id, actorId, actorEmail, actorRole, action, resourceType, resourceId,
summary, ipAddress, userAgent, createdAt
```

Indexes: `createdAt`, `actorEmail`, `resourceType + resourceId`.

### 6.3 Implementation

- Start with an `AuditService.record(...)` called from mutations.
- Optionally add an interceptor/aspect later, but explicit calls are safer for
  redaction and domain context.
- Cover existing high-value flows first: auth, deletes, expense approvals,
  lead conversion, campaign status/delete, finance payments.
- Add audit calls to every new module from day one.

### 6.4 API and UI

| Method | Route | Access |
| --- | --- | --- |
| GET | `/api/audit-logs` | ADMIN |
| GET | `/api/audit-logs/{id}` | ADMIN |

Filters: date range, actor, action, resource type, resource id, text search.
UI: ADMIN-only table with filters, pagination and detail drawer. No edit/delete
endpoint. Retention policy documented (for example 12–24 months).

### 6.5 Tests

- Audit entry created for approved/rejected mutation.
- Password/token fields never appear.
- USER receives 403 on read.
- Pagination/filter tests.

**Acceptance criteria:** every critical mutation produces exactly one safe audit
event; logs are ADMIN-readable and immutable through the API.

---

## 7. Phase 3 — Procurement and vendor management

### 7.1 Scope (release 1)

- Vendor registry.
- Purchase requests with approval.
- Purchase orders with line items.
- Goods receipt against a PO.
- Vendor performance summary (orders, spend, on-time receipts).
- Optional vendor bill captured as an expense with `vendorId` reference.

Do not attempt full accounts payable, multi-currency accounting or tax engines in
this release.

### 7.2 Data model

`Vendor` (`vendors`):

```text
id, code (unique), name, contactName, email, phone, taxId,
address, category, paymentTerms, status (ACTIVE|INACTIVE),
notes, createdAt, updatedAt
```

`PurchaseRequest` (`purchase_requests`):

```text
id, requestNumber (unique), requestedById (employee), department,
justification, currency, items[{description, quantity, unitPrice}],
estimatedTotal, status (DRAFT|SUBMITTED|APPROVED|REJECTED|CANCELLED),
reviewedById, reviewedAt, reviewNotes, createdAt, updatedAt
```

`PurchaseOrder` (`purchase_orders`):

```text
id, orderNumber (unique), vendorId, purchaseRequestId,
currency, items[{description, quantity, unitPrice, receivedQuantity}],
subtotal, taxAmount, total, status
(DRAFT|ISSUED|PARTIALLY_RECEIVED|RECEIVED|CANCELLED),
expectedDeliveryDate, issuedAt, createdAt, updatedAt
```

`GoodsReceipt` (`goods_receipts`):

```text
id, purchaseOrderId, receivedById, receivedAt, notes,
items[{description, quantityReceived}]
```

### 7.3 Business rules

- Vendor code and name uniqueness; email format validation.
- Request/order totals are computed server-side from line items.
- Quantity and unit price must be non-negative; quantity must be positive.
- Only `DRAFT` requests can be edited by the requester.
- `SUBMITTED` requests can be approved/rejected only by ADMIN.
- Only `APPROVED` requests can generate a PO.
- PO status derives from receipts: none → `ISSUED`, partial → `PARTIALLY_RECEIVED`,
  full → `RECEIVED`.
- Receipts cannot exceed ordered quantity; over-receipt returns `400`.
- Cancel is allowed only before any receipt.
- Delete is ADMIN-only and only for DRAFT/CANCELLED records; otherwise cancel.

### 7.4 API

| Resource | Endpoints |
| --- | --- |
| Vendors | `GET/POST /api/vendors`, `GET/PUT/DELETE /api/vendors/{id}` |
| Purchase requests | `GET/POST /api/purchase-requests`, `GET/PUT/DELETE /api/purchase-requests/{id}`, `PATCH /api/purchase-requests/{id}/status` |
| Purchase orders | `GET/POST /api/purchase-orders`, `GET/PUT/DELETE /api/purchase-orders/{id}`, `POST /api/purchase-orders/{id}/receipts` |
| Goods receipts | `GET /api/purchase-orders/{id}/receipts`, `GET /api/goods-receipts/{id}` |

All authenticated roles may read/create requests. ADMIN approves/rejects and
deletes. Vendors and POs can be created by USER but deletion is ADMIN-only.

### 7.5 UI

- **Procurement** section: Vendors, Requests, Orders.
- Vendor form with validation and duplicate errors.
- Request builder with line items and computed total.
- Approval actions visible to ADMIN; read-only for USER.
- PO detail with receipt progress bars and "Receive goods" dialog.
- Search, status filters, loading/empty/error states, pagination.

### 7.6 Dashboard/report metrics

- Active vendors, open requests, pending approvals, open POs, spend committed,
  overdue deliveries.
- Reports: procurement summary with date filters and CSV export.

### 7.7 Tests

- Service: totals, approvals, PO creation guard, over-receipt, status derivation,
  cancel/delete rules, duplicate vendor code.
- API: 401/403, CRUD, validation, approval workflow, receipt flow.
- E2E: Vendor → Request → Approve → PO → Partial Receipt → Full Receipt.
- Dashboard metrics reflect persisted records.

**Acceptance criteria:** the full procurement workflow is usable by USER and
ADMIN with correct authorization, totals, receipts and audit entries.

---

## 8. Phase 4 — Asset management

### 8.1 Scope

- Asset register with categories.
- Assignment to employees and return history.
- Maintenance schedule and service history.
- Disposal/retirement.
- Optional vendor link for purchase/service.

No accounting-grade depreciation in the first release; if added later, label it
as an estimate and keep it separate from finance totals.

### 8.2 Data model

`Asset` (`assets`):

```text
id, assetCode (unique), name, category, serialNumber,
status (AVAILABLE|ASSIGNED|UNDER_MAINTENANCE|RETIRED|DISPOSED),
purchaseDate, purchaseCost, currency, vendorId,
warrantyEndDate, location, assignedToId,
notes, createdAt, updatedAt
```

`AssetAssignment` (`asset_assignments`):

```text
id, assetId, employeeId, assignedAt, returnedAt, condition, notes
```

`AssetMaintenance` (`asset_maintenance`):

```text
id, assetId, vendorId, type (PREVENTIVE|REPAIR|INSPECTION),
scheduledDate, completedDate, cost, notes, status
```

### 8.3 Business rules

- Asset code unique; serial number optional but unique when present.
- An asset can have only one active assignment.
- Assigning requires an existing active employee and `AVAILABLE` asset.
- Returning sets status back to `AVAILABLE` and stamps `returnedAt`.
- Maintenance moves status to `UNDER_MAINTENANCE`; completion restores previous
  state only if not retired/disposed.
- Retired/disposed assets cannot be assigned.
- Delete is ADMIN-only and only when no assignment/history exists; otherwise
  retire/dispose.

### 8.4 API

| Resource | Endpoints |
| --- | --- |
| Assets | `GET/POST /api/assets`, `GET/PUT/DELETE /api/assets/{id}` |
| Assignments | `GET /api/assets/{id}/assignments`, `POST /api/assets/{id}/assign`, `POST /api/assets/{id}/return` |
| Maintenance | `GET /api/assets/{id}/maintenance`, `POST /api/assets/{id}/maintenance`, `PATCH /api/assets/{id}/maintenance/{maintenanceId}` |

### 8.5 UI

- Asset register with category/status/location filters and search.
- Asset detail: current holder, assignment history, maintenance history.
- Assign/return dialog; maintenance scheduling.
- Dashboard tiles: total assets, assigned, under maintenance, warranty expiring
  in 30 days.

### 8.6 Tests

- Service: unique code/serial, assignment conflict, return, maintenance state,
  retire/dispose guards.
- API: RBAC and validation.
- E2E: Create asset → Assign employee → Return → Maintenance → Retire.
- Dashboard metrics.

**Acceptance criteria:** asset lifecycle and assignment history are persisted and
RBAC-protected; no double assignment is possible.

---

## 9. Phase 5 — Notifications

### 9.1 Scope

In-app notifications for:

- purchase request submitted/approved/rejected
- PO issued/received/overdue
- task assigned/overdue
- expense submitted/approved/rejected
- campaign status changed
- asset assigned/returned/maintenance due
- system messages

Email delivery is a later optional adapter. Do not store email credentials in
the app database.

### 9.2 Data model

`Notification` (`notifications`):

```text
id, recipientUserId, type, title, message, link,
readAt, createdAt, createdById, dedupeKey
```

Indexes: `recipientUserId + readAt + createdAt`, TTL optional after retention.

### 9.3 Business rules

- Recipients are resolved server-side from domain events; users cannot create
  arbitrary notifications for others.
- `dedupeKey` prevents duplicate "overdue task" bursts.
- Read/unread count is per authenticated user.
- Retention policy: archive or delete after 90–180 days.

### 9.4 API and UI

| Method | Route | Access |
| --- | --- | --- |
| GET | `/api/notifications` | current user |
| GET | `/api/notifications/unread-count` | current user |
| PATCH | `/api/notifications/{id}/read` | owner |
| PATCH | `/api/notifications/read-all` | current user |
| DELETE | `/api/notifications/{id}` | owner |

UI: bell with unread count, dropdown/list page, mark read/all, deep links.
No notification can be read or modified by another user.

### 9.5 Tests

- Ownership isolation (user A cannot read/mark user B).
- Dedupe behavior.
- Event-to-notification mapping for at least two workflows.
- Unread count updates.

**Acceptance criteria:** in-app notifications are persisted, user-scoped, read
state works and workflow events generate them.

---

## 10. Phase 6 — Backups and monitoring

### 10.1 Backup automation

- Provide `scripts/backup.ps1` and `scripts/restore.ps1` using `mongodump` /
  `mongorestore`, parameters supplied via environment or secret store.
- Schedule off-host: Windows Task Scheduler or platform cron.
- Encrypt archives, restrict ACLs, rotate retention (for example 30 daily,
  12 monthly).
- Record backup success/failure in logs and optionally an admin status record.
- Test restore into a staging database at least quarterly.
- In-app backup execution is **not** recommended initially: web-triggered
  mongodump adds shell/credential exposure. A read-only backup-status page is a
  safer later enhancement.

**Acceptance criteria:** a documented scheduled backup exists, retention and
encryption are defined, and a restore has been verified.

### 10.2 Monitoring

- Add Spring Boot Actuator `health` and `info`; expose only health publicly and
  protect other endpoints or bind to management port.
- Structured logs with request id and secret redaction.
- Uptime check on `/api/health` plus database status.
- Alerts: service down, high error rate, Mongo connection/disk, backup failure.
- Optional ADMIN-only system status page (build/version, DB connectivity,
  last backup time). Never expose secrets or stack traces.

**Acceptance criteria:** uptime and database checks alert correctly; logs are
usable for incident response without leaking secrets.

---

## 11. Cross-cutting acceptance checklist

Every new module must include:

- [ ] Backend model, repository, request/response DTOs, service and controller
- [ ] Bean Validation and domain business rules with clear `400`/`409` responses
- [ ] Server-side RBAC (401 vs 403) and frontend role-aware controls
- [ ] Search, filters, pagination, loading, empty and error states
- [ ] Responsive brown/gold glassmorphism UI
- [ ] Audit events for critical mutations
- [ ] Notifications where a user action needs another user's attention
- [ ] Dashboard/report metrics from persisted data (no demo values)
- [ ] Unit tests, API integration tests and HTTP/browser E2E workflow
- [ ] `mvn -o test`, `npm run lint`, `npm run build` all green
- [ ] README API/RBAC/business-rule updates and known limitations
- [ ] No secrets in code, logs, fixtures or documentation

---

## 12. Risks and decisions

| Risk / decision | Mitigation |
| --- | --- |
| Duplicate data before indexes exist | Do Phase 0 before procurement/assets |
| Audit retrofits are costly | Build audit foundation before new modules |
| Procurement scope creep into full accounting | First release stops at PO + receipt; vendor bills reuse expenses |
| Asset depreciation ambiguity | Defer accounting-grade depreciation; label estimates clearly |
| Notification spam | Dedupe keys, per-user preferences, retention policy |
| Backup credentials in app | Keep backups script/infrastructure-driven, not browser-triggered |
| Atlas deployment unverified | Run a production-like startup once real credentials are supplied |
| Large three.js bundle | Keep it lazy; optionally provide a reduced-motion/low-bandwidth mode |

---

## 13. Definition of done for the next phase

The next phase is complete when one full operational chain is verified in a
browser and through the API:

**Vendor → Purchase Request → Approval → Purchase Order → Goods Receipt →
Asset (optional) → Audit Event → Notification → Dashboard/Report → Backup check**

At that point, update this plan and the README status table with actual test
counts, commands and any remaining blockers.
