export type DraftSettings = {
  model: {
    provider: "stub" | "http";
    baseUrl: string;
    model: string;
    maxTokens: number;
    temperature: number;
    thinkingEnabled: boolean;
    reasoningEffort: string;
  };
  mcp: Array<{ id: string; name: string; command: string; enabled: boolean }>;
  skills: Array<{ id: string; name: string; source: string; enabled: boolean }>;
  plugins: Array<{ id: string; name: string; category: string; enabled: boolean }>;
  ui: {
    autoRefreshSeconds: number;
    denseTables: boolean;
  };
};

const STORAGE_KEY = "auditable-agent.dashboard.settings";

export const defaultSettings: DraftSettings = {
  model: {
    provider: "stub",
    baseUrl: "https://api.deepseek.com",
    model: "deepseek-v4-pro",
    maxTokens: 4096,
    temperature: 0.1,
    thinkingEnabled: false,
    reasoningEffort: "high"
  },
  mcp: [
    { id: "filesystem", name: "文件系统工具", command: "等待后端 API 接入", enabled: true },
    { id: "github", name: "GitHub 集成", command: "等待后端 API 接入", enabled: false }
  ],
  skills: [
    { id: "frontend-design", name: "frontend-design", source: "本地技能", enabled: true },
    { id: "playwright", name: "playwright", source: "本地技能", enabled: true }
  ],
  plugins: [
    { id: "browser-use", name: "Browser Use", category: "测试", enabled: false },
    { id: "github", name: "GitHub", category: "协作", enabled: false }
  ],
  ui: {
    autoRefreshSeconds: 8,
    denseTables: true
  }
};

export function readSettings(): DraftSettings {
  const stored = localStorage.getItem(STORAGE_KEY);
  if (!stored) return defaultSettings;
  try {
    return { ...defaultSettings, ...JSON.parse(stored) } as DraftSettings;
  } catch {
    return defaultSettings;
  }
}

export function writeSettings(settings: DraftSettings) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
}
