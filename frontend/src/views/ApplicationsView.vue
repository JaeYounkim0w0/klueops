<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  api,
  type ApplicationResponse,
  type ApplicationRollbackPreviewResponse,
  type ApplicationStatusResponse
} from '@/api/client';
import { useJobCenterStore } from '@/stores/jobCenter';
import { formatElapsedDuration } from '@/utils/time';

type AppOperation = 'sync' | 'restart' | 'rollback';

const route = useRoute();
const router = useRouter();
const jobCenter = useJobCenterStore();

const applications = ref<ApplicationResponse[]>([]);
const statusByApplicationId = ref<Record<string, ApplicationStatusResponse>>({});
const loading = ref(true);
const statusLoadingId = ref('');
const operatingId = ref('');
const errorMessage = ref('');
const feedback = ref<{ tone: 'success' | 'error' | 'info'; message: string; detail?: string } | null>(null);
const selectedApplicationId = ref('');
const pendingOperation = ref<{ application: ApplicationResponse; operation: AppOperation } | null>(null);
const confirmInput = ref('');
const rollbackPreview = ref<ApplicationRollbackPreviewResponse | null>(null);
const rollbackPreviewLoading = ref(false);
const rollbackTargetRevision = ref('');

const selectedApplication = computed(() =>
  applications.value.find((application) => application.id === selectedApplicationId.value) ?? applications.value[0] ?? null
);
const selectedApplicationJobDetail = computed(() => {
  const application = selectedApplication.value;
  return application ? `${application.namespace || 'default'}/${application.name}` : '';
});
const selectedApplicationOperations = computed(() => {
  const detail = selectedApplicationJobDetail.value;
  if (!detail) {
    return [];
  }
  return jobCenter.jobs
    .filter((job) => job.detail === detail && (job.type?.startsWith('APPLICATION_') || job.title.startsWith('Application ')))
    .slice(0, 5);
});
const operationConfirmPhrase = computed(() => {
  if (!pendingOperation.value) {
    return '';
  }
  if (pendingOperation.value.operation === 'rollback' && rollbackPreview.value?.confirmationText) {
    return rollbackPreview.value.confirmationText;
  }
  const app = pendingOperation.value.application;
  return `${pendingOperation.value.operation.toUpperCase()} ${app.namespace || 'default'}/${app.name}`;
});
const canExecutePendingOperation = computed(() => {
  const operation = pendingOperation.value;
  if (!operation || confirmInput.value.trim() !== operationConfirmPhrase.value) {
    return false;
  }
  return operation.operation !== 'rollback' || Boolean(rollbackPreview.value?.executable && rollbackTargetRevision.value);
});

onMounted(loadApplications);

watch(() => route.query.applicationId, (value) => {
  if (typeof value === 'string' && value) {
    selectedApplicationId.value = value;
  }
});

async function loadApplications() {
  loading.value = true;
  errorMessage.value = '';
  try {
    applications.value = await api.listApplications();
    applyRouteSelection();
    await refreshVisibleStatuses();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '애플리케이션 목록을 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

function applyRouteSelection() {
  const queryApplicationId = typeof route.query.applicationId === 'string' ? route.query.applicationId : '';
  if (queryApplicationId && applications.value.some((application) => application.id === queryApplicationId)) {
    selectedApplicationId.value = queryApplicationId;
    return;
  }
  if (!applications.value.some((application) => application.id === selectedApplicationId.value)) {
    selectedApplicationId.value = applications.value[0]?.id ?? '';
  }
}

async function refreshVisibleStatuses() {
  await Promise.all(applications.value.slice(0, 8).map((application) => loadApplicationStatus(application.id, false)));
}

async function loadApplicationStatus(applicationId: string, showFeedback = true) {
  statusLoadingId.value = applicationId;
  try {
    statusByApplicationId.value[applicationId] = await api.getApplicationStatus(applicationId);
    if (showFeedback) {
      feedback.value = { tone: 'success', message: '상태를 갱신했습니다.' };
    }
  } catch (error) {
    feedback.value = {
      tone: 'error',
      message: '상태 조회 실패',
      detail: error instanceof Error ? error.message : '애플리케이션 상태를 조회하지 못했습니다.'
    };
  } finally {
    statusLoadingId.value = '';
  }
}

async function openOperation(application: ApplicationResponse, operation: AppOperation) {
  pendingOperation.value = { application, operation };
  confirmInput.value = '';
  rollbackPreview.value = null;
  rollbackTargetRevision.value = '';
  if (operation === 'rollback') {
    await loadRollbackPreview(application);
  }
}

function closeOperation() {
  pendingOperation.value = null;
  confirmInput.value = '';
  rollbackPreview.value = null;
  rollbackTargetRevision.value = '';
}

async function loadRollbackPreview(application: ApplicationResponse, targetRevision = rollbackTargetRevision.value) {
  rollbackPreviewLoading.value = true;
  try {
    const preview = await api.previewApplicationRollback(application.id, targetRevision || undefined);
    rollbackPreview.value = preview;
    rollbackTargetRevision.value = preview.targetRevision
      || preview.revisions.find((revision) => !revision.current)?.revision
      || '';
    if (preview.confirmationText) {
      confirmInput.value = '';
    }
  } catch (error) {
    rollbackPreview.value = null;
    feedback.value = {
      tone: 'error',
      message: '롤백 검토 실패',
      detail: error instanceof Error ? error.message : '롤백 revision 정보를 불러오지 못했습니다.'
    };
  } finally {
    rollbackPreviewLoading.value = false;
  }
}

async function selectRollbackRevision(revision: string) {
  rollbackTargetRevision.value = revision;
  confirmInput.value = '';
  if (pendingOperation.value?.operation === 'rollback') {
    await loadRollbackPreview(pendingOperation.value.application, revision);
  }
}

async function executePendingOperation() {
  if (!pendingOperation.value || !canExecutePendingOperation.value) {
    return;
  }
  const { application, operation } = pendingOperation.value;
  operatingId.value = application.id;
  feedback.value = { tone: 'info', message: `${operationLabel(operation)} 요청 중` };
  try {
    const started = operation === 'sync'
      ? await api.syncApplication(application.id)
      : operation === 'restart'
      ? await api.restartApplication(application.id)
      : await api.rollbackApplication(application.id, rollbackTargetRevision.value, confirmInput.value.trim());
    jobCenter.registerJob({
      jobId: started.jobId,
      title: `Application ${operationLabel(operation)}`,
      detail: `${application.namespace || 'default'}/${application.name}`,
      type: `APPLICATION_${operation.toUpperCase()}`
    });
    const job = await jobCenter.waitForJob(started.jobId, {
      title: `Application ${operationLabel(operation)}`,
      detail: `${application.namespace || 'default'}/${application.name}`,
      type: `APPLICATION_${operation.toUpperCase()}`
    });
    feedback.value = job.status === 'SUCCEEDED'
      ? { tone: 'success', message: `${operationLabel(operation)} 완료`, detail: `jobId=${started.jobId}` }
      : { tone: 'error', message: `${operationLabel(operation)} 차단 또는 실패`, detail: job.errorMessage || job.errorCode || `jobId=${started.jobId}` };
    await loadApplicationStatus(application.id, false);
  } catch (error) {
    feedback.value = {
      tone: 'error',
      message: `${operationLabel(operation)} 실패`,
      detail: error instanceof Error ? error.message : '작업을 실행하지 못했습니다.'
    };
  } finally {
    operatingId.value = '';
    closeOperation();
  }
}

function goToAnalysis(application: ApplicationResponse) {
  router.push({
    path: '/analysis',
    query: {
      mode: 'application',
      applicationId: application.id,
      clusterId: application.clusterId
    }
  });
}

function goToNamespaceAnalysis(application: ApplicationResponse) {
  router.push({
    path: '/analysis',
    query: {
      mode: 'namespace',
      clusterId: application.clusterId,
      namespace: application.namespace || 'default'
    }
  });
}

function selectApplication(application: ApplicationResponse) {
  selectedApplicationId.value = application.id;
  router.replace({
    path: '/applications',
    query: { applicationId: application.id }
  });
}

function applicationStatus(application: ApplicationResponse) {
  return statusByApplicationId.value[application.id]?.status || application.status || 'UNKNOWN';
}

function statusClass(status: string) {
  const normalized = status.toUpperCase();
  if (['RUNNING', 'READY', 'DEPLOYED'].includes(normalized)) {
    return 'low';
  }
  if (['FAILED', 'DEGRADED'].includes(normalized)) {
    return 'critical';
  }
  if (['DEPLOY_REQUESTED', 'UNKNOWN'].includes(normalized)) {
    return 'medium';
  }
  return 'info';
}

function operationLabel(operation: AppOperation) {
  if (operation === 'sync') {
    return '동기화';
  }
  if (operation === 'restart') {
    return '재시작';
  }
  return '롤백';
}

function rollbackRevisionLabel(revision: string) {
  if (!rollbackPreview.value) {
    return revision;
  }
  const item = rollbackPreview.value.revisions.find((candidate) => candidate.revision === revision);
  if (!item) {
    return revision;
  }
  return `Revision ${item.revision}${item.current ? ' · 현재' : ''}`;
}

function stateText(value?: string) {
  return value && value.trim() ? value : '-';
}

function operationStatusClass(status: string) {
  if (status === 'SUCCEEDED') {
    return 'low';
  }
  if (['FAILED', 'CANCELED', 'TIMEOUT'].includes(status)) {
    return 'critical';
  }
  return 'info';
}

function operationTime(value?: string) {
  return value ? new Date(value).toLocaleString() : '-';
}

function operationDuration(startedAt?: string, completedAt?: string) {
  return formatElapsedDuration(startedAt, completedAt);
}
</script>

<template>
  <section class="page">
    <header class="page-header">
      <div>
        <h1>Applications</h1>
        <p>AI Analysis에서 식별한 애플리케이션의 상태 확인과 제한된 운영 조치를 수행합니다.</p>
      </div>
      <div class="header-actions">
        <button class="secondary-button" type="button" @click="loadApplications">
          <i class="pi pi-refresh"></i>
          <span>새로고침</span>
        </button>
      </div>
    </header>

    <div v-if="errorMessage" class="inline-error">
      <i class="pi pi-exclamation-triangle"></i>
      <span>{{ errorMessage }}</span>
    </div>
    <div v-if="feedback" class="operation-feedback" :class="feedback.tone">
      <i :class="feedback.tone === 'error' ? 'pi pi-exclamation-triangle' : feedback.tone === 'success' ? 'pi pi-check-circle' : 'pi pi-info-circle'"></i>
      <span>{{ feedback.message }}</span>
      <button v-if="feedback.detail" type="button" @click="errorMessage = feedback.detail || ''">상세</button>
    </div>

    <div v-if="loading" class="empty-state">
      <i class="pi pi-spin pi-spinner"></i>
      <span>애플리케이션 목록을 불러오는 중입니다.</span>
    </div>
    <div v-else-if="applications.length === 0" class="empty-state">
      <i class="pi pi-box"></i>
      <span>아직 관리 중인 애플리케이션이 없습니다. 배포 API 또는 AI Analysis 연계를 통해 등록된 애플리케이션이 표시됩니다.</span>
    </div>

    <div v-else class="application-ops-layout">
      <aside class="application-list-panel">
        <button
          v-for="application in applications"
          :key="application.id"
          class="application-list-item"
          :class="{ active: selectedApplication?.id === application.id }"
          type="button"
          @click="selectApplication(application)"
        >
          <strong>{{ application.name }}</strong>
          <span>{{ application.namespace || '-' }} · {{ application.deploymentType || '-' }}</span>
          <small>{{ application.id }}</small>
        </button>
      </aside>

      <section v-if="selectedApplication" class="application-detail-panel">
        <header>
          <div>
            <span class="label">APPLICATION OPERATIONS</span>
            <h2>{{ selectedApplication.name }}</h2>
            <p>{{ selectedApplication.namespace || 'default' }} namespace · cluster {{ selectedApplication.clusterId || '-' }}</p>
          </div>
          <span class="status-pill" :class="statusClass(applicationStatus(selectedApplication))">
            {{ applicationStatus(selectedApplication) }}
          </span>
        </header>

        <div class="application-ops-summary">
          <article>
            <span>배포 유형</span>
            <strong>{{ selectedApplication.deploymentType || '-' }}</strong>
          </article>
          <article>
            <span>최근 동기화</span>
            <strong>{{ statusByApplicationId[selectedApplication.id]?.lastSyncedAt || '-' }}</strong>
          </article>
          <article>
            <span>동기화 상태</span>
            <strong>{{ statusByApplicationId[selectedApplication.id]?.lastSyncStatus || '-' }}</strong>
          </article>
        </div>

        <div v-if="statusByApplicationId[selectedApplication.id]?.lastSyncError" class="inline-error">
          <i class="pi pi-exclamation-triangle"></i>
          <span>{{ statusByApplicationId[selectedApplication.id]?.lastSyncError }}</span>
        </div>

        <div class="application-action-grid">
          <button class="secondary-button" type="button" :disabled="statusLoadingId === selectedApplication.id" @click="loadApplicationStatus(selectedApplication.id)">
            <i :class="statusLoadingId === selectedApplication.id ? 'pi pi-spin pi-spinner' : 'pi pi-heart'"></i>
            상태 확인
          </button>
          <button class="secondary-button" type="button" :disabled="operatingId === selectedApplication.id" @click="openOperation(selectedApplication, 'sync')">
            <i class="pi pi-refresh"></i>
            동기화
          </button>
          <button class="danger-button" type="button" :disabled="operatingId === selectedApplication.id" @click="openOperation(selectedApplication, 'restart')">
            <i class="pi pi-replay"></i>
            재시작
          </button>
          <button class="secondary-button" type="button" :disabled="operatingId === selectedApplication.id" @click="openOperation(selectedApplication, 'rollback')">
            <i class="pi pi-undo"></i>
            롤백 검토
          </button>
          <button class="primary-button" type="button" @click="goToAnalysis(selectedApplication)">
            <i class="pi pi-chart-line"></i>
            AI 분석
          </button>
        </div>

        <section class="application-guard-panel">
          <strong>운영 안전 기준</strong>
          <p>재시작은 Deployment rollout restart로 제한됩니다. 롤백은 revision diff, RBAC, dry-run, rollback guard, 확인 문구를 모두 통과한 경우에만 실행됩니다.</p>
        </section>

        <section class="application-operation-timeline-panel">
          <header>
            <div>
              <span class="label">ACTION TIMELINE</span>
              <h3>최근 운영 조치</h3>
              <p>이 Application에서 실행한 sync/restart/rollback Job과 조치 후 확인 경로입니다.</p>
            </div>
            <button class="secondary-button" type="button" @click="goToNamespaceAnalysis(selectedApplication)">
              <i class="pi pi-chart-line"></i>
              namespace 재분석
            </button>
          </header>
          <div v-if="selectedApplicationOperations.length === 0" class="empty-state compact">
            <i class="pi pi-history"></i>
            <span>아직 이 화면에서 실행한 운영 조치가 없습니다. 조치 후에는 Job Dock과 이 타임라인에서 결과를 함께 확인하세요.</span>
          </div>
          <div v-else class="application-operation-timeline">
            <article v-for="job in selectedApplicationOperations" :key="job.jobId">
              <span class="status-pill" :class="operationStatusClass(job.status)">{{ job.status }}</span>
              <div>
                <strong>{{ job.title }}</strong>
                <p>{{ job.errorMessage || job.detail || job.type }}</p>
                <small>
                  job {{ job.jobId.slice(0, 8) }}
                  · {{ operationTime(job.completedAt || job.startedAt || job.createdAt || job.updatedAt) }}
                  · {{ operationDuration(job.startedAt, job.completedAt) }}
                </small>
              </div>
              <button class="secondary-button compact-button" type="button" @click="goToAnalysis(selectedApplication)">
                application 분석
              </button>
            </article>
          </div>
        </section>
      </section>
    </div>

    <div v-if="pendingOperation" class="modal-backdrop" @click.self="closeOperation">
      <section class="modal-panel operation-modal">
        <header class="modal-header">
          <div>
            <span class="label">{{ operationLabel(pendingOperation.operation) }}</span>
            <h2>{{ pendingOperation.application.name }}</h2>
            <p>{{ pendingOperation.application.namespace || 'default' }} namespace</p>
          </div>
          <button class="icon-button" type="button" @click="closeOperation">
            <i class="pi pi-times"></i>
          </button>
        </header>
        <div class="operation-modal-body">
          <div class="operation-impact-list">
            <article>
              <strong>대상</strong>
              <span>Deployment/{{ pendingOperation.application.name }}</span>
            </article>
            <article>
              <strong>영향</strong>
              <span>{{ pendingOperation.operation === 'restart' ? 'Pod가 순차 재생성될 수 있습니다.' : pendingOperation.operation === 'sync' ? '상태 조회/동기화 Job을 기록합니다.' : '선택한 revision의 Pod template으로 Deployment를 되돌립니다.' }}</span>
            </article>
            <article>
              <strong>다음 확인</strong>
              <span>{{ pendingOperation.operation === 'restart' ? '재시작 후 같은 scope를 AI 재분석하고 rollout event를 확인하세요.' : pendingOperation.operation === 'rollback' ? '롤백 후 status, event, 관련 namespace 분석을 다시 확인하세요.' : 'Job Dock에서 동기화 완료 여부를 확인하세요.' }}</span>
            </article>
          </div>

          <section v-if="pendingOperation.operation === 'rollback'" class="application-rollback-panel">
            <header>
              <div>
                <span class="label">ROLLBACK PREVIEW</span>
                <h3>되돌릴 revision을 선택하세요</h3>
                <p>현재 revision과 대상 revision의 template 상태를 비교한 뒤 guard가 통과한 경우에만 실행할 수 있습니다.</p>
              </div>
              <span class="status-pill" :class="rollbackPreview?.executable ? 'low' : 'critical'">
                {{ rollbackPreview?.executable ? 'Guard 통과' : 'Guard 차단' }}
              </span>
            </header>
            <div v-if="rollbackPreviewLoading" class="empty-state compact">
              <i class="pi pi-spin pi-spinner"></i>
              <span>revision 정보를 확인하는 중입니다.</span>
            </div>
            <div v-else-if="rollbackPreview" class="application-rollback-content">
              <div class="application-rollback-revisions">
                <button
                  v-for="revision in rollbackPreview.revisions"
                  :key="revision.revision"
                  type="button"
                  class="application-rollback-revision"
                  :class="{ active: rollbackTargetRevision === revision.revision, current: revision.current }"
                  :disabled="revision.current"
                  @click="selectRollbackRevision(revision.revision)"
                >
                  <strong>{{ rollbackRevisionLabel(revision.revision) }}</strong>
                  <span>{{ revision.image || 'image 정보 없음' }}</span>
                  <small>{{ revision.replicaSetName || '-' }}</small>
                </button>
              </div>
              <div class="application-rollback-diff">
                <article>
                  <span class="label">Current</span>
                  <strong>Revision {{ rollbackPreview.currentRevision || '-' }}</strong>
                  <code>{{ stateText(rollbackPreview.currentState) }}</code>
                </article>
                <article>
                  <span class="label">Target</span>
                  <strong>Revision {{ rollbackPreview.targetRevision || rollbackTargetRevision || '-' }}</strong>
                  <code>{{ stateText(rollbackPreview.targetState) }}</code>
                </article>
              </div>
              <div class="application-rollback-guard" :class="{ blocked: !rollbackPreview.executable }">
                <i :class="rollbackPreview.executable ? 'pi pi-shield' : 'pi pi-exclamation-triangle'"></i>
                <div>
                  <strong>{{ rollbackPreview.executable ? 'rollback guard가 통과했습니다.' : 'rollback guard가 실행을 차단했습니다.' }}</strong>
                  <p>{{ rollbackPreview.reason || '대상 revision과 권한을 확인하세요.' }}</p>
                </div>
              </div>
            </div>
          </section>

          <label class="form-field">
            <span>확인 문구</span>
            <input v-model="confirmInput" :placeholder="operationConfirmPhrase" />
            <small>{{ operationConfirmPhrase }}</small>
          </label>
          <div class="modal-actions">
            <button class="secondary-button" type="button" @click="closeOperation">취소</button>
            <button
              class="danger-button"
              type="button"
              :disabled="!canExecutePendingOperation || Boolean(operatingId)"
              @click="executePendingOperation"
            >
              <i :class="operatingId ? 'pi pi-spin pi-spinner' : 'pi pi-check'"></i>
              실행
            </button>
          </div>
        </div>
      </section>
    </div>
  </section>
</template>
