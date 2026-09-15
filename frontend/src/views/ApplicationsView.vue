<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import ApplicationDeliveryNav from '@/components/application/ApplicationDeliveryNav.vue';
import DeploymentStartDialog from '@/components/application/DeploymentStartDialog.vue';
import { api, type ApplicationResponse, type ClusterResponse, type ReleaseOperationResponse } from '@/api/client';
import { useJobCenterStore } from '@/stores/jobCenter';
import { useTenancyStore } from '@/stores/tenancy';

const router = useRouter();
const tenancy = useTenancyStore();
const jobs = useJobCenterStore();
const applications = ref<ApplicationResponse[]>([]);
const clusters = ref<ClusterResponse[]>([]);
const selectedId = ref('');
const operations = ref<ReleaseOperationResponse[]>([]);
const loading = ref(true);
const startOpen = ref(false);
const uninstallOpen = ref(false);
const confirmationText = ref('');
const confirmationInput = ref('');
const impactSummary = ref('');
const message = ref('');

const selected = computed(() => applications.value.find((item) => item.id === selectedId.value) ?? null);
const clusterNames = computed(() => Object.fromEntries(clusters.value.map((cluster) => [cluster.id, cluster.name])));

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
    selectedId.value = applications.value[0]?.id || '';
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
  operations.value = selectedId.value ? await api.listReleaseOperations(tenancy.currentTenantId, selectedId.value).catch(() => []) : [];
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
      <aside v-if="selected" class="application-inspector"><header><div><span class="delivery-eyebrow">SELECTED APPLICATION</span><h2>{{ selected.name }}</h2><p>{{ selected.namespace }} · {{ clusterNames[selected.clusterId || ''] }}</p></div><span class="status-dot" :class="statusTone(selected.status)">{{ selected.status }}</span></header><div class="inspector-actions"><button class="secondary-button" type="button" @click="router.push({ path: '/analysis', query: { mode: 'application', applicationId: selected.id, clusterId: selected.clusterId } })"><i class="pi pi-sparkles"></i> AI Analysis</button><button class="danger-ghost-button" type="button" @click="openUninstall"><i class="pi pi-trash"></i> Uninstall</button></div><section><h3>최근 작업</h3><div v-if="operations.length" class="operation-timeline"><article v-for="operation in operations" :key="operation.id"><span class="timeline-dot" :class="statusTone(operation.status)"></span><div><strong>{{ operation.type }} · {{ operation.status }}</strong><p>{{ operation.outputSummary || operation.errorMessage || 'Job Center에서 실행 중' }}</p><small>{{ new Date(operation.requestedAt).toLocaleString() }} · {{ operation.requestedBy }}</small></div></article></div><div v-else class="mini-empty">기록된 Helm 작업이 없습니다.</div></section></aside>
    </div>
    <div v-else class="delivery-empty"><i class="pi pi-box"></i><h2>배포된 Application이 없습니다</h2><p>보유한 Chart Library에서 시작하거나 Artifact Hub에서 Chart를 찾아보세요.</p><button class="primary-button" type="button" @click="startOpen = true">첫 Application 배포</button></div>
    <DeploymentStartDialog :open="startOpen" @close="startOpen = false" />
    <div v-if="uninstallOpen" class="delivery-modal-backdrop" @click.self="uninstallOpen = false"><section class="delivery-modal narrow"><header><div><span class="delivery-eyebrow danger">DESTRUCTIVE ACTION</span><h2>{{ selected?.name }} Uninstall</h2><p>{{ impactSummary }}</p></div><button class="icon-button" type="button" @click="uninstallOpen = false"><i class="pi pi-times"></i></button></header><label class="exact-confirm-field">정확 확인 문구<code>{{ confirmationText }}</code><input v-model="confirmationInput" autocomplete="off" /></label><footer><button class="secondary-button" type="button" @click="uninstallOpen = false">취소</button><button class="danger-button" type="button" :disabled="confirmationInput.trim() !== confirmationText" @click="uninstall">Uninstall 시작</button></footer></section></div>
  </section>
</template>
