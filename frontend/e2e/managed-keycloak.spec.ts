import { expect, test, type Page } from '@playwright/test';
import { waitForAuthenticatedPortal } from './helpers/live-auth';

const liveEnabled = process.env.AIOPS_LIVE_IDENTITY_ENABLED === 'true';
const applicationUrl = process.env.AIOPS_LIVE_APPLICATION_URL ?? '';

interface LiveUser {
  username: string;
  password: string;
}

function liveUser(prefix: 'ADMIN' | 'OPERATOR' | 'VIEWER'): LiveUser {
  return {
    username: process.env[`AIOPS_LIVE_${prefix}_USERNAME`] ?? '',
    password: process.env[`AIOPS_LIVE_${prefix}_PASSWORD`] ?? '',
  };
}

async function login(page: Page, user: LiveUser): Promise<void> {
  await page.goto(`${applicationUrl}/login`);
  await page.getByRole('button', { name: /로그인 계속하기|Continue to sign in|재시도|Retry/ }).click();
  await page.getByLabel(/Username or email|사용자 이름|Username/i).fill(user.username);
  await page.locator('input[name="password"]').fill(user.password);
  await page.getByRole('button', { name: /Sign In|로그인/i }).click();
  await waitForAuthenticatedPortal(page, applicationUrl);
}

test.describe('managed Keycloak multi-user acceptance', () => {
  test.skip(!liveEnabled, 'Explicit live identity acceptance is disabled.');

  test('platform administrator can authenticate and open access management', async ({ page }) => {
    await login(page, liveUser('ADMIN'));
    await page.goto(`${applicationUrl}/settings/access`);
    await expect(page.getByRole('heading', { name: /계정 및 권한|Identity & Access/ })).toBeVisible();
  });

  test('namespace operator is isolated by the server-side object scope', async ({ page }) => {
    await login(page, liveUser('OPERATOR'));

    const visibleClusterId = process.env.AIOPS_LIVE_OPERATOR_CLUSTER_ID ?? '';
    const hiddenClusterId = process.env.AIOPS_LIVE_HIDDEN_CLUSTER_ID ?? '';
    const visibleNamespace = process.env.AIOPS_LIVE_OPERATOR_NAMESPACE ?? '';
    const clusters = await page.request.get(`${applicationUrl}/api/clusters`);
    expect(clusters.ok()).toBeTruthy();
    const body = await clusters.json() as Array<{ id: string }>;
    expect(body.map((cluster) => cluster.id)).toContain(visibleClusterId);
    expect(body.map((cluster) => cluster.id)).not.toContain(hiddenClusterId);

    const hidden = await page.request.get(
      `${applicationUrl}/api/clusters/${hiddenClusterId}/namespaces/${encodeURIComponent(visibleNamespace)}/diagnostics`,
    );
    expect([403, 404]).toContain(hidden.status());
  });

  test('viewer mutation is rejected and logout invalidates the session', async ({ page }) => {
    await login(page, liveUser('VIEWER'));
    const mutation = await page.request.post(`${applicationUrl}/api/clusters`, {
      data: { name: 'must-not-be-created' },
      headers: { 'X-XSRF-TOKEN': 'invalid-viewer-token' },
    });
    expect(mutation.status()).toBe(403);

    await page.getByRole('button', { name: /로그아웃|Sign out/ }).click();
    const session = await page.request.get(`${applicationUrl}/api/auth/me`);
    expect(session.ok()).toBeTruthy();
    expect((await session.json()).authenticated).toBe(false);
  });
});
