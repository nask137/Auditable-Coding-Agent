export type Id = string;

export type Workspace = {
  id: Id;
  name: string;
  rootPath: string;
  trusted: boolean;
  createdAt?: string;
};

export type WorkflowDefinition = {
  id: Id;
  name: string;
  version: number;
  description: string;
  mode: string;
  enabled: boolean;
  definition: {
    start?: string;
    limits?: Record<string, any>;
    nodes?: Array<string | { id: string; type?: string; input?: Record<string, any> }>;
    edges?: Array<{ from: string; to: string; type?: string; condition?: string }>;
    [key: string]: any;
  };
  createdAt?: string;
  updatedAt?: string;
};

export type CodingTask = {
  id: Id;
  workspaceId: Id;
  title?: string;
  userRequest: string;
  status: string;
  createdAt?: string;
  updatedAt?: string;
};

export type AgentRun = {
  id: Id;
  taskId: Id;
  workflowName?: string;
  status: string;
  startedAt?: string;
  finishedAt?: string;
  completedAt?: string;
  failureReason?: string;
};

export type PlanView = {
  plan: Record<string, any>;
  items: Array<Record<string, any>>;
};

export type AgentStep = Record<string, any> & {
  id: Id;
  stepType?: string;
  status?: string;
  inputSummary?: string;
  outputSummary?: string;
  createdAt?: string;
  completedAt?: string;
};

export type WorkflowNodeExecution = {
  id: Id;
  taskId: Id;
  runId: Id;
  workflowDefinitionId: Id;
  nodeId: string;
  nodeType: string;
  stepId?: Id | null;
  status: string;
  inputSummary?: string;
  outputSummary?: string;
  metadataJson?: Record<string, any>;
  startedAt?: string;
  completedAt?: string;
};

export type WorkflowEdgeDecision = {
  id: Id;
  taskId: Id;
  runId: Id;
  workflowDefinitionId: Id;
  fromNodeId: string;
  toNodeId: string;
  edgeType: string;
  conditionSummary?: string;
  decisionReason?: string;
  selected: boolean;
  metadataJson?: Record<string, any>;
  createdAt?: string;
};

export type AuditEvent = Record<string, any> & {
  id: Id;
  taskId?: Id;
  runId?: Id;
  eventType?: string;
  actor?: string;
  actionType?: string;
  inputSummary?: string;
  outputSummary?: string;
  riskLevel?: string;
  approvalStatus?: string;
  createdAt?: string;
};

export type FileChange = Record<string, any> & {
  id: Id;
  path?: string;
  operation?: string;
  diffText?: string;
  lineAdded?: number;
  lineDeleted?: number;
  riskLevel?: string;
  createdAt?: string;
};

export type RuntimeFailure = Record<string, any> & {
  id: Id;
  failureType?: string;
  recoveryStrategy?: string;
  message?: string;
  createdAt?: string;
};

export type TaskReport = {
  id: Id;
  taskId: Id;
  runId: Id;
  contentMd: string;
  createdAt?: string;
};

export type ProjectProfile = Record<string, any> & {
  id: Id;
  workspaceId: Id;
};

export type ProjectMemoryItem = Record<string, any> & {
  id: Id;
  workspaceId: Id;
  memoryType: string;
  title: string;
  content: string;
  status: string;
  createdAt?: string;
};

export type MemoryWriteProposal = Record<string, any> & {
  id: Id;
  workspaceId: Id;
  proposalType?: string;
  title: string;
  content: string;
  status: string;
  approvalRequestId?: Id;
  createdAt?: string;
};

export type CodeSymbol = Record<string, any> & {
  id: Id;
  path: string;
  symbolType: string;
  symbolName: string;
  signature?: string;
  lineStart?: number;
};

export type MemoryContext = Record<string, any> & {
  retrievalId?: Id;
  profile?: ProjectProfile;
  results?: Array<Record<string, any>>;
  sourceReferences?: Array<Record<string, any>>;
};

export type CommandPolicy = Record<string, any> & {
  id: Id;
  workspaceId: Id;
  executable: string;
  argumentsPattern?: string;
  policyType: string;
  riskLevel: string;
};

export type CliRuntimeSettings = {
  baseUrl: string;
  workspaceId: string;
  workflow: string;
  permissionPreset: string;
  model: string;
  profile: string;
};

export type CliSessionSummary = {
  sessionId: string;
  workspaceId?: string;
  runId?: string;
  taskId?: string;
  status?: string;
  updatedAt?: string;
};
