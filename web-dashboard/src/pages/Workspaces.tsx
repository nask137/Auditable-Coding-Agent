import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api } from "../lib/api";
import { asText, compactId, formatDate } from "../lib/utils";
import { Badge, Button, Card, CardContent, CardHeader, CardTitle, EmptyState, ErrorState, Input, JsonBlock, Select, Table, Td, Th } from "../components/ui";

export function Workspaces() {
  const qc = useQueryClient();
  const [selected, setSelected] = useState("");
  const [form, setForm] = useState({ name: "", rootPath: "", trusted: true });
  const workspaces = useQuery({ queryKey: ["workspaces"], queryFn: api.workspaces });
  const workspaceId = selected || workspaces.data?.[0]?.id || "";
  const profile = useQuery({ queryKey: ["profile", workspaceId], queryFn: () => api.profile(workspaceId), enabled: Boolean(workspaceId), retry: false });
  const scanExecutions = useQuery({ queryKey: ["scan-executions", workspaceId], queryFn: () => api.scanExecutions(workspaceId), enabled: Boolean(workspaceId) });
  const policies = useQuery({ queryKey: ["command-policies", workspaceId], queryFn: () => api.commandPolicies(workspaceId), enabled: Boolean(workspaceId) });

  const create = useMutation({
    mutationFn: () => api.createWorkspace(form),
    onSuccess: (workspace) => {
      setForm({ name: "", rootPath: "", trusted: true });
      setSelected(workspace.id);
      qc.invalidateQueries({ queryKey: ["workspaces"] });
    }
  });
  const scan = useMutation({
    mutationFn: () => api.scanWorkspace(workspaceId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["profile", workspaceId] });
      qc.invalidateQueries({ queryKey: ["scan-executions", workspaceId] });
    }
  });

  return (
    <div className="grid gap-4 xl:grid-cols-[360px_1fr]">
      <Card>
        <CardHeader><CardTitle>注册工作区</CardTitle></CardHeader>
        <CardContent className="space-y-3">
          <Input placeholder="名称" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          <Input placeholder="D:\workspace\project" value={form.rootPath} onChange={(e) => setForm({ ...form, rootPath: e.target.value })} />
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={form.trusted} onChange={(e) => setForm({ ...form, trusted: e.target.checked })} />
            受信任工作区
          </label>
          <Button className="w-full" disabled={!form.name || !form.rootPath || create.isPending} onClick={() => create.mutate()}>
            注册
          </Button>
          {create.error && <ErrorState error={create.error} />}
        </CardContent>
      </Card>

      <div className="space-y-4">
        <Card>
          <CardHeader>
            <CardTitle>工作区</CardTitle>
            <div className="flex gap-2">
              <Select value={workspaceId} onChange={(e) => setSelected(e.target.value)} className="w-80">
                {workspaces.data?.map((workspace) => (
                  <option key={workspace.id} value={workspace.id}>{workspace.name}</option>
                ))}
              </Select>
              <Button disabled={!workspaceId || scan.isPending} onClick={() => scan.mutate()}>扫描</Button>
            </div>
          </CardHeader>
          <CardContent>
            {workspaces.error && <ErrorState error={workspaces.error} />}
            {workspaces.data?.length ? (
              <Table>
                <thead><tr><Th>名称</Th><Th>根目录</Th><Th>信任状态</Th><Th>ID</Th></tr></thead>
                <tbody>
                  {workspaces.data.map((workspace) => (
                    <tr key={workspace.id}>
                      <Td>{workspace.name}</Td>
                      <Td className="mono max-w-xl truncate">{workspace.rootPath}</Td>
                      <Td><Badge>{workspace.trusted ? "受信任" : "未信任"}</Badge></Td>
                      <Td className="mono">{compactId(workspace.id)}</Td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            ) : <EmptyState title="暂无工作区" />}
          </CardContent>
        </Card>

        <div className="grid gap-4 xl:grid-cols-2">
          <Card>
            <CardHeader><CardTitle>项目画像</CardTitle></CardHeader>
            <CardContent>{profile.error ? <ErrorState error={profile.error} /> : <JsonBlock value={profile.data ?? "暂无画像，请运行扫描。"} />}</CardContent>
          </Card>
          <Card>
            <CardHeader><CardTitle>扫描历史</CardTitle></CardHeader>
            <CardContent>
              {scanExecutions.data?.length ? (
                <Table>
                  <thead><tr><Th>状态</Th><Th>文件数</Th><Th>完成时间</Th></tr></thead>
                  <tbody>
                    {scanExecutions.data.slice(0, 8).map((execution) => (
                      <tr key={execution.id}>
                        <Td><Badge>{asText(execution.status)}</Badge></Td>
                        <Td>{asText(execution.filesIndexed ?? execution.files_seen)}</Td>
                        <Td>{formatDate(execution.completedAt ?? execution.completed_at)}</Td>
                      </tr>
                    ))}
                  </tbody>
                </Table>
              ) : <EmptyState title="暂无扫描记录" />}
            </CardContent>
          </Card>
        </div>

        <Card>
          <CardHeader><CardTitle>命令策略</CardTitle></CardHeader>
          <CardContent><JsonBlock value={policies.data ?? []} /></CardContent>
        </Card>
      </div>
    </div>
  );
}
