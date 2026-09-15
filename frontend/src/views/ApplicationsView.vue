<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import ApplicationDeliveryNav from '@/components/application/ApplicationDeliveryNav.vue';
import DeploymentStartDialog from '@/components/application/DeploymentStartDialog.vue';
import { api, type ApplicationResponse, type ApplicationReleaseResponse, type ApplicationRuntimeResponse, type ClusterResponse, type ReleaseOperationResponse } from '@/api/client';
import { useJobCenterStore } from '@/stores/jobCenter';
import { useTenancyStore } from '@/stores/tenancy';

const router = useRouter();
const route = useRoute();
const tenancy = useTenancyStore();
const jobs = useJobCenterStore();
const applications = ref<ApplicationResponse[]>([]);
const clusters = ref<ClusterResponse[]>([]);
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
const message = ref('');

const selected = computed(() => applications.value.find((item) => item.id === selectedId.value) ?? null);
const clusterNames = computed(() => Object.fromEntries(clusters.value.map((cluster) => [cluster.id, cluster.name])));
const canUpgrade = computed(() => ['RUNNING', 'RUNNING_ENDPOINT_DEGRADED', 'FAILED'].includes(selected.value?.status || ''));
const canUninstall = computed(() => !['UNINSTALLED', 'UNINSTALLING'].includes(selected.value?.status || ''));

onMounted(load);

async function load(): Promise<void> {
  loading.value = true;
  try {
    await tenancy.load();
    clusters.value = await api.listClusters({ tenantId: tenancy.currentTenantId, workspaceId: tenancy.currentWorkspaceId });
    const allowed = new Set(clusters.value.map((cluster) => cluster.id));
    // 구형 목록 API의 응답도 선택 Tenant의 Cluster로 한 번 더 제한해 잘못된 화면 노출을 막는다.
    applications.value = (await api.listTenantApplications(tenancy.currentTenantId))
      .filter((application) => application.clusterId && allowed.has(application.clusterId));
    const requestedId = typeof route.query.applicationId === 'string' ? route.query.applicationId : '';
    // 새 배포 직후 deep-link가 가리키는 Application을 우선 선택한다.
    selectedId.value = applications.value.some((item) => item.id === requestedId)
      ? requestedId : applications.value[0]?.id || '';
    await loadOperations();
  } catch (error) { message.value = error instanceof Error ? error.message : 'Applications를 불러오지 못했습니다.'; }
  finally { loading.value = false; }
}

async function selectApplication(application: ApplicationResponse): Promise<void> {
  selectedId.value = application.id;
  await router.replace({ path: '/applications', query: { applicationId: application.id } });
  await loadOperations();
}

async function loadOperations(): Promise<void> {
  if (!selectedId.value) { operations.value = []; releases.value = []; runtime.value = null; return; }
  [operations.value, releases.value, runtime.value] = await Promise.all([
    api.listReleaseOperations(tenancy.currentTenantId, selectedId.value).catch(() => []),
    api.listApplicationReleases(tenancy.currentTenantId, selectedId.value).catch(() => []),
    api.getApplicationRuntime(tenancy.currentTenantId, selectedId.value).catch(() => null),
  ]);
}

async function openRollback(): Promise<void> {
  if (!selected.value || !releases.value.length) return;
  rollbackRevision.value = releases.value.find((item) => item.revision !== selected.value?.currentReleaseRevision)?.revision
    || releases.value[releases.value.length - 1].revision;
  await refreshRollbackConfirmation();
  rollbackOpen.value = true;
}

async function refreshRollbackConfirmation(): Promise<void> {
  if (!selected.value) return;
  const preview = await api.getRollbackConfirmation(tenancy.currentTenantId, selected.value.id, rollbackRevision.value);
  confirmationText.value = preview.confirmationText; impactSummary.value = preview.impactSummary;
  confirmationInput.value = '';
}

async function rollback(): Promise<void> {
  if (!selected.value || confirmationInput.value.trim() !== confirmationText.value) return;
  const accepted = await api.rollbackHelmApplication(tenancy.currentTenantId, selected.value.id, rollbackRevision.value, confirmationInput.value);
  jobs.registerJob({ jobId: accepted.jobId, title: 'Helm Application Rollback', detail: `revision ${rollbackRevision.value}`, type: 'HELM_ROLLBACK' });
  rollbackOpen.value = false; message.value = 'Rollback 작업을 시작했습니다.'; await load();
}

function statusTone(status?: string): string {
  if (status === 'RUNNING' || status === 'SUCCEEDED') return 'success';
  if (status === 'FAILED' || status === 'DEGRADED') return 'danger';
  if (status?.includes('ING') || status === 'DEPLOY_REQUESTED' || status === 'PENDING') return 'progress';
  return 'neutral';
}

async function openUninstall(): Promise<void> {
  if (!selected.value) return;
  const preview = await api.getUninstallConfirmation(tenancy.currentTenantId, selected.value.id);
  confirmationText.value = preview.confirmationText;
  impactSummary.value = preview.impactSummary;
  confirmationInput.value = '';
  uninstallOpen.value = true;
}

async function uninstall(): Promise<void> {
  if (!selected.value || confirmationInput.value.trim() !== confirmationText.value) return;
  const accepted = await api.uninstallHelmApplication(tenancy.currentTenantId, selected.value.id, confirmationInput.value);
  jobs.registerJob({ jobId: accepted.jobId, title: 'Helm Application 제거', detail: `${selected.value.namespace}/${selected.value.name}`, type: 'HELM_UNINSTALL' });
  uninstallOpen.value = false;
  message.value = 'Uninstall 작업을 시작했습니다. Job Center에서 진행 상태를 확인할 수 있습니다.';
  await load();
}

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
      <div><span class="delivery-eyebrow">APPLICATION OPERATIONS</span><h1>Applications</h1><p>KlueOps가 배포한 Helm Release의 상태, 접근 경로와 변경 이력을 한곳에서 운영합니다.</p></div>
      <button class="primary-button large" type="button" @click="startOpen = true"><i class="pi pi-plus"></i> Application 배포</button>
    </header>
    <ApplicationDeliveryNav />
    <div class="delivery-lifecycle"><span><i class="pi pi-sliders-h"></i> 배포 Wizard</span><i class="pi pi-angle-right"></i><span><i class="pi pi-clock"></i> Job Center</span><i class="pi pi-angle-right"></i><span class="active"><i class="pi pi-box"></i> Deployed Applications</span><i class="pi pi-angle-right"></i><span><i class="pi pi-chart-line"></i> 상세 운영</span></div>
    <div v-if="message" class="delivery-notice">{{ message }}</div>
    <div v-if="loading" class="delivery-empty"><i class="pi pi-spin pi-spinner"></i><p>Application 상태를 불러오는 중입니다.</p></div>
    <div v-else-if="applications.length" class="applications-workspace">
      <div class="application-grid">
        <button v-for="application in applications" :key="application.id" type="button" class="application-card" :class="{ selected: selectedId === application.id }" @click="selectApplication(application)">
          <header><span class="application-mark"><i class="pi pi-box"></i></span><span class="status-dot" :class="statusTone(application.status)">{{ application.status || 'UNKNOWN' }}</span></header>
          <h2>{{ application.name }}</h2><p>{{ clusterNames[application.clusterId || ''] || application.clusterId }} · {{ application.namespace || 'default' }}</p>
          <dl><div><dt>배포 방식</dt><dd>{{ application.deploymentType }}</dd></div><div><dt>생성</dt><dd>{{ application.createdAt ? new Date(application.createdAt).toLocaleDateString() : '-' }}</dd></div></dl>
        </button>
      </div>
      <aside v-if="selected" class="application-inspector"><header><div><span class="delivery-eyebrow">SELECTED APPLICATION</span><h2>{{ selected.name }}</h2><p>{{ selected.namespace }} · {{ clusterNames[selected.clusterId || ''] }}</p></div><span class="status-dot" :class="statusTone(selected.status)">{{ selected.status }}</span></header><div class="inspector-actions"><button class="secondary-button" type="button" @click="router.push({ path: '/analysis', query: { mode: 'application', applicationId: selected.id, clusterId: selected.clusterId } })"><i class="pi pi-sparkles"></i> AI Analysis</button><button class="secondary-button" type="button" :disabled="!canUpgrade" @click="startUpgrade"><i class="pi pi-arrow-up-right"></i> Upgrade</button><button class="secondary-button" type="button" :disabled="!canUpgrade || releases.filter(item => item.revision !== selected?.currentReleaseRevision).length === 0" @click="openRollback"><i class="pi pi-history"></i> Rollback</button><button class="danger-ghost-button" type="button" :disabled="!canUninstall" @click="openUninstall"><i class="pi pi-trash"></i> Uninstall</button></div><section v-if="runtime"><h3>Runtime</h3><div class="runtime-summary"><span><b>{{ runtime.readyPods }} / {{ runtime.totalPods }}</b> Ready Pods</span><span><b>{{ runtime.restarts }}</b> Restarts</span></div><div class="runtime-list"><article v-for="workload in runtime.workloads" :key="`${workload.kind}/${workload.name}`"><strong>{{ workload.kind }}/{{ workload.name }}</strong><span class="status-dot" :class="statusTone(workload.status)">{{ workload.ready }}/{{ workload.desired }} · {{ workload.status }}</span></article><article v-for="endpoint in runtime.endpoints" :key="endpoint.url"><strong>{{ endpoint.type }} · {{ endpoint.name }}</strong><a :href="endpoint.url" target="_blank" rel="noreferrer">{{ endpoint.url }} <i class="pi pi-external-link"></i></a></article></div></section><section><h3>최근 작업</h3><div v-if="operations.length" class="operation-timeline"><article v-for="operation in operations" :key="operation.id"><span class="timeline-dot" :class="statusTone(operation.status)"></span><div><strong>{{ operation.type }} · {{ operation.status }}</strong><p>{{ operation.outputSummary || operation.errorMessage || 'Job Center에서 실행 중' }}</p><small>{{ new Date(operation.requestedAt).toLocaleString() }} · {{ operation.requestedBy }}</small></div></article></div><div v-else class="mini-empty">기록된 Helm 작업이 없습니다.</div></section></aside>
    </div>
    <div v-else class="delivery-empty"><i class="pi pi-box"></i><h2>배포된 Application이 없습니다</h2><p>보유한 Chart Library에서 시작하거나 Artifact Hub에서 Chart를 찾아보세요.</p><button class="primary-button" type="button" @click="startOpen = true">첫 Application 배포</button></div>
    <DeploymentStartDialog :open="startOpen" @close="startOpen = false" />
    <div v-if="uninstallOpen" class="delivery-modal-backdrop" @click.self="uninstallOpen = false"><section class="delivery-modal narrow"><header><div><span class="delivery-eyebrow danger">DESTRUCTIVE ACTION</span><h2>{{ selected?.name }} Uninstall</h2><p>{{ impactSummary }}</p></div><button class="icon-button" type="button" @click="uninstallOpen = false"><i class="pi pi-times"></i></button></header><label class="exact-confirm-field">정확 확인 문구<code>{{ confirmationText }}</code><input v-model="confirmationInput" autocomplete="off" /></label><footer><button class="secondary-button" type="button" @click="uninstallOpen = false">취소</button><button class="danger-button" type="button" :disabled="confirmationInput.trim() !== confirmationText" @click="uninstall">Uninstall 시작</button></footer></section></div>
    <div v-if="rollbackOpen" class="delivery-modal-backdrop" @click.self="rollbackOpen = false"><section class="delivery-modal narrow"><header><div><span class="delivery-eyebrow">CONTROLLED ROLLBACK</span><h2>{{ selected?.name }} Rollback</h2><p>{{ impactSummary }}</p></div><button class="icon-button" type="button" @click="rollbackOpen = false"><i class="pi pi-times"></i></button></header><label class="delivery-field">Target revision<select v-model.number="rollbackRevision" @change="refreshRollbackConfirmation"><option v-for="release in releases" :key="release.id" :value="release.revision">Revision {{ release.revision }} · {{ release.status }}</option></select></label><label class="exact-confirm-field">정확 확인 문구<code>{{ confirmationText }}</code><input v-model="confirmationInput" autocomplete="off" /></label><footer><button class="secondary-button" type="button" @click="rollbackOpen = false">취소</button><button class="danger-button" type="button" :disabled="confirmationInput.trim() !== confirmationText" @click="rollback">Rollback 시작</button></footer></section></div>
  </section>
</template>
