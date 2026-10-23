// Shared helpers and constants for the project add/edit form.

export const PROJECT_PRIORITIES = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];

export const PROJECT_STATUSES = [
  "PLANNING",
  "ACTIVE",
  "ON_HOLD",
  "COMPLETED",
  "CANCELLED",
];

export function emptyProjectForm() {
  return {
    projectCode: "",
    name: "",
    description: "",
    clientName: "",
    managerId: "",
    teamMemberIds: [],
    startDate: "",
    dueDate: "",
    priority: "MEDIUM",
    status: "PLANNING",
    milestones: [],
  };
}

export function projectToForm(project) {
  return {
    projectCode: project.projectCode || "",
    name: project.name || "",
    description: project.description || "",
    clientName: project.clientName || "",
    managerId: project.managerId || "",
    teamMemberIds: Array.isArray(project.teamMemberIds)
      ? [...project.teamMemberIds]
      : [],
    startDate: project.startDate || "",
    dueDate: project.dueDate || "",
    priority: project.priority || "MEDIUM",
    status: project.status || "PLANNING",
    milestones: Array.isArray(project.milestones)
      ? project.milestones.map((milestone) => ({
          id: milestone.id || "",
          title: milestone.title || "",
          dueDate: milestone.dueDate || "",
          completed: Boolean(milestone.completed),
        }))
      : [],
  };
}

/** Converts project form state into the JSON payload the backend expects. */
export function projectFormToPayload(form) {
  return {
    projectCode: form.projectCode.trim(),
    name: form.name.trim(),
    description: form.description.trim() || null,
    clientName: form.clientName.trim() || null,
    managerId: form.managerId,
    teamMemberIds: form.teamMemberIds,
    startDate: form.startDate || null,
    dueDate: form.dueDate || null,
    priority: form.priority,
    status: form.status,
    milestones: form.milestones.map((milestone) => ({
      id: milestone.id || null,
      title: milestone.title.trim(),
      dueDate: milestone.dueDate || null,
      completed: Boolean(milestone.completed),
    })),
  };
}

export function milestoneToFormRow() {
  return { id: "", title: "", dueDate: "", completed: false };
}
