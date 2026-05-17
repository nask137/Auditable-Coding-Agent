import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { api } from "../lib/api";
import { Badge, Card, CardContent, CardHeader, CardTitle, EmptyState, ErrorState, JsonBlock, Select } from "../components/ui";
import { compactId, formatDate } from "../lib/utils";

type ReplayItem = {
  id: string;
  type: string;
  title: string;
  time?: string;
  payload: unknown;
};

const replayTypeLabels: Record<string, string> = {
  node: "节点",
  edge: "边",
  event: "审计",
  change: "变更",
  failure: "失败"
};

export function Replay() {
  const [taskId, setTaskId] = useState("");
  const [filter, setFilter] = useState("all");
  const tasks = useQuery({ queryKey: ["tasks"], queryFn: api.tasks });
  const nodes = useQuery({ queryKey: ["replay-nodes", taskId], queryFn: () => api.taskWorkflowNodes(taskId), enabled: Boolean(taskId), retry: false });
  const edges = useQuery({ queryKey: ["replay-edges", taskId], queryFn: () => api.taskWorkflowEdges(taskId), enabled: Boolean(taskId), retry: false });
  const events = useQuery({ queryKey: ["replay-events", taskId], queryFn: () => api.taskEvents(taskId), enabled: Boolean(taskId), retry: false });
  const changes = useQuery({ queryKey: ["replay-changes", taskId], queryFn: () => api.taskChanges(taskId), enabled: Boolean(taskId), retry: false });
  const failures = useQuery({ queryKey: ["replay-failures", taskId], queryFn: () => api.taskFailures(taskId), enabled: Boolean(taskId), retry: false });

  const timeline = useMemo<ReplayItem[]>(() => {
    const items: ReplayItem[] = [
      ...(nodes.data ?? []).map((node) => ({ id: node.id, type: "node", title: `${node.nodeId} ${node.status}`, time: node.completedAt ?? node.startedAt, payload: node })),
      ...(edges.data ?? []).map((edge) => ({ id: edge.id, type: "edge", title: `${edge.fromNodeId} -> ${edge.toNodeId}`, time: edge.createdAt, payload: edge })),
      ...(events.data ?? []).map((event) => ({ id: event.id, type: "event", title: event.eventType ?? event.actionType ?? "审计事件", time: event.createdAt, payload: event })),
      ...(changes.data ?? []).map((change) => ({ id: change.id, type: "change", title: change.path ?? "文件变更", time: change.createdAt, payload: change })),
      ...(failures.data ?? []).map((failure) => ({ id: failure.id, type: "failure", title: failure.message ?? failure.failureType ?? "失败", time: failure.createdAt, payload: failure }))
    ];
    return items
      .filter((item) => filter === "all" || item.type === filter)
      .sort((a, b) => new Date(a.time ?? 0).getTime() - new Date(b.time ?? 0).getTime());
  }, [nodes.data, edges.data, events.data, changes.data, failures.data, filter]);

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader><CardTitle>回放输入</CardTitle></CardHeader>
        <CardContent className="grid gap-3 md:grid-cols-[1fr_180px]">
          <Select value={taskId} onChange={(e) => setTaskId(e.target.value)}>
            <option value="">选择任务</option>
            {tasks.data?.map((task) => (
              <option key={task.id} value={task.id}>
                {compactId(task.id)} · {task.status} · {task.title ?? task.userRequest}
              </option>
            ))}
          </Select>
          <Select value={filter} onChange={(e) => setFilter(e.target.value)}>
            <option value="all">全部事件</option>
            <option value="node">工作流节点</option>
            <option value="edge">边</option>
            <option value="event">审计事件</option>
            <option value="change">文件变更</option>
            <option value="failure">失败记录</option>
          </Select>
          {tasks.error && <div className="md:col-span-2"><ErrorState error={tasks.error} /></div>}
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle>执行时间线</CardTitle></CardHeader>
        <CardContent>
          {timeline.length ? (
            <div className="space-y-3">
              {timeline.map((item) => (
                <div key={`${item.type}-${item.id}`} className="grid gap-3 rounded-md border p-3 md:grid-cols-[170px_1fr]">
                  <div>
                    <Badge>{replayTypeLabels[item.type] ?? item.type}</Badge>
                    <div className="mt-2 text-xs text-muted-foreground">{formatDate(item.time)}</div>
                  </div>
                  <div>
                    <div className="text-sm font-medium">{item.title}</div>
                    <JsonBlock className="mt-2 max-h-56" value={item.payload} />
                  </div>
                </div>
              ))}
            </div>
          ) : <EmptyState title="暂无回放数据" detail="选择任务可查看完整回放。" />}
        </CardContent>
      </Card>
    </div>
  );
}
