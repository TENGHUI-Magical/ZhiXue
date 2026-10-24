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
      path: '/diagnosis',
      name: 'diagnosis',
      component: () => import('../views/DiagnosisWorkbench.vue'),
      meta: { title: '错题诊断工作台' }
    },
    {
      path: '/error-book',
      name: 'error-book',
      component: () => import('../views/ErrorBook.vue'),
      meta: { title: '我的错题本' }
    },
    {
      path: '/portrait',
      name: 'portrait',
      component: () => import('../views/PortraitDashboard.vue'),
      meta: { title: '学情画像看板' }
    },
    {
      path: '/plan',
      name: 'plan',
      component: () => import('../views/PlanCheckin.vue'),
      meta: { title: '周计划与打卡' }
    },
    {
      path: '/knowledge-graph',
      name: 'knowledge-graph',
      component: () => import('../views/KnowledgeGraph.vue'),
      meta: { title: '课程知识图谱' }
    }
  ]
})

router.afterEach((to) => {
  document.title = `${to.meta.title} · 智学领航`
})

export default router