import { fileURLToPath, URL } from 'node:url'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolveApiProxyTarget } from './src/config/proxyTarget'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiProxyTarget = resolveApiProxyTarget(env)

  return {
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return undefined
          }
          if (id.includes('ant-design-vue')) {
            const antDesignMatch = id.match(/ant-design-vue[\\/](es|lib)[\\/](.+?)([\\/]|$)/)
            if (antDesignMatch?.[2]) {
              return `antd-${antDesignMatch[2]}`
            }
            return 'antd-core'
          }
          if (id.includes('@ant-design/icons-vue')) {
            return 'antd-icons'
          }
          if (id.includes('vue') || id.includes('pinia') || id.includes('vue-router')) {
            return 'vue-vendor'
          }
          if (id.includes('axios')) {
            return 'http-vendor'
          }
          if (id.includes('markdown-it')) {
            return 'markdown-vendor'
          }
          if (id.includes('highlight.js')) {
            return 'highlight-vendor'
          }
          return 'vendor'
        },
      },
    },
  },
  server: {
    host: '127.0.0.1',
    port: 5174,
    proxy: {
      '/api': {
        target: apiProxyTarget,
        changeOrigin: true,
        secure: false,
      },
    },
  },
}})
