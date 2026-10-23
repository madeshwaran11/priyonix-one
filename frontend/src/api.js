// Backend API helper for PriyoniX One.
// Every protected call sends the JWT as "Authorization: Bearer <token>".

import { getAccessToken, getRefreshHandler } from "./tokenStore.js";

// Configurable at build time (VITE_API_BASE); defaults to the local backend.
export const API_BASE =
  import.meta.env?.VITE_API_BASE || "http://localhost:8080";

let authRequestGeneration = 0;
let pendingRefresh = null;

export function invalidateAuthRequests() {
  authRequestGeneration += 1;
}

function ensureCurrentSession(generation) {
  if (generation !== authRequestGeneration) {
    throw Object.assign(new Error("The session changed."), { name: "AbortError", code: "SESSION_CHANGED" });
  }
}

/**
 * Error thrown by the API helpers. Carries the HTTP status, a machine
 * code and any field-level validation errors returned by the backend.
 */
export class ApiError extends Error {
  constructor(message, { status, fieldErrors } = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = status === 401 ? "UNAUTHORIZED" : "API_ERROR";
    this.fieldErrors = fieldErrors || {};
  }
}

async function readJson(response) {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

/**
 * Exchanges the HttpOnly refresh cookie for a new access token.
 *
 * The call carries the cookie (credentials: "include") and never returns the
 * refresh token itself: the browser keeps it, the script never sees it.
 */
export async function refreshAccessToken() {
  const response = await fetch(`${API_BASE}/api/auth/refresh`, {
    method: "POST",
    credentials: "include",
    signal: AbortSignal.timeout(15000),
    headers: { "Content-Type": "application/json" },
  });

  const data = await readJson(response);

  if (!response.ok || !data?.token || !data?.email || !data?.role) {
    throw new ApiError(response.status === 401 || response.status === 403 ? "Your session is no longer valid." : "Your workspace could not be reached. Please try again.", {
      status: response.ok ? 502 : response.status,
    });
  }

  return data;
}

/** Revokes the refresh cookie on the server. */
export async function revokeServerSession() {
  try {
    await fetch(`${API_BASE}/api/auth/logout`, {
      method: "POST",
      credentials: "include",
      signal: AbortSignal.timeout(15000),
      headers: { "Content-Type": "application/json" },
    });
  } catch {
    // The local session is cleared regardless of the network outcome.
  }
}

/**
 * Performs a request against the backend.
 *
 * @param {string} path
 * @param {object} options
 * @param {string} [options.token] explicit access token; the token of the
 *        running session is used when it is omitted
 * @param {boolean} [options.skipAuth] send no Authorization header and never
 *        retry, used by the public authentication endpoints
 */
export async function apiFetch(path, { token, skipAuth, ...options } = {}) {
  const generation = authRequestGeneration;
  const headers = { ...(options.headers || {}) };
  const accessToken = skipAuth ? null : token || getAccessToken();

  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }

  // The requests are credentialed so the HttpOnly refresh cookie that the
  // backend sets on sign-in survives a cross-origin deployment. Without it
  // the browser silently drops the Set-Cookie header of the response and
  // "Keep me signed in" would never work.
  const request = {
    credentials: "include",
    ...options,
    headers,
  };

  let response = await fetch(`${API_BASE}${path}`, request);
  if (accessToken) ensureCurrentSession(generation);

  // A 401 on a request that carried a token means the access token expired.
  // A remembered session can recover from that transparently, so the
  // refresh cookie is exchanged for a new access token and the request is
  // retried once before the session is given up.
  if (response.status === 401 && accessToken && !skipAuth) {
    // Other requests may have refreshed this token while this one was in flight.
    const currentToken = getAccessToken();
    if (!currentToken) return response;
    const refreshed = currentToken !== accessToken ? currentToken : await refreshSession();
    ensureCurrentSession(generation);

    if (refreshed) {
      response = await fetch(`${API_BASE}${path}`, {
        ...request,
        headers: { ...headers, Authorization: `Bearer ${refreshed}` },
      });
    }
  }

  // A 401 on a request that carried a token means the JWT expired or the
  // account was disabled. Notify the app shell so it can clear the stale
  // session and show the sign-in screen (login itself has no token).
  if (accessToken) ensureCurrentSession(generation);
  if (response.status === 401 && accessToken && getAccessToken() && typeof window !== "undefined") {
    window.dispatchEvent(new Event("priyonix:unauthorized"));
  }

  return response;
}

/**
 * Asks the registered refresh handler for a new access token.
 *
 * @returns {Promise<string | null>} the new token, or null when the session
 *          cannot be recovered
 */
async function refreshSession() {
  const handler = getRefreshHandler();

  if (!handler) {
    return null;
  }

  if (pendingRefresh?.handler === handler && pendingRefresh.generation === authRequestGeneration) return pendingRefresh.promise;
  const operation = { handler, generation: authRequestGeneration };
  operation.promise = Promise.resolve().then(handler).then((token) => token || null).catch((error) => {
    // Connectivity failures remain retryable and do not sign the user out.
    if (error?.status === 401 || error?.status === 403) return null;
    throw error;
  }).finally(() => { if (pendingRefresh === operation) pendingRefresh = null; });
  pendingRefresh = operation;
  return operation.promise;
}

function toApiError(response, data, fallbackMessage) {
  if (response.status === 401) {
    return new ApiError("Your session is no longer valid.", {
      status: 401,
    });
  }

  return new ApiError((data && data.message) || fallbackMessage, {
    status: response.status,
    fieldErrors: data && data.errors,
  });
}

export async function fetchBackendHealth() {
  const response = await apiFetch("/api/health");
  if (!response.ok) throw new Error("Backend unavailable");
  return response.json();
}

// Verifies the stored JWT against a real protected backend endpoint.
export async function verifyDashboardSession(token) {
  const response = await apiFetch("/api/dashboard", { token });
  const data = await readJson(response);

  if (response.status === 401) {
    const error = new Error("Your session is no longer valid.");
    error.code = "UNAUTHORIZED";
    throw error;
  }

  if (!response.ok) throw new Error("Dashboard service is unavailable.");

  if (!data || data.status !== "SUCCESS") {
    throw new Error("Unexpected response from the dashboard service.");
  }

  return data;
}

// ----- Employee & HR ------------------------------------------------------

export async function listEmployees(token, search) {
  const trimmed = (search || "").trim();
  const query = trimmed ? `?search=${encodeURIComponent(trimmed)}` : "";

  const response = await apiFetch(`/api/employees${query}`, { token });
  if (response.status === 403) {
    const lookup = await apiFetch("/api/workspace/lookups/employees", { token });
    if (!lookup.ok) throw new ApiError("Employee choices are unavailable.", { status: lookup.status });
    return (await lookup.json()).map((item) => ({ id: item.id, fullName: item.label, employeeId: item.id }));
  }
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not load employees.");
  }

  return data && Array.isArray(data.employees) ? data.employees : [];
}

export async function createEmployee(token, payload) {
  const response = await apiFetch("/api/employees", {
    token,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not create the employee.");
  }

  return data.employee;
}

export async function updateEmployee(token, id, payload) {
  const response = await apiFetch(
    `/api/employees/${encodeURIComponent(id)}`,
    {
      token,
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    }
  );
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not update the employee.");
  }

  return data.employee;
}

export async function deleteEmployee(token, id) {
  const response = await apiFetch(
    `/api/employees/${encodeURIComponent(id)}`,
    { token, method: "DELETE" }
  );
  const data = await readJson(response);

  if (response.status === 403) {
    throw new ApiError("Only administrators can delete employees.", {
      status: 403,
    });
  }

  if (!response.ok) {
    throw toApiError(response, data, "Could not delete the employee.");
  }
}

// ----- Dashboard metrics --------------------------------------------------

// Real project/task counts for the overview page.
export async function fetchDashboardMetrics(token) {
  const response = await apiFetch("/api/dashboard/metrics", { token });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not load dashboard metrics.");
  }

  return data && data.metrics ? data.metrics : {};
}

// ----- Projects -----------------------------------------------------------

function buildQuery(params) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      query.set(key, String(value));
    }
  });
  const asString = query.toString();
  return asString ? `?${asString}` : "";
}

export async function listProjects(
  token,
  { search, status, priority, page = 1, size = 20 } = {}
) {
  const query = buildQuery({ search: (search || "").trim(), status, priority, page, size });
  const response = await apiFetch(`/api/projects${query}`, { token });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not load projects.");
  }

  return {
    items: data && Array.isArray(data.projects) ? data.projects : [],
    total: (data && data.total) || 0,
    page: (data && data.page) || page,
    size: (data && data.size) || size,
    totalPages: (data && data.totalPages) || 0,
  };
}

export async function getProject(token, id) {
  const response = await apiFetch(
    `/api/projects/${encodeURIComponent(id)}`,
    { token }
  );
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not load the project.");
  }

  return data.project;
}

export async function getProjectSummary(token, id) {
  const response = await apiFetch(
    `/api/projects/${encodeURIComponent(id)}/summary`,
    { token }
  );
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not load the project summary.");
  }

  return data.summary;
}

export async function listProjectTasks(token, projectId) {
  const response = await apiFetch(
    `/api/projects/${encodeURIComponent(projectId)}/tasks`,
    { token }
  );
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not load project tasks.");
  }

  return data && Array.isArray(data.tasks) ? data.tasks : [];
}

export async function createProject(token, payload) {
  const response = await apiFetch("/api/projects", {
    token,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not create the project.");
  }

  return data.project;
}

export async function updateProject(token, id, payload) {
  const response = await apiFetch(`/api/projects/${encodeURIComponent(id)}`, {
    token,
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not update the project.");
  }

  return data.project;
}

export async function deleteProject(token, id, { cascade = false } = {}) {
  const query = cascade ? "?cascade=true" : "";
  const response = await apiFetch(
    `/api/projects/${encodeURIComponent(id)}${query}`,
    { token, method: "DELETE" }
  );
  const data = await readJson(response);

  if (response.status === 403) {
    throw new ApiError("Only administrators can delete projects.", {
      status: 403,
    });
  }

  if (!response.ok) {
    throw toApiError(response, data, "Could not delete the project.");
  }
}

// ----- Tasks --------------------------------------------------------------

export async function listTasks(
  token,
  { search, status, priority, projectId, assigneeId, overdue, page = 1, size = 20 } = {}
) {
  const query = buildQuery({
    search: (search || "").trim(),
    status,
    priority,
    projectId,
    assigneeId,
    overdue: overdue ? "true" : undefined,
    page,
    size,
  });
  const response = await apiFetch(`/api/tasks${query}`, { token });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not load tasks.");
  }

  return {
    items: data && Array.isArray(data.tasks) ? data.tasks : [],
    total: (data && data.total) || 0,
    page: (data && data.page) || page,
    size: (data && data.size) || size,
    totalPages: (data && data.totalPages) || 0,
  };
}

export async function getTask(token, id) {
  const response = await apiFetch(`/api/tasks/${encodeURIComponent(id)}`, {
    token,
  });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not load the task.");
  }

  return data.task;
}

export async function createTask(token, payload) {
  const response = await apiFetch("/api/tasks", {
    token,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not create the task.");
  }

  return data.task;
}

export async function updateTask(token, id, payload) {
  const response = await apiFetch(`/api/tasks/${encodeURIComponent(id)}`, {
    token,
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not update the task.");
  }

  return data.task;
}

export async function deleteTask(token, id) {
  const response = await apiFetch(`/api/tasks/${encodeURIComponent(id)}`, {
    token,
    method: "DELETE",
  });
  const data = await readJson(response);

  if (response.status === 403) {
    throw new ApiError("Only administrators can delete tasks.", {
      status: 403,
    });
  }

  if (!response.ok) {
    throw toApiError(response, data, "Could not delete the task.");
  }
}

// ----- Subtasks -----------------------------------------------------------

export async function createSubtask(token, taskId, payload) {
  const response = await apiFetch(
    `/api/tasks/${encodeURIComponent(taskId)}/subtasks`,
    {
      token,
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    }
  );
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not create the subtask.");
  }

  return data.task;
}

export async function updateSubtask(token, taskId, subtaskId, payload) {
  const response = await apiFetch(
    `/api/tasks/${encodeURIComponent(taskId)}/subtasks/${encodeURIComponent(subtaskId)}`,
    {
      token,
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    }
  );
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not update the subtask.");
  }

  return data.task;
}

export async function deleteSubtask(token, taskId, subtaskId) {
  const response = await apiFetch(
    `/api/tasks/${encodeURIComponent(taskId)}/subtasks/${encodeURIComponent(subtaskId)}`,
    { token, method: "DELETE" }
  );
  const data = await readJson(response);

  if (response.status === 403) {
    throw new ApiError("Only administrators can delete subtasks.", {
      status: 403,
    });
  }

  if (!response.ok) {
    throw toApiError(response, data, "Could not delete the subtask.");
  }

  return data.task;
}

// ----- Clients ------------------------------------------------------------

export async function listClients(
  token,
  { search, status, page = 1, size = 20 } = {}
) {
  const query = buildQuery({ search: (search || "").trim(), status, page, size });
  const response = await apiFetch(`/api/clients${query}`, { token });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not load clients.");
  }

  return {
    items: data && Array.isArray(data.clients) ? data.clients : [],
    total: (data && data.total) || 0,
    page: (data && data.page) || page,
    size: (data && data.size) || size,
    totalPages: (data && data.totalPages) || 0,
  };
}

export async function getClient(token, id) {
  const response = await apiFetch(`/api/clients/${encodeURIComponent(id)}`, {
    token,
  });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not load the client.");
  }

  return data.client;
}

export async function createClient(token, payload) {
  const response = await apiFetch("/api/clients", {
    token,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not create the client.");
  }

  return data.client;
}

export async function updateClient(token, id, payload) {
  const response = await apiFetch(`/api/clients/${encodeURIComponent(id)}`, {
    token,
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not update the client.");
  }

  return data.client;
}

export async function deleteClient(token, id, { cascade = false } = {}) {
  const query = cascade ? "?cascade=true" : "";
  const response = await apiFetch(
    `/api/clients/${encodeURIComponent(id)}${query}`,
    { token, method: "DELETE" }
  );
  const data = await readJson(response);

  if (response.status === 403) {
    throw new ApiError("Only administrators can delete clients.", {
      status: 403,
    });
  }

  if (!response.ok) {
    throw toApiError(response, data, "Could not delete the client.");
  }
}

export async function listClientFollowUps(token, clientId) {
  const response = await apiFetch(
    `/api/clients/${encodeURIComponent(clientId)}/follow-ups`,
    { token }
  );
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not load client follow-ups.");
  }

  return data && Array.isArray(data.followUps) ? data.followUps : [];
}

// ----- Marketing: campaigns -----------------------------------------------

export async function listCampaigns(
  token,
  { search, status, channel, page = 1, size = 20 } = {}
) {
  const query = buildQuery({
    search: (search || "").trim(),
    status,
    channel,
    page,
    size,
  });
  const response = await apiFetch(`/api/campaigns${query}`, { token });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not load campaigns.");
  }

  return {
    items: data && Array.isArray(data.campaigns) ? data.campaigns : [],
    total: (data && data.total) || 0,
    page: (data && data.page) || page,
    size: (data && data.size) || size,
    totalPages: (data && data.totalPages) || 0,
  };
}

export async function getCampaign(token, id) {
  const response = await apiFetch(`/api/campaigns/${encodeURIComponent(id)}`, {
    token,
  });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not load the campaign.");
  }

  return data.campaign;
}

export async function createCampaign(token, payload) {
  const response = await apiFetch("/api/campaigns", {
    token,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not create the campaign.");
  }

  return data.campaign;
}

export async function updateCampaign(token, id, payload) {
  const response = await apiFetch(
    `/api/campaigns/${encodeURIComponent(id)}`,
    {
      token,
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    }
  );
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not update the campaign.");
  }

  return data.campaign;
}

export async function updateCampaignStatus(token, id, status) {
  const response = await apiFetch(
    `/api/campaigns/${encodeURIComponent(id)}/status`,
    {
      token,
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ status }),
    }
  );
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not update the campaign status.");
  }

  return data.campaign;
}

export async function deleteCampaign(token, id, { detachLeads = false } = {}) {
  const query = detachLeads ? "?detachLeads=true" : "";
  const response = await apiFetch(
    `/api/campaigns/${encodeURIComponent(id)}${query}`,
    { token, method: "DELETE" }
  );
  const data = await readJson(response);

  if (response.status === 403) {
    throw new ApiError("Only administrators can delete campaigns.", {
      status: 403,
    });
  }

  if (response.status === 409) {
    const conflict = new ApiError(
      (data && data.message) ||
        "This campaign has attributed leads. Detach them first.",
      { status: 409 }
    );
    conflict.code = "HAS_LEADS";
    throw conflict;
  }

  if (!response.ok) {
    throw toApiError(response, data, "Could not delete the campaign.");
  }
}

// ----- Leads --------------------------------------------------------------

export async function listLeads(
  token,
  {
    search,
    status,
    priority,
    assignedToId,
    campaignId,
    page = 1,
    size = 20,
  } = {}
) {
  const query = buildQuery({
    search: (search || "").trim(),
    status,
    priority,
    assignedToId,
    campaignId,
    page,
    size,
  });
  const response = await apiFetch(`/api/leads${query}`, { token });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not load leads.");
  }

  return {
    items: data && Array.isArray(data.leads) ? data.leads : [],
    total: (data && data.total) || 0,
    page: (data && data.page) || page,
    size: (data && data.size) || size,
    totalPages: (data && data.totalPages) || 0,
  };
}

export async function getLead(token, id) {
  const response = await apiFetch(`/api/leads/${encodeURIComponent(id)}`, {
    token,
  });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not load the lead.");
  }

  return data.lead;
}

export async function createLead(token, payload) {
  const response = await apiFetch("/api/leads", {
    token,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not create the lead.");
  }

  return data.lead;
}

export async function updateLead(token, id, payload) {
  const response = await apiFetch(`/api/leads/${encodeURIComponent(id)}`, {
    token,
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not update the lead.");
  }

  return data.lead;
}

export async function deleteLead(token, id, { cascade = false } = {}) {
  const query = cascade ? "?cascade=true" : "";
  const response = await apiFetch(
    `/api/leads/${encodeURIComponent(id)}${query}`,
    { token, method: "DELETE" }
  );
  const data = await readJson(response);

  if (response.status === 403) {
    throw new ApiError("Only administrators can delete leads.", {
      status: 403,
    });
  }

  if (!response.ok) {
    throw toApiError(response, data, "Could not delete the lead.");
  }
}

// Converts a WON lead into a client (reusing an existing client by email).
export async function convertLead(token, id) {
  const response = await apiFetch(
    `/api/leads/${encodeURIComponent(id)}/convert`,
    { token, method: "POST" }
  );
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not convert the lead.");
  }

  return {
    created: Boolean(data.created),
    lead: data.lead,
    client: data.client,
  };
}

export async function listLeadFollowUps(token, leadId) {
  const response = await apiFetch(
    `/api/leads/${encodeURIComponent(leadId)}/follow-ups`,
    { token }
  );
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not load lead follow-ups.");
  }

  return data && Array.isArray(data.followUps) ? data.followUps : [];
}

// ----- Follow-ups ---------------------------------------------------------

export async function listFollowUps(
  token,
  {
    search,
    leadId,
    clientId,
    assignedToId,
    completed,
    overdue,
    page = 1,
    size = 20,
  } = {}
) {
  const query = buildQuery({
    search: (search || "").trim(),
    leadId,
    clientId,
    assignedToId,
    completed: completed === "" || completed === undefined ? undefined : completed,
    overdue: overdue ? "true" : undefined,
    page,
    size,
  });
  const response = await apiFetch(`/api/follow-ups${query}`, { token });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not load follow-ups.");
  }

  return {
    items: data && Array.isArray(data.followUps) ? data.followUps : [],
    total: (data && data.total) || 0,
    page: (data && data.page) || page,
    size: (data && data.size) || size,
    totalPages: (data && data.totalPages) || 0,
  };
}

export async function getFollowUp(token, id) {
  const response = await apiFetch(`/api/follow-ups/${encodeURIComponent(id)}`, {
    token,
  });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not load the follow-up.");
  }

  return data.followUp;
}

export async function createFollowUp(token, payload) {
  const response = await apiFetch("/api/follow-ups", {
    token,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not create the follow-up.");
  }

  return data.followUp;
}

export async function updateFollowUp(token, id, payload) {
  const response = await apiFetch(`/api/follow-ups/${encodeURIComponent(id)}`, {
    token,
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not update the follow-up.");
  }

  return data.followUp;
}

export async function deleteFollowUp(token, id) {
  const response = await apiFetch(`/api/follow-ups/${encodeURIComponent(id)}`, {
    token,
    method: "DELETE",
  });
  const data = await readJson(response);

  if (response.status === 403) {
    throw new ApiError("Only administrators can delete follow-ups.", {
      status: 403,
    });
  }

  if (!response.ok) {
    throw toApiError(response, data, "Could not delete the follow-up.");
  }
}

// ----- Finance: invoices --------------------------------------------------

export async function listInvoices(
  token,
  {
    search,
    status,
    clientId,
    projectId,
    overdue,
    page = 1,
    size = 20,
  } = {}
) {
  const query = buildQuery({
    search: (search || "").trim(),
    status,
    clientId,
    projectId,
    overdue: overdue ? "true" : undefined,
    page,
    size,
  });
  const response = await apiFetch(`/api/invoices${query}`, { token });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not load invoices.");
  }

  return {
    items: data && Array.isArray(data.invoices) ? data.invoices : [],
    total: (data && data.total) || 0,
    page: (data && data.page) || page,
    size: (data && data.size) || size,
    totalPages: (data && data.totalPages) || 0,
  };
}

export async function getInvoice(token, id) {
  const response = await apiFetch(`/api/invoices/${encodeURIComponent(id)}`, {
    token,
  });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not load the invoice.");
  }

  return data.invoice;
}

export async function listInvoicePayments(token, invoiceId) {
  const response = await apiFetch(
    `/api/invoices/${encodeURIComponent(invoiceId)}/payments`,
    { token }
  );
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not load invoice payments.");
  }

  return data && Array.isArray(data.payments) ? data.payments : [];
}

export async function createInvoice(token, payload) {
  const response = await apiFetch("/api/invoices", {
    token,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not create the invoice.");
  }

  return data.invoice;
}

export async function updateInvoice(token, id, payload) {
  const response = await apiFetch(`/api/invoices/${encodeURIComponent(id)}`, {
    token,
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not update the invoice.");
  }

  return data.invoice;
}

export async function deleteInvoice(token, id, { cascade = false } = {}) {
  const suffix = cascade ? "?cascade=true" : "";
  const response = await apiFetch(
    `/api/invoices/${encodeURIComponent(id)}${suffix}`,
    { token, method: "DELETE" }
  );
  const data = await readJson(response);

  if (response.status === 403) {
    throw new ApiError("Only administrators can delete invoices.", {
      status: 403,
    });
  }

  if (response.status === 409) {
    const conflict = new ApiError(
      (data && data.message) ||
        "This invoice has payments. Delete them first or cascade the delete.",
      { status: 409 }
    );
    conflict.code = "HAS_PAYMENTS";
    throw conflict;
  }

  if (!response.ok) {
    throw toApiError(response, data, "Could not delete the invoice.");
  }
}

// ----- Finance: payments --------------------------------------------------

export async function listPayments(
  token,
  { search, invoiceId, method, from, to, page = 1, size = 20 } = {}
) {
  const query = buildQuery({
    search: (search || "").trim(),
    invoiceId,
    method,
    from,
    to,
    page,
    size,
  });
  const response = await apiFetch(`/api/payments${query}`, { token });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not load payments.");
  }

  return {
    items: data && Array.isArray(data.payments) ? data.payments : [],
    total: (data && data.total) || 0,
    page: (data && data.page) || page,
    size: (data && data.size) || size,
    totalPages: (data && data.totalPages) || 0,
  };
}

export async function getPayment(token, id) {
  const response = await apiFetch(`/api/payments/${encodeURIComponent(id)}`, {
    token,
  });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not load the payment.");
  }

  return data.payment;
}

export async function createPayment(token, payload) {
  const response = await apiFetch("/api/payments", {
    token,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not record the payment.");
  }

  return data.payment;
}

export async function updatePayment(token, id, payload) {
  const response = await apiFetch(`/api/payments/${encodeURIComponent(id)}`, {
    token,
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not update the payment.");
  }

  return data.payment;
}

export async function deletePayment(token, id) {
  const response = await apiFetch(`/api/payments/${encodeURIComponent(id)}`, {
    token,
    method: "DELETE",
  });
  const data = await readJson(response);

  if (response.status === 403) {
    throw new ApiError("Only administrators can delete payments.", {
      status: 403,
    });
  }

  if (!response.ok) {
    throw toApiError(response, data, "Could not delete the payment.");
  }
}

// ----- Finance: expenses --------------------------------------------------

export async function listExpenses(
  token,
  { search, status, category, from, to, page = 1, size = 20 } = {}
) {
  const query = buildQuery({
    search: (search || "").trim(),
    status,
    category,
    from,
    to,
    page,
    size,
  });
  const response = await apiFetch(`/api/expenses${query}`, { token });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not load expenses.");
  }

  return {
    items: data && Array.isArray(data.expenses) ? data.expenses : [],
    total: (data && data.total) || 0,
    page: (data && data.page) || page,
    size: (data && data.size) || size,
    totalPages: (data && data.totalPages) || 0,
  };
}

export async function getExpense(token, id) {
  const response = await apiFetch(`/api/expenses/${encodeURIComponent(id)}`, {
    token,
  });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not load the expense.");
  }

  return data.expense;
}

export async function createExpense(token, payload) {
  const response = await apiFetch("/api/expenses", {
    token,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not create the expense.");
  }

  return data.expense;
}

export async function updateExpense(token, id, payload) {
  const response = await apiFetch(`/api/expenses/${encodeURIComponent(id)}`, {
    token,
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not update the expense.");
  }

  return data.expense;
}

export async function updateExpenseStatus(token, id, status) {
  const response = await apiFetch(
    `/api/expenses/${encodeURIComponent(id)}/status`,
    {
      token,
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ status }),
    }
  );
  const data = await readJson(response);

  if (response.status === 403) {
    throw new ApiError(
      "Only administrators can approve, reject or reimburse expenses.",
      { status: 403 }
    );
  }

  if (!response.ok) {
    throw toApiError(response, data, "Could not update the expense status.");
  }

  return data.expense;
}

export async function deleteExpense(token, id) {
  const response = await apiFetch(`/api/expenses/${encodeURIComponent(id)}`, {
    token,
    method: "DELETE",
  });
  const data = await readJson(response);

  if (response.status === 403) {
    throw new ApiError("Only administrators can delete expenses.", {
      status: 403,
    });
  }

  if (!response.ok) {
    throw toApiError(response, data, "Could not delete the expense.");
  }
}

// ----- Finance: reports ---------------------------------------------------

export async function fetchFinanceSummary(token, { from, to } = {}) {
  const query = buildQuery({ from, to });
  const response = await apiFetch(`/api/finance/summary${query}`, { token });
  const data = await readJson(response);

  if (!response.ok) {
    throw toApiError(response, data, "Could not load the financial summary.");
  }

  return data && data.summary ? data.summary : {};
}

/**
 * Streams a CSV export from the backend and triggers a browser download.
 * The JWT travels in the Authorization header (a plain <a> link could not
 * send it), so the file is fetched as a blob first.
 */
export async function downloadCsv(token, path, filename) {
  const response = await apiFetch(path, { token });

  if (response.status === 403) {
    throw new ApiError("You do not have permission to export this data.", {
      status: 403,
    });
  }

  if (!response.ok) {
    const data = await readJson(response);
    throw toApiError(response, data, "Could not export the data.");
  }

  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
