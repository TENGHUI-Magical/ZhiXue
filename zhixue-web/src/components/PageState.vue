<template>
  <div v-if="loading" class="page-state" role="status" aria-live="polite" aria-label="加载中">
    <el-skeleton :rows="skeletonRows" animated />
  </div>
  <div v-else-if="error" class="page-state" role="alert">
    <el-alert :title="error" type="error" show-icon :closable="false" />
    <el-button class="state-action" type="primary" plain @click="emit('retry')">
      {{ retryText }}
    </el-button>
  </div>
  <el-empty v-else-if="empty" role="status" :description="emptyText" :image-size="96">
    <slot name="empty-action" />
  </el-empty>
  <slot v-else />
</template>

<script setup lang="ts">
interface Props {
  loading?: boolean;
  error?: string;
  empty?: boolean;
  emptyText?: string;
  retryText?: string;
  skeletonRows?: number;
}

withDefaults(defineProps<Props>(), {
  loading: false,
  error: "",
  empty: false,
  emptyText: "暂无数据",
  retryText: "重试",
  skeletonRows: 5
});

const emit = defineEmits<{
  (e: "retry"): void;
}>();
</script>

<style scoped>
.page-state {
  padding: 12px 0;
}
.state-action {
  margin-top: 12px;
}
</style>
