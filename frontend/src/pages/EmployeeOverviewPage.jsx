import { useRemote } from "../workspaceApi";
import { DataState, PageHeading } from "../components/WorkspaceUI";
import StatCard from "../components/StatCard";

export default function EmployeeOverviewPage({ session, onNavigate }) {
  const { data, loading, error, reload } = useRemote("/api/employee/overview");
  const actions = [["tasks", "My tasks"], ["follow-ups", "Follow-ups"], ["timesheets", "Log time"], ["attendance", "Attendance"], ["leave", "Request leave"]];
  return <div className="dash-page">
    <PageHeading title={`Welcome back, ${data?.profile?.fullName || session.email.split("@")[0]}`} description="Your work, time and requests in one place." />
    <DataState loading={loading} error={error} retry={reload}>
      {data && <><div className="dash-stat-grid">
        <StatCard label="Assigned tasks" value={data.metrics.assignedTasks} />
        <StatCard label="Open tasks" value={data.metrics.openTasks} />
        <StatCard label="Assigned follow-ups" value={data.metrics.assignedFollowUps} />
        <StatCard label="Open follow-ups" value={data.metrics.openFollowUps} />
      </div>
      <section className="workspace-panel"><h2>Continue your work</h2><div className="workspace-actions">{actions.map(([key,label])=><button key={key} className="dash-button secondary" onClick={()=>onNavigate(key)}>{label}</button>)}</div></section>
      {!data.profile.employeeId && <p className="workspace-help">An administrator can link your employee record using your work email to enable attendance, leave and timesheets.</p>}</>}
    </DataState>
  </div>;
}
