import { NavLink, Outlet } from "react-router-dom";
import {
  Activity,
  Boxes,
  Brain,
  Database,
  GitBranch,
  Home,
  MessageSquare,
  Monitor,
  RotateCcw,
  Settings,
  ShieldCheck,
  Workflow
} from "lucide-react";
import { cn } from "../lib/utils";

const nav = [
  { to: "/", label: "仪表盘", icon: Home },
  { to: "/workspaces", label: "工作区", icon: Database },
  { to: "/tasks", label: "任务", icon: Activity },
  { to: "/workflow", label: "工作流审计", icon: Workflow },
  { to: "/replay", label: "回放", icon: RotateCcw },
  { to: "/approvals", label: "审批", icon: ShieldCheck },
  { to: "/inputs", label: "用户输入", icon: MessageSquare },
  { to: "/memory", label: "记忆", icon: Brain },
  { to: "/context", label: "上下文", icon: GitBranch },
  { to: "/cli-sessions", label: "CLI 会话", icon: Monitor },
  { to: "/settings", label: "设置", icon: Settings }
];

export function Layout() {
  return (
    <div className="min-h-screen">
      <aside className="fixed inset-y-0 left-0 z-20 hidden w-64 border-r bg-card/90 backdrop-blur lg:block">
        <div className="flex h-14 items-center gap-2 border-b px-4">
          <Boxes className="h-5 w-5 text-primary" />
          <div>
            <div className="text-sm font-semibold">Auditable Coding Agent</div>
          </div>
        </div>
        <nav className="space-y-1 p-3">
          {nav.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === "/"}
              className={({ isActive }) =>
                cn(
                  "flex items-center gap-3 rounded-md px-3 py-2 text-sm text-muted-foreground hover:bg-secondary hover:text-foreground",
                  isActive && "bg-secondary text-foreground"
                )
              }
            >
              <item.icon className="h-4 w-4" />
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <div className="lg:pl-64">
        <main className="mx-auto max-w-[1540px] p-4">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
