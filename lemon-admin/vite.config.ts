import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 本地后端地址，默认 8080；同时跑多个后端时可用环境变量 API_TARGET 指定（如 http://localhost:8081）
const API_TARGET = process.env.API_TARGET || 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: API_TARGET, changeOrigin: true },
      // 开发期上传的图片由后端提供
      '/uploads': { target: API_TARGET, changeOrigin: true },
    },
  },
})
