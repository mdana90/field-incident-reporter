import { defineConfig, devices } from '@playwright/test';
import { API_PORT, API_URL, WEB_PORT } from './e2e/env';

// Runs the real API on a throwaway data dir plus a production build of the dashboard.
export default defineConfig({
  testDir: './e2e',
  testMatch: '*.e2e.ts', // keeps Vitest (*.test.ts) and Playwright apart
  workers: 1, // tests share one API
  timeout: 45_000,
  use: { baseURL: `http://localhost:${WEB_PORT}`, trace: 'retain-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: `node e2e/api-server.mjs ${API_PORT}`,
      url: `${API_URL}/incidents`,
      reuseExistingServer: false,
    },
    {
      command: `npm run build && npx next start -p ${WEB_PORT}`,
      url: `http://localhost:${WEB_PORT}/incidents`,
      env: { API_BASE_URL: API_URL },
      reuseExistingServer: false,
      timeout: 180_000,
    },
  ],
});
