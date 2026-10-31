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
  // 拆包：把重量级第三方库从入口 index chunk 分离，压低最大单 chunk 体积
  // （体积棘轮 ceilings.json 同时约束 totalJs 与 largestJs；拆包主要改善 largestJs）
  build: {
    rollupOptions: {
      output: {
        manualChunks(id: string) {
          if (!id.includes('node_modules')) return
          // 注意：不要分组 element-plus——unplugin 已按组件懒加载拆分，
          // 强制合并会把整个 EP 打成 eager 大 chunk，反而暴涨总体积。
          if (id.includes('/vue-router/') || id.includes('/@vue/') || id.includes('/vue/')) return 'vue-vendor'
          if (id.includes('/axios/')) return 'axios-vendor'
        }
      }
    }
  },
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
