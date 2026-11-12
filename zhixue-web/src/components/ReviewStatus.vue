<template>
  <div
    v-if="result === 'PASS' || result === 'RETURN'"
    class="review-status"
    :class="result === 'RETURN' ? 'is-return' : 'is-pass'"
    :data-review-result="result"
  >
    <template v-if="result === 'PASS'">老师已复核这条讲解<template v-if="comment">：{{ comment }}</template></template>
    <template v-else>需要补充：{{ comment }}</template>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  result?: "PASS" | "RETURN" | null;
  comment?: string | null;
}>();
</script>

<style scoped>
/* 只做视觉：状态徽标（左侧圆点 + 同色描边），文案/类名/结构保持不变。
   不铺同色浅底，避免 12–13px 文字在白底淡色块上掉到 4.5:1 以下。 */
.review-status {
  display: inline-flex;
  align-items: flex-start;
  gap: 6px;
  max-width: 100%;
  margin-top: 8px;
  padding: 3px 10px 3px 8px;
  border: 1px solid transparent;
  border-radius: var(--app-radius-md);
  font-size: var(--app-fs-sm);
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.review-status::before {
  content: "";
  flex: none;
  width: 6px;
  height: 6px;
  margin-top: 8px;
  border-radius: var(--app-radius-full);
  background: currentColor;
}
.is-pass {
  color: var(--app-success);
  border-color: color-mix(in srgb, var(--app-success) 34%, transparent);
}
.is-return {
  color: var(--app-warning);
  border-color: color-mix(in srgb, var(--app-warning) 34%, transparent);
}
</style>
