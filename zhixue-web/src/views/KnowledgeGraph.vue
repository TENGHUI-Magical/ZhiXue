<template>
  <div class="page">
    <h2>课程知识图谱</h2>
    <el-card>
      <!-- TODO(P1 需求 #23)：AntV G6 渲染 knowledge_point + knowledge_prereq
           节点按薄弱度红绿着色，点击展开详情抽屉 -->
      <el-empty description="知识点先修关系图谱（关系表渲染，不引入图数据库）" :image-size="120" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
// 骨架页：规划书 2.2 节决策——Neo4j 替换为 先修关系表 + G6 渲染
</script>

<style scoped>
.page h2 { margin-top: 0; }
</style>