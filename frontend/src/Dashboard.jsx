import { useCallback, useEffect, useMemo, useState } from "react";
import { useLocation, useSearchParams } from "react-router-dom";
import Header from "./components/Header";
import Sidebar from "./components/Sidebar";
import { navigationForRole, PLATFORM_SECTIONS, WORKSPACE_SECTIONS } from "./nav";
import ClientsPage from "./pages/ClientsPage";
import EmployeesPage from "./pages/EmployeesPage";
import FollowUpsPage from "./pages/FollowUpsPage";
import LeadsPage from "./pages/LeadsPage";
import Overview from "./pages/Overview";
import EmployeeOverviewPage from "./pages/EmployeeOverviewPage";
import ProjectsPage from "./pages/ProjectsPage";
import TasksPage from "./pages/TasksPage";
import FinanceOverviewPage from "./pages/FinanceOverviewPage";
import FinancialReportsPage from "./pages/FinancialReportsPage";
import MarketingPage from "./pages/MarketingPage";
import InvoicesPage from "./pages/InvoicesPage";
import PaymentsPage from "./pages/PaymentsPage";
import ExpensesPage from "./pages/ExpensesPage";
import ReportsPage from "./pages/ReportsPage";
import WorkspacePage from "./pages/WorkspacePage";
import PlatformPage from "./pages/PlatformPage";
import { DataState, Notice } from "./components/WorkspaceUI";
import { useRemote } from "./workspaceApi";
import { useLegacyModalAccessibility } from "./useLegacyModalAccessibility";
import "./Dashboard.css";
import "./Workspace.css";

const SECTION_ALIASES = { employees: "people", campaigns: "marketing" };

export default function Dashboard({ session, onSignOut }) {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const closeSidebar = useCallback(() => setSidebarOpen(false), []);
  const schemas = useRemote("/api/workspace/schema");
  useLegacyModalAccessibility();

  // The navigation depends on the role of the account, so an employee never
  // sees the management modules in the sidebar.
  const navigation = useMemo(
    () => navigationForRole(session.role),
    [session.role]
  );

  // The open section is kept in the query string, so a reload or a shared
  // link opens the same module.
  const [searchParams, setSearchParams] = useSearchParams();
  const location = useLocation();
  const requestedSection = searchParams.get("section");
  const requestedId = SECTION_ALIASES[requestedSection] || requestedSection;
  const sectionAllowed = navigation.some((item) => item.id === requestedId);

  const activeId = sectionAllowed
    ? requestedId
    : navigation[0].id;

  useEffect(() => {
    if (sectionAllowed && requestedSection === requestedId) return;
    const next = new URLSearchParams(searchParams);
    next.set("section", activeId);
    if (!sectionAllowed) next.delete("record");
    setSearchParams(next, {
      replace: true,
      state: requestedSection && !sectionAllowed
        ? { sectionNotice: "This section is not available for your account. Your overview has been opened." }
        : null,
    });
  }, [activeId, requestedId, requestedSection, searchParams, sectionAllowed, setSearchParams]);

  const activeItem =
    navigation.find((item) => item.id === activeId) || navigation[0];

  const selectSection = useCallback(
    (id, recordId) => {
      const section = SECTION_ALIASES[id] || id;
      setSearchParams((current) => {
        const next = current.get("section") === section ? new URLSearchParams(current) : new URLSearchParams();
        next.set("section", section);
        if (recordId) next.set("record", String(recordId));
        else next.delete("record");
        return next;
      }, { state: null });
      setSidebarOpen(false);
    },
    [setSearchParams]
  );

  const groupItems = navigation.filter((item) => item.group === activeItem.group);
  const activeSchema = schemas.data?.find((schema) => schema.key === activeId);

  return (
    <div className="dashboard-layout">
      <Sidebar
        activeId={activeId}
        isOpen={sidebarOpen}
        navigation={navigation}
        onClose={closeSidebar}
        onSelect={selectSection}
        session={session}
      />

      <div className="dash-main">
        <Header
          activeItem={activeItem}
          session={session}
          onToggleSidebar={() => setSidebarOpen((open) => !open)}
          onSignOut={onSignOut}
          onNavigate={selectSection}
        />
        {groupItems.length > 1 && <nav className="workspace-tabs" aria-label={`${activeItem.groupLabel} sections`}>{groupItems.map((item) => <button key={item.id} className={item.id === activeId ? "active" : ""} aria-current={item.id === activeId ? "page" : undefined} onClick={() => selectSection(item.id)}>{item.label}</button>)}</nav>}
        <main className="dash-content" id="main-content">
          <Notice onDismiss={() => setSearchParams(searchParams, { replace: true, state: null })}>{location.state?.sectionNotice}</Notice>
          {activeId === "overview" && (
            session.role === "ADMIN" ? <Overview session={session} onNavigate={selectSection} /> : <EmployeeOverviewPage session={session} onNavigate={selectSection} />
          )}
          {activeId === "people" && <EmployeesPage session={session} />}
          {activeId === "projects" && <ProjectsPage session={session} />}
          {activeId === "tasks" && <TasksPage session={session} />}
          {activeId === "clients" && <ClientsPage session={session} />}
          {activeId === "leads" && <LeadsPage session={session} />}
          {activeId === "follow-ups" && <FollowUpsPage session={session} />}
          {activeId === "finance" && (
            <FinanceOverviewPage session={session} onNavigate={selectSection} />
          )}
          {activeId === "invoices" && <InvoicesPage session={session} />}
          {activeId === "payments" && <PaymentsPage session={session} />}
          {activeId === "expenses" && <ExpensesPage session={session} />}
          {activeId === "finance-reports" && <FinancialReportsPage session={session} />}
          {activeId === "marketing" && <MarketingPage session={session} />}
          {activeId === "reports" && (
            <ReportsPage session={session} onNavigate={selectSection} />
          )}
          {WORKSPACE_SECTIONS.has(activeId) && <DataState loading={schemas.loading} error={schemas.error} retry={schemas.reload}>{activeSchema ? <WorkspacePage key={activeId} schema={activeSchema} session={session} /> : !schemas.loading && !schemas.error ? <div className="workspace-state" role="alert"><strong>Module configuration unavailable</strong><p>The server did not return the configuration for this module.</p><button className="dash-button secondary" onClick={schemas.reload}>Retry</button></div> : null}</DataState>}
          {PLATFORM_SECTIONS.has(activeId) && <PlatformPage key={activeId} section={activeId} session={session} />}
        </main>
      </div>
    </div>
  );
}
