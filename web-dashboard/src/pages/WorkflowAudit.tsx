import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { WorkflowGraph } from "../components/WorkflowGraph";
import { Badge, Card, CardContent, CardHeader, CardTitle, EmptyState, ErrorState, Input, JsonBlock, Select, Table, Td, Th } from "../components/ui";
import { api } from "../lib/api";
import { compactId, formatDate } from "../lib/utils";

export function WorkflowAudit() {
  const [runId, setRunId] = useState("");
  const workflows = useQuery({ queryKey: ["workflows"], queryFn: api.workflows });
  const [workflowId, setWorkflowId] = useState("");
  const selectedWorkflowId = workflowId || workflows.data?.[0]?.id || "";
  const workflow = useQuery({ queryKey: ["workflow", selectedWorkflowId], queryFn: () => api.workflow(selectedWorkflowId), enabled: Boolean(selectedWorkflowId) });
  const runWorkflow = useQuery({ queryKey: ["run-workflow", runId], queryFn: () => api.runWorkflow(runId), enabled: Boolean(runId), retry: false });
  const nodes = useQuery({ queryKey: ["workflow-nodes", runId], queryFn: () => api.runWorkflowNodes(runId), enabled: Boolean(runId), retry: false });
  const edges = useQuery({ queryKey: ["workflow-edges", runId], queryFn: () => api.runWorkflowEdges(runId), enabled: Boolean(runId), retry: false });

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>工作流定义</CardTitle>
          <Select className="w-80" value={selectedWorkflowId} onChange={(e) => setWorkflowId(e.target.value)}>
            {workflows.data?.map((item) => <option key={item.id} value={item.id}>{item.name} v{item.version}</option>)}
          </Select>
        </CardHeader>
        <CardContent>
          {workflow.error ? <ErrorState error={workflow.error} /> : <JsonBlock value={workflow.data ?? "正在加载工作流..."} />}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>运行执行路径</CardTitle>
          <Input className="w-96" placeholder="运行 ID" value={runId} onChange={(e) => setRunId(e.target.value)} />
        </CardHeader>
        <CardContent className="space-y-4">
          {runWorkflow.data && (
            <div className="flex items-center gap-2 text-sm">
              <span>{runWorkflow.data.name}</span>
              <Badge>{runWorkflow.data.mode}</Badge>
              <span className="mono text-muted-foreground">{compactId(runWorkflow.data.id)}</span>
            </div>
          )}
          {nodes.data?.length ? <WorkflowGraph nodes={nodes.data} edges={edges.data ?? []} /> : <EmptyState title="输入运行 ID 以可视化执行过程" />}
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
                <thead><tr><Th>来源</Th><Th>目标</Th><Th>类型</Th><Th>原因</Th></tr></thead>
                <tbody>
                  {edges.data.map((edge) => (
                    <tr key={edge.id}>
                      <Td>{edge.fromNode}</Td>
                      <Td>{edge.toNode}</Td>
                      <Td>{edge.edgeType}</Td>
                      <Td className="max-w-md truncate">{edge.decisionReason}</Td>
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
