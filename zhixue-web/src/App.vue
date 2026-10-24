<template>
  <router-view v-if="$route.path === '/login'" />

  <el-container v-else class="layout">
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
        <span class="user">
          {{ userName }}
          <el-button link type="primary" @click="logout">退出</el-button>
        </span>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api } from "./api/request";

const router = useRouter();
const route = useRoute();
const userName = ref("未登录");

interface MeUser {
  userId: number;
  realName: string;
  role: string;
  className?: string | null;
}

const applyUser = (user: MeUser) => {
  userName.value = `${user.realName}（${user.role}）`;
};

/** 路由变化时刷新用户名；本地有 token 但缺 user 信息时回查 /api/auth/me */
const refreshUser = async () => {
  if (!localStorage.getItem("satoken")) {
    userName.value = "未登录";
    return;
  }
  const raw = localStorage.getItem("zhixue_user");
  if (raw) {
    try {
      applyUser(JSON.parse(raw) as MeUser);
      return;
    } catch {
      // 本地缓存坏了，走接口
    }
  }
  try {
    const me = await api.get<MeUser>("/api/auth/me");
    localStorage.setItem("zhixue_user", JSON.stringify(me));
    applyUser(me);
  } catch {
    userName.value = "未登录";
  }
};

watch(() => route.path, refreshUser, { immediate: true });

const logout = async () => {
  try {
    await api.post("/api/auth/logout");
  } catch {
    // 登出失败也要清本地态
  } finally {
    localStorage.removeItem("satoken");
    localStorage.removeItem("zhixue_user");
    userName.value = "未登录";
    await router.replace("/login");
  }
};
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
.user { font-size: 13px; color: #6b7280; display: flex; align-items: center; gap: 8px; }
.main { background: #f5f6fa; }
</style>
