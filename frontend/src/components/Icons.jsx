// Lightweight inline SVG icons — no extra dependencies.

const baseProps = {
  width: 18,
  height: 18,
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.8,
  strokeLinecap: "round",
  strokeLinejoin: "round",
  "aria-hidden": true,
};

export function IconChart(props) {
  return (
    <svg {...baseProps} {...props}>
      <path d="M3 3v18h18" />
      <path d="M7 15l4-5 3 3 5-7" />
    </svg>
  );
}

export function IconUsers(props) {
  return (
    <svg {...baseProps} {...props}>
      <circle cx="9" cy="8" r="3.2" />
      <path d="M3.5 19a5.5 5.5 0 0 1 11 0" />
      <path d="M16 5.5a3.2 3.2 0 0 1 0 6" />
      <path d="M17.5 13.6A5.5 5.5 0 0 1 20.5 19" />
    </svg>
  );
}

export function IconProjects(props) {
  return (
    <svg {...baseProps} {...props}>
      <rect x="3" y="4" width="18" height="16" rx="2.5" />
      <path d="M3 9h18" />
      <path d="M7.5 13.5l2 2 3.5-4" />
    </svg>
  );
}

export function IconTasks(props) {
  return (
    <svg {...baseProps} {...props}>
      <rect x="3" y="4" width="18" height="16" rx="2.5" />
      <path d="M7 9.5l1.6 1.6L12 8" />
      <path d="M14 9.5h4" />
      <path d="M7 16h4" />
      <path d="M14 16h4" />
    </svg>
  );
}

export function IconClients(props) {
  return (
    <svg {...baseProps} {...props}>
      <path d="M4 20v-1.5A4.5 4.5 0 0 1 8.5 14h3A4.5 4.5 0 0 1 16 18.5V20" />
      <circle cx="10" cy="8" r="3.4" />
      <path d="M17 11h4M19 9v4" />
    </svg>
  );
}

export function IconLeads(props) {
  return (
    <svg {...baseProps} {...props}>
      <path d="M3 5h18l-7 8v6l-4-2v-4z" />
    </svg>
  );
}

export function IconFollowUp(props) {
  return (
    <svg {...baseProps} {...props}>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7.5V12l3.2 2" />
    </svg>
  );
}

export function IconFinance(props) {
  return (
    <svg {...baseProps} {...props}>
      <rect x="3" y="6" width="18" height="12" rx="2.5" />
      <circle cx="12" cy="12" r="2.6" />
      <path d="M7 10v4M17 10v4" />
    </svg>
  );
}

export function IconMarketing(props) {
  return (
    <svg {...baseProps} {...props}>
      <path d="M4 10v4a1 1 0 0 0 1 1h2l7 4V5L7 9H5a1 1 0 0 0-1 1z" />
      <path d="M18 9a4 4 0 0 1 0 6" />
    </svg>
  );
}

export function IconReports(props) {
  return (
    <svg {...baseProps} {...props}>
      <path d="M6 3h9l4 4v14H6z" />
      <path d="M14 3v5h5" />
      <path d="M9.5 17v-3M12 17v-5M14.5 17v-2" />
    </svg>
  );
}

export function IconSignOut(props) {
  return (
    <svg {...baseProps} {...props}>
      <path d="M14 4h4a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-4" />
      <path d="M10 8l-4 4 4 4" />
      <path d="M6 12h9" />
    </svg>
  );
}

export function IconMenu(props) {
  return (
    <svg {...baseProps} {...props}>
      <path d="M4 7h16M4 12h16M4 17h16" />
    </svg>
  );
}

export function IconClose(props) {
  return (
    <svg {...baseProps} {...props}>
      <path d="M6 6l12 12M18 6L6 18" />
    </svg>
  );
}

export function IconRefresh(props) {
  return (
    <svg {...baseProps} {...props}>
      <path d="M20 12a8 8 0 1 1-2.3-5.6" />
      <path d="M20 4v4h-4" />
    </svg>
  );
}

export function IconInbox(props) {
  return (
    <svg {...baseProps} {...props}>
      <path d="M4 13l2-8h12l2 8v5a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z" />
      <path d="M4 13h4l1.5 2.5h5L16 13h4" />
    </svg>
  );
}

export function IconInvoice(props) {
  return (
    <svg {...baseProps} {...props}>
      <rect x="3" y="4" width="18" height="16" rx="2.5" />
      <path d="M7 8h10M7 12h6M7 16h4" />
    </svg>
  );
}

export function IconPayment(props) {
  return (
    <svg {...baseProps} {...props}>
      <rect x="3" y="6" width="18" height="12" rx="2.5" />
      <circle cx="12" cy="12" r="2.6" />
      <path d="M7 10v4M17 10v4" />
    </svg>
  );
}

export function IconExpense(props) {
  return (
    <svg {...baseProps} {...props}>
      <rect x="3" y="5" width="18" height="14" rx="2.5" />
      <path d="M7 15l2.6-2.6a1.5 1.5 0 0 1 2.1 0l1.4 1.4a1.5 1.5 0 0 0 2.1 0l2.8-2.8" />
      <circle cx="7" cy="9" r="1.4" />
    </svg>
  );
}

export function IconDownload(props) {
  return (
    <svg {...baseProps} {...props}>
      <path d="M12 5v8M8 9l4 4 4-4M6 17h12" />
    </svg>
  );
}

export function IconPlus(props) {
  return (
    <svg {...baseProps} {...props}>
      <path d="M12 5v14M5 12h14" />
    </svg>
  );
}
