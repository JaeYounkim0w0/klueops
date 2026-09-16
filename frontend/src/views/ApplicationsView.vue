<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import ApplicationDeliveryNav from '@/components/application/ApplicationDeliveryNav.vue';
import DeploymentStartDialog from '@/components/application/DeploymentStartDialog.vue';
import { api, type ApplicationResponse, type ApplicationReleaseResponse, type ApplicationRuntimeResponse, type ClusterResponse, type LibraryChartResponse, type ReleaseOperationResponse } from '@/api/client';
import { isApplicationProgressing, useApplicationStatusPolling } from '@/composables/useApplicationStatusPolling';
import { useJobCenterStore } from '@/stores/jobCenter';
import { useTenancyStore } from '@/stores/tenancy';

const router = useRouter();
const route = useRoute();
const tenancy = useTenancyStore();
const jobs = useJobCenterStore();
const applications = ref<ApplicationResponse[]>([]);
const clusters = ref<ClusterResponse[]>([]);
const charts = ref<LibraryChartResponse[]>([]);
const selectedId = ref('');
const operations = ref<ReleaseOperationResponse[]>([]);
const runtime = ref<ApplicationRuntimeResponse | null>(null);
const releases = ref<ApplicationReleaseResponse[]>([]);
const loading = ref(true);
const startOpen = ref(false);
const uninstallOpen = ref(false);
const rollbackOpen = ref(false);
const rollbackRevision = ref(0);
const confirmationText = ref('');
const confirmationInput = ref('');
const impactSummary = ref('');
const preservePvcs = ref(true);
const preserveDns = ref(false);
const preserveTls = ref(true);
const cleanupRetry = ref(false);
const message = ref('');
const applicationSearch = ref('');
const statusFilter = ref('ALL');

const selected = computed(() => applications.value.find((item) => item.id === selectedId.value) ?? null);
const clusterNames = computed(() => Object.fromEntries(clusters.value.map((cluster) => [cluster.id, cluster.name])));
const chartVersions = computed(() => Object.fromEntries(charts.value.flatMap((chart) => chart.versions.map((version) => [
  version.id,
  {
    name: chart.name,
    packageName: chart.packageName,
    provider: chart.sourceName || (chart.sourceType === 'HELM_REPOSITORY' ? 'Helm Repository' : '직접 업로드'),
    sourceType: chart.sourceType,
    chartVersion: version.chartVersion,
    appVersion: version.appVersion,
  },
]))));
const canUpgrade = computed(() => ['RUNNING', 'RUNNING_ENDPOINT_DEGRADED', 'FAILED'].includes(selected.value?.status || ''));
const canUninstall = computed(() => !['UNINSTALLED', 'UNINSTALLING'].includes(selected.value?.status || ''));
const filteredApplications = computed(() => {
  // 검색과 상태 필터는 이미 Tenant로 제한된 목록에만 적용해 화면 scope를 유지한다.
  const query = applicationSearch.value.trim().toLowerCase();
  return applications.value.filter((application) => {
    const matchesStatus = statusFilter.value === 'ALL' || application.status === statusFilter.value;
    const searchTarget = [
      application.name,
      application.helmReleaseName,
      application.namespace,
      application.clusterId ? clusterNames.value[application.clusterId] : '',
      application.chartVersionId ? chartVersions.value[application.chartVersionId]?.name : application.helmChart,
    ].filter(Boolean).join(' ').toLowerCase();
    return matchesStatus && (!query || searchTarget.includes(query));
  });
});
const applicationSummary = computed(() => ({
  healthy: applications.value.filter((item) => item.status === 'RUNNING').length,
  progressing: applications.value.filter((item) => isApplicationProgressing(item.status)).length,
  failed: applications.value.filter((item) => ['FAILED', 'DEGRADED', 'RUNNING_ENDPOINT_DEGRADED'].includes(item.status || '')).length,
  inactive: applications.value.filter((item) => ['UNINSTALLED', 'UNKNOWN'].includes(item.status || '')).length,
}));

const statusPolling = useApplicationStatusPolling(
  refreshApplicationStatuses,
  () => applications.value.some((item) => isApplicationProgressing(item.status)),
);

onMounted(async () => {
  await load();
  statusPolling.schedule();
});

watch(() => [tenancy.currentTenantId, tenancy.currentWorkspaceId], ([tenantId, workspaceId], previous) => {
  // 상단 운영 범위가 바뀌면 이전 Tenant의 Application을 남기지 않고 즉시 다시 조회한다.
  if (previous && tenantId && workspaceId) void load();
});

/** load 처리 결과를 조회해 반환한다. */
async function load(): Promise<void> {
  loading.value = true;
  try {
    await tenancy.load();
    const [clusterItems, applicationItems, chartItems] = await Promise.all([
      api.listClusters({ tenantId: tenancy.currentTenantId, workspaceId: tenancy.currentWorkspaceId }),
      api.listTenantApplications(tenancy.currentTenantId),
      api.listLibraryCharts(tenancy.currentTenantId).catch(() => []),
    ]);
    clusters.value = clusterItems;
    charts.value = chartItems;
    const allowed = new Set(clusters.value.map((cluster) => cluster.id));
    // 구형 목록 API의 응답도 선택 Tenant의 Cluster로 한 번 더 제한해 잘못된 화면 노출을 막는다.
    applications.value = applicationItems
      .filter((application) => application.clusterId && allowed.has(application.clusterId));
    const requestedId = typeof route.query.applicationId === 'string' ? route.query.applicationId : '';
    // 새 배포 직후 deep-link가 가리키는 Application을 우선 선택한다.
    selectedId.value = applications.value.some((item) => item.id === requestedId)
      ? requestedId : applications.value[0]?.id || '';
    await loadOperations();
    statusPolling.schedule();
  } catch (error) { message.value = error instanceof Error ? error.message : 'Applications를 불러오지 못했습니다.'; }
  finally { loading.value = false; }
}

/** refreshApplicationStatuses 처리의 핵심 작업 흐름을 실행한다. */
async function refreshApplicationStatuses(): Promise<void> {
  const previousSelected = selected.value;
  try {
    const applicationItems = await api.listTenantApplications(tenancy.currentTenantId);
    const allowed = new Set(clusters.value.map((cluster) => cluster.id));
    applications.value = applicationItems
      .filter((application) => application.clusterId && allowed.has(application.clusterId));

    if (!applications.value.some((item) => item.id === selectedId.value)) {
      selectedId.value = applications.value[0]?.id || '';
    }
    const currentSelected = selected.value;
    // 완료·실패 전환 시 Release revision, Runtime, History도 같은 시점에 갱신한다.
    if (previousSelected?.id !== currentSelected?.id || previousSelected?.status !== currentSelected?.status
      || previousSelected?.currentReleaseRevision !== currentSelected?.currentReleaseRevision) {
      await loadOperations();
    }
  } catch {
    // 일시적인 조회 실패에는 현재 화면을 유지하고 다음 주기에 다시 확인한다.
  }
}

/** chartDetails 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function chartDetails(application: ApplicationResponse | null) {
  if (!application?.chartVersionId) return null;
  return chartVersions.value[application.chartVersionId] || null;
}

/** endpointScope 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function endpointScope(scope?: string): string {
  if (scope === 'EXTERNAL_DOMAIN') return '외부 도메인';
  if (scope === 'LOAD_BALANCER') return 'LoadBalancer';
  if (scope === 'NODE_PORT') return 'NodePort';
  return '클러스터 내부';
}

/** isBrowsableEndpoint 처리 조건의 충족 여부를 판단한다. */
function isBrowsableEndpoint(endpoint: ApplicationRuntimeResponse['endpoints'][number]): boolean {
  // TCP 데이터베이스 endpoint와 거부된 Route를 브라우저 URL처럼 오인하지 않게 한다.
  return /^https?:\/\//.test(endpoint.url) && endpoint.status !== 'DEGRADED';
}

/** selectApplication 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function selectApplication(application: ApplicationResponse): Promise<void> {
  selectedId.value = application.id;
  await router.replace({ path: '/applications', query: { applicationId: application.id } });
  await loadOperations();
}

/** loadOperations 처리 결과를 조회해 반환한다. */
async function loadOperations(): Promise<void> {
  if (!selectedId.value) { operations.value = []; releases.value = []; runtime.value = null; return; }
  [operations.value, releases.value, runtime.value] = await Promise.all([
    api.listReleaseOperations(tenancy.currentTenantId, selectedId.value).catch(() => []),
    api.listApplicationReleases(tenancy.currentTenantId, selectedId.value).catch(() => []),
    api.getApplicationRuntime(tenancy.currentTenantId, selectedId.value).catch(() => null),
  ]);
}

/** openRollback 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function openRollback(): Promise<void> {
  if (!selected.value || !releases.value.length) return;
  rollbackRevision.value = releases.value.find((item) => item.revision !== selected.value?.currentReleaseRevision)?.revision
    || releases.value[releases.value.length - 1].revision;
  await refreshRollbackConfirmation();
  rollbackOpen.value = true;
}

/** refreshRollbackConfirmation 처리의 핵심 작업 흐름을 실행한다. */
async function refreshRollbackConfirmation(): Promise<void> {
  if (!selected.value) return;
  const preview = await api.getRollbackConfirmation(tenancy.currentTenantId, selected.value.id, rollbackRevision.value);
  confirmationText.value = preview.confirmationText; impactSummary.value = preview.impactSummary;
  confirmationInput.value = '';
}

/** rollback 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function rollback(): Promise<void> {
  if (!selected.value || confirmationInput.value.trim() !== confirmationText.value) return;
  const accepted = await api.rollbackHelmApplication(tenancy.currentTenantId, selected.value.id, rollbackRevision.value, confirmationInput.value);
  const job = { jobId: accepted.jobId, title: 'Helm Application Rollback', detail: `revision ${rollbackRevision.value}`, type: 'HELM_ROLLBACK' };
  rollbackOpen.value = false;
  message.value = 'Rollback 작업을 시작했습니다.';
  void jobs.trackJob(job).then(async (result) => {
    message.value = result.status === 'SUCCEEDED'
      ? 'Rollback 작업이 완료되었습니다.'
      : 'Rollback 작업이 실패했습니다. Job Center에서 원인을 확인하세요.';
    await load();
  }).catch(() => { message.value = 'Rollback 작업이 실패했습니다. Job Center에서 원인을 확인하세요.'; });
  await load();
}

/** statusTone 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function statusTone(status?: string): string {
  // Application, Workload, Endpoint 상태를 동일한 시각 언어로 전달한다.
  if (['RUNNING', 'SUCCEEDED', 'READY', 'HEALTHY'].includes(status || '')) return 'success';
  if (status === 'FAILED' || status === 'DEGRADED') return 'danger';
  if (status?.includes('ING') || ['DEPLOY_REQUESTED', 'PENDING', 'APPLIED'].includes(status || '')) return 'progress';
  return 'neutral';
}

/** openUninstall 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function openUninstall(retry = false): Promise<void> {
  if (!selected.value) return;
  const preview = await api.getUninstallConfirmation(tenancy.currentTenantId, selected.value.id);
  confirmationText.value = preview.confirmationText;
  impactSummary.value = preview.impactSummary;
  confirmationInput.value = '';
  cleanupRetry.value = retry;
  uninstallOpen.value = true;
}

/** uninstall 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function uninstall(): Promise<void> {
  if (!selected.value || confirmationInput.value.trim() !== confirmationText.value) return;
  const options = { preservePvcs: preservePvcs.value, preserveDns: preserveDns.value, preserveTls: preserveTls.value };
  const accepted = cleanupRetry.value
    ? await api.retryApplicationCleanup(tenancy.currentTenantId, selected.value.id, confirmationInput.value, options)
    : await api.uninstallHelmApplication(tenancy.currentTenantId, selected.value.id, confirmationInput.value, options);
  const removedName = selected.value.name;
  const job = { jobId: accepted.jobId, title: 'Helm Application 제거', detail: `${selected.value.namespace}/${removedName}`, type: 'HELM_UNINSTALL' };
  uninstallOpen.value = false;
  message.value = 'Uninstall 작업을 시작했습니다. Job Center에서 진행 상태를 확인할 수 있습니다.';
  void jobs.trackJob(job).then(async (result) => {
    // 삭제 성공 시 서버에서 Application graph가 사라지므로 목록과 선택 상세를 즉시 동기화한다.
    message.value = result.status === 'SUCCEEDED'
      ? `${removedName} Application을 제거했습니다.`
      : 'Uninstall 작업이 실패했습니다. Job Center에서 원인을 확인하세요.';
    await load();
  }).catch(() => { message.value = 'Uninstall 작업이 실패했습니다. Job Center에서 원인을 확인하세요.'; });
  await load();
}

/** startUpgrade 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function startUpgrade(): void {
  if (!selected.value) return;
  router.push({ path: '/applications/library', query: {
    upgradeApplicationId: selected.value.id, clusterId: selected.value.clusterId,
    namespace: selected.value.namespace, releaseName: selected.value.helmReleaseName || selected.value.name,
  } });
}
</script>

<template>
  <section class="page delivery-page">
    <header class="delivery-hero">
      <div>
        <span class="delivery-eyebrow">APPLICATION DELIVERY</span>
        <h1>Deployed Applications</h1>
        <p>설치가 시작된 순간부터 완료·실패 이후까지 Helm Release의 상태, 접근 경로와 변경 이력을 한곳에서 운영합니다.</p>
      </div>
      <button class="primary-button large" type="button" @click="startOpen = true">
        <i class="pi pi-plus"></i> Application 배포
      </button>
    </header>
    <ApplicationDeliveryNav />
    <div class="delivery-lifecycle" aria-label="Application 배포 흐름">
      <span><i class="pi pi-sliders-h"></i> 배포 Wizard</span><i class="pi pi-angle-right"></i>
      <span><i class="pi pi-clock"></i> Job Center</span><i class="pi pi-angle-right"></i>
      <span class="active"><i class="pi pi-box"></i> Deployed Applications</span><i class="pi pi-angle-right"></i>
      <span><i class="pi pi-chart-line"></i> 상세 운영</span>
    </div>
    <div v-if="message" class="delivery-notice">{{ message }}</div>
    <div v-if="loading" class="delivery-empty">
      <i class="pi pi-spin pi-spinner"></i><p>Application 상태를 불러오는 중입니다.</p>
    </div>
    <div v-else-if="applications.length" class="applications-workspace">
      <section class="application-summary" aria-label="Application 상태 요약">
        <article><span>Healthy</span><strong class="success">{{ applicationSummary.healthy }}</strong><small>정상 실행 중</small></article>
        <article><span>Progressing</span><strong class="progress">{{ applicationSummary.progressing }}</strong><small>설치·변경 진행 중</small></article>
        <article><span>Attention</span><strong class="danger">{{ applicationSummary.failed }}</strong><small>확인이 필요한 상태</small></article>
        <article><span>Inactive</span><strong>{{ applicationSummary.inactive }}</strong><small>삭제됨·확인 불가</small></article>
      </section>

      <div class="application-operation-layout">
        <section class="application-list-panel">
          <header class="application-list-toolbar">
            <label class="application-search-field">
              <i class="pi pi-search"></i>
              <span class="visually-hidden">Application 검색</span>
              <input v-model="applicationSearch" type="search" placeholder="Application, Cluster, Namespace 검색" />
            </label>
            <label class="application-filter-field">
              <span class="visually-hidden">상태 필터</span>
              <select v-model="statusFilter">
                <option value="ALL">모든 상태</option>
                <option value="RUNNING">Running</option>
                <option value="DEPLOY_REQUESTED">Deploy requested</option>
                <option value="FAILED">Failed</option>
                <option value="DEGRADED">Degraded</option>
                <option value="UNINSTALLED">Uninstalled</option>
              </select>
            </label>
          </header>

          <div class="application-table" role="table" aria-label="배포된 Application">
            <div class="application-table-header" role="row">
              <span role="columnheader">Application / Release</span>
              <span role="columnheader">Cluster / Namespace</span>
              <span role="columnheader">Helm Chart</span>
              <span role="columnheader">상태</span>
              <span role="columnheader">Revision</span>
            </div>
            <button
              v-for="application in filteredApplications"
              :key="application.id"
              type="button"
              class="application-table-row"
              :class="{ selected: selectedId === application.id }"
              role="row"
              @click="selectApplication(application)"
            >
              <span class="application-table-title" role="cell">
                <span class="application-mark"><i class="pi pi-box"></i></span>
                <span><strong>{{ application.name }}</strong><small>{{ application.helmReleaseName || application.name }}</small></span>
              </span>
              <span class="application-table-target" role="cell">
                <strong>{{ clusterNames[application.clusterId || ''] || application.clusterId }}</strong>
                <small>{{ application.namespace || 'default' }}</small>
              </span>
              <span class="application-table-chart" role="cell">
                <strong>{{ chartDetails(application)?.name || application.helmChart || '-' }}</strong>
                <small v-if="chartDetails(application)">Chart {{ chartDetails(application)?.chartVersion }}<template v-if="chartDetails(application)?.appVersion"> · App {{ chartDetails(application)?.appVersion }}</template></small>
              </span>
              <span role="cell"><span class="status-dot" :class="statusTone(application.status)">{{ application.status || 'UNKNOWN' }}</span></span>
              <span role="cell">{{ application.currentReleaseRevision || '-' }}</span>
            </button>
            <div v-if="!filteredApplications.length" class="application-filter-empty">
              <i class="pi pi-filter-slash"></i><span>조건에 맞는 Application이 없습니다.</span>
            </div>
          </div>
        </section>

        <aside v-if="selected" class="application-inspector">
          <header>
            <div>
              <span class="delivery-eyebrow">SELECTED APPLICATION</span>
              <h2>{{ selected.name }}</h2>
              <p>{{ selected.namespace }} · {{ clusterNames[selected.clusterId || ''] }}</p>
            </div>
            <span class="status-dot" :class="statusTone(selected.status)">{{ selected.status }}</span>
          </header>
          <div class="inspector-actions">
            <button class="secondary-button" type="button" @click="router.push({ path: '/analysis', query: { mode: 'application', applicationId: selected.id, clusterId: selected.clusterId } })"><i class="pi pi-sparkles"></i> AI Analysis</button>
            <button class="secondary-button" type="button" :disabled="!canUpgrade" @click="startUpgrade"><i class="pi pi-arrow-up-right"></i> Upgrade</button>
            <button class="secondary-button" type="button" :disabled="!canUpgrade || releases.filter(item => item.revision !== selected?.currentReleaseRevision).length === 0" @click="openRollback"><i class="pi pi-history"></i> Rollback</button>
            <button class="danger-ghost-button" type="button" :disabled="!canUninstall" @click="openUninstall()"><i class="pi pi-trash"></i> Uninstall</button>
            <button v-if="selected.status === 'FAILED'" class="secondary-button" type="button" @click="openUninstall(true)"><i class="pi pi-refresh"></i> Cleanup 재시도</button>
          </div>
          <section class="application-package-panel">
            <h3>배포 Helm 정보</h3>
            <dl v-if="chartDetails(selected)" class="application-package-grid">
              <div><dt>Chart</dt><dd>{{ chartDetails(selected)?.name }}</dd></div>
              <div><dt>Package</dt><dd>{{ chartDetails(selected)?.packageName }}</dd></div>
              <div><dt>Chart Version</dt><dd>{{ chartDetails(selected)?.chartVersion }}</dd></div>
              <div><dt>App Version</dt><dd>{{ chartDetails(selected)?.appVersion || '제공되지 않음' }}</dd></div>
              <div class="wide"><dt>제공사 / Source</dt><dd>{{ chartDetails(selected)?.provider }} · {{ chartDetails(selected)?.sourceType }}</dd></div>
            </dl>
            <div v-else class="mini-empty">Chart Library 메타데이터를 찾지 못했습니다. 참조 ID: {{ selected.chartVersionId || selected.helmChart || '-' }}</div>
          </section>
          <section v-if="runtime">
            <h3>Runtime</h3>
            <div class="runtime-summary">
              <span><b>{{ runtime.readyPods }} / {{ runtime.totalPods }}</b> Ready Pods</span>
              <span><b>{{ runtime.restarts }}</b> Restarts</span>
            </div>
            <div class="runtime-list">
              <article v-for="workload in runtime.workloads" :key="`${workload.kind}/${workload.name}`">
                <strong>{{ workload.kind }}/{{ workload.name }}</strong>
                <span class="status-dot" :class="statusTone(workload.status)">{{ workload.ready }}/{{ workload.desired }} · {{ workload.status }}</span>
              </article>
              <article v-for="endpoint in runtime.endpoints" :key="endpoint.url" class="runtime-endpoint">
                <div class="runtime-endpoint-heading"><strong>{{ endpoint.type }} · {{ endpoint.name }}</strong><span class="status-dot" :class="statusTone(endpoint.status)">{{ endpoint.status }}</span></div>
                <div class="runtime-endpoint-address">
                  <span><small>접근 범위</small><b>{{ endpointScope(endpoint.accessScope) }}</b></span>
                  <span><small>IP / Host</small><b>{{ endpoint.address || '할당 대기' }}</b></span>
                  <span><small>Service Port</small><b>{{ endpoint.port || '-' }}</b></span>
                  <span v-if="endpoint.targetPort"><small>Target Port</small><b>{{ endpoint.targetPort }}</b></span>
                  <span v-if="endpoint.nodePort"><small>Node Port</small><b>{{ endpoint.nodePort }}</b></span>
                </div>
                <a v-if="isBrowsableEndpoint(endpoint)" :href="endpoint.url" target="_blank" rel="noreferrer">{{ endpoint.url }} <i class="pi pi-external-link"></i></a>
                <span v-else class="runtime-endpoint-value">{{ endpoint.url }}<small>{{ endpoint.status === 'DEGRADED' ? '사용할 수 없는 접근 경로' : '전용 Client 또는 port-forward로 접속' }}</small></span>
              </article>
            </div>
          </section>
          <section>
            <h3>최근 작업</h3>
            <div v-if="operations.length" class="operation-timeline">
              <article v-for="operation in operations" :key="operation.id">
                <span class="timeline-dot" :class="statusTone(operation.status)"></span>
                <div><strong>{{ operation.type }} · {{ operation.status }}</strong><p>{{ operation.outputSummary || operation.errorMessage || 'Job Center에서 실행 중' }}</p><small>{{ new Date(operation.requestedAt).toLocaleString() }} · {{ operation.requestedBy }}</small></div>
              </article>
            </div>
            <div v-else class="mini-empty">기록된 Helm 작업이 없습니다.</div>
          </section>
        </aside>
      </div>
    </div>
    <div v-else class="delivery-empty">
      <i class="pi pi-box"></i><h2>배포된 Application이 없습니다</h2><p>보유한 Chart Library에서 시작하거나 Artifact Hub에서 Chart를 찾아보세요.</p><button class="primary-button" type="button" @click="startOpen = true">첫 Application 배포</button>
    </div>
    <DeploymentStartDialog :open="startOpen" @close="startOpen = false" />
    <div v-if="uninstallOpen" class="delivery-modal-backdrop" @click.self="uninstallOpen = false">
      <section class="delivery-modal narrow" role="dialog" aria-modal="true"><header><div><span class="delivery-eyebrow danger">{{ cleanupRetry ? 'CLEANUP RETRY' : 'DESTRUCTIVE ACTION' }}</span><h2>{{ selected?.name }} {{ cleanupRetry ? 'Cleanup 재시도' : 'Uninstall' }}</h2><p>{{ impactSummary }}</p></div><button class="icon-button" type="button" aria-label="닫기" @click="uninstallOpen = false"><i class="pi pi-times"></i></button></header><div class="uninstall-policy"><label><input v-model="preservePvcs" type="checkbox" /> PVC 보존</label><label><input v-model="preserveDns" type="checkbox" /> KlueOps DNS 경로 보존</label><label><input v-model="preserveTls" type="checkbox" /> TLS Secret 보존</label><small>보존하지 않은 release label 범위의 자원만 정리합니다. Namespace와 Chart Library는 항상 유지합니다.</small></div><label class="exact-confirm-field">정확 확인 문구<code>{{ confirmationText }}</code><input v-model="confirmationInput" autocomplete="off" /></label><footer><button class="secondary-button" type="button" @click="uninstallOpen = false">취소</button><button class="danger-button" type="button" :disabled="confirmationInput.trim() !== confirmationText" @click="uninstall">{{ cleanupRetry ? 'Cleanup 재시도' : 'Uninstall 시작' }}</button></footer></section>
    </div>
    <div v-if="rollbackOpen" class="delivery-modal-backdrop" @click.self="rollbackOpen = false">
      <section class="delivery-modal narrow"><header><div><span class="delivery-eyebrow">CONTROLLED ROLLBACK</span><h2>{{ selected?.name }} Rollback</h2><p>{{ impactSummary }}</p></div><button class="icon-button" type="button" @click="rollbackOpen = false"><i class="pi pi-times"></i></button></header><label class="delivery-field">Target revision<select v-model.number="rollbackRevision" @change="refreshRollbackConfirmation"><option v-for="release in releases" :key="release.id" :value="release.revision">Revision {{ release.revision }} · {{ release.status }}</option></select></label><label class="exact-confirm-field">정확 확인 문구<code>{{ confirmationText }}</code><input v-model="confirmationInput" autocomplete="off" /></label><footer><button class="secondary-button" type="button" @click="rollbackOpen = false">취소</button><button class="danger-button" type="button" :disabled="confirmationInput.trim() !== confirmationText" @click="rollback">Rollback 시작</button></footer></section>
    </div>
  </section>
</template>
