<script setup lang="ts">
import { arrayValue, type AnalysisResult } from '@/utils/analysisResult';
import { displayText } from '@/utils/text';

const props = defineProps<{
  intelligence: AnalysisResult;
  beginner: boolean;
  severityClass: (value: unknown) => Record<string, boolean>;
}>();

const emit = defineEmits<{ copy: [command: string] }>();

function text(value: unknown, fallback = '') {
  return displayText(value, fallback);
}

function number(value: unknown, fallback = '0') {
  return value === null || value === undefined || value === '' ? fallback : String(value);
}
</script>

<template>
  <section
    v-if="arrayValue(props.intelligence.items).length"
    class="analysis-log-intelligence-panel analysis-log-intelligence-component"
    aria-label="로그 인텔리전스"
  >
    <header>
      <div>
        <span class="label">Log Intelligence</span>
        <h3>{{ props.beginner ? '로그에서 무엇을 먼저 봐야 하나요?' : 'Log Signals' }}</h3>
        <p>{{ text(props.beginner ? props.intelligence.beginnerSummary || props.intelligence.summary : props.intelligence.summary || props.intelligence.beginnerSummary) }}</p>
      </div>
      <div class="analysis-log-intelligence-summary">
        <span class="analysis-chip danger">High {{ number(props.intelligence.highSeveritySignals) }}</span>
        <span class="analysis-chip">Previous {{ number(props.intelligence.previousLogSignals) }}</span>
      </div>
    </header>
    <div class="analysis-log-intelligence-list">
      <article
        v-for="item in arrayValue(props.intelligence.items).slice(0, 8)"
        :key="`${text(item.podName)}-${text(item.containerName)}-${text(item.category)}-${text(item.signal)}`"
      >
        <div class="analysis-log-intelligence-heading">
          <span class="analysis-chip" :class="props.severityClass(item.severity)">{{ text(item.severity, 'INFO') }}</span>
          <strong>{{ text(item.title || item.category, '로그 위험 신호') }}</strong>
          <small>{{ text(item.podName) }} / {{ text(item.containerName) }}</small>
        </div>
        <code>{{ text(item.signal) }}</code>
        <p>{{ text(props.beginner ? item.beginnerExplanation || item.operatorMeaning : item.operatorMeaning || item.beginnerExplanation) }}</p>
        <small>{{ text(item.recommendedNextAction) }}</small>
        <button
          v-if="text(item.verificationCommand).trim()"
          class="command-copy-row"
          type="button"
          @click="emit('copy', text(item.verificationCommand))"
        >
          <strong><i class="pi pi-copy"></i> 검증 명령 복사</strong>
          <code>{{ text(item.verificationCommand) }}</code>
          <span class="command-copy-reason"><b>판단 근거</b>{{ text(item.operatorMeaning) }}</span>
        </button>
      </article>
    </div>
  </section>
</template>
