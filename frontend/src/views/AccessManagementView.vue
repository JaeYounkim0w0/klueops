<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';

import { ApiError, requestJson } from '@/api/http';
import { api, type TenantResponse, type WorkspaceResponse } from '@/api/client';
import { useAuthStore } from '@/stores/auth';

interface UserRow { id: string; username: string; displayName: string; email?: string; active: boolean; lastLoginAt: string }
interface BindingRow { id: string; principalType: string; principalKey: string; role: string; scopeType: string; tenantId?: string; workspaceId?: string; clusterId?: string; namespace?: string }
interface ClusterOption { id: string; name: string }

const { t } = useI18n();
const auth = useAuthStore();
const users = ref<UserRow[]>([]);
const bindings = ref<BindingRow[]>([]);
const clusters = ref<ClusterOption[]>([]);
const tenants = ref<TenantResponse[]>([]);
const workspaces = ref<WorkspaceResponse[]>([]);
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const feedback = ref('');
const form = reactive({
  principalType: 'USER' as 'USER' | 'GROUP',
  principalKey: '',
  role: 'VIEWER' as 'PLATFORM_ADMIN' | 'CLUSTER_ADMIN' | 'OPERATOR' | 'VIEWER',
  scopeType: 'PLATFORM' as 'PLATFORM' | 'TENANT' | 'WORKSPACE' | 'CLUSTER' | 'NAMESPACE',
  tenantId: '',
  workspaceId: '',
  clusterId: '',
  namespace: '',
});

const canSave = computed(() => Boolean(
  form.principalKey
  && (form.scopeType !== 'TENANT' || form.tenantId)
  && (form.scopeType !== 'WORKSPACE' || (form.tenantId && form.workspaceId))
  && (!['CLUSTER', 'NAMESPACE'].includes(form.scopeType) || form.clusterId)
  && (form.scopeType !== 'NAMESPACE' || form.namespace.trim()),
));

async function load(): Promise<void> {
  loading.value = true;
  error.value = '';
  try {
    [users.value, bindings.value, clusters.value, tenants.value] = await Promise.all([
      requestJson<UserRow[]>('/api/security/users'),
      requestJson<BindingRow[]>('/api/security/role-bindings'),
      requestJson<ClusterOption[]>('/api/clusters'),
      api.listTenants(),
    ]);
    if (!form.principalKey && users.value.length) form.principalKey = users.value[0].id;
    if (!form.clusterId && clusters.value.length) form.clusterId = clusters.value[0].id;
    if (!form.tenantId && tenants.value.length) form.tenantId = tenants.value[0].id;
    await loadBindingWorkspaces();
  } catch (cause) {
    error.value = message(cause, t('auth.accessLoadFailed'));
  } finally {
    loading.value = false;
  }
}

async function toggleUser(user: UserRow): Promise<void> {
  error.value = '';
  feedback.value = '';
  try {
    const updated = await requestJson<UserRow>(`/api/security/users/${user.id}`, {
      method: 'PATCH',
      body: JSON.stringify({ active: !user.active }),
    });
    users.value = users.value.map((item) => item.id === updated.id ? updated : item);
    feedback.value = t('auth.userStateUpdated', { name: user.displayName });
  } catch (cause) {
    error.value = message(cause, t('auth.userUpdateFailed'));
  }
}

async function createBinding(): Promise<void> {
  if (!canSave.value) return;
  saving.value = true;
  error.value = '';
  feedback.value = '';
  try {
    const created = await requestJson<BindingRow>('/api/security/role-bindings', {
      method: 'POST',
      body: JSON.stringify({
        principalType: form.principalType,
        principalKey: form.principalKey.trim(),
        role: form.role,
        scopeType: form.scopeType,
        tenantId: ['TENANT', 'WORKSPACE'].includes(form.scopeType) ? form.tenantId : null,
        workspaceId: form.scopeType === 'WORKSPACE' ? form.workspaceId : null,
        clusterId: ['CLUSTER', 'NAMESPACE'].includes(form.scopeType) ? form.clusterId : null,
        namespace: form.scopeType === 'NAMESPACE' ? form.namespace.trim() : null,
      }),
    });
    if (!bindings.value.some((binding) => binding.id === created.id)) bindings.value.unshift(created);
    feedback.value = t('auth.bindingCreated');
  } catch (cause) {
    error.value = message(cause, t('auth.bindingCreateFailed'));
  } finally {
    saving.value = false;
  }
}

async function deleteBinding(binding: BindingRow): Promise<void> {
  if (!window.confirm(t('auth.bindingDeleteConfirm'))) return;
  error.value = '';
  feedback.value = '';
  try {
    await requestJson<void>(`/api/security/role-bindings/${binding.id}`, { method: 'DELETE' });
    bindings.value = bindings.value.filter((item) => item.id !== binding.id);
    feedback.value = t('auth.bindingDeleted');
  } catch (cause) {
    error.value = message(cause, t('auth.bindingDeleteFailed'));
  }
}

function principalLabel(binding: BindingRow): string {
  if (binding.principalType === 'GROUP') return binding.principalKey;
  const user = users.value.find((item) => item.id === binding.principalKey);
  return user ? `${user.displayName} (${user.username})` : binding.principalKey;
}

function scopeLabel(binding: BindingRow): string {
  const tenant = tenants.value.find((item) => item.id === binding.tenantId)?.name ?? binding.tenantId;
  const workspace = workspaces.value.find((item) => item.id === binding.workspaceId)?.name ?? binding.workspaceId;
  const cluster = clusters.value.find((item) => item.id === binding.clusterId)?.name ?? binding.clusterId;
  return [binding.scopeType, tenant, workspace, cluster, binding.namespace].filter(Boolean).join(' / ');
}

async function loadBindingWorkspaces(): Promise<void> {
  workspaces.value = form.tenantId ? await api.listWorkspaces(form.tenantId) : [];
  if (!workspaces.value.some((item) => item.id === form.workspaceId)) {
    form.workspaceId = workspaces.value[0]?.id ?? '';
  }
}

function message(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback;
}

onMounted(load);
</script>

<template>
  <section class="page access-page">
    <header class="page-header">
      <div>
        <span class="section-eyebrow">IDENTITY & ACCESS</span>
        <h1>{{ t('auth.accessTitle') }}</h1>
        <p>{{ t('auth.accessDescription') }}</p>
      </div>
      <button type="button" class="secondary-button" :disabled="loading" @click="load">
        <i class="pi pi-refresh"></i>{{ t('common.refresh') }}
      </button>
    </header>

    <p v-if="error" class="inline-feedback error" role="alert">{{ error }}</p>
    <p v-if="feedback" class="inline-feedback success" role="status">{{ feedback }}</p>

    <section class="access-band" aria-labelledby="users-title">
      <header><div><h2 id="users-title">{{ t('auth.users') }}</h2><p>{{ t('auth.usersDescription') }}</p></div><strong>{{ users.length }}</strong></header>
      <div v-if="loading" class="operations-empty-state">{{ t('common.loading') }}</div>
      <div v-else class="access-table">
        <article v-for="user in users" :key="user.id">
          <span class="identity-avatar" aria-hidden="true">{{ user.displayName.slice(0, 1).toUpperCase() }}</span>
          <div><strong>{{ user.displayName }}</strong><small>{{ user.username }} · {{ user.email || '-' }}</small></div>
          <span class="status-pill" :class="user.active ? 'ready' : 'blocked'">{{ user.active ? t('auth.active') : t('auth.disabled') }}</span>
          <button type="button" class="secondary-button compact" :disabled="user.id === auth.user?.id" :title="user.id === auth.user?.id ? t('auth.selfDisableBlocked') : undefined" @click="toggleUser(user)">{{ user.active ? t('auth.disable') : t('auth.enable') }}</button>
        </article>
      </div>
    </section>

    <section class="access-band" aria-labelledby="create-binding-title">
      <header><div><h2 id="create-binding-title">{{ t('auth.assignRole') }}</h2><p>{{ t('auth.assignRoleDescription') }}</p></div></header>
      <form class="access-binding-form" @submit.prevent="createBinding">
        <label><span>{{ t('auth.principalType') }}</span><select v-model="form.principalType" @change="form.principalKey = ''"><option value="USER">{{ t('auth.user') }}</option><option value="GROUP">{{ t('auth.group') }}</option></select></label>
        <label v-if="form.principalType === 'USER'"><span>{{ t('auth.principal') }}</span><select v-model="form.principalKey"><option value="" disabled>{{ t('auth.selectUser') }}</option><option v-for="user in users" :key="user.id" :value="user.id">{{ user.displayName }} ({{ user.username }})</option></select></label>
        <label v-else><span>{{ t('auth.groupName') }}</span><input v-model="form.principalKey" type="text" autocomplete="off" :placeholder="t('auth.groupPlaceholder')" /></label>
        <label><span>{{ t('auth.role') }}</span><select v-model="form.role"><option value="VIEWER">Viewer</option><option value="OPERATOR">Operator</option><option value="CLUSTER_ADMIN">Cluster Admin</option><option value="PLATFORM_ADMIN">Platform Admin</option></select></label>
        <label><span>{{ t('auth.scope') }}</span><select v-model="form.scopeType"><option value="PLATFORM">{{ t('auth.platformScope') }}</option><option value="TENANT">Tenant</option><option value="WORKSPACE">Workspace</option><option value="CLUSTER">{{ t('auth.clusterScope') }}</option><option value="NAMESPACE">{{ t('auth.namespaceScope') }}</option></select></label>
        <label v-if="['TENANT', 'WORKSPACE'].includes(form.scopeType)"><span>{{ t('tenancy.tenant') }}</span><select v-model="form.tenantId" @change="loadBindingWorkspaces"><option value="" disabled>{{ t('tenancy.tenant') }}</option><option v-for="tenant in tenants" :key="tenant.id" :value="tenant.id">{{ tenant.name }}</option></select></label>
        <label v-if="form.scopeType === 'WORKSPACE'"><span>{{ t('tenancy.workspace') }}</span><select v-model="form.workspaceId"><option value="" disabled>{{ t('tenancy.workspace') }}</option><option v-for="workspace in workspaces" :key="workspace.id" :value="workspace.id">{{ workspace.name }}</option></select></label>
        <label v-if="['CLUSTER', 'NAMESPACE'].includes(form.scopeType)"><span>{{ t('auth.cluster') }}</span><select v-model="form.clusterId"><option value="" disabled>{{ t('auth.selectCluster') }}</option><option v-for="cluster in clusters" :key="cluster.id" :value="cluster.id">{{ cluster.name }}</option></select></label>
        <label v-if="form.scopeType === 'NAMESPACE'"><span>Namespace</span><input v-model="form.namespace" type="text" autocomplete="off" placeholder="default" /></label>
        <button type="submit" class="primary-button" :disabled="!canSave || saving"><i class="pi pi-plus"></i>{{ saving ? t('common.saving') : t('auth.assign') }}</button>
      </form>
    </section>

    <section class="access-band" aria-labelledby="bindings-title">
      <header><div><h2 id="bindings-title">{{ t('auth.bindings') }}</h2><p>{{ t('auth.bindingsDescription') }}</p></div><strong>{{ bindings.length }}</strong></header>
      <div class="access-binding-list">
        <article v-for="binding in bindings" :key="binding.id">
          <div><strong>{{ binding.role }}</strong><small>{{ binding.principalType }} · {{ principalLabel(binding) }}</small></div>
          <code>{{ scopeLabel(binding) }}</code>
          <button type="button" class="icon-button danger" :title="t('auth.deleteBinding')" :aria-label="t('auth.deleteBinding')" @click="deleteBinding(binding)"><i class="pi pi-trash"></i></button>
        </article>
        <p v-if="!bindings.length && !loading" class="operations-empty-state compact">{{ t('auth.noBindings') }}</p>
      </div>
    </section>
  </section>
</template>
