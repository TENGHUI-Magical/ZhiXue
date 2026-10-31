<template>
  <div class="state-page">
    <el-card class="state-card" shadow="never">
      <div class="state-code">404</div>
      <h2>页面不存在</h2>
      <p class="state-desc">链接可能已失效或输入有误。回首页继续，或返回上一页。</p>
      <div class="state-actions">
        <el-button @click="goBack">返回上一页</el-button>
        <el-button type="primary" @click="goHome">回到首页</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";

const router = useRouter();

const homePath = () => {
  try {
    const raw = localStorage.getItem("zhixue_user");
    const role = raw ? (JSON.parse(raw) as { role?: string }).role : "";
    return role === "TEACHER" ? "/teacher/reviews" : "/today";
  } catch {
    return "/today";
  }
};

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
  border-radius: var(--app-radius-lg);
}
.state-code {
  font-size: 56px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--app-text-muted);
  line-height: 1;
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
</style>
