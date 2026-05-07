import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { api } from "../lib/api";
import { Badge, Button, Card, CardContent, CardHeader, CardTitle, EmptyState, ErrorState, Input, JsonBlock, Select, Table, Td, Th } from "../components/ui";

export function Context() {
  const workspaces = useQuery({ queryKey: ["workspaces"], queryFn: api.workspaces });
  const [workspaceId, setWorkspaceId] = useState("");
  const resolved = workspaceId || workspaces.data?.[0]?.id || "";
  const [query, setQuery] = useState("如何运行测试");
  const [symbolQuery, setSymbolQuery] = useState("");
  const [outlinePath, setOutlinePath] = useState("");
  const [submittedQuery, setSubmittedQuery] = useState("");
  const context = useQuery({ queryKey: ["context", resolved, submittedQuery], queryFn: () => api.context(resolved, submittedQuery), enabled: Boolean(resolved && submittedQuery), retry: false });
  const symbols = useQuery({ queryKey: ["symbols", resolved, symbolQuery], queryFn: () => api.symbols(resolved, symbolQuery), enabled: Boolean(resolved), retry: false });
  const outline = useQuery({ queryKey: ["outline", resolved, outlinePath], queryFn: () => api.outline(resolved, outlinePath), enabled: Boolean(resolved && outlinePath), retry: false });
  const retrievals = useQuery({ queryKey: ["retrievals", resolved], queryFn: () => api.retrievals(resolved), enabled: Boolean(resolved), retry: false });

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>上下文工作区</CardTitle>
          <Select className="w-80" value={resolved} onChange={(e) => setWorkspaceId(e.target.value)}>
            {workspaces.data?.map((workspace) => <option key={workspace.id} value={workspace.id}>{workspace.name}</option>)}
          </Select>
        </CardHeader>
      </Card>

      <div className="grid gap-4 xl:grid-cols-[0.9fr_1.1fr]">
        <Card>
          <CardHeader><CardTitle>搜索上下文</CardTitle></CardHeader>
          <CardContent className="space-y-3">
            <div className="flex gap-2">
              <Input value={query} onChange={(e) => setQuery(e.target.value)} />
              <Button onClick={() => setSubmittedQuery(query)}>搜索</Button>
            </div>
            {context.error ? <ErrorState error={context.error} /> : <JsonBlock value={context.data ?? "运行一次上下文搜索"} />}
          </CardContent>
        </Card>
        <Card>
          <CardHeader><CardTitle>检索记录</CardTitle></CardHeader>
          <CardContent><JsonBlock value={retrievals.data ?? []} /></CardContent>
        </Card>
      </div>

      <div className="grid gap-4 xl:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>符号</CardTitle>
            <Input className="w-72" placeholder="符号查询" value={symbolQuery} onChange={(e) => setSymbolQuery(e.target.value)} />
          </CardHeader>
          <CardContent>
            {symbols.data?.length ? (
              <Table>
                <thead><tr><Th>类型</Th><Th>名称</Th><Th>路径</Th><Th>行号</Th></tr></thead>
                <tbody>
                  {symbols.data.slice(0, 40).map((symbol) => (
                    <tr key={symbol.id}>
                      <Td><Badge>{symbol.symbolType}</Badge></Td>
                      <Td>{symbol.symbolName}</Td>
                      <Td className="mono max-w-md truncate">{symbol.path}</Td>
                      <Td>{symbol.lineStart}</Td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            ) : <EmptyState title="暂无符号" />}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>文件大纲</CardTitle>
            <Input className="w-96" placeholder="src/main/java/..." value={outlinePath} onChange={(e) => setOutlinePath(e.target.value)} />
          </CardHeader>
          <CardContent><JsonBlock value={outline.data ?? "输入相对工作区的路径"} /></CardContent>
        </Card>
      </div>
    </div>
  );
}
