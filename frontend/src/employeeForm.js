// Shared helpers and constants for the employee add/edit form.

export const EMPLOYMENT_STATUSES = [
  "ACTIVE",
  "PROBATION",
  "ON_LEAVE",
  "RESIGNED",
  "TERMINATED",
];

export function emptyEmployeeForm() {
  return {
    employeeId: "",
    fullName: "",
    workEmail: "",
    phone: "",
    department: "",
    designation: "",
    joiningDate: "",
    skills: "",
    reportingManager: "",
    employmentStatus: "ACTIVE",
  };
}

export function employeeToForm(employee) {
  return {
    employeeId: employee.employeeId || "",
    fullName: employee.fullName || "",
    workEmail: employee.workEmail || "",
    phone: employee.phone || "",
    department: employee.department || "",
    designation: employee.designation || "",
    joiningDate: employee.joiningDate || "",
    skills: Array.isArray(employee.skills) ? employee.skills.join(", ") : "",
    reportingManager: employee.reportingManager || "",
    employmentStatus: employee.employmentStatus || "ACTIVE",
  };
}

/** Converts form state into the JSON payload the backend expects. */
export function formToPayload(form) {
  return {
    employeeId: form.employeeId.trim(),
    fullName: form.fullName.trim(),
    workEmail: form.workEmail.trim(),
    phone: form.phone.trim() || null,
    department: form.department.trim(),
    designation: form.designation.trim(),
    joiningDate: form.joiningDate || null,
    skills: form.skills
      .split(",")
      .map((skill) => skill.trim())
      .filter(Boolean),
    reportingManager: form.reportingManager.trim() || null,
    employmentStatus: form.employmentStatus,
  };
}
