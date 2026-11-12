// 断点响应式：手机 <768、平板 768–1023、桌面 ≥1024（与 glass.css / e2e 的 390 断言对齐）
// 只读 matchMedia，不引入 resize 轮询；SSR/旧浏览器下返回安全默认值。
import { onBeforeUnmount, readonly, ref, type Ref } from "vue";

const MOBILE_QUERY = "(max-width: 767px)";
const TABLET_QUERY = "(min-width: 768px) and (max-width: 1023px)";

function createMatcher(query: string): Readonly<Ref<boolean>> {
  const matches = ref(false);
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
    return readonly(matches);
  }
  let media: MediaQueryList | null = null;
  try {
    media = window.matchMedia(query);
    matches.value = media.matches;
  } catch {
    return readonly(matches);
  }
  const onChange = (event: MediaQueryListEvent) => {
    matches.value = event.matches;
  };
  if (typeof media.addEventListener === "function") media.addEventListener("change", onChange);
  else if (typeof media.addListener === "function") media.addListener(onChange);
  onBeforeUnmount(() => {
    if (!media) return;
    if (typeof media.removeEventListener === "function") media.removeEventListener("change", onChange);
    else if (typeof media.removeListener === "function") media.removeListener(onChange);
  });
  return readonly(matches);
}

/** 手机断点（<768）：底部 TabBar、底部 Sheet、单列内容 */
export function useIsMobile() {
  return createMatcher(MOBILE_QUERY);
}

/** 平板断点（768–1023）：72px 图标侧栏强制收起 */
export function useIsTablet() {
  return createMatcher(TABLET_QUERY);
}
