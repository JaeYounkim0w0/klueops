<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import {
  ApiError,
  api,
  type AiCalibrationSummaryResponse,
  type AiQualitySummaryResponse,
  type AiReleaseGateResponse,
  type AiTrustSnapshotResponse,
  type AnalysisBenchmarkResponse,
  type RegressionRunResponse
} from '@/api/client';
import { formatTimestamp } from '@/utils/time';

const { t } = useI18n();
const quality = ref<AiQualitySummaryResponse | null>(null);
const calibration = ref<AiCalibrationSummaryResponse | null>(null);
const benchmark = ref<AnalysisBenchmarkResponse | null>(null);
const regressions = ref<RegressionRunResponse[]>([]);
const gates = ref<AiReleaseGateResponse[]>([]);
const corpus = ref<AiTrustSnapshotResponse['corpus'] | null>(null);
const contractEvaluation = ref<AiTrustSnapshotResponse['contractEvaluation'] | null>(null);
const serverTrustState = ref<'TRUSTED' | 'NEEDS_EVIDENCE' | 'BLOCKED'>('NEEDS_EVIDENCE');
const loading = ref(true);
const refreshing = ref(false);
const error = ref('');

const trustState = computed(() => {
  return serverTrustState.value;
});

async function load(silent = false) {
  if (silent) refreshing.value = true;
  else loading.value = true;
  error.value = '';
  try {
    const snapshot = await api.getAiTrustSnapshot();
    serverTrustState.value = snapshot.state;
    quality.value = snapshot.quality;
    calibration.value = snapshot.calibration;
    benchmark.value = snapshot.latestBenchmark ?? null;
    regressions.value = snapshot.recentRegressions;
    gates.value = snapshot.recentReleaseGates;
    corpus.value = snapshot.corpus;
    contractEvaluation.value = snapshot.contractEvaluation;
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : t('trustCenter.loadFailed');
  } finally {
    loading.value = false;
    refreshing.value = false;
  }
}

function stateClass(state?: string) {
  if (state === 'TRUSTED' || state === 'PASSED') return 'low';
  if (state === 'BLOCKED' || state === 'FAILED') return 'critical';
  return 'warning';
}

onMounted(() => load());
</script>

<template>
  <section class="page-stack trust-center-page">
    <header class="page-header">
      <div><span class="page-eyebrow">AI TRUST CENTER</span><h1>{{ t('trustCenter.title') }}</h1><p>{{ t('trustCenter.description') }}</p></div>
      <button class="secondary-button" type="button" :disabled="refreshing" @click="load(true)"><i :class="refreshing ? 'pi pi-spin pi-spinner' : 'pi pi-refresh'"></i><span>{{ t('common.refresh') }}</span></button>
    </header>
    <div v-if="error" class="inline-feedback error"><i class="pi pi-exclamation-triangle"></i><span>{{ error }}</span></div>
    <div v-if="loading" class="empty-state"><i class="pi pi-spin pi-spinner"></i><span>{{ t('common.loading') }}</span></div>
    <template v-else>
      <section class="trust-hero-band">
        <div><span class="status-pill" :class="stateClass(trustState)">{{ trustState }}</span><h2>{{ t(`trustCenter.state.${trustState}`) }}</h2><p>{{ t('trustCenter.stateDescription') }}</p></div>
        <strong>{{ quality?.verifiedAccuracyRate ?? 0 }}<small>%</small></strong>
      </section>

      <section class="trust-metric-grid">
        <article><span>{{ t('trustCenter.verifiedAnalyses') }}</span><strong>{{ quality?.feedbackCount ?? 0 }}</strong><small>{{ calibration?.groundTruthCount ?? 0 }} ground truth</small></article>
        <article><span>{{ t('trustCenter.analysisSuccess') }}</span><strong>{{ quality?.successRate ?? 0 }}%</strong><small>{{ quality?.successfulAnalyses ?? 0 }}/{{ (quality?.successfulAnalyses ?? 0) + (quality?.failedAnalyses ?? 0) }}</small></article>
        <article><span>{{ t('trustCenter.commandSafety') }}</span><strong>{{ benchmark?.commandSafetyRate ?? 0 }}%</strong><small>{{ benchmark?.state || t('trustCenter.notMeasured') }}</small></article>
        <article><span>{{ t('trustCenter.dangerousSuggestions') }}</span><strong>{{ quality?.dangerousSuggestionCount ?? 0 }}</strong><small>{{ t('trustCenter.mustBeZero') }}</small></article>
      </section>

      <div class="trust-two-column">
        <section class="detail-panel">
          <div class="detail-section-header"><div><span>MODEL / PROMPT</span><h2>{{ t('trustCenter.calibration') }}</h2><p>{{ t('trustCenter.calibrationDescription') }}</p></div></div>
          <div v-if="!calibration?.profiles.length" class="embedded-empty-state">{{ t('trustCenter.noProfiles') }}</div>
          <div v-else class="trust-list">
            <article v-for="profile in calibration.profiles" :key="`${profile.model}:${profile.promptVersion}`">
              <div><strong>{{ profile.model }}</strong><small>{{ profile.promptVersion }} · {{ profile.feedbackCount }} samples</small></div>
              <span class="status-pill" :class="profile.dangerousSuggestionCount ? 'critical' : profile.verifiedAccuracyRate >= 80 ? 'low' : 'warning'">{{ profile.verifiedAccuracyRate }}%</span>
            </article>
          </div>
        </section>

        <section class="detail-panel">
          <div class="detail-section-header"><div><span>RELEASE EVIDENCE</span><h2>{{ t('trustCenter.releaseEvidence') }}</h2><p>{{ t('trustCenter.releaseDescription') }}</p></div></div>
          <div class="trust-list">
            <article v-if="benchmark"><div><strong>Benchmark · {{ benchmark.overallScore }}</strong><small>P95 {{ benchmark.p95LatencyMs }}ms · {{ formatTimestamp(benchmark.completedAt) }}</small></div><span class="status-pill" :class="stateClass(benchmark.state)">{{ benchmark.state }}</span></article>
            <article v-if="regressions[0]"><div><strong>Regression · {{ regressions[0].score }}</strong><small>{{ regressions[0].passedCases }}/{{ regressions[0].totalCases }} cases · {{ formatTimestamp(regressions[0].completedAt) }}</small></div><span class="status-pill" :class="stateClass(regressions[0].status)">{{ regressions[0].status }}</span></article>
            <article v-if="gates[0]"><div><strong>{{ gates[0].candidateVersion }}</strong><small>{{ gates[0].reasons[0] || t('trustCenter.thresholdsPassed') }}</small></div><span class="status-pill" :class="stateClass(gates[0].state)">{{ gates[0].state }}</span></article>
            <div v-if="!benchmark && !regressions.length && !gates.length" class="embedded-empty-state">{{ t('trustCenter.noReleaseEvidence') }}</div>
          </div>
        </section>
      </div>

      <section class="detail-panel trust-contract-panel">
        <div class="detail-section-header">
          <div>
            <span>FIXED CONTRACT CORPUS</span>
            <h2>{{ t('trustCenter.contractEvaluation') }}</h2>
            <p>{{ t('trustCenter.contractEvaluationDescription') }}</p>
          </div>
          <span class="status-pill" :class="stateClass(contractEvaluation?.state)">{{ contractEvaluation?.state || t('trustCenter.notMeasured') }}</span>
        </div>
        <div v-if="corpus && contractEvaluation" class="contract-evaluation-body">
          <div class="trust-metric-grid compact">
            <article><span>{{ t('trustCenter.fixedCases') }}</span><strong>{{ corpus.distinctCases }}</strong><small>{{ corpus.version }}</small></article>
            <article><span>{{ t('trustCenter.macroF1') }}</span><strong>{{ contractEvaluation.macroF1 }}%</strong><small>{{ contractEvaluation.sampleCount }} samples</small></article>
            <article><span>{{ t('trustCenter.abstentionAccuracy') }}</span><strong>{{ contractEvaluation.abstentionAccuracy }}%</strong><small>{{ corpus.abstentionCases }} abstention cases</small></article>
            <article><span>{{ t('trustCenter.generatedVariants') }}</span><strong>{{ corpus.generatedVariantsRemoved ? t('trustCenter.removed') : t('trustCenter.present') }}</strong><small>{{ corpus.categories.length }} categories</small></article>
          </div>
          <div class="contract-category-table" role="region" :aria-label="t('trustCenter.categoryMetrics')" tabindex="0">
            <table>
              <thead><tr><th>{{ t('trustCenter.category') }}</th><th>Precision</th><th>Recall</th><th>F1</th><th>TP / FP / FN</th></tr></thead>
              <tbody>
                <tr v-for="score in contractEvaluation.categories" :key="score.category">
                  <td><strong>{{ score.category }}</strong></td><td>{{ score.precision }}%</td><td>{{ score.recall }}%</td><td>{{ score.f1 }}%</td><td>{{ score.truePositive }} / {{ score.falsePositive }} / {{ score.falseNegative }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p class="contract-evaluation-note"><i class="pi pi-info-circle"></i>{{ t('trustCenter.contractEvaluationNote') }}</p>
        </div>
        <div v-else class="embedded-empty-state">{{ t('trustCenter.noContractEvaluation') }}</div>
      </section>
    </template>
  </section>
</template>
