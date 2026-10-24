<template>
  <el-dropdown trigger="click" @command="select">
    <button type="button" class="theme-trigger" :aria-label="`切换主题，当前 ${current.label}`">
      <span class="swatch" aria-hidden="true" :style="{ background: current.accent }" />
      <span class="label">{{ current.label }}</span>
      <span class="caret" aria-hidden="true">▾</span>
    </button>
    <template #dropdown>
      <el-dropdown-menu class="theme-menu">
        <el-dropdown-item v-for="theme in THEMES" :key="theme.key" :command="theme.key">
          <span class="swatch" aria-hidden="true" :style="{ background: theme.accent }" />
          <span class="menu-label">
            <strong>{{ theme.label }}</strong>
            <small>{{ theme.description }}</small>
          </span>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { applyTheme, getStoredTheme, THEMES, type ThemeKey } from "../composables/useTheme";

const active = ref<ThemeKey>(getStoredTheme());
const current = computed(() => THEMES.find((theme) => theme.key === active.value) ?? THEMES[0]);

const select = (key: ThemeKey) => {
  active.value = key;
  applyTheme(key);
};
</script>

<style scoped>
.theme-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 12px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  background: var(--app-surface);
  color: var(--app-text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.16s ease, color 0.16s ease, background-color 0.2s ease;
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
  font-size: 10px;
  opacity: 0.7;
}
.theme-menu :deep(.el-dropdown-menu__item) {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
}
.menu-label {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}
.menu-label small {
  color: var(--app-text-muted);
  font-size: 11px;
}
</style>
