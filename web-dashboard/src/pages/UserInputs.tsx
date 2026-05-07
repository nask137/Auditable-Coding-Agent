import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api } from "../lib/api";
import { Badge, Button, Card, CardContent, CardHeader, CardTitle, ConfirmButton, EmptyState, ErrorState, JsonBlock, Textarea } from "../components/ui";
import { compactId, formatDate } from "../lib/utils";

export function UserInputs() {
  const qc = useQueryClient();
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const inputs = useQuery({ queryKey: ["inputs", "PENDING"], queryFn: () => api.userInputs("PENDING"), refetchInterval: 8000 });
  const answer = useMutation({
    mutationFn: ({ id, text }: { id: string; text: string }) => api.answerUserInput(id, text),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["inputs"] })
  });
  const cancel = useMutation({ mutationFn: api.cancelUserInput, onSuccess: () => qc.invalidateQueries({ queryKey: ["inputs"] }) });

  return (
    <Card>
      <CardHeader><CardTitle>用户干预请求</CardTitle></CardHeader>
      <CardContent className="space-y-3">
        {inputs.error && <ErrorState error={inputs.error} />}
        {inputs.data?.length ? inputs.data.map((item) => (
          <div key={item.id} className="rounded-md border p-3">
            <div className="flex flex-wrap items-center gap-2">
              <Badge>{item.status}</Badge>
              <span className="mono text-sm">{compactId(item.id)}</span>
              <span className="text-xs text-muted-foreground">{formatDate(item.createdAt)}</span>
            </div>
            <JsonBlock className="mt-3" value={item} />
            <Textarea
              className="mt-3"
              placeholder="为暂停的运行填写答复"
              value={answers[item.id] ?? ""}
              onChange={(e) => setAnswers({ ...answers, [item.id]: e.target.value })}
            />
            <div className="mt-3 flex gap-2">
              <Button size="sm" disabled={!answers[item.id]} onClick={() => answer.mutate({ id: item.id, text: answers[item.id] })}>提交答复</Button>
              <ConfirmButton message="取消此请求并使关联运行失败？" onConfirm={() => cancel.mutate(item.id)}>取消</ConfirmButton>
            </div>
          </div>
        )) : <EmptyState title="暂无待处理用户输入请求" />}
      </CardContent>
    </Card>
  );
}
