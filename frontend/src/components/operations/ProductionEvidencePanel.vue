<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ApiError, api, type ProductionEvidenceRunResponse } from '@/api/client';
import { formatTimestamp } from '@/utils/time';
import { useI18n } from 'vue-i18n';

const { t } = useI18n();

const runs = ref<ProductionEvidenceRunResponse[]>([]);
const selected = ref<ProductionEvidenceRunResponse | null>(null);
const state = ref('ALL');
const loading = ref(true);
const error = ref('');

const visibleRuns = computed(() => state.value === 'ALL'
  ? runs.value
  : runs.value.filter((run) => run.state === state.value));

async function load() {
  loading.value = true;
  error.value = '';
  try {
    runs.value = await api.listProductionEvidenceRuns();
    if (selected.value) selected.value = runs.value.find((run) => run.id === selected.value?.id) ?? null;
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : t('commercialEvidence.loadFailed');
  } finally {
    loading.value = false;
  }
}

function stateClass(value: string) {
  if (value === 'PASSED') return 'healthy';
  if (value === 'FAILED' || value === 'BLOCKED') return 'critical';
  return 'warning';
}

onMounted(load);
</script>

<template>
  <section class="reliability-band production-evidence-panel">
    <header>
      <div><span class="page-eyebrow">COMMERCIAL EVIDENCE</span><h2>{{ t('commercialEvidence.title') }}</h2><p>{{ t('commercialEvidence.description') }}</p></div>
      <div class="panel-toolbar">
        <label><span class="sr-only">{{ t('commercialEvidence.stateFilter') }}</span><select v-model="state"><option value="ALL">{{ t('commercialEvidence.allStates') }}</option><option>PASSED</option><option>FAILED</option><option>BLOCKED</option><option>EXPIRED</option></select></label>
        <button class="icon-button" type="button" :title="t('commercialEvidence.refresh')" :aria-label="t('commercialEvidence.refresh')" @click="load"><i class="pi pi-refresh"></i></button>
      </div>
    </header>
    <div v-if="error" class="inline-feedback error"><i class="pi pi-exclamation-triangle"></i><span>{{ error }}</span></div>
    <div v-if="loading" class="embedded-empty-state"><i class="pi pi-spin pi-spinner"></i><span>{{ t('commercialEvidence.loading') }}</span></div>
    <div v-else-if="!visibleRuns.length" class="embedded-empty-state"><i class="pi pi-file-check"></i><span>{{ t('commercialEvidence.empty') }}</span></div>
    <div v-else class="evidence-run-list">
      <button v-for="run in visibleRuns" :key="run.id" type="button" :class="{ selected: selected?.id === run.id }" @click="selected = run">
        <span class="status-pill" :class="stateClass(run.state)">{{ run.state }}</span>
        <span><strong>{{ run.releaseName }}</strong><small>{{ run.environment }} · {{ formatTimestamp(run.completedAt || run.startedAt) }}</small></span>
        <b>{{ run.checks.filter((check) => check.state === 'PASSED').length }}/{{ run.checks.length }}</b>
      </button>
    </div>
    <section v-if="selected" class="evidence-check-detail" aria-live="polite">
      <header><div><span class="page-eyebrow">CHECK RESULTS</span><h3>{{ selected.releaseName }}</h3></div><button class="icon-button" type="button" :title="t('common.close')" :aria-label="t('common.close')" @click="selected = null"><i class="pi pi-times"></i></button></header>
      <article v-for="check in selected.checks" :key="check.id">
        <span class="status-pill" :class="stateClass(check.state)">{{ check.state }}</span>
        <div><strong>{{ check.title }}</strong><small>{{ check.category }} · {{ check.code }} · {{ check.durationMs }}ms</small><p>{{ check.detail || check.observedValue || t('commercialEvidence.noDetail') }}</p><p v-if="check.action"><b>{{ t('commercialEvidence.nextAction') }}</b> {{ check.action }}</p><small v-if="check.artifacts.length">{{ t('commercialEvidence.artifacts', { count: check.artifacts.length }) }}</small></div>
      </article>
    </section>
  </section>
</template>
