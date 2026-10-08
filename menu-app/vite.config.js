import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 서버(CorsConfig)가 5173 만 허용하므로 포트가 비어 있지 않으면 다른 포트로 넘어가지 않고 멈춘다.
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, strictPort: true },
})
