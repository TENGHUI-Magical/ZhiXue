<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <template v-if="name === 'light'">
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M6.3 17.7l-1.4 1.4M19.1 4.9l-1.4 1.4" />
    </template>
    <path v-else-if="name === 'dark'" d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z" />
    <template v-else>
      <rect x="2" y="3" width="20" height="14" rx="2" />
      <path d="M8 21h8M12 17v4" />
    </template>
  </svg>
</template>

<script setup lang="ts">
// 三档主题图标单一定义：顶部切换器与个人中心"外观"共用，避免两处各画一套 SVG
withDefaults(
  defineProps<{
    name: "light" | "dark" | "system";
    size?: number;
    strokeWidth?: number;
  }>(),
  { size: 16, strokeWidth: 1.8 }
);
</script>
