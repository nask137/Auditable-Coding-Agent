import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Badge, Card, CardContent, CardHeader, CardTitle, Table, Td, Th } from "../components/ui";
import { api } from "../lib/api";
import type { CliSessionSummary } from "../types";

export function CliSessions() {
  const [sessions, setSessions] = useState<CliSessionSummary[]>([]);
  const [error, setError] = useState("");

  useEffect(() => {
    api.cliSessions().then(setSessions).catch((err) => setError(err.message));
  }, []);

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>CLI 会话</CardTitle>
          <Badge tone="success">后端 conversation</Badge>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          展示后端 conversation 记录。TUI 的 /resume 会恢复这里的会话，并在同一会话下继续创建多个任务。
        </CardContent>
      </Card>
      <Card>
        <CardContent>
          {error ? <div className="text-sm text-destructive">{error}</div> : (
            <Table>
              <thead>
                <tr>{["Conversation", "Latest status", "Workspace", "Tasks", "Latest task", "Updated"].map((heading) => <Th key={heading}>{heading}</Th>)}</tr>
              </thead>
              <tbody>
                {sessions.map((session) => (
                  <tr key={session.conversationId}>
                    <Td>
                      <div className="font-medium">{session.conversationTitle || "Conversation"}</div>
                      <div className="mt-1 text-xs text-muted-foreground">{session.conversationId}</div>
                    </Td>
                    <Td>{session.latestTaskStatus || "-"}</Td>
                    <Td>{session.workspaceId || "-"}</Td>
                    <Td>{session.taskCount}</Td>
                    <Td>{session.latestTaskId ? <Link className="text-primary underline" to={`/tasks/${session.latestTaskId}`}>{session.latestTaskId}</Link> : "-"}</Td>
                    <Td>{session.updatedAt || "-"}</Td>
                  </tr>
                ))}
              </tbody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
