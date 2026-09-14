import { computed, ref } from 'vue';
import { defineStore } from 'pinia';

import { api, type TenantResponse, type WorkspaceResponse } from '@/api/client';

const TENANT_KEY = 'aiops.tenantId';
const WORKSPACE_KEY = 'aiops.workspaceId';

export const useTenancyStore = defineStore('tenancy', () => {
  const tenants = ref<TenantResponse[]>([]);
  const workspaces = ref<WorkspaceResponse[]>([]);
  const currentTenantId = ref('');
  const currentWorkspaceId = ref('');
  const loading = ref(false);
  const loaded = ref(false);
  const error = ref('');

  const currentTenant = computed(() => tenants.value.find((item) => item.id === currentTenantId.value) ?? null);
  const currentWorkspace = computed(() => workspaces.value.find((item) => item.id === currentWorkspaceId.value) ?? null);

  async function load(force = false): Promise<void> {
    if (loaded.value && !force) return;
    loading.value = true;
    error.value = '';
    try {
      tenants.value = (await api.listTenants()).filter((item) => item.status === 'ACTIVE');
      const savedTenantId = storage()?.getItem(TENANT_KEY) ?? '';
      currentTenantId.value = tenants.value.some((item) => item.id === savedTenantId)
        ? savedTenantId
        : tenants.value[0]?.id ?? '';
      persist(TENANT_KEY, currentTenantId.value);
      await loadWorkspaces(storage()?.getItem(WORKSPACE_KEY) ?? '');
      loaded.value = true;
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : 'Tenant context is unavailable.';
      throw cause;
    } finally {
      loading.value = false;
    }
  }

  async function selectTenant(tenantId: string): Promise<void> {
    if (!tenants.value.some((item) => item.id === tenantId)) return;
    currentTenantId.value = tenantId;
    persist(TENANT_KEY, tenantId);
    await loadWorkspaces('');
  }

  function selectWorkspace(workspaceId: string): void {
    if (!workspaces.value.some((item) => item.id === workspaceId)) return;
    currentWorkspaceId.value = workspaceId;
    persist(WORKSPACE_KEY, workspaceId);
  }

  async function refresh(): Promise<void> {
    loaded.value = false;
    await load(true);
  }

  async function loadWorkspaces(preferredId: string): Promise<void> {
    workspaces.value = currentTenantId.value
      ? (await api.listWorkspaces(currentTenantId.value)).filter((item) => item.status === 'ACTIVE')
      : [];
    currentWorkspaceId.value = workspaces.value.some((item) => item.id === preferredId)
      ? preferredId
      : workspaces.value[0]?.id ?? '';
    persist(WORKSPACE_KEY, currentWorkspaceId.value);
  }

  function persist(key: string, value: string): void {
    const target = storage();
    if (!target) return;
    if (value) target.setItem(key, value);
    else target.removeItem(key);
  }

  function storage(): Storage | null {
    return typeof localStorage === 'undefined' ? null : localStorage;
  }

  return {
    tenants, workspaces, currentTenantId, currentWorkspaceId, currentTenant, currentWorkspace,
    loading, loaded, error, load, refresh, selectTenant, selectWorkspace,
  };
});
