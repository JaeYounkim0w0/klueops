<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute, useRouter } from 'vue-router';
import {
  ApiError,
  api,
  downloadIncidentReport,
  type IncidentDetailResponse,
  type IncidentCollaborationResponse,
  type IncidentPostmortemResponse,
  type IncidentState,
  type RemediationObservationResponse,
  type RemediationLearningResponse,
  type RunbookTemplateResponse
} from '@/api/client';
import { formatTimestamp } from '@/utils/time';
import { useAuthStore } from '@/stores/auth';

const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const auth = useAuthStore();
const detail = ref<IncidentDetailResponse | null>(null);
const runbooks = ref<RunbookTemplateResponse[]>([]);
const loading = ref(true);
const saving = ref(false);
const error = ref('');
const activeTab = ref<'overview' | 'impact' | 'evidence' | 'timeline' | 'actions' | 'collaboration' | 'prevention'>('overview');
const mode = ref<'beginner' | 'expert'>('beginner');
const nextState = ref<IncidentState>('INVESTIGATING');
const stateNote = ref('');
const observations = ref<RemediationObservationResponse[]>([]);
const remediationLearning = ref<RemediationLearningResponse | null>(null);
const postmortem = ref<IncidentPostmortemResponse | null>(null);
const observing = ref(false);
const reportFormat = ref<'markdown' | 'json' | 'zip'>('markdown');
const reportExporting = ref(false);
const reportNotice = ref('');
const collaboration = ref<IncidentCollaborationResponse | null>(null);
const assignee = ref('');
const tags = ref('');
const acknowledgeDueAt = ref('');
const resolveDueAt = ref('');
const comment = ref('');
const relatedIncidentId = ref('');
const collaborationSaving = ref(false);
const selectedEvidenceIds = ref<string[]>([]);
const splitTitle = ref('');
const canOperate = computed(() => auth.hasCapability('operation:execute'));

const incidentId = computed(() => String(route.params.incidentId ?? ''));
const incident = computed(() => detail.value?.incident ?? null);
const factualEvidence = computed(() => detail.value?.evidence.filter((item) => item.factual) ?? []);
const inferredEvidence = computed(() => detail.value?.evidence.filter((item) => !item.factual) ?? []);
const targetNode = computed(() => detail.value?.intelligence.correlation.nodes.find((item) => item.role === 'TARGET'));

/** load 처리 결과를 조회해 반환한다. */
async function load() {
  loading.value = true;
  error.value = '';
  try {
    detail.value = await api.getIncident(incidentId.value);
    nextState.value = suggestedState(detail.value.incident.state);
    const signal = detail.value.incident.title.split('·')[0]?.trim();
    runbooks.value = await api.listRunbooks(signal ? { signal } : undefined);
    if (!runbooks.value.length) runbooks.value = await api.listRunbooks();
    observations.value = await api.listRemediationObservations(incidentId.value);
    remediationLearning.value = await api.getRemediationLearning(incidentId.value);
    collaboration.value = await api.getIncidentCollaboration(incidentId.value);
    assignee.value = collaboration.value.assignee || '';
    tags.value = collaboration.value.tags.join(', ');
    acknowledgeDueAt.value = localDateTime(collaboration.value.acknowledgeDueAt);
    resolveDueAt.value = localDateTime(collaboration.value.resolveDueAt);
    try {
      postmortem.value = await api.getIncidentPostmortem(incidentId.value);
    } catch {
      postmortem.value = null;
    }
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : 'Incident 상세를 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

/** saveCollaboration 처리에 필요한 데이터를 생성하거나 저장한다. */
async function saveCollaboration() {
  collaborationSaving.value = true;
  error.value = '';
  try {
    collaboration.value = await api.updateIncidentCollaboration(incidentId.value, {
      assignee: assignee.value.trim() || undefined,
      tags: tags.value.split(',').map((item) => item.trim()).filter(Boolean),
      acknowledgeDueAt: isoDateTime(acknowledgeDueAt.value),
      resolveDueAt: isoDateTime(resolveDueAt.value)
    });
    await refreshTimeline();
  } catch (cause) { error.value = cause instanceof ApiError ? cause.message : '협업 정보를 저장하지 못했습니다.'; }
  finally { collaborationSaving.value = false; }
}

/** addComment 처리에 필요한 데이터를 생성하거나 저장한다. */
async function addComment() {
  if (!comment.value.trim()) return;
  collaborationSaving.value = true;
  try { await api.addIncidentComment(incidentId.value, comment.value.trim()); comment.value = ''; await refreshTimeline(); }
  catch (cause) { error.value = cause instanceof ApiError ? cause.message : '댓글을 저장하지 못했습니다.'; }
  finally { collaborationSaving.value = false; }
}

/** addIncidentLink 처리에 필요한 데이터를 생성하거나 저장한다. */
async function addIncidentLink() {
  if (!relatedIncidentId.value.trim()) return;
  collaborationSaving.value = true;
  try {
    collaboration.value = await api.linkIncident(incidentId.value, relatedIncidentId.value.trim(), 'RELATED');
    relatedIncidentId.value = '';
    await refreshTimeline();
  } catch (cause) { error.value = cause instanceof ApiError ? cause.message : 'Incident를 연결하지 못했습니다.'; }
  finally { collaborationSaving.value = false; }
}

/** unlinkIncident 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function unlinkIncident(relatedId: string) {
  await api.unlinkIncident(incidentId.value, relatedId);
  collaboration.value = await api.getIncidentCollaboration(incidentId.value);
  await refreshTimeline();
}

/** mergeRelatedIncident 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function mergeRelatedIncident() {
  const sourceId = relatedIncidentId.value.trim();
  if (!sourceId || !window.confirm('입력한 Incident를 현재 Incident로 병합할까요? 원본은 RESOLVED 처리되고 감사 이력은 보존됩니다.')) return;
  collaborationSaving.value = true;
  try {
    collaboration.value = await api.mergeIncidents(incidentId.value, [sourceId], 'Merged from incident collaboration workspace');
    relatedIncidentId.value = '';
    await refreshTimeline();
  } catch (cause) { error.value = cause instanceof ApiError ? cause.message : 'Incident 병합에 실패했습니다.'; }
  finally { collaborationSaving.value = false; }
}

/** splitSelectedEvidence 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function splitSelectedEvidence() {
  if (!selectedEvidenceIds.value.length || !splitTitle.value.trim()) return;
  collaborationSaving.value = true;
  try {
    const created = await api.splitIncident(incidentId.value, selectedEvidenceIds.value, splitTitle.value.trim(), incident.value?.severity || 'MEDIUM');
    await router.push(`/incidents/${created.id}`);
  } catch (cause) { error.value = cause instanceof ApiError ? cause.message : 'Incident 분할에 실패했습니다.'; }
  finally { collaborationSaving.value = false; }
}

/** refreshTimeline 처리의 핵심 작업 흐름을 실행한다. */
async function refreshTimeline() {
  const latest = await api.getIncident(incidentId.value);
  if (detail.value) detail.value = { ...detail.value, timeline: latest.timeline };
}

/** localDateTime 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function localDateTime(value?: string) {
  if (!value) return '';
  const date = new Date(value);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
}
/** isoDateTime 처리 조건의 충족 여부를 판단한다. */
function isoDateTime(value: string) { return value ? new Date(value).toISOString() : undefined; }

/** startObservation 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function startObservation() {
  observing.value = true;
  error.value = '';
  try {
    await api.startRemediationObservation(
      incidentId.value,
      300,
      incident.value?.sourceAnalysisId
    );
    observations.value = await api.listRemediationObservations(incidentId.value);
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '조치 관찰을 시작하지 못했습니다.';
  } finally {
    observing.value = false;
  }
}

/** evaluateObservation 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function evaluateObservation(observationId: string) {
  await api.evaluateRemediationObservation(observationId);
  observations.value = await api.listRemediationObservations(incidentId.value);
}

/** cancelObservation 처리 조건의 충족 여부를 판단한다. */
async function cancelObservation(observationId: string) {
  await api.cancelRemediationObservation(observationId);
  observations.value = await api.listRemediationObservations(incidentId.value);
}

/** generatePostmortem 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function generatePostmortem() {
  observing.value = true;
  error.value = '';
  try {
    postmortem.value = await api.generateIncidentPostmortem(incidentId.value);
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '회고 초안을 생성하지 못했습니다.';
  } finally {
    observing.value = false;
  }
}

/** updateState 처리 대상의 상태를 갱신한다. */
async function updateState() {
  if (!incident.value) return;
  saving.value = true;
  error.value = '';
  try {
    await api.updateIncidentState(incident.value.id, nextState.value, stateNote.value.trim() || undefined);
    stateNote.value = '';
    await load();
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '상태 변경에 실패했습니다.';
  } finally {
    saving.value = false;
  }
}

/** suggestedState 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function suggestedState(current: IncidentState): IncidentState {
  if (current === 'OPEN' || current === 'REOPENED') return 'ACKNOWLEDGED';
  if (current === 'ACKNOWLEDGED') return 'INVESTIGATING';
  if (current === 'INVESTIGATING') return 'MITIGATING';
  if (current === 'MITIGATING') return 'MONITORING';
  return 'RESOLVED';
}

/** bindCommand 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function bindCommand(command: string) {
  return command
    .split('{namespace}').join(incident.value?.namespace || 'default')
    .split('{resourceName}').join(incident.value?.resourceName || 'RESOURCE_NAME');
}

/** copy 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function copy(value: string) {
  await navigator.clipboard.writeText(value);
}

/** exportReport 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function exportReport() {
  reportExporting.value = true;
  error.value = '';
  reportNotice.value = '';
  try {
    const report = await downloadIncidentReport(incidentId.value, reportFormat.value);
    const url = URL.createObjectURL(report.blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = report.filename;
    anchor.click();
    URL.revokeObjectURL(url);
    reportNotice.value = t('incidentReport.exported', { filename: report.filename });
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : t('incidentReport.exportFailed');
  } finally {
    reportExporting.value = false;
  }
}

onMounted(load);
</script>

<template>
  <section class="page incident-detail-page">
    <header class="incident-detail-header">
      <button class="icon-button" type="button" title="Incident 목록" aria-label="Incident 목록" @click="router.push('/incidents')"><i class="pi pi-arrow-left"></i></button>
      <div v-if="incident"><span class="page-eyebrow">{{ incident.clusterName }}<template v-if="incident.namespace"> / {{ incident.namespace }}</template></span><h1>{{ incident.title }}</h1><p>{{ incident.resourceKind || 'Scope' }}/{{ incident.resourceName || incident.namespace || 'cluster' }} · 최초 {{ formatTimestamp(incident.firstDetectedAt) }}</p></div>
      <div class="incident-header-actions">
        <div class="incident-export-control">
          <select v-model="reportFormat" :aria-label="t('incidentReport.format')"><option value="markdown">Markdown</option><option value="json">JSON</option><option value="zip">Evidence ZIP</option></select>
          <button class="icon-button" type="button" :title="t('incidentReport.export')" :aria-label="t('incidentReport.export')" :disabled="reportExporting" @click="exportReport"><i :class="reportExporting ? 'pi pi-spin pi-spinner' : 'pi pi-download'"></i></button>
        </div>
        <div class="analysis-mode-switch" role="group" aria-label="상세 보기 모드"><button type="button" :class="{ active: mode === 'beginner' }" @click="mode = 'beginner'">초보자</button><button type="button" :class="{ active: mode === 'expert' }" @click="mode = 'expert'">숙련자</button></div>
      </div>
    </header>

    <div v-if="error" class="inline-feedback error"><i class="pi pi-exclamation-triangle"></i><span>{{ error }}</span></div>
    <div v-if="reportNotice" class="inline-feedback success"><i class="pi pi-check-circle"></i><span>{{ reportNotice }}</span></div>
    <div v-if="loading" class="operations-empty-state"><i class="pi pi-spin pi-spinner"></i><span>Incident 근거를 구성하고 있습니다.</span></div>

    <template v-else-if="incident && detail">
      <div class="incident-hero-summary">
        <span class="status-pill" :class="incident.severity.toLowerCase()">{{ incident.severity }}</span>
        <span class="incident-state-badge">{{ incident.state }}</span>
        <strong>{{ incident.summary }}</strong>
        <dl><div><dt>반복</dt><dd>{{ incident.occurrenceCount }}회</dd></div><div><dt>재발</dt><dd>{{ incident.reopenCount }}회</dd></div><div><dt>사실 근거</dt><dd>{{ factualEvidence.length }}</dd></div><div><dt>AI 추론</dt><dd>{{ inferredEvidence.length }}</dd></div></dl>
      </div>

      <nav class="detail-tabs" aria-label="Incident 상세 탭">
        <button v-for="tab in [{id:'overview',label:'개요'},{id:'impact',label:'영향·변경'},{id:'evidence',label:'근거'},{id:'timeline',label:'타임라인'},{id:'actions',label:'검증·조치'},{id:'collaboration',label:'협업'},{id:'prevention',label:'재발 방지'}]" :key="tab.id" type="button" :class="{ active: activeTab === tab.id }" @click="activeTab = tab.id as typeof activeTab">{{ tab.label }}</button>
      </nav>

      <section v-if="activeTab === 'overview'" class="incident-detail-band">
        <div><span class="page-eyebrow">WHAT HAPPENED</span><h2>{{ mode === 'beginner' ? '무슨 문제가 발생했나요?' : 'Incident Summary' }}</h2><p>{{ incident.summary }}</p></div>
        <div><span class="page-eyebrow">NEXT ACTION</span><h2>{{ mode === 'beginner' ? '지금 무엇을 해야 하나요?' : 'Recommended Next Action' }}</h2><p>{{ incident.nextAction }}</p><button v-if="incident.sourceAnalysisId" class="secondary-button" type="button" @click="router.push({ path: '/analysis', query: { analysisId: incident.sourceAnalysisId } })"><i class="pi pi-chart-line"></i><span>원본 분석 보기</span></button></div>
        <div class="incident-recovery-card">
          <span class="page-eyebrow">RECOVERY VERIFICATION</span>
          <h2>{{ detail.recovery.autoResolvable ? '자동 정상화 확인' : '수동 정상화 확인 필요' }}</h2>
          <template v-if="detail.recovery.autoResolvable">
            <div class="recovery-progress" role="progressbar" :aria-valuenow="detail.recovery.consecutiveHealthyCount" :aria-valuemax="detail.recovery.requiredHealthyCount">
              <span :style="{ width: `${Math.min(100, detail.recovery.consecutiveHealthyCount / detail.recovery.requiredHealthyCount * 100)}%` }"></span>
            </div>
            <strong>{{ detail.recovery.consecutiveHealthyCount }} / {{ detail.recovery.requiredHealthyCount }}회 연속 정상</strong>
            <p>서로 다른 최신 동기화 스냅샷에서 두 번 정상이어야 자동으로 종료합니다. 같은 스냅샷 반복 조회는 횟수에 포함하지 않습니다.</p>
            <small>마지막 관측 {{ detail.recovery.lastObservedStatus || '-' }} · {{ formatTimestamp(detail.recovery.lastObservedAt) }}</small>
          </template>
          <p v-else>로그만으로 판단한 문제처럼 상태를 확정하기 어려운 Incident는 자동 종료하지 않습니다. 조치 후 재분석하고 운영 상태를 직접 변경하세요.</p>
        </div>
        <div class="incident-confidence-card">
          <div><span class="page-eyebrow">EVIDENCE CONFIDENCE</span><h2>{{ mode === 'beginner' ? '이 판단을 얼마나 믿을 수 있나요?' : 'Confidence Assessment' }}</h2></div>
          <div class="confidence-score" :class="detail.intelligence.confidence.level.toLowerCase()"><strong>{{ detail.intelligence.confidence.score }}</strong><span>/ 100 · {{ detail.intelligence.confidence.level }}</span></div>
          <p>{{ detail.intelligence.confidence.rationale[0] || '충분한 사실 근거가 아직 수집되지 않았습니다.' }}</p>
          <ul v-if="detail.intelligence.confidence.missingEvidence.length"><li v-for="item in detail.intelligence.confidence.missingEvidence.slice(0, mode === 'beginner' ? 2 : 6)" :key="item">추가 확인: {{ item }}</li></ul>
        </div>
        <form class="incident-state-form" @submit.prevent="updateState"><label><span>운영 상태</span><select v-model="nextState"><option>ACKNOWLEDGED</option><option>INVESTIGATING</option><option>MITIGATING</option><option>MONITORING</option><option>RESOLVED</option></select></label><label><span>상태 변경 메모</span><input v-model="stateNote" maxlength="2000" placeholder="확인 내용 또는 조치 결과"></label><button class="primary-button" type="submit" :disabled="saving"><i class="pi pi-check"></i><span>{{ saving ? '저장 중' : '상태 변경' }}</span></button></form>
      </section>

      <section v-else-if="activeTab === 'impact'" class="incident-impact-section">
        <header class="incident-section-heading"><div><span class="page-eyebrow">BLAST RADIUS</span><h2>{{ mode === 'beginner' ? '어디까지 영향을 받을 수 있나요?' : 'Correlation & Change Candidates' }}</h2><p>{{ detail.intelligence.correlation.blastRadiusSummary }}</p></div><dl><div><dt>연결 리소스</dt><dd>{{ detail.intelligence.correlation.impactedResourceCount }}</dd></div><div><dt>Workload</dt><dd>{{ detail.intelligence.correlation.impactedWorkloadCount }}</dd></div><div><dt>Service</dt><dd>{{ detail.intelligence.correlation.impactedServiceCount }}</dd></div></dl></header>
        <div class="incident-impact-grid">
          <section><h3>연결된 리소스</h3><p class="section-helper">실선 근거는 Kubernetes 명시적 참조, ‘추정’ 표시는 이름·label 규칙으로 연결한 후보입니다.</p><div class="correlation-path"><article v-for="node in detail.intelligence.correlation.nodes" :key="node.id" :class="{ target: node.role === 'TARGET', unhealthy: node.unhealthy }"><i class="pi" :class="node.role === 'TARGET' ? 'pi-map-marker' : 'pi-box'"></i><div><strong>{{ node.resourceKind }}/{{ node.resourceName }}</strong><span>{{ node.role }} · {{ node.status || '-' }}</span></div><em v-if="node.inferred">추정</em></article></div><details v-if="mode === 'expert' && detail.intelligence.correlation.edges.length"><summary>관계 근거 {{ detail.intelligence.correlation.edges.length }}개</summary><ul class="correlation-edge-list"><li v-for="edge in detail.intelligence.correlation.edges" :key="`${edge.sourceId}-${edge.relation}-${edge.targetId}`"><strong>{{ edge.relation }}</strong><span>{{ edge.sourceId }} → {{ edge.targetId }}</span><small>{{ edge.evidence }}<template v-if="edge.inferred"> · 추정</template></small></li></ul></details></section>
          <section><h3>변경 원인 후보</h3><p class="section-helper">문제 발생 시간과 리소스 연결성을 함께 계산한 우선순위이며, 점수만으로 원인을 확정하지 않습니다.</p><div v-if="detail.intelligence.changeCandidates.length" class="change-candidate-list"><article v-for="change in detail.intelligence.changeCandidates" :key="change.changeId"><span class="change-score">{{ change.relevanceScore }}</span><div><strong>{{ change.resourceKind }}/{{ change.resourceName }} · {{ change.changeType }}</strong><p>{{ change.explanation }}</p><small>{{ change.summary }} · {{ formatTimestamp(change.detectedAt) }}</small></div></article></div><div v-else class="embedded-empty-state"><i class="pi pi-history"></i><span>직접 연결된 변경 후보가 없습니다. baseline이 두 번 이상 수집되었는지 확인하세요.</span></div></section>
        </div>
        <small class="inventory-freshness">판단 스냅샷 {{ formatTimestamp(detail.intelligence.correlation.inventoryCollectedAt) }} · 대상 상태 {{ targetNode?.status || '확인 불가' }}</small>
      </section>

      <section v-else-if="activeTab === 'evidence'" class="incident-evidence-ledger">
        <header><div><span class="page-eyebrow">EVIDENCE LEDGER</span><h2>판단 근거</h2><p>실제 Kubernetes 사실과 AI가 해석한 추론을 분리했습니다.</p></div></header>
        <article v-for="item in detail.evidence" :key="item.id" class="incident-evidence-row"><input v-if="canOperate && mode === 'expert'" v-model="selectedEvidenceIds" type="checkbox" :value="item.id" :aria-label="`${item.evidenceType} 선택`"><span class="evidence-type" :class="{ inference: !item.factual }">{{ item.factual ? 'FACT' : 'INFERENCE' }}</span><div><strong>{{ item.evidenceType }}</strong><p>{{ item.summary }}</p><small>{{ item.sourceRef }} · {{ formatTimestamp(item.occurredAt) }}</small></div></article>
        <form v-if="canOperate && mode === 'expert'" class="incident-split-form" @submit.prevent="splitSelectedEvidence"><div><strong>선택 근거를 새 Incident로 분리</strong><p>원본 근거는 유지되고 선택한 근거가 새 Incident에 복제됩니다.</p></div><input v-model="splitTitle" maxlength="500" placeholder="새 Incident 제목"><button class="secondary-button" type="submit" :disabled="!selectedEvidenceIds.length || !splitTitle.trim() || collaborationSaving"><i class="pi pi-share-alt"></i><span>분리</span></button></form>
      </section>

      <section v-else-if="activeTab === 'timeline'" class="incident-timeline">
        <article v-for="activity in detail.timeline" :key="activity.id"><span></span><div><strong>{{ activity.activityType }}</strong><p><template v-if="activity.fromState">{{ activity.fromState }} → </template>{{ activity.toState }}</p><small>{{ activity.note }} · {{ activity.actor }} · {{ formatTimestamp(activity.createdAt) }}</small></div></article>
      </section>

      <section v-else-if="activeTab === 'actions'" class="incident-runbook-list">
        <header><span class="page-eyebrow">VERIFIED RUNBOOK</span><h2>{{ mode === 'beginner' ? '안전한 확인 순서' : 'Matched Runbook' }}</h2></header>
        <section v-if="remediationLearning" class="remediation-learning-band">
          <header>
            <div><span class="page-eyebrow">OUTCOME LEARNING</span><h3>{{ mode === 'beginner' ? '과거에 어떤 조치가 효과가 있었나요?' : 'Comparable Remediation Outcomes' }}</h3><p>{{ remediationLearning.evidenceNotice }}</p></div>
            <span>{{ remediationLearning.comparableSamples }} samples</span>
          </header>
          <div v-if="remediationLearning.recommendations.length" class="remediation-recommendation-list">
            <article v-for="item in remediationLearning.recommendations.slice(0, mode === 'beginner' ? 3 : 10)" :key="`${item.action}:${item.sampleCount}`">
              <div class="recommendation-rate"><strong>{{ item.successRate }}%</strong><span>성공</span></div>
              <div><strong>{{ item.action }}</strong><p>{{ item.explanation }}</p><small>{{ item.sampleCount }}회 관찰 · 성공 {{ item.succeededCount }} · 실패 {{ item.failedCount }} · 평균 {{ item.averageObservationSeconds }}초</small></div>
              <span class="status-pill" :class="item.confidence.toLowerCase()">{{ item.confidence }}</span>
            </article>
          </div>
          <div v-else class="embedded-empty-state"><i class="pi pi-info-circle"></i><span>검증된 과거 표본이 없어 현재 Runbook과 사실 근거를 우선합니다.</span></div>
        </section>
        <section class="remediation-observation-band">
          <div><span class="page-eyebrow">CLOSED-LOOP VERIFICATION</span><h3>조치 결과 관찰</h3><p>조치 전 상태와 최신 Kubernetes snapshot을 비교합니다. CPU·메모리 사용률은 Prometheus 연동 전까지 판정하지 않습니다.</p></div>
          <button class="primary-button" type="button" :disabled="observing || observations.some((item) => item.state === 'OBSERVING')" @click="startObservation"><i class="pi pi-eye"></i><span>{{ observing ? '시작 중' : '5분 관찰 시작' }}</span></button>
          <div v-if="observations.length" class="remediation-observation-list">
            <article v-for="observation in observations" :key="observation.id">
              <span class="status-pill" :class="observation.state.toLowerCase()">{{ observation.state }}</span>
              <div><strong>{{ observation.conclusion || 'Kubernetes 상태를 확인하고 있습니다.' }}</strong><small>{{ formatTimestamp(observation.startedAt) }} → {{ formatTimestamp(observation.observeUntil) }}</small><p v-if="observation.rollbackCandidate">{{ observation.rollbackCandidate }}</p></div>
              <div v-if="observation.state === 'OBSERVING'"><button class="icon-button" type="button" title="지금 평가" aria-label="지금 평가" @click="evaluateObservation(observation.id)"><i class="pi pi-refresh"></i></button><button class="icon-button danger" type="button" title="관찰 중단" aria-label="관찰 중단" @click="cancelObservation(observation.id)"><i class="pi pi-stop"></i></button></div>
            </article>
          </div>
        </section>
        <div class="verification-plan"><article v-for="step in detail.intelligence.verificationPlan" :key="step.order"><span>{{ step.order }}</span><div><div class="verification-title"><strong>{{ step.title }}</strong><em>{{ step.safetyLevel }}</em></div><p>{{ step.purpose }}</p><code>{{ step.command }}</code><small>확인할 신호: {{ step.expectedSignal }}</small></div><button class="icon-button" type="button" title="검증 명령 복사" aria-label="검증 명령 복사" @click="copy(step.command)"><i class="pi pi-copy"></i></button></article></div>
        <article v-for="runbook in runbooks.slice(0, mode === 'beginner' ? 3 : 9)" :key="runbook.id"><div class="runbook-title-row"><span class="status-pill info">{{ runbook.safetyLevel }}</span><div><strong>{{ runbook.title }}</strong><p>{{ runbook.beginnerExplanation }}</p></div></div><div class="runbook-step"><span>1</span><div><strong>원인 확인</strong><p>{{ runbook.expectedResult }}</p><code>{{ bindCommand(runbook.verificationCommand) }}</code></div><button class="icon-button" type="button" title="명령 복사" aria-label="명령 복사" @click="copy(bindCommand(runbook.verificationCommand))"><i class="pi pi-copy"></i></button></div><div class="runbook-step"><span>2</span><div><strong>조치 후 검증</strong><code>{{ bindCommand(runbook.validationCommand) }}</code></div><button class="icon-button" type="button" title="검증 명령 복사" aria-label="검증 명령 복사" @click="copy(bindCommand(runbook.validationCommand))"><i class="pi pi-copy"></i></button></div><details v-if="runbook.rollbackGuidance"><summary>Rollback 가이드</summary><p>{{ runbook.rollbackGuidance }}</p></details></article>
      </section>

      <section v-else-if="activeTab === 'collaboration'" class="incident-collaboration-section">
        <header><div><span class="page-eyebrow">INCIDENT COLLABORATION</span><h2>담당자와 대응 목표</h2><p>누가 언제까지 확인하고 해결할지 한곳에 남깁니다. 변경 내용과 메모는 타임라인에도 기록됩니다.</p></div></header>
        <form class="incident-collaboration-form" @submit.prevent="saveCollaboration">
          <label><span>담당자</span><input v-model="assignee" maxlength="255" placeholder="이름 또는 이메일" :disabled="!canOperate"></label>
          <label><span>태그</span><input v-model="tags" maxlength="1000" placeholder="customer-impact, database" :disabled="!canOperate"></label>
          <label><span>확인 목표</span><input v-model="acknowledgeDueAt" type="datetime-local" :disabled="!canOperate"></label>
          <label><span>해결 목표</span><input v-model="resolveDueAt" type="datetime-local" :disabled="!canOperate"></label>
          <button v-if="canOperate" class="primary-button" type="submit" :disabled="collaborationSaving"><i class="pi pi-save"></i><span>협업 정보 저장</span></button>
        </form>
        <div class="incident-collaboration-grid">
          <section><h3>운영 메모</h3><p>판단 근거, 담당자 인계, 확인 결과를 시간 순서로 남깁니다.</p><form v-if="canOperate" class="incident-comment-form" @submit.prevent="addComment"><textarea v-model="comment" rows="3" maxlength="2000" placeholder="확인한 내용이나 다음 교대자에게 전달할 내용을 입력하세요"></textarea><button class="secondary-button" type="submit" :disabled="collaborationSaving || !comment.trim()"><i class="pi pi-send"></i><span>메모 추가</span></button></form><div class="incident-comment-list"><article v-for="item in detail.timeline.filter((entry) => entry.activityType === 'COMMENT')" :key="item.id"><i class="pi pi-comment"></i><div><p>{{ item.note }}</p><small>{{ item.actor }} · {{ formatTimestamp(item.createdAt) }}</small></div></article><div v-if="!detail.timeline.some((entry) => entry.activityType === 'COMMENT')" class="embedded-empty-state"><i class="pi pi-comment"></i><span>아직 운영 메모가 없습니다.</span></div></div></section>
          <section><h3>관련 Incident</h3><p>같은 장애 원인이나 영향 범위에 속하는 Incident를 연결합니다. 병합은 원본 기록을 삭제하지 않습니다.</p><form v-if="canOperate" class="incident-link-form" @submit.prevent="addIncidentLink"><input v-model="relatedIncidentId" placeholder="Incident ID"><button class="icon-button" type="submit" title="Incident 연결" aria-label="Incident 연결" :disabled="collaborationSaving"><i class="pi pi-link"></i></button><button class="icon-button warning" type="button" title="현재 Incident로 병합" aria-label="현재 Incident로 병합" :disabled="collaborationSaving" @click="mergeRelatedIncident"><i class="pi pi-clone"></i></button></form><div class="incident-link-list"><article v-for="link in collaboration?.links || []" :key="link.relatedIncidentId"><button type="button" @click="router.push(`/incidents/${link.relatedIncidentId}`)"><strong>{{ link.relatedTitle }}</strong><span>{{ link.relationType }} · {{ link.relatedState }}</span></button><button v-if="canOperate" class="icon-button" type="button" title="연결 해제" aria-label="연결 해제" @click="unlinkIncident(link.relatedIncidentId)"><i class="pi pi-times"></i></button></article><div v-if="!collaboration?.links.length" class="embedded-empty-state"><i class="pi pi-link"></i><span>연결된 Incident가 없습니다.</span></div></div></section>
        </div>
      </section>

      <section v-else class="incident-prevention-section"><span class="page-eyebrow">PREVENTION</span><h2>재발 방지 체크</h2><ul><li>동일 fingerprint가 다시 감지되면 Incident는 새로 생성되지 않고 REOPENED로 전환됩니다.</li><li>관련 정책 위반과 resource change를 함께 확인해 설정 원인을 제거하세요.</li><li>조치 후 Namespace AI Analysis를 다시 실행하고 위험도와 Issue Group 감소를 확인하세요.</li></ul><div class="incident-prevention-actions"><button class="secondary-button" type="button" @click="router.push({ path: '/policies', query: { clusterId: incident.clusterId, namespace: incident.namespace } })"><i class="pi pi-shield"></i><span>관련 정책 보기</span></button><button class="secondary-button" type="button" @click="router.push({ path: '/analysis', query: { clusterId: incident.clusterId, namespace: incident.namespace } })"><i class="pi pi-refresh"></i><span>재분석</span></button><button class="secondary-button" type="button" :disabled="observing" @click="generatePostmortem"><i class="pi pi-file-edit"></i><span>회고 초안 생성</span></button></div><section v-if="postmortem" class="incident-postmortem"><header><div><span class="page-eyebrow">FACTUAL POSTMORTEM</span><h3>{{ postmortem.title }}</h3></div><small>{{ formatTimestamp(postmortem.generatedAt) }}</small></header><dl><div><dt>영향</dt><dd>{{ postmortem.impact }}</dd></div><div><dt>원인</dt><dd>{{ postmortem.rootCause }}</dd></div><div><dt>해결</dt><dd>{{ postmortem.resolution }}</dd></div></dl><h4>재발 방지</h4><ul><li v-for="item in postmortem.prevention" :key="item">{{ item }}</li></ul></section></section>
    </template>
  </section>
</template>
