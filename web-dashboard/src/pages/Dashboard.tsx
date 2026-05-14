import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { Brain, Database, GitBranch, ListChecks, Workflow } from "lucide-react";
import { api } from "../lib/api";
import { formatDate } from "../lib/utils";
import { Badge, Card, CardContent, CardHeader, CardTitle, EmptyState, ErrorState, Table, Td, Th } from "../components/ui";

export function Dashboard() {
  const workspaces = useQuery({ queryKey: ["workspaces"], queryFn: api.workspaces });
  const workflows = useQuery({ queryKey: ["workflows"], queryFn: api.workflows });
  const tasks = useQuery({ queryKey: ["tasks"], queryFn: api.tasks });
  const runs = useQuery({ queryKey: ["runs"], queryFn: api.runs });

  const cards = [
    { label: "工作区", value: workspaces.data?.length ?? 0, icon: Database, to: "/workspaces" },
    { label: "工作流", value: workflows.data?.length ?? 0, icon: Workflow, to: "/workflow" },
    { label: "任务记录", value: tasks.data?.length ?? 0, icon: ListChecks },
    { label: "运行记录", value: runs.data?.length ?? 0, icon: GitBranch, to: "/replay" }
  ];

  const firstWorkspace = workspaces.data?.[0];
  const memory = useQuery({
    queryKey: ["memory", firstWorkspace?.id],
    queryFn: () => api.memory(firstWorkspace!.id),
    enabled: Boolean(firstWorkspace)
  });
  const proposals = useQuery({
    queryKey: ["memory-proposals", firstWorkspace?.id],
    queryFn: () => api.memoryProposals(firstWorkspace!.id),
    enabled: Boolean(firstWorkspace)
  });

  return (
    <div className="space-y-4">
      <div className="grid gap-3 md:grid-cols-4">
        {cards.map((card) => (
          card.to ? (
            <Link key={card.label} to={card.to}>
              <Card className="transition hover:border-primary/50">
                <CardContent className="flex items-center justify-between">
                  <div>
                    <div className="text-xs uppercase text-muted-foreground">{card.label}</div>
                    <div className="mt-2 text-3xl font-semibold">{card.value}</div>
                  </div>
                  <card.icon className="h-7 w-7 text-primary" />
                </CardContent>
              </Card>
            </Link>
          ) : (
            <Card key={card.label}>
              <CardContent className="flex items-center justify-between">
                <div>
                  <div className="text-xs uppercase text-muted-foreground">{card.label}</div>
                  <div className="mt-2 text-3xl font-semibold">{card.value}</div>
                </div>
                <card.icon className="h-7 w-7 text-primary" />
              </CardContent>
            </Card>
          )
        ))}
      </div>

      {(workspaces.error || workflows.error || tasks.error || runs.error) && (
        <ErrorState error={workspaces.error || workflows.error || tasks.error || runs.error} />
      )}

      <div className="grid gap-4 xl:grid-cols-[1.15fr_0.85fr]">
        <Card>
          <CardHeader>
            <CardTitle>最近运行</CardTitle>
            <GitBranch className="h-4 w-4 text-primary" />
          </CardHeader>
          <CardContent>
            {runs.data?.length ? (
              <Table>
                <thead>
                  <tr>
                    <Th>运行</Th>
                    <Th>任务</Th>
                    <Th>状态</Th>
                    <Th>开始时间</Th>
                  </tr>
                </thead>
                <tbody>
                  {runs.data.slice(0, 8).map((run) => (
                    <tr key={run.id}>
                      <Td><Link className="text-primary underline" to={`/runs/${run.id}?taskId=${run.taskId}`}>{run.id}</Link></Td>
                      <Td className="mono">{run.taskId}</Td>
                      <Td><Badge>{run.status}</Badge></Td>
                      <Td>{formatDate(run.startedAt)}</Td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            ) : (
              <EmptyState title="暂无运行记录" detail="任务创建、启动和交互处理仅在 CLI 中进行。" />
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>项目记忆概览</CardTitle>
            <Brain className="h-4 w-4 text-primary" />
          </CardHeader>
          <CardContent className="space-y-3">
            {!firstWorkspace ? (
              <EmptyState title="尚未注册工作区" detail="请先注册工作区，再查看项目记忆。" />
            ) : (
              <>
                <div className="text-sm text-muted-foreground">{firstWorkspace.name}</div>
                <div className="grid grid-cols-2 gap-2">
                  <div className="rounded-md border p-3">
                    <div className="text-xs uppercase text-muted-foreground">已批准记忆</div>
                    <div className="mt-2 text-2xl font-semibold">{memory.data?.length ?? 0}</div>
                  </div>
                  <div className="rounded-md border p-3">
                    <div className="text-xs uppercase text-muted-foreground">写入提案</div>
                    <div className="mt-2 text-2xl font-semibold">{proposals.data?.length ?? 0}</div>
                  </div>
                </div>
              </>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
