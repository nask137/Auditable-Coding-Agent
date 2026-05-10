import { useEffect, useState } from "react";
import { Badge, Button, Card, CardContent, CardHeader, CardTitle, Input, Select } from "../components/ui";
import { api } from "../lib/api";
import type { CliRuntimeSettings } from "../types";

const defaults: CliRuntimeSettings = {
  baseUrl: "http://localhost:8080",
  workspaceId: "",
  workflow: "coding-agent",
  permissionPreset: "workspace-write",
  model: "",
  profile: "default"
};

export function Settings() {
  const [settings, setSettings] = useState<CliRuntimeSettings>(defaults);
  const [message, setMessage] = useState("");

  useEffect(() => {
    api.cliSettings().then(setSettings).catch((error) => setMessage(error.message));
  }, []);

  const save = async () => {
    setSettings(await api.saveCliSettings(settings));
    setMessage("已保存到后端 CLI 配置");
  };

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>CLI / Runtime 配置</CardTitle>
          <Badge tone="success">后端配置</Badge>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          配置会写入后端进程用户目录下的 .auditable-agent/config.toml，CLI TUI 和 dashboard 会读取同一份本机设置。
        </CardContent>
      </Card>

      <div className="grid gap-4 xl:grid-cols-2">
        <Card>
          <CardHeader><CardTitle>CLI 默认值</CardTitle></CardHeader>
          <CardContent className="space-y-3">
            <Input value={settings.baseUrl} onChange={(e) => setSettings({ ...settings, baseUrl: e.target.value })} />
            <Input value={settings.workspaceId} onChange={(e) => setSettings({ ...settings, workspaceId: e.target.value })} />
            <Select value={settings.workflow} onChange={(e) => setSettings({ ...settings, workflow: e.target.value })}>
              <option value="coding-agent">coding-agent</option>
              <option value="review-agent">review-agent</option>
              <option value="test-agent">test-agent</option>
            </Select>
            <Select value={settings.permissionPreset} onChange={(e) => setSettings({ ...settings, permissionPreset: e.target.value })}>
              <option value="read-only">read-only</option>
              <option value="workspace-write">workspace-write</option>
              <option value="full-auto">full-auto</option>
            </Select>
          </CardContent>
        </Card>
        <Card>
          <CardHeader><CardTitle>模型 / Profile</CardTitle></CardHeader>
          <CardContent className="space-y-3">
            <Input value={settings.model} onChange={(e) => setSettings({ ...settings, model: e.target.value })} />
            <Input value={settings.profile} onChange={(e) => setSettings({ ...settings, profile: e.target.value })} />
            <div className="flex gap-2">
              <Button onClick={save}>保存配置</Button>
              <Button variant="secondary" onClick={() => setSettings(defaults)}>重置表单</Button>
            </div>
            {message && <div className="text-sm text-muted-foreground">{message}</div>}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
