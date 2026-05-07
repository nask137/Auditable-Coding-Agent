import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api } from "../lib/api";
import { Badge, Button, Card, CardContent, CardHeader, CardTitle, ConfirmButton, EmptyState, ErrorState, Input, JsonBlock, Select, Table, Td, Textarea, Th } from "../components/ui";
import { formatDate } from "../lib/utils";

export function Memory() {
  const qc = useQueryClient();
  const workspaces = useQuery({ queryKey: ["workspaces"], queryFn: api.workspaces });
  const [workspaceId, setWorkspaceId] = useState("");
  const resolved = workspaceId || workspaces.data?.[0]?.id || "";
  const [form, setForm] = useState({ memoryType: "PROJECT_RULE", title: "", content: "", sourcePath: "" });
  const memory = useQuery({ queryKey: ["memory", resolved], queryFn: () => api.memory(resolved), enabled: Boolean(resolved) });
  const proposals = useQuery({ queryKey: ["memory-proposals", resolved], queryFn: () => api.memoryProposals(resolved), enabled: Boolean(resolved) });
  const create = useMutation({
    mutationFn: () => api.createMemory(resolved, form),
    onSuccess: () => {
      setForm({ memoryType: "PROJECT_RULE", title: "", content: "", sourcePath: "" });
      qc.invalidateQueries({ queryKey: ["memory", resolved] });
    }
  });
  const approve = useMutation({ mutationFn: api.approveMemoryProposal, onSuccess: () => qc.invalidateQueries({ queryKey: ["memory-proposals", resolved] }) });
  const reject = useMutation({ mutationFn: (id: string) => api.rejectMemoryProposal(id), onSuccess: () => qc.invalidateQueries({ queryKey: ["memory-proposals", resolved] }) });

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>记忆工作区</CardTitle>
          <Select className="w-80" value={resolved} onChange={(e) => setWorkspaceId(e.target.value)}>
            {workspaces.data?.map((workspace) => <option key={workspace.id} value={workspace.id}>{workspace.name}</option>)}
          </Select>
        </CardHeader>
      </Card>

      <div className="grid gap-4 xl:grid-cols-[380px_1fr]">
        <Card>
          <CardHeader><CardTitle>添加已批准记忆</CardTitle></CardHeader>
          <CardContent className="space-y-3">
            <Select value={form.memoryType} onChange={(e) => setForm({ ...form, memoryType: e.target.value })}>
              {["PROJECT_RULE", "COMMON_COMMAND", "TEST_STRATEGY", "MODULE_SUMMARY", "TASK_LESSON", "DO_NOT_TOUCH"].map((type) => <option key={type}>{type}</option>)}
            </Select>
            <Input placeholder="标题" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
            <Textarea placeholder="内容" value={form.content} onChange={(e) => setForm({ ...form, content: e.target.value })} />
            <Input placeholder="来源路径" value={form.sourcePath} onChange={(e) => setForm({ ...form, sourcePath: e.target.value })} />
            <Button disabled={!resolved || !form.title || !form.content} onClick={() => create.mutate()}>保存记忆</Button>
            {create.error && <ErrorState error={create.error} />}
          </CardContent>
        </Card>

        <Card>
          <CardHeader><CardTitle>已批准记忆项</CardTitle></CardHeader>
          <CardContent>
            {memory.data?.length ? (
              <Table>
                <thead><tr><Th>类型</Th><Th>标题</Th><Th>状态</Th><Th>创建时间</Th></tr></thead>
                <tbody>
                  {memory.data.map((item) => (
                    <tr key={item.id}>
                      <Td>{item.memoryType}</Td>
                      <Td><div>{item.title}</div><div className="mt-1 text-xs text-muted-foreground">{item.content}</div></Td>
                      <Td><Badge>{item.status}</Badge></Td>
                      <Td>{formatDate(item.createdAt)}</Td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            ) : <EmptyState title="暂无记忆项" />}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader><CardTitle>记忆写入提案</CardTitle></CardHeader>
        <CardContent className="space-y-3">
          {proposals.data?.length ? proposals.data.map((proposal) => (
            <div key={proposal.id} className="rounded-md border p-3">
              <div className="flex flex-wrap items-center gap-2">
                <Badge>{proposal.status}</Badge>
                <span className="text-sm font-medium">{proposal.title}</span>
                <span className="text-xs text-muted-foreground">{formatDate(proposal.createdAt)}</span>
              </div>
              <JsonBlock className="mt-3" value={proposal} />
              <div className="mt-3 flex gap-2">
                <ConfirmButton variant="default" message="批准此记忆提案？" onConfirm={() => approve.mutate(proposal.id)}>批准</ConfirmButton>
                <ConfirmButton message="拒绝此记忆提案？" onConfirm={() => reject.mutate(proposal.id)}>拒绝</ConfirmButton>
              </div>
            </div>
          )) : <EmptyState title="暂无记忆提案" />}
        </CardContent>
      </Card>
    </div>
  );
}
