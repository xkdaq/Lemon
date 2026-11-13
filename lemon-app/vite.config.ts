import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

// 本地后端地址，默认 8080；同时跑多个后端时可用环境变量 API_TARGET 指定（如 http://localhost:8081）
const API_TARGET = process.env.API_TARGET || 'http://localhost:8080'

export default defineConfig({
  plugins: [uni()],
  server: {
    port: 5174,
    // H5 开发时代理到本地后端；小程序端直接用 VITE_API_BASE 的完整地址
    proxy: {
      '/api': { target: API_TARGET, changeOrigin: true },
      '/uploads': { target: API_TARGET, changeOrigin: true },
    },
  },
})
