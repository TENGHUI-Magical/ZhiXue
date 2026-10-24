<template>
  <div class="page">
    <h2>周计划与打卡</h2>
    <el-card header="本周打卡清单（每天限 3 题，study_plan 表）">
      <!-- TODO(P0)：GET /api/plan/week → 七天卡片；PENDING/DONE/OVERDUE 三态渲染 -->
      <el-empty description="本周计划尚未生成，完成诊断后将自动生成打卡清单" />
    </el-card>
    <el-card header="三层练习推荐（契约 2.6 节）" class="mt16">
      <!-- TODO(P0)：GET /api/practice/recommend?logId= → 折叠卡片 + 15 字推荐理由气泡 -->
      <el-empty description="巩固 / 变式 / 拓展 各 1 题，附带推荐理由" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
// 骨架页：与契约 2.6 节 items[].layer/reason 对齐
</script>

<style scoped>
.page h2 { margin-top: 0; }
.mt16 { margin-top: 16px; }
</style>