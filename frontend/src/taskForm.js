// Shared helpers and constants for the task add/edit form.

export const TASK_PRIORITIES = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];

export const TASK_STATUSES = [
  "TODO",
  "IN_PROGRESS",
  "IN_REVIEW",
  "BLOCKED",
  "COMPLETED",
];

export function emptyTaskForm() {
  return {
    taskCode: "",
    title: "",
    description: "",
    projectId: "",
    assigneeId: "",
    startDate: "",
    dueDate: "",
    priority: "MEDIUM",
    status: "TODO",
    progress: 0,
    estimatedHours: "",
    actualHours: "",
  };
}

export function taskToForm(task) {
  return {
    taskCode: task.taskCode || "",
    title: task.title || "",
    description: task.description || "",
    projectId: task.projectId || "",
    assigneeId: task.assigneeId || "",
    startDate: task.startDate || "",
    dueDate: task.dueDate || "",
    priority: task.priority || "MEDIUM",
    status: task.status || "TODO",
    progress: typeof task.progress === "number" ? task.progress : 0,
    estimatedHours:
      task.estimatedHours === null || task.estimatedHours === undefined
        ? ""
        : String(task.estimatedHours),
    actualHours:
      task.actualHours === null || task.actualHours === undefined
        ? ""
        : String(task.actualHours),
  };
}

function toOptionalNumber(value) {
  if (value === "" || value === null || value === undefined) return null;
  const parsed = Number(value);
  return Number.isNaN(parsed) ? null : parsed;
}

/** Converts task form state into the JSON payload the backend expects. */
export function taskFormToPayload(form) {
  return {
    taskCode: form.taskCode.trim() || null,
    title: form.title.trim(),
    description: form.description.trim() || null,
    projectId: form.projectId,
    assigneeId: form.assigneeId || null,
    startDate: form.startDate || null,
    dueDate: form.dueDate || null,
    priority: form.priority,
    status: form.status,
    progress: Number(form.progress) || 0,
    estimatedHours: toOptionalNumber(form.estimatedHours),
    actualHours: toOptionalNumber(form.actualHours),
  };
}
