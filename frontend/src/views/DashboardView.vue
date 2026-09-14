<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ApiError, api, type OperationsOverviewResponse } from '@/api/client';
import { formatDurationMs, formatTimestamp } from '@/utils/time';

const router = useRouter();
const overview = ref<OperationsOverviewResponse | null>(null);
const loading = ref(true);
const reconciling = ref(false);
const error = ref('');

const analysisTotal = computed(() => (overview.value?.successfulAnalyses ?? 0) + (overview.value?.failedAnalyses ?? 0));

async function loadOverview() {
  loading.value = true;
  error.value = '';
  try {
    overview.value = await api.getOperationsOverview();
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '운영 현황을 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

async function reconcile() {
  reconciling.value = true;
  error.value = '';
  try {
    overview.value = await api.reconcileOperations();
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '운영 근거 갱신에 실패했습니다.';
  } finally {
    reconciling.value = false;
  }
}

function openPriority(path: string) {
  if (path.startsWith('/')) void router.push(path);
}

function postureClass(value?: string) {
  return String(value ?? '').toLowerCase();
}

onMounted(loadOverview);
</script>

<template>
  <section class="page operations-dashboard-page">
    <header class="page-header operations-page-header">
      <div>
        <span class="page-eyebrow">OPERATIONS OVERVIEW</span>
        <h1>{{ $t('pages.dashboardTitle') }}</h1>
        <p>{{ $t('pages.dashboardDescription') }}</p>
      </div>
      <button class="primary-button" type="button" :disabled="reconciling" @click="reconcile">
        <i class="pi pi-refresh" :class="{ 'pi-spin': reconciling }"></i>
        <span>{{ reconciling ? '근거 갱신 중' : '운영 근거 갱신' }}</span>
      </button>
    </header>

    <div v-if="error" class="inline-feedback error" role="alert">
      <i class="pi pi-exclamation-triangle"></i>
      <div><strong>대시보드를 갱신하지 못했습니다.</strong><span>{{ error }}</span></div>
    </div>

    <div class="operations-metric-strip" aria-label="핵심 운영 지표">
      <button type="button" @click="router.push('/clusters')">
        <span>Clusters</span><strong>{{ loading ? '-' : overview?.clusters ?? 0 }}</strong><small>등록된 운영 대상</small>
      </button>
      <button type="button" class="danger" @click="router.push('/incidents')">
        <span>Open incidents</span><strong>{{ loading ? '-' : overview?.openIncidents ?? 0 }}</strong><small>Critical {{ overview?.criticalIncidents ?? 0 }}</small>
      </button>
      <button type="button" class="warning" @click="router.push('/policies')">
        <span>Policy failures</span><strong>{{ loading ? '-' : overview?.failedPolicyEvaluations ?? 0 }}</strong><small>구성 기반 위반</small>
      </button>
      <button type="button" @click="router.push('/analysis')">
        <span>AI analyses</span><strong>{{ loading ? '-' : analysisTotal }}</strong><small>실패 {{ overview?.failedAnalyses ?? 0 }}</small>
      </button>
      <button type="button">
        <span>Running jobs</span><strong>{{ loading ? '-' : overview?.runningJobs ?? 0 }}</strong><small>평균 {{ formatDurationMs(overview?.averageJobDurationMs) }}</small>
      </button>
    </div>

    <section class="operations-priority-section">
      <header class="section-heading-row">
        <div>
          <span class="page-eyebrow">PRIORITY QUEUE</span>
          <h2>지금 확인할 항목</h2>
          <p>심각도, 반복 횟수, 조치 가능성을 함께 계산한 운영 순서입니다.</p>
        </div>
        <RouterLink to="/incidents" class="text-link">전체 Incident <i class="pi pi-arrow-right"></i></RouterLink>
      </header>

      <div v-if="loading" class="operations-empty-state"><i class="pi pi-spin pi-spinner"></i><span>운영 근거를 집계하고 있습니다.</span></div>
      <div v-else-if="!overview?.priorityQueue.length" class="operations-empty-state success">
        <i class="pi pi-check-circle"></i><div><strong>즉시 확인할 운영 항목이 없습니다.</strong><span>운영 근거 갱신 시 최신 snapshot과 분석 결과를 다시 확인합니다.</span></div>
      </div>
      <div v-else class="priority-table" role="table">
        <button
          v-for="item in overview.priorityQueue"
          :key="`${item.sourceType}-${item.id}`"
          type="button"
          class="priority-row"
          @click="openPriority(item.targetPath)"
        >
          <span class="priority-urgency" :class="postureClass(item.severity)">{{ item.urgency }}</span>
          <span class="priority-main">
            <strong>{{ item.title }}</strong>
            <small>{{ item.clusterName || 'Platform' }}<template v-if="item.namespace"> / {{ item.namespace }}</template> · {{ item.reason || item.sourceType }}</small>
          </span>
          <span class="priority-next">{{ item.nextAction || '상세 근거를 확인하세요.' }}</span>
          <time>{{ formatTimestamp(item.detectedAt) }}</time>
          <i class="pi pi-chevron-right"></i>
        </button>
      </div>
    </section>

    <div class="operations-dashboard-grid">
      <section class="operations-cluster-section">
        <header class="section-heading-row compact">
          <div><span class="page-eyebrow">CLUSTER HEALTH</span><h2>클러스터 상태</h2></div>
          <RouterLink to="/clusters" class="text-link">상세</RouterLink>
        </header>
        <div class="cluster-health-list">
          <button
            v-for="cluster in overview?.clusterHealth ?? []"
            :key="cluster.clusterId"
            type="button"
            @click="router.push(`/clusters/${cluster.clusterId}`)"
          >
            <span class="health-score" :class="postureClass(cluster.posture)">{{ cluster.healthScore }}</span>
            <span><strong>{{ cluster.clusterName }}</strong><small>{{ cluster.posture }} · Incident {{ cluster.openIncidents }} · Policy {{ cluster.failedPolicies }}</small></span>
            <time>{{ formatTimestamp(cluster.lastObservedAt) }}</time>
            <i class="pi pi-chevron-right"></i>
          </button>
          <div v-if="!loading && !overview?.clusterHealth.length" class="operations-empty-state compact">등록된 클러스터가 없습니다.</div>
        </div>
      </section>

      <section class="operations-posture-section">
        <header class="section-heading-row compact"><div><span class="page-eyebrow">CONFIGURATION POSTURE</span><h2>용량·안정성 구성</h2></div><span class="data-source-badge">Spec 기준</span></header>
        <p class="posture-summary">{{ overview?.capacityPosture.summary || '정책 평가 후 구성 기반 posture가 표시됩니다.' }}</p>
        <dl class="posture-definition-list">
          <div><dt>Unavailable workload</dt><dd>{{ overview?.capacityPosture.unavailableWorkloads ?? 0 }}</dd></div>
          <div><dt>Unhealthy Pod</dt><dd>{{ overview?.capacityPosture.unhealthyPods ?? 0 }}</dd></div>
          <div><dt>Pending PVC</dt><dd>{{ overview?.capacityPosture.pendingPvcs ?? 0 }}</dd></div>
          <div><dt>NetworkPolicy gap</dt><dd>{{ overview?.capacityPosture.namespacesWithoutNetworkPolicy ?? 0 }}</dd></div>
        </dl>
        <small class="evidence-disclaimer"><i class="pi pi-info-circle"></i> 실제 CPU·메모리 사용률이 아니라 Kubernetes Spec/Status 기반 결과입니다.</small>
      </section>

      <section class="operations-quality-section">
        <header class="section-heading-row compact"><div><span class="page-eyebrow">AI TRUST</span><h2>분석 신뢰도</h2></div><RouterLink to="/analysis" class="text-link">피드백 남기기</RouterLink></header>
        <div class="quality-score-line"><strong>{{ overview?.aiQuality.verifiedAccuracyRate ?? 0 }}%</strong><span>운영자 검증 정확도</span></div>
        <dl class="posture-definition-list">
          <div><dt>검증된 분석</dt><dd>{{ overview?.aiQuality.feedbackCount ?? 0 }}</dd></div>
          <div><dt>해결·개선</dt><dd>{{ overview?.aiQuality.resolvedOrImprovedCount ?? 0 }}</dd></div>
          <div><dt>부정확</dt><dd>{{ overview?.aiQuality.incorrectCount ?? 0 }}</dd></div>
          <div><dt>위험 제안</dt><dd>{{ overview?.aiQuality.dangerousSuggestionCount ?? 0 }}</dd></div>
        </dl>
      </section>
    </div>
  </section>
</template>
