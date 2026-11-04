<template>
  <div class="brand-empty" role="status">
    <BrandMark :size="56" tone="outline" class="be-mark" />
    <p class="be-desc">{{ description }}</p>
    <div v-if="$slots.default" class="be-action">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import BrandMark from "./BrandMark.vue";

withDefaults(
  defineProps<{
    description?: string;
  }>(),
  {
    description: "暂无数据"
  }
);
</script>

<style scoped>
.brand-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 28px 12px;
  text-align: center;
}
.be-mark {
  opacity: 0.9;
}
.be-desc {
  margin: 14px 0 0;
  max-width: 420px;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
  line-height: 1.65;
}
.be-action {
  margin-top: 14px;
}
</style>
