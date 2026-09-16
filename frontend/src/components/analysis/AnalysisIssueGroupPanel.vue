<script setup lang="ts">
import { arrayValue, stringArray, type AnalysisResult } from '@/utils/analysisResult';

defineProps<{
  groups: AnalysisResult[];
  deepDiveForGroup: (group: AnalysisResult) => AnalysisResult;
  conclusionForGroup: (group: AnalysisResult) => AnalysisResult;
  fixReadinessLabel: (value: unknown) => string;
  readinessClass: (value: unknown) => Record<string, boolean>;
  severityClass: (value: unknown) => Record<string, boolean>;
  confidenceClass: (value: unknown) => Record<string, boolean>;
}>();

const emit = defineEmits<{
  textDetail: [group: AnalysisResult];
  deepDive: [group: AnalysisResult];
  resource: [group: AnalysisResult];
  logs: [group: AnalysisResult];
}>();

/** textValue 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function textValue(value: unknown, fallback = '') {
  return value === null || value === undefined ? fallback : String(value);
}

/** numberValue 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function numberValue(value: unknown, fallback = '0') {
  return value === null || value === undefined || value === '' ? fallback : String(value);
}
</script>

<template>
  <section v-if="groups.length" class="analysis-issue-group-panel analysis-issue-group-component" aria-label="원인 그룹">
    <header>
      <div>
        <span class="label">Issue Groups</span>
        <h3>중복 원인 그룹</h3>
        <p>반복 이벤트, 문제 리소스, 로그 신호를 같은 원인 후보별로 묶었습니다.</p>
      </div>
      <span>{{ groups.length }} groups</span>
    </header>
    <div class="analysis-issue-group-list">
      <article v-for="group in groups" :key="textValue(group.issueGroupId)" class="analysis-issue-group-card">
        <div class="analysis-issue-group-heading">
          <div>
            <strong>{{ textValue(group.issueGroupId) }} · {{ textValue(group.title) }}</strong>
            <span>{{ textValue(group.category) }} · score {{ numberValue(group.score, '0') }}</span>
          </div>
          <span class="status-pill" :class="severityClass(group.severity)">{{ textValue(group.severity, 'INFO') }}</span>
        </div>
        <p class="analysis-long-text-preview">{{ textValue(group.rootCause) }}</p>
        <div class="analysis-problem-card-metrics">
          <span class="analysis-chip" :class="readinessClass(group.fixReadiness)">{{ fixReadinessLabel(group.fixReadiness) }}</span>
          <span class="analysis-chip">resources {{ numberValue(group.affectedResourceCount, '0') }}</span>
          <span class="analysis-chip">events {{ numberValue(group.eventOccurrenceCount, '0') }}</span>
          <span class="analysis-chip">logs {{ numberValue(group.logSignalCount, '0') }}</span>
        </div>
        <small class="analysis-long-text-preview">{{ textValue(group.recommendedNextAction) }}</small>
        <div v-if="Object.keys(deepDiveForGroup(group)).length || Object.keys(conclusionForGroup(group)).length" class="analysis-deep-dive-summary">
          <div>
            <strong>진단 체크</strong>
            <span>{{ textValue(deepDiveForGroup(group).beginnerSummary || conclusionForGroup(group).operatorMeaning, '관련 리소스와 이벤트를 단계별로 확인합니다.') }}</span>
          </div>
          <span class="analysis-chip" :class="confidenceClass(conclusionForGroup(group).status)">{{ textValue(conclusionForGroup(group).status, '검증 필요') }}</span>
        </div>
        <div v-if="stringArray(group.evidenceSummary).length" class="analysis-evidence-trace">
          <span class="label">주요 근거</span>
          <ol>
            <li v-for="evidence in stringArray(group.evidenceSummary).slice(0, 2)" :key="evidence"><span>{{ evidence }}</span></li>
          </ol>
          <button v-if="stringArray(group.evidenceSummary).length > 2" class="text-link-button" type="button" @click="emit('textDetail', group)">
            근거 {{ stringArray(group.evidenceSummary).length }}개 전체 보기
          </button>
        </div>
        <div v-if="arrayValue(group.relatedReferences).length" class="analysis-reference-list">
          <span class="label">참조 리소스</span>
          <span v-for="reference in arrayValue(group.relatedReferences).slice(0, 2)" :key="`${textValue(group.issueGroupId)}-${textValue(reference.kind)}-${textValue(reference.name)}`">
            {{ textValue(reference.kind) }}/{{ textValue(reference.name) }} · {{ textValue(reference.reason) }}
          </span>
          <button v-if="arrayValue(group.relatedReferences).length > 2" class="text-link-button" type="button" @click="emit('textDetail', group)">
            참조 {{ arrayValue(group.relatedReferences).length }}개 전체 보기
          </button>
        </div>
        <div class="analysis-problem-card-actions">
          <button class="secondary-button" type="button" @click="emit('textDetail', group)"><i class="pi pi-expand"></i>전체 내용</button>
          <button v-if="Object.keys(deepDiveForGroup(group)).length || Object.keys(conclusionForGroup(group)).length" class="secondary-button" type="button" @click="emit('deepDive', group)"><i class="pi pi-check-square"></i>진단 체크</button>
          <button class="secondary-button" type="button" @click="emit('resource', group)"><i class="pi pi-search"></i>상세</button>
          <button v-if="textValue(group.representativeResourceKind) === 'Pod'" class="secondary-button" type="button" @click="emit('logs', group)"><i class="pi pi-list"></i>로그</button>
        </div>
      </article>
    </div>
  </section>
</template>
