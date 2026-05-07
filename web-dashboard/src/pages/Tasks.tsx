import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../lib/api";
import { Badge, Button, Card, CardContent, CardHeader, CardTitle, ErrorState, Input, Select, Textarea } from "../components/ui";
import { compactId, formatDate } from "../lib/utils";

export function Tasks() {
  const qc = useQueryClient();
  const workspaces = useQuery({ queryKey: ["workspaces"], queryFn: api.workspaces });
  const workflows = useQuery({ queryKey: ["workflows"], queryFn: api.workflows });
  const [workspaceId, setWorkspaceId] = useState("");
  const [workflow, setWorkflow] = useState("coding-agent");
  const [request, setRequest] = useState("");
  const [createdTaskId, setCreatedTaskId] = useState("");
  const [runId, setRunId] = useState("");

  const resolvedWorkspace = workspaceId || workspaces.data?.[0]?.id || "";
  const createTask = useMutation({
    mutationFn: () => api.createTask({ workspaceId: resolvedWorkspace, userRequest: request }),
    onSuccess: (task) => {
      setCreatedTaskId(task.id);
      qc.invalidateQueries({ queryKey: ["task", task.id] });
    }
  });
  const startTask = useMutation({
    mutationFn: () => api.startTask(createdTaskId, workflow),
    onSuccess: (run) => setRunId(run.id)
  });
  const task = useQuery({ queryKey: ["task", createdTaskId], queryFn: () => api.task(createdTaskId), enabled: Boolean(createdTaskId) });
  const run = useQuery({ queryKey: ["run", runId], queryFn: () => api.run(runId), enabled: Boolean(runId), refetchInterval: 8000 });

  return (
    <div className="grid gap-4 xl:grid-cols-[420px_1fr]">
      <Card>
        <CardHeader><CardTitle>创建并启动任务</CardTitle></CardHeader>
        <CardContent className="space-y-3">
          <Select value={resolvedWorkspace} onChange={(e) => setWorkspaceId(e.target.value)}>
            {workspaces.data?.map((workspace) => <option key={workspace.id} value={workspace.id}>{workspace.name}</option>)}
          </Select>
          <Select value={workflow} onChange={(e) => setWorkflow(e.target.value)}>
            {workflows.data?.map((item) => <option key={item.id} value={item.name}>{item.name}</option>)}
          </Select>
          <Textarea placeholder="描述 coding agent 任务" value={request} onChange={(e) => setRequest(e.target.value)} />
          <div className="flex gap-2">
            <Button disabled={!resolvedWorkspace || !request || createTask.isPending} onClick={() => createTask.mutate()}>创建</Button>
            <Button variant="secondary" disabled={!createdTaskId || startTask.isPending} onClick={() => startTask.mutate()}>启动</Button>
          </div>
          {(createTask.error || startTask.error) && <ErrorState error={createTask.error || startTask.error} />}
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle>当前任务</CardTitle></CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-3 md:grid-cols-2">
            <div className="rounded-md border p-3">
              <div className="text-xs uppercase text-muted-foreground">任务</div>
              <div className="mt-2 mono text-sm">{compactId(createdTaskId)}</div>
              {task.data && <div className="mt-2"><Badge>{task.data.status}</Badge></div>}
            </div>
            <div className="rounded-md border p-3">
              <div className="text-xs uppercase text-muted-foreground">运行</div>
              <div className="mt-2 mono text-sm">{compactId(runId)}</div>
              {run.data && <div className="mt-2"><Badge>{run.data.status}</Badge></div>}
            </div>
          </div>
          {runId ? <Link className="text-sm text-primary underline" to={`/runs/${runId}?taskId=${createdTaskId}`}>打开运行详情</Link> : null}
          {run.data ? (
            <div className="text-sm text-muted-foreground">
              启动时间 {formatDate(run.data.startedAt)}。完成时间 {formatDate(run.data.completedAt)}。
            </div>
          ) : null}
          <Input placeholder="输入已有运行 ID" value={runId} onChange={(e) => setRunId(e.target.value)} />
          <Input placeholder="输入关联任务 ID，用于观察接口" value={createdTaskId} onChange={(e) => setCreatedTaskId(e.target.value)} />
        </CardContent>
      </Card>
    </div>
  );
}
