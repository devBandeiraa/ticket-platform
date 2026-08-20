import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// A porta 5173 nao e so o padrao do Vite: e a origem liberada no CORS do api-gateway.
// Trocar aqui exige trocar CORS_ALLOWED_ORIGINS la, ou o navegador barra toda chamada.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: { port: 5173 },
  test: {
    // jsdom pelo localStorage, de que o cliente HTTP depende para guardar o refresh token.
    environment: 'jsdom',
    globals: true,
  },
})
