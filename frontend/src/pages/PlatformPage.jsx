import { useState } from "react";
import { request, useRemote, queryString, humanize } from "../workspaceApi";
import { passwordViolations } from "../passwordPolicy";
import { Modal, DataState, PageHeading, Pagination, Notice } from "../components/WorkspaceUI";
import { IconRefresh } from "../components/Icons";

const ORGANIZATION_FIELDS = [
  ["companyName", "Company name", "text", true], ["legalName", "Legal name", "text", true],
  ["email", "Contact email", "email"], ["phone", "Contact phone", "tel"], ["address", "Address", "textarea"],
  ["currency", "Currency (ISO code)", "text", true], ["timezone", "Time zone", "text", true], ["fiscalYearStart", "Fiscal year start", "number", true],
];

function OrganizationForm({ organization, onSaved }) {
  const [form, setForm] = useState(organization);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError("");
    try { await request("/api/platform/organization", { method: "PUT", payload: { ...form, fiscalYearStart: Number(form.fiscalYearStart) } }); onSaved("Organization settings saved."); }
    catch (failure) { setError(failure.message); }
    finally { setBusy(false); }
  }
  return <form className="dash-form workspace-settings-form" onSubmit={submit}>
    <div className="dash-form-grid">{ORGANIZATION_FIELDS.map(([key, label, type, required]) => <label key={key} className={`dash-field ${type === "textarea" ? "full-width" : ""}`}><span>{label}{required ? " *" : ""}</span>
      {type === "textarea" ? <textarea rows={3} value={form[key] ?? ""} disabled={busy} onChange={(event) => setForm({ ...form, [key]: event.target.value })} /> : <input type={type} required={required} value={form[key] ?? ""} disabled={busy} min={type === "number" ? 1 : undefined} max={type === "number" ? 12 : undefined} maxLength={key === "currency" ? 3 : 250} onChange={(event) => setForm({ ...form, [key]: event.target.value })} />}
      {key === "fiscalYearStart" && <small>Month number: January = 1, April = 4, December = 12.</small>}{key === "timezone" && <small>Use an IANA time zone, for example Asia/Kolkata.</small>}
    </label>)}</div><Notice error>{error}</Notice><div className="dash-form-actions"><button className="dash-button" disabled={busy}>{busy ? "Saving…" : "Save organization"}</button></div>
  </form>;
}

function UserForm({ user, session, onClose, onSaved }) {
  const [form, setForm] = useState(user || { email: "", password: "", role: "EMPLOYEE", enabled: true });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function submit(event) {
    event.preventDefault();
    if (!user) { const violations = passwordViolations(form.password); if (violations.length) { setError(`Password needs: ${violations.join(", ").toLowerCase()}.`); return; } }
    setBusy(true); setError("");
    try { await request(`/api/platform/users${user ? `/${user.id}` : ""}`, { method: user ? "PATCH" : "POST", payload: user ? { role: form.role, enabled: form.enabled } : { email: form.email, password: form.password, role: form.role } }); onSaved(user ? "Account updated." : "Account created."); }
    catch (failure) { setError(failure.message); }
    finally { setBusy(false); }
  }
  const self = user?.email === session.email;
  return <Modal title={user ? "Manage account" : "Create account"} onClose={onClose} busy={busy} wide={false}><form className="dash-form" onSubmit={submit}>
    <label className="dash-field"><span>Email *</span><input type="email" autoComplete="off" value={form.email} required disabled={busy || Boolean(user)} onChange={(event) => setForm({ ...form, email: event.target.value })} /></label>
    {!user && <label className="dash-field"><span>Temporary password *</span><input type="password" autoComplete="new-password" value={form.password} required minLength={12} maxLength={128} disabled={busy} onChange={(event) => setForm({ ...form, password: event.target.value })} /><small>At least 12 characters with uppercase, lowercase, number and symbol. No spaces.</small></label>}
    <label className="dash-field"><span>Role</span><select value={form.role} disabled={busy || self} onChange={(event) => setForm({ ...form, role: event.target.value })}><option value="EMPLOYEE">Employee</option><option value="ADMIN">Administrator</option></select></label>
    {user && <label className="workspace-checkbox"><input type="checkbox" checked={form.enabled} disabled={busy || self} onChange={(event) => setForm({ ...form, enabled: event.target.checked })} />Account enabled</label>}
    {self && <p className="workspace-help">Your current administrator account cannot be disabled or demoted here.</p>}
    <Notice error>{error}</Notice><div className="dash-form-actions"><button type="button" className="dash-button secondary" disabled={busy} onClick={onClose}>Cancel</button><button className="dash-button" disabled={busy}>{busy ? "Saving…" : user ? "Save account" : "Create account"}</button></div>
  </form></Modal>;
}

function UsersPage({ session }) {
  const { data, loading, error, reload } = useRemote("/api/platform/users");
  const [search, setSearch] = useState("");
  const [dialog, setDialog] = useState(null);
  const [message, setMessage] = useState("");
  const users = data?.filter((user) => `${user.email} ${user.role}`.toLowerCase().includes(search.toLowerCase())) || [];
  return <div className="dash-page"><PageHeading title="User accounts" description="Manage access, roles and account availability."><button className="dash-button" onClick={() => setDialog({})}>+ Create account</button></PageHeading><Notice onDismiss={() => setMessage("")}>{message}</Notice>
    <section className="workspace-table-panel"><div className="workspace-toolbar"><label className="workspace-search"><span className="sr-only">Search accounts</span><input placeholder="Search accounts…" value={search} onChange={(event) => setSearch(event.target.value)} /></label><button className="dash-icon-button" aria-label="Refresh accounts" onClick={reload}><IconRefresh /></button></div>
      <DataState loading={loading} error={error} retry={reload} empty={users.length === 0}><div className="workspace-table-scroll"><table className="workspace-table"><thead><tr><th>Email</th><th>Role</th><th>Status</th><th>Actions</th></tr></thead><tbody>{users.map((user) => <tr key={user.id}><td><strong>{user.email}</strong>{user.email === session.email && <small className="dash-cell-sub">Your account</small>}</td><td>{humanize(user.role)}</td><td><span className={`workspace-badge ${user.enabled ? "status-active" : "status-inactive"}`}>{user.enabled ? "Enabled" : "Disabled"}</span></td><td><button className="workspace-link" onClick={() => setDialog({ user })}>Manage access</button></td></tr>)}</tbody></table></div></DataState>
      <div className="workspace-pagination">{users.length} account{users.length === 1 ? "" : "s"}</div>
    </section>{dialog && <UserForm user={dialog.user} session={session} onClose={() => setDialog(null)} onSaved={(text) => { setMessage(text); setDialog(null); reload(); }} />}
  </div>;
}

function AuditPage() {
  const [search, setSearch] = useState(""); const [query, setQuery] = useState(""); const [page, setPage] = useState(1);
  const { data, loading, error, reload } = useRemote(`/api/platform/audit?${queryString({ page, size: 25, search: query })}`);
  return <div className="dash-page"><PageHeading title="Audit trail" description="Review saved account, configuration and business record changes." />
    <section className="workspace-table-panel"><form className="workspace-toolbar" onSubmit={(event) => { event.preventDefault(); setQuery(search.trim()); setPage(1); }}><label className="workspace-search"><span className="sr-only">Search audit events</span><input value={search} placeholder="Search actor, action or resource…" onChange={(event) => setSearch(event.target.value)} /></label><button className="dash-button secondary">Search</button><button type="button" className="dash-icon-button" aria-label="Refresh audit trail" onClick={reload}><IconRefresh /></button></form>
      <DataState loading={loading} error={error} retry={reload} empty={data?.items?.length === 0}><div className="workspace-table-scroll"><table className="workspace-table"><thead><tr><th>When</th><th>Actor</th><th>Action</th><th>Resource</th></tr></thead><tbody>{data?.items?.map((event) => <tr key={event.id}><td>{new Date(event.createdAt).toLocaleString()}</td><td>{event.actor}</td><td><span className="workspace-badge">{humanize(event.action)}</span></td><td>{event.resource}</td></tr>)}</tbody></table></div></DataState>
      <Pagination page={page} totalPages={data?.totalPages} total={data?.total || 0} onPage={setPage} />
    </section>
  </div>;
}

function SystemPage() {
  const { data, loading, error, reload } = useRemote("/api/platform/status");
  return <div className="dash-page"><PageHeading title="System status" description="Current application and database connectivity."><button className="dash-button secondary" onClick={reload}><IconRefresh />Refresh status</button></PageHeading>
    <DataState loading={loading} error={error} retry={reload}>{data && <><section className="workspace-system-health"><div><span className={`health-dot ${data.status === "UP" ? "up" : "down"}`} /><div><h3>Application {data.status === "UP" ? "operational" : "unavailable"}</h3><p>Database: {data.database} · Uptime: {Math.floor(data.uptimeSeconds / 3600)}h {Math.floor((data.uptimeSeconds % 3600) / 60)}m</p></div></div></section><section className="workspace-table-panel"><div className="workspace-table-scroll"><table className="workspace-table"><thead><tr><th>Resource</th><th>Saved records</th></tr></thead><tbody>{Object.entries(data.counts || {}).map(([key, value]) => <tr key={key}><td>{humanize(key)}</td><td>{Number(value).toLocaleString()}</td></tr>)}</tbody></table></div></section></>}</DataState>
  </div>;
}

function PermissionsPage() {
  const { data, loading, error, reload } = useRemote("/api/platform/permissions");
  return <div className="dash-page"><PageHeading title="Roles & permissions" description="The server enforces these permissions for every request." /><DataState loading={loading} error={error} retry={reload}><div className="workspace-permissions">{data?.map((role) => <section className="workspace-table-panel" key={role.role}><h3>{humanize(role.role)}</h3><ul>{role.permissions.map((permission) => <li key={permission}>{permission}</li>)}</ul></section>)}</div></DataState></div>;
}

function OrganizationPage() {
  const { data, loading, error, reload } = useRemote("/api/platform/organization"); const [message, setMessage] = useState("");
  return <div className="dash-page"><PageHeading title="Organization" description="Set your business identity and operating preferences." /><Notice onDismiss={() => setMessage("")}>{message}</Notice><DataState loading={loading} error={error} retry={reload}>{data && <section className="workspace-table-panel"><OrganizationForm organization={data} onSaved={(text) => { setMessage(text); reload(); }} /></section>}</DataState></div>;
}

export default function PlatformPage({ section, session }) {
  if (section === "users") return <UsersPage session={session} />;
  if (section === "audit") return <AuditPage />;
  if (section === "system") return <SystemPage />;
  if (section === "permissions") return <PermissionsPage />;
  return <OrganizationPage />;
}
