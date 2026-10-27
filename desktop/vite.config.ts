import { resolve } from 'node:path';
import { defineConfig } from 'vite';

// Four pages, one per Tauri window: the hidden core (data, tray), the panel, the mini strip and the toast.
export default defineConfig({
  clearScreen: false,
  resolve: {
    alias: {
      'damla-gauge': resolve(__dirname, '../gauge-js/build/compileSync/js/main/productionLibrary/kotlin/damla-gauge-js.mjs'),
    },
  },
  server: { port: 1420, strictPort: true, fs: { allow: ['..'] } },
  build: {
    target: 'es2022',
    rollupOptions: {
      input: {
        core: resolve(__dirname, 'index.html'),
        panel: resolve(__dirname, 'panel.html'),
        strip: resolve(__dirname, 'strip.html'),
        toast: resolve(__dirname, 'toast.html'),
      },
    },
  },
});
