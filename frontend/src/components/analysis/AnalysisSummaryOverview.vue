<script setup lang="ts">
import { useI18n } from 'vue-i18n';

type ResultValue = Record<string, unknown>;
const props = defineProps<{
  result: ResultValue;
  diagnostics: ResultValue;
  incremental: ResultValue;
  localeName: string;
  mode: 'beginner' | 'expert';
}>();
defineEmits<{ modeChange: [mode: 'beginner' | 'expert'] }>();
const { t } = useI18n();

function text(value: unknown, fallback = '-') { return value == null || String(value).trim() === '' ? fallback : String(value); }
function number(value: unknown, fallback = '0') { const parsed = Number(value); return Number.isFinite(parsed) ? String(parsed) : fallback; }
function severityClass(value: unknown) {
  const normalized = text(value, 'INFO').toLowerCase();
  return ['critical', 'high', 'medium', 'low', 'info'].includes(normalized) ? normalized : 'info';
}
</script>

<template>
  <section class="analysis-summary-band">
    <div>
      <span class="status-pill" :class="severityClass(props.result.severity)">{{ text(props.result.severity, 'INFO') }}</span>
      <span class="status-pill info">Risk {{ number(props.result.riskScore) }}</span>
      <span class="status-pill info">{{ t('analysis.resultLanguage', { language: props.localeName }) }}</span>
      <span v-if="props.diagnostics.totalLatencyMs" class="status-pill info">{{ number(props.diagnostics.totalLatencyMs) }}ms</span>
      <span v-if="props.diagnostics.totalContextChars" class="status-pill info">context {{ number(props.diagnostics.totalContextChars) }}</span>
      <span v-if="number(props.incremental.reusedSections) !== '0'" class="status-pill healthy">reused {{ number(props.incremental.reusedSections) }}</span>
    </div>
    <p>{{ text(props.result.summary) }}</p>
    <small v-if="props.diagnostics.mode">
      {{ text(props.diagnostics.mode) }} · sections {{ number(props.diagnostics.successfulSections) }}/{{ number(props.diagnostics.sectionCount) }}
      <template v-if="props.diagnostics.failureReason"> · {{ text(props.diagnostics.failureReason) }}</template>
    </small>
  </section>

  <section class="analysis-persona-panel" :class="props.mode" aria-label="분석 결과 보기 방식">
    <div>
      <span class="label">{{ props.mode === 'beginner' ? 'Beginner View' : 'Expert View' }}</span>
      <h3>{{ props.mode === 'beginner' ? '설명과 순서 중심으로 보기' : '근거와 실행 중심으로 보기' }}</h3>
      <p>{{ props.mode === 'beginner' ? '문제 의미, 왜 중요한지, 어떤 순서로 확인해야 하는지를 먼저 보여줍니다.' : '고신호 로그, 원인 그룹, 검증 명령, Evidence를 앞세워 빠르게 판단할 수 있게 압축합니다.' }}</p>
    </div>
    <div class="analysis-persona-actions">
      <span class="analysis-chip">{{ props.mode === 'beginner' ? 'guided' : 'dense' }}</span>
      <button class="icon-button" type="button" :title="props.mode === 'beginner' ? '숙련자 보기' : '초보자 보기'" @click="$emit('modeChange', props.mode === 'beginner' ? 'expert' : 'beginner')">
        <i :class="props.mode === 'beginner' ? 'pi pi-terminal' : 'pi pi-compass'"></i>
      </button>
    </div>
  </section>
</template>
