# Verification report — 22 October 2026

The supplied ZIP was extended and verified on an isolated local database. Original input ZIP/PDF and unrelated MongoDB services were preserved. All seeded people, companies, finances and attachments are synthetic. This report covers the delivered code and local packaged application; it is not company UAT or production approval.

## Executed checks

| Check | Result | Evidence |
|---|---|---|
| Full Java test suite | **472 tests; 0 failures, 0 errors, 0 skipped** | `evidence/backend-test-summary.json` |
| Real Mongo transaction tests, included above | Duplicate business keys, optimistic conflicts and competing receipts checked against a replica set | `WorkspaceMongoIntegrationTest` |
| Frontend ESLint | Passed, exit 0 | `npm run lint` |
| Frontend release build | Passed, exit 0; 106 modules, split dashboard bundle and locally bundled Inter fonts | `npm run build`, local API port configured |
| Frontend authentication regression | **20 checks passed** for startup restoration, shared refresh, logout races, duplicate sign-in, safe returns and legacy aliases | `node --test src/auth/__tests__/*.test.mjs` |
| Initial live HTTP acceptance | **169 passed** across all 20 added modules, references, workflow transitions, finance and authorization | `evidence/acceptance-results.json` |
| Additional live HTTP acceptance | **91 passed**, no failures | `evidence/extra-acceptance-results.json` |
| Restart and packaged release | **26 checks passed**; all 20 modules, browser-created contact, payment and attachment persisted | `evidence/restart-verification.json` |
| Backup/restore | **231 documents in 18 collections restored** to a new database; BSON hashes, counts, ordered index keys/options and collection options matched | `evidence/restore-verification.json` |
| Compose configuration | `docker compose config --quiet` passed with synthetic environment values | Static configuration only; containers were not deployed |
| Source archive | ZIP integrity checked; real `.env`, dependencies, Git metadata, database files and local credentials excluded | Archive manifest verification |

Additional API coverage includes upload/download byte and SHA-256 matching; GridFS file persistence; authorization on private attachment reads, writes and deletes; invalid content/extensions/oversized files; employee ownership and spoofing checks; denied self-approval; cross-employee search/list/detail isolation; won-lead conversion and repeated-conversion rejection; tasks and subtask completion. Temporary test accounts were disabled after testing.

Finance checks cover calculated totals, invoice payments, overpayment rejection, paid-invoice mutation/deletion protection, approved-expense immutability, legal status transitions, concurrent transaction guards and CSV formula neutralization. These are operational controls, not a statutory accounting or tax certification.

## Redesign browser observations

The packaged redesign was exercised in the actual browser at 1440×1000, 768×1024 and 360×800. A 720px CSS viewport was also used as the layout equivalent of 200% zoom from a 1440px desktop viewport. Verified:

- One role-free sign-in form on `/login`, `/login/admin` and `/login/employee`; the server-returned role opened the correct administrator or employee workspace.
- Remembered-cookie restoration in a new tab, explicit logout, session-only access-token storage, a failed sign-in state with disabled pending action and action-specific text, and permitted/forbidden return destinations.
- Forgot-password and invalid/missing reset-link recovery screens. No real reset email was sent and no account password was changed during this redesign pass.
- All **39 sections in 11 administrator navigation groups** opened through the real sidebar/tabs with their expected page heading and no configuration or recovery failure.
- Global search opened the exact employee record through the `employees` → `people` compatibility alias. Direct client deep links opened their exact record; closing removed only `record`, Back reopened it and Forward closed it again.
- Invalid sections normalized to the correct overview with a clear notice. An employee could open a permitted client deep link but was redirected away from an administrator invoice destination.
- Initial module loading used the branded logo/skeleton treatment. A deliberately blocked client request showed a retry state and recovered to the two saved rows after retry.
- Manual refresh keeps the current rows, metrics and totals visible, disables duplicate submission, and announces progress. A deliberately slowed client refresh retained both saved rows until the updated response completed.
- Client form validation retained entered values. Escape closed the mobile drawer and restored focus to its opener; dialog closure also restored focus.
- Wide tables stayed inside their own horizontal container at 360px (`326px` viewport container, `777px` table scroll width), while the document itself had no horizontal overflow. The 768px and 720px layouts also had no document overflow.
- Emulated `prefers-reduced-motion: reduce` matched and reduced animation/transition duration to `0.00001s`.

The original live browser acceptance also covered contact creation/editing, procurement receipt restrictions, employee-owned forms and packaged restart persistence before the UI redesign. The backend and database model were not changed during the redesign. Date-dependent workflows remain covered through live API checks. Browser checks are focused manual acceptance, not a comprehensive accessibility audit or cross-browser/device certification. `redesign-login-desktop.jpg`, `redesign-dashboard-desktop.jpg` and `redesign-mobile.jpg` are included in `docs/evidence/` inside the ZIP and accompany the outer delivery folder.

## Reproduction

Use Java 21, Maven 3.9.16, Node 22.12+ and MongoDB 8 in replica-set mode. The verification instance binds to loopback: web 5186, API 18086, Mongo 27189. The source uses the existing React/Spring/Mongo stack. No original environment credentials were required.

```powershell
# backend directory, with Java 21 selected:
$env:WORKSPACE_MONGO_TEST_URI = 'mongodb://127.0.0.1:27189/?replicaSet=priyonix-local'
.\mvnw.cmd test
.\mvnw.cmd -DskipTests package

# frontend directory:
npm ci
npm run lint
$env:VITE_API_BASE = 'http://127.0.0.1:18086'
npm run build
```

The database integration suite creates and removes only its own randomly named test database. The live acceptance records are kept in the isolated review database. The running application was stopped before the backup; restore used a different empty database. After restart, the API returned the same persisted records and the retained attachment matched its original SHA-256.

The workflow in `.github/workflows/verify.yml` is supplied to repeat backend, real-Mongo and frontend checks in GitHub Actions. It has not been run in a company repository. Build images and action dependencies need the organization's normal review/pinning policy.

## Release boundary

See `ASSIGNMENT-HANDOVER.md` for the full requirements matrix and bounded implementation choices. External providers, company credentials, production infrastructure, review/merge approval, sustained load/security testing, operational ownership and company sign-off remain external release steps. The local demo does not send email, take payments or contact real employees/customers.
