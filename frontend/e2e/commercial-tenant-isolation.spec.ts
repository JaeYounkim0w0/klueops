import { expect, test, type APIRequestContext, type Browser, type BrowserContext, type Page } from '@playwright/test';
import { waitForAuthenticatedPortal } from './helpers/live-auth';

const enabled = process.env.AIOPS_COMMERCIAL_TENANT_VALIDATION === 'true';
const applicationUrl = process.env.AIOPS_LIVE_APPLICATION_URL ?? 'http://127.0.0.1:30081';

interface Credentials { username: string; password: string }
interface SessionUser { id: string }
interface AuthSession { authenticated: boolean; user: SessionUser | null }
interface Tenant { id: string; code: string }
interface Workspace { id: string; code: string }
interface Cluster { id: string; name: string; tenantId: string; workspaceId: string }
interface RoleBinding { id: string }

const createdClusterIds: string[] = [];
const createdBindingIds: string[] = [];
let adminContext: BrowserContext | undefined;

function credentials(prefix: 'ADMIN' | 'TENANT_A' | 'TENANT_B'): Credentials {
  return {
    username: process.env[`AIOPS_COMMERCIAL_${prefix}_USERNAME`] ?? '',
    password: process.env[`AIOPS_COMMERCIAL_${prefix}_PASSWORD`] ?? '',
  };
}

async function login(browser: Browser, user: Credentials): Promise<BrowserContext> {
  expect(user.username).not.toBe('');
  expect(user.password).not.toBe('');
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto(`${applicationUrl}/login`);
  await page.getByRole('button', { name: /로그인 계속하기|Continue to sign in|재시도|Retry/ }).click();
  await page.getByLabel(/Username or email|사용자 이름|Username/i).fill(user.username);
  await page.locator('input[name="password"]').fill(user.password);
  await page.getByRole('button', { name: /Sign In|로그인/i }).click();
  await waitForAuthenticatedPortal(page, applicationUrl);
  return context;
}

async function csrf(request: APIRequestContext): Promise<string> {
  await request.get(`${applicationUrl}/api/auth/me`);
  const state = await request.storageState();
  return state.cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')?.value ?? '';
}

async function get<T>(request: APIRequestContext, path: string): Promise<T> {
  const response = await request.get(`${applicationUrl}${path}`);
  expect(response.ok(), `${path} returned ${response.status()}`).toBeTruthy();
  return response.json() as Promise<T>;
}

async function post<T>(request: APIRequestContext, path: string, data: unknown): Promise<T> {
  const response = await request.post(`${applicationUrl}${path}`, {
    data,
    headers: { 'X-XSRF-TOKEN': await csrf(request) },
  });
  expect(response.ok(), `${path} returned ${response.status()}: ${await response.text()}`).toBeTruthy();
  return response.json() as Promise<T>;
}

async function ensureTenant(request: APIRequestContext, code: string): Promise<Tenant> {
  const tenants = await get<Tenant[]>(request, '/api/tenants');
  return tenants.find((tenant) => tenant.code === code)
    ?? post<Tenant>(request, '/api/tenants', { code, name: `Acceptance ${code}`, description: 'Commercial isolation fixture' });
}

async function ensureWorkspace(request: APIRequestContext, tenantId: string, code: string): Promise<Workspace> {
  const workspaces = await get<Workspace[]>(request, `/api/tenants/${tenantId}/workspaces`);
  return workspaces.find((workspace) => workspace.code === code)
    ?? post<Workspace>(request, `/api/tenants/${tenantId}/workspaces`, {
      code, name: `Acceptance ${code}`, description: 'Commercial isolation fixture',
    });
}

async function registerFixtureCluster(
  request: APIRequestContext,
  tenantId: string,
  workspaceId: string,
  name: string,
): Promise<Cluster> {
  const kubeconfig = `apiVersion: v1
kind: Config
clusters:
  - name: acceptance
    cluster:
      server: https://127.0.0.1:6443
      insecure-skip-tls-verify: true
contexts:
  - name: acceptance
    context:
      cluster: acceptance
      user: acceptance
current-context: acceptance
users:
  - name: acceptance
    user:
      token: acceptance-fixture-token
`;
  const cluster = await post<Cluster>(request, '/api/clusters', {
    tenantId,
    workspaceId,
    name,
    description: 'Commercial tenant isolation fixture',
    environment: 'DEV',
    provider: 'KIND',
    region: 'acceptance',
    credentialType: 'KUBECONFIG',
    kubeconfig,
    namespaceAccess: { clusterWide: true, allowedNamespaces: [], defaultNamespace: 'default' },
    syncSettings: { autoSyncEnabled: false, syncIntervalSeconds: 300 },
  });
  createdClusterIds.push(cluster.id);
  return cluster;
}

async function bindTenant(
  request: APIRequestContext,
  principalKey: string,
  role: 'OPERATOR' | 'VIEWER',
  tenantId: string,
): Promise<void> {
  const binding = await post<RoleBinding>(request, '/api/security/role-bindings', {
    principalType: 'USER', principalKey, role, scopeType: 'TENANT', tenantId,
  });
  createdBindingIds.push(binding.id);
}

async function expectTenantIsolation(
  request: APIRequestContext,
  own: { tenant: Tenant; workspace: Workspace; cluster: Cluster },
  hidden: { tenant: Tenant; workspace: Workspace; cluster: Cluster },
): Promise<void> {
  const tenants = await get<Tenant[]>(request, '/api/tenants');
  expect(tenants.map((item) => item.id)).toContain(own.tenant.id);
  expect(tenants.map((item) => item.id)).not.toContain(hidden.tenant.id);

  const clusters = await get<Cluster[]>(request, '/api/clusters');
  expect(clusters.map((item) => item.id)).toContain(own.cluster.id);
  expect(clusters.map((item) => item.id)).not.toContain(hidden.cluster.id);

  expect((await request.get(`${applicationUrl}/api/tenants/${own.tenant.id}`)).status()).toBe(200);
  expect([403, 404]).toContain((await request.get(`${applicationUrl}/api/tenants/${hidden.tenant.id}`)).status());
  expect((await request.get(`${applicationUrl}/api/workspaces/${own.workspace.id}`)).status()).toBe(200);
  expect([403, 404]).toContain((await request.get(`${applicationUrl}/api/workspaces/${hidden.workspace.id}`)).status());
  expect((await request.get(`${applicationUrl}/api/clusters/${own.cluster.id}`)).status()).toBe(200);
  expect([403, 404]).toContain((await request.get(`${applicationUrl}/api/clusters/${hidden.cluster.id}`)).status());
  expect((await request.get(`${applicationUrl}/api/analysis/history?clusterId=${own.cluster.id}`)).status()).toBe(200);
  expect([403, 404]).toContain((await request.get(
    `${applicationUrl}/api/analysis/history?clusterId=${hidden.cluster.id}`,
  )).status());
}

test.describe('commercial two-tenant isolation', () => {
  test.skip(!enabled, 'Explicit commercial tenant validation is disabled.');
  test.describe.configure({ mode: 'serial' });

  test('list, direct URL, analysis history, and mutations honor the same tenant boundary', async ({ browser }) => {
    const tenantAContext = await login(browser, credentials('TENANT_A'));
    const tenantBContext = await login(browser, credentials('TENANT_B'));
    const tenantASession = await get<AuthSession>(tenantAContext.request, '/api/auth/me');
    const tenantBSession = await get<AuthSession>(tenantBContext.request, '/api/auth/me');
    await tenantAContext.close();
    await tenantBContext.close();

    adminContext = await login(browser, credentials('ADMIN'));
    const suffix = process.env.AIOPS_COMMERCIAL_FIXTURE_SUFFIX ?? Date.now().toString();
    const tenantA = await ensureTenant(adminContext.request, 'commercial-a');
    const tenantB = await ensureTenant(adminContext.request, 'commercial-b');
    const workspaceA = await ensureWorkspace(adminContext.request, tenantA.id, 'commercial-a');
    const workspaceB = await ensureWorkspace(adminContext.request, tenantB.id, 'commercial-b');
    const clusterA = await registerFixtureCluster(adminContext.request, tenantA.id, workspaceA.id, `commercial-a-${suffix}`);
    const clusterB = await registerFixtureCluster(adminContext.request, tenantB.id, workspaceB.id, `commercial-b-${suffix}`);
    await bindTenant(adminContext.request, tenantASession.user?.id ?? '', 'OPERATOR', tenantA.id);
    await bindTenant(adminContext.request, tenantBSession.user?.id ?? '', 'VIEWER', tenantB.id);

    const scopedA = await login(browser, credentials('TENANT_A'));
    const scopedB = await login(browser, credentials('TENANT_B'));
    await expectTenantIsolation(scopedA.request, { tenant: tenantA, workspace: workspaceA, cluster: clusterA },
      { tenant: tenantB, workspace: workspaceB, cluster: clusterB });
    await expectTenantIsolation(scopedB.request, { tenant: tenantB, workspace: workspaceB, cluster: clusterB },
      { tenant: tenantA, workspace: workspaceA, cluster: clusterA });

    const viewerDelete = await scopedB.request.delete(`${applicationUrl}/api/clusters/${clusterB.id}`, {
      headers: { 'X-XSRF-TOKEN': await csrf(scopedB.request) },
    });
    expect(viewerDelete.status()).toBe(403);
    expect((await adminContext.request.get(`${applicationUrl}/api/audit-logs`)).status()).toBe(200);
    await scopedA.close();
    await scopedB.close();
  });

  test.afterAll(async () => {
    if (!adminContext) return;
    const token = await csrf(adminContext.request);
    for (const clusterId of createdClusterIds) {
      await adminContext.request.delete(`${applicationUrl}/api/clusters/${clusterId}`, {
        headers: { 'X-XSRF-TOKEN': token },
      });
    }
    for (const bindingId of createdBindingIds) {
      await adminContext.request.delete(`${applicationUrl}/api/security/role-bindings/${bindingId}`, {
        headers: { 'X-XSRF-TOKEN': token },
      });
    }
    await adminContext.close();
  });
});
