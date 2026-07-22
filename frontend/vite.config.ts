import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const backend = process.env.VITE_BACKEND_URL ?? 'http://localhost:8080'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/items': backend,
      '/recommend': backend,
      '/outfit': backend,
      '/uploads': backend,
      '/fits': backend,
    },
  },
})
