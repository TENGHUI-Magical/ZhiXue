<template>
  <div ref="rootEl" class="theme-seg" role="radiogroup" aria-label="外观主题">
    <button
      v-for="(option, index) in THEME_OPTIONS"
      :key="option.key"
      type="button"
      role="radio"
      class="seg-btn"
      :class="{ 'is-active': pref === option.key }"
      :aria-checked="pref === option.key"
      :aria-label="ariaLabel(option)"
      :title="ariaLabel(option)"
      :tabindex="pref === option.key ? 0 : -1"
      @click="select(option.key)"
      @keydown="onKeydown($event, index)"
    >
      <ThemeIcon class="seg-icon" :name="option.key" :size="16" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import ThemeIcon from "./ThemeIcon.vue";
import { initTheme, THEME_OPTIONS, useTheme, type ThemePref } from "../composables/useTheme";

// 原生分段控件而非原生下拉：三档用一次点击即可切换（下拉需两次），
// 且零新增 Element 依赖（体积棘轮 ceilings.json 不允许上调）
const { pref, systemDark, setPref } = useTheme();
const rootEl = ref<HTMLElement | null>(null);

const select = (key: ThemePref) => setPref(key);

/** 「跟随系统」需说清当前实际外观，否则用户不知道它现在是不是深色 */
const ariaLabel = (option: (typeof THEME_OPTIONS)[number]) => {
  if (option.key !== "system") return `${option.label}主题`;
  return `跟随系统，当前${systemDark.value ? "深色" : "浅色"}`;
};

/** roving tabindex：方向键在档位间移动，Home/End 到首尾（WAI-ARIA radiogroup 范式） */
const onKeydown = (event: KeyboardEvent, index: number) => {
  const last = THEME_OPTIONS.length - 1;
  let next = -1;
  if (event.key === "ArrowRight" || event.key === "ArrowDown") next = index === last ? 0 : index + 1;
  else if (event.key === "ArrowLeft" || event.key === "ArrowUp") next = index === 0 ? last : index - 1;
  else if (event.key === "Home") next = 0;
  else if (event.key === "End") next = last;
  if (next < 0) return;
  event.preventDefault();
  select(THEME_OPTIONS[next].key);
  const buttons = rootEl.value?.querySelectorAll<HTMLButtonElement>(".seg-btn");
  buttons?.[next]?.focus();
};

onMounted(initTheme);
</script>

<style scoped>
.theme-seg {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 3px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-full);
  background: var(--app-surface-muted);
}
.seg-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 26px;
  padding: 0;
  border: none;
  border-radius: var(--app-radius-full);
  background: transparent;
  color: var(--app-text-muted);
  cursor: pointer;
  transition: background-color var(--app-dur-fast) var(--app-ease-standard),
    color var(--app-dur-fast) var(--app-ease-standard);
}
.seg-btn:hover {
  color: var(--app-text);
}
.seg-btn:focus-visible {
  outline: 2px solid var(--app-primary);
  outline-offset: 2px;
}
/* 选中档为"浮起的小滑块"：分段控件的通行读法（iOS/macOS/GitHub 一致）。
   必须用 surface-raised 而非 surface：深色下 surface(#121a28) 比凹槽 muted(#192334) 更暗，
   会读成"凹陷"，与浅色下的"浮起"语义相反。 */
.seg-btn.is-active {
  background: var(--app-surface-raised);
  color: var(--app-primary);
  box-shadow: var(--app-shadow-xs), inset 0 1px 0 var(--app-card-hi);
}
.seg-icon {
  flex: none;
}
/* 触控目标：每档 ≥44px（e2e 有 width/height ≥44 断言）。
   条件含 (pointer: coarse)：平板横屏 1024 / 手机横屏 812·896 宽度均 >768，
   只判宽度会让这些真实触摸设备停留在桌面尺寸 26px。 */
@media (max-width: 768px), (pointer: coarse) {
  .seg-btn {
    width: 44px;
    height: 44px;
  }
  .theme-seg {
    padding: 2px;
  }
}
</style>
