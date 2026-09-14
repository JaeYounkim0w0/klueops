<script setup lang="ts">
import { arrayValue, type AnalysisResult } from '@/utils/analysisResult';
import { displayText } from '@/utils/text';

const props = defineProps<{
  ledger: AnalysisResult;
  beginner: boolean;
}>();

const emit = defineEmits<{ copy: [command: string] }>();

function text(value: unknown, fallback = '-') {
  return displayText(value, fallback);
}

function confidenceClass(value: unknown) {
  const confidence = text(value, '').toUpperCase();
  return {
    success: ['HIGH', 'VERIFIED', 'FACT'].includes(confidence),
    warning: ['MEDIUM', 'INFERRED'].includes(confidence),
    muted: !confidence || ['LOW', 'NEEDS_EVIDENCE'].includes(confidence)
  };
}
</script>

<template>
  <section v-if="arrayValue(props.ledger.items).length" class="analysis-evidence-ledger-panel" aria-label="분석 근거 장부">
    <header>
      <div>
        <span class="label">Evidence Ledger</span>
        <h3>{{ props.beginner ? 'AI가 무엇을 보고 판단했나요?' : 'Evidence Ledger' }}</h3>
        <p>{{ text(props.beginner ? props.ledger.beginnerSummary || props.ledger.summary : props.ledger.summary || props.ledger.beginnerSummary) }}</p>
      </div>
      <span class="analysis-chip">{{ arrayValue(props.ledger.items).length }} evidence</span>
    </header>
    <div class="analysis-evidence-ledger-list">
      <article v-for="item in arrayValue(props.ledger.items).slice(0, 12)" :key="text(item.evidenceId)">
        <span class="analysis-chip" :class="confidenceClass(item.confidence)">
          {{ text(item.evidenceId) }} · {{ text(item.evidenceType) }}
        </span>
        <strong>{{ text(item.source) }}</strong>
        <p>{{ text(props.beginner ? item.beginnerExplanation || item.message : item.message || item.beginnerExplanation) }}</p>
        <button
          v-if="text(item.verificationCommand, '').trim()"
          class="command-copy-row"
          type="button"
          @click="emit('copy', text(item.verificationCommand, ''))"
        >
          <strong><i class="pi pi-copy"></i> 검증 명령 복사</strong>
          <code>{{ text(item.verificationCommand) }}</code>
          <span class="command-copy-reason"><b>판단 근거</b>{{ text(item.message) }}</span>
        </button>
      </article>
    </div>
  </section>
</template>
