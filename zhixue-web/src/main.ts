import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'element-plus/theme-chalk/dark/css-vars.css'
import './styles/theme.css'
import App from './App.vue'
import router from './router'
import { applyTheme, getStoredTheme } from './composables/useTheme'

// 首屏挂载前应用主题，避免刷新闪白
applyTheme(getStoredTheme())

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
