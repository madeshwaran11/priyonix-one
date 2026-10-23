import { useEffect, useState } from "react";
import { apiFetch } from "../api";
import { canonicalRole } from "../roles";
import { request, useRemote, queryString, exportRows, saveBlob, humanize } from "../workspaceApi";
import { Modal, DataState, PageHeading, Pagination, Notice } from "../components/WorkspaceUI";
import { IconRefresh } from "../components/Icons";
import { useRecordDialog } from "../useRecordDialog";

function RecordForm({ schema, record, lookups, lookupError, onClose, onSaved }) {
  const [values, setValues] = useState(() => Object.fromEntries(schema.fields.map((field) => [field.key, record?.values?.[field.key] ?? ""])));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError(""); setFieldErrors({});
    const payloadValues = Object.fromEntries(schema.fields.filter((field) => !field.readOnly).map((field) => [field.key,
      field.type === "number" && values[field.key] !== "" ? Number(values[field.key]) : values[field.key],
    ]));
    try {
      await request(`/api/workspace/${schema.key}${record ? `/${record.id}` : ""}`, {
        method: record ? "PUT" : "POST", payload: { values: payloadValues, ...(record ? { version: record.version } : {}) },
      });
      onSaved(record ? "Record updated." : "Record created.");
    } catch (failure) { setError(failure.message); setFieldErrors(failure.fieldErrors || {}); }
    finally { setBusy(false); }
  }
  return <Modal title={`${record ? "Edit record" : "Create record"} · ${schema.label}`} onClose={onClose} busy={busy}>
    <form className="dash-form" onSubmit={submit}>
      <p className="workspace-help">Required fields are marked with *. {record ? "Changes apply to this record." : `New records start as ${humanize(schema.initialState)}.`}</p>
      <Notice error>{lookupError}</Notice>
      <div className="dash-form-grid">{schema.fields.filter((field) => !field.readOnly).map((field) => {
        const common = { id: `field-${field.key}`, name: field.key, value: values[field.key], required: field.required, disabled: busy,
          "aria-invalid": Boolean(fieldErrors[field.key]), "aria-describedby": fieldErrors[field.key] ? `error-${field.key}` : undefined,
          onChange: (event) => setValues((previous) => ({ ...previous, [field.key]: event.target.value })) };
        return <label key={field.key} className={`dash-field ${field.type === "textarea" ? "full-width" : ""}`} htmlFor={common.id}>
          <span>{field.label}{field.required ? " *" : ""}</span>
          {field.type === "textarea" ? <textarea {...common} rows={3} maxLength={5000} /> :
            field.type === "reference" ? <select {...common}><option value="">Select {field.label.toLowerCase()}</option>{(lookups[field.reference] || []).map((option) => <option key={option.id} value={option.id}>{option.label}</option>)}</select> :
              field.type === "select" ? <select {...common}><option value="">Select {field.label.toLowerCase()}</option>{(field.options || []).map((option) => <option key={option} value={option}>{humanize(option)}</option>)}</select> :
                <input {...common} type={field.type === "number" ? "number" : field.type === "date" ? "date" : field.type === "email" ? "email" : "text"} step={field.type === "number" ? "any" : undefined} maxLength={field.type === "text" ? 500 : undefined} />}
          {fieldErrors[field.key] && <em id={`error-${field.key}`} className="dash-field-error">{fieldErrors[field.key]}</em>}
          {field.type === "reference" && lookups[field.reference]?.length === 0 && <small>No {humanize(field.reference).toLowerCase()} available. Create the related record first.</small>}
        </label>;
      })}</div>
      <Notice error>{error}</Notice>
      <div className="dash-form-actions"><button className="dash-button secondary" type="button" onClick={onClose} disabled={busy}>Cancel</button><button className="dash-button" disabled={busy || Boolean(lookupError)}>{busy ? "Saving…" : record ? "Save changes" : "Create record"}</button></div>
    </form>
  </Modal>;
}

function Attachments({ module, recordId, canDelete }) {
  const path = `/api/platform/attachments?${queryString({ module, recordId })}`;
  const { data, loading, error, reload } = useRemote(path);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [pendingDelete, setPendingDelete] = useState(null);
  async function upload(event) {
    const file = event.target.files?.[0]; if (!file) return;
    event.target.value = ""; setBusy(true); setMessage("");
    try {
      const body = new FormData(); body.append("file", file);
      await request(path, { method: "POST", body }); reload();
    } catch (failure) { setMessage(failure.message); }
    finally { setBusy(false); }
  }
  async function download(file) {
    setBusy(true); setMessage("");
    try {
      const response = await apiFetch(`/api/platform/attachments/${file.id}/download`);
      if (!response.ok) throw new Error("The attachment could not be downloaded.");
      saveBlob(await response.blob(), file.filename);
    } catch (failure) { setMessage(failure.message); }
    finally { setBusy(false); }
  }
  async function remove() {
    setBusy(true); setMessage("");
    try { await request(`/api/platform/attachments/${pendingDelete.id}`, { method: "DELETE" }); setPendingDelete(null); reload(); }
    catch (failure) { setMessage(failure.message); }
    finally { setBusy(false); }
  }
  return <section className="workspace-attachments"><div className="workspace-section-heading"><h4>Attachments</h4><label className="dash-button secondary compact attachment-upload">{busy ? "Working…" : "Upload file"}<input type="file" disabled={busy} onChange={upload} /></label></div>
    <p className="workspace-help">Attach supporting documents to this record.</p><Notice error>{message}</Notice>
    <DataState loading={loading} error={error} retry={reload}>
      {data?.length ? <ul className="attachment-list">{data.map((file) => <li key={file.id}><div><strong>{file.filename}</strong><small>{Math.max(1, Math.round(file.size / 1024))} KB · {new Date(file.createdAt).toLocaleDateString()}</small></div><div className="workspace-actions"><button className="dash-button secondary compact" disabled={busy} onClick={() => download(file)}>Download</button>{canDelete && <button className="dash-button danger compact" disabled={busy} onClick={() => setPendingDelete(file)}>Delete</button>}</div></li>)}</ul> : <p className="workspace-help">No attachments yet.</p>}
    </DataState>
    {pendingDelete && <div className="workspace-inline-confirm" role="alert"><p>Delete <strong>{pendingDelete.filename}</strong> permanently?</p><div className="workspace-actions"><button className="dash-button secondary compact" disabled={busy} onClick={() => setPendingDelete(null)}>Cancel</button><button className="dash-button danger compact" disabled={busy} onClick={remove}>Delete file</button></div></div>}
  </section>;
}

export default function WorkspacePage({ schema, session }) {
  const isAdmin = canonicalRole(session.role) === "ADMIN";
  const canEdit = (record) => ["DRAFT", "REJECTED", "PLANNED", "RECORDED"].includes(record.status)
    || ["contacts", "vendors", "courses", "departments"].includes(schema.key)
    || (schema.key === "opportunities" && !["WON", "LOST"].includes(record.status))
    || (schema.key === "assets" && record.status === "AVAILABLE")
    || (schema.key === "students" && record.status === "ENROLLED")
    || (schema.key === "placements" && !["PLACED", "REJECTED"].includes(record.status));
  const [search, setSearch] = useState("");
  const [submittedSearch, setSubmittedSearch] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const { dialog, setDialog, detailError, detailLoading } = useRecordDialog(`/api/workspace/${schema.key}`, "record");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [actionError, setActionError] = useState("");
  const [lookups, setLookups] = useState({});
  const [lookupError, setLookupError] = useState("");
  const [exporting, setExporting] = useState(false);
  const base = `/api/workspace/${schema.key}`;
  const { data, error, loading, refreshing, reload } = useRemote(`${base}?${queryString({ search: submittedSearch, status, page, size })}`);
  useEffect(() => {
    let cancelled = false;
    const refs = [...new Set(schema.fields.filter((field) => field.reference).map((field) => field.reference))];
    Promise.all(refs.map(async (ref) => [ref, await request(`/api/workspace/lookups/${ref}`)]))
      .then((pairs) => { if (!cancelled) { setLookups(Object.fromEntries(pairs)); setLookupError(""); } })
      .catch((failure) => { if (!cancelled) setLookupError(`Related records could not be loaded. ${failure.message}`); });
    return () => { cancelled = true; };
  }, [schema]);
  const fieldByKey = Object.fromEntries(schema.fields.map((field) => [field.key, field]));
  function display(record, key) {
    const value = record.values?.[key]; const field = fieldByKey[key];
    if (value == null || value === "") return "—";
    if (field?.reference) return lookups[field.reference]?.find((entry) => String(entry.id) === String(value))?.label || String(value);
    if (field?.type === "select") return humanize(value);
    if (typeof value === "number") return value.toLocaleString(undefined, { maximumFractionDigits: 2 });
    return String(value);
  }
  function saved(text) { setDialog(null); setMessage(text); setActionError(""); reload(); }
  function open(mode, record) { setActionError(""); setDialog({ mode, record }); }
  async function act(action, record, nextStatus) {
    setBusy(true); setActionError("");
    try {
      await request(`${base}/${record.id}${action === "status" ? "/status" : `?version=${record.version}`}`, {
        method: action === "status" ? "PATCH" : "DELETE", ...(action === "status" ? { payload: { status: nextStatus, version: record.version } } : {}),
      });
      saved(action === "status" ? `Status changed to ${humanize(nextStatus)}.` : "Record deleted.");
    } catch (failure) { setActionError(failure.message); }
    finally { setBusy(false); }
  }
  async function exportAll() {
    setExporting(true); setActionError("");
    try {
      const rows = []; let current = 1; let totalPages = 1;
      do {
        const result = await request(`${base}?${queryString({ search: submittedSearch, status, page: current, size: 100 })}`);
        rows.push(...result.items); totalPages = result.totalPages; current++;
      } while (current <= totalPages);
      exportRows(rows, [{ label: "Record ID", value: (record) => record.id }, ...schema.columns.map((key) => ({ label: fieldByKey[key]?.label || humanize(key), value: (record) => display(record, key) })), { label: "Status", value: (record) => record.status }], `${schema.key}.csv`);
      setMessage(`Exported ${rows.length} record${rows.length === 1 ? "" : "s"}.`);
    } catch (failure) { setActionError(failure.message); }
    finally { setExporting(false); }
  }
  const transitions = dialog?.record ? (schema.transitions || []).filter((transition) => transition.from === dialog.record.status && (!transition.adminOnly || isAdmin)) : [];
  return <div className="dash-page workspace-page">
    <PageHeading title={schema.label} description={schema.description}><button className="dash-button secondary" onClick={exportAll} disabled={exporting || loading}>{exporting ? "Exporting…" : "Export CSV"}</button><button className="dash-button" onClick={() => open("form", null)}>+ New record</button></PageHeading>
    <Notice onDismiss={() => setMessage("")}>{message}</Notice>{!dialog && <Notice error onDismiss={() => setActionError("")}>{actionError}</Notice>}
    <Notice error>{detailError}</Notice>
    {detailLoading && <p className="workspace-help" role="status">Loading requested record…</p>}
    <section className="workspace-table-panel" aria-label={`${schema.label} records`}>
      <div className="workspace-toolbar"><form className="workspace-search" onSubmit={(event) => { event.preventDefault(); setSubmittedSearch(search.trim()); setPage(1); }}><label className="sr-only" htmlFor="module-search">Search {schema.label.toLowerCase()}</label><input id="module-search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder={`Search ${schema.label.toLowerCase()}…`} /><button className="dash-button secondary compact">Search</button></form>
        <label className="workspace-filter">Status<select value={status} onChange={(event) => { setStatus(event.target.value); setPage(1); }}><option value="">All statuses</option>{schema.states.map((state) => <option key={state}>{state}</option>)}</select></label>
        <button className="dash-icon-button" title={refreshing ? "Refreshing records" : "Refresh records"} aria-label={refreshing ? "Refreshing records" : "Refresh records"} aria-busy={refreshing} disabled={refreshing} onClick={reload}><IconRefresh /></button>
        <span className="sr-only" role="status" aria-live="polite">{refreshing ? "Refreshing records while keeping the current list visible." : ""}</span>
      </div>
      <DataState loading={loading} error={error} retry={reload} empty={data?.items?.length === 0}>
        <div className="workspace-table-scroll"><table className="workspace-table"><thead><tr>{schema.columns.map((key) => <th scope="col" key={key}>{fieldByKey[key]?.label || humanize(key)}</th>)}<th scope="col">Status</th><th scope="col" className="actions-column">Actions</th></tr></thead><tbody>{data?.items.map((record) => <tr key={record.id}>
          {schema.columns.map((key, index) => <td key={key}>{index === 0 ? <button className="workspace-link" onClick={() => open("detail", record)}>{display(record, key)}</button> : display(record, key)}</td>)}
          <td><span className={`workspace-badge status-${record.status?.toLowerCase()}`}>{humanize(record.status)}</span></td>
          <td><div className="workspace-row-actions"><button onClick={() => open("detail", record)}>View</button>{canEdit(record) && <button onClick={() => open("form", record)}>Edit</button>}{isAdmin && canEdit(record) && <button className="danger-text" onClick={() => open("delete", record)}>Delete</button>}</div></td>
        </tr>)}</tbody></table></div>
      </DataState>
      <Pagination page={page} totalPages={data?.totalPages} total={data?.total || 0} size={size} onPage={setPage} onSize={(value) => { setSize(value); setPage(1); }} />
    </section>
    {dialog?.mode === "form" && <RecordForm schema={schema} record={dialog.record} lookups={lookups} lookupError={lookupError} onClose={() => setDialog(null)} onSaved={saved} />}
    {dialog?.mode === "detail" && <Modal title={`${schema.label} · ${display(dialog.record, schema.columns[0])}`} onClose={() => !busy && setDialog(null)} busy={busy}>
      <div className="workspace-detail-meta"><span className="workspace-badge">{humanize(dialog.record.status)}</span><span>Updated {new Date(dialog.record.updatedAt).toLocaleString()}</span></div>
      <dl className="workspace-detail-grid">{[...new Set([...schema.fields.map((field) => field.key), ...schema.columns])].map((key) => <div key={key}><dt>{fieldByKey[key]?.label || humanize(key)}</dt><dd>{display(dialog.record, key)}</dd></div>)}</dl>
      <Notice error>{actionError}</Notice>
      <div className="workspace-actions workspace-lifecycle">{canEdit(dialog.record) && <button className="dash-button secondary" onClick={() => open("form", dialog.record)} disabled={busy}>Edit record</button>}{transitions.map((transition) => <button key={`${transition.from}-${transition.to}`} className="dash-button" disabled={busy} onClick={() => act("status", dialog.record, transition.to)}>{busy ? "Updating…" : transition.label}</button>)}</div>
      <Attachments module={schema.key} recordId={dialog.record.id} canDelete={isAdmin || dialog.record.createdBy === session.email} />
    </Modal>}
    {dialog?.mode === "delete" && <Modal title="Delete this record?" onClose={() => !busy && setDialog(null)} busy={busy} wide={false}><p>This permanently removes the {schema.label.toLowerCase()} record. Related records may prevent deletion.</p><Notice error>{actionError}</Notice><div className="dash-form-actions"><button className="dash-button secondary" disabled={busy} onClick={() => setDialog(null)}>Cancel</button><button className="dash-button danger" disabled={busy} onClick={() => act("delete", dialog.record)}>{busy ? "Deleting…" : "Delete record"}</button></div></Modal>}
  </div>;
}
