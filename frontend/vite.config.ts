import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5174,
    // In development, /api calls go to the Spring Boot server, so the browser talks to one origin.
    proxy: { '/api': 'http://localhost:8080' },
  },
})
