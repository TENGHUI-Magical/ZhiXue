<template>
  <div class="state-page">
    <el-card class="state-card" shadow="never">
      <BrandMark :size="56" tone="outline" class="state-brand" aria-hidden="true" />
      <div class="state-code">403</div>
      <h2>你没有权限查看该内容</h2>
      <p class="state-desc">该页面仅对特定角色开放。可以返回上一页，或回到你的首页。</p>
      <div class="state-actions">
        <el-button @click="goBack">返回上一页</el-button>
        <el-button type="primary" @click="goHome">回到首页</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";
import { getUser } from "../utils/authSession";
import BrandMark from "../components/BrandMark.vue";

const router = useRouter();

const homePath = () => (getUser()?.role === "TEACHER" ? "/teacher" : "/today");

const goHome = () => router.replace(homePath());
const goBack = () => {
  if (window.history.length > 1) router.back();
  else goHome();
};
</script>

<style scoped>
.state-page {
  min-height: 60vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.state-card {
  max-width: 520px;
  width: 100%;
  text-align: center;
  /* B 层焦点卡：品牌空态用 24px 外层圆角，子内容保持 8–12px */
  border-radius: var(--app-radius-2xl);
}
.state-brand {
  margin: 0 auto 10px;
  opacity: 0.9;
}
.state-code {
  font-size: 56px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--app-text-muted);
  line-height: 1;
  font-variant-numeric: tabular-nums;
}
.state-card h2 {
  margin: 14px 0 8px;
  font-size: var(--app-fs-lg);
  color: var(--app-text);
}
.state-desc {
  margin: 0 0 18px;
  color: var(--app-text-secondary);
  line-height: 1.7;
}
.state-actions {
  display: flex;
  gap: 8px;
  justify-content: center;
  flex-wrap: wrap;
}
@media (max-width: 767px) {
  .state-page {
    min-height: auto;
    padding: 12px 0;
  }
  .state-actions :deep(.el-button) {
    min-height: 44px;
    min-width: 132px;
  }
}
</style>
