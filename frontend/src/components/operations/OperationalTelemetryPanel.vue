<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ApiError, api, type OperationalTelemetryResponse } from '@/api/client';
import { useI18n } from 'vue-i18n';

const { t } = useI18n();

const telemetry = ref<OperationalTelemetryResponse | null>(null);
const loading = ref(true);
const error = ref('');

const failureRate = computed(() => {
  if (!telemetry.value?.requestCount) return 0;
  return Math.round(telemetry.value.failureCount * 1000 / telemetry.value.requestCount) / 10;
});

async function load() {
  loading.value = true;
  error.value = '';
  try {
    telemetry.value = await api.getOperationalTelemetry();
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : t('operationalTelemetry.loadFailed');
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <section class="reliability-band operational-telemetry-panel">
    <header><div><span class="page-eyebrow">SERVICE QUALITY</span><h2>{{ t('operationalTelemetry.title') }}</h2><p>{{ t('operationalTelemetry.description') }}</p></div><button class="icon-button" type="button" :title="t('common.refresh')" :aria-label="t('common.refresh')" @click="load"><i class="pi pi-refresh"></i></button></header>
    <div v-if="error" class="inline-feedback error"><i class="pi pi-exclamation-triangle"></i><span>{{ error }}</span></div>
    <div v-if="loading" class="embedded-empty-state"><i class="pi pi-spin pi-spinner"></i><span>{{ t('operationalTelemetry.loading') }}</span></div>
    <template v-else-if="telemetry">
      <dl class="telemetry-summary"><div><dt>{{ t('operationalTelemetry.requests') }}</dt><dd>{{ telemetry.requestCount }}</dd></div><div><dt>{{ t('operationalTelemetry.failures') }}</dt><dd>{{ telemetry.failureCount }}</dd></div><div><dt>{{ t('operationalTelemetry.failureRate') }}</dt><dd>{{ failureRate }}%</dd></div><div><dt>{{ t('operationalTelemetry.retention') }}</dt><dd>{{ t('operationalTelemetry.currentInstance') }}</dd></div></dl>
      <div v-if="telemetry.series.length" class="telemetry-table" role="table" :aria-label="t('operationalTelemetry.tableLabel')">
        <div class="telemetry-table-head" role="row"><span>{{ t('operationalTelemetry.operation') }}</span><span>{{ t('operationalTelemetry.outcome') }}</span><span>{{ t('operationalTelemetry.count') }}</span><span>{{ t('operationalTelemetry.average') }}</span><span>P95</span><span>{{ t('operationalTelemetry.maximum') }}</span></div>
        <div v-for="series in telemetry.series" :key="`${series.operation}:${series.outcome}`" role="row"><strong>{{ series.operation }}</strong><span class="status-pill" :class="series.outcome === 'success' ? 'healthy' : 'critical'">{{ series.outcome }}</span><span>{{ series.count }}</span><span>{{ series.averageMs }}ms</span><span>{{ series.p95Ms }}ms</span><span>{{ series.maxMs }}ms</span></div>
      </div>
      <div v-else class="embedded-empty-state"><i class="pi pi-chart-line"></i><span>{{ t('operationalTelemetry.empty') }}</span></div>
    </template>
  </section>
</template>
