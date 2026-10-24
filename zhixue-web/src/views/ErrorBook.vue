<template>
  <div class="page">
    <h2>我的错题本</h2>
    <!-- TODO(P0)：GET 错题列表（按章节/知识点筛选、分页契约 1.3 节） -->
    <el-table :data="[]" empty-text="暂无错题记录，去「错题诊断工作台」收录第一道吧">
      <el-table-column prop="logId" label="编号" width="100" />
      <el-table-column prop="kpId" label="知识点" width="140" />
      <el-table-column prop="errorType" label="错因" width="120" />
      <el-table-column prop="correctedText" label="题目（校正后）" />
      <el-table-column prop="status" label="状态" width="110" />
      <el-table-column label="操作" width="140" />
    </el-table>
  </div>
</template>

<script setup lang="ts">
// 骨架页：字段与 student_error_record 表对齐（数据库设计说明书 3 节）
</script>

<style scoped>
.page h2 { margin-top: 0; }
</style>