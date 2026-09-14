import { defineConfig, devices } from '@playwright/test';

const liveIdentity = process.env.AIOPS_LIVE_IDENTITY_ENABLED === 'true';
const configuredPort = process.env.AIOPS_E2E_PORT ?? '4174';
const e2ePort = Number(configuredPort);

if (!Number.isInteger(e2ePort) || e2ePort < 1024 || e2ePort > 65535) {
  throw new Error(`AIOPS_E2E_PORT must be an integer between 1024 and 65535: ${configuredPort}`);
}

const baseURL = process.env.AIOPS_E2E_BASE_URL ?? `http://127.0.0.1:${e2ePort}`;

export default defineConfig({
  testDir: './e2e',
  outputDir: './test-results/playwright',
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 2 : undefined,
  reporter: process.env.CI
    ? [['line'], ['html', { outputFolder: 'playwright-report', open: 'never' }]]
    : 'line',
  use: {
    baseURL,
    trace: liveIdentity ? 'off' : 'on-first-retry',
    screenshot: liveIdentity ? 'off' : 'only-on-failure',
    video: liveIdentity ? 'off' : 'retain-on-failure'
  },
  projects: [
    {
      name: 'desktop-chromium',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 1000 } }
    },
    {
      name: 'mobile-chromium',
      use: { ...devices['Pixel 7'] }
    }
  ],
  webServer: liveIdentity ? undefined : {
    command: `npm run dev -- --host 127.0.0.1 --port ${e2ePort}`,
    url: `http://127.0.0.1:${e2ePort}`,
    reuseExistingServer: !process.env.CI,
    timeout: 60_000
  }
});
