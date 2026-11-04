import { createApp } from 'vue'
import 'element-plus/theme-chalk/dark/css-vars.css'
import './styles/theme.css'
import App from './App.vue'
import router from './router'
import { applyTheme, getStoredTheme } from './composables/useTheme'

// 首屏挂载前应用主题，避免刷新闪白
applyTheme(getStoredTheme())

// 未引入 Pinia：当前无跨页共享状态，用户态经 localStorage + /api/auth/me 读取；
// 引入全局状态管理时再评估（避免入口包死重）
const app = createApp(App)
app.use(router)
app.mount('#app')

// __E2E_RELOAD__ 临时红检：挂载后连续整页 reload 3 次
try { const n = Number(sessionStorage.getItem('__e2e_reload__') || '0'); if (n < 3) { sessionStorage.setItem('__e2e_reload__', String(n + 1)); setTimeout(() => location.reload(), 200) } } catch { }
