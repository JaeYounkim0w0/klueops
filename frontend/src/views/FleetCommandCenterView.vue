<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import {
  ApiError,
  api,
  type AnalysisBenchmarkResponse,
  type ClusterResponse,
  type FleetQueueResponse,
  type LiveValidationPolicyResponse,
  type LiveValidationPreviewResponse,
  type LiveValidationRunResponse,
  type ReliabilityTrendResponse,
  type ShiftBriefingResponse,
  type ValidationLabRunResponse,
  type ValidationScenarioResponse
} from '@/api/client';
import { subscribeOperationsFeed, useOperationsFeedState } from '@/stores/operationsFeed';
import { formatTimestamp } from '@/utils/time';

type WorkspaceTab = 'queue' | 'trend' | 'validation';
type Persona = 'beginner' | 'expert';

interface SavedView {
  id: string;
  name: string;
  cluster: string;
  severity: string;
  query: string;
}

const SAVED_VIEWS_KEY = 'aiops-fleet-saved-views-v1';
const PERSONA_KEY = 'aiops-operator-persona';
const { connected } = useOperationsFeedState();
const route = useRoute();
const router = useRouter();
const mode = ref<Persona>(localStorage.getItem(PERSONA_KEY) === 'expert' ? 'expert' : 'beginner');
const activeTab = ref<WorkspaceTab>(
  ['trend', 'validation'].includes(String(route.query.tab)) ? route.query.tab as WorkspaceTab : 'queue'
);
const queue = ref<FleetQueueResponse>();
const briefing = ref<ShiftBriefingResponse>();
const scenarios = ref<ValidationScenarioResponse[]>([]);
const clusters = ref<ClusterResponse[]>([]);
const validation = ref<ValidationLabRunResponse>();
const benchmark = ref<AnalysisBenchmarkResponse>();
const trend = ref<ReliabilityTrendResponse>();
const livePolicy = ref<LiveValidationPolicyResponse>();
const livePreview = ref<LiveValidationPreviewResponse>();
const liveRun = ref<LiveValidationRunResponse>();
const loading = ref(true);
const validating = ref(false);
const benchmarking = ref(false);
const previewing = ref(false);
const runningLive = ref(false);
const cleaningLive = ref(false);
const error = ref('');
const clusterFilter = ref(String(route.query.cluster ?? ''));
const severityFilter = ref(String(route.query.severity ?? ''));
const searchQuery = ref(String(route.query.q ?? ''));
const trendCluster = ref('');
const trendNamespace = ref('');
const trendDays = ref(30);
const liveCluster = ref('');
const liveScenario = ref('failed-mount');
const liveTtl = ref(300);
const liveConfirmation = ref('');
const viewName = ref('');
const savedViews = ref<SavedView[]>(loadSavedViews());
let unsubscribe = /** unsubscribe 처리에 필요한 화면 또는 업무 로직을 수행한다. */ () => {};
let refreshTimer: number | undefined;

const clusterNames = computed(() => [...new Set((queue.value?.items ?? []).map((item) => item.clusterName))].sort());
const filteredItems = computed(() => {
  const query = searchQuery.value.trim().toLowerCase();
  return (queue.value?.items ?? []).filter((item) =>
    (!clusterFilter.value || item.clusterName === clusterFilter.value)
    && (!severityFilter.value || item.severity === severityFilter.value)
    && (!query || [item.title, item.summary, item.nextAction, item.namespace, item.clusterName]
      .some((value) => value?.toLowerCase().includes(query)))
  );
});
const trendNamespaces = computed(() => [...new Set(
  (trend.value?.scopes ?? [])
    .filter((item) => !trendCluster.value || item.clusterId === trendCluster.value)
    .map((item) => item.namespace)
    .filter((value): value is string => Boolean(value))
)].sort());
const maximumDaily = computed(() => Math.max(1, ...(trend.value?.daily ?? []).map((item) => item.detected)));
const visibleDaily = computed(() => (trend.value?.daily ?? []).slice(-14));
const selectedScenario = computed(() => scenarios.value.find((item) => item.id === liveScenario.value));
const canRunLive = computed(() => Boolean(
  livePolicy.value?.enabled
  && livePreview.value?.executable
  && liveConfirmation.value === livePolicy.value.requiredConfirmation
));

/** load 처리 결과를 조회해 반환한다. */
async function load(silent = false) {
  if (!silent) loading.value = true;
  error.value = '';
  try {
    const [fleet, handoff, catalog, clusterList, policy, latestBenchmark, reliability] = await Promise.all([
      api.getFleetQueue(),
      api.getShiftBriefing(),
      api.listValidationScenarios(),
      api.listClusters(),
      api.getLiveValidationPolicy(),
      api.getLatestAnalysisBenchmark(),
      api.getReliabilityTrend({ days: trendDays.value })
    ]);
    queue.value = fleet;
    briefing.value = handoff;
    scenarios.value = catalog;
    clusters.value = clusterList;
    livePolicy.value = policy;
    benchmark.value = latestBenchmark;
    trend.value = reliability;
    if (!liveCluster.value && clusterList.length) liveCluster.value = clusterList[0].id;
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : 'Fleet 운영 정보를 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

/** loadTrend 처리 결과를 조회해 반환한다. */
async function loadTrend() {
  error.value = '';
  try {
    trend.value = await api.getReliabilityTrend({
      clusterId: trendCluster.value || undefined,
      namespace: trendNamespace.value || undefined,
      days: trendDays.value
    });
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '운영 추세를 계산하지 못했습니다.';
  }
}

/** scheduleRefresh 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function scheduleRefresh() {
  window.clearTimeout(refreshTimer);
  refreshTimer = window.setTimeout(() => void load(true), 500);
}

/** runValidation 처리의 핵심 작업 흐름을 실행한다. */
async function runValidation() {
  validating.value = true;
  error.value = '';
  try {
    validation.value = await api.runValidationLab();
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '가상 검증을 실행하지 못했습니다.';
  } finally {
    validating.value = false;
  }
}

/** runBenchmark 처리의 핵심 작업 흐름을 실행한다. */
async function runBenchmark() {
  benchmarking.value = true;
  error.value = '';
  try {
    benchmark.value = await api.runAnalysisBenchmark();
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '분석 Benchmark를 실행하지 못했습니다.';
  } finally {
    benchmarking.value = false;
  }
}

/** previewLiveValidation 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function previewLiveValidation() {
  if (!liveCluster.value) return;
  previewing.value = true;
  livePreview.value = undefined;
  liveRun.value = undefined;
  error.value = '';
  try {
    livePreview.value = await api.previewLiveValidation({
      clusterId: liveCluster.value,
      scenarioId: liveScenario.value,
      ttlSeconds: liveTtl.value
    });
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : 'Live Validation 사전 점검에 실패했습니다.';
  } finally {
    previewing.value = false;
  }
}

/** runLiveValidation 처리의 핵심 작업 흐름을 실행한다. */
async function runLiveValidation() {
  if (!canRunLive.value) return;
  runningLive.value = true;
  error.value = '';
  try {
    liveRun.value = await api.runLiveValidation({
      clusterId: liveCluster.value,
      scenarioId: liveScenario.value,
      ttlSeconds: liveTtl.value,
      confirmation: liveConfirmation.value
    });
    liveConfirmation.value = '';
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : 'Live Validation 실행에 실패했습니다.';
  } finally {
    runningLive.value = false;
  }
}

/** cleanupLiveValidation 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function cleanupLiveValidation() {
  if (!liveRun.value) return;
  cleaningLive.value = true;
  error.value = '';
  try {
    liveRun.value = await api.cleanupLiveValidation(liveRun.value.id);
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '검증 namespace 정리에 실패했습니다.';
  } finally {
    cleaningLive.value = false;
  }
}

/** saveCurrentView 처리에 필요한 데이터를 생성하거나 저장한다. */
function saveCurrentView() {
  const name = viewName.value.trim();
  if (!name) return;
  const next: SavedView = {
    id: crypto.randomUUID(),
    name,
    cluster: clusterFilter.value,
    severity: severityFilter.value,
    query: searchQuery.value.trim()
  };
  savedViews.value = [next, ...savedViews.value].slice(0, 10);
  persistSavedViews();
  viewName.value = '';
}

/** applySavedView 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function applySavedView(view: SavedView) {
  clusterFilter.value = view.cluster;
  severityFilter.value = view.severity;
  searchQuery.value = view.query;
}

/** removeSavedView 처리 대상과 관련 상태를 안전하게 정리한다. */
function removeSavedView(id: string) {
  savedViews.value = savedViews.value.filter((item) => item.id !== id);
  persistSavedViews();
}

/** persistSavedViews 처리에 필요한 데이터를 생성하거나 저장한다. */
function persistSavedViews() {
  localStorage.setItem(SAVED_VIEWS_KEY, JSON.stringify(savedViews.value));
}

/** loadSavedViews 처리 결과를 조회해 반환한다. */
function loadSavedViews(): SavedView[] {
  try {
    const value = JSON.parse(localStorage.getItem(SAVED_VIEWS_KEY) ?? '[]');
    return Array.isArray(value) ? value.slice(0, 10) : [];
  } catch {
    return [];
  }
}

/** severityClass 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function severityClass(value: string) {
  return value.toLowerCase();
}

watch(mode, (value) => localStorage.setItem(PERSONA_KEY, value));
watch(activeTab, (value) => {
  void router.replace({ query: { ...route.query, tab: value === 'queue' ? undefined : value } });
});
watch([clusterFilter, severityFilter, searchQuery], ([cluster, severity, q]) => {
  void router.replace({
    query: {
      ...route.query,
      cluster: cluster || undefined,
      severity: severity || undefined,
      q: q.trim() || undefined
    }
  });
});
watch([liveCluster, liveScenario, liveTtl], () => {
  livePreview.value = undefined;
  liveRun.value = undefined;
  liveConfirmation.value = '';
});
watch(trendCluster, () => {
  trendNamespace.value = '';
});

onMounted(() => {
  void load();
  unsubscribe = subscribeOperationsFeed((event) => {
    if (event.type !== 'heartbeat') scheduleRefresh();
  });
});

onBeforeUnmount(() => {
  unsubscribe();
  window.clearTimeout(refreshTimer);
});
</script>

<template>
  <section class="page fleet-command-page">
    <header class="page-header operations-page-header">
      <div>
        <span class="page-eyebrow">FLEET OPERATIONS</span>
        <h1>Fleet Command Center</h1>
        <p>클러스터 우선순위, 운영 신뢰도와 안전한 검증을 한 작업 공간에서 관리합니다.</p>
      </div>
      <div class="command-header-tools">
        <div class="view-mode-toggle" role="group" aria-label="표시 난이도">
          <button type="button" :class="{ active: mode === 'beginner' }" @click="mode = 'beginner'">초보자</button>
          <button type="button" :class="{ active: mode === 'expert' }" @click="mode = 'expert'">숙련자</button>
        </div>
        <div class="live-feed-indicator compact" :class="{ connected }"><span></span>{{ connected ? 'Live' : '재연결 중' }}</div>
      </div>
    </header>

    <nav class="workspace-tabs" aria-label="Fleet 작업 공간">
      <button type="button" :class="{ active: activeTab === 'queue' }" @click="activeTab = 'queue'"><i class="pi pi-list-check"></i><span>운영 큐</span></button>
      <button type="button" :class="{ active: activeTab === 'trend' }" @click="activeTab = 'trend'"><i class="pi pi-chart-line"></i><span>신뢰도 추세</span></button>
      <button type="button" :class="{ active: activeTab === 'validation' }" @click="activeTab = 'validation'"><i class="pi pi-verified"></i><span>검증 랩</span></button>
    </nav>

    <div v-if="error" class="inline-feedback error"><i class="pi pi-exclamation-triangle"></i><span>{{ error }}</span></div>
    <div v-if="loading" class="operations-empty-state"><i class="pi pi-spin pi-spinner"></i><span>운영 정보를 계산하고 있습니다.</span></div>

    <template v-else-if="briefing && queue">
      <template v-if="activeTab === 'queue'">
        <section class="command-briefing-band" :class="briefing.posture.toLowerCase()">
          <header>
            <div>
              <span class="page-eyebrow">SHIFT HANDOFF</span>
              <h2>지금 운영 상태</h2>
              <p>{{ mode === 'beginner' ? briefing.beginnerSummary : briefing.expertSummary }}</p>
            </div>
            <span class="command-posture">{{ briefing.posture }}</span>
          </header>
          <dl class="command-metric-strip">
            <div><dt>즉시 확인</dt><dd>{{ queue.immediateItems }}</dd></div>
            <div><dt>진행 Incident</dt><dd>{{ briefing.openIncidents }}</dd></div>
            <div><dt>Critical</dt><dd>{{ briefing.criticalIncidents }}</dd></div>
            <div><dt>실패 Job</dt><dd>{{ briefing.failedJobs }}</dd></div>
            <div><dt>수집 저하</dt><dd>{{ briefing.degradedCollectors }}</dd></div>
          </dl>
          <div class="handoff-columns">
            <article>
              <h3><i class="pi pi-bolt"></i> 이번 교대에서 먼저 할 일</h3>
              <ol v-if="briefing.immediateActions.length"><li v-for="item in briefing.immediateActions" :key="item">{{ item }}</li></ol>
              <p v-else>즉시 조치가 필요한 항목이 없습니다.</p>
            </article>
            <article>
              <h3><i class="pi pi-eye"></i> 계속 관찰할 항목</h3>
              <ul v-if="briefing.watchItems.length"><li v-for="item in briefing.watchItems" :key="item">{{ item }}</li></ul>
              <p v-else>별도 관찰 항목이 없습니다.</p>
            </article>
          </div>
        </section>

        <section class="fleet-queue-band">
          <header class="section-heading-row">
            <div><span class="page-eyebrow">ACTION QUEUE</span><h2>전체 클러스터 우선순위</h2><p>Collector 장애, Incident와 실패 Job을 같은 기준으로 정렬합니다.</p></div>
            <button class="icon-button" type="button" title="새로고침" aria-label="새로고침" @click="load()"><i class="pi pi-refresh"></i></button>
          </header>
          <div class="fleet-filter-row expanded">
            <label><span>검색</span><input v-model="searchQuery" type="search" placeholder="문제, namespace, 다음 행동"></label>
            <label><span>클러스터</span><select v-model="clusterFilter"><option value="">전체 클러스터</option><option v-for="name in clusterNames" :key="name">{{ name }}</option></select></label>
            <label><span>심각도</span><select v-model="severityFilter"><option value="">전체 심각도</option><option>CRITICAL</option><option>HIGH</option><option>MEDIUM</option><option>LOW</option></select></label>
            <span>{{ filteredItems.length }}개 표시</span>
          </div>
          <details class="saved-view-panel">
            <summary>저장된 운영 보기 {{ savedViews.length }}개</summary>
            <form @submit.prevent="saveCurrentView"><input v-model="viewName" maxlength="40" placeholder="현재 필터 이름"><button class="secondary-button" type="submit" :disabled="!viewName.trim()"><i class="pi pi-bookmark"></i><span>저장</span></button></form>
            <div v-if="savedViews.length"><span v-for="view in savedViews" :key="view.id"><button type="button" @click="applySavedView(view)">{{ view.name }}</button><button type="button" :aria-label="`${view.name} 삭제`" @click="removeSavedView(view.id)"><i class="pi pi-times"></i></button></span></div>
            <p v-else>반복 사용하는 cluster, severity, 검색 조건을 브라우저에 저장할 수 있습니다.</p>
          </details>
          <div class="fleet-queue-list" :class="mode">
            <article v-for="item in filteredItems" :key="`${item.sourceType}:${item.id}`">
              <div class="fleet-score" :class="severityClass(item.severity)"><strong>{{ item.score }}</strong><span>{{ item.severity }}</span></div>
              <div class="fleet-item-main">
                <div><span>{{ item.sourceType }}</span><strong>{{ item.title }}</strong></div>
                <p>{{ item.summary }}</p>
                <small><i class="pi pi-cloud"></i>{{ item.clusterName }}<template v-if="item.namespace"> / {{ item.namespace }}</template> · {{ formatTimestamp(item.detectedAt) }}</small>
                <div v-if="mode === 'beginner' && item.nextAction" class="fleet-next-action"><i class="pi pi-arrow-right"></i><span>{{ item.nextAction }}</span></div>
              </div>
              <RouterLink v-if="item.targetPath" :to="item.targetPath" class="icon-button" title="상세 열기" aria-label="상세 열기"><i class="pi pi-arrow-up-right"></i></RouterLink>
            </article>
            <div v-if="!filteredItems.length" class="embedded-empty-state"><i class="pi pi-check-circle"></i><span>현재 필터에 해당하는 운영 항목이 없습니다.</span></div>
          </div>
        </section>
      </template>

      <section v-else-if="activeTab === 'trend' && trend" class="reliability-trend-band">
        <header class="section-heading-row">
          <div><span class="page-eyebrow">EVENT-BASED RELIABILITY</span><h2>운영 신뢰도 추세</h2><p>Incident, 상태 변경과 조치 관찰만 사용합니다. CPU·메모리 사용률은 포함하지 않습니다.</p></div>
          <span class="evidence-source-badge">{{ trend.evidenceType }}</span>
        </header>
        <form class="trend-filter-row" @submit.prevent="loadTrend">
          <label><span>클러스터</span><select v-model="trendCluster"><option value="">전체 클러스터</option><option v-for="cluster in clusters" :key="cluster.id" :value="cluster.id">{{ cluster.name }}</option></select></label>
          <label><span>Namespace</span><select v-model="trendNamespace"><option value="">전체 Namespace</option><option v-for="namespace in trendNamespaces" :key="namespace">{{ namespace }}</option></select></label>
          <label><span>기간</span><select v-model="trendDays"><option :value="7">7일</option><option :value="30">30일</option><option :value="90">90일</option></select></label>
          <button class="secondary-button" type="submit"><i class="pi pi-filter"></i><span>적용</span></button>
        </form>
        <dl class="reliability-metric-grid">
          <div><dt>탐지 Incident</dt><dd>{{ trend.incidentsDetected }}</dd><small>선택 기간에 새로 탐지</small></div>
          <div><dt>해결 Incident</dt><dd>{{ trend.incidentsResolved }}</dd><small>RESOLVED 근거 보유</small></div>
          <div><dt>재발률</dt><dd>{{ trend.recurrenceRate }}%</dd><small>{{ trend.recurringIncidents }}개 재발</small></div>
          <div><dt>평균 인지</dt><dd>{{ trend.meanTimeToAcknowledgeMinutes }}분</dd><small>최초 탐지 → 상태 변경</small></div>
          <div><dt>평균 해결</dt><dd>{{ trend.meanTimeToResolveMinutes }}분</dd><small>최초 탐지 → RESOLVED</small></div>
          <div><dt>조치 성공</dt><dd>{{ trend.remediationSuccessRate }}%</dd><small>Closed-loop 관찰 기준</small></div>
          <div><dt>Collector coverage</dt><dd>{{ trend.collectorCoverageRate }}%</dd><small>현재 Watch/Polling 상태 기준</small></div>
        </dl>
        <div class="trend-content-grid">
          <section>
            <h3>최근 14일 Incident 흐름</h3>
            <div class="daily-reliability-chart">
              <article v-for="day in visibleDaily" :key="day.date">
                <span>{{ day.date.slice(5) }}</span>
                <div><i :style="{ width: `${day.detected / maximumDaily * 100}%` }"></i></div>
                <strong>{{ day.detected }}</strong>
                <small>해결 {{ day.resolved }} · 재발 {{ day.recurred }}</small>
              </article>
            </div>
          </section>
          <section>
            <h3>운영 집중 Scope</h3>
            <div v-if="trend.scopes.length" class="reliability-scope-list">
              <article v-for="scope in trend.scopes.slice(0, 12)" :key="`${scope.clusterId}:${scope.namespace}`">
                <div><strong>{{ scope.clusterName }}</strong><span>{{ scope.namespace || 'cluster' }}</span></div>
                <dl><div><dt>탐지</dt><dd>{{ scope.detected }}</dd></div><div><dt>진행</dt><dd>{{ scope.open }}</dd></div><div><dt>재발</dt><dd>{{ scope.recurred }}</dd></div></dl>
              </article>
            </div>
            <div v-else class="embedded-empty-state"><i class="pi pi-chart-line"></i><span>선택 기간에 Incident 기록이 없습니다.</span></div>
          </section>
        </div>
      </section>

      <section v-else class="validation-workspace">
        <section class="benchmark-band">
          <header class="section-heading-row">
            <div><span class="page-eyebrow">ANALYSIS BENCHMARK</span><h2>분석 품질 Release Gate</h2><p>분류, 사실 근거, 명령 안전성과 assertion 실패를 동일한 기준으로 채점합니다.</p></div>
            <button class="primary-button" type="button" :disabled="benchmarking" @click="runBenchmark"><i class="pi" :class="benchmarking ? 'pi-spin pi-spinner' : 'pi-play'"></i><span>{{ benchmarking ? '측정 중' : 'Benchmark 실행' }}</span></button>
          </header>
          <div v-if="benchmark" class="benchmark-result" :class="benchmark.state.toLowerCase()">
            <div><span class="status-pill" :class="benchmark.state.toLowerCase()">{{ benchmark.state }}</span><strong>{{ benchmark.overallScore }}점</strong><small>{{ benchmark.releaseRecommendation }} · {{ formatTimestamp(benchmark.completedAt) }}</small></div>
            <dl><div><dt>원인 분류</dt><dd>{{ benchmark.classificationAccuracy }}%</dd></div><div><dt>근거 충족</dt><dd>{{ benchmark.evidenceCoverage }}%</dd></div><div><dt>명령 안전</dt><dd>{{ benchmark.commandSafetyRate }}%</dd></div><div><dt>Assertion 실패</dt><dd>{{ benchmark.falseAssertionRate }}%</dd></div><div><dt>P95</dt><dd>{{ benchmark.p95LatencyMs }}ms</dd></div></dl>
            <ul v-if="benchmark.blockingReasons.length"><li v-for="reason in benchmark.blockingReasons" :key="reason">{{ reason }}</li></ul>
          </div>
          <div v-else class="embedded-empty-state"><i class="pi pi-verified"></i><span>아직 실행된 Benchmark가 없습니다.</span></div>
        </section>

        <section class="validation-lab-band">
          <header class="section-heading-row">
            <div><span class="page-eyebrow">VIRTUAL SAFE</span><h2>가상 장애 회귀 검증</h2><p>실제 클러스터를 변경하지 않고 알려진 장애 계약을 빠르게 인증합니다.</p></div>
            <button class="secondary-button" type="button" :disabled="validating" @click="runValidation"><i class="pi" :class="validating ? 'pi-spin pi-spinner' : 'pi-verified'"></i><span>{{ validating ? '검증 중' : '8개 검증' }}</span></button>
          </header>
          <div class="validation-summary" v-if="validation">
            <span class="status-pill" :class="validation.status.toLowerCase()">{{ validation.status }}</span>
            <strong>{{ validation.score }}점</strong>
            <p>{{ validation.passedCases }}/{{ validation.totalCases }} 통과 · {{ validation.mode }} · {{ formatTimestamp(validation.completedAt) }}</p>
          </div>
          <div class="validation-scenario-grid compact">
            <article v-for="scenario in scenarios" :key="scenario.id">
              <div><span>{{ scenario.category }}</span><em>{{ scenario.safetyMode }}</em></div>
              <strong>{{ scenario.title }}</strong>
              <p>{{ scenario.expectedRootCause }}</p>
              <template v-if="validation"><span class="scenario-result" :class="validation.cases.find((item) => item.scenarioId === scenario.id)?.status.toLowerCase()">{{ validation.cases.find((item) => item.scenarioId === scenario.id)?.status || 'NOT RUN' }}</span></template>
            </article>
          </div>
        </section>

        <section class="live-validation-band">
          <header class="section-heading-row">
            <div><span class="page-eyebrow">SAFE LIVE VALIDATION</span><h2>실제 Kubernetes 신호 검증</h2><p>격리 namespace에서만 장애 fixture를 만들고 TTL 이후 자동 정리합니다.</p></div>
            <span class="status-pill" :class="livePolicy?.enabled ? 'succeeded' : 'blocked'">{{ livePolicy?.enabled ? 'ENABLED' : 'DISABLED' }}</span>
          </header>
          <div class="live-validation-layout">
            <form class="live-validation-form" @submit.prevent="previewLiveValidation">
              <label><span>테스트 클러스터</span><select v-model="liveCluster"><option v-for="cluster in clusters" :key="cluster.id" :value="cluster.id">{{ cluster.name }} · {{ cluster.environment }}</option></select></label>
              <label><span>장애 시나리오</span><select v-model="liveScenario"><option v-for="scenario in scenarios" :key="scenario.id" :value="scenario.id">{{ scenario.title }}</option></select></label>
              <label><span>자동 정리 TTL</span><select v-model="liveTtl"><option :value="300">5분</option><option :value="600">10분</option><option :value="900">15분</option></select></label>
              <div class="live-scenario-explanation"><strong>{{ selectedScenario?.signal }}</strong><p>{{ selectedScenario?.expectedRootCause }}</p></div>
              <button class="secondary-button" type="submit" :disabled="previewing || !livePolicy?.enabled"><i class="pi" :class="previewing ? 'pi-spin pi-spinner' : 'pi-shield'"></i><span>{{ previewing ? '점검 중' : 'RBAC·안전 점검' }}</span></button>
            </form>
            <aside class="live-safeguards">
              <h3>강제 안전장치</h3>
              <ul><li v-for="item in livePolicy?.safeguards" :key="item">{{ item }}</li></ul>
            </aside>
          </div>
          <div v-if="!livePolicy?.enabled" class="inline-feedback warning"><i class="pi pi-lock"></i><span>서버에서 Live Validation이 비활성화되어 있습니다. 가상 검증과 Benchmark는 계속 사용할 수 있습니다.</span></div>
          <section v-if="livePreview" class="live-preview-panel" :class="{ blocked: !livePreview.executable }">
            <header><div><span class="page-eyebrow">PRE-FLIGHT</span><h3>{{ livePreview.executable ? '실행 준비가 완료되었습니다.' : '실행할 수 없습니다.' }}</h3></div><span>{{ livePreview.namespace }}</span></header>
            <div class="preflight-columns">
              <article><strong>통과</strong><ul><li v-for="item in livePreview.passedChecks" :key="item">{{ item }}</li></ul></article>
              <article v-if="livePreview.blockingReasons.length"><strong>차단 이유</strong><ul><li v-for="item in livePreview.blockingReasons" :key="item">{{ item }}</li></ul></article>
              <article><strong>생성 예정</strong><ul><li v-for="item in livePreview.plannedResources" :key="item">{{ item }}</li></ul></article>
            </div>
            <form v-if="livePreview.executable" class="live-confirm-form" @submit.prevent="runLiveValidation">
              <label><span>실행 확인 문구</span><input v-model="liveConfirmation" autocomplete="off" :placeholder="livePolicy?.requiredConfirmation"></label>
              <button class="danger-button" type="submit" :disabled="runningLive || !canRunLive"><i class="pi" :class="runningLive ? 'pi-spin pi-spinner' : 'pi-play'"></i><span>{{ runningLive ? '실행·관찰 중' : '격리 검증 실행' }}</span></button>
            </form>
          </section>
          <section v-if="liveRun" class="live-run-result">
            <header><div><span class="status-pill" :class="liveRun.state.toLowerCase()">{{ liveRun.state }}</span><strong>{{ liveRun.clusterName }} / {{ liveRun.namespace }}</strong></div><button v-if="liveRun.cleanupRequired" class="secondary-button" type="button" :disabled="cleaningLive" @click="cleanupLiveValidation"><i class="pi pi-trash"></i><span>{{ cleaningLive ? '정리 중' : '지금 정리' }}</span></button></header>
            <p>{{ liveRun.detail }}</p><dl><div><dt>관측 신호</dt><dd>{{ liveRun.observedSignal || '관찰 시간 내 신호 미확정' }}</dd></div><div><dt>자동 정리</dt><dd>{{ formatTimestamp(liveRun.expiresAt) }}</dd></div><div><dt>리소스</dt><dd>{{ liveRun.resources.join(', ') }}</dd></div></dl>
          </section>
        </section>
      </section>
    </template>
  </section>
</template>
