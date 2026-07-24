/// <reference types="vitest" />
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
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
        // Split heavy vendors into独立 chunks: business-code changes won't
        // invalidate the browser cache for stable libs, and no single chunk
        // ships 1MB+. echarts + element-plus are the heavy ones.
        manualChunks: {
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
          'element-plus': ['element-plus', '@element-plus/icons-vue'],
          'echarts': ['echarts'],
        }
      }
    }
  },
  test: {
    environment: 'jsdom',
    globals: false, // 显式 import { describe, it, expect, vi } from 'vitest',TS 友好
    include: ['src/**/*.{test,spec}.ts'],
    exclude: ['src/**/*.spec.ts'], // 留给未来的 e2e/spec 命名空间
    coverage: {
      provider: 'v8',
      reporter: ['text', 'text-summary', 'json-summary', 'html'],
      include: ['src/utils/**', 'src/api/**', 'src/stores/**', 'src/composables/**', 'src/constants/**'],
      // 不覆盖 .vue 组件(本次目标"纯逻辑层 100%");router/main.ts 有副作用排除
      exclude: ['src/main.ts', 'src/router/**', 'src/types/**'],
      thresholds: {
        // 全局门禁,所有被 include 的目录都必须 100%(lines/branches/functions/statements)
        // 单文件未达 100% 直接 build fail,与后端 jacoco 模式一致
        lines: 100,
        branches: 100,
        functions: 100,
        statements: 100,
        perFile: true
      }
    }
  }
})