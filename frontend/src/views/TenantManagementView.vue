<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';

import { api } from '@/api/client';
import { useAuthStore } from '@/stores/auth';
import { useTenancyStore } from '@/stores/tenancy';

const { t } = useI18n();
const auth = useAuthStore();
const tenancy = useTenancyStore();
const saving = ref<'tenant' | 'workspace' | ''>('');
const error = ref('');
const feedback = ref('');
const tenantForm = reactive({ code: '', name: '', description: '' });
const workspaceForm = reactive({ code: '', name: '', description: '' });
const canManage = computed(() => auth.hasCapability('platform:admin'));

onMounted(() => void tenancy.load());

/** createTenant 처리에 필요한 데이터를 생성하거나 저장한다. */
async function createTenant(): Promise<void> {
  if (!canManage.value || !tenantForm.code.trim() || !tenantForm.name.trim()) return;
  saving.value = 'tenant';
  resetMessages();
  try {
    const created = await api.createTenant({
      code: tenantForm.code.trim(), name: tenantForm.name.trim(), description: optional(tenantForm.description),
    });
    Object.assign(tenantForm, { code: '', name: '', description: '' });
    await tenancy.refresh();
    await tenancy.selectTenant(created.id);
    feedback.value = t('tenancy.tenantCreated', { name: created.name });
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : t('tenancy.createFailed');
  } finally {
    saving.value = '';
  }
}

/** createWorkspace 처리에 필요한 데이터를 생성하거나 저장한다. */
async function createWorkspace(): Promise<void> {
  if (!canManage.value || !tenancy.currentTenantId || !workspaceForm.code.trim() || !workspaceForm.name.trim()) return;
  saving.value = 'workspace';
  resetMessages();
  try {
    const created = await api.createWorkspace(tenancy.currentTenantId, {
      code: workspaceForm.code.trim(), name: workspaceForm.name.trim(), description: optional(workspaceForm.description),
    });
    Object.assign(workspaceForm, { code: '', name: '', description: '' });
    await tenancy.refresh();
    await tenancy.selectTenant(created.tenantId);
    tenancy.selectWorkspace(created.id);
    feedback.value = t('tenancy.workspaceCreated', { name: created.name });
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : t('tenancy.createFailed');
  } finally {
    saving.value = '';
  }
}

/** optional 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function optional(value: string): string | undefined {
  return value.trim() || undefined;
}

/** resetMessages 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function resetMessages(): void {
  error.value = '';
  feedback.value = '';
}
</script>

<template>
  <section class="page tenancy-management-page">
    <header class="page-header">
      <div>
        <span class="section-eyebrow">TENANT & WORKSPACE</span>
        <h1>{{ t('tenancy.manage') }}</h1>
        <p>{{ t('tenancy.manageDescription') }}</p>
      </div>
      <button type="button" class="secondary-button" :disabled="tenancy.loading" @click="tenancy.refresh">
        <i class="pi pi-refresh"></i>{{ t('common.refresh') }}
      </button>
    </header>

    <p v-if="error" class="inline-feedback error" role="alert">{{ error }}</p>
    <p v-if="feedback" class="inline-feedback success" role="status">{{ feedback }}</p>

    <section class="tenancy-overview-band">
      <header>
        <div><h2>{{ t('tenancy.currentScope') }}</h2><p>{{ t('tenancy.currentScopeDescription') }}</p></div>
        <strong>{{ tenancy.tenants.length }} / {{ tenancy.workspaces.length }}</strong>
      </header>
      <div class="tenancy-scope-summary">
        <div><span>{{ t('tenancy.tenant') }}</span><strong>{{ tenancy.currentTenant?.name ?? '-' }}</strong><small>{{ tenancy.currentTenant?.code }}</small></div>
        <i class="pi pi-angle-right" aria-hidden="true"></i>
        <div><span>{{ t('tenancy.workspace') }}</span><strong>{{ tenancy.currentWorkspace?.name ?? '-' }}</strong><small>{{ tenancy.currentWorkspace?.code }}</small></div>
      </div>
    </section>

    <div class="tenancy-management-grid">
      <section class="tenancy-form-band ui-surface-card">
        <header><div><h2>{{ t('tenancy.createTenant') }}</h2><p>{{ t('tenancy.createTenantDescription') }}</p></div></header>
        <form class="ui-form-grid" @submit.prevent="createTenant">
          <label class="ui-form-field"><span>{{ t('tenancy.code') }}</span><input v-model="tenantForm.code" pattern="[A-Za-z0-9-]+" placeholder="customer-a" required /></label>
          <label class="ui-form-field"><span>{{ t('tenancy.name') }}</span><input v-model="tenantForm.name" placeholder="Customer A" required /></label>
          <label class="wide ui-form-field ui-form-field-wide"><span>{{ t('tenancy.description') }}</span><textarea v-model="tenantForm.description" rows="3" /></label>
          <button type="submit" class="primary-button" :disabled="!canManage || saving === 'tenant'">
            <i :class="saving === 'tenant' ? 'pi pi-spin pi-spinner' : 'pi pi-plus'"></i>{{ t('tenancy.createTenant') }}
          </button>
        </form>
      </section>

      <section class="tenancy-form-band ui-surface-card">
        <header><div><h2>{{ t('tenancy.createWorkspace') }}</h2><p>{{ t('tenancy.createWorkspaceDescription') }}</p></div></header>
        <form class="ui-form-grid" @submit.prevent="createWorkspace">
          <label class="ui-form-field"><span>{{ t('tenancy.code') }}</span><input v-model="workspaceForm.code" pattern="[A-Za-z0-9-]+" placeholder="platform-ops" required /></label>
          <label class="ui-form-field"><span>{{ t('tenancy.name') }}</span><input v-model="workspaceForm.name" placeholder="Platform Operations" required /></label>
          <label class="wide ui-form-field ui-form-field-wide"><span>{{ t('tenancy.description') }}</span><textarea v-model="workspaceForm.description" rows="3" /></label>
          <button type="submit" class="primary-button" :disabled="!canManage || !tenancy.currentTenantId || saving === 'workspace'">
            <i :class="saving === 'workspace' ? 'pi pi-spin pi-spinner' : 'pi pi-folder-plus'"></i>{{ t('tenancy.createWorkspace') }}
          </button>
        </form>
      </section>
    </div>
  </section>
</template>
