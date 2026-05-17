import { useQuery } from "@tanstack/react-query";
import { useParams, useSearchParams } from "react-router-dom";
import ReactMarkdown from "react-markdown";
import { api } from "../lib/api";
import { WorkflowGraph } from "../components/WorkflowGraph";
import { Badge, Card, CardContent, CardHeader, CardTitle, EmptyState, ErrorState, JsonBlock, Table, Td, Th } from "../components/ui";
import { compactId, formatDate } from "../lib/utils";

export function TaskDetail() {
  const { taskId = "" } = useParams();
  const [search] = useSearchParams();
  const effectiveTaskId = taskId || search.get("taskId") || "";
  const task = useQuery({ queryKey: ["task", effectiveTaskId], queryFn: () => api.task(effectiveTaskId), enabled: Boolean(effectiveTaskId), refetchInterval: 8000 });
  const plan = useQuery({ queryKey: ["plan", effectiveTaskId], queryFn: () => api.taskPlan(effectiveTaskId), enabled: Boolean(effectiveTaskId), retry: false });
  const steps = useQuery({ queryKey: ["steps", effectiveTaskId], queryFn: () => api.taskSteps(effectiveTaskId), enabled: Boolean(effectiveTaskId), retry: false });
  const nodes = useQuery({ queryKey: ["workflow-nodes", effectiveTaskId], queryFn: () => api.taskWorkflowNodes(effectiveTaskId), enabled: Boolean(effectiveTaskId), retry: false });
  const edges = useQuery({ queryKey: ["workflow-edges", effectiveTaskId], queryFn: () => api.taskWorkflowEdges(effectiveTaskId), enabled: Boolean(effectiveTaskId), retry: false });
  const events = useQuery({ queryKey: ["events", effectiveTaskId], queryFn: () => api.taskEvents(effectiveTaskId), enabled: Boolean(effectiveTaskId), retry: false });
  const changes = useQuery({ queryKey: ["changes", effectiveTaskId], queryFn: () => api.taskChanges(effectiveTaskId), enabled: Boolean(effectiveTaskId), retry: false });
  const failures = useQuery({ queryKey: ["task-failures", effectiveTaskId], queryFn: () => api.taskFailures(effectiveTaskId), enabled: Boolean(effectiveTaskId), retry: false });
  const report = useQuery({ queryKey: ["report", effectiveTaskId], queryFn: () => api.taskReport(effectiveTaskId), enabled: Boolean(effectiveTaskId), retry: false });
  const graphNodes = (nodes.data ?? []).map((node) => ({
    id: node.nodeId,
    type: node.nodeType,
    status: node.status,
    summary: node.outputSummary
  }));
  const graphEdges = (edges.data ?? []).map((edge) => ({
    id: edge.id,
    source: edge.fromNodeId,
    target: edge.toNodeId,
    type: edge.edgeType,
    condition: edge.conditionSummary || undefined,
    reason: edge.decisionReason,
    selected: edge.selected
  }));

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>任务 {compactId(effectiveTaskId)}</CardTitle>
          {task.data && <Badge>{task.data.status}</Badge>}
        </CardHeader>
        <CardContent>
          {task.error ? <ErrorState error={task.error} /> : <JsonBlock value={task.data ?? "正在加载任务信息..."} />}
        </CardContent>
      </Card>

      <div className="grid gap-4 xl:grid-cols-[1.2fr_0.8fr]">
        <Card>
          <CardHeader><CardTitle>工作流图</CardTitle></CardHeader>
          <CardContent>
            {nodes.data?.length ? <WorkflowGraph nodes={graphNodes} edges={graphEdges} /> : <EmptyState title="暂无工作流节点记录" />}
          </CardContent>
        </Card>
        <Card>
          <CardHeader><CardTitle>计划</CardTitle></CardHeader>
          <CardContent>{plan.error ? <ErrorState error={plan.error} /> : <JsonBlock value={plan.data ?? "暂无可用计划"} />}</CardContent>
        </Card>
      </div>

      <div className="grid gap-4 xl:grid-cols-2">
        <Card>
          <CardHeader><CardTitle>步骤</CardTitle></CardHeader>
          <CardContent>
            {steps.data?.length ? (
              <Table>
                <thead><tr><Th>类型</Th><Th>状态</Th><Th>输出</Th><Th>完成时间</Th></tr></thead>
                <tbody>
                  {steps.data.map((step) => (
                    <tr key={step.id}>
                      <Td>{step.stepType ?? step.type}</Td>
                      <Td><Badge>{step.status}</Badge></Td>
                      <Td className="max-w-md truncate">{step.outputSummary}</Td>
                      <Td>{formatDate(step.completedAt)}</Td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            ) : <EmptyState title="暂无步骤" />}
          </CardContent>
        </Card>
        <Card>
          <CardHeader><CardTitle>失败记录</CardTitle></CardHeader>
          <CardContent><JsonBlock value={failures.data ?? []} /></CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader><CardTitle>文件变更</CardTitle></CardHeader>
        <CardContent className="space-y-3">
          {changes.data?.length ? changes.data.map((change) => (
            <div key={change.id} className="rounded-md border p-3">
              <div className="flex flex-wrap items-center gap-2 text-sm">
                <span className="mono">{change.path}</span>
                <Badge>{change.operation}</Badge>
                <span className="text-muted-foreground">+{change.lineAdded ?? 0} -{change.lineDeleted ?? 0}</span>
              </div>
              <JsonBlock className="mt-3" value={change.diffText ?? change} />
            </div>
          )) : <EmptyState title="暂无文件变更" />}
        </CardContent>
      </Card>

      <div className="grid gap-4 xl:grid-cols-[0.8fr_1.2fr]">
        <Card>
          <CardHeader><CardTitle>审计事件</CardTitle></CardHeader>
          <CardContent><JsonBlock value={events.data?.slice(-40) ?? []} /></CardContent>
        </Card>
        <Card>
          <CardHeader><CardTitle>报告</CardTitle></CardHeader>
          <CardContent className="prose prose-invert max-w-none prose-pre:bg-background prose-pre:text-xs">
            {report.data?.contentMd ? <ReactMarkdown>{report.data.contentMd}</ReactMarkdown> : <EmptyState title="暂无报告" />}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
