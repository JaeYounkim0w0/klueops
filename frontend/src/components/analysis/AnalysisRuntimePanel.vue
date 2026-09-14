<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { arrayValue, type AnalysisResult } from '@/utils/analysisResult';
import { displayText } from '@/utils/text';

const props = defineProps<{
  diagnostics: AnalysisResult;
  incremental: AnalysisResult;
}>();
const { t } = useI18n();

function text(value: unknown, fallback = '-') {
  return displayText(value, fallback);
}

function number(value: unknown, fallback = '0') {
  return typeof value === 'number' && Number.isFinite(value) ? String(value) : displayText(value, fallback);
}

const collection = computed(() => {
  const value = props.diagnostics.collection;
  return value && typeof value === 'object' && !Array.isArray(value) ? value as AnalysisResult : {};
});

const collectionStages = computed(() => arrayValue(collection.value.stages));

function collectionStatusClass(status: unknown) {
  const normalized = text(status, '').toUpperCase();
  if (normalized === 'COMPLETE' || normalized === 'SUCCEEDED') return 'is-success';
  if (normalized === 'FAILED') return 'is-danger';
  return 'is-warning';
}
</script>

<template>
  <article class="analysis-detail-section" aria-label="분석 실행 정보">
    <h3>Analysis Runtime</h3>
    <p>
      {{ text(props.diagnostics.mode, 'runtime metadata 없음') }}
      · total {{ number(props.diagnostics.totalLatencyMs) }}ms
      · context {{ number(props.diagnostics.totalContextChars) }} chars
    </p>
    <p v-if="props.incremental.summary">{{ text(props.incremental.summary) }}</p>
    <ul v-if="arrayValue(props.diagnostics.sections).length">
      <li v-for="section in arrayValue(props.diagnostics.sections)" :key="`${text(section.name)}-${text(section.status)}`">
        <strong>{{ text(section.name) }} · {{ text(section.status) }}</strong>
        <span>{{ number(section.latencyMs) }}ms · context {{ number(section.contextChars) }}<template v-if="section.cacheHit"> · cache hit</template></span>
        <small v-if="section.error">{{ text(section.error) }}</small>
      </li>
    </ul>
    <section v-if="collection.status" class="analysis-collection-runtime" aria-label="Kubernetes 정보 수집 상태">
      <header>
        <div>
          <strong>{{ t('analysisRuntime.collectionTitle') }}</strong>
          <small>{{ t('analysisRuntime.collectionCounts', {
            success: number(collection.successfulSources),
            failed: number(collection.failedSources),
            skipped: number(collection.skippedSources)
          }) }}</small>
        </div>
        <span class="analysis-runtime-status" :class="collectionStatusClass(collection.status)">{{ text(collection.status) }}</span>
      </header>
      <p v-if="collection.status && text(collection.status).toUpperCase() !== 'COMPLETE'">{{ t('analysisRuntime.partialMessage') }}</p>
      <details v-if="collectionStages.length">
        <summary>{{ t('analysisRuntime.stageDetails', { count: collectionStages.length }) }}</summary>
        <ul>
          <li v-for="stage in collectionStages" :key="text(stage.source)">
            <strong>{{ text(stage.source) }} · {{ text(stage.status) }}</strong>
            <span>{{ number(stage.latencyMs) }}ms · {{ number(stage.itemCount) }} items</span>
            <small v-if="stage.detail">{{ text(stage.detail) }}</small>
          </li>
        </ul>
      </details>
    </section>
  </article>
</template>
