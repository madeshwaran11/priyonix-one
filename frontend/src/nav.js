import { IconChart, IconClients, IconFinance, IconMarketing, IconProjects, IconReports, IconTasks, IconUsers } from "./components/Icons";
import { ROLE_ADMIN, canonicalRole } from "./roles";

const groups = [
  { key: "overview", label: "Overview", icon: IconChart, items: [["overview", "Overview"]] },
  { key: "crm", label: "CRM & sales", icon: IconClients, items: [["clients", "Clients"], ["leads", "Leads"], ["contacts", "Contacts"], ["opportunities", "Opportunities"], ["quotations", "Quotations"], ["follow-ups", "Follow-ups"]] },
  { key: "delivery", label: "Projects & work", icon: IconProjects, items: [["projects", "Projects"], ["tasks", "Tasks"], ["timesheets", "Timesheets"]] },
  { key: "people", label: "People & HR", employeeLabel: "My attendance & leave", icon: IconUsers, items: [["people", "Employees"], ["departments", "Departments"], ["attendance", "Attendance"], ["leave", "Leave requests"]] },
  { key: "finance", label: "Finance", icon: IconFinance, items: [["finance", "Overview"], ["invoices", "Invoices"], ["payments", "Payments"], ["expenses", "Expenses"], ["budgets", "Budgets"], ["finance-reports", "Financial reports"]] },
  { key: "procurement", label: "Procurement", icon: IconTasks, items: [["vendors", "Vendors"], ["purchase-requests", "Purchase requests"], ["purchase-orders", "Purchase orders"], ["goods-receipts", "Goods receipts"]] },
  { key: "assets", label: "Assets", icon: IconProjects, items: [["assets", "Asset register"], ["maintenance", "Maintenance"]] },
  { key: "training", label: "Training & placement", icon: IconUsers, items: [["courses", "Courses"], ["batches", "Batches"], ["students", "Students"], ["training-attendance", "Attendance"], ["assessments", "Assessments"], ["placements", "Placements"]] },
  { key: "marketing", label: "Marketing", icon: IconMarketing, items: [["marketing", "Campaigns"]] },
  { key: "reports", label: "Reports", icon: IconReports, items: [["reports", "Business reports"]] },
  { key: "administration", label: "Administration", icon: IconTasks, items: [["organization", "Organization"], ["users", "User accounts"], ["permissions", "Roles & permissions"], ["audit", "Audit trail"], ["system", "System status"]] },
];

export const WORKSPACE_SECTIONS = new Set(["contacts", "opportunities", "quotations", "timesheets", "attendance", "leave", "vendors", "purchase-requests", "purchase-orders", "goods-receipts", "assets", "maintenance", "courses", "batches", "students", "training-attendance", "assessments", "placements", "budgets", "departments"]);
export const PLATFORM_SECTIONS = new Set(["organization", "users", "permissions", "audit", "system"]);
export const navigation = groups.flatMap((group) => group.items.map(([id, label]) => ({ id, label, group: group.key, groupLabel: group.label, employeeGroupLabel: group.employeeLabel, icon: group.icon })));
export const ADMIN_ONLY_SECTIONS = new Set(navigation.filter((item) => ["finance", "procurement", "assets", "training", "reports", "administration"].includes(item.group) || ["people", "departments"].includes(item.id)).map((item) => item.id));

export function navigationForRole(role) {
  return canonicalRole(role) === ROLE_ADMIN ? navigation : navigation.filter((item) => !ADMIN_ONLY_SECTIONS.has(item.id));
}
