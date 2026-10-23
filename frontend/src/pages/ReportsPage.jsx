import { useEffect, useState } from "react";
import {
  downloadCsv,
  fetchDashboardMetrics,
  fetchFinanceSummary,
} from "../api";
import { formatMoney } from "../format";
import StatusPanel from "../components/StatusPanel";
import StatCard from "../components/StatCard";
import BackgroundRefreshStatus from "../components/BackgroundRefreshStatus";
import { useRetainedRefresh } from "../useRetainedRefresh";

// Cross-module report: live delivery/CRM counts from the dashboard metrics
// endpoint plus the finance summary, filtered by the selected date range.
export default function ReportsPage({ session, onNavigate }) {
  const token = session.token;
  const [report, setReport] = useState({ state: "loading", data: null });
  const [error, setError] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [reloadKey, setReloadKey] = useState(0);
  const [exporting, setExporting] = useState(false);
  const { refreshing, beginRefresh, settleRefresh, cancelRefresh } =
    useRetainedRefresh();

  useEffect(() => {
    let cancelled = false;

    Promise.all([
      fetchDashboardMetrics(token),
      fetchFinanceSummary(token, { from, to }),
    ])
      .then(([metrics, finance]) => {
        if (cancelled) return;
        setError("");
        settleRefresh();
        setReport({ state: "ready", data: { metrics, finance } });
      })
      .catch((requestError) => {
        if (cancelled) return;
        setError(
          requestError.code === "UNAUTHORIZED"
            ? "Your session expired. Please sign in again."
            : requestError.message
        );
        if (settleRefresh()) {
          setReport((current) => ({ ...current, state: "ready" }));
        } else {
          setReport({ state: "error", data: null });
        }
      });

    return () => {
      cancelled = true;
    };
  }, [token, from, to, reloadKey, settleRefresh]);

  function reload() {
    if (report.state === "ready") {
      if (!beginRefresh()) return;
    } else {
      cancelRefresh();
      setReport({ state: "loading", data: null });
    }
    setError("");
    setReloadKey((key) => key + 1);
  }

  function changeFrom(value) {
    cancelRefresh();
    setReport({ state: "loading", data: null });
    setError("");
    setFrom(value);
  }

  function changeTo(value) {
    cancelRefresh();
    setReport({ state: "loading", data: null });
    setError("");
    setTo(value);
  }

  async function exportReport() {
    setExporting(true);
    setError("");
    try {
      const query = [];
      if (from) query.push(`from=${from}`);
      if (to) query.push(`to=${to}`);
      const qs = query.length ? `?${query.join("&")}` : "";
      await downloadCsv(token, `/api/finance/export${qs}`, "business-report.csv");
    } catch (exportError) {
      setError(exportError.message);
    } finally {
      setExporting(false);
    }
  }

  const metrics = report.data?.metrics || {};
  const finance = report.data?.finance || {};
  const monthly = Array.isArray(finance.monthly) ? finance.monthly : [];

  return (
    <div className="dash-page">
      <section className="dash-hero">
        <div>
          <span className="dash-eyebrow">Reports</span>
          <h2>Business Reports</h2>
          <p>
            Delivery, CRM, marketing and finance figures combined into one live
            report.
          </p>
        </div>
        <div className="dash-row-actions">
          <button
            type="button"
            className="dash-button ghost"
            onClick={exportReport}
            disabled={exporting || report.state !== "ready"}
          >
            {exporting ? "Exporting…" : "Export CSV"}
          </button>
          <button
            type="button"
            className="dash-button"
            onClick={reload}
            disabled={refreshing || report.state === "loading"}
            aria-busy={refreshing}
          >
            {refreshing ? "Refreshing…" : "Refresh"}
          </button>
        </div>
      </section>

      <div className="dash-toolbar">
        <label className="dash-filter">
          <span>From</span>
          <input
            type="date"
            value={from}
            onChange={(event) => changeFrom(event.target.value)}
          />
        </label>
        <label className="dash-filter">
          <span>To</span>
          <input
            type="date"
            value={to}
            onChange={(event) => changeTo(event.target.value)}
          />
        </label>
        <span className="dash-toolbar-count">
          {report.state === "ready" ? `${monthly.length} month(s)` : ""}
        </span>
      </div>

      <BackgroundRefreshStatus refreshing={refreshing} error={report.state === "ready" ? error : ""} label="business report" />

      {report.state === "loading" && (
        <StatusPanel variant="loading" title="Building your report…" />
      )}

      {report.state === "error" && (
        <StatusPanel
          variant="error"
          title="Could not build the report"
          description={error}
          actionLabel="Retry"
          onAction={reload}
        />
      )}

      {report.state === "ready" && (
        <>
          <section className="dash-section">
            <div className="dash-section-head">
              <h3>Delivery &amp; CRM</h3>
              <span className="dash-live-badge">Live from API</span>
            </div>
            <div className="dash-stat-grid">
              <StatCard label="Active projects" value={metrics.activeProjects ?? 0} />
              <StatCard label="Completed projects" value={metrics.completedProjects ?? 0} />
              <StatCard label="Overdue tasks" value={metrics.overdueTasks ?? 0} />
              <StatCard label="Total clients" value={metrics.totalClients ?? 0} />
              <StatCard label="Won leads" value={metrics.wonLeads ?? 0} />
              <StatCard label="Overdue follow-ups" value={metrics.overdueFollowUps ?? 0} />
            </div>
          </section>

          <section className="dash-section">
            <div className="dash-section-head">
              <h3>Marketing</h3>
              <span className="dash-live-badge">Live from API</span>
            </div>
            <div className="dash-stat-grid">
              <StatCard
                label="Total campaigns"
                value={metrics.totalCampaigns ?? 0}
              />
              <StatCard
                label="Active campaigns"
                value={metrics.activeCampaigns ?? 0}
              />
              <StatCard
                label="Attributed leads"
                value={metrics.attributedLeads ?? 0}
              />
              <StatCard
                label="Campaign conversions"
                value={metrics.campaignConversions ?? 0}
              />
              <StatCard
                label="Conversion rate"
                value={`${metrics.campaignConversionRate ?? 0}%`}
              />
              <StatCard
                label="Campaign spend"
                value={`₹ ${formatMoney(metrics.campaignSpendTotal)}`}
                meta={`Budget ₹ ${formatMoney(metrics.campaignBudgetTotal)}`}
              />
            </div>
          </section>

          <section className="dash-section">
            <div className="dash-section-head">
              <h3>Finance</h3>
              <span className="dash-live-badge">Live from API</span>
            </div>
            <div className="dash-stat-grid">
              <StatCard
                label="Total invoiced (issued)"
                value={`₹ ${formatMoney(finance.totalInvoiced)}`}
              />
              <StatCard
                label="Collected payments"
                value={`₹ ${formatMoney(finance.totalCollected)}`}
              />
              <StatCard
                label="Outstanding balance"
                value={`₹ ${formatMoney(finance.totalOutstanding)}`}
              />
              <StatCard
                label="Net profit"
                value={`₹ ${formatMoney(finance.netProfit)}`}
              />
              <StatCard
                label="Approved expenses"
                value={`₹ ${formatMoney(finance.approvedExpenseTotal)}`}
              />
              <StatCard
                label="Overdue invoices"
                value={finance.overdueInvoiceCount ?? 0}
              />
            </div>
          </section>

          <section className="dash-section">
            <div className="dash-section-head">
              <h4>Monthly breakdown</h4>
            </div>

            {monthly.length === 0 ? (
              <StatusPanel
                variant="empty"
                title="No monthly data in this range"
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
                    {monthly.map((month) => (
                      <tr key={month.month}>
                        <td>{month.month}</td>
                        <td>₹ {formatMoney(month.invoiced)}</td>
                        <td>₹ {formatMoney(month.collected)}</td>
                        <td>₹ {formatMoney(month.expenses)}</td>
                        <td>₹ {formatMoney(month.profit)}</td>
                        <td>{month.invoiceCount}</td>
                        <td>{month.paymentCount}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            <p className="dash-form-hint">{finance.profitDefinition}</p>
          </section>

          {onNavigate && (
            <section className="dash-section">
              <div className="dash-section-head">
                <h4>Open a module</h4>
              </div>
              <div className="dash-row-actions">
                <button
                  type="button"
                  className="dash-button ghost"
                  onClick={() => onNavigate("projects")}
                >
                  Projects
                </button>
                <button
                  type="button"
                  className="dash-button ghost"
                  onClick={() => onNavigate("leads")}
                >
                  Leads
                </button>
                <button
                  type="button"
                  className="dash-button ghost"
                  onClick={() => onNavigate("marketing")}
                >
                  Marketing
                </button>
                <button
                  type="button"
                  className="dash-button ghost"
                  onClick={() => onNavigate("invoices")}
                >
                  Invoices
                </button>
                <button
                  type="button"
                  className="dash-button ghost"
                  onClick={() => onNavigate("finance-reports")}
                >
                  Financial reports
                </button>
              </div>
            </section>
          )}
        </>
      )}
    </div>
  );
}
