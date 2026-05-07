import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../lib/api";
import { Badge, Card, CardContent, CardHeader, CardTitle, ConfirmButton, EmptyState, ErrorState, JsonBlock, Table, Td, Th } from "../components/ui";
import { compactId, formatDate } from "../lib/utils";

export function Approvals() {
  const qc = useQueryClient();
  const approvals = useQuery({ queryKey: ["approvals", "PENDING"], queryFn: () => api.approvals("PENDING"), refetchInterval: 8000 });
  const approve = useMutation({ mutationFn: (id: string) => api.approve(id), onSuccess: () => qc.invalidateQueries({ queryKey: ["approvals"] }) });
  const deny = useMutation({ mutationFn: (id: string) => api.deny(id), onSuccess: () => qc.invalidateQueries({ queryKey: ["approvals"] }) });

  return (
    <Card>
      <CardHeader><CardTitle>待审批请求</CardTitle></CardHeader>
      <CardContent>
        {approvals.error && <ErrorState error={approvals.error} />}
        {approvals.data?.length ? (
          <Table>
            <thead><tr><Th>类型</Th><Th>风险</Th><Th>原因</Th><Th>ID</Th><Th>创建时间</Th><Th>操作</Th></tr></thead>
            <tbody>
              {approvals.data.map((item) => (
                <tr key={item.id}>
                  <Td>{item.approvalType}</Td>
                  <Td><Badge>{item.riskLevel}</Badge></Td>
                  <Td className="max-w-md">
                    <div>{item.reason}</div>
                    {item.preview ? <JsonBlock className="mt-2 max-h-40" value={item.preview} /> : null}
                  </Td>
                  <Td className="mono">{compactId(item.id)}</Td>
                  <Td>{formatDate(item.createdAt)}</Td>
                  <Td>
                    <div className="flex gap-2">
                      <ConfirmButton variant="default" message="批准此请求并恢复运行？" onConfirm={() => approve.mutate(item.id)}>批准</ConfirmButton>
                      <ConfirmButton message="拒绝此请求并使关联操作失败？" onConfirm={() => deny.mutate(item.id)}>拒绝</ConfirmButton>
                    </div>
                  </Td>
                </tr>
              ))}
            </tbody>
          </Table>
        ) : <EmptyState title="暂无待审批请求" />}
      </CardContent>
    </Card>
  );
}
