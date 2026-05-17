import type {
  AgentStep,
  AuditEvent,
  CodeSymbol,
  CliRuntimeSettings,
  CliSessionSummary,
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
  tasks: () => request<CodingTask[]>("/api/tasks"),
  task: (id: string) => request<CodingTask>(`/api/tasks/${id}`),
  taskPlan: (taskId: string) => request<PlanView>(`/api/tasks/${taskId}/plan`),
  taskSteps: (taskId: string) => request<AgentStep[]>(`/api/tasks/${taskId}/steps`),
  taskWorkflow: (taskId: string) => request<WorkflowDefinition>(`/api/tasks/${taskId}/workflow`),
  taskWorkflowNodes: (taskId: string) => request<WorkflowNodeExecution[]>(`/api/tasks/${taskId}/workflow/nodes`),
  taskWorkflowEdges: (taskId: string) => request<WorkflowEdgeDecision[]>(`/api/tasks/${taskId}/workflow/edges`),
  taskEvents: (taskId: string) => request<AuditEvent[]>(`/api/tasks/${taskId}/events`),
  taskChanges: (taskId: string) => request<FileChange[]>(`/api/tasks/${taskId}/changes`),
  taskFailures: (taskId: string) => request<RuntimeFailure[]>(`/api/tasks/${taskId}/failures`),
  taskReport: (taskId: string) => request<TaskReport>(`/api/tasks/${taskId}/report`),
  scanWorkspace: (workspaceId: string) => request<Record<string, any>>(`/api/workspaces/${workspaceId}/scan`, { method: "POST" }),
  profile: (workspaceId: string) => request<ProjectProfile>(`/api/workspaces/${workspaceId}/profile`),
  scanExecutions: (workspaceId: string) => request<Record<string, any>[]>(`/api/workspaces/${workspaceId}/scan-executions`),
  memory: (workspaceId: string) => request<ProjectMemoryItem[]>(`/api/workspaces/${workspaceId}/memory`),
  createMemory: (workspaceId: string, body: Record<string, any>) =>
    request<ProjectMemoryItem>(`/api/workspaces/${workspaceId}/memory`, { method: "POST", body }),
  memoryProposals: (workspaceId: string) => request<MemoryWriteProposal[]>(`/api/workspaces/${workspaceId}/memory-proposals`),
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
  deleteCommandPolicy: (id: string) => request<void>(`/api/command-policies/${id}`, { method: "DELETE" }),
  cliSettings: () => request<CliRuntimeSettings>("/api/cli/settings"),
  saveCliSettings: (body: CliRuntimeSettings) => request<CliRuntimeSettings>("/api/cli/settings", { method: "POST", body }),
  cliSessions: () => request<CliSessionSummary[]>("/api/cli/sessions")
};
