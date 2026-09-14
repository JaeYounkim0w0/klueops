<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute, useRouter } from 'vue-router';
import {
  api,
  ResourceLogStreamError,
  streamClusterResourceLogs,
  type AnalysisCommandExecutionResponse,
  type AnalysisResponse,
  type ClusterConnectionTestResponse,
  type ClusterCredentialResponse,
  type ClusterReadinessResponse,
  type ClusterResourcePageResponse,
  type ClusterResourceLogResponse,
  type ClusterResourceLogTargetsResponse,
  type ClusterResponse,
  type ClusterSyncSettingsResponse,
  type ClusterSyncStatusResponse,
  type KubernetesEventSnapshotResponse,
  type KubernetesResourceManifestResponse,
  type KubernetesNamespaceResponse,
  type KubernetesNodeResponse,
  type KubernetesResourceSnapshotResponse,
  type ResourceContextResponse
} from '@/api/client';
import { useJobCenterStore } from '@/stores/jobCenter';
import { useAuthStore } from '@/stores/auth';

const route = useRoute();
const router = useRouter();
const jobCenter = useJobCenterStore();
const auth = useAuthStore();
const { t } = useI18n();

const clusterId = computed(() => String(route.params.clusterId || ''));
const cluster = ref<ClusterResponse | null>(null);
const credential = ref<ClusterCredentialResponse | null>(null);
const syncSettings = ref<ClusterSyncSettingsResponse | null>(null);
const syncStatus = ref<ClusterSyncStatusResponse | null>(null);
const namespaces = ref<KubernetesNamespaceResponse[]>([]);
const nodes = ref<KubernetesNodeResponse[]>([]);
const resources = ref<KubernetesResourceSnapshotResponse[]>([]);
const resourcePage = ref<ClusterResourcePageResponse | null>(null);
const resourceLoading = ref(false);
const resourcePageError = ref('');
let resourceRequestSequence = 0;
const events = ref<KubernetesEventSnapshotResponse[]>([]);
const loading = ref(true);
const runtimeLoading = ref(false);
const runtimeError = ref('');
const runtimeLoaded = ref(false);
const refreshing = ref(false);
const startingSync = ref(false);
const activeSyncJobId = ref<string | null>(null);
const errorMessage = ref('');
const feedback = ref<{ tone: 'success' | 'error' | 'info'; message: string; detail?: string } | null>(null);
const selectedNamespace = ref('__ALL__');
const selectedResourceType = ref('__ALL__');
const selectedResource = ref<KubernetesResourceSnapshotResponse | null>(null);
const selectedResourceManifest = ref<KubernetesResourceManifestResponse | null>(null);
const selectedResourceContext = ref<ResourceContextResponse | null>(null);
const resourceContextLoading = ref(false);
const resourceManifestLoading = ref(false);
const resourceManifestError = ref('');
const resourceDetailTab = ref<'overview' | 'logs' | 'yaml'>('overview');
const resourceLogMode = ref<'recent' | 'stream'>('recent');
const resourceLogTargets = ref<ClusterResourceLogTargetsResponse | null>(null);
const resourceLogTargetsLoading = ref(false);
const resourceLogLoading = ref(false);
const resourceLogError = ref('');
const selectedLogPod = ref('');
const selectedLogContainer = ref('');
const resourceLogTailLines = ref(100);
const resourceLogPrevious = ref(false);
const resourceRecentLog = ref<ClusterResourceLogResponse | null>(null);
const resourceLogLines = ref<string[]>([]);
const resourceLogStreaming = ref(false);
const resourceLogAutoScroll = ref(true);
const resourceLogViewer = ref<HTMLElement | null>(null);
const resourceLogTailOptions = [50, 100, 300, 500, 1000];
let resourceLogAbortController: AbortController | null = null;
let resourceLogStreamSequence = 0;
let resourceLogTargetSequence = 0;
let resourceLogRecentSequence = 0;
const operationsLoading = ref(false);
const operationsError = ref('');
const analysisHistory = ref<AnalysisResponse[]>([]);
const commandExecutions = ref<AnalysisCommandExecutionResponse[]>([]);
const selectedCommandExecution = ref<AnalysisCommandExecutionResponse | null>(null);
const readiness = ref<ClusterReadinessResponse | null>(null);
const readinessLoading = ref(false);
const readinessError = ref('');
const readinessTab = ref<'capabilities' | 'credential' | 'upgrade'>('capabilities');
const readinessTargetVersion = ref('');

type ResourceInsight = {
  label: string;
  value: string;
  tone?: 'good' | 'warning' | 'danger' | 'neutral';
};

type ResourceRisk = {
  level: 'LOW' | 'MEDIUM' | 'HIGH';
  title: string;
  detail: string;
};

type ResourceRelation = {
  kind: string;
  name: string;
  namespace?: string;
  reason: string;
  tone?: 'direct' | 'event' | 'inferred';
};

const namespaceOptions = computed(() => {
  const names = new Set<string>();
  resourcePage.value?.namespaceFacets.forEach((facet) => names.add(facet.value));
  namespaces.value.forEach((namespace) => names.add(namespace.name));
  resources.value.forEach((resource) => {
    if (resource.namespace) {
      names.add(resource.namespace);
    }
  });
  events.value.forEach((event) => {
    if (event.namespace) {
      names.add(event.namespace);
    }
  });
  return Array.from(names).sort();
});

const resourceTypeCounts = computed(() => {
  if (resourcePage.value) {
    return resourcePage.value.resourceTypeFacets.map((facet) => ({ type: facet.value, count: facet.count }));
  }
  const counts = new Map<string, number>();
  filteredByNamespaceResources.value.forEach((resource) => {
    counts.set(resource.resourceType, (counts.get(resource.resourceType) || 0) + 1);
  });
  return Array.from(counts.entries())
    .map(([type, count]) => ({ type, count }))
    .sort((left, right) => left.type.localeCompare(right.type));
});

const filteredByNamespaceResources = computed(() => {
  if (selectedNamespace.value === '__ALL__') {
    return resources.value;
  }
  return resources.value.filter((resource) => resource.namespace === selectedNamespace.value);
});

const filteredResources = computed(() => {
  const namespaceFiltered = filteredByNamespaceResources.value;
  if (selectedResourceType.value === '__ALL__') {
    return namespaceFiltered;
  }
  return namespaceFiltered.filter((resource) => resource.resourceType === selectedResourceType.value);
});

const filteredEvents = computed(() => {
  if (selectedNamespace.value === '__ALL__') {
    return events.value;
  }
  return events.value.filter((event) => event.namespace === selectedNamespace.value);
});

const healthCounts = computed(() => {
  const problemResources = resources.value.filter((resource) => isProblemResource(resource));
  const warningEvents = events.value.filter((event) => (event.type || '').toUpperCase() === 'WARNING');
  return {
    namespaces: namespaceOptions.value.length,
    resources: resourcePage.value?.totalElements ?? resources.value.length,
    problemResources: resourcePage.value?.problemCount ?? problemResources.length,
    events: events.value.length,
    warnings: warningEvents.length
  };
});

const clusterPosture = computed(() => {
  if (healthCounts.value.problemResources > 0) {
    return {
      level: '주의 필요',
      tone: 'danger',
      summary: `문제 상태 리소스 ${healthCounts.value.problemResources}개가 감지되었습니다. 이벤트와 AI Analysis를 우선 확인하세요.`
    };
  }
  if (healthCounts.value.warnings > 0) {
    return {
      level: '관찰 필요',
      tone: 'warning',
      summary: `Warning 이벤트 ${healthCounts.value.warnings}개가 있습니다. 반복 count가 높은 이벤트부터 확인하세요.`
    };
  }
  return {
    level: '안정',
    tone: 'good',
    summary: '동기화 스냅샷 기준 즉시 확인할 문제 리소스나 warning 이벤트가 없습니다.'
  };
});

const recentAnalyses = computed(() => [...analysisHistory.value]
  .sort((left, right) => Date.parse(right.createdAt || '') - Date.parse(left.createdAt || ''))
  .slice(0, 5));

const recentCommandExecutions = computed(() => [...commandExecutions.value]
  .sort((left, right) => Date.parse(right.createdAt || '') - Date.parse(left.createdAt || ''))
  .slice(0, 5));

const operationSnapshot = computed(() => {
  const failedCommands = commandExecutions.value.filter((item) => item.status === 'FAILED' || item.status === 'BLOCKED').length;
  const changedCommands = commandExecutions.value.filter((item) => item.safety === 'CHANGE' || item.safety === 'DESTRUCTIVE').length;
  return {
    analyses: analysisHistory.value.length,
    commands: commandExecutions.value.length,
    failedCommands,
    changedCommands
  };
});

const selectedResourceSummary = computed(() => parseJson(selectedResource.value?.summaryJson));

const selectedLogPodTarget = computed(() => resourceLogTargets.value?.pods
  .find((pod) => pod.podName === selectedLogPod.value));

const resourceLogContent = computed(() => resourceLogMode.value === 'stream'
  ? resourceLogLines.value.join('\n')
  : resourceRecentLog.value?.log || '');

const resourceSupportsLogs = computed(() => {
  const resource = selectedResource.value;
  return Boolean(resource?.namespace && [
    'pod', 'deployment', 'statefulset', 'daemonset', 'replicaset', 'job', 'cronjob', 'service'
  ].includes((resource?.resourceType || '').toLowerCase()));
});

const selectedResourceEvents = computed(() => {
  const resource = selectedResource.value;
  if (!resource) {
    return [];
  }
  return events.value
    .filter((event) => (
      sameNamespace(event.namespace, resource.namespace)
      && (event.involvedName === resource.resourceName || event.message?.includes(resource.resourceName))
    ))
    .slice(0, 8);
});

const selectedResourceInsights = computed<ResourceInsight[]>(() => {
  const resource = selectedResource.value;
  if (!resource) {
    return [];
  }
  const summary = selectedResourceSummary.value;
  const facts: ResourceInsight[] = [
    {
      label: '역할',
      value: resourcePurpose(resource.resourceType),
      tone: 'neutral'
    },
    {
      label: '상태',
      value: resource.status || '-',
      tone: isProblemResource(resource) ? 'danger' : 'good'
    }
  ];

  const ready = firstPresent(summary.readyReplicas, summary.ready, summary.readyPods);
  const desired = firstPresent(summary.desiredReplicas, summary.replicas, summary.desired, summary.totalContainers);
  if (ready !== undefined || desired !== undefined) {
    facts.push({
      label: 'Ready',
      value: `${valueLabel(ready, '-')} / ${valueLabel(desired, '-')}`,
      tone: String(ready) === String(desired) ? 'good' : 'warning'
    });
  }

  const restartCount = firstPresent(summary.restartCount, summary.restarts);
  if (restartCount !== undefined) {
    facts.push({
      label: 'Restarts',
      value: valueLabel(restartCount, '0'),
      tone: Number(restartCount) > 0 ? 'warning' : 'good'
    });
  }

  const nodeName = firstPresent(summary.nodeName, summary.node);
  if (nodeName !== undefined) {
    facts.push({ label: 'Node', value: valueLabel(nodeName, '-'), tone: 'neutral' });
  }

  const dataKeys = arrayValue(summary.dataKeys);
  if (dataKeys.length) {
    facts.push({ label: 'Data keys', value: `${dataKeys.length}개`, tone: 'neutral' });
  }

  const containers = arrayValue(summary.containers);
  if (containers.length) {
    facts.push({ label: 'Containers', value: `${containers.length}개`, tone: 'neutral' });
  }

  appendResourceSpecificInsights(resource, summary, facts);
  return facts.slice(0, 8);
});

const selectedResourceRisks = computed<ResourceRisk[]>(() => {
  const resource = selectedResource.value;
  if (!resource) {
    return [];
  }
  const summary = selectedResourceSummary.value;
  const risks: ResourceRisk[] = [];
  if (isProblemResource(resource)) {
    risks.push({
      level: 'HIGH',
      title: '현재 상태가 정상 범위를 벗어났습니다.',
      detail: `status=${resource.status || '-'} 입니다. 관련 Event와 live YAML을 먼저 확인하세요.`
    });
  }
  selectedResourceEvents.value
    .filter((event) => (event.type || '').toUpperCase() === 'WARNING')
    .slice(0, 3)
    .forEach((event) => {
      risks.push({
        level: Number(event.count || 0) > 10 ? 'HIGH' : 'MEDIUM',
        title: `${event.reason || 'Warning'} 이벤트가 있습니다.`,
        detail: `${event.message || '-'}${event.count ? ` · count=${event.count}` : ''}`
      });
    });

  const containers = arrayValue(summary.containers);
  const probeMissing = containers.some((container) => objectValue(container).hasLivenessProbe === false || objectValue(container).hasReadinessProbe === false);
  if (probeMissing) {
    risks.push({
      level: 'MEDIUM',
      title: 'Probe 설정이 부족할 수 있습니다.',
      detail: 'liveness/readiness probe가 없으면 장애 감지와 트래픽 제외가 늦어질 수 있습니다.'
    });
  }

  const missingRequests = containers.some((container) => {
    const item = objectValue(container);
    return item.requests && Object.keys(objectValue(item.requests)).length === 0;
  });
  if (missingRequests) {
    risks.push({
      level: 'LOW',
      title: 'Resource request가 비어 있습니다.',
      detail: '스케줄링 품질과 HPA 기준을 안정화하려면 CPU/Memory request를 정의하는 편이 좋습니다.'
    });
  }

  appendResourceSpecificRisks(resource, summary, risks);
  return risks.slice(0, 6);
});

const selectedResourceRelations = computed<ResourceRelation[]>(() => {
  const resource = selectedResource.value;
  if (!resource) {
    return [];
  }
  const summaryText = JSON.stringify(selectedResourceSummary.value);
  const sameScopeResources = resources.value.filter((candidate) => (
    candidate.id !== resource.id && sameNamespace(candidate.namespace, resource.namespace)
  ));
  const relations: ResourceRelation[] = [];

  sameScopeResources.forEach((candidate) => {
    if (resource.resourceType === 'Pod' && resource.resourceName.startsWith(`${candidate.resourceName}-`)
      && ['ReplicaSet', 'Deployment', 'StatefulSet', 'DaemonSet', 'Job'].includes(candidate.resourceType)) {
      relations.push(toRelation(candidate, 'Pod 이름 패턴 기준 상위 workload 후보입니다.', 'inferred'));
      return;
    }
    if (['Deployment', 'ReplicaSet', 'StatefulSet', 'DaemonSet', 'Job'].includes(resource.resourceType)
      && candidate.resourceType === 'Pod'
      && candidate.resourceName.startsWith(`${resource.resourceName}-`)) {
      relations.push(toRelation(candidate, '이 workload에서 생성된 Pod 후보입니다.', 'inferred'));
      return;
    }
    if (resource.resourceType === 'Service' && candidate.resourceType === 'Endpoint' && candidate.resourceName === resource.resourceName) {
      relations.push(toRelation(candidate, 'Service와 같은 이름의 Endpoint입니다.', 'direct'));
      return;
    }
    if (resource.resourceType === 'Endpoint' && candidate.resourceType === 'Service' && candidate.resourceName === resource.resourceName) {
      relations.push(toRelation(candidate, 'Endpoint와 같은 이름의 Service입니다.', 'direct'));
      return;
    }
    if (summaryText.includes(`"${candidate.resourceName}"`) || summaryText.includes(candidate.resourceName)) {
      relations.push(toRelation(candidate, 'YAML/요약에서 참조되는 리소스입니다.', 'direct'));
    }
  });

  arrayValue(selectedResourceSummary.value.volumes).forEach((volume) => {
    const item = objectValue(volume);
    const kind = valueLabel(item.type, '');
    const name = valueLabel(item.sourceName, '');
    if (['ConfigMap', 'Secret', 'PersistentVolumeClaim'].includes(kind) && name) {
      relations.push({
        kind,
        name,
        namespace: resource.namespace,
        reason: `${valueLabel(item.name, 'volume')} volume이 참조합니다.`,
        tone: 'direct'
      });
    }
  });

  selectedResourceEvents.value.forEach((event) => {
    relations.push({
      kind: 'Event',
      name: event.reason || event.involvedName || 'event',
      namespace: event.namespace,
      reason: event.message || '이 리소스와 연결된 Kubernetes 이벤트입니다.',
      tone: 'event'
    });
  });

  return dedupeRelations(relations).slice(0, 10);
});

const snapshotNodes = computed(() => {
  const counts = new Map<string, number>();
  resources.value
    .filter((resource) => resource.resourceType === 'Pod')
    .forEach((resource) => {
      const nodeName = valueLabel(parseJson(resource.summaryJson).nodeName, '');
      if (nodeName) {
        counts.set(nodeName, (counts.get(nodeName) || 0) + 1);
      }
    });
  return Array.from(counts.entries())
    .sort((left, right) => left[0].localeCompare(right[0]))
    .map(([name, podCount]) => ({
      name,
      status: 'Snapshot',
      kubernetesVersion: `${podCount} pods`,
      containerRuntimeVersion: 'sync evidence'
    }));
});

const displayedNodes = computed(() => nodes.value.length ? nodes.value : snapshotNodes.value);

const syncInProgress = computed(() => (
  startingSync.value
  || Boolean(activeSyncJobId.value)
  || ['PENDING', 'RUNNING'].includes((syncStatus.value?.status || '').toUpperCase())
));

const syncButtonLabel = computed(() => {
  if (startingSync.value) {
    return t('pages.syncRequesting');
  }
  if (syncInProgress.value) {
    return t('pages.syncRunning');
  }
  return t('pages.syncResources');
});

const resourceHasMore = computed(() => resourcePage.value
  ? resourcePage.value.page + 1 < resourcePage.value.totalPages
  : false);

const syncDurationMs = computed(() => {
  if (!syncStatus.value?.startedAt) return null;
  const end = syncStatus.value.completedAt ? Date.parse(syncStatus.value.completedAt) : Date.now();
  return Math.max(0, end - Date.parse(syncStatus.value.startedAt));
});

const syncFreshness = computed(() => {
  if (!syncStatus.value?.completedAt || syncStatus.value.status !== 'SUCCEEDED') {
    return { label: '스냅샷 없음', tone: 'neutral', detail: '성공한 동기화가 필요합니다.' };
  }
  const ageMs = Math.max(0, Date.now() - Date.parse(syncStatus.value.completedAt));
  const staleAfterMs = Math.max(10 * 60_000, (syncSettings.value?.syncIntervalSeconds || 300) * 2_000);
  return ageMs > staleAfterMs
    ? { label: '업데이트 필요', tone: 'warning', detail: `${durationLabel(ageMs)} 전 스냅샷` }
    : { label: '최신', tone: 'good', detail: `${durationLabel(ageMs)} 전 스냅샷` };
});

const nextSyncAt = computed(() => {
  if (!syncSettings.value?.autoSyncEnabled || !syncStatus.value?.completedAt) return null;
  return new Date(Date.parse(syncStatus.value.completedAt) + syncSettings.value.syncIntervalSeconds * 1_000).toISOString();
});

const syncStages = computed(() => {
  const status = (syncStatus.value?.status || '').toUpperCase();
  const failed = status === 'FAILED' || status === 'CANCELLED';
  return [
    { label: '요청 접수', state: syncStatus.value ? 'complete' : 'pending', detail: timeLabel(syncStatus.value?.createdAt) },
    { label: 'Kubernetes API 수집', state: failed ? 'failed' : status === 'RUNNING' || status === 'PENDING' ? 'active' : status === 'SUCCEEDED' ? 'complete' : 'pending', detail: syncStatus.value?.startedAt ? timeLabel(syncStatus.value.startedAt) : '대기 중' },
    { label: '스냅샷 반영', state: failed ? 'failed' : status === 'SUCCEEDED' ? 'complete' : 'pending', detail: syncStatus.value?.completedAt ? `${timeLabel(syncStatus.value.completedAt)} · ${durationLabel(syncDurationMs.value)}` : '완료 대기' }
  ];
});

onMounted(() => {
  loadDetail();
});

onBeforeUnmount(() => {
  stopResourceLogStream();
});

async function loadDetail() {
  loading.value = true;
  errorMessage.value = '';
  try {
    await loadAll();
  } finally {
    loading.value = false;
  }
}

async function refreshDetail() {
  refreshing.value = true;
  feedback.value = null;
  try {
    await loadAll();
    feedback.value = { tone: 'success', message: '클러스터 상세 정보를 새로고침했습니다.' };
  } finally {
    refreshing.value = false;
  }
}

async function loadAll() {
  const id = clusterId.value;
  const results = await Promise.allSettled([
    api.getCluster(id),
    api.getClusterCredential(id, false),
    api.getClusterSyncSettings(id),
    api.getClusterSyncStatus(id),
    api.pageClusterResources(id, { page: 0, size: 100 }),
    api.listClusterEvents(id)
  ]) as [
    PromiseSettledResult<ClusterResponse>,
    PromiseSettledResult<ClusterCredentialResponse>,
    PromiseSettledResult<ClusterSyncSettingsResponse>,
    PromiseSettledResult<ClusterSyncStatusResponse>,
    PromiseSettledResult<ClusterResourcePageResponse>,
    PromiseSettledResult<KubernetesEventSnapshotResponse[]>
  ];

  assignResult(results[0], (value) => { cluster.value = value; });
  assignResult(results[1], (value) => { credential.value = value; });
  assignResult(results[2], (value) => { syncSettings.value = value; });
  assignResult(results[3], (value) => { syncStatus.value = value; }, true);
  assignResult(results[4], (value) => {
    resourcePage.value = value;
    resources.value = value.items;
  });
  assignResult(results[5], (value) => { events.value = value; });
  hydrateRunningSyncJob();
  void loadOperationsInfo(id);
}

async function loadRuntimeInfo(id: string) {
  runtimeLoading.value = true;
  runtimeError.value = '';
  runtimeLoaded.value = true;
  try {
    const results = await Promise.allSettled([
      api.listNamespaces(id),
      api.listNodes(id)
    ]) as [
      PromiseSettledResult<KubernetesNamespaceResponse[]>,
      PromiseSettledResult<KubernetesNodeResponse[]>
    ];
    if (clusterId.value !== id) {
      return;
    }
    assignResult(results[0], (value) => { namespaces.value = value; }, true);
    assignResult(results[1], (value) => { nodes.value = value; }, true);
    if (results.every((result) => result.status === 'rejected')) {
      const reason = results[0].status === 'rejected' ? results[0].reason : results[1].status === 'rejected' ? results[1].reason : null;
      runtimeError.value = reason instanceof Error ? reason.message : 'Kubernetes 실시간 정보를 불러오지 못했습니다.';
    }
  } finally {
    if (clusterId.value === id) {
      runtimeLoading.value = false;
    }
  }
}

async function refreshRuntimeInfo() {
  await loadRuntimeInfo(clusterId.value);
}

async function loadOperationsInfo(id: string) {
  operationsLoading.value = true;
  operationsError.value = '';
  try {
    const history = await api.listAnalysisHistory({ clusterId: id });
    if (clusterId.value !== id) {
      return;
    }
    analysisHistory.value = history;
    const recent = history.slice(0, 3);
    const commandResults = await Promise.allSettled(recent.map((analysis) => api.listAnalysisCommandExecutions(analysis.id)));
    if (clusterId.value !== id) {
      return;
    }
    commandExecutions.value = commandResults.flatMap((result) => result.status === 'fulfilled' ? result.value : []);
  } catch (error) {
    if (clusterId.value === id) {
      operationsError.value = error instanceof Error ? error.message : '운영 이력을 불러오지 못했습니다.';
    }
  } finally {
    if (clusterId.value === id) {
      operationsLoading.value = false;
    }
  }
}

async function loadClusterReadiness(refresh = false) {
  if (readinessLoading.value) return;
  readinessLoading.value = true;
  readinessError.value = '';
  try {
    readiness.value = await api.getClusterReadiness(clusterId.value, {
      namespace: selectedNamespace.value === '__ALL__' ? undefined : selectedNamespace.value,
      targetVersion: readinessTargetVersion.value || undefined,
      refresh
    });
  } catch (error) {
    readinessError.value = error instanceof Error ? error.message : t('clusterReadiness.loadFailed');
  } finally {
    readinessLoading.value = false;
  }
}

function assignResult<T>(
  result: PromiseSettledResult<T>,
  setter: (value: T) => void,
  optional = false
) {
  if (result.status === 'fulfilled') {
    setter(result.value);
    return;
  }
  if (!optional) {
    errorMessage.value = result.reason instanceof Error ? result.reason.message : '클러스터 상세 정보를 불러오지 못했습니다.';
  }
}

async function revealCredential() {
  feedback.value = null;
  credential.value = await api.getClusterCredential(clusterId.value, true);
}

async function maskCredential() {
  credential.value = await api.getClusterCredential(clusterId.value, false);
}

async function syncCluster() {
  if (syncInProgress.value) {
    feedback.value = {
      tone: 'info',
      message: '이미 이 클러스터의 리소스 동기화가 진행 중입니다.',
      detail: activeSyncJobId.value || syncStatus.value?.asyncJobId
        ? `jobId=${activeSyncJobId.value || syncStatus.value?.asyncJobId}`
        : undefined
    };
    return;
  }

  startingSync.value = true;
  try {
    const result = await api.syncCluster(clusterId.value);
    activeSyncJobId.value = result.jobId;
    jobCenter.registerJob({
      jobId: result.jobId,
      title: '클러스터 리소스 동기화',
      detail: `${cluster.value?.name || clusterId.value} · Kubernetes resource/event inventory`,
      type: 'CLUSTER_SYNC'
    });
    feedback.value = {
      tone: 'info',
      message: 'Kubernetes 리소스/이벤트 스냅샷 동기화 작업을 시작했습니다.',
      detail: 'Pod, Service, Deployment, ConfigMap, Secret metadata, PVC/PV, Event 등 Kubernetes API에서 조회 가능한 inventory를 수집합니다. AI 분석은 실행하지 않습니다.'
    };
    const job = await jobCenter.waitForJob(result.jobId, {
      title: '클러스터 리소스 동기화',
      detail: cluster.value?.name || clusterId.value
    });
    activeSyncJobId.value = null;
    await loadAll();
    feedback.value = {
      tone: job.status === 'SUCCEEDED' ? 'success' : 'error',
      message: job.status === 'SUCCEEDED' ? '리소스 동기화가 완료되었습니다.' : '리소스 동기화가 완료되지 않았습니다.',
      detail: job.errorMessage
    };
  } catch (error) {
    activeSyncJobId.value = null;
    feedback.value = {
      tone: 'error',
      message: '리소스 동기화 요청 또는 대기 중 오류가 발생했습니다.',
      detail: error instanceof Error ? error.message : '동기화 상태를 확인하지 못했습니다.'
    };
  } finally {
    startingSync.value = false;
  }
}

function hydrateRunningSyncJob() {
  const status = (syncStatus.value?.status || '').toUpperCase();
  const jobId = syncStatus.value?.asyncJobId;
  if (!jobId || !['PENDING', 'RUNNING'].includes(status)) {
    activeSyncJobId.value = null;
    return;
  }
  activeSyncJobId.value = jobId;
  jobCenter.registerJob({
    jobId,
    title: '클러스터 리소스 동기화',
    detail: cluster.value?.name || clusterId.value,
    type: 'CLUSTER_SYNC'
  });
}

async function testConnection() {
  const result = await api.testClusterConnection(clusterId.value);
  feedback.value = connectionFeedback(result);
}

function connectionFeedback(result: ClusterConnectionTestResponse) {
  return {
    tone: result.reachable ? 'success' as const : 'error' as const,
    message: result.reachable ? 'Kubernetes API 연결이 정상입니다.' : 'Kubernetes API 연결에 실패했습니다.',
    detail: [
      result.kubernetesVersion ? `version: ${result.kubernetesVersion}` : null,
      result.message,
      result.namespaces?.length ? `namespaces: ${result.namespaces.join(', ')}` : null
    ].filter(Boolean).join('\n')
  };
}

function isProblemResource(resource: KubernetesResourceSnapshotResponse) {
  const status = (resource.status || '').toLowerCase();
  if (!status) {
    return false;
  }
  return ['pending', 'failed', 'error', 'crash', '0/'].some((signal) => status.includes(signal));
}

async function selectNamespace(namespace: string) {
  if (selectedNamespace.value === namespace) return;
  selectedNamespace.value = namespace;
  selectedResourceType.value = '__ALL__';
  await loadResourcePage(true);
}

async function selectResourceType(type: string) {
  selectedResourceType.value = type;
  await loadResourcePage(true);
}

async function loadResourcePage(reset: boolean) {
  const sequence = ++resourceRequestSequence;
  resourceLoading.value = true;
  resourcePageError.value = '';
  try {
    const page = reset ? 0 : (resourcePage.value?.page ?? -1) + 1;
    const result = await api.pageClusterResources(clusterId.value, {
      namespace: selectedNamespace.value === '__ALL__' ? undefined : selectedNamespace.value,
      resourceType: selectedResourceType.value === '__ALL__' ? undefined : selectedResourceType.value,
      page,
      size: 100
    });
    if (sequence !== resourceRequestSequence) return;
    resources.value = reset ? result.items : [...resources.value, ...result.items];
    resourcePage.value = result;
  } catch (cause) {
    if (sequence === resourceRequestSequence) {
      resourcePageError.value = cause instanceof Error ? cause.message : '리소스 목록을 불러오지 못했습니다.';
    }
  } finally {
    if (sequence === resourceRequestSequence) resourceLoading.value = false;
  }
}

async function openResourceDetail(resource: KubernetesResourceSnapshotResponse) {
  stopResourceLogStream();
  selectedResource.value = resource;
  resourceDetailTab.value = 'overview';
  selectedResourceManifest.value = null;
  selectedResourceContext.value = null;
  resourceManifestError.value = '';
  resourceManifestLoading.value = false;
  resetResourceLogs();
  resourceContextLoading.value = true;
  try {
    selectedResourceContext.value = await api.getResourceContext(clusterId.value, resource.namespace, resource.resourceType, resource.resourceName);
  } catch {
    selectedResourceContext.value = null;
  } finally {
    resourceContextLoading.value = false;
  }
}

async function loadResourceManifest() {
  const resource = selectedResource.value;
  if (!resource || selectedResourceManifest.value || resourceManifestLoading.value) return;
  resourceManifestLoading.value = true;
  try {
    selectedResourceManifest.value = await api.getClusterResourceManifest(
      clusterId.value,
      resource.resourceType,
      resource.resourceName,
      resource.namespace
    );
  } catch (error) {
    resourceManifestError.value = error instanceof Error
      ? error.message
      : 'Kubernetes API에서 리소스 YAML을 가져오지 못했습니다.';
  } finally {
    resourceManifestLoading.value = false;
  }
}

function closeResourceDetail() {
  stopResourceLogStream();
  selectedResource.value = null;
  resourceDetailTab.value = 'overview';
  selectedResourceManifest.value = null;
  selectedResourceContext.value = null;
  resourceManifestError.value = '';
  resourceManifestLoading.value = false;
  resetResourceLogs();
}

async function changeResourceDetailTab(tab: 'overview' | 'logs' | 'yaml') {
  if (tab !== 'logs') stopResourceLogStream();
  resourceDetailTab.value = tab;
  if (tab === 'yaml') await loadResourceManifest();
  if (tab === 'logs' && !resourceLogTargets.value) await loadResourceLogTargets();
}

function resetResourceLogs() {
  resourceLogTargetSequence++;
  resourceLogRecentSequence++;
  resourceLogMode.value = 'recent';
  resourceLogTargets.value = null;
  resourceLogTargetsLoading.value = false;
  resourceLogLoading.value = false;
  resourceLogError.value = '';
  selectedLogPod.value = '';
  selectedLogContainer.value = '';
  resourceLogPrevious.value = false;
  resourceRecentLog.value = null;
  resourceLogLines.value = [];
}

async function loadResourceLogTargets() {
  const resource = selectedResource.value;
  if (!resource?.namespace) return;
  const sequence = ++resourceLogTargetSequence;
  stopResourceLogStream();
  resourceLogTargetsLoading.value = true;
  resourceLogError.value = '';
  resourceRecentLog.value = null;
  const resourceKey = `${resource.id}:${resource.resourceType}:${resource.resourceName}`;
  try {
    const targets = await api.getClusterResourceLogTargets(
      clusterId.value, resource.resourceType, resource.resourceName, resource.namespace
    );
    const current = selectedResource.value;
    if (sequence !== resourceLogTargetSequence
      || !current || `${current.id}:${current.resourceType}:${current.resourceName}` !== resourceKey) return;
    resourceLogTargets.value = targets;
    const firstPod = targets.pods[0];
    selectedLogPod.value = firstPod?.podName || '';
    selectedLogContainer.value = preferredContainerName(firstPod);
    if (selectedLogPod.value && selectedLogContainer.value) await loadRecentResourceLogs();
  } catch (error) {
    if (sequence === resourceLogTargetSequence) {
      resourceLogError.value = error instanceof Error ? error.message : t('clusterLogs.targetLoadFailed');
    }
  } finally {
    if (sequence === resourceLogTargetSequence) resourceLogTargetsLoading.value = false;
  }
}

function preferredContainerName(pod?: ClusterResourceLogTargetsResponse['pods'][number]) {
  return pod?.containers.find((container) => !container.initContainer)?.containerName
    || pod?.containers[0]?.containerName
    || '';
}

async function selectResourceLogPod() {
  stopResourceLogStream();
  selectedLogContainer.value = preferredContainerName(selectedLogPodTarget.value);
  resourceRecentLog.value = null;
  resourceLogLines.value = [];
  if (resourceLogMode.value === 'recent' && selectedLogContainer.value) await loadRecentResourceLogs();
}

async function selectResourceLogContainer() {
  stopResourceLogStream();
  resourceRecentLog.value = null;
  resourceLogLines.value = [];
  if (resourceLogMode.value === 'recent') await loadRecentResourceLogs();
}

async function changeResourceLogMode(mode: 'recent' | 'stream') {
  stopResourceLogStream();
  resourceLogMode.value = mode;
  resourceLogError.value = '';
  if (mode === 'recent') await loadRecentResourceLogs();
}

async function loadRecentResourceLogs() {
  const resource = selectedResource.value;
  if (!resource?.namespace || !selectedLogPod.value || !selectedLogContainer.value) return;
  const sequence = ++resourceLogRecentSequence;
  const podName = selectedLogPod.value;
  const containerName = selectedLogContainer.value;
  stopResourceLogStream();
  resourceLogLoading.value = true;
  resourceLogError.value = '';
  try {
    const result = await api.getClusterResourceLogs(
      clusterId.value,
      resource.resourceType,
      resource.resourceName,
      resource.namespace,
      podName,
      containerName,
      resourceLogTailLines.value,
      resourceLogPrevious.value
    );
    if (sequence === resourceLogRecentSequence) resourceRecentLog.value = result;
  } catch (error) {
    if (sequence === resourceLogRecentSequence) {
      resourceLogError.value = error instanceof Error ? error.message : t('clusterLogs.recentLoadFailed');
    }
  } finally {
    if (sequence === resourceLogRecentSequence) resourceLogLoading.value = false;
  }
}

async function startResourceLogStream() {
  const resource = selectedResource.value;
  if (!resource?.namespace || !selectedLogPod.value || !selectedLogContainer.value) return;
  stopResourceLogStream();
  const sequence = ++resourceLogStreamSequence;
  const controller = new AbortController();
  resourceLogAbortController = controller;
  resourceLogLines.value = [];
  resourceLogError.value = '';
  resourceLogStreaming.value = true;
  try {
    const result = await streamClusterResourceLogs({
      clusterId: clusterId.value,
      namespace: resource.namespace,
      resourceType: resource.resourceType,
      resourceName: resource.resourceName,
      podName: selectedLogPod.value,
      containerName: selectedLogContainer.value,
      tailLines: resourceLogTailLines.value
    }, async (entry) => {
      if (sequence !== resourceLogStreamSequence) return;
      resourceLogLines.value.push(entry.line);
      if (resourceLogLines.value.length > 5000) resourceLogLines.value.splice(0, 500);
      if (resourceLogAutoScroll.value) {
        await nextTick();
        const viewer = resourceLogViewer.value;
        if (viewer) viewer.scrollTop = viewer.scrollHeight;
      }
    }, controller.signal);
    if (sequence === resourceLogStreamSequence && result.reason === 'TIME_LIMIT') {
      resourceLogError.value = t('clusterLogs.timeLimitReached');
    }
  } catch (error) {
    if (!controller.signal.aborted && sequence === resourceLogStreamSequence) {
      const partial = error instanceof ResourceLogStreamError && error.partial;
      resourceLogError.value = `${error instanceof Error ? error.message : t('clusterLogs.streamFailed')}`
        + (partial ? ` ${t('clusterLogs.partialPreserved')}` : '');
    }
  } finally {
    if (sequence === resourceLogStreamSequence) {
      resourceLogStreaming.value = false;
      resourceLogAbortController = null;
    }
  }
}

function stopResourceLogStream() {
  resourceLogStreamSequence++;
  resourceLogAbortController?.abort();
  resourceLogAbortController = null;
  resourceLogStreaming.value = false;
}

function clearResourceLogs() {
  resourceRecentLog.value = null;
  resourceLogLines.value = [];
  resourceLogError.value = '';
}

async function openRelatedResource(relation: ResourceRelation) {
  const candidate = resources.value.find((resource) => (
    resource.resourceType === relation.kind
    && resource.resourceName === relation.name
    && sameNamespace(resource.namespace, relation.namespace)
  ));
  if (candidate) {
    await openResourceDetail(candidate);
  }
}

function openCommandExecution(execution: AnalysisCommandExecutionResponse) {
  selectedCommandExecution.value = execution;
}

function closeCommandExecution() {
  selectedCommandExecution.value = null;
}

function parseJson(value?: string) {
  if (!value) {
    return {};
  }
  try {
    return JSON.parse(value) as Record<string, unknown>;
  } catch {
    return { raw: value };
  }
}

function prettyJson(value?: string) {
  const parsed = parseJson(value);
  return JSON.stringify(parsed, null, 2);
}

function objectValue(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function arrayValue(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function firstPresent(...values: unknown[]) {
  return values.find((value) => value !== undefined && value !== null && value !== '');
}

function valueLabel(value: unknown, fallback = '-') {
  if (value === undefined || value === null || value === '') {
    return fallback;
  }
  if (Array.isArray(value)) {
    return value.join(', ');
  }
  if (typeof value === 'object') {
    return JSON.stringify(value);
  }
  return String(value);
}

function numberValue(value: unknown, fallback = 0) {
  const number = Number(value);
  return Number.isFinite(number) ? number : fallback;
}

function sameNamespace(left?: string, right?: string) {
  return (left || '') === (right || '');
}

function appendResourceSpecificInsights(
  resource: KubernetesResourceSnapshotResponse,
  summary: Record<string, unknown>,
  facts: ResourceInsight[]
) {
  const containers = arrayValue(summary.containers);
  if (resource.resourceType === 'Pod') {
    const readyContainers = firstPresent(summary.readyContainers);
    const totalContainers = firstPresent(summary.totalContainers);
    if (readyContainers !== undefined || totalContainers !== undefined) {
      facts.push({
        label: 'Container Ready',
        value: `${valueLabel(readyContainers, '0')} / ${valueLabel(totalContainers, '0')}`,
        tone: String(readyContainers) === String(totalContainers) ? 'good' : 'warning'
      });
    }
    if (summary.qosClass) {
      facts.push({ label: 'QoS', value: valueLabel(summary.qosClass), tone: summary.qosClass === 'BestEffort' ? 'warning' : 'neutral' });
    }
    const volumeCount = arrayValue(summary.volumes).length;
    if (volumeCount) {
      facts.push({ label: 'Volumes', value: `${volumeCount}개`, tone: 'neutral' });
    }
    const portCount = containers.reduce<number>((sum, container) => sum + arrayValue(objectValue(container).ports).length, 0);
    if (portCount) {
      facts.push({ label: 'Ports', value: `${portCount}개`, tone: 'neutral' });
    }
    return;
  }

  if (['Deployment', 'ReplicaSet', 'StatefulSet', 'DaemonSet'].includes(resource.resourceType)) {
    const available = firstPresent(summary.availableReplicas, summary.readyReplicas, summary.ready);
    const desired = firstPresent(summary.desiredReplicas, summary.desired);
    facts.push({
      label: 'Rollout',
      value: `${valueLabel(available, '0')} / ${valueLabel(desired, '0')}`,
      tone: String(available) === String(desired) ? 'good' : 'warning'
    });
    return;
  }

  if (resource.resourceType === 'Service') {
    const endpoint = resources.value.find((item) => item.resourceType === 'Endpoint'
      && item.resourceName === resource.resourceName
      && sameNamespace(item.namespace, resource.namespace));
    facts.push({
      label: 'Endpoint',
      value: endpoint ? endpoint.status || 'exists' : 'not found',
      tone: endpoint ? 'good' : 'warning'
    });
    if (summary.type) {
      facts.push({ label: 'Service Type', value: valueLabel(summary.type), tone: 'neutral' });
    }
    return;
  }

  if (resource.resourceType === 'Endpoint') {
    if (summary.readyAddresses !== undefined || summary.notReadyAddresses !== undefined) {
      facts.push({ label: 'Ready Addr', value: valueLabel(summary.readyAddresses, '0'), tone: numberValue(summary.readyAddresses) > 0 ? 'good' : 'warning' });
      facts.push({ label: 'NotReady Addr', value: valueLabel(summary.notReadyAddresses, '0'), tone: numberValue(summary.notReadyAddresses) > 0 ? 'warning' : 'good' });
    }
    return;
  }

  if (resource.resourceType === 'PersistentVolumeClaim' || resource.resourceType === 'PersistentVolume') {
    const phase = firstPresent(summary.phase, resource.status);
    facts.push({ label: 'Phase', value: valueLabel(phase), tone: phase === 'Bound' ? 'good' : 'warning' });
    if (summary.storageClassName) {
      facts.push({ label: 'StorageClass', value: valueLabel(summary.storageClassName), tone: 'neutral' });
    }
    if (summary.volumeName) {
      facts.push({ label: 'Volume', value: valueLabel(summary.volumeName), tone: 'neutral' });
    }
    return;
  }

  if (resource.resourceType === 'Ingress') {
    facts.push({ label: 'Class', value: valueLabel(summary.className, '-'), tone: summary.className ? 'neutral' : 'warning' });
    if (summary.rules !== undefined) {
      facts.push({ label: 'Rules', value: valueLabel(summary.rules, '0'), tone: numberValue(summary.rules) > 0 ? 'good' : 'warning' });
    }
    return;
  }

  if (resource.resourceType === 'Job') {
    facts.push({ label: 'Succeeded', value: valueLabel(summary.succeeded, '0'), tone: numberValue(summary.failed) > 0 ? 'warning' : 'good' });
    facts.push({ label: 'Failed', value: valueLabel(summary.failed, '0'), tone: numberValue(summary.failed) > 0 ? 'warning' : 'good' });
    return;
  }

  if (resource.resourceType === 'CronJob') {
    facts.push({ label: 'Schedule', value: valueLabel(summary.schedule, '-'), tone: 'neutral' });
    facts.push({ label: 'Suspend', value: valueLabel(summary.suspend, 'false'), tone: summary.suspend ? 'warning' : 'good' });
  }
}

function appendResourceSpecificRisks(
  resource: KubernetesResourceSnapshotResponse,
  summary: Record<string, unknown>,
  risks: ResourceRisk[]
) {
  if (resource.resourceType === 'Pod') {
    const ready = numberValue(summary.readyContainers);
    const total = numberValue(summary.totalContainers);
    if (total > 0 && ready < total) {
      risks.push({
        level: 'MEDIUM',
        title: '일부 컨테이너가 Ready 상태가 아닙니다.',
        detail: `readyContainers=${ready}, totalContainers=${total}. container state, readiness probe, event를 확인하세요.`
      });
    }
    arrayValue(summary.volumes)
      .map(objectValue)
      .filter((volume) => ['ConfigMap', 'Secret', 'PersistentVolumeClaim'].includes(valueLabel(volume.type, '')))
      .forEach((volume) => {
        const exists = resources.value.some((item) => (
          item.resourceType === volume.type
          && item.resourceName === volume.sourceName
          && sameNamespace(item.namespace, resource.namespace)
        ));
        if (!exists) {
          risks.push({
            level: 'HIGH',
            title: `${valueLabel(volume.type)} 참조 대상이 스냅샷에 없습니다.`,
            detail: `${valueLabel(volume.name, 'volume')} -> ${valueLabel(volume.sourceName, '-')}. FailedMount 이벤트가 있는지 확인하세요.`
          });
        }
      });
  }

  if (resource.resourceType === 'Service') {
    const endpoint = resources.value.find((item) => item.resourceType === 'Endpoint'
      && item.resourceName === resource.resourceName
      && sameNamespace(item.namespace, resource.namespace));
    if (!endpoint) {
      risks.push({
        level: 'MEDIUM',
        title: '연결된 Endpoint가 스냅샷에 없습니다.',
        detail: 'Service selector가 Pod label과 맞는지, EndpointSlice/Endpoint가 생성되는지 확인하세요.'
      });
    }
  }

  if (resource.resourceType === 'Endpoint') {
    if (summary.readyAddresses !== undefined && numberValue(summary.readyAddresses) === 0) {
      risks.push({
        level: 'HIGH',
        title: 'Ready endpoint address가 없습니다.',
        detail: 'Service 뒤에 트래픽을 받을 Pod가 없거나 readiness가 실패했을 수 있습니다.'
      });
    }
    if (numberValue(summary.notReadyAddresses) > 0) {
      risks.push({
        level: 'MEDIUM',
        title: 'NotReady endpoint가 있습니다.',
        detail: 'Pod readiness probe, container port, Service targetPort를 확인하세요.'
      });
    }
  }

  if (resource.resourceType === 'PersistentVolumeClaim' && String(firstPresent(summary.phase, resource.status)) !== 'Bound') {
    risks.push({
      level: 'HIGH',
      title: 'PVC가 Bound 상태가 아닙니다.',
      detail: 'StorageClass, PV provisioning, access mode, capacity 요청을 확인하세요.'
    });
  }

  if (resource.resourceType === 'Ingress' && !summary.className) {
    risks.push({
      level: 'LOW',
      title: 'Ingress class가 비어 있습니다.',
      detail: '클러스터 기본 IngressClass가 없다면 라우팅이 동작하지 않을 수 있습니다.'
    });
  }
}

function resourcePurpose(kind: string) {
  const purpose: Record<string, string> = {
    Pod: '컨테이너가 실제 실행되는 최소 실행 단위입니다.',
    Deployment: 'Pod 복제본 수와 rollout을 관리하는 workload입니다.',
    ReplicaSet: 'Deployment가 생성한 Pod 복제본 묶음입니다.',
    StatefulSet: '고정 identity와 순서를 가진 stateful workload입니다.',
    DaemonSet: '각 Node에 Pod를 배치하는 workload입니다.',
    Job: '완료될 때까지 실행되는 일회성 workload입니다.',
    Service: 'Pod로 들어가는 안정적인 네트워크 진입점입니다.',
    Endpoint: 'Service가 실제로 바라보는 Pod IP 목록입니다.',
    Ingress: '외부 HTTP/HTTPS 요청을 Service로 라우팅합니다.',
    ConfigMap: '애플리케이션 설정 데이터를 제공합니다.',
    Secret: '민감 정보를 제공합니다. 화면에서는 값이 마스킹될 수 있습니다.',
    PersistentVolumeClaim: 'Pod가 사용할 저장소 요청입니다.',
    PersistentVolume: '클러스터에서 제공하는 실제 저장소입니다.',
    Namespace: '리소스를 논리적으로 구분하는 운영 범위입니다.',
    ServiceAccount: 'Pod나 컨트롤러가 API에 접근할 때 쓰는 identity입니다.'
  };
  return purpose[kind] || 'Kubernetes 리소스입니다. YAML과 관련 이벤트를 함께 확인하세요.';
}

function toRelation(resource: KubernetesResourceSnapshotResponse, reason: string, tone: ResourceRelation['tone']): ResourceRelation {
  return {
    kind: resource.resourceType,
    name: resource.resourceName,
    namespace: resource.namespace,
    reason,
    tone
  };
}

function dedupeRelations(relations: ResourceRelation[]) {
  const seen = new Set<string>();
  return relations.filter((relation) => {
    const key = `${relation.kind}:${relation.namespace || ''}:${relation.name}:${relation.reason}`;
    if (seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });
}

function goToAnalysisForScope() {
  const namespace = selectedResource.value?.namespace || (selectedNamespace.value === '__ALL__' ? undefined : selectedNamespace.value);
  router.push({
    name: 'analysis',
    query: {
      clusterId: clusterId.value,
      mode: namespace ? 'namespace' : 'cluster',
      namespace
    }
  });
}

function riskClass(level?: string) {
  const normalized = (level || '').toUpperCase();
  if (normalized === 'HIGH') {
    return 'critical';
  }
  if (normalized === 'MEDIUM') {
    return 'warning';
  }
  return 'low';
}

function toneClass(tone?: string) {
  if (tone === 'danger') {
    return 'critical';
  }
  if (tone === 'warning') {
    return 'warning';
  }
  if (tone === 'good') {
    return 'low';
  }
  return '';
}

function readinessTone(status?: string) {
  const normalized = (status || '').toUpperCase();
  if (['CRITICAL', 'BLOCKED', 'DENIED'].includes(normalized)) return 'critical';
  if (['WARNING', 'REVIEW', 'LIMITED', 'UNKNOWN'].includes(normalized)) return 'warning';
  return 'low';
}

function timeLabel(value?: string) {
  return value ? new Date(value).toLocaleString() : '-';
}

function durationLabel(value?: number | null) {
  if (value == null || !Number.isFinite(value)) return '-';
  if (value < 1_000) return `${Math.round(value)}ms`;
  if (value < 60_000) return `${Math.round(value / 1_000)}초`;
  const minutes = Math.floor(value / 60_000);
  const seconds = Math.round((value % 60_000) / 1_000);
  return `${minutes}분 ${seconds}초`;
}

function shortId(value?: string) {
  return value ? value.slice(0, 8) : '-';
}

function openKubernetesConsole(command?: string, namespace?: string) {
  const query: Record<string, string> = {};
  if (command) query.command = command;
  if (namespace) query.namespace = namespace;
  router.push({ name: 'cluster-console', params: { clusterId: clusterId.value }, query });
}

function openSelectedResourceInConsole() {
  const resource = selectedResource.value;
  if (!resource) return;
  openKubernetesConsole(
    `kubectl get ${resource.resourceType.toLowerCase()} ${resource.resourceName} -o yaml`,
    resource.namespace
  );
}

function openSelectedPodTerminal() {
  const resource = selectedResource.value;
  if (!resource || resource.resourceType.toLowerCase() !== 'pod') return;
  openKubernetesConsole(`kubectl exec -it ${resource.resourceName} -- /bin/sh`, resource.namespace);
}
</script>

<template>
  <section class="page cluster-detail-page">
    <header class="page-header">
      <div>
        <button class="text-button" type="button" @click="router.push({ name: 'clusters' })">
          <i class="pi pi-arrow-left"></i>
          <span>Clusters</span>
        </button>
        <h1>{{ cluster?.name || t('pages.clusterDetailTitle') }}</h1>
        <p>{{ cluster?.description || t('pages.clusterDetailDescription') }}</p>
      </div>
      <div class="header-actions">
        <button
          v-if="auth.hasCapability('operation:execute')"
          class="secondary-button"
          type="button"
          @click="openKubernetesConsole()"
        >
          <i class="pi pi-desktop"></i>
          <span>{{ t('console.open') }}</span>
        </button>
        <button class="secondary-button" type="button" :disabled="refreshing" @click="refreshDetail">
          <i :class="refreshing ? 'pi pi-spin pi-spinner' : 'pi pi-refresh'"></i>
          <span>{{ t('common.refresh') }}</span>
        </button>
        <button class="secondary-button" type="button" @click="testConnection">
          <i class="pi pi-wifi"></i>
          <span>{{ t('common.connectionTest') }}</span>
        </button>
        <button class="primary-button" type="button" :disabled="syncInProgress" @click="syncCluster">
          <i :class="syncInProgress ? 'pi pi-spin pi-spinner' : 'pi pi-sync'"></i>
          <span>{{ syncButtonLabel }}</span>
        </button>
      </div>
    </header>

    <div v-if="errorMessage" class="inline-error">
      <i class="pi pi-exclamation-triangle"></i>
      <span>{{ errorMessage }}</span>
    </div>

    <div v-if="feedback" class="inline-feedback" :class="feedback.tone">
      <i :class="feedback.tone === 'error' ? 'pi pi-exclamation-triangle' : 'pi pi-info-circle'"></i>
      <div>
        <strong>{{ feedback.message }}</strong>
        <pre v-if="feedback.detail">{{ feedback.detail }}</pre>
      </div>
    </div>

    <div v-if="loading" class="empty-state">
      <i class="pi pi-spin pi-spinner"></i>
      <span>클러스터 상세 정보를 불러오는 중입니다.</span>
    </div>

    <div v-else class="cluster-detail-layout">
      <aside class="cluster-detail-sidebar">
        <section class="detail-panel">
          <div class="detail-panel-title">
            <span>CLUSTER</span>
            <strong>{{ cluster?.status || 'REGISTERED' }}</strong>
          </div>
          <dl class="detail-definition-list">
            <div>
              <dt>ID</dt>
              <dd>{{ shortId(cluster?.id) }}</dd>
            </div>
            <div>
              <dt>Environment</dt>
              <dd>{{ cluster?.environment || '-' }}</dd>
            </div>
            <div>
              <dt>Provider</dt>
              <dd>{{ cluster?.provider || '-' }}</dd>
            </div>
            <div>
              <dt>Region</dt>
              <dd>{{ cluster?.region || '-' }}</dd>
            </div>
          </dl>
        </section>

        <section class="detail-panel">
          <div class="detail-panel-title">
            <span>NAMESPACE</span>
            <strong>{{ namespaceOptions.length }}</strong>
          </div>
          <button
            class="namespace-filter-button"
            :class="{ active: selectedNamespace === '__ALL__' }"
            type="button"
            @click="selectNamespace('__ALL__')"
          >
            전체 네임스페이스
          </button>
          <button
            v-for="namespace in namespaceOptions"
            :key="namespace"
            class="namespace-filter-button"
            :class="{ active: selectedNamespace === namespace }"
            type="button"
            @click="selectNamespace(namespace)"
          >
            {{ namespace }}
          </button>
        </section>

        <section class="detail-panel">
          <div class="detail-panel-title">
            <span>SYNC SETTINGS</span>
            <strong>{{ syncInProgress ? 'RUNNING' : syncSettings?.autoSyncEnabled ? 'AUTO' : 'MANUAL' }}</strong>
          </div>
          <p class="sync-scope-note">
            이 동기화는 Kubernetes API에서 리소스와 이벤트 스냅샷만 수집합니다. AI 분석과 Pod 로그 분석은 별도 AI Analysis 화면에서 실행합니다.
          </p>
          <dl class="detail-definition-list">
            <div>
              <dt>Scope</dt>
              <dd>K8s resources/events</dd>
            </div>
            <div>
              <dt>Status</dt>
              <dd>{{ syncStatus?.status || '-' }}</dd>
            </div>
            <div>
              <dt>Interval</dt>
              <dd>{{ syncSettings?.syncIntervalSeconds || 300 }}s</dd>
            </div>
            <div>
              <dt>Last sync</dt>
              <dd>{{ syncStatus ? timeLabel(syncStatus.completedAt || syncStatus.startedAt || syncStatus.createdAt) : '-' }}</dd>
            </div>
            <div>
              <dt>Freshness</dt>
              <dd><span class="status-pill" :class="toneClass(syncFreshness.tone)">{{ syncFreshness.label }}</span> {{ syncFreshness.detail }}</dd>
            </div>
            <div>
              <dt>Duration</dt>
              <dd>{{ durationLabel(syncDurationMs) }}</dd>
            </div>
            <div>
              <dt>Next auto</dt>
              <dd>{{ nextSyncAt ? timeLabel(nextSyncAt) : '자동 동기화 꺼짐' }}</dd>
            </div>
            <div>
              <dt>Resources</dt>
              <dd>{{ syncStatus?.resourceCount ?? resources.length }}</dd>
            </div>
            <div>
              <dt>Events</dt>
              <dd>{{ syncStatus?.eventCount ?? events.length }}</dd>
            </div>
          </dl>
          <div class="sync-stage-list" aria-label="동기화 진행 단계">
            <div v-for="stage in syncStages" :key="stage.label" class="sync-stage-row" :class="stage.state">
              <i :class="stage.state === 'complete' ? 'pi pi-check-circle' : stage.state === 'active' ? 'pi pi-spin pi-spinner' : stage.state === 'failed' ? 'pi pi-times-circle' : 'pi pi-circle'"></i>
              <span><strong>{{ stage.label }}</strong><small>{{ stage.detail }}</small></span>
            </div>
          </div>
          <div v-if="syncStatus?.errorMessage" class="sync-error-detail">
            <strong>마지막 동기화 오류</strong>
            <p>{{ syncStatus.errorMessage }}</p>
            <button class="text-button" type="button" :disabled="syncInProgress" @click="syncCluster"><i class="pi pi-refresh"></i><span>다시 시도</span></button>
          </div>
        </section>
      </aside>

      <main class="cluster-detail-main">
        <section class="metric-strip">
          <article>
            <span>Namespaces</span>
            <strong>{{ healthCounts.namespaces }}</strong>
          </article>
          <article>
            <span>Resources</span>
            <strong>{{ healthCounts.resources }}</strong>
          </article>
          <article>
            <span>Problems</span>
            <strong>{{ healthCounts.problemResources }}</strong>
          </article>
          <article>
            <span>Warnings</span>
            <strong>{{ healthCounts.warnings }}</strong>
          </article>
        </section>

        <section class="cluster-ops-overview">
          <article class="detail-panel cluster-posture-card">
            <div class="detail-section-header compact-heading">
              <div>
                <span>OPERATIONS POSTURE</span>
                <h2>운영 상태 요약</h2>
              </div>
              <span class="status-pill" :class="toneClass(clusterPosture.tone)">{{ clusterPosture.level }}</span>
            </div>
            <p>{{ clusterPosture.summary }}</p>
            <div class="cluster-posture-actions">
              <button class="secondary-button" type="button" @click="goToAnalysisForScope">
                <i class="pi pi-sparkles"></i>
                <span>AI Analysis로 확인</span>
              </button>
              <button class="secondary-button" type="button" :disabled="runtimeLoading" @click="refreshRuntimeInfo">
                <i :class="runtimeLoading ? 'pi pi-spin pi-spinner' : 'pi pi-server'"></i>
                <span>Live 상태 새로고침</span>
              </button>
            </div>
          </article>

          <article class="detail-panel cluster-operation-card">
            <div class="detail-section-header compact-heading">
              <div>
                <span>RECENT OPERATIONS</span>
                <h2>분석/조치 이력</h2>
              </div>
              <span v-if="operationsLoading" class="analysis-chip">loading</span>
              <strong v-else>{{ operationSnapshot.analyses }}</strong>
            </div>
            <div v-if="operationsError" class="inline-feedback error compact-feedback">
              <i class="pi pi-exclamation-triangle"></i>
              <span>{{ operationsError }}</span>
            </div>
            <div class="operation-mini-grid">
              <span>분석 {{ operationSnapshot.analyses }}</span>
              <span>명령 {{ operationSnapshot.commands }}</span>
              <span>변경 {{ operationSnapshot.changedCommands }}</span>
              <span>실패/차단 {{ operationSnapshot.failedCommands }}</span>
            </div>
            <div v-if="recentAnalyses.length === 0" class="muted-line">아직 이 클러스터의 분석 이력이 없습니다.</div>
            <div v-else class="operation-timeline compact">
              <article v-for="analysis in recentAnalyses.slice(0, 3)" :key="analysis.id">
                <strong>{{ analysis.namespace ? `Namespace ${analysis.namespace}` : analysis.applicationId ? 'Application Analysis' : 'Cluster Analysis' }}</strong>
                <span>{{ analysis.status || '-' }} · {{ timeLabel(analysis.createdAt) }}</span>
                <small>{{ analysis.resultSummary || analysis.aiModel || '분석 결과 요약 없음' }}</small>
              </article>
            </div>
            <div v-if="recentCommandExecutions.length" class="operation-command-strip">
              <button
                v-for="execution in recentCommandExecutions.slice(0, 3)"
                :key="execution.id"
                type="button"
                @click="openCommandExecution(execution)"
              >
                <span class="status-pill" :class="execution.status === 'SUCCEEDED' ? 'low' : 'critical'">{{ execution.status }}</span>
                <code>{{ execution.command }}</code>
                <small>{{ execution.safety }} · {{ execution.durationMs ?? 0 }}ms</small>
              </button>
            </div>
          </article>
        </section>

        <section class="detail-panel cluster-readiness-panel">
          <div class="detail-section-header">
            <div>
              <span>CLUSTER READINESS</span>
              <h2>{{ t('clusterReadiness.title') }}</h2>
              <p>{{ t('clusterReadiness.description') }}</p>
            </div>
            <div class="inline-actions">
              <span v-if="readiness" class="status-pill" :class="readinessTone(readiness.overallStatus)">
                {{ readiness.overallStatus }} · {{ readiness.overallScore }}
              </span>
              <button class="secondary-button" type="button" :disabled="readinessLoading" @click="loadClusterReadiness(Boolean(readiness))">
                <i :class="readinessLoading ? 'pi pi-spin pi-spinner' : readiness ? 'pi pi-refresh' : 'pi pi-shield'"></i>
                <span>{{ readiness ? t('clusterReadiness.recheck') : t('clusterReadiness.check') }}</span>
              </button>
            </div>
          </div>

          <div v-if="readinessError" class="inline-feedback error compact-feedback">
            <i class="pi pi-exclamation-triangle"></i><span>{{ readinessError }}</span>
          </div>
          <div v-else-if="!readiness" class="readiness-empty">
            <i class="pi pi-verified"></i>
            <div><strong>{{ t('clusterReadiness.emptyTitle') }}</strong><p>{{ t('clusterReadiness.emptyDescription') }}</p></div>
          </div>
          <template v-else>
            <div class="readiness-summary-grid">
              <button type="button" :class="{ active: readinessTab === 'capabilities' }" @click="readinessTab = 'capabilities'">
                <i class="pi pi-key"></i><span>{{ t('clusterReadiness.capabilities') }}</span>
                <strong>{{ readiness.capabilities.allowedCount }}/{{ readiness.capabilities.checks.length }}</strong>
                <small>{{ readiness.capabilities.status }} · {{ t('clusterReadiness.unknownCount', { count: readiness.capabilities.unknownCount }) }}</small>
              </button>
              <button type="button" :class="{ active: readinessTab === 'credential' }" @click="readinessTab = 'credential'">
                <i class="pi pi-lock"></i><span>{{ t('clusterReadiness.credential') }}</span>
                <strong>{{ readiness.credential.status }}</strong>
                <small>{{ readiness.credential.connectionReachable ? t('clusterReadiness.reachable') : t('clusterReadiness.unreachable') }}</small>
              </button>
              <button type="button" :class="{ active: readinessTab === 'upgrade' }" @click="readinessTab = 'upgrade'">
                <i class="pi pi-arrow-up-right"></i><span>{{ t('clusterReadiness.upgrade') }}</span>
                <strong>{{ readiness.upgrade.score }}</strong>
                <small>{{ readiness.upgrade.currentVersion || '-' }} → {{ readiness.upgrade.targetVersion || '-' }}</small>
              </button>
            </div>

            <section v-if="readinessTab === 'capabilities'" class="readiness-detail-list">
              <article v-for="check in readiness.capabilities.checks" :key="check.id">
                <i :class="check.state === 'ALLOWED' ? 'pi pi-check-circle readiness-ok' : check.state === 'UNKNOWN' ? 'pi pi-question-circle readiness-unknown' : 'pi pi-times-circle readiness-denied'"></i>
                <div><strong>{{ check.displayName }} · {{ check.state }}</strong><p><code>{{ check.verb }} {{ check.apiGroup || 'core' }}/{{ check.resource }}{{ check.namespace ? ` · ${check.namespace}` : '' }}</code></p><small>{{ check.reason }}</small></div>
              </article>
            </section>

            <section v-else-if="readinessTab === 'credential'" class="readiness-credential-detail">
              <dl class="detail-definition-list wide">
                <div><dt>{{ t('clusterReadiness.type') }}</dt><dd>{{ readiness.credential.credentialType }}</dd></div>
                <div><dt>{{ t('clusterReadiness.storedAge') }}</dt><dd>{{ readiness.credential.ageDays }} days</dd></div>
                <div><dt>{{ t('clusterReadiness.tokenExpiry') }}</dt><dd>{{ readiness.credential.tokenExpiresAt ? timeLabel(readiness.credential.tokenExpiresAt) : t('clusterReadiness.notObservable') }}</dd></div>
                <div><dt>{{ t('clusterReadiness.clientCertificateExpiry') }}</dt><dd>{{ readiness.credential.clientCertificateExpiresAt ? timeLabel(readiness.credential.clientCertificateExpiresAt) : t('clusterReadiness.notObservable') }}</dd></div>
                <div><dt>{{ t('clusterReadiness.caCertificateExpiry') }}</dt><dd>{{ readiness.credential.caCertificateExpiresAt ? timeLabel(readiness.credential.caCertificateExpiresAt) : t('clusterReadiness.notObservable') }}</dd></div>
                <div><dt>{{ t('clusterReadiness.encryption') }}</dt><dd>{{ readiness.credential.encryptionAlgorithm }} · {{ readiness.credential.keyId }}</dd></div>
                <div><dt>{{ t('clusterReadiness.secretExposure') }}</dt><dd>{{ readiness.credential.secretValueExposed ? 'EXPOSED' : 'NOT EXPOSED' }}</dd></div>
                <div><dt>{{ t('clusterReadiness.rotationStatus') }}</dt><dd>{{ readiness.credential.rotationStatus }}</dd></div>
                <div><dt>{{ t('clusterReadiness.recommendedRotationBy') }}</dt><dd>{{ readiness.credential.recommendedRotationBy ? timeLabel(readiness.credential.recommendedRotationBy) : t('clusterReadiness.notObservable') }}</dd></div>
              </dl>
              <ul><li v-for="finding in readiness.credential.findings" :key="finding">{{ finding }}</li></ul>
              <div v-if="readiness.credential.rotationSteps.length" class="readiness-rotation-steps">
                <strong>{{ t('clusterReadiness.rotationSteps') }}</strong>
                <ol><li v-for="step in readiness.credential.rotationSteps" :key="step">{{ step }}</li></ol>
              </div>
            </section>

            <section v-else class="readiness-upgrade-detail">
              <div class="readiness-target-control">
                <label><span>{{ t('clusterReadiness.targetVersion') }}</span><input v-model.trim="readinessTargetVersion" placeholder="v1.32" type="text"></label>
                <button class="secondary-button" type="button" :disabled="readinessLoading" @click="loadClusterReadiness(true)"><i class="pi pi-search"></i><span>{{ t('clusterReadiness.evaluate') }}</span></button>
              </div>
              <div v-if="readiness.upgrade.findings.length === 0" class="inline-feedback success"><i class="pi pi-check-circle"></i><span>{{ t('clusterReadiness.noBlockers') }}</span></div>
              <div v-else class="readiness-detail-list">
                <article v-for="finding in readiness.upgrade.findings" :key="`${finding.category}:${finding.resourceRef}:${finding.evidence}`">
                  <span class="status-pill" :class="readinessTone(finding.severity)">{{ finding.severity }}</span>
                  <div><strong>{{ finding.title }}</strong><p>{{ finding.detail }}</p><small>{{ finding.resourceRef || finding.category }} · {{ finding.evidence }}</small></div>
                </article>
              </div>
              <small class="muted-line">{{ t('clusterReadiness.catalog') }}: {{ readiness.upgrade.compatibilityCatalogVersion }}</small>
            </section>
            <small class="muted-line readiness-checked-at">{{ t('clusterReadiness.checkedAt') }} {{ timeLabel(readiness.checkedAt) }}</small>
          </template>
        </section>

        <section class="detail-panel">
          <div class="detail-section-header">
            <div>
              <span>K8S INVENTORY</span>
              <h2>리소스 조회</h2>
              <p>선택한 클러스터와 네임스페이스 기준으로 동기화된 Kubernetes 리소스를 보여줍니다.</p>
            </div>
            <div class="inventory-heading-actions">
              <span class="inventory-freshness" :class="syncFreshness.tone">{{ syncFreshness.label }} · {{ resourcePage?.totalElements ?? 0 }}개</span>
            </div>
          </div>

          <div class="resource-type-grid">
            <button
              class="resource-type-chip"
              :class="{ active: selectedResourceType === '__ALL__' }"
              type="button"
              @click="selectResourceType('__ALL__')"
            >
              <span>All</span>
              <strong>{{ resourcePage?.totalElements ?? filteredByNamespaceResources.length }}</strong>
            </button>
            <button
              v-for="item in resourceTypeCounts"
              :key="item.type"
              class="resource-type-chip"
              :class="{ active: selectedResourceType === item.type }"
              type="button"
              @click="selectResourceType(item.type)"
            >
              <span>{{ item.type }}</span>
              <strong>{{ item.count }}</strong>
            </button>
          </div>

          <div v-if="resourcePageError" class="inline-feedback error compact-feedback">
            <i class="pi pi-exclamation-triangle"></i><span>{{ resourcePageError }}</span>
            <button class="text-button" type="button" @click="loadResourcePage(true)">재시도</button>
          </div>
          <div v-if="resourceLoading && filteredResources.length === 0" class="empty-state compact">
            <i class="pi pi-spin pi-spinner"></i><span>리소스 인벤토리를 불러오고 있습니다.</span>
          </div>
          <div v-else-if="filteredResources.length === 0" class="empty-state compact">
            <i class="pi pi-inbox"></i>
            <span>선택한 조건에 해당하는 리소스가 없습니다. 먼저 클러스터 동기화를 실행해보세요.</span>
          </div>
          <div v-else class="resource-table">
            <button
              v-for="resource in filteredResources"
              :key="resource.id"
              class="resource-row"
              :class="{ problem: isProblemResource(resource) }"
              type="button"
              @click="openResourceDetail(resource)"
            >
              <span class="resource-kind">{{ resource.resourceType }}</span>
              <strong>{{ resource.resourceName }}</strong>
              <span>{{ resource.namespace || 'cluster-scope' }}</span>
              <span>{{ resource.status || '-' }}</span>
              <i class="pi pi-angle-right"></i>
            </button>
          </div>
          <div v-if="filteredResources.length" class="resource-page-footer">
            <span>{{ filteredResources.length }} / {{ resourcePage?.totalElements ?? filteredResources.length }}개 표시</span>
            <button v-if="resourceHasMore" class="secondary-button" type="button" :disabled="resourceLoading" @click="loadResourcePage(false)">
              <i :class="resourceLoading ? 'pi pi-spin pi-spinner' : 'pi pi-chevron-down'"></i>
              <span>{{ resourceLoading ? '불러오는 중' : '100개 더 보기' }}</span>
            </button>
          </div>
        </section>

        <section class="cluster-detail-grid">
          <article class="detail-panel">
            <div class="detail-section-header compact-heading">
              <div>
                <span>EVENTS</span>
                <h2>최근 이벤트</h2>
              </div>
              <strong>{{ filteredEvents.length }}</strong>
            </div>
            <div v-if="filteredEvents.length === 0" class="muted-line">이벤트 스냅샷이 없습니다.</div>
            <div v-else class="event-list">
              <article v-for="event in filteredEvents.slice(0, 12)" :key="event.id" class="event-item">
                <span class="status-pill" :class="{ error: (event.type || '').toUpperCase() === 'WARNING' }">
                  {{ event.type || 'Event' }}
                </span>
                <strong>{{ event.reason || '-' }} · {{ event.involvedKind || '-' }}/{{ event.involvedName || '-' }}</strong>
                <p>{{ event.message || '-' }}</p>
                <small>{{ event.namespace || 'cluster-scope' }} · count {{ event.count || 0 }} · {{ timeLabel(event.eventTime || event.collectedAt) }}</small>
              </article>
            </div>
          </article>

          <article class="detail-panel">
            <div class="detail-section-header compact-heading">
              <div>
                <span>NODES</span>
                <h2>노드 상태</h2>
              </div>
              <span class="analysis-chip">{{ nodes.length ? 'live' : 'snapshot' }}</span>
              <button class="icon-button" title="Live 상태 새로고침" type="button" :disabled="runtimeLoading" @click="refreshRuntimeInfo">
                <i :class="runtimeLoading ? 'pi pi-spin pi-spinner' : 'pi pi-refresh'"></i>
              </button>
            </div>
            <div v-if="runtimeLoading" class="empty-state compact">
              <i class="pi pi-spin pi-spinner"></i>
              <span>노드 실시간 정보를 조회하는 중입니다.</span>
            </div>
            <div v-else-if="runtimeError && displayedNodes.length === 0" class="inline-feedback error">
              <i class="pi pi-exclamation-triangle"></i>
              <div>
                <strong>실시간 조회 실패</strong>
                <pre>{{ runtimeError }}</pre>
              </div>
            </div>
            <div v-else-if="displayedNodes.length === 0" class="muted-line">
              노드 live 조회 전입니다. 필요 시 새로고침 버튼으로 Kubernetes API에서 조회하세요.
            </div>
            <div v-else class="node-list">
              <article v-for="node in displayedNodes" :key="node.name" class="node-item">
                <strong>{{ node.name }}</strong>
                <span>{{ node.status || '-' }}</span>
                <small>{{ node.kubernetesVersion || '-' }} · {{ node.containerRuntimeVersion || '-' }}</small>
              </article>
            </div>
            <small v-if="runtimeError && displayedNodes.length" class="muted-line">
              live 조회는 실패했지만 동기화 스냅샷 기준 노드 후보를 표시합니다.
            </small>
          </article>
        </section>

        <section class="detail-panel credential-panel">
          <div class="detail-section-header">
            <div>
              <span>REGISTERED SETTINGS</span>
              <h2>등록 인증 정보</h2>
              <p>저장된 kubeconfig 또는 ServiceAccount 정보를 확인합니다. 민감값은 기본 마스킹됩니다.</p>
            </div>
            <div class="header-actions">
              <button v-if="credential?.revealed" class="secondary-button" type="button" @click="maskCredential">
                <i class="pi pi-eye-slash"></i>
                <span>마스킹</span>
              </button>
              <button v-else class="secondary-button" type="button" @click="revealCredential">
                <i class="pi pi-eye"></i>
                <span>원문 보기</span>
              </button>
            </div>
          </div>
          <div class="credential-meta">
            <span class="status-pill">{{ credential?.credentialType || '-' }}</span>
            <span>{{ credential?.revealed ? '원문 표시 중' : '마스킹 표시 중' }}</span>
          </div>
          <pre class="credential-viewer">{{ credential?.payload || '저장된 인증 정보를 불러오지 못했습니다.' }}</pre>
        </section>
      </main>
    </div>

    <div v-if="selectedResource" class="modal-backdrop" @click.self="closeResourceDetail">
      <section class="modal-panel resource-detail-modal" aria-modal="true" role="dialog">
        <header class="modal-header">
          <div>
            <h2>{{ selectedResource.resourceType }} 상세</h2>
            <p>{{ selectedResource.namespace || 'cluster-scope' }} / {{ selectedResource.resourceName }}</p>
          </div>
          <button class="icon-button" title="닫기" type="button" @click="closeResourceDetail">
            <i class="pi pi-times"></i>
          </button>
        </header>
        <nav class="resource-detail-tabs" :aria-label="t('clusterLogs.detailSections')">
          <button type="button" :class="{ active: resourceDetailTab === 'overview' }" @click="changeResourceDetailTab('overview')">
            <i class="pi pi-chart-bar"></i><span>{{ t('clusterLogs.overview') }}</span>
          </button>
          <button
            v-if="resourceSupportsLogs"
            type="button"
            :class="{ active: resourceDetailTab === 'logs' }"
            @click="changeResourceDetailTab('logs')"
          >
            <i class="pi pi-align-left"></i><span>{{ t('clusterLogs.logs') }}</span>
          </button>
          <button type="button" :class="{ active: resourceDetailTab === 'yaml' }" @click="changeResourceDetailTab('yaml')">
            <i class="pi pi-code"></i><span>YAML</span>
          </button>
        </nav>
        <div class="resource-detail-body">
          <dl class="detail-definition-list wide">
            <div>
              <dt>Status</dt>
              <dd>{{ selectedResource.status || '-' }}</dd>
            </div>
            <div>
              <dt>UID</dt>
              <dd>{{ selectedResource.resourceUid || '-' }}</dd>
            </div>
            <div>
              <dt>Collected</dt>
              <dd>{{ timeLabel(selectedResource.collectedAt) }}</dd>
            </div>
          </dl>

          <template v-if="resourceDetailTab === 'overview'">
          <section class="resource-intelligence-panel">
            <div class="detail-section-header compact-heading">
              <div>
                <span>RESOURCE INTELLIGENCE</span>
                <h3>운영 해석</h3>
                <p>동기화 스냅샷, 이벤트, live YAML 근거를 묶어 현재 리소스를 빠르게 이해합니다.</p>
              </div>
              <button class="secondary-button" type="button" @click="goToAnalysisForScope">
                <i class="pi pi-sparkles"></i>
                <span>AI 분석</span>
              </button>
            </div>
            <div class="resource-insight-grid">
              <article
                v-for="item in selectedResourceInsights"
                :key="`${item.label}-${item.value}`"
                class="resource-insight-card"
                :class="toneClass(item.tone)"
              >
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </article>
            </div>
          </section>

          <section class="resource-detail-grid">
            <article>
              <div class="detail-section-header compact-heading">
                <div>
                  <span>RISK SIGNALS</span>
                  <h3>확인해야 할 위험</h3>
                </div>
                <strong>{{ selectedResourceRisks.length }}</strong>
              </div>
              <div v-if="selectedResourceRisks.length === 0" class="muted-line">스냅샷 기준 즉시 확인할 위험 신호가 없습니다.</div>
              <ul v-else class="resource-risk-list">
                <li v-for="risk in selectedResourceRisks" :key="`${risk.level}-${risk.title}`">
                  <span class="status-pill" :class="riskClass(risk.level)">{{ risk.level }}</span>
                  <div>
                    <strong>{{ risk.title }}</strong>
                    <p>{{ risk.detail }}</p>
                  </div>
                </li>
              </ul>
            </article>

            <article>
              <div class="detail-section-header compact-heading">
                <div>
                  <span>RELATION MAP</span>
                  <h3>연결 리소스</h3>
                </div>
                <strong>{{ selectedResourceRelations.length }}</strong>
              </div>
              <div v-if="selectedResourceRelations.length === 0" class="muted-line">스냅샷 기준 연결 후보가 없습니다.</div>
              <div v-else class="resource-relation-list">
                <button
                  v-for="relation in selectedResourceRelations"
                  :key="`${relation.kind}-${relation.namespace}-${relation.name}-${relation.reason}`"
                  type="button"
                  @click="openRelatedResource(relation)"
                >
                  <span class="status-pill" :class="relation.tone === 'event' ? 'warning' : relation.tone === 'direct' ? 'low' : ''">
                    {{ relation.kind }}
                  </span>
                  <strong>{{ relation.name }}</strong>
                  <small>{{ relation.reason }}</small>
                </button>
              </div>
            </article>
          </section>
          <section class="resource-history-panel">
            <div class="detail-section-header compact-heading"><div><span>RESOURCE CONTEXT</span><h3>운영 이력과 Incident</h3><p>현재 리소스와 정확히 일치하는 상태 변경 및 장애 기록입니다.</p></div><i v-if="resourceContextLoading" class="pi pi-spin pi-spinner"></i></div>
            <div v-if="!resourceContextLoading && !selectedResourceContext?.changes.length && !selectedResourceContext?.fieldDiffs.length && !selectedResourceContext?.incidents.length" class="muted-line">연결된 변경 이력이나 Incident가 없습니다.</div>
            <div v-else class="resource-history-grid"><div><h4>최근 상태 변경</h4><button v-for="change in selectedResourceContext?.changes || []" :key="change.id" type="button" class="resource-history-row"><span class="status-pill low">{{ change.changeType }}</span><strong>{{ change.previousStatus || '-' }} → {{ change.currentStatus || '-' }}</strong><small>{{ change.summary || '상태 변경 감지' }} · {{ timeLabel(change.detectedAt) }}</small></button></div><div><h4>핵심 필드 변경</h4><article v-for="diff in selectedResourceContext?.fieldDiffs || []" :key="diff.field" class="resource-history-row"><strong>{{ diff.field }}</strong><small>{{ diff.previousValue || '(없음)' }} → {{ diff.currentValue || '(없음)' }}</small></article></div><div><h4>관련 Incident</h4><button v-for="item in selectedResourceContext?.incidents || []" :key="item.id" type="button" class="resource-history-row" @click="router.push(`/incidents/${item.id}`)"><span class="status-pill" :class="item.severity.toLowerCase()">{{ item.severity }}</span><strong>{{ item.title }}</strong><small>{{ item.state }} · {{ timeLabel(item.lastDetectedAt) }}</small></button></div></div>
          </section>
          </template>

          <section v-else-if="resourceDetailTab === 'logs'" class="resource-log-panel">
            <div class="detail-section-header compact-heading">
              <div>
                <span>LIVE KUBERNETES LOGS</span>
                <h3>{{ t('clusterLogs.title') }}</h3>
                <p>{{ t('clusterLogs.description') }}</p>
              </div>
              <button class="icon-button" type="button" :title="t('common.refresh')" :disabled="resourceLogTargetsLoading" @click="loadResourceLogTargets">
                <i :class="resourceLogTargetsLoading ? 'pi pi-spin pi-spinner' : 'pi pi-refresh'"></i>
              </button>
            </div>

            <div v-if="resourceLogTargetsLoading" class="empty-state compact">
              <i class="pi pi-spin pi-spinner"></i><span>{{ t('clusterLogs.loadingTargets') }}</span>
            </div>
            <div v-else-if="resourceLogTargets && (!resourceLogTargets.supported || resourceLogTargets.pods.length === 0)" class="empty-state compact">
              <i class="pi pi-info-circle"></i>
              <span>{{ resourceLogTargets.unavailableReason || t('clusterLogs.noPods') }}</span>
            </div>
            <template v-else-if="resourceLogTargets?.pods.length">
              <div class="resource-log-toolbar">
                <label>
                  <span>Pod</span>
                  <select v-model="selectedLogPod" :disabled="resourceLogStreaming" @change="selectResourceLogPod">
                    <option v-for="pod in resourceLogTargets.pods" :key="pod.podName" :value="pod.podName">
                      {{ pod.podName }} · {{ pod.phase }}
                    </option>
                  </select>
                </label>
                <label>
                  <span>Container</span>
                  <select v-model="selectedLogContainer" :disabled="resourceLogStreaming" @change="selectResourceLogContainer">
                    <option v-for="container in selectedLogPodTarget?.containers || []" :key="container.containerName" :value="container.containerName">
                      {{ container.containerName }}{{ container.initContainer ? ' · init' : '' }} · {{ container.state }}
                    </option>
                  </select>
                </label>
                <label>
                  <span>{{ t('clusterLogs.lineCount') }}</span>
                  <select v-model.number="resourceLogTailLines" :disabled="resourceLogStreaming" @change="resourceLogMode === 'recent' && loadRecentResourceLogs()">
                    <option v-for="count in resourceLogTailOptions" :key="count" :value="count">{{ t('clusterLogs.tailLines', { count }) }}</option>
                  </select>
                </label>
              </div>

              <div class="resource-log-control-row">
                <div class="segmented-control" :aria-label="t('clusterLogs.mode')">
                  <button type="button" value="recent" :class="{ active: resourceLogMode === 'recent' }" @click="changeResourceLogMode('recent')">
                    {{ t('clusterLogs.recent') }}
                  </button>
                  <button type="button" value="stream" :class="{ active: resourceLogMode === 'stream' }" @click="changeResourceLogMode('stream')">
                    {{ t('clusterLogs.streaming') }}
                  </button>
                </div>
                <div class="resource-log-options">
                  <label v-if="resourceLogMode === 'recent'" class="checkbox-label">
                    <input v-model="resourceLogPrevious" type="checkbox" @change="loadRecentResourceLogs">
                    <span>{{ t('clusterLogs.previous') }}</span>
                  </label>
                  <label v-else class="checkbox-label">
                    <input v-model="resourceLogAutoScroll" type="checkbox">
                    <span>{{ t('clusterLogs.autoScroll') }}</span>
                  </label>
                  <button class="text-button" type="button" @click="clearResourceLogs">
                    <i class="pi pi-eraser"></i><span>{{ t('clusterLogs.clear') }}</span>
                  </button>
                </div>
              </div>

              <div v-if="resourceLogError" class="inline-feedback error compact-feedback">
                <i class="pi pi-exclamation-triangle"></i><span>{{ resourceLogError }}</span>
              </div>
              <div class="resource-log-viewer-header">
                <span>
                  <i :class="resourceLogStreaming ? 'pi pi-circle-fill resource-log-live-dot' : 'pi pi-file'"></i>
                  {{ selectedLogPod }} / {{ selectedLogContainer }}
                </span>
                <small v-if="resourceLogMode === 'recent' && resourceRecentLog?.collectedAt">
                  {{ t('clusterLogs.collectedAt') }} {{ timeLabel(resourceRecentLog.collectedAt) }}
                </small>
                <small v-else-if="resourceLogStreaming">{{ t('clusterLogs.connected') }}</small>
              </div>
              <pre ref="resourceLogViewer" class="resource-log-viewer" aria-live="polite">{{ resourceLogLoading ? t('clusterLogs.loadingLogs') : resourceLogContent || t('clusterLogs.empty') }}</pre>
              <div class="resource-log-actions">
                <button v-if="resourceLogMode === 'recent'" class="secondary-button" type="button" :disabled="resourceLogLoading" @click="loadRecentResourceLogs">
                  <i :class="resourceLogLoading ? 'pi pi-spin pi-spinner' : 'pi pi-refresh'"></i><span>{{ t('clusterLogs.loadRecent') }}</span>
                </button>
                <button v-else-if="!resourceLogStreaming" class="primary-button" type="button" @click="startResourceLogStream">
                  <i class="pi pi-play"></i><span>{{ t('clusterLogs.start') }}</span>
                </button>
                <button v-else class="danger-button" type="button" @click="stopResourceLogStream">
                  <i class="pi pi-stop"></i><span>{{ t('clusterLogs.stop') }}</span>
                </button>
              </div>
            </template>
          </section>

          <section v-if="resourceDetailTab === 'yaml'" class="resource-live-manifest">
            <div class="detail-section-header compact-heading">
              <div>
                <span>LIVE MANIFEST</span>
                <h3>현재 YAML</h3>
                <p>Kubernetes API에서 실시간으로 조회한 정의입니다. 동기화 DB에는 저장하지 않습니다.</p>
              </div>
              <div class="inline-actions">
                <button
                  v-if="auth.hasCapability('operation:execute') && selectedResource.resourceType.toLowerCase() === 'pod'"
                  class="secondary-button compact-button"
                  type="button"
                  @click="openSelectedPodTerminal"
                >
                  <i class="pi pi-desktop"></i>
                  <span>{{ t('console.openPodTerminal') }}</span>
                </button>
                <button
                  v-if="auth.hasCapability('operation:execute')"
                  class="secondary-button compact-button"
                  type="button"
                  @click="openSelectedResourceInConsole"
                >
                  <i class="pi pi-code"></i>
                  <span>{{ t('console.openResource') }}</span>
                </button>
                <span
                  v-if="selectedResourceManifest"
                  class="status-pill"
                  :class="selectedResourceManifest.source === 'SNAPSHOT_FALLBACK' ? 'warning' : 'low'"
                >
                  {{ selectedResourceManifest.source === 'SNAPSHOT_FALLBACK' ? 'Snapshot fallback' : 'Live' }}
                </span>
                <span v-if="selectedResourceManifest?.secretRedacted" class="status-pill warning">Secret redacted</span>
              </div>
            </div>
            <div v-if="resourceManifestLoading" class="empty-state compact">
              <i class="pi pi-spin pi-spinner"></i>
              <span>현재 리소스 YAML을 조회하는 중입니다.</span>
            </div>
            <div v-else-if="resourceManifestError" class="inline-feedback error">
              <i class="pi pi-exclamation-triangle"></i>
              <div>
                <strong>YAML 조회 실패</strong>
                <pre>{{ resourceManifestError }}</pre>
              </div>
            </div>
            <template v-else>
              <div
                v-if="selectedResourceManifest?.source === 'SNAPSHOT_FALLBACK'"
                class="inline-feedback warning"
              >
                <i class="pi pi-info-circle"></i>
                <div>
                  <strong>실시간 조회가 지연되어 최근 동기화 스냅샷을 표시합니다.</strong>
                  <p>{{ selectedResourceManifest.fallbackReason || 'Kubernetes API live lookup unavailable.' }}</p>
                </div>
              </div>
              <pre class="yaml-viewer">{{ selectedResourceManifest?.manifestYaml || '표시할 YAML이 없습니다.' }}</pre>
              <small v-if="selectedResourceManifest" class="muted-line">
                {{ selectedResourceManifest.source === 'SNAPSHOT_FALLBACK' ? 'snapshot collected at' : 'live fetched at' }}
                {{ timeLabel(selectedResourceManifest.collectedAt) }}
              </small>
            </template>
          </section>

          <section v-if="resourceDetailTab === 'overview'">
            <div class="detail-section-header compact-heading">
              <div>
                <span>SYNC SUMMARY</span>
                <h3>동기화 요약</h3>
                <p>목록과 검색을 빠르게 하기 위해 저장한 최소 요약입니다.</p>
              </div>
            </div>
            <pre>{{ prettyJson(selectedResource.summaryJson) }}</pre>
          </section>
        </div>
      </section>
    </div>

    <div v-if="selectedCommandExecution" class="modal-backdrop" @click.self="closeCommandExecution">
      <section class="modal-panel feedback-detail-modal" aria-modal="true" role="dialog">
        <header class="modal-header">
          <div>
            <h2>명령 실행 결과</h2>
            <p>{{ selectedCommandExecution.safety }} · {{ selectedCommandExecution.status }}</p>
          </div>
          <button class="icon-button" title="닫기" type="button" @click="closeCommandExecution">
            <i class="pi pi-times"></i>
          </button>
        </header>
        <div class="feedback-detail-body">
          <section class="command-result-summary">
            <span class="status-pill" :class="selectedCommandExecution.status === 'SUCCEEDED' ? 'low' : 'critical'">
              {{ selectedCommandExecution.status }}
            </span>
            <strong>{{ selectedCommandExecution.command }}</strong>
            <small>
              namespace {{ selectedCommandExecution.namespace || 'default' }}
              · exit {{ selectedCommandExecution.exitCode ?? '-' }}
              · {{ selectedCommandExecution.durationMs ?? 0 }}ms
            </small>
            <p v-if="selectedCommandExecution.reason">{{ selectedCommandExecution.reason }}</p>
          </section>
          <section>
            <div class="detail-section-header compact-heading">
              <div>
                <span>STDOUT</span>
                <h3>표준 출력</h3>
              </div>
            </div>
            <pre>{{ selectedCommandExecution.stdoutText || '표준 출력이 없습니다.' }}</pre>
          </section>
          <section>
            <div class="detail-section-header compact-heading">
              <div>
                <span>STDERR</span>
                <h3>오류 출력</h3>
              </div>
            </div>
            <pre>{{ selectedCommandExecution.stderrText || '오류 출력이 없습니다.' }}</pre>
          </section>
          <button class="secondary-button" type="button" @click="goToAnalysisForScope">
            <i class="pi pi-refresh"></i>
            <span>같은 scope 재분석</span>
          </button>
        </div>
      </section>
    </div>
  </section>
</template>
