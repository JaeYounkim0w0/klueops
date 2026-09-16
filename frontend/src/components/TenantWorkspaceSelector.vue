<script setup lang="ts">
import { onMounted } from 'vue';
import { useI18n } from 'vue-i18n';

import { useTenancyStore } from '@/stores/tenancy';

const tenancy = useTenancyStore();
const { t } = useI18n();

onMounted(() => void tenancy.load());

/** changeTenant 처리 대상의 상태를 갱신한다. */
async function changeTenant(event: Event): Promise<void> {
  await tenancy.selectTenant((event.target as HTMLSelectElement).value);
}
</script>

<template>
  <section class="tenancy-selector" :aria-label="t('tenancy.context')">
    <label>
      <span>{{ t('tenancy.tenant') }}</span>
      <select :value="tenancy.currentTenantId" :disabled="tenancy.loading || !tenancy.tenants.length" @change="changeTenant">
        <option v-for="tenant in tenancy.tenants" :key="tenant.id" :value="tenant.id">{{ tenant.name }}</option>
      </select>
    </label>
    <label>
      <span>{{ t('tenancy.workspace') }}</span>
      <select :value="tenancy.currentWorkspaceId" :disabled="tenancy.loading || !tenancy.workspaces.length"
        @change="tenancy.selectWorkspace(($event.target as HTMLSelectElement).value)">
        <option v-for="workspace in tenancy.workspaces" :key="workspace.id" :value="workspace.id">{{ workspace.name }}</option>
      </select>
    </label>
    <p v-if="tenancy.error" role="alert" :title="tenancy.error">{{ t('tenancy.loadFailed') }}</p>
  </section>
</template>
