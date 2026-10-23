# PriyoniX One ERP

PriyoniX One is a database-backed business workspace for people, customer relationships, projects, finance, procurement, assets, training and placement. It combines the supplied application's existing workflows with twenty new business modules and shared administration services.

The interface uses a premium light workspace with white navigation, a soft-gray work area, locally bundled Inter typography, responsive tables and forms, keyboard-friendly dialogs, global search and personal notifications. The supplied PriyoniX mark is preserved in authentication, navigation, loading states and the favicon. Records and totals come from the API; the frontend does not substitute hardcoded demonstration data for saved operations.

**Delivery boundary:** the code covers the functional areas described below within a deliberately bounded model. Company UAT, a reviewed company repository/merge, production deployment, external-provider integration and operational sign-off are separate requirements. This README does not carry forward historical test counts or claim a passing CI/production deployment.

## Documentation

- [Assignment handover and acceptance matrix](docs/ASSIGNMENT-HANDOVER.md): requirement-by-requirement coverage, supported business rules, architecture, user/admin guide, UI research and remaining company decisions.
- [Verification evidence](docs/VERIFICATION.md): executed Java, frontend, live API, browser and backup/restore checks for this delivery.
- [Platform operations and recovery](docs/platform-operations.md): local Compose, environment configuration, health, backups and isolated restore.
- [Implementation workstreams](docs/IMPLEMENTATION.md): implementation planning context.
- `NEXT_PHASE_PLAN.md` is the earlier planning baseline. Its old “not started” statuses and historical totals are not the current feature inventory; use this README, the handover matrix and current verification evidence.

## Included modules

| Workspace | Delivered functionality |
| --- | --- |
| Core administration | Organization settings, departments, user provisioning, enable/disable and role changes, fixed role-permission matrix, audit search and system status. |
| CRM & sales | Clients, leads and won-lead conversion, contacts, opportunities, single-line quotations and follow-ups. |
| Projects & work | Projects, teams, tasks, subtasks, milestones, priorities, delivery status and submitted/approved timesheets. |
| People & HR | Employee records, daily attendance, leave requests, administrator decisions, ownership-scoped employee records and CSV-based operational reporting. |
| Finance | Multi-line invoices, manually recorded receipts/payments, expenses and approval/reimbursement, department budgets, financial summaries and CSV exports. |
| Procurement | Vendors, purchase requests and approvals, single-item purchase orders, partial/final goods receipts and remaining-quantity validation. |
| Assets | Asset register, employee allocation/return, retirement and maintenance workflows. |
| Training & placement | Courses, batches/trainers, student enrollment, attendance, assessments, computed percentages and application-to-placement tracking. |
| Marketing | Campaign lifecycle, budget/spend fields, persisted lead attribution and derived pipeline/conversion measures. |
| Reporting & services | Admin/employee summaries, module filters/pagination, CSV exports, authorized global search, own notifications and record attachments. |

The twenty workspace module keys are `contacts`, `opportunities`, `quotations`, `timesheets`, `attendance`, `leave`, `vendors`, `purchase-requests`, `purchase-orders`, `goods-receipts`, `assets`, `maintenance`, `courses`, `batches`, `students`, `training-attendance`, `assessments`, `placements`, `budgets` and `departments`.

## Architecture

- **Frontend:** React 19, React Router and Vite 8; JavaScript components with shared light-design tokens, a 240px desktop sidebar, a mobile drawer below 1024px and locally bundled Inter variable fonts. The sign-in screen no longer loads the decorative Three.js scene.
- **Backend:** Java 21, Spring Boot 4.1, Spring Security, Bean Validation and Spring Data MongoDB.
- **Persistence:** MongoDB 8 in a replica set, existing dedicated feature collections plus schema-validated `workspace_records`; GridFS stores attachment bytes.
- **Authentication:** BCrypt passwords, 15-minute JWT access tokens and optional rotating HttpOnly refresh cookies. The server-returned role selects the workspace automatically; `/login/admin` and `/login/employee` remain compatible aliases for the same role-free form. The browser stores the short-lived session in sessionStorage, not localStorage, and validates or restores it once during startup.
- **Authorization:** server-side roles and record ownership. Navigation visibility is only a usability aid.
- **Integrity:** field/reference validation, server-side money calculations, allowed status transitions, optimistic record versions, unique business keys and transaction guards for related changes.

```text
frontend/src/
  Dashboard.jsx              Grouped app shell and section routing
  pages/WorkspacePage.jsx    Schema-driven business records and attachments
  pages/PlatformPage.jsx     Organization, accounts, audit and system views
  pages/                     Existing CRM, project, finance and auth pages
  components/WorkspaceUI.jsx Shared native dialogs and table controls
  api.js / workspaceApi.js   Authenticated requests, errors and exports
  Workspace.css / Auth.css   Operational workspace and compact sign-in styles
backend/src/main/java/backend/
  WorkspaceSchemas.java      Fields, references, columns and lifecycle rules
  WorkspaceService.java      Ownership, validation, transactions and workflows
  BusinessRules.java         Normalization and deterministic calculations
  Platform*.java             Settings, accounts, search, notifications, indexes
  Audit*.java                Persisted mutation audit
  Attachment*.java           Authorized file handling and GridFS storage
  *Service.java              Existing dedicated business feature services
scripts/
  platform-compose.yml       Local replica-set/API/frontend topology
  platform-*.Dockerfile      Backend/frontend container build definitions
  platform-nginx.conf        Local frontend/API reverse proxy
  platform-backup.py         BSON backup and guarded isolated restore
```

The supplied project uses MongoDB. The assignment's relational-schema/ERD expectation has not been silently treated as fulfilled by a SQL database: this retained architecture and its explicit reference checks are documented for supervisor review in the handover.

## Run locally

### Requirements

- Java 21 and Maven 3.9+.
- Node.js 22.12+ and npm.
- MongoDB 8 configured as a replica set. A standalone MongoDB instance is not supported for transaction-dependent workflows.
- Docker with Compose only if using the supplied local container topology.

### Configuration

Use `backend/.env.example` and `frontend/.env.example` as variable inventories. Preserve existing local configuration and set secrets through the process environment, IDE, container or approved secret store. **Spring Boot does not load `backend/.env` automatically.** Vite reads its frontend environment at startup/build time; every `VITE_*` value is public browser configuration.

| Variable | Use |
| --- | --- |
| `MONGODB_URI` | Required database connection to the intended replica set. Include the correct replica-set name/connection options. |
| `MONGODB_DATABASE` | Application database; defaults to `priyonix`. Use a separate database for verification. |
| `JWT_SECRET` | Required strong signing key, preferably base64-encoded random bytes; decoded key must be at least 32 bytes. |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Initial administrator provisioning when that account does not exist. Use a company-approved address and strong password. |
| `ADMIN_RESET_PASSWORD` | Keep false/unset for normal startup; do not accidentally reset an existing administrator. |
| `CORS_ALLOWED_ORIGINS` | Exact allowed browser origins, comma-separated; default `http://localhost:5173`. |
| `VITE_API_BASE` | Frontend API origin; default `http://localhost:8080`. Set before starting or building Vite. |
| `SERVER_PORT` | Optional Spring HTTP port override. |
| `AUTH_REFRESH_COOKIE_SECURE` | Must be true for a hosted HTTPS installation; local HTTP defaults false. |
| `AUTH_REFRESH_COOKIE_SAME_SITE` | Default Lax. Cross-site hosting requires deliberate cookie/CORS configuration. |
| `MAIL_*`, `PASSWORD_RESET_*` | Password-reset mail configuration. Keep reset development mode false outside an isolated development environment. |

Passwords require at least 12 characters, uppercase/lowercase letters, a number and a symbol, without whitespace. Supply credentials securely; no default production password is documented.

After configuring the environment and starting a writable replica-set primary, run the API in one terminal:

```powershell
Set-Location backend
mvn spring-boot:run
```

Run the frontend in another terminal:

```powershell
Set-Location frontend
npm ci
npm run dev -- --host 127.0.0.1
```

Use the URL printed by Vite and ensure it matches `CORS_ALLOWED_ORIGINS`. Changing from `localhost` to `127.0.0.1`, or changing the port, changes the browser origin. Match `VITE_API_BASE` to the actual API origin. Do not point a local test run at production data.

For the optional local container installation, export `JWT_SECRET`, `ADMIN_EMAIL` and `ADMIN_PASSWORD`, then run from the repository root:

```powershell
docker compose -f scripts/platform-compose.yml config --quiet
docker compose -f scripts/platform-compose.yml up --build -d
```

That topology serves the UI/API at `http://localhost:5173` and keeps MongoDB on loopback port `27029`. It is a local development configuration with HTTP and an unauthenticated local database, not a production deployment. Full details and data-preserving shutdown instructions are in the [operations guide](docs/platform-operations.md).

The supplied release/backend.jar and release/web are the verified prebuilt local release. The web build targets http://127.0.0.1:18086. After configuring and starting MongoDB and the Java backend on that port, run node scripts/serve-local.mjs to serve http://127.0.0.1:5186. The outer delivery folder also includes Start-PriyoniX.ps1 and LOCAL-ACCESS.txt for the verified setup on the original computer. These local credentials are excluded from the ZIP. Rebuild the frontend for a different API origin.

## Use the application

Administrators maintain the organization and user accounts, create employee records, set up shared reference data, and approve business requests. Link an employee account to its employee record using the same work email. Employees use shared CRM/project work and their own attendance, leave and timesheets; organization-wide HR/finance and administrative APIs remain restricted.

Choose a sidebar area and section tab, search/filter the list, and use **New record**, **View** or **Edit**. Record details contain the available lifecycle decisions and attachments. Status changes are validated on the server. If a record has changed elsewhere, refresh before retrying. Delete confirmations do not override reference or posted-history protection.

Create dependencies in order: client before opportunity/quote; employee/project before timesheet; vendor and approved request before order; placed order before goods receipt; course/trainer/batch before student and assessment. The [handover guide](docs/ASSIGNMENT-HANDOVER.md) provides the full sequence and acceptance scenarios.

## Checks and evidence

From `backend`, the standard Java check is:

```powershell
mvn test
```

The real MongoDB workspace tests are opt-in. Configure `WORKSPACE_MONGO_TEST_URI` to an isolated replica-set server, then run:

```powershell
mvn -Dtest=WorkspaceMongoIntegrationTest test
```

That suite creates a separately named synthetic database and cleans up only that test database. A normal test run without the environment variable can skip these checks; do not report skipped tests as verified transaction coverage.

From `frontend`:

```powershell
npm run lint
npm run build
node --test src/auth/__tests__/*.test.mjs
```

The repository also contains `frontend/scripts/e2e-auth.mjs` and its PowerShell launcher. Review its required environment/browser setup before use. Use separate admin/employee accounts and authorized synthetic records for end-to-end checks. Final evidence should include exercised critical workflows, negative permission/validation cases, browser behavior, persistence and recovery—not only successful compilation.

The delivery's reported verification results are 472 passing Java tests (including two real-MongoDB tests), successful frontend lint/build, 169 original plus 91 additional passing live checks, and an isolated restore of 231 documents across 18 collections including GridFS with hash/index/options checks. See [Verification evidence](docs/VERIFICATION.md) for the environment, commands and scope. These results do not establish a CI execution or production release.

The packaged release also passed 26 restart/persistence checks. A GitHub Actions workflow is supplied in .github/workflows/verify.yml; remote CI execution is pending.

## Operations and limits

Public `GET /api/health` is liveness. Administrator `GET /api/platform/status` checks database connectivity, uptime and counts. The audit API records successful authenticated mutations without request bodies or secrets; audit persistence occurs after the business commit and is not an atomic compliance journal.

Attachments accept PDF, PNG, JPEG, UTF-8 TXT and CSV up to 5 MB, with content/extension checks and authorized downloads. The backup utility covers Mongo collections and GridFS, validates checksums, and restores only into a new empty destination. Follow the [backup/restore guide](docs/platform-operations.md); the presence of a script does not prove a scheduled backup policy or recovery drill.

Known scope limits:

- Financial workflows calculate and display INR. Organization currency is a stored preference, not a multi-currency conversion engine.
- Quotations and purchase orders are single-line/single-item; invoices retain multiple lines. Budgets do not automatically reserve or reconcile actual spending.
- Two fixed roles and one organization; no custom role builder, multi-tenancy, SSO or MFA.
- No payroll, general ledger, statutory tax compliance, bank reconciliation, warehouse stock ledger, asset depreciation, credit-note/reversal subsystem or automatic quotation-to-invoice conversion.
- Payments are manual records. No external payment, banking, accounting, advertising, job-board or messaging provider is connected. SMTP reset code requires configured and verified delivery.
- Operational CSV reports are included. No scheduled report distribution, PDF reporting engine or arbitrary report designer.
- Startup indexes and legacy role migration are included; a complete versioned migration/import/reconciliation framework remains to be approved.
- Production TLS/private database configuration, secret rotation, malware scanning, load/security testing, CI/CD, monitoring/alerting, encrypted scheduled backups, recovery ownership and company UAT/sign-off remain release work.

UI research is grounded in [Carbon data-table guidance](https://carbondesignsystem.com/components/data-table/usage/), [W3C WAI modal-dialog guidance](https://www.w3.org/WAI/ARIA/apg/patterns/dialog-modal/) and [ERPNext purchase-receipt documentation](https://docs.frappe.io/erpnext/purchase-receipt). The handover records exactly which decisions were adopted and which capabilities are outside scope.

