import { useState } from "react";
import { IconMenu, IconSignOut } from "./Icons";
import { Modal, DataState, Notice } from "./WorkspaceUI";
import { request, useRemote, humanize } from "../workspaceApi";
import { roleLabel } from "../roles";

function SearchDialog({ onClose, onNavigate }) {
  const [input, setInput] = useState(""); const [query, setQuery] = useState("");
  const { data, error, loading, reload } = useRemote(query ? `/api/platform/search?q=${encodeURIComponent(query)}` : null);
  return <Modal title="Search your workspace" onClose={onClose}><form className="workspace-global-search" onSubmit={(event) => { event.preventDefault(); setQuery(input.trim()); }}><label className="sr-only" htmlFor="global-search">Search all records</label><input id="global-search" autoFocus value={input} onChange={(event) => setInput(event.target.value)} placeholder="Search clients, projects, people and more…" minLength={2} required /><button className="dash-button">Search</button></form>
    {query ? <DataState loading={loading} error={error} retry={reload}><div className="workspace-search-results">{data?.length ? data.map((result) => <button key={`${result.type}-${result.id}`} onClick={() => { onNavigate(result.section, result.id); onClose(); }}><span><strong>{result.label}</strong><small>{humanize(result.type)} · Record {result.id}</small></span><span aria-hidden="true">→</span></button>) : <p>No matching records for “{query}”.</p>}</div></DataState> : <p className="workspace-help">Enter at least two characters to find saved records across your permitted modules.</p>}
  </Modal>;
}

function NotificationDialog({ onClose }) {
  const { data, loading, error, reload } = useRemote("/api/platform/notifications"); const [actionError, setActionError] = useState(""); const [busy, setBusy] = useState(null);
  async function markRead(item) {
    setBusy(item.id); setActionError("");
    try { await request(`/api/platform/notifications/${item.id}/read`, { method: "PATCH" }); reload(); }
    catch (failure) { setActionError(failure.message); }
    finally { setBusy(null); }
  }
  return <Modal title="Notifications" onClose={onClose}><Notice error>{actionError}</Notice><DataState loading={loading} error={error} retry={reload}>{data?.length ? <ul className="workspace-notifications">{data.map((item) => <li key={item.id} className={item.read ? "read" : "unread"}><div><strong>{item.title}</strong><p>{item.message}</p><small>{new Date(item.createdAt).toLocaleString()}</small></div>{!item.read && <button className="dash-button secondary compact" disabled={busy === item.id} onClick={() => markRead(item)}>{busy === item.id ? "Saving…" : "Mark read"}</button>}</li>)}</ul> : <div className="workspace-state"><strong>You’re all caught up</strong><p>Updates and activity notifications will appear here.</p></div>}</DataState></Modal>;
}

export default function Header({ activeItem, session, onToggleSidebar, onSignOut, onNavigate }) {
  const [panel, setPanel] = useState(null);
  const initials = (session.email || "U").split("@")[0].split(/[._\-+\s]+/).filter(Boolean).slice(0, 2).map((part) => part[0].toUpperCase()).join("");
  return <>
    <header className="dash-header">
      <button className="dash-menu-button" onClick={onToggleSidebar} aria-label="Open navigation"><IconMenu /></button>
      <div className="dash-header-title"><span className="dash-header-eyebrow">Workspace / {activeItem.groupLabel}</span><h1>{activeItem.label}</h1></div>
      <div className="dash-header-actions"><button className="workspace-search-trigger" onClick={() => setPanel("search")} aria-label="Search workspace"><svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5" /><path d="m16 16 4 4" /></svg><span>Search workspace</span></button>
        <button className="dash-icon-button notification-trigger" title="Notifications" aria-label="Open notifications" onClick={() => setPanel("notifications")}><svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" /></svg></button>
        <div className="dash-user"><span className="dash-avatar" aria-hidden="true">{initials}</span><span className="dash-user-meta"><strong>{session.email}</strong><span>{roleLabel(session.role)}</span></span></div>
        <button className="dash-signout" onClick={onSignOut} title={`Sign out ${session.email}`} aria-label="Sign out"><IconSignOut /><span>Sign out</span></button>
      </div>
    </header>
    {panel === "search" && <SearchDialog onClose={() => setPanel(null)} onNavigate={onNavigate} />}
    {panel === "notifications" && <NotificationDialog onClose={() => setPanel(null)} />}
  </>;
}
