# Platform operations and recovery

This guide covers a local installation and a recovery drill. The Compose files are operating assets; their presence does not constitute a production deployment or a verified Docker run. Use the delivered verification report for checks actually performed.

## Runtime requirements

- Java 21, Maven 3.9+, Node 22.12+ and npm, MongoDB 8 in a replica set.
- MongoDB transactions are required for workspace approvals, procurement receipts, shared references and account administration. Standalone MongoDB is not a supported configuration.
- Supply `MONGODB_URI`, `MONGODB_DATABASE`, `JWT_SECRET`, `ADMIN_EMAIL` and `ADMIN_PASSWORD` through the process environment. The JWT secret must decode to at least 32 random bytes. Account passwords require at least 12 characters, upper/lower case, a digit and a symbol, without whitespace.
- `ADMIN_RESET_PASSWORD` must remain false/unset during normal startup. Initial administrator configuration creates an absent account without resetting an existing password.

Start the backend from `backend` using `mvn spring-boot:run`. Start the frontend from `frontend` using `npm ci` then `npm run dev`. Set `VITE_API_BASE` before starting/building the frontend when the API does not use `http://localhost:8080`. Set `CORS_ALLOWED_ORIGINS` to the actual frontend origin. Do not commit environment files, database snapshots, refresh tokens or credentials.

## Local Docker Compose installation

Install Docker with Compose and BuildKit. Export `ADMIN_EMAIL` and `ADMIN_PASSWORD` for the initial account, and generate `JWT_SECRET` using a cryptographically random source (for example, PowerShell: `$env:JWT_SECRET = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))`). These are mandatory Compose inputs; no sample credentials are embedded. Run from the repository root:

```sh
docker compose -f scripts/platform-compose.yml config --quiet
docker compose -f scripts/platform-compose.yml up --build -d
docker compose -f scripts/platform-compose.yml ps
```

The UI is `http://localhost:5173`. The reverse proxy forwards `/api` to the backend. MongoDB listens only on loopback port 27029 for host-side recovery tools and uses a named Docker volume. Its local connection string is `mongodb://127.0.0.1:27029/?directConnection=true&replicaSet=priyonix-local`. The health check initializes the single-node replica set and waits for a writable primary. The backend is not published separately.

Use the exact `localhost:5173` URL: the frontend API origin is fixed at build time. Wait for `http://localhost:5173/api/health` to respond before signing in; starting the frontend container does not imply that Java startup has finished. Dockerfile-specific ignore files whitelist source inputs and exclude `.env`, host `node_modules`, database snapshots and local build output from the build contexts. This uses Docker's documented [Dockerfile-specific ignore behavior](https://docs.docker.com/build/concepts/context/).

This Compose topology is for a developer computer: MongoDB has no database authentication and the browser uses HTTP. A hosted installation requires authenticated database access on a private network, TLS termination, secure refresh cookies, an approved SMTP/reset-link configuration, a durable backup location, and deployment-specific origins. Set `AUTH_REFRESH_COOKIE_SECURE=true` when serving HTTPS. The company must choose hosting, ownership, credentials and acceptance conditions before production release.

Stop services without removing data:

```sh
docker compose -f scripts/platform-compose.yml stop
```

Do not use `down --volumes` unless intentionally discarding this installation's database. Docker image tags are functional major-version baselines; pin approved digests after validating the chosen deployment environment.

## Monitoring and audit

- `GET /api/health` provides public liveness.
- An authenticated administrator calls `GET /api/platform/status` for a database ping, uptime and collection counts. A green liveness check alone does not establish database availability.
- Audit records persist successful authenticated business mutations with actor, action, timestamp and resource path/id. The API is append-only; no edit/delete route exists. Request bodies, authorization headers and password values are not recorded.
- Audit writes occur after business commit. An audit-storage fault is logged explicitly and cannot roll back an already committed action. A production compliance requirement for atomic audit/business commit needs a transactional outbox across the legacy API surface.
- Notifications persist own changes, team requests and updates to assigned work. Notification reads are scoped to the authenticated recipient.
- Startup creates unique account/workspace indexes and query indexes. Conflicting legacy data fails index creation and must be reconciled; do not disable invariants to make startup appear healthy. `platform.indexes.enabled=false` is reserved for repository-mocked controller tests.

## Backup and isolated restore drill

Install the recovery dependency with `python -m pip install pymongo`. The application data includes the attachment metadata plus GridFS `fs.files` and `fs.chunks`; all are included by `scripts/platform-backup.py`. BSON preserves exact MongoDB types, and the manifest includes collection options, indexes, document counts and SHA-256 checksums. The script never prints a connection string. Protect the backup because it contains company records and password hashes.

Stop application writers while retaining MongoDB (`docker compose -f scripts/platform-compose.yml stop backend frontend` for the local stack). Set `MONGODB_URI` to the source server URI and `MONGODB_RESTORE_URI` to the destination server URI in the shell. Use a new backup directory and a new destination database name:

```sh
python scripts/platform-backup.py backup --database priyonix --output ../backups/priyonix-2026-10-22 --writers-stopped
python scripts/platform-backup.py restore --database priyonix_recovery_20261022 --input ../backups/priyonix-2026-10-22 --report ../backups/recovery-verification.json
```

The backup reads all collections at one snapshot-session timestamp. The restore rejects system databases, the source database name, any nonempty destination, unsafe backup paths, invalid BSON and failed checksums. It never drops data. A failed restore leaves its isolated destination intact for investigation; use another fresh database name after addressing the error. Restore success means each collection's document count and BSON SHA-256 digest match, collection options match, and index names, ordered keys and functional options (including uniqueness and TTL) match. This verification includes GridFS chunks, so attachment bytes participate in the checksums.

This is an application database recovery utility: it does not back up MongoDB server users/roles, replica-set configuration, environment secrets or an oplog for point-in-time recovery. Views and time-series collections are unsupported. Very large backups can exceed the server's snapshot history window. TTL indexes may legitimately remove expired session/reset documents during a long restore; investigate an exact-checksum failure rather than ignoring it. The files are unencrypted, so access controls and encryption of the backup destination are the operator's responsibility.

Before relying on the recovered database, start an isolated app pointing at it and verify administrator sign-in, a representative CRM record, a posted invoice/payment, a leave approval and an attachment download. Never point the production app at an unreviewed restoration. Revoke outstanding sessions before any real incident recovery cutover. A separately approved restore/cutover plan should set retention, encryption, backup frequency, responsible operator and recovery time/data-loss targets.

## Attachment and role boundaries

Uploads accept PDF, PNG, JPG/JPEG, UTF-8 TXT or CSV up to 5 MB. File content must match the extension; filenames are sanitized and download responses use attachment disposition, `nosniff`, sandbox CSP and `no-store`. Files are linked to an existing, accessible record. An uploader or administrator may delete a file. Remove attachments before deleting their parent record.

Administrators manage organization settings, accounts, finance, HR records, procurement, assets, training and audit/status. Employees collaborate on shared CRM/projects and use ownership-scoped attendance, leave, timesheet and purchase-request records. Hiding a navigation item is not the enforcement boundary: the API verifies roles and record access. Role changes and account disablement affect subsequent JWT requests and revoke stored refresh tokens.
