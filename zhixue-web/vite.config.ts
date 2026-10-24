import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 智学领航前端构建配置（部署方案 1 节：5173 端口，/api 代理到 Spring Boot 8080）
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
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