<template>
  <div class="page">
    <h2>学情画像看板</h2>
    <el-row :gutter="16">
      <el-col :span="10">
        <el-card header="五维能力雷达（契约 2.5 节）">
          <!-- TODO(P0)：ECharts radar，五维 dim：concept/structure/algorithm/complexity/boundary -->
          <el-empty description="数据来自 learning_portrait_snapshot 快照表" :image-size="90" />
        </el-card>
      </el-col>
      <el-col :span="14">
        <el-card header="薄弱知识点 TOP">
          <el-empty description="掌握度 = 1 − 错题权重/总训练量（公式公开，需求说明 5 节）" :image-size="90" />
        </el-card>
        <el-card header="错因分布（5 类标准错因）" class="mt16">
          <el-empty description="概念混淆/步骤遗漏/边界条件忽略/计算失误/方法缺失" :image-size="90" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
// 骨架页：三个图表区域与契约 2.5 节 scores/weakestKps/errorDist 字段一一对应
</script>

<style scoped>
.page h2 { margin-top: 0; }
.mt16 { margin-top: 16px; }
</style>