import process from 'node:process';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

const backendProxy = {
  target: 'http://localhost:8080',
  changeOrigin: false,
  xfwd: true,
};

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': new URL('./src', import.meta.url).pathname
    }
  },
  server: {
    host: process.env.AIOPS_FRONTEND_HOST || '127.0.0.1',
    port: 5173,
    proxy: {
      '/api': backendProxy,
      '/ws': { ...backendProxy, changeOrigin: true, ws: true, rewriteWsOrigin: true },
      '/v3/api-docs': backendProxy,
      '/oauth2': backendProxy,
      '^/login/oauth2/code/': backendProxy,
      '/logout': backendProxy,
    }
  }
});
