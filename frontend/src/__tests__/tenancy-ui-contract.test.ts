import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

describe('tenant workspace UI contract', () => {
  it('keeps the global selector in the application shell with accessible labels', () => {
    const app = readFileSync(new URL('../App.vue', import.meta.url), 'utf8');
    const selector = readFileSync(new URL('../components/TenantWorkspaceSelector.vue', import.meta.url), 'utf8');

    expect(app).toContain('TenantWorkspaceSelector');
    expect(selector).toContain("t('tenancy.tenant')");
    expect(selector).toContain("t('tenancy.workspace')");
  });

  it('requires cluster registration to carry the selected placement', () => {
    const clusters = readFileSync(new URL('../views/ClustersView.vue', import.meta.url), 'utf8');

    expect(clusters).toContain('tenantId: tenancy.currentTenantId');
    expect(clusters).toContain('workspaceId: tenancy.currentWorkspaceId');
  });

  it('provides a platform administration route for creating tenants and workspaces', () => {
    const router = readFileSync(new URL('../router/index.ts', import.meta.url), 'utf8');
    const view = readFileSync(new URL('../views/TenantManagementView.vue', import.meta.url), 'utf8');

    expect(router).toContain("path: '/settings/tenancy'");
    expect(view).toContain('api.createTenant');
    expect(view).toContain('api.createWorkspace');
    expect(view).toContain("auth.hasCapability('platform:admin')");
  });
});
