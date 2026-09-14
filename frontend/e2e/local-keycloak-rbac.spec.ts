import { expect, test, type Page } from '@playwright/test';
import { waitForAuthenticatedPortal } from './helpers/live-auth';

const enabled = process.env.AIOPS_LOCAL_RBAC_VALIDATION === 'true';
const applicationUrl = process.env.AIOPS_LIVE_APPLICATION_URL ?? 'http://127.0.0.1:5173';

type RoleName = 'PLATFORM_ADMIN' | 'CLUSTER_ADMIN' | 'OPERATOR' | 'VIEWER';

interface RoleExpectation {
  prefix: 'ADMIN' | 'CLUSTER_ADMIN' | 'OPERATOR' | 'VIEWER';
  role: RoleName;
  capabilities: string[];
}

const roles: RoleExpectation[] = [
  {
    prefix: 'ADMIN',
    role: 'PLATFORM_ADMIN',
    capabilities: [
      'platform:admin', 'identity:manage', 'cluster:read', 'cluster:manage', 'analysis:read',
      'analysis:run', 'operation:execute', 'policy:manage', 'audit:read',
    ],
  },
  {
    prefix: 'CLUSTER_ADMIN',
    role: 'CLUSTER_ADMIN',
    capabilities: [
      'cluster:read', 'cluster:manage', 'analysis:read', 'analysis:run', 'operation:execute',
      'policy:manage', 'audit:read',
    ],
  },
  {
    prefix: 'OPERATOR',
    role: 'OPERATOR',
    capabilities: ['cluster:read', 'analysis:read', 'analysis:run', 'operation:execute'],
  },
  {
    prefix: 'VIEWER',
    role: 'VIEWER',
    capabilities: ['cluster:read', 'analysis:read'],
  },
];

async function login(page: Page, prefix: RoleExpectation['prefix']): Promise<void> {
  const username = process.env[`AIOPS_RBAC_${prefix}_USERNAME`] ?? '';
  const password = process.env[`AIOPS_RBAC_${prefix}_PASSWORD`] ?? '';
  expect(username, `${prefix} username must be configured`).not.toBe('');
  expect(password, `${prefix} password must be configured`).not.toBe('');

  await page.goto(`${applicationUrl}/login`);
  await page.getByRole('button', { name: /로그인 계속하기|Continue to sign in|재시도|Retry/ }).click();
  await page.getByLabel(/Username or email|사용자 이름|Username/i).fill(username);
  await page.locator('input[name="password"]').fill(password);
  await page.getByRole('button', { name: /Sign In|로그인/i }).click();
  await waitForAuthenticatedPortal(page, applicationUrl);
}

async function authSession(page: Page): Promise<{
  authenticated: boolean;
  capabilities: string[];
  scopes: Array<{ type: string; role: RoleName }>;
}> {
  const response = await page.request.get(`${applicationUrl}/api/auth/me`);
  expect(response.ok()).toBeTruthy();
  return response.json();
}

test.describe('local managed Keycloak RBAC acceptance', () => {
  test.skip(!enabled, 'Explicit local RBAC acceptance is disabled.');

  for (const expected of roles) {
    test(`${expected.role} receives only its intended capabilities`, async ({ page }) => {
      await login(page, expected.prefix);

      const session = await authSession(page);
      expect(session.authenticated).toBe(true);
      expect([...session.capabilities].sort()).toEqual([...expected.capabilities].sort());
      expect(session.scopes).toContainEqual(expect.objectContaining({ type: 'PLATFORM', role: expected.role }));

      const users = await page.request.get(`${applicationUrl}/api/security/users`);
      expect(users.status()).toBe(expected.role === 'PLATFORM_ADMIN' ? 200 : 403);
    });
  }

  test('VIEWER mutation is denied with a valid CSRF token and logout invalidates the BFF session', async ({ page }) => {
    await login(page, 'VIEWER');
    const csrfCookie = (await page.context().cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN');
    expect(csrfCookie?.value).toBeTruthy();

    const mutation = await page.request.post(`${applicationUrl}/api/clusters`, {
      data: {},
      headers: { 'X-XSRF-TOKEN': csrfCookie?.value ?? '' },
    });
    expect(mutation.status()).toBe(403);

    await page.getByRole('button', { name: /로그아웃|Sign out/ }).click();
    await expect.poll(async () => (await authSession(page)).authenticated).toBe(false);
  });
});
