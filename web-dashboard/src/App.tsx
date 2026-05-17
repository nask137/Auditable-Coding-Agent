import { createBrowserRouter, RouterProvider } from "react-router-dom";
import { Layout } from "./components/Layout";
import { Context } from "./pages/Context";
import { Dashboard } from "./pages/Dashboard";
import { Memory } from "./pages/Memory";
import { Replay } from "./pages/Replay";
import { TaskDetail } from "./pages/TaskDetail";
import { Settings } from "./pages/Settings";
import { CliSessions } from "./pages/CliSessions";
import { WorkflowAudit } from "./pages/WorkflowAudit";
import { Workspaces } from "./pages/Workspaces";

const router = createBrowserRouter([
  {
    path: "/",
    element: <Layout />,
    children: [
      { index: true, element: <Dashboard /> },
      { path: "workspaces", element: <Workspaces /> },
      { path: "tasks/:taskId", element: <TaskDetail /> },
      { path: "workflow", element: <WorkflowAudit /> },
      { path: "replay", element: <Replay /> },
      { path: "memory", element: <Memory /> },
      { path: "context", element: <Context /> },
      { path: "cli-sessions", element: <CliSessions /> },
      { path: "settings", element: <Settings /> }
    ]
  }
]);

export function App() {
  return <RouterProvider router={router} />;
}
