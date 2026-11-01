import { createRouter, createWebHistory } from 'vue-router'

// 路由骨架（需求规格说明书第 3 章：学生端五项核心 P0 功能对应五个路由）
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: () => (currentRole() === 'TEACHER' ? '/teacher' : '/today')
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { title: '登录' }
    },
    {
      path: '/today',
      name: 'today',
      component: () => import('../views/TodayDashboard.vue'),
      meta: { title: '今日工作台', roles: ['STUDENT'] }
    },
    {
      path: '/diagnosis',
      name: 'diagnosis',
      component: () => import('../views/DiagnosisWorkbench.vue'),
      meta: { title: '错题诊断工作台', roles: ['STUDENT'] }
    },
    {
      path: '/error-book',
      name: 'error-book',
      component: () => import('../views/ErrorBook.vue'),
      meta: { title: '我的错题本', roles: ['STUDENT'] }
    },
    {
      path: '/portrait',
      name: 'portrait',
      component: () => import('../views/PortraitDashboard.vue'),
      meta: { title: '学情画像看板', roles: ['STUDENT'] }
    },
    {
      path: '/plan',
      name: 'plan',
      component: () => import('../views/PlanCheckin.vue'),
      meta: { title: '周计划与打卡', roles: ['STUDENT'] }
    },
    {
      path: '/knowledge-graph',
      name: 'knowledge-graph',
      component: () => import('../views/KnowledgeGraph.vue'),
      meta: { title: '课程知识图谱', roles: ['STUDENT'] }
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('../views/ProfileView.vue'),
      meta: { title: '个人中心' }
    },
    {
      path: '/teacher',
      name: 'teacher-overview',
      component: () => import('../views/TeacherOverview.vue'),
      meta: { title: '教学概览', roles: ['TEACHER'] }
    },
    {
      path: '/teacher/reviews',
      name: 'teacher-reviews',
      component: () => import('../views/TeacherReviews.vue'),
      meta: { title: '教师评阅', roles: ['TEACHER'] }
    },
    {
      path: '/teacher/class',
      name: 'teacher-class',
      component: () => import('../views/TeacherClass.vue'),
      meta: { title: '班级学情', roles: ['TEACHER'] }
    },
    {
      path: '/403',
      name: 'forbidden',
      component: () => import('../views/ForbiddenView.vue'),
      meta: { title: '无权访问' }
    },
    {
      path: '/404',
      name: 'not-found',
      component: () => import('../views/NotFoundView.vue'),
      meta: { title: '页面不存在' }
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/404'
    }
  ]
})

const currentRole = () => {
  try {
    const raw = localStorage.getItem('zhixue_user')
    return raw ? (JSON.parse(raw) as { role?: string }).role ?? '' : ''
  } catch {
    return ''
  }
}

router.beforeEach((to) => {
  const token = localStorage.getItem('satoken')
  if (to.path !== '/login' && !token) {
    // 保留原始站内地址，登录后回跳（仅相对路径，防开放重定向）
    return to.fullPath && to.fullPath !== '/'
      ? { path: '/login', query: { redirect: to.fullPath } }
      : '/login'
  }
  if (to.path === '/login' && token) {
    return currentRole() === 'TEACHER' ? '/teacher' : '/today'
  }
  const role = currentRole()
  // 本地有 token 但用户缓存缺失时先放行，App 会回查 /api/auth/me 再补角色；
  // 否则会在角色未知时与默认首页互相重定向。
  if (!role) return true
  if (to.meta.roles && !to.meta.roles.includes(role)) {
    return role === 'TEACHER' ? '/teacher' : '/today'
  }
  return true
})

router.afterEach((to) => {
  document.title = `${to.meta.title} · 智学领航`
})

export default router