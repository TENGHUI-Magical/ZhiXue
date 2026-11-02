import { createRouter, createWebHistory } from 'vue-router'
import { api } from '../api/request'

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

interface MeUser {
  role?: string
}

const homeForRole = (role: string) => (role === 'TEACHER' ? '/teacher' : '/today')

// 本地缓存缺角色时只发一次 /auth/me；并发导航共用同一个 Promise
let roleRequest: Promise<string> | null = null
const resolveRole = async (): Promise<string> => {
  const cached = currentRole()
  if (cached) return cached
  if (!roleRequest) {
    roleRequest = api
      .get<MeUser>('/api/auth/me')
      .then((me) => {
        localStorage.setItem('zhixue_user', JSON.stringify(me))
        return me.role ?? ''
      })
      .finally(() => {
        roleRequest = null
      })
  }
  return roleRequest
}

router.beforeEach(async (to) => {
  const token = localStorage.getItem('satoken')
  if (to.path !== '/login' && !token) {
    // 保留原始站内地址，登录后回跳（仅相对路径，防开放重定向）
    return to.fullPath && to.fullPath !== '/'
      ? { path: '/login', query: { redirect: to.fullPath } }
      : '/login'
  }
  if (!token) return true

  // 不再“角色未知先放行”：有 token 但缓存缺角色时，先按 /auth/me 的真实角色再判路由
  let role = currentRole()
  if (!role) {
    try {
      role = await resolveRole()
    } catch {
      // /me 失败（401 由请求拦截器跳到登录）时不在前端猜角色；回登录页重试
      return to.path === '/login'
        ? true
        : { path: '/login', query: { redirect: to.fullPath } }
    }
  }

  if (to.path === '/login') {
    return role ? homeForRole(role) : true
  }
  if (to.meta.roles && !role) {
    // token 有效但后端没有返回角色：留在 403，避免在 /login 与首页之间循环
    return '/403'
  }
  if (to.meta.roles && !to.meta.roles.includes(role)) {
    return homeForRole(role)
  }
  return true
})

router.afterEach((to) => {
  document.title = `${to.meta.title} · 智学领航`
})

export default router