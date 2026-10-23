import { useEffect, useId, useRef } from "react";
import { IconClose, IconProjects, IconRefresh } from "./Icons";
import BrandLoader from "./BrandLoader";

export function Modal({ title, children, onClose, busy = false, wide = true }) {
  const ref = useRef(null);
  const headingId = useId();
  useEffect(() => {
    const dialog = ref.current;
    const previousFocus = document.activeElement;
    dialog.showModal();
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      dialog.close();
      document.body.style.overflow = overflow;
      if (previousFocus?.isConnected) previousFocus.focus();
    };
  }, []);
  return <dialog ref={ref} className={`workspace-dialog ${wide ? "wide" : ""}`} aria-labelledby={headingId}
    onCancel={(event) => { event.preventDefault(); if (!busy) onClose(); }}
    onClick={(event) => { if (event.target === event.currentTarget && !busy) onClose(); }}>
    <div className="workspace-dialog-inner">
      <div className="dash-modal-head"><h3 id={headingId}>{title}</h3><button type="button" className="dash-icon-button" aria-label="Close dialog" onClick={onClose} disabled={busy}><IconClose /></button></div>
      {children}
    </div>
  </dialog>;
}

export function DataState({ loading, error, retry, empty, children }) {
  if (loading) return <div className="workspace-loading" aria-busy="true"><BrandLoader fullPage={false} detail="" label="Loading records…" /><div className="workspace-skeletons" aria-hidden="true">{[0, 1, 2, 3].map((row) => <div className="workspace-skeleton-row" key={row}><span /><span /><span /></div>)}</div></div>;
  if (error) return <div className="workspace-state" role="alert"><span className="workspace-state-icon error"><IconRefresh /></span><strong>Could not load this view</strong><p>{error.message}</p>{retry && <button type="button" className="dash-button secondary" onClick={retry}><IconRefresh />Try again</button>}</div>;
  if (empty) return <div className="workspace-state"><span className="workspace-state-icon"><IconProjects /></span><strong>No matching records</strong><p>Create your first record or adjust the filters.</p></div>;
  return children;
}

export function PageHeading({ title, description, children }) {
  return <div className="workspace-heading"><div><h2>{title}</h2><p>{description}</p></div><div className="workspace-actions">{children}</div></div>;
}

export function Pagination({ page, totalPages, total, size, onPage, onSize }) {
  return <div className="workspace-pagination"><span>{total} record{total === 1 ? "" : "s"}</span><div>
    {onSize && <label>Rows <select aria-label="Rows per page" value={size} onChange={(event) => onSize(Number(event.target.value))}>{[10, 25, 50].map((value) => <option key={value}>{value}</option>)}</select></label>}
    <button className="dash-button secondary compact" disabled={page <= 1} onClick={() => onPage(page - 1)}>Previous</button>
    <span>Page {page} of {Math.max(1, totalPages || 0)}</span>
    <button className="dash-button secondary compact" disabled={page >= (totalPages || 1)} onClick={() => onPage(page + 1)}>Next</button>
  </div></div>;
}

export function Notice({ children, error = false, onDismiss }) {
  if (!children) return null;
  return <div className={`workspace-notice ${error ? "error" : "success"}`} role={error ? "alert" : "status"}><span>{children}</span>{onDismiss && <button aria-label="Dismiss message" onClick={onDismiss}><IconClose /></button>}</div>;
}

