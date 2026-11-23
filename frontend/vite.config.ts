import { fileURLToPath } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [
    vue(),
    tailwindcss(),
    VitePWA({
      registerType: 'autoUpdate',
      injectRegister: 'script',
      manifest: {
        name: 'WG Hub',
        short_name: 'WG Hub',
        description: 'Putzplan, Einkaufsliste, Kalender und Kasse für eure WG',
        lang: 'de',
        start_url: '/',
        display: 'standalone',
        background_color: '#fafaf9',
        theme_color: '#2f6f5e',
        icons: [
          { src: '/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: '/icon.svg', sizes: 'any', type: 'image/svg+xml' },
        ],
      },
      workbox: {
        // A new version takes over open tabs right away; main.ts then reloads them.
        skipWaiting: true,
        clientsClaim: true,
        // App shell only. API data is never cached, so nobody acts on stale data.
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [/^\/api\//],
        globPatterns: ['**/*.{js,css,html,svg,png,woff2}'],
      },
    }),
  ],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    proxy: { '/api': { target: 'http://localhost:3000' } },
  },
  test: { include: ['test/**/*.test.ts'] },
})
