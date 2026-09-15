import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Configuracion de Vite (el servidor de desarrollo del frontend).
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5900, // debe coincidir con app.cors.allowed-origins del backend
    proxy: {
      // Todo lo que empiece con /api se reenvia al backend Spring Boot.
      // Asi en el codigo llamamos "/api/..." sin preocuparnos del host/puerto,
      // y ademas evitamos problemas de CORS en desarrollo.
      '/api': {
        target: 'http://localhost:9600',
        changeOrigin: true,
      },
    },
  },
})
