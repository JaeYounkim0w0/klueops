<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ApiError, api, type ClusterResponse, type IncidentResponse } from '@/api/client';
import { formatTimestamp } from '@/utils/time';
import { useAuthStore } from '@/stores/auth';

const router = useRouter();
const auth = useAuthStore();
const incidents = ref<IncidentResponse[]>([]);
const clusters = ref<ClusterResponse[]>([]);
const loading = ref(true);
const reconciling = ref(false);
const error = ref('');
const clusterId = ref('');
const namespace = ref('');
const state = ref('');
const severity = ref('');
const createOpen = ref(false);
const creating = ref(false);
const manual = ref({ clusterId: '', namespace: 'default', resourceKind: '', resourceName: '', severity: 'MEDIUM', title: '', summary: '', nextAction: '' });
const canOperate = computed(() => auth.hasCapability('operation:execute'));

const openCount = computed(() => incidents.value.filter((item) => item.state !== 'RESOLVED').length);
const criticalCount = computed(() => incidents.value.filter((item) => item.state !== 'RESOLVED' && ['CRITICAL', 'HIGH'].includes(item.severity)).length);
const recurringCount = computed(() => incidents.value.filter((item) => item.occurrenceCount > 1 || item.reopenCount > 0).length);

/** load 처리 결과를 조회해 반환한다. */
async function load() {
  loading.value = true;
  error.value = '';
  try {
    incidents.value = await api.listIncidents({
      clusterId: clusterId.value || undefined,
      namespace: namespace.value.trim() || undefined,
      state: state.value || undefined,
      severity: severity.value || undefined
    });
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : 'Incident 목록을 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

/** reconcile 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function reconcile() {
  reconciling.value = true;
  error.value = '';
  try {
    await api.reconcileOperations();
    await load();
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : 'Incident 근거 갱신에 실패했습니다.';
  } finally {
    reconciling.value = false;
  }
}

/** createIncident 처리에 필요한 데이터를 생성하거나 저장한다. */
async function createIncident() {
  creating.value = true;
  error.value = '';
  try {
    const created = await api.createManualIncident({ ...manual.value, resourceKind: manual.value.resourceKind || undefined, resourceName: manual.value.resourceName || undefined });
    createOpen.value = false;
    await router.push(`/incidents/${created.id}`);
  } catch (cause) { error.value = cause instanceof ApiError ? cause.message : 'Incident를 생성하지 못했습니다.'; }
  finally { creating.value = false; }
}

/** severityClass 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function severityClass(value?: string) {
  return String(value ?? '').toLowerCase();
}

watch([clusterId, state, severity], load);
onMounted(async () => {
  try { clusters.value = await api.listClusters(); } catch { clusters.value = []; }
  await load();
});
</script>

<template>
  <section class="page operations-list-page">
    <header class="page-header operations-page-header">
      <div><span class="page-eyebrow">INCIDENT MANAGEMENT</span><h1>Incidents</h1><p>분석 세션을 넘어 장애의 발생, 검증, 조치, 재발을 하나의 이력으로 관리합니다.</p></div>
      <div class="page-header-actions"><button v-if="canOperate" class="secondary-button" type="button" @click="createOpen = true"><i class="pi pi-plus"></i><span>수동 Incident</span></button><button class="primary-button" type="button" :disabled="reconciling" @click="reconcile"><i class="pi pi-refresh" :class="{ 'pi-spin': reconciling }"></i><span>{{ reconciling ? '근거 수집 중' : '근거 갱신' }}</span></button></div>
    </header>

    <div class="operations-summary-line">
      <span><strong>{{ openCount }}</strong> Open</span>
      <span class="danger"><strong>{{ criticalCount }}</strong> High priority</span>
      <span><strong>{{ recurringCount }}</strong> Recurring</span>
    </div>

    <section class="operations-filter-bar" aria-label="Incident 필터">
      <label><span>Cluster</span><select v-model="clusterId"><option value="">전체 클러스터</option><option v-for="cluster in clusters" :key="cluster.id" :value="cluster.id">{{ cluster.name }}</option></select></label>
      <label><span>Namespace</span><input v-model="namespace" type="search" placeholder="namespace" @keyup.enter="load"></label>
      <label><span>State</span><select v-model="state"><option value="">전체 상태</option><option value="OPEN">Open</option><option value="ACKNOWLEDGED">Acknowledged</option><option value="INVESTIGATING">Investigating</option><option value="MITIGATING">Mitigating</option><option value="MONITORING">Monitoring</option><option value="RESOLVED">Resolved</option><option value="REOPENED">Reopened</option></select></label>
      <label><span>Severity</span><select v-model="severity"><option value="">전체 심각도</option><option>CRITICAL</option><option>HIGH</option><option>MEDIUM</option><option>LOW</option></select></label>
      <button class="icon-button" type="button" title="필터 적용" aria-label="필터 적용" @click="load"><i class="pi pi-search"></i></button>
    </section>

    <div v-if="error" class="inline-feedback error"><i class="pi pi-exclamation-triangle"></i><span>{{ error }}</span></div>
    <div v-if="loading" class="operations-empty-state"><i class="pi pi-spin pi-spinner"></i><span>Incident를 불러오고 있습니다.</span></div>
    <div v-else-if="!incidents.length" class="operations-empty-state success"><i class="pi pi-check-circle"></i><div><strong>조건에 맞는 Incident가 없습니다.</strong><span>근거 갱신으로 최신 분석과 Kubernetes Warning Event를 확인할 수 있습니다.</span></div></div>
    <div v-else class="incident-list">
      <button v-for="incident in incidents" :key="incident.id" type="button" class="incident-list-row" @click="router.push(`/incidents/${incident.id}`)">
        <span class="incident-severity-rail" :class="severityClass(incident.severity)"></span>
        <span class="incident-state-column"><span class="status-pill" :class="severityClass(incident.severity)">{{ incident.severity }}</span><small>{{ incident.state }}</small></span>
        <span class="incident-main"><strong class="operator-content-title">{{ incident.title }}</strong><span>{{ incident.summary || '상세 근거를 확인하세요.' }}</span><small>{{ incident.clusterName }}<template v-if="incident.namespace"> / {{ incident.namespace }}</template><template v-if="incident.resourceKind"> · {{ incident.resourceKind }}/{{ incident.resourceName }}</template></small></span>
        <span class="incident-count"><strong>{{ incident.occurrenceCount }}</strong><small>occurrences</small><em v-if="incident.reopenCount">재발 {{ incident.reopenCount }}</em></span>
        <time>{{ formatTimestamp(incident.lastDetectedAt) }}</time>
        <i class="pi pi-chevron-right"></i>
      </button>
    </div>
    <div v-if="createOpen" class="modal-backdrop" @click.self="createOpen = false"><section class="modal-card incident-create-modal" role="dialog" aria-modal="true"><header><div><span class="page-eyebrow">OPERATOR REPORTED</span><h2>수동 Incident 생성</h2><p>자동 감지 전이라도 운영자가 확인한 장애를 협업 대상으로 등록합니다.</p></div><button class="icon-button" type="button" title="닫기" aria-label="닫기" @click="createOpen = false"><i class="pi pi-times"></i></button></header><form class="incident-create-form" @submit.prevent="createIncident"><div class="incident-create-grid"><label><span>Cluster</span><select v-model="manual.clusterId" required><option value="" disabled>선택</option><option v-for="cluster in clusters" :key="cluster.id" :value="cluster.id">{{ cluster.name }}</option></select></label><label><span>Namespace</span><input v-model="manual.namespace" maxlength="255"></label><label><span>Severity</span><select v-model="manual.severity"><option>CRITICAL</option><option>HIGH</option><option>MEDIUM</option><option>LOW</option></select></label><label><span>Resource kind</span><input v-model="manual.resourceKind" maxlength="100" placeholder="Pod"></label><label><span>Resource name</span><input v-model="manual.resourceName" maxlength="255"></label></div><label><span>Title</span><input v-model="manual.title" required maxlength="500"></label><label><span>확인한 내용</span><textarea v-model="manual.summary" rows="4" maxlength="4000"></textarea></label><label><span>다음 행동</span><textarea v-model="manual.nextAction" rows="3" maxlength="2000"></textarea></label><div class="modal-actions"><button class="secondary-button" type="button" @click="createOpen = false">취소</button><button class="primary-button" type="submit" :disabled="creating"><i class="pi pi-plus"></i><span>{{ creating ? '생성 중' : 'Incident 생성' }}</span></button></div></form></section></div>
  </section>
</template>
