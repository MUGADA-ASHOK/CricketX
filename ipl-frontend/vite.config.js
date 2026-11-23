import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8090',
        changeOrigin: true
      },
      '/auth': {
        target: 'http://localhost:8090',
        changeOrigin: true
      },
      '/events': {
        target: 'http://localhost:8090',
        changeOrigin: true
      },
      '/orders': {
        target: 'http://localhost:8090',
        changeOrigin: true
      },
      '/notifications': {
        target: 'http://localhost:8090',
        changeOrigin: true
      }
    }
  }
});

