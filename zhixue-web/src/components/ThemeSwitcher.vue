<template>
  <div ref="rootEl" class="theme-switch">
    <button
      type="button"
      class="theme-trigger"
      :aria-expanded="open"
      :aria-label="`切换主题，当前 ${current.label}`"
      @click="open = !open"
    >
      <span class="swatch" aria-hidden="true" :style="{ background: current.accent }" />
      <span class="label">{{ current.label }}</span>
      <svg class="caret" viewBox="0 0 24 24" width="14" height="14" aria-hidden="true">
        <path d="m6 9 6 6 6-6" fill="none" stroke="currentColor" stroke-width="2"
              stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </button>
    <div v-if="open" class="theme-menu" role="listbox" aria-label="选择主题">
      <button
        v-for="theme in THEMES"
        :key="theme.key"
        type="button"
        role="option"
        class="theme-option"
        :aria-selected="theme.key === active"
        @click="select(theme.key)"
      >
        <span class="swatch" aria-hidden="true" :style="{ background: theme.accent }" />
        <span class="menu-label">
          <strong>{{ theme.label }}</strong>
          <small>{{ theme.description }}</small>
        </span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { applyTheme, getStoredTheme, THEMES, type ThemeKey } from "../composables/useTheme";

// 原生下拉而非 el-dropdown：体积棘轮（perf/bench/ceilings.json）不允许上调，
// 头部组件必须零新增 Element 依赖
const active = ref<ThemeKey>(getStoredTheme());
const open = ref(false);
const rootEl = ref<HTMLElement | null>(null);
const current = computed(() => THEMES.find((theme) => theme.key === active.value) ?? THEMES[0]);

const select = (key: ThemeKey) => {
  active.value = key;
  applyTheme(key);
  open.value = false;
};

const onDocClick = (event: MouseEvent) => {
  if (open.value && rootEl.value && !rootEl.value.contains(event.target as Node)) {
    open.value = false;
  }
};

const onKeydown = (event: KeyboardEvent) => {
  if (event.key === "Escape" && open.value) {
    open.value = false;
  }
};

onMounted(() => {
  document.addEventListener("click", onDocClick);
  document.addEventListener("keydown", onKeydown);
});

onBeforeUnmount(() => {
  document.removeEventListener("click", onDocClick);
  document.removeEventListener("keydown", onKeydown);
});
</script>

<style scoped>
.theme-switch {
  position: relative;
}
.theme-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 12px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-full);
  background: var(--app-surface);
  color: var(--app-text-secondary);
  font-size: var(--app-fs-sm);
  cursor: pointer;
}
.theme-trigger:hover {
  border-color: var(--app-primary);
  color: var(--app-text);
}
.theme-trigger:focus-visible {
  outline: 2px solid var(--app-primary);
  outline-offset: 2px;
}
.swatch {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex: none;
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--app-border) 70%, transparent);
}
.caret {
  flex: none;
  opacity: 0.7;
}
.theme-menu {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 30;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 210px;
  padding: 6px;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  box-shadow: var(--app-shadow-lg);
}
.theme-option {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border: none;
  border-radius: var(--app-radius-sm);
  background: transparent;
  color: var(--app-text);
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.theme-option:hover {
  background: var(--app-surface-muted);
}
.theme-option:focus-visible {
  outline: 2px solid var(--app-primary);
  outline-offset: -2px;
}
.menu-label {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}
.menu-label strong {
  font-weight: 600;
}
.menu-label small {
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
@media (max-width: 768px) {
  .theme-trigger .label,
  .theme-trigger .caret {
    display: none;
  }
  .theme-trigger {
    width: 44px;
    height: 44px;
    padding: 0;
    justify-content: center;
  }
}
</style>
