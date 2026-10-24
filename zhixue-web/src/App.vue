<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">智学领航</div>
      <el-menu :default-active="$route.path" router class="menu">
        <el-menu-item index="/diagnosis">错题诊断工作台</el-menu-item>
        <el-menu-item index="/error-book">我的错题本</el-menu-item>
        <el-menu-item index="/portrait">学情画像看板</el-menu-item>
        <el-menu-item index="/plan">周计划与打卡</el-menu-item>
        <el-menu-item index="/knowledge-graph">课程知识图谱</el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <span>{{ $route.meta.title }}</span>
        <span class="user">朱同学（数据结构）</span>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
// 全局布局：左侧导航（学生端五项 P0）+ 顶部标题栏
// TODO(P1)：登录态与角色判断（TEACHER 切换教师端导航，契约 2.1 节 role 字段）
</script>

<style scoped>
.layout { height: 100vh; }
.aside { border-right: 1px solid #e5e7eb; }
.logo { font-size: 20px; font-weight: 700; color: #3b5bdb; padding: 18px 24px; }
.menu { border-right: none; }
.header {
  display: flex; align-items: center; justify-content: space-between;
  border-bottom: 1px solid #e5e7eb; font-weight: 600;
}
.user { font-size: 13px; color: #6b7280; }
.main { background: #f5f6fa; }
</style>