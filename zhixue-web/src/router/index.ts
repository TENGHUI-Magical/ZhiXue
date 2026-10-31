import { createRouter, createWebHistory } from 'vue-router'

// 路由骨架（需求规格说明书第 3 章：学生端五项核心 P0 功能对应五个路由）
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/diagnosis'
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { title: '登录' }
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
    return '/login'
  }
  if (to.path === '/login' && token) {
    return currentRole() === 'TEACHER' ? '/teacher/reviews' : '/diagnosis'
  }
  const role = currentRole()
  // 本地有 token 但用户缓存缺失时先放行，App 会回查 /api/auth/me 再补角色；
  // 否则会在角色未知时与 /diagnosis 互相重定向。
  if (!role) return true
  if (to.meta.roles && !to.meta.roles.includes(role)) {
    return role === 'TEACHER' ? '/teacher/reviews' : '/diagnosis'
  }
  return true
})

router.afterEach((to) => {
  document.title = `${to.meta.title} · 智学领航`
})

export default router