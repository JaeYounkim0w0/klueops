import { expect, test, type Page } from '@playwright/test';
import { waitForAuthenticatedPortal } from './helpers/live-auth';

const enabled = process.env.AIOPS_LIVE_PHASE2_UI === 'true';
const applicationUrl = process.env.AIOPS_LIVE_APPLICATION_URL ?? '';
const username = process.env.AIOPS_LIVE_ADMIN_USERNAME ?? '';
const password = process.env.AIOPS_LIVE_ADMIN_PASSWORD ?? '';

async function login(page: Page): Promise<void> {
  // 실제 BFF 세션을 사용해 정적 mock에서 놓칠 수 있는 OIDC와 route 조합을 확인한다.
  await page.goto(`${applicationUrl}/login`);
  await page.getByRole('button', { name: /로그인 계속하기|Continue to sign in|재시도|Retry/ }).click();
  await page.getByLabel(/Username or email|사용자 이름|Username/i).fill(username);
  await page.locator('input[name="password"]').fill(password);
  await page.getByRole('button', { name: /Sign In|로그인/i }).click();
  await waitForAuthenticatedPortal(page, applicationUrl);
}

test.describe('live Phase 2 product UI acceptance', () => {
  test.skip(!enabled, 'Explicit live Phase 2 UI acceptance is disabled.');

  test('renders the shared desktop shell and Applications operation hierarchy', async ({ page }) => {
    const browserErrors: string[] = [];
    await login(page);
    // 인증 전 session probe의 의도된 401은 제외하고 제품 화면 진입 이후 오류만 수집한다.
    page.on('console', (message) => { if (message.type() === 'error') browserErrors.push(message.text()); });
    page.on('pageerror', (error) => browserErrors.push(error.message));
    await page.goto(`${applicationUrl}/applications`);

    await expect(page.locator('.sidebar')).toBeVisible();
    await expect(page.locator('.sidebar')).toHaveCSS('background-color', 'rgb(16, 29, 51)');
    await expect(page.locator('.workspace-topbar')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Deployed Applications' })).toBeVisible();
    await page.getByRole('combobox', { name: /Company \/ Tenant|회사 \/ Tenant/ }).selectOption({ label: 'Default Tenant' });
    await expect(page.locator('.application-summary')).toBeVisible();
    await expect(page.getByRole('table', { name: '배포된 Application' })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);

    await page.getByRole('button', { name: 'Application 배포' }).click();
    await expect(page.getByRole('dialog', { name: '어떤 Chart로 시작할까요?' })).toBeVisible();
    await page.getByRole('button', { name: '닫기' }).click();
    await expect(page.getByRole('dialog', { name: '어떤 Chart로 시작할까요?' })).toBeHidden();

    await page.goto(applicationUrl);
    await expect(page.getByRole('heading', { name: /Dashboard|대시보드/ })).toBeVisible();
    await expect(page.locator('.workspace-topbar')).toBeVisible();
    expect(browserErrors).toEqual([]);
  });
});
