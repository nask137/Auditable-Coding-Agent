import type {
  AgentRun,
  AgentStep,
  ApprovalRequest,
  AuditEvent,
  CodeSymbol,
  CodingTask,
  CommandPolicy,
  FileChange,
  MemoryContext,
  MemoryWriteProposal,
  PlanView,
  ProjectMemoryItem,
  ProjectProfile,
  RuntimeFailure,
  TaskReport,
  UserInputRequest,
  WorkflowDefinition,
  WorkflowEdgeDecision,
  WorkflowNodeExecution,
  Workspace
} from "../types";

const configuredBase = import.meta.env.VITE_API_BASE_URL as string | undefined;
export const API_BASE_URL = configuredBase?.replace(/\/$/, "") || "";

type RequestOptions = {
  method?: "GET" | "POST" | "DELETE";
  body?: unknown;
};

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: options.method ?? "GET",
    headers: options.body == null ? undefined : { "Content-Type": "application/json" },
    body: options.body == null ? undefined : JSON.stringify(options.body)
  });

  if (!response.ok) {
    const detail = await response.text();
    throw new Error(`${response.status} ${response.statusText}${detail ? `: ${detail}` : ""}`);
  }

  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export const api = {
  workspaces: () => request<Workspace[]>("/api/workspaces"),
  createWorkspace: (body: { name: string; rootPath: string; trusted: boolean }) =>
    request<Workspace>("/api/workspaces", { method: "POST", body }),
  workflows: () => request<WorkflowDefinition[]>("/api/workflows"),
  workflow: (id: string) => request<WorkflowDefinition>(`/api/workflows/${id}`),
  createTask: (body: { workspaceId: string; userRequest: string }) =>
    request<CodingTask>("/api/tasks", { method: "POST", body }),
  task: (id: string) => request<CodingTask>(`/api/tasks/${id}`),
  startTask: (taskId: string, workflow = "coding-agent") =>
    request<AgentRun>(`/api/tasks/${taskId}/start?workflow=${encodeURIComponent(workflow)}`, { method: "POST" }),
  cancelTask: (taskId: string) => request<CodingTask>(`/api/tasks/${taskId}/cancel`, { method: "POST" }),
  run: (runId: string) => request<AgentRun>(`/api/runs/${runId}`),
  runPlan: (runId: string) => request<PlanView>(`/api/runs/${runId}/plan`),
  runSteps: (runId: string) => request<AgentStep[]>(`/api/runs/${runId}/steps`),
  runFailures: (runId: string) => request<RuntimeFailure[]>(`/api/runs/${runId}/failures`),
  runWorkflow: (runId: string) => request<WorkflowDefinition>(`/api/runs/${runId}/workflow`),
  runWorkflowNodes: (runId: string) => request<WorkflowNodeExecution[]>(`/api/runs/${runId}/workflow/nodes`),
  runWorkflowEdges: (runId: string) => request<WorkflowEdgeDecision[]>(`/api/runs/${runId}/workflow/edges`),
  taskEvents: (taskId: string) => request<AuditEvent[]>(`/api/tasks/${taskId}/events`),
  taskChanges: (taskId: string) => request<FileChange[]>(`/api/tasks/${taskId}/changes`),
  taskFailures: (taskId: string) => request<RuntimeFailure[]>(`/api/tasks/${taskId}/failures`),
  taskReport: (taskId: string) => request<TaskReport>(`/api/tasks/${taskId}/report`),
  approvals: (status?: string) => request<ApprovalRequest[]>(`/api/approvals${status ? `?status=${status}` : ""}`),
  approve: (id: string, reason?: string) =>
    request<ApprovalRequest>(`/api/approvals/${id}/approve`, { method: "POST", body: { resolvedBy: "web-dashboard", reason } }),
  deny: (id: string, reason = "Denied from web dashboard") =>
    request<ApprovalRequest>(`/api/approvals/${id}/deny`, { method: "POST", body: { resolvedBy: "web-dashboard", reason } }),
  userInputs: (status?: string) =>
    request<UserInputRequest[]>(`/api/user-input-requests${status ? `?status=${status}` : ""}`),
  answerUserInput: (id: string, answer: string) =>
    request<UserInputRequest>(`/api/user-input-requests/${id}/answer`, { method: "POST", body: { answer } }),
  cancelUserInput: (id: string) => request<UserInputRequest>(`/api/user-input-requests/${id}/cancel`, { method: "POST" }),
  scanWorkspace: (workspaceId: string) => request<Record<string, any>>(`/api/workspaces/${workspaceId}/scan`, { method: "POST" }),
  profile: (workspaceId: string) => request<ProjectProfile>(`/api/workspaces/${workspaceId}/profile`),
  scanRuns: (workspaceId: string) => request<Record<string, any>[]>(`/api/workspaces/${workspaceId}/scan-runs`),
  memory: (workspaceId: string) => request<ProjectMemoryItem[]>(`/api/workspaces/${workspaceId}/memory`),
  createMemory: (workspaceId: string, body: Record<string, any>) =>
    request<ProjectMemoryItem>(`/api/workspaces/${workspaceId}/memory`, { method: "POST", body }),
  memoryProposals: (workspaceId: string) => request<MemoryWriteProposal[]>(`/api/workspaces/${workspaceId}/memory-proposals`),
  approveMemoryProposal: (id: string) =>
    request<MemoryWriteProposal>(`/api/memory-proposals/${id}/approve`, { method: "POST", body: { resolvedBy: "web-dashboard" } }),
  rejectMemoryProposal: (id: string, reason = "Not reusable from web dashboard") =>
    request<MemoryWriteProposal>(`/api/memory-proposals/${id}/reject`, { method: "POST", body: { resolvedBy: "web-dashboard", reason } }),
  context: (workspaceId: string, q: string, limit = 10) =>
    request<MemoryContext>(`/api/workspaces/${workspaceId}/search-context?q=${encodeURIComponent(q)}&limit=${limit}`),
  symbols: (workspaceId: string, query = "", type = "") =>
    request<CodeSymbol[]>(`/api/workspaces/${workspaceId}/symbols?query=${encodeURIComponent(query)}${type ? `&type=${type}` : ""}`),
  outline: (workspaceId: string, path: string) =>
    request<CodeSymbol[]>(`/api/workspaces/${workspaceId}/outline?path=${encodeURIComponent(path)}`),
  retrievals: (workspaceId: string) => request<Record<string, any>[]>(`/api/workspaces/${workspaceId}/memory-retrievals`),
  commandPolicies: (workspaceId: string) => request<CommandPolicy[]>(`/api/workspaces/${workspaceId}/command-policies`),
  createCommandPolicy: (workspaceId: string, body: Record<string, any>) =>
    request<CommandPolicy>(`/api/workspaces/${workspaceId}/command-policies`, { method: "POST", body }),
  deleteCommandPolicy: (id: string) => request<void>(`/api/command-policies/${id}`, { method: "DELETE" })
};
