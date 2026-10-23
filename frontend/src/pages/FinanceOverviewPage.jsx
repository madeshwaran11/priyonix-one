import { useEffect, useState } from "react";
import { fetchFinanceSummary, listInvoices } from "../api";
import { formatDate, formatMoney } from "../format";
import StatusPanel from "../components/StatusPanel";
import StatCard from "../components/StatCard";
import BackgroundRefreshStatus from "../components/BackgroundRefreshStatus";
import { useRetainedRefresh } from "../useRetainedRefresh";

// Quick links back into the individual Finance & Billing screens. The ids
// match the dashboard navigation ids handled in Dashboard.jsx.
const FINANCE_LINKS = [
  { id: "invoices", label: "Invoices" },
  { id: "payments", label: "Payments" },
  { id: "expenses", label: "Expenses" },
  { id: "finance-reports", label: "Financial reports" },
];

export default function FinanceOverviewPage({ session, onNavigate }) {
  const token = session.token;
  const [summary, setSummary] = useState(null);
  const [invoices, setInvoices] = useState([]);
  const [listState, setListState] = useState("loading");
  const [listError, setListError] = useState("");
  const [reloadKey, setReloadKey] = useState(0);
  const { refreshing, beginRefresh, settleRefresh, cancelRefresh } =
    useRetainedRefresh();

  useEffect(() => {
    let cancelled = false;
    Promise.all([
      fetchFinanceSummary(token),
      listInvoices(token, { page: 1, size: 5 }),
    ])
      .then(([s, inv]) => {
        if (cancelled) return;
        setSummary(s);
        setInvoices(inv.items || []);
        setListError("");
        settleRefresh();
        setListState("ready");
      })
      .catch((error) => {
        if (cancelled) return;
        setListError(error.code === "UNAUTHORIZED" ? "Session expired." : error.message);
        setListState(settleRefresh() ? "ready" : "error");
      });
    return () => {
      cancelled = true;
    };
  }, [token, reloadKey, settleRefresh]);

  function reload() {
    if (listState === "ready") {
      if (!beginRefresh()) return;
    } else {
      cancelRefresh();
      setListState("loading");
    }
    setListError("");
    setReloadKey((key) => key + 1);
  }

  return (
    <div className="dash-page">
      <section className="dash-hero">
        <div>
          <span className="dash-eyebrow">Finance</span>
          <h2>Finance Overview</h2>
          <p>Snapshot of revenue, collections and expenses.</p>
        </div>
        <button type="button" className="dash-button" onClick={reload} disabled={refreshing || listState === "loading"} aria-busy={refreshing}>
          {refreshing ? "Refreshing…" : "Refresh"}
        </button>
      </section>

      <BackgroundRefreshStatus refreshing={refreshing} error={listState === "ready" ? listError : ""} label="finance overview" />

      {onNavigate && (
        <section className="dash-section">
          <div className="dash-section-head">
            <h4>Manage finance</h4>
          </div>
          <div className="dash-row-actions">
            {FINANCE_LINKS.map((link) => (
              <button
                key={link.id}
                type="button"
                className="dash-button ghost"
                onClick={() => onNavigate(link.id)}
              >
                {link.label}
              </button>
            ))}
          </div>
        </section>
      )}

      {listState === "loading" && <StatusPanel variant="loading" title="Loading finance data…" />}
      {listState === "error" && (
        <StatusPanel
          variant="error"
          title="Could not load finance data"
          description={listError}
          actionLabel="Retry"
          onAction={reload}
        />
      )}

      {listState === "ready" && summary && (
        <>
          <div className="dash-stat-grid">
            <StatCard label="Total invoiced (issued)" value={`₹ ${formatMoney(summary.totalInvoiced)}`} />
            <StatCard label="Collected payments" value={`₹ ${formatMoney(summary.totalCollected)}`} />
            <StatCard label="Outstanding balance" value={`₹ ${formatMoney(summary.totalOutstanding)}`} />
            <StatCard label="Net profit" value={`₹ ${formatMoney(summary.netProfit)}`} />
            <StatCard label="Approved expenses" value={`₹ ${formatMoney(summary.approvedExpenseTotal)}`} />
            <StatCard label="Overdue invoices" value={summary.overdueInvoiceCount} />
          </div>

          <section className="dash-section">
            <div className="dash-section-head">
              <h4>Recent invoices</h4>
            </div>

            {invoices.length === 0 ? (
              <StatusPanel
                variant="empty"
                title="No invoices yet"
                description="Invoices you create will appear here with their balance and status."
              />
            ) : (
              <div className="dash-table-wrap">
                <table className="dash-table">
                  <thead>
                    <tr>
                      <th>Invoice</th>
                      <th>Client</th>
                      <th>Issue</th>
                      <th>Due</th>
                      <th>Total</th>
                      <th>Balance</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {invoices.map((inv) => (
                      <tr key={inv.id}>
                        <td>{inv.invoiceNumber}</td>
                        <td>{inv.clientName || "—"}</td>
                        <td>{formatDate(inv.issueDate)}</td>
                        <td>{formatDate(inv.dueDate)}</td>
                        <td>₹ {formatMoney(inv.total)}</td>
                        <td>₹ {formatMoney(inv.balance)}</td>
                        <td>{inv.status}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </>
      )}
    </div>
  );
}
