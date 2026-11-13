import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      // 開発中（npm run dev）は、/api で始まるアクセスを8080番のSpring Bootへ中継する
      '/api': 'http://localhost:8080',
    },
  },
  build: {
    // ビルドした画面を、Spring Bootが配信するフォルダに出力する
    outDir: '../src/main/resources/static',
    // 出力前にフォルダの中身を空にする（古いファイルが残らないように）
    emptyOutDir: true,
  },
})