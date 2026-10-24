import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 智学领航前端构建配置（部署方案 1 节：5173 端口，/api 代理到 Spring Boot 8080）
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        // Windows 下 localhost 可能解析为 IPv6（::1），统一用 127.0.0.1
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    rollupOptions: {
      output: {
        // 大体积第三方库各自分包，避免单个 chunk 超过 500 kB
        manualChunks: {
          'element-plus': ['element-plus'],
          echarts: ['echarts'],
          g6: ['@antv/g6']
        }
      }
    }
  }
})