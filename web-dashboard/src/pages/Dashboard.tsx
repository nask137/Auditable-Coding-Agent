import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { AlertTriangle, Brain, Database, MessageSquare, ShieldCheck, Workflow } from "lucide-react";
import { api } from "../lib/api";
import { compactId, formatDate } from "../lib/utils";
import { Badge, Card, CardContent, CardHeader, CardTitle, EmptyState, ErrorState, Table, Td, Th } from "../components/ui";

export function Dashboard() {
  const workspaces = useQuery({ queryKey: ["workspaces"], queryFn: api.workspaces });
  const workflows = useQuery({ queryKey: ["workflows"], queryFn: api.workflows });
  const approvals = useQuery({ queryKey: ["approvals", "PENDING"], queryFn: () => api.approvals("PENDING") });
  const inputs = useQuery({ queryKey: ["inputs", "PENDING"], queryFn: () => api.userInputs("PENDING") });

  const cards = [
    { label: "工作区", value: workspaces.data?.length ?? 0, icon: Database, to: "/workspaces" },
    { label: "工作流", value: workflows.data?.length ?? 0, icon: Workflow, to: "/workflow" },
    { label: "待审批", value: approvals.data?.length ?? 0, icon: ShieldCheck, to: "/approvals" },
    { label: "用户输入", value: inputs.data?.length ?? 0, icon: MessageSquare, to: "/inputs" }
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
        ))}
      </div>

      {(workspaces.error || workflows.error || approvals.error || inputs.error) && (
        <ErrorState error={workspaces.error || workflows.error || approvals.error || inputs.error} />
      )}

      <div className="grid gap-4 xl:grid-cols-[1.15fr_0.85fr]">
        <Card>
          <CardHeader>
            <CardTitle>运行时待处理队列</CardTitle>
            <AlertTriangle className="h-4 w-4 text-amber-300" />
          </CardHeader>
          <CardContent>
            {approvals.data?.length || inputs.data?.length ? (
              <Table>
                <thead>
                  <tr>
                    <Th>类型</Th>
                    <Th>ID</Th>
                    <Th>状态</Th>
                    <Th>创建时间</Th>
                  </tr>
                </thead>
                <tbody>
                  {approvals.data?.slice(0, 5).map((item) => (
                    <tr key={item.id}>
                      <Td>审批</Td>
                      <Td className="mono">{compactId(item.id)}</Td>
                      <Td><Badge>{item.status}</Badge></Td>
                      <Td>{formatDate(item.createdAt)}</Td>
                    </tr>
                  ))}
                  {inputs.data?.slice(0, 5).map((item) => (
                    <tr key={item.id}>
                      <Td>用户输入</Td>
                      <Td className="mono">{compactId(item.id)}</Td>
                      <Td><Badge>{item.status}</Badge></Td>
                      <Td>{formatDate(item.createdAt)}</Td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            ) : (
              <EmptyState title="暂无待处理运行时干预" detail="审批和用户输入请求均已清空。" />
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
