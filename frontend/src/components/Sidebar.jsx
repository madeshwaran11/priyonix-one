import { useEffect, useRef } from "react";
import { IconClose } from "./Icons";
import Brand from "./Brand";
import { canonicalRole, roleLabel } from "../roles";

export default function Sidebar({ activeId, isOpen, navigation, onClose, onSelect, session }) {
  const drawer = useRef(null);
  const groups = new Map();
  for (const item of navigation) {
    if (!groups.has(item.group)) groups.set(item.group, item);
  }
  const grouped = [...groups.values()];
  const activeGroup = navigation.find((item) => item.id === activeId)?.group;
  const isAdmin = canonicalRole(session.role) === "ADMIN";
  useEffect(() => {
    if (!isOpen) return;
    const previous = document.activeElement;
    const visibleButtons = () => [...drawer.current.querySelectorAll("button:not(:disabled)")].filter((button) => button.getClientRects().length > 0);
    visibleButtons()[0]?.focus();
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    function handleKey(event) {
      if (event.key === "Escape") onClose();
      if (event.key === "Tab") {
        const elements = visibleButtons();
        const first = elements[0]; const last = elements.at(-1);
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
        if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
      }
    }
    document.addEventListener("keydown", handleKey);
    return () => { document.removeEventListener("keydown", handleKey); document.body.style.overflow = overflow; if (previous?.isConnected) previous.focus(); };
  }, [isOpen, onClose]);
  return <>
    {isOpen && <button className="dash-sidebar-backdrop" onClick={onClose} tabIndex={-1} aria-label="Close navigation" />}
    <aside ref={drawer} className={`dash-sidebar${isOpen ? " is-open" : ""}`} aria-label="Workspace navigation" role={isOpen ? "dialog" : undefined} aria-modal={isOpen ? "true" : undefined}>
      <div className="dash-brand"><Brand compact /><button className="sidebar-close" aria-label="Close navigation" onClick={onClose}><IconClose /></button></div>
      <nav className="dash-nav" aria-label="Dashboard sections"><span className="dash-nav-label">Workspace</span>
        {grouped.map((item) => { const Icon = item.icon; const active = item.group === activeGroup; return <button key={item.group} className={`dash-nav-item${active ? " is-active" : ""}`} aria-current={active ? "page" : undefined} onClick={() => { onSelect(item.id); onClose(); }}><Icon /><span>{!isAdmin && item.employeeGroupLabel ? item.employeeGroupLabel : item.groupLabel}</span>{active && <span className="nav-active-indicator" />}</button>; })}
      </nav>
      <div className="dash-sidebar-footer"><span className="sidebar-account-role">{roleLabel(session.role)} workspace</span><p>{session.email}</p><span className="sidebar-account-note">Your connected business, in one place.</span></div>
    </aside>
  </>;
}
