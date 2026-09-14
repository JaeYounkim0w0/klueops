<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';

import { api, type RuntimeReadinessResponse } from '@/api/client';

const readiness = ref<RuntimeReadinessResponse | null>(null);
const { t } = useI18n();
const dismissed = ref(false);
const visible = computed(() => !dismissed.value && readiness.value && readiness.value.status !== 'READY');
const leadingChecks = computed(() => readiness.value?.checks
  .filter((check) => check.status !== 'READY')
  .slice(0, 2) ?? []);

onMounted(async () => {
  try {
    readiness.value = await api.getRuntimeReadiness();
  } catch {
    readiness.value = null;
  }
});
</script>

<template>
  <section v-if="visible" class="runtime-readiness-banner" :class="readiness?.status.toLowerCase()" aria-live="polite">
    <i :class="readiness?.status === 'BLOCKED' ? 'pi pi-ban' : 'pi pi-info-circle'"></i>
    <div>
      <strong>{{ readiness?.status === 'BLOCKED' ? t('readiness.blocked') : t('readiness.pilot') }}</strong>
      <span>{{ leadingChecks.map((check) => check.title).join(' · ') }}</span>
    </div>
    <RouterLink to="/settings/reliability">{{ t('readiness.details') }}</RouterLink>
    <button type="button" :aria-label="t('readiness.dismiss')" :title="t('common.close')" @click="dismissed = true">
      <i class="pi pi-times"></i>
    </button>
  </section>
</template>
