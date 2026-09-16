import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { useTenancyStore } from '@/stores/tenancy';

describe('tenant and workspace context', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.unstubAllGlobals();
  });

  it('discards inaccessible saved scope and selects the first accessible workspace', async () => {
    const storage = new Map<string, string>([
      ['aiops.tenantId', 'removed-tenant'],
      ['aiops.workspaceId', 'removed-workspace'],
    ]);
    vi.stubGlobal('localStorage', {
      getItem: /** getItem 처리 결과를 조회해 반환한다. */ (key: string) => storage.get(key) ?? null,
      setItem: /** setItem 처리 대상의 상태를 갱신한다. */ (key: string, value: string) => storage.set(key, value),
      removeItem: /** removeItem 처리 대상과 관련 상태를 안전하게 정리한다. */ (key: string) => storage.delete(key),
    });
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url === '/api/tenants') return json([{ id: 'tenant-a', code: 'a', name: 'Tenant A', status: 'ACTIVE' }]);
      if (url === '/api/tenants/tenant-a/workspaces') return json([{ id: 'workspace-a', tenantId: 'tenant-a', code: 'ops', name: 'Operations', status: 'ACTIVE' }]);
      return json([], 404);
    }));

    const tenancy = useTenancyStore();
    await tenancy.load();

    expect(tenancy.currentTenantId).toBe('tenant-a');
    expect(tenancy.currentWorkspaceId).toBe('workspace-a');
    expect(storage.get('aiops.tenantId')).toBe('tenant-a');
    expect(storage.get('aiops.workspaceId')).toBe('workspace-a');
  });

  it('changes tenant and cascades to its first workspace', async () => {
    const storage = new Map<string, string>();
    vi.stubGlobal('localStorage', {
      getItem: /** getItem 처리 결과를 조회해 반환한다. */ (key: string) => storage.get(key) ?? null,
      setItem: /** setItem 처리 대상의 상태를 갱신한다. */ (key: string, value: string) => storage.set(key, value),
      removeItem: /** removeItem 처리 대상과 관련 상태를 안전하게 정리한다. */ (key: string) => storage.delete(key),
    });
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url === '/api/tenants') return json([
        { id: 'tenant-a', code: 'a', name: 'Tenant A', status: 'ACTIVE' },
        { id: 'tenant-b', code: 'b', name: 'Tenant B', status: 'ACTIVE' },
      ]);
      if (url.endsWith('/tenant-a/workspaces')) return json([{ id: 'workspace-a', tenantId: 'tenant-a', code: 'a', name: 'A', status: 'ACTIVE' }]);
      if (url.endsWith('/tenant-b/workspaces')) return json([{ id: 'workspace-b', tenantId: 'tenant-b', code: 'b', name: 'B', status: 'ACTIVE' }]);
      return json([], 404);
    }));

    const tenancy = useTenancyStore();
    await tenancy.load();
    await tenancy.selectTenant('tenant-b');

    expect(tenancy.currentTenantId).toBe('tenant-b');
    expect(tenancy.currentWorkspaceId).toBe('workspace-b');
  });
});

/** json 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}
