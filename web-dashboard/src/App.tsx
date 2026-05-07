import { createBrowserRouter, RouterProvider } from "react-router-dom";
import { Layout } from "./components/Layout";
import { Approvals } from "./pages/Approvals";
import { Context } from "./pages/Context";
import { Dashboard } from "./pages/Dashboard";
import { Memory } from "./pages/Memory";
import { Replay } from "./pages/Replay";
import { RunDetail } from "./pages/RunDetail";
import { Settings } from "./pages/Settings";
import { Tasks } from "./pages/Tasks";
import { UserInputs } from "./pages/UserInputs";
import { WorkflowAudit } from "./pages/WorkflowAudit";
import { Workspaces } from "./pages/Workspaces";

const router = createBrowserRouter([
  {
    path: "/",
    element: <Layout />,
    children: [
      { index: true, element: <Dashboard /> },
      { path: "workspaces", element: <Workspaces /> },
      { path: "tasks", element: <Tasks /> },
      { path: "runs/:runId", element: <RunDetail /> },
      { path: "workflow", element: <WorkflowAudit /> },
      { path: "replay", element: <Replay /> },
      { path: "approvals", element: <Approvals /> },
      { path: "inputs", element: <UserInputs /> },
      { path: "memory", element: <Memory /> },
      { path: "context", element: <Context /> },
      { path: "settings", element: <Settings /> }
    ]
  }
]);

export function App() {
  return <RouterProvider router={router} />;
}
