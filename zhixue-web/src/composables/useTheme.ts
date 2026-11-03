// 主题注册与持久化：只切换 html[data-theme] 与 dark 类，不动组件逻辑
// 主题收敛：仅保留 glass（浅）+ glass-dark（深）两套（裁决 V3.0 §3 D2）；
// 旧 academic/ink/paper/mono 已删除；历史 localStorage 值经 isThemeKey 校验失败后自动回退 glass。
export type ThemeKey = "glass" | "glass-dark";

export interface ThemeOption {
  key: ThemeKey;
  label: string;
  description: string;
  accent: string;
  surface: string;
}

export const THEMES: ThemeOption[] = [
  { key: "glass", label: "液态玻璃", description: "默认；冷雾蓝灰与发丝边", accent: "#2563eb", surface: "#d7e0ec" },
  { key: "glass-dark", label: "玻璃·夜", description: "石墨蓝黑，适合演示与夜间", accent: "#4d8dff", surface: "#080d17" }
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
  root.classList.toggle("dark", key === "glass-dark");
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
