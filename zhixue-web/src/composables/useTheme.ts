// 主题系统（V4.1）：三档偏好 = 浅色 / 深色 / 跟随系统
// 设计要点：
//  1. 分离「用户偏好」与「实际主题」：localStorage 只存偏好（system|light|dark），
//     html[data-theme] 只取解析后的实际值（glass|glass-dark），对全部 CSS 零影响。
//  2. 旧值兼容：V3.0 及以前存的是 glass / glass-dark，读取时映射到 light / dark，
//     否则老用户与 e2e 双主题回归会一起退化（e2e/verify.mjs 正是写入这两个值）。
//  3. 默认 system（对齐 UX-011「按系统偏好支持深色」，也是 GitHub/Linear 等大厂默认）。
//  4. 仅当偏好为 system 时才响应系统切换；用户手动锁定后系统变化不打扰。
import { computed, readonly, ref } from "vue";

/** 用户偏好（持久化） */
export type ThemePref = "system" | "light" | "dark";
/** 解析后的实际主题（写入 html[data-theme]） */
export type ResolvedTheme = "glass" | "glass-dark";

export interface ThemeOption {
  key: ThemePref;
  label: string;
  description: string;
}

/** 三档顺序：浅 → 深 → 跟随（与 macOS/Windows 外观设置一致） */
export const THEME_OPTIONS: ThemeOption[] = [
  { key: "light", label: "浅色", description: "冷雾蓝灰，白天与投影环境" },
  { key: "dark", label: "深色", description: "石墨蓝黑，夜间与演示" },
  { key: "system", label: "跟随系统", description: "随设备外观自动切换" },
];

const STORAGE_KEY = "zhixue-theme";
const MEDIA_QUERY = "(prefers-color-scheme: dark)";
const META_COLOR: Record<ResolvedTheme, string> = {
  glass: "#d7e0ec",
  "glass-dark": "#080d17",
};

/** 旧值 → 新偏好（兼容 V3.0 的 glass / glass-dark） */
const LEGACY: Record<string, ThemePref> = {
  glass: "light",
  "glass-dark": "dark",
  light: "light",
  dark: "dark",
  system: "system",
};

// ---- 响应式单例（全应用共享一份，避免多处 ref 状态漂移）----
const pref = ref<ThemePref>("system");
const systemDark = ref(false);
let media: MediaQueryList | null = null;
let started = false;

function readMedia(): boolean {
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") return false;
  try {
    return window.matchMedia(MEDIA_QUERY).matches;
  } catch {
    return false;
  }
}

function readStored(): ThemePref {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return "system";
    return LEGACY[raw] ?? "system";
  } catch {
    return "system";
  }
}

/** 偏好 → 实际主题 */
export function resolveTheme(value: ThemePref, dark = systemDark.value): ResolvedTheme {
  if (value === "light") return "glass";
  if (value === "dark") return "glass-dark";
  return dark ? "glass-dark" : "glass";
}

function syncDom(): ResolvedTheme {
  const resolved = resolveTheme(pref.value);
  const root = document.documentElement;
  root.dataset.theme = resolved;
  root.classList.toggle("dark", resolved === "glass-dark");
  // 移动端地址栏/状态栏跟随，避免深色下顶部仍是浅色条
  let meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]');
  if (!meta) {
    meta = document.createElement("meta");
    meta.name = "theme-color";
    document.head.appendChild(meta);
  }
  meta.content = META_COLOR[resolved];
  return resolved;
}

/**
 * 初始化：读取偏好 + 系统外观 + 订阅系统变化。
 * 幂等，可被 main.ts 与 ThemeSwitcher 同时调用。
 */
export function initTheme(): void {
  if (started) return;
  started = true;
  systemDark.value = readMedia();
  pref.value = readStored();
  syncDom();

  if (typeof window === "undefined" || typeof window.matchMedia !== "function") return;
  try {
    media = window.matchMedia(MEDIA_QUERY);
    const onChange = (event: MediaQueryListEvent) => {
      systemDark.value = event.matches;
      // 只有「跟随系统」时才重绘；手动锁定后系统变化不应打断用户选择
      if (pref.value === "system") syncDom();
    };
    // Safari < 14 只有 addListener
    if (typeof media.addEventListener === "function") media.addEventListener("change", onChange);
    else if (typeof media.addListener === "function") media.addListener(onChange);
  } catch {
    // 环境不支持 matchMedia 时保持当前主题，不影响功能
  }
}

/** 设置偏好并立即生效（写入 localStorage） */
export function setThemePref(value: ThemePref): void {
  pref.value = value;
  if (!started) {
    systemDark.value = readMedia();
    started = true;
  }
  syncDom();
  try {
    localStorage.setItem(STORAGE_KEY, value);
  } catch {
    // 隐私模式下不可写，主题仍对当前会话生效
  }
}

/** 供组件消费的响应式入口 */
export function useTheme() {
  return {
    pref: readonly(pref),
    resolved: computed(() => resolveTheme(pref.value)),
    isSystem: computed(() => pref.value === "system"),
    /** 跟随系统时，当前系统是否为深色（用于切换器提示） */
    systemDark: readonly(systemDark),
    setPref: setThemePref,
  };
}

/** 兼容旧调用：只读当前偏好 */
export function getStoredPref(): ThemePref {
  return readStored();
}
