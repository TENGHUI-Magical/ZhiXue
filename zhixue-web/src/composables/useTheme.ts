// 主题注册与持久化：只切换 html[data-theme] 与 dark 类，不动组件逻辑
export type ThemeKey = "glass" | "glass-dark" | "academic" | "ink" | "paper" | "mono";

export interface ThemeOption {
  key: ThemeKey;
  label: string;
  description: string;
  accent: string;
  surface: string;
}

export const THEMES: ThemeOption[] = [
  { key: "glass", label: "液态玻璃", description: "默认；冷雾蓝灰与发丝边", accent: "#2563eb", surface: "#d7e0ec" },
  { key: "glass-dark", label: "玻璃·夜", description: "石墨蓝黑，适合演示与夜间", accent: "#4d8dff", surface: "#080d17" },
  { key: "academic", label: "学术蓝", description: "答辩与教师端经典", accent: "#3b5bdb", surface: "#f4f6fb" },
  { key: "ink", label: "墨夜", description: "旧版暗色", accent: "#38bdf8", surface: "#0b1220" },
  { key: "paper", label: "护眼米", description: "暖纸底色，适合阅读投屏", accent: "#2f7d5d", surface: "#f4f1e8" },
  { key: "mono", label: "极简黑白", description: "高对比，适合打印材料", accent: "#111111", surface: "#ffffff" }
];

const STORAGE_KEY = "zhixue-theme";

export function isThemeKey(value: string | null): value is ThemeKey {
  return value !== null && THEMES.some((theme) => theme.key === value);
}

export function getStoredTheme(): ThemeKey {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    return isThemeKey(saved) ? saved : "glass";
  } catch {
    return "glass";
  }
}

export function applyTheme(key: ThemeKey): void {
  const root = document.documentElement;
  root.dataset.theme = key;
  root.classList.toggle("dark", key === "ink" || key === "glass-dark");
  const theme = THEMES.find((item) => item.key === key) ?? THEMES[0];
  let meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]');
  if (!meta) {
    meta = document.createElement("meta");
    meta.name = "theme-color";
    document.head.appendChild(meta);
  }
  meta.content = theme.surface;
  try {
    localStorage.setItem(STORAGE_KEY, key);
  } catch {
    // 隐私模式下 localStorage 不可写，主题仍对当前会话生效
  }
}
