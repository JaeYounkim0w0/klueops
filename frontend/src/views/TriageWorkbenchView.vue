<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import {
  ApiError,
  api,
  type AiCalibrationSummaryResponse,
  type ClusterResponse,
  type OperationsScorecardResponse,
  type TriageItemResponse,
  type TriageQueueResponse
} from '@/api/client';
import { formatTimestamp } from '@/utils/time';
import { subscribeOperationsFeed, useOperationsFeedState } from '@/stores/operationsFeed';

const router = useRouter();
const { connected: feedConnected } = useOperationsFeedState();
const mode = ref<'BEGINNER' | 'EXPERT'>('BEGINNER');
const clusters = ref<ClusterResponse[]>([]);
const queue = ref<TriageQueueResponse | null>(null);
const scorecard = ref<OperationsScorecardResponse | null>(null);
const calibration = ref<AiCalibrationSummaryResponse | null>(null);
const clusterId = ref('');
const namespace = ref('');
const state = ref('');
const severity = ref('');
const loading = ref(true);
const updatingId = ref('');
const error = ref('');
let unsubscribe = /** unsubscribe 처리에 필요한 화면 또는 업무 로직을 수행한다. */ () => {};
let refreshTimer: number | undefined;

const items = computed(() => {
  const source = queue.value?.items ?? [];
  return severity.value ? source.filter((item) => item.severity === severity.value) : source;
});

/** load 처리 결과를 조회해 반환한다. */
async function load() {
  loading.value = true;
  error.value = '';
  try {
    const [clusterList, triage, operations, ai] = await Promise.all([
      api.listClusters(),
      api.getTriageQueue({
        clusterId: clusterId.value || undefined,
        namespace: namespace.value.trim() || undefined,
        state: state.value || undefined,
        limit: 200
      }),
      api.getOperationsScorecard(),
      api.getAiCalibration()
    ]);
    clusters.value = clusterList;
    queue.value = triage;
    scorecard.value = operations;
    calibration.value = ai;
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '운영 신호를 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

/** updateState 처리 대상의 상태를 갱신한다. */
async function updateState(item: TriageItemResponse, nextState: 'OPEN' | 'ACKNOWLEDGED' | 'SUPPRESSED') {
  updatingId.value = item.id;
  error.value = '';
  try {
    await api.updateTriageState(item.id, nextState);
    await load();
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '신호 상태를 변경하지 못했습니다.';
  } finally {
    updatingId.value = '';
  }
}

/** openItem 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function openItem(item: TriageItemResponse) {
  if (item.incidentId) {
    void router.push(`/incidents/${item.incidentId}`);
    return;
  }
  void router.push({
    path: '/analysis',
    query: { clusterId: item.clusterId, namespace: item.namespace, mode: 'namespace' }
  });
}

watch([clusterId, state], load);
onMounted(() => {
  void load();
  unsubscribe = subscribeOperationsFeed((event) => {
    if (!['triage', 'incident', 'watch-signal', 'watch-continuity'].includes(event.type)) return;
    window.clearTimeout(refreshTimer);
    refreshTimer = window.setTimeout(() => void load(), 350);
  });
});
onBeforeUnmount(() => {
  unsubscribe();
  window.clearTimeout(refreshTimer);
});
</script>

<template>
  <section class="page triage-page">
    <header class="page-header operations-page-header">
      <div>
        <span class="page-eyebrow">OPERATIONS TRIAGE</span>
        <h1>실시간 운영 판단</h1>
        <p>반복 신호를 하나의 원인 후보로 묶고, 확인이 필요한 순서와 다음 행동을 제시합니다.</p>
      </div>
      <div class="triage-header-actions">
        <span class="live-feed-indicator compact" :class="{ connected: feedConnected }"><span></span>{{ feedConnected ? 'Live' : '재연결' }}</span>
        <div class="triage-mode-switch" role="group" aria-label="표시 난이도">
          <button type="button" :class="{ active: mode === 'BEGINNER' }" @click="mode = 'BEGINNER'">초보자</button>
          <button type="button" :class="{ active: mode === 'EXPERT' }" @click="mode = 'EXPERT'">숙련자</button>
        </div>
      </div>
    </header>

    <div v-if="error" class="inline-feedback error"><i class="pi pi-exclamation-triangle"></i><span>{{ error }}</span></div>

    <section v-if="scorecard" class="triage-score-strip" aria-label="운영 핵심 지표">
      <div><span>즉시 확인</span><strong>{{ queue?.highPriority ?? 0 }}</strong><small>High 이상 신호</small></div>
      <div><span>진행 중 사고</span><strong>{{ scorecard.openIncidents }}</strong><small>총 {{ scorecard.totalIncidents }}건</small></div>
      <div><span>평균 인지 시간</span><strong>{{ scorecard.meanTimeToAcknowledgeMinutes }}분</strong><small>MTTA</small></div>
      <div><span>평균 해결 시간</span><strong>{{ scorecard.meanTimeToResolveMinutes }}분</strong><small>MTTR</small></div>
      <div><span>검증 정확도</span><strong>{{ calibration?.verifiedAccuracyRate ?? 0 }}%</strong><small>실제 피드백 기준</small></div>
      <div><span>주간 방향</span><strong>{{ scorecard.weeklyTrend.direction }}</strong><small>{{ scorecard.weeklyTrend.currentIncidents }}건 감지</small></div>
    </section>

    <section class="triage-filter-band">
      <label><span>클러스터</span><select v-model="clusterId"><option value="">전체 클러스터</option><option v-for="cluster in clusters" :key="cluster.id" :value="cluster.id">{{ cluster.name }}</option></select></label>
      <label><span>네임스페이스</span><input v-model="namespace" placeholder="전체 namespace" @keyup.enter="load"></label>
      <label><span>상태</span><select v-model="state"><option value="">전체 상태</option><option value="OPEN">새 신호</option><option value="ACKNOWLEDGED">확인 중</option><option value="INCIDENT_CREATED">사고 생성</option><option value="SUPPRESSED">숨김</option></select></label>
      <label><span>심각도</span><select v-model="severity"><option value="">전체 심각도</option><option value="CRITICAL">Critical</option><option value="HIGH">High</option><option value="MEDIUM">Medium</option><option value="LOW">Low</option></select></label>
      <button class="secondary-button" type="button" :disabled="loading" @click="load"><i class="pi" :class="loading ? 'pi-spin pi-spinner' : 'pi-refresh'"></i><span>새로고침</span></button>
    </section>

    <div v-if="loading" class="operations-empty-state"><i class="pi pi-spin pi-spinner"></i><span>실시간 신호를 정리하고 있습니다.</span></div>
    <section v-else-if="items.length" class="triage-workbench">
      <header><div><span class="page-eyebrow">PRIORITIZED SIGNALS</span><h2>확인할 항목 {{ items.length }}개</h2></div><span>중복 신호 {{ queue?.promotedSignals ?? 0 }}개가 Incident로 승격됨</span></header>
      <div class="triage-table" :class="{ expert: mode === 'EXPERT' }">
        <article v-for="item in items" :key="item.id" class="triage-row">
          <div class="triage-severity" :class="item.severity.toLowerCase()"><strong>{{ item.severity }}</strong><span>{{ item.score }}</span></div>
          <div class="triage-main">
            <div class="triage-title-line"><strong>{{ item.title }}</strong><span>{{ item.state }}</span></div>
            <p>{{ item.summary || '상세 메시지가 없습니다. 현재 리소스 상태와 Event를 확인하세요.' }}</p>
            <div class="triage-scope"><span><i class="pi pi-cloud"></i>{{ item.clusterName }}</span><span><i class="pi pi-folder"></i>{{ item.namespace || 'cluster scope' }}</span><span><i class="pi pi-refresh"></i>{{ item.occurrences }}회</span><span>{{ formatTimestamp(item.lastObservedAt) }}</span></div>
            <div v-if="mode === 'BEGINNER'" class="triage-next-action"><i class="pi pi-arrow-right"></i><span>{{ item.nextAction }}</span></div>
          </div>
          <dl v-if="mode === 'EXPERT'" class="triage-metrics"><div><dt>신뢰도</dt><dd>{{ item.confidence }}</dd></div><div><dt>영향도</dt><dd>{{ item.impact }}</dd></div><div><dt>반복</dt><dd>{{ item.occurrences }}</dd></div></dl>
          <div class="triage-actions">
            <button class="icon-button" type="button" title="상세 확인" aria-label="상세 확인" @click="openItem(item)"><i class="pi pi-search"></i></button>
            <button v-if="item.state === 'OPEN'" class="icon-button" type="button" title="확인 중으로 표시" aria-label="확인 중으로 표시" :disabled="updatingId === item.id" @click="updateState(item, 'ACKNOWLEDGED')"><i class="pi pi-check"></i></button>
            <button v-if="item.state !== 'SUPPRESSED'" class="icon-button" type="button" title="신호 숨김" aria-label="신호 숨김" :disabled="updatingId === item.id" @click="updateState(item, 'SUPPRESSED')"><i class="pi pi-eye-slash"></i></button>
            <button v-else class="icon-button" type="button" title="신호 다시 열기" aria-label="신호 다시 열기" :disabled="updatingId === item.id" @click="updateState(item, 'OPEN')"><i class="pi pi-replay"></i></button>
          </div>
        </article>
      </div>
    </section>
    <div v-else class="operations-empty-state"><i class="pi pi-check-circle"></i><span>현재 필터에서 확인할 운영 신호가 없습니다.</span></div>

    <section v-if="scorecard?.hotspots.length" class="triage-hotspot-band">
      <header><span class="page-eyebrow">RECURRENCE HOTSPOTS</span><h2>반복 장애 지점</h2><p>재발 횟수와 최근 감지 시각을 기준으로 구조적 개선 대상을 보여줍니다.</p></header>
      <div class="hotspot-list"><article v-for="item in scorecard.hotspots" :key="`${item.clusterId}-${item.namespace}-${item.resourceName}`"><span :class="item.severity.toLowerCase()">{{ item.severity }}</span><div><strong>{{ item.resourceKind }}/{{ item.resourceName }}</strong><small>{{ item.clusterName }} · {{ item.namespace || 'cluster scope' }}</small></div><b>재발 {{ item.recurrenceCount }}회</b></article></div>
    </section>
  </section>
</template>
