import { useState } from "react";
import { Badge, Button, Card, CardContent, CardHeader, CardTitle, Input, JsonBlock, Select } from "../components/ui";
import { defaultSettings, readSettings, writeSettings, type DraftSettings } from "../lib/settings";

export function Settings() {
  const [settings, setSettings] = useState<DraftSettings>(() => readSettings());
  const save = () => writeSettings(settings);
  const reset = () => {
    setSettings(defaultSettings);
    writeSettings(defaultSettings);
  };

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>配置草稿</CardTitle>
          <Badge tone="warning">前端草稿 / 后端 API 待接入</Badge>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          这些设置用于记录模型、MCP、技能和插件的配置界面草稿。在后端配置 API 接入前，内容会暂存于 localStorage。
        </CardContent>
      </Card>

      <div className="grid gap-4 xl:grid-cols-2">
        <Card>
          <CardHeader><CardTitle>模型</CardTitle></CardHeader>
          <CardContent className="space-y-3">
            <Select value={settings.model.provider} onChange={(e) => setSettings({ ...settings, model: { ...settings.model, provider: e.target.value as "stub" | "http" } })}>
              <option value="stub">stub</option>
              <option value="http">http</option>
            </Select>
            <Input value={settings.model.baseUrl} onChange={(e) => setSettings({ ...settings, model: { ...settings.model, baseUrl: e.target.value } })} />
            <Input value={settings.model.model} onChange={(e) => setSettings({ ...settings, model: { ...settings.model, model: e.target.value } })} />
            <Input type="number" value={settings.model.maxTokens} onChange={(e) => setSettings({ ...settings, model: { ...settings.model, maxTokens: Number(e.target.value) } })} />
            <Input type="number" step="0.1" value={settings.model.temperature} onChange={(e) => setSettings({ ...settings, model: { ...settings.model, temperature: Number(e.target.value) } })} />
          </CardContent>
        </Card>
        <Card>
          <CardHeader><CardTitle>界面偏好</CardTitle></CardHeader>
          <CardContent className="space-y-3">
            <Input type="number" value={settings.ui.autoRefreshSeconds} onChange={(e) => setSettings({ ...settings, ui: { ...settings.ui, autoRefreshSeconds: Number(e.target.value) } })} />
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={settings.ui.denseTables} onChange={(e) => setSettings({ ...settings, ui: { ...settings.ui, denseTables: e.target.checked } })} />
              紧凑表格
            </label>
            <div className="flex gap-2">
              <Button onClick={save}>保存草稿</Button>
              <Button variant="secondary" onClick={reset}>重置</Button>
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-4 xl:grid-cols-3">
        <Card><CardHeader><CardTitle>MCP</CardTitle></CardHeader><CardContent><JsonBlock value={settings.mcp} /></CardContent></Card>
        <Card><CardHeader><CardTitle>技能</CardTitle></CardHeader><CardContent><JsonBlock value={settings.skills} /></CardContent></Card>
        <Card><CardHeader><CardTitle>插件</CardTitle></CardHeader><CardContent><JsonBlock value={settings.plugins} /></CardContent></Card>
      </div>
    </div>
  );
}
