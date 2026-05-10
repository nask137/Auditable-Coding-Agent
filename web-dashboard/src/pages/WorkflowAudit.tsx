import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { WorkflowGraph, type WorkflowGraphEdge, type WorkflowGraphNode } from "../components/WorkflowGraph";
import { Badge, Card, CardContent, CardHeader, CardTitle, EmptyState, ErrorState, Input, Select, Table, Td, Th } from "../components/ui";
import { api } from "../lib/api";
import { compactId, formatDate } from "../lib/utils";
import type { WorkflowDefinition, WorkflowEdgeDecision, WorkflowNodeExecution } from "../types";

export function WorkflowAudit() {
  const [runId, setRunId] = useState("");
  const workflows = useQuery({ queryKey: ["workflows"], queryFn: api.workflows });
  const [workflowId, setWorkflowId] = useState("");
  const selectedWorkflowId = workflowId || workflows.data?.[0]?.id || "";
  const workflow = useQuery({ queryKey: ["workflow", selectedWorkflowId], queryFn: () => api.workflow(selectedWorkflowId), enabled: Boolean(selectedWorkflowId) });
  const runWorkflow = useQuery({ queryKey: ["run-workflow", runId], queryFn: () => api.runWorkflow(runId), enabled: Boolean(runId), retry: false });
  const nodes = useQuery({ queryKey: ["workflow-nodes", runId], queryFn: () => api.runWorkflowNodes(runId), enabled: Boolean(runId), retry: false });
  const edges = useQuery({ queryKey: ["workflow-edges", runId], queryFn: () => api.runWorkflowEdges(runId), enabled: Boolean(runId), retry: false });
  const definitionGraph = workflow.data ? graphFromDefinition(workflow.data) : { nodes: [], edges: [] };
  const executionGraph = graphFromExecution(nodes.data ?? [], edges.data ?? []);
  const lastNode = nodes.data?.at(-1);

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>工作流定义</CardTitle>
          <Select className="w-80" value={selectedWorkflowId} onChange={(e) => setWorkflowId(e.target.value)}>
            {workflows.data?.map((item) => <option key={item.id} value={item.id}>{item.name} v{item.version}</option>)}
          </Select>
        </CardHeader>
        <CardContent className="space-y-4">
          {workflow.error ? <ErrorState error={workflow.error} /> : (
            workflow.data ? (
              <>
                <div className="grid gap-3 text-sm md:grid-cols-4">
                  <InfoTile label="起始节点" value={workflow.data.definition.start ?? "-"} />
                  <InfoTile label="模式" value={workflow.data.mode} />
                  <InfoTile label="节点数" value={String(definitionGraph.nodes.length)} />
                  <InfoTile label="边数" value={String(definitionGraph.edges.length)} />
                </div>
                <WorkflowGraph nodes={definitionGraph.nodes} edges={definitionGraph.edges} heightClassName="h-[360px]" />
                <div className="grid gap-4 xl:grid-cols-2">
                  <DefinitionNodes nodes={definitionGraph.nodes} start={workflow.data.definition.start} />
                  <DefinitionEdges edges={definitionGraph.edges} />
                </div>
              </>
            ) : <EmptyState title="正在加载工作流..." />
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>运行执行路径</CardTitle>
          <Input className="w-96" placeholder="运行 ID" value={runId} onChange={(e) => setRunId(e.target.value)} />
        </CardHeader>
        <CardContent className="space-y-4">
          {runWorkflow.data && (
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <span>{runWorkflow.data.name}</span>
              <Badge>{runWorkflow.data.mode}</Badge>
              <span className="mono text-muted-foreground">{compactId(runWorkflow.data.id)}</span>
              <span className="text-muted-foreground">已执行 {nodes.data?.length ?? 0} 个节点 / {edges.data?.length ?? 0} 条边</span>
              {lastNode ? <span className="text-muted-foreground">当前到达 {lastNode.nodeId}</span> : null}
            </div>
          )}
          {nodes.data?.length ? <WorkflowGraph nodes={executionGraph.nodes} edges={executionGraph.edges} /> : <EmptyState title="输入运行 ID 以可视化执行过程" />}
        </CardContent>
      </Card>

      <div className="grid gap-4 xl:grid-cols-2">
        <Card>
          <CardHeader><CardTitle>节点执行</CardTitle></CardHeader>
          <CardContent>
            {nodes.data?.length ? (
              <Table>
                <thead><tr><Th>节点</Th><Th>类型</Th><Th>状态</Th><Th>完成时间</Th></tr></thead>
                <tbody>
                  {nodes.data.map((node) => (
                    <tr key={node.id}>
                      <Td>{node.nodeId}</Td>
                      <Td>{node.nodeType}</Td>
                      <Td><Badge>{node.status}</Badge></Td>
                      <Td>{formatDate(node.completedAt)}</Td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            ) : <EmptyState title="暂无节点" />}
          </CardContent>
        </Card>
        <Card>
          <CardHeader><CardTitle>边决策</CardTitle></CardHeader>
          <CardContent>
            {edges.data?.length ? (
              <Table>
                <thead><tr><Th>来源</Th><Th>目标</Th><Th>类型</Th><Th>条件</Th><Th>判定依据</Th></tr></thead>
                <tbody>
                  {edges.data.map((edge) => (
                    <tr key={edge.id}>
                      <Td>{edge.fromNodeId}</Td>
                      <Td>{edge.toNodeId}</Td>
                      <Td>{edge.edgeType}</Td>
                      <Td className="mono text-xs text-muted-foreground">{edge.conditionSummary || "-"}</Td>
                      <Td className="max-w-xl">
                        <div>{edge.decisionReason}</div>
                        <EdgeMetadata metadata={edge.metadataJson} />
                      </Td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            ) : <EmptyState title="暂无边" />}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function InfoTile({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md border bg-background px-3 py-2">
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className="mt-1 truncate text-sm font-medium">{value}</div>
    </div>
  );
}

function DefinitionNodes({ nodes, start }: { nodes: WorkflowGraphNode[]; start?: string }) {
  return (
    <div className="overflow-hidden rounded-md border">
      <Table>
        <thead><tr><Th>节点</Th><Th>类型</Th><Th>角色</Th></tr></thead>
        <tbody>
          {nodes.map((node) => (
            <tr key={node.id}>
              <Td>{node.id}</Td>
              <Td>{node.type}</Td>
              <Td>{node.id === start ? <Badge tone="success">START</Badge> : <span className="text-muted-foreground">STEP</span>}</Td>
            </tr>
          ))}
        </tbody>
      </Table>
    </div>
  );
}

function DefinitionEdges({ edges }: { edges: WorkflowGraphEdge[] }) {
  return (
    <div className="overflow-hidden rounded-md border">
      <Table>
        <thead><tr><Th>来源</Th><Th>目标</Th><Th>类型</Th><Th>条件</Th></tr></thead>
        <tbody>
          {edges.map((edge) => (
            <tr key={edge.id}>
              <Td>{edge.source}</Td>
              <Td>{edge.target}</Td>
              <Td>{edge.type}</Td>
              <Td className="mono text-xs text-muted-foreground">{edge.condition || "-"}</Td>
            </tr>
          ))}
        </tbody>
      </Table>
    </div>
  );
}

function EdgeMetadata({ metadata }: { metadata?: Record<string, any> }) {
  if (!metadata || !Object.keys(metadata).length) return null;
  const facts = [
    metadata.lastStatus ? `上一节点状态: ${metadata.lastStatus}` : "",
    metadata.currentPlanItem ? `当前计划项: ${metadata.currentPlanItem}` : "",
    metadata.lastSummary ? `节点输出: ${metadata.lastSummary}` : ""
  ].filter(Boolean);
  if (!facts.length) return null;
  return <div className="mt-1 text-xs leading-5 text-muted-foreground">{facts.join("；")}</div>;
}

function graphFromDefinition(workflow: WorkflowDefinition): { nodes: WorkflowGraphNode[]; edges: WorkflowGraphEdge[] } {
  const nodes = (workflow.definition.nodes ?? []).map((node) => {
    if (typeof node === "string") {
      return { id: node, type: "NODE" };
    }
    return { id: node.id, type: node.type ?? "NODE" };
  });
  const edges = (workflow.definition.edges ?? []).map((edge, index) => ({
    id: `${edge.from}-${edge.to}-${index}`,
    source: edge.from,
    target: edge.to,
    type: edge.type ?? "ON_SUCCESS",
    condition: edge.condition || undefined
  }));
  return { nodes, edges };
}

function graphFromExecution(
  nodeExecutions: WorkflowNodeExecution[],
  edgeDecisions: WorkflowEdgeDecision[]
): { nodes: WorkflowGraphNode[]; edges: WorkflowGraphEdge[] } {
  return {
    nodes: nodeExecutions.map((node) => ({
      id: node.nodeId,
      type: node.nodeType,
      status: node.status,
      summary: node.outputSummary
    })),
    edges: edgeDecisions.map((edge) => ({
      id: edge.id,
      source: edge.fromNodeId,
      target: edge.toNodeId,
      type: edge.edgeType,
      condition: edge.conditionSummary || undefined,
      reason: edge.decisionReason,
      selected: edge.selected
    }))
  };
}
