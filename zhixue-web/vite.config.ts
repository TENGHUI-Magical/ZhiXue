import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// 智学领航前端构建配置（部署方案 1 节：5173 端口，/api 代理到 Spring Boot 8080）
export default defineConfig({
  plugins: [
    vue(),
    // Element Plus 按需引入：组件与样式只加载实际用到的部分
    AutoImport({
      resolvers: [ElementPlusResolver()],
      dts: 'src/types/auto-imports.d.ts'
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'src/types/components.d.ts'
    })
  ],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        // Windows 下 localhost 可能解析为 IPv6（::1），统一用 127.0.0.1
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  }
})
