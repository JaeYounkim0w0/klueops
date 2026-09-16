<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ApiError, api, type CleanupPreviewResponse, type OperationSettingsResponse, type RegressionRunResponse, type WatchRuntimeStatusResponse } from '@/api/client';
import { formatTimestamp } from '@/utils/time';

const settings = ref<OperationSettingsResponse | null>(null);
const preview = ref<CleanupPreviewResponse | null>(null);
const loading = ref(true);
const saving = ref(false);
const cleaning = ref(false);
const error = ref('');
const message = ref('');
const watchStatuses = ref<WatchRuntimeStatusResponse[]>([]);
const regressionRuns = ref<RegressionRunResponse[]>([]);
const regressionRunning = ref(false);
const restartingWatchId = ref('');
const expandedRegressionId = ref('');

/** load 처리 결과를 조회해 반환한다. */
async function load() {
  loading.value = true;
  try {
    const [operationSettings, watches, regressions] = await Promise.all([
      api.getOperationSettings(), api.listWatchStatuses(), api.listRegressionRuns()
    ]);
    settings.value = operationSettings;
    watchStatuses.value = watches;
    regressionRuns.value = regressions;
  }
  catch (cause) { error.value = cause instanceof ApiError ? cause.message : '운영 설정을 불러오지 못했습니다.'; }
  finally { loading.value = false; }
}

/** save 처리에 필요한 데이터를 생성하거나 저장한다. */
async function save() {
  if (!settings.value) return;
  saving.value = true; error.value = ''; message.value = '';
  try { settings.value = await api.updateOperationSettings(settings.value); message.value = '운영 설정을 저장했습니다.'; }
  catch (cause) { error.value = cause instanceof ApiError ? cause.message : '운영 설정 저장에 실패했습니다.'; }
  finally { saving.value = false; }
}

/** previewCleanup 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function previewCleanup() {
  error.value = ''; message.value = '';
  try { preview.value = await api.previewOperationCleanup(); }
  catch (cause) { error.value = cause instanceof ApiError ? cause.message : '정리 대상을 계산하지 못했습니다.'; }
}

/** executeCleanup 처리의 핵심 작업 흐름을 실행한다. */
async function executeCleanup() {
  if (!preview.value) return;
  cleaning.value = true; error.value = ''; message.value = '';
  try { preview.value = await api.executeOperationCleanup(); message.value = '보관 정책에 따라 데이터를 정리했습니다.'; }
  catch (cause) { error.value = cause instanceof ApiError ? cause.message : '데이터 정리에 실패했습니다.'; }
  finally { cleaning.value = false; }
}

/** restartWatch 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function restartWatch(clusterId: string) {
  restartingWatchId.value = clusterId; error.value = ''; message.value = '';
  try {
    const status = await api.restartWatch(clusterId);
    watchStatuses.value = watchStatuses.value.map((item) => item.clusterId === clusterId ? status : item);
    message.value = `${status.clusterName} Watch 재연결을 요청했습니다.`;
  } catch (cause) { error.value = cause instanceof ApiError ? cause.message : 'Watch 재연결에 실패했습니다.'; }
  finally { restartingWatchId.value = ''; }
}

/** toggleWatch 처리 데이터를 화면 또는 API 표현으로 변환한다. */
async function toggleWatch(watch: WatchRuntimeStatusResponse) {
  restartingWatchId.value = watch.clusterId; error.value = ''; message.value = '';
  try {
    const status = watch.paused ? await api.resumeWatch(watch.clusterId) : await api.pauseWatch(watch.clusterId);
    watchStatuses.value = watchStatuses.value.map((item) => item.clusterId === watch.clusterId ? status : item);
    message.value = `${status.clusterName} Watch를 ${status.paused ? '일시정지' : '재개'}했습니다.`;
  } catch (cause) { error.value = cause instanceof ApiError ? cause.message : 'Watch 상태 변경에 실패했습니다.'; }
  finally { restartingWatchId.value = ''; }
}

/** runRegression 처리의 핵심 작업 흐름을 실행한다. */
async function runRegression() {
  regressionRunning.value = true; error.value = ''; message.value = '';
  try {
    const run = await api.runAnalysisRegression();
    regressionRuns.value = [run, ...regressionRuns.value.filter((item) => item.id !== run.id)];
    expandedRegressionId.value = run.id;
    message.value = `분석 인증 ${run.passedCases}/${run.totalCases} 케이스를 통과했습니다.`;
  } catch (cause) { error.value = cause instanceof ApiError ? cause.message : '분석 회귀 인증에 실패했습니다.'; }
  finally { regressionRunning.value = false; }
}
onMounted(load);
</script>

<template>
  <section class="page operations-settings-page">
    <header class="page-header operations-page-header"><div><span class="page-eyebrow">DATA & RUNTIME</span><h1>{{ $t('pages.operationsSettingsTitle') }}</h1><p>{{ $t('pages.operationsSettingsDescription') }}</p></div><button class="primary-button" type="button" :disabled="saving || !settings" @click="save"><i class="pi pi-save"></i><span>{{ saving ? $t('common.saving') : $t('common.save') }}</span></button></header>
    <div v-if="error" class="inline-feedback error"><i class="pi pi-exclamation-triangle"></i><span>{{ error }}</span></div><div v-if="message" class="inline-feedback success"><i class="pi pi-check-circle"></i><span>{{ message }}</span></div>
    <div v-if="loading" class="operations-empty-state"><i class="pi pi-spin pi-spinner"></i><span>설정을 불러오고 있습니다.</span></div>
    <template v-else-if="settings">
      <section class="settings-band"><header><span class="page-eyebrow">RETENTION</span><h2>데이터 보관 기간</h2><p>운영 데이터, Audit, 완료된 command execution을 정책에 따라 정리합니다. 실행 중인 명령과 최신 기준선은 보존합니다.</p></header><div class="settings-field-grid"><label><span>Event snapshot</span><div><input v-model.number="settings.eventRetentionDays" type="number" min="1" max="365"><em>일</em></div></label><label><span>AI Analysis</span><div><input v-model.number="settings.analysisRetentionDays" type="number" min="7" max="730"><em>일</em></div></label><label><span>Job</span><div><input v-model.number="settings.jobRetentionDays" type="number" min="1" max="365"><em>일</em></div></label><label><span>Notification</span><div><input v-model.number="settings.notificationRetentionDays" type="number" min="1" max="365"><em>일</em></div></label><label><span>Resolved Incident</span><div><input v-model.number="settings.resolvedIncidentRetentionDays" type="number" min="7" max="730"><em>일</em></div></label><label><span>Resource change</span><div><input v-model.number="settings.changeRetentionDays" type="number" min="1" max="365"><em>일</em></div></label><label><span>Audit log</span><div><input v-model.number="settings.auditRetentionDays" type="number" min="30" max="2555"><em>일</em></div></label><label><span>Command execution</span><div><input v-model.number="settings.commandRetentionDays" type="number" min="7" max="730"><em>일</em></div></label></div></section>
      <section class="settings-band"><header><span class="page-eyebrow">RUNTIME THRESHOLDS</span><h2>운영 판단 기준</h2></header><div class="settings-field-grid"><label><span>알림 중복 억제</span><div><input v-model.number="settings.notificationSuppressMinutes" type="number" min="1" max="1440"><em>분</em></div></label><label><span>동기화 지연 판단</span><div><input v-model.number="settings.staleSyncMinutes" type="number" min="5" max="1440"><em>분</em></div></label><label><span>장시간 Job 판단</span><div><input v-model.number="settings.longRunningJobSeconds" type="number" min="30" max="3600"><em>초</em></div></label></div></section>
      <section class="settings-band assurance-band">
        <header><div><span class="page-eyebrow">REAL-TIME SIGNALS</span><h2>Kubernetes Watch</h2><p>전체 동기화 사이에도 Pod와 Event 변화를 감지합니다. 실패 시 지수 백오프로 재연결하며 원문 자격증명과 Secret 값은 저장하지 않습니다.</p></div></header>
        <div v-if="watchStatuses.length" class="watch-status-list">
          <article v-for="watch in watchStatuses" :key="watch.clusterId">
            <span class="watch-state" :class="watch.state.toLowerCase()"><i class="pi" :class="watch.state === 'CONNECTED' ? 'pi-wifi' : watch.paused ? 'pi-pause' : 'pi-exclamation-circle'"></i></span>
            <div>
              <strong>{{ watch.clusterName }}</strong>
              <span>{{ watch.state }} · 마지막 신호 {{ formatTimestamp(watch.lastSignalAt) }}</span>
              <small v-if="watch.lastError">{{ watch.lastError }}</small>
              <small v-else>heartbeat {{ formatTimestamp(watch.lastHeartbeatAt) }} · 재연결 {{ watch.reconnectCount }}회</small>
              <small v-if="watch.nextRetryAt">연속 실패 {{ watch.consecutiveFailures }}회 · 다음 재시도 {{ formatTimestamp(watch.nextRetryAt) }}</small>
            </div>
            <div class="watch-row-actions">
              <button v-if="watch.state !== 'DISABLED'" class="icon-button" type="button" :title="watch.paused ? 'Watch 재개' : 'Watch 일시정지'" :aria-label="watch.paused ? 'Watch 재개' : 'Watch 일시정지'" :disabled="restartingWatchId === watch.clusterId" @click="toggleWatch(watch)"><i class="pi" :class="watch.paused ? 'pi-play' : 'pi-pause'"></i></button>
              <button v-if="watch.state !== 'DISABLED' && !watch.paused" class="icon-button" type="button" title="Watch 재연결" aria-label="Watch 재연결" :disabled="restartingWatchId === watch.clusterId" @click="restartWatch(watch.clusterId)"><i class="pi" :class="restartingWatchId === watch.clusterId ? 'pi-spin pi-spinner' : 'pi-refresh'"></i></button>
            </div>
          </article>
        </div>
        <div v-else class="embedded-empty-state"><i class="pi pi-wifi"></i><span>등록된 클러스터가 없어 Watch를 시작하지 않았습니다.</span></div>
      </section>
      <section class="settings-band assurance-band"><header class="assurance-header"><div><span class="page-eyebrow">ANALYSIS CERTIFICATION</span><h2>AI Analysis 회귀 인증</h2><p>모델 호출과 분리해 핵심 원인 분류, 사실 근거, 읽기 전용 검증, 변경 명령 guard를 반복 검증합니다.</p></div><button class="primary-button" type="button" :disabled="regressionRunning" @click="runRegression"><i class="pi" :class="regressionRunning ? 'pi-spin pi-spinner' : 'pi-verified'"></i><span>{{ regressionRunning ? '인증 중' : '인증 실행' }}</span></button></header><div v-if="regressionRuns.length" class="regression-run-list"><article v-for="run in regressionRuns.slice(0, 5)" :key="run.id"><button type="button" @click="expandedRegressionId = expandedRegressionId === run.id ? '' : run.id"><span class="regression-score" :class="run.status.toLowerCase()">{{ run.score }}</span><div><strong>{{ run.status }} · {{ run.passedCases }}/{{ run.totalCases }} cases</strong><small>{{ run.baselineVersion }} · {{ formatTimestamp(run.completedAt || run.startedAt) }}</small></div><i class="pi" :class="expandedRegressionId === run.id ? 'pi-chevron-up' : 'pi-chevron-down'"></i></button><div v-if="expandedRegressionId === run.id" class="regression-case-list"><article v-for="item in run.cases" :key="item.id"><span :class="item.status.toLowerCase()">{{ item.status }}</span><div><strong>{{ item.title }}</strong><small>{{ item.category }} · {{ item.score }}점 · {{ item.durationMs }}ms</small><ul v-if="item.failures.length"><li v-for="failure in item.failures" :key="failure">{{ failure }}</li></ul><p v-else>필수 assertion {{ item.assertions.length }}개를 통과했습니다.</p></div></article></div></article></div><div v-else class="embedded-empty-state"><i class="pi pi-verified"></i><span>아직 실행된 인증이 없습니다. 배포 전 첫 기준선을 생성하세요.</span></div></section>
      <section class="settings-cleanup-band"><div><span class="page-eyebrow">SAFE CLEANUP</span><h2>정리 대상 미리보기</h2><p>실행 전에 삭제될 레코드 수를 확인합니다. 클러스터별 최신 동기화 스냅샷, 최신 분석 인증, 실행 중인 명령, Kubernetes 실제 리소스에는 영향을 주지 않습니다.</p></div><div class="cleanup-actions"><button class="secondary-button" type="button" @click="previewCleanup"><i class="pi pi-search"></i><span>미리보기</span></button><button class="danger-button" type="button" :disabled="!preview || cleaning" @click="executeCleanup"><i class="pi pi-trash"></i><span>{{ cleaning ? '정리 중' : '정리 실행' }}</span></button></div><dl v-if="preview" class="cleanup-preview"><div><dt>Event snapshots</dt><dd>{{ preview.eventSnapshots }}</dd></div><div><dt>Watch signals</dt><dd>{{ preview.watchSignals }}</dd></div><div><dt>Completed jobs</dt><dd>{{ preview.jobs }}</dd></div><div><dt>Notification</dt><dd>{{ preview.notifications }}</dd></div><div><dt>Changes</dt><dd>{{ preview.changes }}</dd></div><div><dt>Resolved incidents</dt><dd>{{ preview.resolvedIncidents }}</dd></div><div><dt>Policy results</dt><dd>{{ preview.policyEvaluations }}</dd></div><div><dt>Analyses</dt><dd>{{ preview.analyses }}</dd></div><div><dt>Regression runs</dt><dd>{{ preview.regressionRuns }}</dd></div><div><dt>Audit logs</dt><dd>{{ preview.auditLogs }}</dd></div><div><dt>Command executions</dt><dd>{{ preview.commandExecutions }}</dd></div></dl></section>
    </template>
  </section>
</template>
