import { expect, type Page } from '@playwright/test';

interface AuthSession {
  authenticated?: boolean;
}

export async function waitForAuthenticatedPortal(
  page: Page,
  applicationUrl: string,
  timeoutMs = 30_000,
): Promise<void> {
  const applicationOrigin = new URL(applicationUrl).origin;
  await page.waitForURL((url) => url.origin === applicationOrigin && !isAuthenticationRoute(url.pathname), {
    timeout: timeoutMs,
  });
  await expect.poll(async () => {
    const response = await page.request.get(`${applicationUrl}/api/auth/me`);
    if (!response.ok()) return false;
    const session = await response.json() as AuthSession;
    return session.authenticated === true;
  }, {
    message: 'Portal BFF session did not become authenticated after the OIDC callback',
    timeout: timeoutMs,
  }).toBe(true);
  await expect(page.getByText('KlueOps')).toBeVisible();
}

function isAuthenticationRoute(pathname: string): boolean {
  return pathname === '/login'
    || pathname.startsWith('/login/')
    || pathname === '/oauth2'
    || pathname.startsWith('/oauth2/');
}
