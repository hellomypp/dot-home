import {createLogger} from 'vite';
import {redactTokenPaths} from './src/lib/privacy.ts';
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

const logger=createLogger();
const originalError=logger.error.bind(logger),originalWarn=logger.warn.bind(logger),originalInfo=logger.info.bind(logger),originalWarnOnce=logger.warnOnce.bind(logger);
logger.error=(message,options)=>originalError(redactTokenPaths(message),options);
logger.warn=(message,options)=>originalWarn(redactTokenPaths(message),options);
logger.info=(message,options)=>originalInfo(redactTokenPaths(message),options);
logger.warnOnce=(message,options)=>originalWarnOnce(redactTokenPaths(message),options);
const apiPort = Number(process.env.DOT_HOME_API_PORT || '8080');
if (!Number.isInteger(apiPort) || apiPort < 1 || apiPort > 65535) throw new Error('Invalid loopback API port');
export default defineConfig({
  customLogger: logger,
  plugins: [react()],
  server: {
    host: '127.0.0.1',
    allowedHosts: process.env.DOT_HOME_PREVIEW_HOST ? [process.env.DOT_HOME_PREVIEW_HOST] : [],
    port: 5173,
    strictPort: true,
    proxy: {
      '^/healthz(?:\\?.*)?$': `http://127.0.0.1:${apiPort}`,
      '/api': `http://127.0.0.1:${apiPort}`,
      '/oauth2': `http://127.0.0.1:${apiPort}`,
      '/login/oauth2': `http://127.0.0.1:${apiPort}`,
      '/ws': { target: `ws://127.0.0.1:${apiPort}`, ws: true },
    },
  },
  build: {
    chunkSizeWarningLimit: 1600,
    rolldownOptions: {
      output: {
        manualChunks: (id) => id.includes('/phaser/') ? 'phaser' : undefined,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.test.{ts,tsx}'],
    restoreMocks: true,
    clearMocks: true,
  },
});
