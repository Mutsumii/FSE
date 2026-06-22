import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 3000,
    proxy: {
      '/api/publish': {
        target: 'http://localhost:8083',
        changeOrigin: true
      }
    }
  },
  test: {
    environment: 'jsdom',
    globals: true,
  },
})
