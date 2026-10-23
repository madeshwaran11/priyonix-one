import { useEffect, useState } from "react";
import { fetchFinanceSummary, downloadCsv } from "../api";
import { formatMoney } from "../format";
import StatusPanel from "../components/StatusPanel";
import StatCard from "../components/StatCard";
import BackgroundRefreshStatus from "../components/BackgroundRefreshStatus";
import { useRetainedRefresh } from "../useRetainedRefresh";

export default function FinancialReportsPage({ session }) {
  const token = session.token;
  const [summary, setSummary] = useState(null);
  const [listState, setListState] = useState("loading");
  const [listError, setListError] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [reloadKey, setReloadKey] = useState(0);
  const [exporting, setExporting] = useState(false);
  const { refreshing, beginRefresh, settleRefresh, cancelRefresh } =
    useRetainedRefresh();

  useEffect(() => {
    let cancelled = false;
    fetchFinanceSummary(token, { from, to })
      .then((s) => {
        if (cancelled) return;
        setSummary(s);
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
  }, [token, from, to, reloadKey, settleRefresh]);

  function reload() {
    if (listState === "ready") {
      if (!beginRefresh()) return;
    } else {
      cancelRefresh();
      setListState("loading");
    }
    setListError("");
    setReloadKey((k) => k + 1);
  }

  function changeFrom(value) {
    cancelRefresh();
    setListState("loading");
    setListError("");
    setFrom(value);
  }

  function changeTo(value) {
    cancelRefresh();
    setListState("loading");
    setListError("");
    setTo(value);
  }

  async function exportReport() {
    setExporting(true);
    try {
      const query = [];
      if (from) query.push(`from=${from}`);
      if (to) query.push(`to=${to}`);
      const qs = query.length ? `?${query.join("&")}` : "";
      await downloadCsv(token, `/api/finance/export${qs}`, "finance-report.csv");
    } catch (error) {
      setListError(error.message);
    } finally {
      setExporting(false);
    }
  }

  const monthly = Array.isArray(summary?.monthly) ? summary.monthly : [];

  return (
    <div className="dash-page">
      <section className="dash-hero">
        <div>
          <span className="dash-eyebrow">Finance</span>
          <h2>Financial Reports</h2>
          <p>Live summary with monthly breakdown.</p>
        </div>
        <div className="dash-row-actions">
          <button type="button" className="dash-button ghost" onClick={exportReport} disabled={exporting || listState !== "ready"}>
            {exporting ? "Exporting…" : "Export CSV"}
          </button>
          <button type="button" className="dash-button" onClick={reload} disabled={refreshing || listState === "loading"} aria-busy={refreshing}>{refreshing ? "Refreshing…" : "Refresh"}</button>
        </div>
      </section>

      <div className="dash-toolbar">
        <label className="dash-filter">
          <span>From</span>
          <input type="date" value={from} onChange={(e) => changeFrom(e.target.value)} />
        </label>
        <label className="dash-filter">
          <span>To</span>
          <input type="date" value={to} onChange={(e) => changeTo(e.target.value)} />
        </label>
        <span className="dash-toolbar-count">
          {summary ? `${monthly.length} month(s)` : ""}
        </span>
      </div>

      <BackgroundRefreshStatus refreshing={refreshing} error={listState === "ready" ? listError : ""} label="financial data" />

      {listState === "loading" && <StatusPanel variant="loading" title="Loading financial data…" />}
      {listState === "error" && <StatusPanel variant="error" title="Could not load financial data" description={listError} actionLabel="Retry" onAction={reload} />}

      {listState === "ready" && summary && (
        <>
          <div className="dash-stat-grid">
            <StatCard label="Total invoiced (issued)" value={`₹ ${formatMoney(summary.totalInvoiced)}`} />
            <StatCard label="Collected payments" value={`₹ ${formatMoney(summary.totalCollected)}`} />
            <StatCard label="Outstanding balance" value={`₹ ${formatMoney(summary.totalOutstanding)}`} />
            <StatCard label="Net profit" value={`₹ ${formatMoney(summary.netProfit)}`} />
            <StatCard label="Approved expenses" value={`₹ ${formatMoney(summary.approvedExpenseTotal)}`} />
            <StatCard label="Pending expenses" value={`₹ ${formatMoney(summary.pendingExpenseTotal)}`} />
            <StatCard label="Overdue invoices" value={summary.overdueInvoiceCount} />
            <StatCard label="Paid invoices" value={summary.paidInvoiceCount} />
          </div>

          <section className="dash-section">
            <div className="dash-section-head">
              <h4>Monthly breakdown</h4>
            </div>

            {monthly.length === 0 ? (
              <StatusPanel
                variant="empty"
                title="No monthly data yet"
                description="Invoice, payment and expense activity will appear here once recorded."
              />
            ) : (
              <div className="dash-table-wrap">
                <table className="dash-table">
                  <thead>
                    <tr>
                      <th>Month</th>
                      <th>Invoiced</th>
                      <th>Collected</th>
                      <th>Approved expenses</th>
                      <th>Profit</th>
                      <th>Invoices</th>
                      <th>Payments</th>
                    </tr>
                  </thead>
                  <tbody>
                    {monthly.map((m, i) => (
                      <tr key={i}>
                        <td>{m.month}</td>
                        <td>₹ {formatMoney(m.invoiced)}</td>
                        <td>₹ {formatMoney(m.collected)}</td>
                        <td>₹ {formatMoney(m.expenses)}</td>
                        <td>₹ {formatMoney(m.profit)}</td>
                        <td>{m.invoiceCount}</td>
                        <td>{m.paymentCount}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            <p className="dash-form-hint">{summary.profitDefinition}</p>
          </section>
        </>
      )}
    </div>
  );
}
