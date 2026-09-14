<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import {
  ApiError,
  api,
  type AiReleaseGateResponse,
  type ClusterResponse,
  type RuntimeReadinessResponse,
  type SignalNoisePolicyResponse,
  type WatchContinuityResponse,
  type WatchRuntimeStatusResponse
} from '@/api/client';
import { subscribeOperationsFeed, useOperationsFeedState } from '@/stores/operationsFeed';
import { formatTimestamp } from '@/utils/time';
import ProductionEvidencePanel from '@/components/operations/ProductionEvidencePanel.vue';
import OperationalTelemetryPanel from '@/components/operations/OperationalTelemetryPanel.vue';
import RuntimeReadinessPanel from '@/components/operations/RuntimeReadinessPanel.vue';

const { connected: feedConnected } = useOperationsFeedState();
const clusters = ref<ClusterResponse[]>([]);
const continuities = ref<WatchContinuityResponse[]>([]);
const statuses = ref<WatchRuntimeStatusResponse[]>([]);
const policies = ref<SignalNoisePolicyResponse[]>([]);
const gates = ref<AiReleaseGateResponse[]>([]);
const readiness = ref<RuntimeReadinessResponse | null>(null);
const loading = ref(true);
const saving = ref(false);
const watchBusyId = ref('');
const error = ref('');
const message = ref('');
const policyForm = reactive({
  name: '',
  clusterId: '',
  namespacePattern: '*',
  severityFloor: 'MEDIUM' as SignalNoisePolicyResponse['severityFloor'],
  repeatThreshold: 3,
  maintenanceStart: '',
  maintenanceEnd: '',
  snoozeMinutes: 0,
  enabled: true
});
const gateForm = reactive({
  candidateVersion: 'candidate-v1',
  baselineVersion: 'k8s-analysis-contract-v1',
  minimumRegressionScore: 90,
  minimumGroundTruthSamples: 3,
  minimumVerifiedAccuracy: 80
});
let unsubscribe = () => {};
let refreshTimer: number | undefined;

const continuityRows = computed(() => clusters.value.map((cluster) => ({
  cluster,
  continuity: continuities.value.find((item) => item.clusterId === cluster.id),
  status: statuses.value.find((item) => item.clusterId === cluster.id)
})));

async function load(silent = false) {
  if (!silent) loading.value = true;
  error.value = '';
  try {
    const [clusterList, checkpointList, watchList, policyList, gateList, runtimeReadiness] = await Promise.all([
      api.listClusters(),
      api.listWatchContinuity(),
      api.listWatchStatuses(),
      api.listNoisePolicies(),
      api.listAiReleaseGates(),
      api.getRuntimeReadiness()
    ]);
    clusters.value = clusterList;
    continuities.value = checkpointList;
    statuses.value = watchList;
    policies.value = policyList;
    gates.value = gateList;
    readiness.value = runtimeReadiness;
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '운영 신뢰성 정보를 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

function scheduleRefresh() {
  window.clearTimeout(refreshTimer);
  refreshTimer = window.setTimeout(() => void load(true), 350);
}

async function savePolicy() {
  saving.value = true;
  error.value = '';
  message.value = '';
  try {
    const now = Date.now();
    await api.createNoisePolicy({
      name: policyForm.name.trim(),
      clusterId: policyForm.clusterId || undefined,
      namespacePattern: policyForm.namespacePattern.trim() || '*',
      severityFloor: policyForm.severityFloor,
      repeatThreshold: policyForm.repeatThreshold,
      maintenanceStart: localIso(policyForm.maintenanceStart),
      maintenanceEnd: localIso(policyForm.maintenanceEnd),
      snoozeUntil: policyForm.snoozeMinutes > 0
        ? new Date(now + policyForm.snoozeMinutes * 60_000).toISOString()
        : undefined,
      enabled: policyForm.enabled
    });
    policyForm.name = '';
    policyForm.maintenanceStart = '';
    policyForm.maintenanceEnd = '';
    policyForm.snoozeMinutes = 0;
    message.value = '신호 정책을 저장했습니다.';
    await load(true);
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '신호 정책을 저장하지 못했습니다.';
  } finally {
    saving.value = false;
  }
}

async function togglePolicy(policy: SignalNoisePolicyResponse) {
  await api.updateNoisePolicy(policy.id, {
    name: policy.name,
    clusterId: policy.clusterId,
    namespacePattern: policy.namespacePattern,
    severityFloor: policy.severityFloor,
    repeatThreshold: policy.repeatThreshold,
    maintenanceStart: policy.maintenanceStart,
    maintenanceEnd: policy.maintenanceEnd,
    snoozeUntil: policy.snoozeUntil,
    enabled: !policy.enabled
  });
  await load(true);
}

async function removePolicy(policy: SignalNoisePolicyResponse) {
  await api.deleteNoisePolicy(policy.id);
  await load(true);
}

async function runGate() {
  saving.value = true;
  error.value = '';
  try {
    const result = await api.evaluateAiReleaseGate({ ...gateForm });
    message.value = result.state === 'PASSED'
      ? 'AI 후보가 현재 품질 기준을 통과했습니다.'
      : 'AI 후보 승격이 보류되었습니다. 실패 기준을 확인하세요.';
    await load(true);
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : 'AI Release Gate를 실행하지 못했습니다.';
  } finally {
    saving.value = false;
  }
}

async function restartCollector(clusterId: string) {
  watchBusyId.value = clusterId;
  error.value = '';
  try {
    await api.restartWatch(clusterId);
    message.value = '실시간 수집기 재연결을 요청했습니다.';
    await load(true);
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '수집기를 재연결하지 못했습니다.';
  } finally {
    watchBusyId.value = '';
  }
}

async function toggleCollector(status?: WatchRuntimeStatusResponse) {
  if (!status) return;
  watchBusyId.value = status.clusterId;
  error.value = '';
  try {
    if (status.paused) {
      await api.resumeWatch(status.clusterId);
      message.value = '수집기를 다시 시작했습니다.';
    } else {
      await api.pauseWatch(status.clusterId);
      message.value = '수집기를 일시정지했습니다.';
    }
    await load(true);
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '수집기 상태를 변경하지 못했습니다.';
  } finally {
    watchBusyId.value = '';
  }
}

function collectionModeDescription(state?: string) {
  if (state === 'POLLING') return 'Watch 연결이 불안정해 안전한 주기 조회로 수집 중입니다.';
  if (state === 'RECOVERING') return '주기 조회가 안정화되어 Watch 복귀를 준비하고 있습니다.';
  if (state === 'CONNECTED') return 'Kubernetes 변경을 실시간으로 수집하고 있습니다.';
  if (state === 'PAUSED') return '운영자가 수집을 일시정지했습니다.';
  return '수집 연결 상태를 확인하고 있습니다.';
}

function localIso(value: string) {
  return value ? new Date(value).toISOString() : undefined;
}

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
  <section class="page reliability-page">
    <header class="page-header operations-page-header">
      <div><span class="page-eyebrow">OPERATIONS RELIABILITY</span><h1>{{ $t('pages.reliabilityTitle') }}</h1><p>{{ $t('pages.reliabilityDescription') }}</p></div>
      <div class="live-feed-indicator" :class="{ connected: feedConnected }"><span></span>{{ feedConnected ? '실시간 연결' : '재연결 중' }}</div>
    </header>
    <div v-if="error" class="inline-feedback error"><i class="pi pi-exclamation-triangle"></i><span>{{ error }}</span></div>
    <div v-if="message" class="inline-feedback success"><i class="pi pi-check-circle"></i><span>{{ message }}</span></div>
    <div v-if="loading" class="operations-empty-state"><i class="pi pi-spin pi-spinner"></i><span>운영 상태를 확인하고 있습니다.</span></div>

    <template v-else>
      <RuntimeReadinessPanel v-if="readiness" :readiness="readiness" />

      <section class="commercial-readiness-grid">
        <ProductionEvidencePanel />
        <OperationalTelemetryPanel />
      </section>

      <section class="reliability-band">
        <header><div><span class="page-eyebrow">SIGNAL CONTINUITY</span><h2>Watch 연속성</h2><p>재연결 시 현재 문제 상태를 다시 수집하고 마지막 체크포인트를 기록합니다.</p></div><button class="icon-button" type="button" title="새로고침" aria-label="새로고침" @click="load()"><i class="pi pi-refresh"></i></button></header>
        <div class="continuity-grid">
          <article v-for="row in continuityRows" :key="row.cluster.id">
            <div class="continuity-card-header">
              <div><strong>{{ row.cluster.name }}</strong><span class="status-pill" :class="(row.status?.state || 'starting').toLowerCase()">{{ row.status?.state || 'STARTING' }}</span></div>
              <div class="continuity-actions">
                <button v-if="row.status?.state !== 'DISABLED' && !row.status?.paused" class="icon-button" type="button" title="수집기 재연결" aria-label="수집기 재연결" :disabled="watchBusyId === row.cluster.id" @click="restartCollector(row.cluster.id)"><i class="pi" :class="watchBusyId === row.cluster.id ? 'pi-spin pi-spinner' : 'pi-refresh'"></i></button>
                <button v-if="row.status?.state !== 'DISABLED'" class="icon-button" type="button" :title="row.status?.paused ? '수집기 재개' : '수집기 일시정지'" :aria-label="row.status?.paused ? '수집기 재개' : '수집기 일시정지'" :disabled="watchBusyId === row.cluster.id" @click="toggleCollector(row.status)"><i class="pi" :class="row.status?.paused ? 'pi-play' : 'pi-pause'"></i></button>
              </div>
            </div>
            <p class="continuity-mode-description">{{ collectionModeDescription(row.status?.state) }}</p>
            <dl><div><dt>연속성</dt><dd>{{ row.continuity?.continuityState || '아직 없음' }}</dd></div><div><dt>복구 신호</dt><dd>{{ row.continuity?.gapSignalCount ?? 0 }}</dd></div><div><dt>마지막 재조정</dt><dd>{{ formatTimestamp(row.continuity?.lastReconciledAt) }}</dd></div><div><dt>Heartbeat</dt><dd>{{ formatTimestamp(row.status?.lastHeartbeatAt) }}</dd></div></dl>
            <p v-if="row.continuity?.lastError || row.status?.lastError">{{ row.continuity?.lastError || row.status?.lastError }}</p>
          </article>
        </div>
      </section>

      <section class="reliability-two-column">
        <section class="reliability-band">
          <header><div><span class="page-eyebrow">NOISE GOVERNANCE</span><h2>유지보수·노이즈 정책</h2><p>정상적인 작업 시간에는 신호를 보존하되 자동 Incident 승격을 막습니다.</p></div></header>
          <form class="noise-policy-form" @submit.prevent="savePolicy">
            <label><span>정책 이름</span><input v-model="policyForm.name" maxlength="255" required placeholder="예: qa 야간 점검"></label>
            <label><span>클러스터</span><select v-model="policyForm.clusterId"><option value="">전체 클러스터</option><option v-for="cluster in clusters" :key="cluster.id" :value="cluster.id">{{ cluster.name }}</option></select></label>
            <label><span>Namespace</span><input v-model="policyForm.namespacePattern" maxlength="255" placeholder="* 또는 nginx-*"></label>
            <label><span>최소 심각도</span><select v-model="policyForm.severityFloor"><option>LOW</option><option>MEDIUM</option><option>HIGH</option><option>CRITICAL</option></select></label>
            <label><span>반복 승격 기준</span><input v-model.number="policyForm.repeatThreshold" type="number" min="1" max="100"></label>
            <label><span>즉시 숨김(분)</span><input v-model.number="policyForm.snoozeMinutes" type="number" min="0" max="10080"></label>
            <label><span>점검 시작</span><input v-model="policyForm.maintenanceStart" type="datetime-local"></label>
            <label><span>점검 종료</span><input v-model="policyForm.maintenanceEnd" type="datetime-local"></label>
            <button class="primary-button" type="submit" :disabled="saving"><i class="pi pi-plus"></i><span>정책 추가</span></button>
          </form>
          <div class="noise-policy-list">
            <article v-for="policy in policies" :key="policy.id">
              <div><strong>{{ policy.name }}</strong><span :class="{ disabled: !policy.enabled }">{{ policy.enabled ? '적용 중' : '중지' }}</span></div>
              <p>{{ clusters.find((item) => item.id === policy.clusterId)?.name || '전체 클러스터' }} · {{ policy.namespacePattern }} · {{ policy.severityFloor }} 이상 · {{ policy.repeatThreshold }}회</p>
              <small v-if="policy.snoozeUntil">숨김 만료 {{ formatTimestamp(policy.snoozeUntil) }}</small>
              <div><button class="icon-button" type="button" :title="policy.enabled ? '정책 중지' : '정책 재개'" :aria-label="policy.enabled ? '정책 중지' : '정책 재개'" @click="togglePolicy(policy)"><i class="pi" :class="policy.enabled ? 'pi-pause' : 'pi-play'"></i></button><button class="icon-button danger" type="button" title="정책 삭제" aria-label="정책 삭제" @click="removePolicy(policy)"><i class="pi pi-trash"></i></button></div>
            </article>
            <div v-if="!policies.length" class="embedded-empty-state"><i class="pi pi-volume-off"></i><span>등록된 노이즈 정책이 없습니다.</span></div>
          </div>
        </section>

        <section class="reliability-band">
          <header><div><span class="page-eyebrow">AI RELEASE GATE</span><h2>AI 품질 승격 기준</h2><p>회귀 인증과 운영자 Ground Truth를 모두 통과한 후보만 승격합니다.</p></div></header>
          <form class="release-gate-form" @submit.prevent="runGate">
            <label><span>후보 버전</span><input v-model="gateForm.candidateVersion" required></label>
            <label><span>기준 버전</span><input v-model="gateForm.baselineVersion" required></label>
            <label><span>최소 회귀 점수</span><input v-model.number="gateForm.minimumRegressionScore" type="number" min="0" max="100"></label>
            <label><span>최소 Ground Truth</span><input v-model.number="gateForm.minimumGroundTruthSamples" type="number" min="0" max="1000"></label>
            <label><span>최소 검증 정확도</span><input v-model.number="gateForm.minimumVerifiedAccuracy" type="number" min="0" max="100"></label>
            <button class="primary-button" type="submit" :disabled="saving"><i class="pi pi-shield"></i><span>{{ saving ? '검증 중' : 'Gate 실행' }}</span></button>
          </form>
          <div class="release-gate-list">
            <article v-for="gate in gates" :key="gate.id">
              <span class="status-pill" :class="gate.state === 'PASSED' ? 'healthy' : 'failed'">{{ gate.state }}</span>
              <div><strong>{{ gate.candidateVersion }}</strong><p>회귀 {{ gate.regressionScore }} · 정확도 {{ gate.verifiedAccuracy }}% · 표본 {{ gate.groundTruthSamples }}</p><ul v-if="gate.reasons.length"><li v-for="reason in gate.reasons" :key="reason">{{ reason }}</li></ul><small>{{ formatTimestamp(gate.evaluatedAt) }}</small></div>
            </article>
            <div v-if="!gates.length" class="embedded-empty-state"><i class="pi pi-shield"></i><span>아직 실행한 Release Gate가 없습니다.</span></div>
          </div>
        </section>
      </section>
    </template>
  </section>
</template>
