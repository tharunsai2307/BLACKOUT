import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 3000,
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'maplibre-vendor': ['maplibre-gl'],
          'supabase-vendor': ['@supabase/supabase-js'],
          'ui-vendor': ['react', 'react-dom', 'react-router-dom', 'lucide-react'],
        },
      },
    },
    chunkSizeWarningLimit: 600,
  },
  // @ts-ignore
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
});
