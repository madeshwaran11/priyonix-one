import { useEffect, useState } from "react";
import { fetchDashboardMetrics, fetchFinanceSummary, verifyDashboardSession } from "../api";
import { formatMoney } from "../format";
import StatCard from "../components/StatCard";
import StatusPanel from "../components/StatusPanel";

function SummaryPanel({ title, description, items, children }) {
  return (
    <section className="dash-summary-panel">
      <div className="dash-summary-heading">
        <div><h3>{title}</h3><p>{description}</p></div>
        <span className="dash-live-badge">Live</span>
      </div>
      <dl className="dash-summary-list">
        {items.map((item) => <div key={item.label}><dt>{item.label}</dt><dd className={item.attention ? "needs-attention" : ""}>{item.value}</dd></div>)}
      </dl>
      {children}
    </section>
  );
}

export default function Overview({ session, onNavigate }) {
  const [sessionCheck, setSessionCheck] = useState({ state: "loading", message: "" });
  const [retryCount, setRetryCount] = useState(0);
  const [metrics, setMetrics] = useState({ state: "loading", data: null });
  const [metricsRetry, setMetricsRetry] = useState(0);
  const [finance, setFinance] = useState({ state: "loading", data: null });
  const [financeRetry, setFinanceRetry] = useState(0);

  useEffect(() => {
    let cancelled = false;
    verifyDashboardSession(session.token)
      .then((data) => { if (!cancelled) setSessionCheck({ state: "ok", message: data.message || "Connected" }); })
      .catch((error) => { if (!cancelled) setSessionCheck({ state: error.code === "UNAUTHORIZED" ? "unauthorized" : "error", message: error.message || "Could not verify your session." }); });
    return () => { cancelled = true; };
  }, [session.token, retryCount]);

  useEffect(() => {
    let cancelled = false;
    fetchDashboardMetrics(session.token)
      .then((data) => { if (!cancelled) setMetrics({ state: "ready", data }); })
      .catch(() => { if (!cancelled) setMetrics({ state: "error", data: null }); });
    return () => { cancelled = true; };
  }, [session.token, metricsRetry]);

  useEffect(() => {
    let cancelled = false;
    fetchFinanceSummary(session.token)
      .then((data) => { if (!cancelled) setFinance({ state: "ready", data }); })
      .catch(() => { if (!cancelled) setFinance({ state: "error", data: null }); });
    return () => { cancelled = true; };
  }, [session.token, financeRetry]);

  const retrySession = () => { setSessionCheck({ state: "loading", message: "" }); setRetryCount((count) => count + 1); };
  const retryMetrics = () => { setMetrics({ state: "loading", data: null }); setMetricsRetry((count) => count + 1); };
  const retryFinance = () => { setFinance({ state: "loading", data: null }); setFinanceRetry((count) => count + 1); };
  const operational = metrics.data;
  const financial = finance.data;
  const loading = metrics.state === "loading" || finance.state === "loading";

  return (
    <div className="dash-page overview-page">
      <section className="dash-hero">
        <div><h2>Welcome back, {session.email.split("@")[0]}</h2><p>A focused view of delivery, customer activity and cash position.</p></div>
        <div className={`dash-session-chip is-${sessionCheck.state}`} role="status">
          {sessionCheck.state === "loading" && "Connecting…"}
          {sessionCheck.state === "ok" && "Workspace connected"}
          {sessionCheck.state === "unauthorized" && "Session expired"}
          {sessionCheck.state === "error" && "Connection issue"}
        </div>
      </section>

      {sessionCheck.state === "unauthorized" && <StatusPanel variant="error" title="Your session is no longer valid" description="Sign in again to continue." />}
      {sessionCheck.state === "error" && <StatusPanel variant="error" title="Could not reach the workspace" description={sessionCheck.message} actionLabel="Retry" onAction={retrySession} />}
      {loading && !operational && !financial && <StatusPanel variant="loading" title="Preparing your overview…" description="Loading the latest operational and financial totals." />}
      {metrics.state === "error" && <StatusPanel variant="error" title="Operational metrics unavailable" description="Projects, clients and campaigns could not be loaded." actionLabel="Retry" onAction={retryMetrics} />}
      {finance.state === "error" && <StatusPanel variant="error" title="Finance metrics unavailable" description="The financial snapshot could not be loaded." actionLabel="Retry" onAction={retryFinance} />}

      {(operational || financial) && (
        <section className="dash-primary-metrics" aria-label="Primary workspace metrics">
          <StatCard label="Active projects" value={operational?.activeProjects ?? "—"} meta={`${operational?.totalProjects ?? 0} total projects`} />
          <StatCard label="Open follow-ups" value={operational?.openFollowUps ?? "—"} meta={operational?.overdueFollowUps ? `${operational.overdueFollowUps} overdue` : "On track"} trend={operational?.overdueFollowUps ? "up" : "flat"} />
          <StatCard label="Outstanding balance" value={financial ? `₹ ${formatMoney(financial.totalOutstanding)}` : "—"} meta="Issued invoices less collections" />
          <StatCard label="Net profit" value={financial ? `₹ ${formatMoney(financial.netProfit)}` : "—"} meta="Collected less approved expenses" />
        </section>
      )}

      {operational && (
        <div className="dash-summary-grid">
          <SummaryPanel title="Delivery" description="Projects and tasks requiring attention." items={[
            { label: "Projects", value: operational.totalProjects }, { label: "Completed", value: operational.completedProjects },
            { label: "Tasks", value: operational.totalTasks }, { label: "In progress", value: operational.tasksInProgress },
            { label: "Overdue", value: operational.overdueTasks, attention: operational.overdueTasks > 0 },
          ]}>
            <div className="dash-row-actions"><button type="button" className="dash-button secondary" onClick={() => onNavigate?.("projects")}>Open projects</button><button type="button" className="dash-button ghost" onClick={() => onNavigate?.("tasks")}>View tasks</button></div>
          </SummaryPanel>
          <SummaryPanel title="Customers" description="Clients, leads and upcoming follow-ups." items={[
            { label: "Clients", value: operational.totalClients }, { label: "Active clients", value: operational.activeClients },
            { label: "Leads", value: operational.totalLeads }, { label: "Won leads", value: operational.wonLeads },
            { label: "Overdue follow-ups", value: operational.overdueFollowUps, attention: operational.overdueFollowUps > 0 },
          ]}>
            <div className="dash-row-actions"><button type="button" className="dash-button secondary" onClick={() => onNavigate?.("clients")}>Open clients</button><button type="button" className="dash-button ghost" onClick={() => onNavigate?.("leads")}>View leads</button></div>
          </SummaryPanel>
          <SummaryPanel title="Marketing" description="Campaign activity and attributed outcomes." items={[
            { label: "Active campaigns", value: operational.activeCampaigns ?? 0 }, { label: "Attributed leads", value: operational.attributedLeads ?? 0 },
            { label: "Conversions", value: operational.campaignConversions ?? 0 }, { label: "Conversion rate", value: `${operational.campaignConversionRate ?? 0}%` },
            { label: "Spend", value: `₹ ${formatMoney(operational.campaignSpendTotal)}` },
          ]}>
            <div className="dash-row-actions"><button type="button" className="dash-button secondary" onClick={() => onNavigate?.("marketing")}>Open marketing</button></div>
          </SummaryPanel>
          {financial && <SummaryPanel title="Finance" description="Issued, collected and approved totals." items={[
            { label: "Total invoiced", value: `₹ ${formatMoney(financial.totalInvoiced)}` }, { label: "Collected", value: `₹ ${formatMoney(financial.totalCollected)}` },
            { label: "Outstanding", value: `₹ ${formatMoney(financial.totalOutstanding)}`, attention: financial.totalOutstanding > 0 }, { label: "Net profit", value: `₹ ${formatMoney(financial.netProfit)}` },
          ]}>
            <div className="dash-row-actions"><button type="button" className="dash-button secondary" onClick={() => onNavigate?.("finance")}>Finance overview</button><button type="button" className="dash-button ghost" onClick={() => onNavigate?.("finance-reports")}>Reports</button></div>
          </SummaryPanel>}
        </div>
      )}
    </div>
  );
}
