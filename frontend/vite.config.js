import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';

export default defineConfig({
  plugins: [svelte()],
  server: {
    proxy: {
      // 开发环境下将 API 代理到 Quarkus 后端
      '/api': 'http://localhost:8080'
    }
  },
  preview: {
    proxy: {
      '/api': 'http://localhost:8080'
    }
  }
});
