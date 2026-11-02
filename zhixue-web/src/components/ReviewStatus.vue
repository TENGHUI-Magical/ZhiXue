<template>
  <div
    v-if="result === 'PASS' || result === 'RETURN'"
    class="review-status"
    :class="result === 'RETURN' ? 'is-return' : 'is-pass'"
    :data-review-result="result"
  >
    <template v-if="result === 'PASS'">老师已复核这条讲解</template>
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
.review-status {
  margin-top: 8px;
  font-size: var(--app-fs-sm);
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.is-pass {
  color: var(--app-success);
}
.is-return {
  color: var(--app-warning);
}
</style>
