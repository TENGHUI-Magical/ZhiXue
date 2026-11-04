<template>
  <svg
    class="brand-mark"
    :width="size"
    :height="size"
    viewBox="0 0 32 32"
    :role="label ? 'img' : undefined"
    :aria-label="label || undefined"
    :aria-hidden="label ? undefined : 'true'"
    focusable="false"
  >
    <template v-if="tone === 'solid'">
      <rect width="32" height="32" rx="8" fill="var(--app-primary-strong)" />
      <rect
        x="0.5"
        y="0.5"
        width="31"
        height="31"
        rx="7.5"
        fill="none"
        stroke="rgba(255, 255, 255, 0.22)"
      />
    </template>
    <rect
      v-else-if="tone === 'outline'"
      x="0.5"
      y="0.5"
      width="31"
      height="31"
      rx="7.5"
      fill="var(--app-surface-muted)"
      stroke="var(--app-hairline)"
    />
    <g :fill="markColor" :stroke="markColor">
      <circle cx="16" cy="6.8" r="1.5" stroke="none" />
      <path
        d="M9 11.6 L16 7 L23 11.6"
        fill="none"
        stroke-width="2.4"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
      <rect x="8.5" y="17.4" width="6" height="2.6" rx="1.3" stroke="none" />
      <rect x="8.5" y="21.8" width="10" height="2.6" rx="1.3" stroke="none" />
      <rect x="8.5" y="26.2" width="14" height="2.6" rx="1.3" stroke="none" />
    </g>
  </svg>
</template>

<script setup lang="ts">
import { computed } from "vue";

type Tone = "solid" | "outline" | "mono";

const props = withDefaults(
  defineProps<{
    size?: number;
    tone?: Tone;
    label?: string;
  }>(),
  {
    size: 32,
    tone: "solid",
    label: ""
  }
);

/**
 * 几何品牌母题「航标」：顶点 + 上行箭头 + 三级递增横条。
 * 语义：方向（领航）× 结构化知识（章→节→点）× 成长（递增）。
 * 纯 SVG，随主题令牌变色；装饰场景默认 aria-hidden。
 */
const markColor = computed(() => {
  if (props.tone === "solid") return "#ffffff";
  if (props.tone === "outline") return "var(--app-primary)";
  return "currentColor";
});
</script>

<style scoped>
.brand-mark {
  display: block;
  flex: none;
}
</style>
