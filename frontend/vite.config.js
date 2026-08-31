import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import legacy from '@vitejs/plugin-legacy'

// 端口可经环境变量覆盖（多项目并行开发时避免冲突），缺省保持 5173/8080
const devPort = Number(process.env.DEV_PORT) || 5173
const apiTarget = process.env.API_TARGET || 'http://localhost:8080'

export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
    legacy({
      targets: ['> 1%', 'last 2 versions', 'not dead'],
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: devPort,
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/__tests__/**/*.test.js'],
  },
})
