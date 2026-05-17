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
          <Badge tone="success">本机 transcript</Badge>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          展示后端进程用户目录下 .auditable-agent/sessions 的最近 CLI TUI 会话摘要。
        </CardContent>
      </Card>
      <Card>
        <CardContent>
          {error ? <div className="text-sm text-destructive">{error}</div> : (
            <Table>
              <thead>
                <tr>{["Session", "Status", "Workspace", "Task", "Updated"].map((heading) => <Th key={heading}>{heading}</Th>)}</tr>
              </thead>
              <tbody>
                {sessions.map((session) => (
                  <tr key={session.sessionId}>
                    <Td>{session.sessionId}</Td>
                    <Td>{session.status || "-"}</Td>
                    <Td>{session.workspaceId || "-"}</Td>
                    <Td>{session.taskId ? <Link className="text-primary underline" to={`/tasks/${session.taskId}`}>{session.taskId}</Link> : "-"}</Td>
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
