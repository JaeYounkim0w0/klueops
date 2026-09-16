<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute, useRouter } from 'vue-router';
import {
  api,
  type AnalysisCommandExecutionResponse,
  type AnalysisCommandPreviewResponse,
  type AnalysisResponse,
  type ApplicationResponse,
  type ClusterResponse,
  type KubernetesNamespaceResponse,
  type NamespaceDiagnosticsResponse,
  type PodLogsResponse,
  type WorkflowStatusValue
} from '@/api/client';
import { displayText } from '@/utils/text';
import {
  arrayValue,
  formattedAnalysisJson,
  objectValue,
  parseAnalysisResult,
  stringArray,
  type AnalysisResult
} from '@/utils/analysisResult';
import { useJobCenterStore } from '@/stores/jobCenter';
import AnalysisFeedbackPanel from '@/components/AnalysisFeedbackPanel.vue';
import AnalysisRunPanel from '@/components/analysis/AnalysisRunPanel.vue';
import AnalysisRuntimePanel from '@/components/analysis/AnalysisRuntimePanel.vue';
import AnalysisEvidencePanel from '@/components/analysis/AnalysisEvidencePanel.vue';
import AnalysisSummaryOverview from '@/components/analysis/AnalysisSummaryOverview.vue';
import AnalysisCommandSafetyPanel from '@/components/analysis/AnalysisCommandSafetyPanel.vue';
import AnalysisRunbookPanel from '@/components/analysis/AnalysisRunbookPanel.vue';
import AnalysisIssueGroupPanel from '@/components/analysis/AnalysisIssueGroupPanel.vue';
import AnalysisTextDetailModal from '@/components/analysis/AnalysisTextDetailModal.vue';
import AnalysisResourceDetailModal from '@/components/analysis/AnalysisResourceDetailModal.vue';
import AnalysisLogDetailModal from '@/components/analysis/AnalysisLogDetailModal.vue';
import AnalysisLogIntelligencePanel from '@/components/analysis/AnalysisLogIntelligencePanel.vue';
import AnalysisCommandModals from '@/components/analysis/AnalysisCommandModals.vue';
import { useAnalysisOperations } from '@/composables/useAnalysisOperations';
import { useAnalysisCommandNavigation } from '@/composables/useAnalysisCommandNavigation';

type RiskPrediction = NonNullable<NamespaceDiagnosticsResponse['riskForecast']>['predictions'][number];
type TimelineItem = NamespaceDiagnosticsResponse['changeTimeline'][number];
type RunbookAction = NamespaceDiagnosticsResponse['runbookActions'][number];
type SignalFilter = 'all' | 'high' | 'actionable';
type WorkflowStatus = WorkflowStatusValue;
type AnalysisUiMode = 'beginner' | 'expert';

const clusters = ref<ClusterResponse[]>([]);
const jobCenter = useJobCenterStore();
const { waitForAnalysisJob } = useAnalysisOperations(jobCenter);
const route = useRoute();
const router = useRouter();
const { locale, t } = useI18n();
const applications = ref<ApplicationResponse[]>([]);
const namespaces = ref<KubernetesNamespaceResponse[]>([]);
const history = ref<AnalysisResponse[]>([]);
const namespaceDiagnostics = ref<NamespaceDiagnosticsResponse | null>(null);
const analysisMode = ref<'cluster' | 'namespace' | 'application'>('namespace');
const selectedClusterId = ref('');
const selectedNamespace = ref('default');
const selectedApplicationId = ref('');
const loading = ref(true);
const loadingHistory = ref(false);
const loadingNamespaces = ref(false);
const loadingDiagnostics = ref(false);
const running = ref(false);
const deletingAnalysisId = ref('');
const errorMessage = ref('');
const runFeedback = ref<{ tone: 'success' | 'error' | 'info'; message: string; detail?: string } | null>(null);
const selectedAnalysis = ref<AnalysisResponse | null>(null);
const { copyCommand, openCommandConsole } = useAnalysisCommandNavigation({
  router, route, selectedAnalysis, selectedClusterId, selectedNamespace, runFeedback
});
const feedbackDetail = ref<{ title: string; detail: string } | null>(null);
const analysisTextDetail = ref<{
  title: string;
  subtitle?: string;
  sections: Array<{ label: string; content: string }>;
} | null>(null);
const selectedLogTarget = ref<{
  mode: 'resource' | 'pod';
  resourceType?: string;
  resourceName: string;
  containerName?: string;
} | null>(null);
const selectedLogTailLines = ref(100);
const selectedLogContainerName = ref('');
const selectedLogs = ref<PodLogsResponse | null>(null);
const loadingLogs = ref(false);
const logErrorMessage = ref('');
const selectedDiagnosticResource = ref<NamespaceDiagnosticsResponse['problemResources'][number] | null>(null);
const signalFilter = ref<SignalFilter>('all');
const analysisUiMode = ref<AnalysisUiMode>('beginner');
const commandSafetyExpanded = ref(false);
const issueWorkflowState = ref<Record<string, WorkflowStatus>>({});
const runbookChecklist = ref<Record<string, boolean>>({});
const commandExecutions = ref<AnalysisCommandExecutionResponse[]>([]);
const commandExecuting = ref('');
const commandExecutionDetail = ref<AnalysisCommandExecutionResponse | null>(null);
const loadingAnalysisOperations = ref(false);
const manualChangeCommand = ref('');
const pendingChangeCommand = ref<AnalysisCommandPreviewResponse | null>(null);
const changeConfirmInput = ref('');

const filteredApplications = computed(() => applications.value.filter((application) =>
  !selectedClusterId.value || application.clusterId === selectedClusterId.value
));
const visibleHistory = computed(() => history.value.filter((item) => analysisMatchesCurrentSelection(item)));
const selectedApplication = computed(() => applications.value.find((application) => application.id === selectedApplicationId.value) ?? null);
const canRunNamespaceAnalysis = computed(() => Boolean(selectedClusterId.value && selectedNamespace.value.trim()) && !running.value);
const canRunApplicationAnalysis = computed(() => Boolean(selectedApplicationId.value) && !running.value);
const canRunClusterAnalysis = computed(() => Boolean(selectedClusterId.value) && !running.value);
const canRunSelectedAnalysis = computed(() => {
  if (analysisMode.value === 'cluster') {
    return canRunClusterAnalysis.value;
  }
  return analysisMode.value === 'namespace'
    ? canRunNamespaceAnalysis.value
    : canRunApplicationAnalysis.value;
});
const analysisReadinessSteps = computed(() => [
  {
    label: 'Cluster',
    detail: selectedClusterId.value ? selectedClusterName.value : '클러스터 선택 필요',
    status: selectedClusterId.value ? 'done' : 'active'
  },
  {
    label: 'Scope',
    detail: analysisMode.value === 'cluster'
      ? 'Cluster 전체'
      : analysisMode.value === 'application'
      ? selectedApplication.value?.name ?? '애플리케이션 선택 필요'
      : selectedNamespace.value.trim() || 'namespace 입력 필요',
    status: (analysisMode.value === 'cluster' ? Boolean(selectedClusterId.value) : analysisMode.value === 'application' ? Boolean(selectedApplication.value) : Boolean(selectedNamespace.value.trim()))
      ? 'done'
      : 'pending'
  },
  {
    label: 'Signals',
    detail: analysisMode.value === 'cluster'
      ? 'cluster inventory live 수집'
      : namespaceDiagnostics.value
      ? `${namespaceDiagnostics.value.resourceCount} resources · ${namespaceDiagnostics.value.warningEventCount} warnings`
      : '진단 대상 조회 대기',
    status: analysisMode.value === 'cluster' ? selectedClusterId.value ? 'active' : 'pending' : namespaceDiagnostics.value ? 'done' : selectedClusterId.value ? 'active' : 'pending'
  },
  {
    label: 'AI Analysis',
    detail: running.value ? '분석 중' : canRunSelectedAnalysis.value ? '실행 가능' : '조건 확인 필요',
    status: running.value ? 'active' : canRunSelectedAnalysis.value ? 'done' : 'pending'
  }
]);
const selectedAnalysisResult = computed(() => parseAnalysisResult(selectedAnalysis.value?.resultJson));
const selectedAnalysisLocale = computed(() =>
  selectedAnalysis.value?.locale || textValue(selectedAnalysisResult.value?.locale, 'ko-KR'),
);
const analysisLocaleMismatch = computed(() =>
  Boolean(selectedAnalysis.value && selectedAnalysisLocale.value !== locale.value),
);
const selectedAnalysisLanguageName = computed(() =>
  selectedAnalysisLocale.value === 'ko-KR' ? t('common.korean') : t('common.english'),
);
const activeLanguageName = computed(() =>
  locale.value === 'ko-KR' ? t('common.korean') : t('common.english'),
);
const isBeginnerAnalysisUi = computed(() => analysisUiMode.value === 'beginner');
const isExpertAnalysisUi = computed(() => analysisUiMode.value === 'expert');
const selectedAnalysisDiagnostics = computed(() => objectValue(selectedAnalysisResult.value?.analysisDiagnostics));
const selectedIncrementalAnalysis = computed(() => objectValue(selectedAnalysisResult.value?.incrementalAnalysis));
const selectedAnalysisComparison = computed(() => objectValue(selectedAnalysisResult.value?.analysisComparison));
const selectedAnalysisQuality = computed(() => objectValue(selectedAnalysisResult.value?.analysisQuality));
const selectedActionRecommendations = computed(() => objectValue(selectedAnalysisResult.value?.actionRecommendations));
const selectedConfidenceValidation = computed(() => objectValue(selectedAnalysisResult.value?.confidenceValidation));
const selectedEvidenceLedger = computed(() => objectValue(selectedAnalysisResult.value?.evidenceLedger));
const selectedCommandSafety = computed(() => objectValue(selectedAnalysisResult.value?.commandSafety));
const selectedEventNoise = computed(() => objectValue(selectedAnalysisResult.value?.eventNoiseReduction));
const selectedCorrelationMap = computed(() => objectValue(selectedAnalysisResult.value?.correlationMap));
const selectedActionWorkflow = computed(() => objectValue(selectedAnalysisResult.value?.actionWorkflow));
const selectedIssueGroupDeepDives = computed(() => arrayValue(selectedAnalysisResult.value?.issueGroupDeepDives));
const selectedConclusionValidation = computed(() => objectValue(selectedAnalysisResult.value?.conclusionValidation));
const selectedReanalysisPlan = computed(() => objectValue(selectedAnalysisResult.value?.reanalysisPlan));
const selectedCommandSafetyCommands = computed(() => arrayValue(selectedCommandSafety.value.commands));
const executableCommandSafetyCommands = computed(() => selectedCommandSafetyCommands.value
  .filter((command) => ['READ_ONLY', 'DIAGNOSE'].includes(textValue(command.safetyLevel, '').toUpperCase()))
  .slice(0, 8));
const changeCommandSafetyCommands = computed(() => selectedCommandSafetyCommands.value
  .filter((command) => textValue(command.safetyLevel, '').toUpperCase() === 'CHANGE')
  .slice(0, 6));
const visibleCommandSafetyCommands = computed(() =>
  commandSafetyExpanded.value
    ? selectedCommandSafetyCommands.value
    : selectedCommandSafetyCommands.value.slice(0, 8)
);
const hiddenCommandSafetyCount = computed(() =>
  Math.max(0, selectedCommandSafetyCommands.value.length - visibleCommandSafetyCommands.value.length)
);
const workflowStatuses = computed(() => {
  const statuses = arrayValue(selectedActionWorkflow.value.statuses);
  return statuses.length
    ? statuses
    : [
      { value: 'OPEN', label: '열림', description: '아직 담당자가 확인하지 않은 상태' },
      { value: 'INVESTIGATING', label: '확인 중', description: '근거와 영향 범위를 확인하는 상태' },
      { value: 'ACTION_PENDING', label: '조치 대기', description: '변경 작업 승인 또는 실행을 기다리는 상태' },
      { value: 'FIXED', label: '해결', description: '조치 후 검증이 끝난 상태' },
      { value: 'ACCEPTED', label: '수용', description: '위험을 인지하고 운영 기준상 허용한 상태' }
    ];
});
const workflowIssueGroups = computed(() => arrayValue(selectedAnalysisResult.value?.issueGroups));
const runbookChecklistItems = computed(() => {
  const items: AnalysisResult[] = [];
  arrayValue(objectValue(selectedAnalysisResult.value?.remediationPlan).stages).forEach((stage) => {
    arrayValue(stage.commands).forEach((command) => {
      items.push({
        source: textValue(stage.title, 'Remediation'),
        label: textValue(command.label, '검증 명령'),
        command: textValue(command.command, ''),
        why: textValue(command.why || stage.objective, ''),
        riskLevel: textValue(stage.riskLevel, 'LOW')
      });
    });
  });
  safeRunbookActions(selectedAnalysisResult.value?.runbookActions).forEach((action) => {
    items.push({
      source: 'Runbook',
      label: textValue(action.title, '운영 명령'),
      command: textValue(action.command, ''),
      why: textValue(action.reason || action.description, ''),
      riskLevel: textValue(action.riskLevel || action.priority, 'LOW')
    });
  });
  return items.filter((item) => textValue(item.command, '').trim()).slice(0, 12);
});
const checkedRunbookChecklistCount = computed(() =>
  runbookChecklistItems.value.filter((item, index) => runbookChecklist.value[runbookChecklistKey(item, index)]).length
);
const selectedNextActions = computed(() => {
  const directActions = normalizeNextActionItems(selectedAnalysisResult.value?.nextActions);
  return directActions.length ? directActions : fallbackNextActionItems();
});
const selectedClusterName = computed(() => clusters.value.find((cluster) => cluster.id === selectedClusterId.value)?.name ?? '');
const historyScopeLabel = computed(() => {
  if (!selectedClusterId.value) {
    return '클러스터 선택 필요';
  }
  if (analysisMode.value === 'application' && selectedApplication.value) {
    return `${selectedClusterName.value || 'Cluster'} · ${selectedApplication.value.name}`;
  }
  if (analysisMode.value === 'namespace') {
    return `${selectedClusterName.value || 'Cluster'} · ${selectedNamespace.value || 'namespace'}`;
  }
  return `${selectedClusterName.value || 'Cluster'} · 전체 분석`;
});
const selectedAnalysisApplicationCandidates = computed(() =>
  applications.value
    .filter((application) => applicationMatchesAnalysis(application))
    .slice(0, 4)
);
const selectedResourceEvents = computed(() => {
  const resource = selectedDiagnosticResource.value;
  const diagnostics = namespaceDiagnostics.value;
  if (!resource || !diagnostics) {
    return [];
  }
  return diagnostics.warningEvents
    .filter((event) => resourceMatchesEvent(resource, event))
    .sort((left, right) => timestampValue(right.eventTime) - timestampValue(left.eventTime));
});
const selectedResourceEvidence = computed(() => {
  const resource = selectedDiagnosticResource.value;
  const diagnostics = namespaceDiagnostics.value;
  if (!resource || !diagnostics) {
    return [];
  }
  return diagnostics.evidenceSignals.filter((evidence) =>
    Boolean(evidence.source?.includes(`${resource.resourceType}/`) || evidence.source?.includes(resource.resourceName))
  );
});
const selectedResourcePodLogs = computed(() => {
  const resource = selectedDiagnosticResource.value;
  const diagnostics = namespaceDiagnostics.value;
  if (!resource || !diagnostics) {
    return [];
  }
  if (resource.resourceType !== 'Pod') {
    return [];
  }
  return diagnostics.podLogSources.filter((log) => log.podName === resource.resourceName);
});
const selectedResourceRelatedReferences = computed(() => relatedReferencesForResource(selectedDiagnosticResource.value));
const logContainerOptions = computed(() => {
  const names = new Set<string>();
  if (selectedLogTarget.value?.containerName) {
    names.add(selectedLogTarget.value.containerName);
  }
  selectedLogs.value?.containers.forEach((container) => {
    if (container.containerName) {
      names.add(container.containerName);
    }
  });
  return Array.from(names).sort();
});
const filteredRiskPredictions = computed(() => {
  const predictions = namespaceDiagnostics.value?.riskForecast?.predictions ?? [];
  return predictions.filter(matchesSignalFilterForRisk).slice(0, 5);
});
const filteredTimeline = computed(() => (namespaceDiagnostics.value?.changeTimeline ?? [])
  .filter(matchesSignalFilterForTimeline)
  .slice(0, 8));
const filteredRunbookActions = computed(() => (namespaceDiagnostics.value?.runbookActions ?? [])
  .filter(matchesSignalFilterForRunbook)
  .slice(0, 8));
const runbookPrioritySummary = computed(() => {
  const actions = namespaceDiagnostics.value?.runbookActions ?? [];
  return {
    total: actions.length,
    urgent: actions.filter((action) => ['P0', 'P1', 'P2'].includes(String(action.priority ?? '').toUpperCase())).length,
    commands: actions.filter((action) => Boolean(action.command)).length
  };
});
const operationsSummary = computed(() => {
  const diagnostics = namespaceDiagnostics.value;
  const firstRisk = diagnostics?.riskForecast?.predictions[0];
  const firstProblem = diagnostics?.problemResources[0];
  const firstRunbook = diagnostics?.runbookActions[0];
  const firstTimeline = diagnostics?.changeTimeline[0];

  return {
    riskTitle: firstRisk ? predictionTargetLabel(firstRisk) : firstProblem ? `${firstProblem.resourceType}/${firstProblem.resourceName}` : '고위험 후보 낮음',
    riskDetail: displayText(firstRisk?.signal ?? firstProblem?.summaryJson ?? firstProblem?.status, '현재 즉시 조치가 필요한 위험 신호는 낮습니다.'),
    actionTitle: firstRunbook ? `${firstRunbook.priority || 'P3'} · ${firstRunbook.title || '검증 작업'}` : '기준선 점검',
    actionDetail: firstRunbook?.command ?? `kubectl get all,events -n ${selectedNamespace.value || 'default'}`,
    timelineTitle: firstTimeline?.title || firstTimeline?.category || '변화 신호 없음',
    timelineDetail: firstTimeline
      ? `${collectedAtLabel(firstTimeline.occurredAt)} · ${firstTimeline.suspectedChange || firstTimeline.detail || '-'}`
      : '상관 분석할 이벤트나 로그 변화가 없습니다.',
    postureTitle: diagnostics?.riskForecast
      ? `${diagnostics.riskForecast.riskLevel} · ${diagnostics.riskForecast.overallRisk}`
      : `Health ${diagnostics?.healthScore?.overall ?? '-'}`,
    postureDetail: displayText(diagnostics?.riskForecast?.summary, 'Kubernetes API 기반 진단 결과를 요약합니다.')
  };
});
const hasDiagnosticsSignals = computed(() => Boolean(
  namespaceDiagnostics.value
  && (
    namespaceDiagnostics.value.resourceCount > 0
    || namespaceDiagnostics.value.eventCount > 0
    || namespaceDiagnostics.value.podLogCount > 0
  )
));
const diagnosticsEmptyMessage = computed(() => {
  if (!selectedClusterId.value) {
    return '클러스터를 선택하면 namespace 진단 대상을 조회할 수 있습니다.';
  }
  if (!selectedNamespace.value.trim()) {
    return '분석할 namespace를 입력하거나 선택하세요.';
  }
  return '현재 namespace에서 표시할 리소스, 이벤트, 로그 신호가 없습니다. 대상 조회로 최신 상태를 다시 확인할 수 있습니다.';
});

onMounted(async () => {
  loadAnalysisUiMode();
  await loadPage();
});

watch(analysisUiMode, () => {
  persistAnalysisUiMode();
});

watch(selectedClusterId, async (clusterId) => {
  if (!clusterId) {
    namespaces.value = [];
    namespaceDiagnostics.value = null;
    selectedApplicationId.value = '';
    return;
  }
  await loadNamespaces(clusterId);
  syncSelectedApplication();
});

watch([selectedClusterId, selectedNamespace, analysisMode, selectedApplicationId], () => {
  void loadHistory();
});

watch([selectedClusterId, selectedNamespace], async ([clusterId, namespace]) => {
  if (analysisMode.value === 'cluster' || !clusterId || !namespace.trim()) {
    namespaceDiagnostics.value = null;
    return;
  }
  await loadNamespaceDiagnostics(clusterId, namespace.trim());
});

watch(analysisMode, async (mode) => {
  if (mode === 'cluster') {
    namespaceDiagnostics.value = null;
    return;
  }
  if (selectedClusterId.value && selectedNamespace.value.trim()) {
    await loadNamespaceDiagnostics(selectedClusterId.value, selectedNamespace.value.trim());
  }
});

/** loadPage 처리 결과를 조회해 반환한다. */
async function loadPage() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const [clusterResult, applicationResult] = await Promise.all([
      api.listClusters(),
      api.listApplications()
    ]);
    clusters.value = clusterResult;
    applications.value = applicationResult;
    if (!selectedClusterId.value && clusterResult.length > 0) {
      selectedClusterId.value = clusterResult[0].id;
    }
    applyRouteQuerySelection();
    syncSelectedApplication();
    await loadHistory();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'AI 분석 정보를 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

/** historyFilters 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function historyFilters() {
  const filters: { clusterId?: string; namespace?: string; applicationId?: string } = {};
  if (selectedClusterId.value) {
    filters.clusterId = selectedClusterId.value;
  }
  if (analysisMode.value !== 'cluster' && selectedNamespace.value.trim()) {
    filters.namespace = selectedNamespace.value.trim();
  }
  if (analysisMode.value === 'application' && selectedApplicationId.value) {
    filters.applicationId = selectedApplicationId.value;
  }
  return filters;
}

/** loadHistory 처리 결과를 조회해 반환한다. */
async function loadHistory() {
  if (!selectedClusterId.value) {
    history.value = [];
    return;
  }

  const filters = historyFilters();
  const requestKey = JSON.stringify(filters);
  loadingHistory.value = true;
  try {
    const result = await api.listAnalysisHistory(filters);
    if (requestKey !== JSON.stringify(historyFilters())) {
      return;
    }
    history.value = result;
    if (selectedAnalysis.value && !analysisMatchesCurrentSelection(selectedAnalysis.value)) {
      closeAnalysisDetail();
    }
  } catch (error) {
    runFeedback.value = {
      tone: 'error',
      message: '분석 목록 조회 실패',
      detail: error instanceof Error ? error.message : '분석 이력을 불러오지 못했습니다.'
    };
  } finally {
    if (requestKey === JSON.stringify(historyFilters())) {
      loadingHistory.value = false;
    }
  }
}

/** syncSelectedApplication 처리의 핵심 작업 흐름을 실행한다. */
function syncSelectedApplication() {
  if (filteredApplications.value.some((application) => application.id === selectedApplicationId.value)) {
    return;
  }
  selectedApplicationId.value = filteredApplications.value[0]?.id ?? '';
}

/** applyRouteQuerySelection 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function applyRouteQuerySelection() {
  const mode = typeof route.query.mode === 'string' ? route.query.mode : '';
  const applicationId = typeof route.query.applicationId === 'string' ? route.query.applicationId : '';
  const clusterId = typeof route.query.clusterId === 'string' ? route.query.clusterId : '';
  const namespace = typeof route.query.namespace === 'string' ? route.query.namespace : '';
  if (clusterId && clusters.value.some((cluster) => cluster.id === clusterId)) {
    selectedClusterId.value = clusterId;
  }
  if (mode === 'cluster') {
    analysisMode.value = 'cluster';
  }
  if (mode === 'namespace') {
    analysisMode.value = 'namespace';
  }
  if (mode === 'application') {
    analysisMode.value = 'application';
  }
  if (namespace) {
    selectedNamespace.value = namespace;
  }
  if (applicationId && applications.value.some((application) => application.id === applicationId)) {
    selectedApplicationId.value = applicationId;
    const application = applications.value.find((item) => item.id === applicationId);
    if (application?.clusterId) {
      selectedClusterId.value = application.clusterId;
    }
    if (application?.namespace) {
      selectedNamespace.value = application.namespace;
    }
  }
}

/** loadNamespaces 처리 결과를 조회해 반환한다. */
async function loadNamespaces(clusterId: string) {
  loadingNamespaces.value = true;
  try {
    namespaces.value = await api.listNamespaces(clusterId);
    if (namespaces.value.length > 0 && !namespaces.value.some((namespace) => namespace.name === selectedNamespace.value)) {
      selectedNamespace.value = namespaces.value[0].name;
    }
  } catch (error) {
    namespaces.value = [];
    runFeedback.value = {
      tone: 'error',
      message: 'namespace 조회 실패',
      detail: error instanceof Error ? error.message : 'namespace 목록을 불러오지 못했습니다.'
    };
  } finally {
    loadingNamespaces.value = false;
  }
}

/** loadNamespaceDiagnostics 처리 결과를 조회해 반환한다. */
async function loadNamespaceDiagnostics(clusterId = selectedClusterId.value, namespace = selectedNamespace.value.trim()) {
  if (!clusterId || !namespace) {
    namespaceDiagnostics.value = null;
    return;
  }

  loadingDiagnostics.value = true;
  try {
    namespaceDiagnostics.value = await api.getNamespaceDiagnostics(clusterId, namespace);
  } catch (error) {
    namespaceDiagnostics.value = null;
    runFeedback.value = {
      tone: 'error',
      message: '분석 대상 조회 실패',
      detail: error instanceof Error ? error.message : 'namespace 진단 대상을 불러오지 못했습니다.'
    };
  } finally {
    loadingDiagnostics.value = false;
  }
}

/** applyCompletedAnalysis 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function applyCompletedAnalysis(analysis: AnalysisResponse) {
  if (analysisMatchesCurrentSelection(analysis)) {
    history.value = [analysis, ...history.value.filter((item) => item.id !== analysis.id)];
  } else {
    void loadHistory();
  }
  selectedAnalysis.value = analysis;
}

/** runNamespaceAnalysis 처리의 핵심 작업 흐름을 실행한다. */
async function runNamespaceAnalysis() {
  if (!canRunNamespaceAnalysis.value) {
    return;
  }

  const namespace = selectedNamespace.value.trim();
  running.value = true;
  runFeedback.value = { tone: 'info', message: 'AI 분석 작업 생성 중' };
  try {
    const started = await api.startNamespaceAnalysis(selectedClusterId.value, namespace);
    jobCenter.registerJob({
      jobId: started.jobId,
      analysisId: started.analysisId,
      title: `Namespace 분석 · ${namespace}`,
      detail: selectedClusterName.value || selectedClusterId.value,
      type: 'AI_ANALYSIS'
    });
    runFeedback.value = {
      tone: 'info',
      message: 'AI 분석 작업 시작됨',
      detail: `jobId=${started.jobId}\nanalysisId=${started.analysisId}`
    };
    const analysis = await waitForAnalysisJob(started.jobId, started.analysisId);
    applyCompletedAnalysis(analysis);
    await loadNamespaceDiagnostics(selectedClusterId.value, namespace);
    runFeedback.value = {
      tone: analysis.status === 'FAILED' ? 'error' : 'success',
      message: analysis.status === 'FAILED' ? 'AI 분석 실패' : 'AI 분석 완료',
      detail: analysis.resultSummary || analysis.id
    };
  } catch (error) {
    runFeedback.value = {
      tone: 'error',
      message: 'AI 분석 실패',
      detail: error instanceof Error ? error.message : 'AI 분석을 실행하지 못했습니다.'
    };
  } finally {
    running.value = false;
  }
}

/** runApplicationAnalysis 처리의 핵심 작업 흐름을 실행한다. */
async function runApplicationAnalysis() {
  if (!canRunApplicationAnalysis.value) {
    return;
  }

  running.value = true;
  runFeedback.value = { tone: 'info', message: 'Application AI 분석 작업 생성 중' };
  try {
    const started = await api.startApplicationAnalysis(selectedApplicationId.value);
    jobCenter.registerJob({
      jobId: started.jobId,
      analysisId: started.analysisId,
      title: `Application 분석 · ${selectedApplication.value?.name ?? 'application'}`,
      detail: selectedApplication.value?.namespace ?? selectedClusterName.value,
      type: 'AI_ANALYSIS'
    });
    runFeedback.value = {
      tone: 'info',
      message: 'Application AI 분석 작업 시작됨',
      detail: `jobId=${started.jobId}\nanalysisId=${started.analysisId}`
    };
    const analysis = await waitForAnalysisJob(started.jobId, started.analysisId);
    applyCompletedAnalysis(analysis);
    if (analysis.clusterId && analysis.namespace) {
      selectedClusterId.value = analysis.clusterId;
      selectedNamespace.value = analysis.namespace;
      await loadNamespaceDiagnostics(analysis.clusterId, analysis.namespace);
    }
    runFeedback.value = {
      tone: analysis.status === 'FAILED' ? 'error' : 'success',
      message: analysis.status === 'FAILED' ? 'Application AI 분석 실패' : 'Application AI 분석 완료',
      detail: analysis.resultSummary || analysis.id
    };
  } catch (error) {
    runFeedback.value = {
      tone: 'error',
      message: 'Application AI 분석 실패',
      detail: error instanceof Error ? error.message : 'Application AI 분석을 실행하지 못했습니다.'
    };
  } finally {
    running.value = false;
  }
}

/** runClusterAnalysis 처리의 핵심 작업 흐름을 실행한다. */
async function runClusterAnalysis() {
  if (!canRunClusterAnalysis.value) {
    return;
  }

  running.value = true;
  runFeedback.value = { tone: 'info', message: 'Cluster AI 분석 작업 생성 중' };
  try {
    const started = await api.startClusterAnalysis(selectedClusterId.value);
    jobCenter.registerJob({
      jobId: started.jobId,
      analysisId: started.analysisId,
      title: `Cluster 분석 · ${selectedClusterName.value || 'cluster'}`,
      detail: selectedClusterId.value,
      type: 'AI_ANALYSIS'
    });
    runFeedback.value = {
      tone: 'info',
      message: 'Cluster AI 분석 작업 시작됨',
      detail: `jobId=${started.jobId}\nanalysisId=${started.analysisId}`
    };
    const analysis = await waitForAnalysisJob(started.jobId, started.analysisId);
    applyCompletedAnalysis(analysis);
    runFeedback.value = {
      tone: analysis.status === 'FAILED' ? 'error' : 'success',
      message: analysis.status === 'FAILED' ? 'Cluster AI 분석 실패' : 'Cluster AI 분석 완료',
      detail: analysis.resultSummary || analysis.id
    };
  } catch (error) {
    runFeedback.value = {
      tone: 'error',
      message: 'Cluster AI 분석 실패',
      detail: error instanceof Error ? error.message : 'Cluster AI 분석을 실행하지 못했습니다.'
    };
  } finally {
    running.value = false;
  }
}

/** runSelectedAnalysis 처리의 핵심 작업 흐름을 실행한다. */
async function runSelectedAnalysis() {
  if (analysisMode.value === 'cluster') {
    await runClusterAnalysis();
    return;
  }
  if (analysisMode.value === 'application') {
    await runApplicationAnalysis();
    return;
  }
  await runNamespaceAnalysis();
}

/** retrySelectedAnalysis 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function retrySelectedAnalysis() {
  if (!selectedAnalysis.value?.id || running.value) {
    return;
  }

  running.value = true;
  runFeedback.value = { tone: 'info', message: 'AI 분석 재시도 작업 생성 중' };
  try {
    const source = selectedAnalysis.value;
    const started = await api.retryAnalysis(source.id);
    jobCenter.registerJob({
      jobId: started.jobId,
      analysisId: started.analysisId,
      title: `AI 분석 재시도 · ${targetLabel(source)}`,
      detail: source.resultSummary || source.id,
      type: 'AI_ANALYSIS'
    });
    runFeedback.value = {
      tone: 'info',
      message: 'AI 분석 재시도 시작됨',
      detail: `jobId=${started.jobId}\nanalysisId=${started.analysisId}`
    };
    const analysis = await waitForAnalysisJob(started.jobId, started.analysisId);
    applyCompletedAnalysis(analysis);
    runFeedback.value = {
      tone: analysis.status === 'FAILED' ? 'error' : 'success',
      message: analysis.status === 'FAILED' ? 'AI 분석 재시도 실패' : 'AI 분석 재시도 완료',
      detail: analysis.resultSummary || analysis.id
    };
  } catch (error) {
    runFeedback.value = {
      tone: 'error',
      message: 'AI 분석 재시도 실패',
      detail: error instanceof Error ? error.message : 'AI 분석 재시도를 실행하지 못했습니다.'
    };
  } finally {
    running.value = false;
  }
}

/** openAnalysisDetail 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function openAnalysisDetail(analysis: AnalysisResponse) {
  commandSafetyExpanded.value = false;
  selectedAnalysis.value = analysis;
  loadAnalysisInteractionState(analysis);
  void loadAnalysisOperationState(analysis);
}

/** closeAnalysisDetail 처리 대상과 관련 상태를 안전하게 정리한다. */
function closeAnalysisDetail() {
  selectedAnalysis.value = null;
  analysisTextDetail.value = null;
  commandExecutionDetail.value = null;
  pendingChangeCommand.value = null;
  changeConfirmInput.value = '';
}

/** setAnalysisUiMode 처리 대상의 상태를 갱신한다. */
function setAnalysisUiMode(mode: AnalysisUiMode) {
  analysisUiMode.value = mode;
}

/** loadAnalysisUiMode 처리 결과를 조회해 반환한다. */
function loadAnalysisUiMode() {
  try {
    const saved = localStorage.getItem('k8s-aiops.analysis-ui-mode');
    if (saved === 'beginner' || saved === 'expert') {
      analysisUiMode.value = saved;
    }
  } catch {
    analysisUiMode.value = 'beginner';
  }
}

/** persistAnalysisUiMode 처리에 필요한 데이터를 생성하거나 저장한다. */
function persistAnalysisUiMode() {
  try {
    localStorage.setItem('k8s-aiops.analysis-ui-mode', analysisUiMode.value);
  } catch {
    // localStorage가 제한된 환경에서는 현재 화면에서만 유지한다.
  }
}

/** loadAnalysisOperationState 처리 결과를 조회해 반환한다. */
async function loadAnalysisOperationState(analysis: AnalysisResponse) {
  if (!analysis.id) {
    return;
  }
  loadingAnalysisOperations.value = true;
  try {
    const [executions, workflowStates] = await Promise.all([
      api.listAnalysisCommandExecutions(analysis.id),
      api.listAnalysisWorkflowStates(analysis.id)
    ]);
    if (selectedAnalysis.value?.id !== analysis.id) {
      return;
    }
    commandExecutions.value = executions;
    issueWorkflowState.value = workflowStates.reduce<Record<string, WorkflowStatus>>((accumulator, state) => {
      accumulator[state.issueGroupId] = state.status;
      return accumulator;
    }, issueWorkflowState.value);
  } catch (error) {
    runFeedback.value = {
      tone: 'error',
      message: '운영 상태 조회 실패',
      detail: error instanceof Error ? error.message : '분석 운영 상태를 불러오지 못했습니다.'
    };
  } finally {
    loadingAnalysisOperations.value = false;
  }
}

/** openFeedbackDetail 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function openFeedbackDetail() {
  if (!runFeedback.value?.detail) {
    return;
  }
  feedbackDetail.value = {
    title: runFeedback.value.message,
    detail: runFeedback.value.detail
  };
}

/** closeFeedbackDetail 처리 대상과 관련 상태를 안전하게 정리한다. */
function closeFeedbackDetail() {
  feedbackDetail.value = null;
}

/** openAnalysisTextDetail 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function openAnalysisTextDetail(title: string, subtitle: string, sections: Array<{ label: string; content: string }>) {
  analysisTextDetail.value = {
    title,
    subtitle,
    sections: sections.filter((section) => section.content.trim())
  };
}

/** closeAnalysisTextDetail 처리 대상과 관련 상태를 안전하게 정리한다. */
function closeAnalysisTextDetail() {
  analysisTextDetail.value = null;
}

/** openIssueGroupTextDetail 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function openIssueGroupTextDetail(group: AnalysisResult) {
  const evidence = stringArray(group.evidenceSummary);
  const references = arrayValue(group.relatedReferences).map((reference) =>
    `${textValue(reference.kind)}/${textValue(reference.name)} · ${textValue(reference.reason)}`
  );
  const affectedResources = stringArray(group.affectedResources);
  openAnalysisTextDetail(
    textValue(group.title, 'Issue Group 상세'),
    `${textValue(group.issueGroupId, '-')} · ${textValue(group.category, '-')}`,
    [
      { label: 'Root Cause', content: textValue(group.rootCause, '') },
      { label: '권장 다음 행동', content: textValue(group.recommendedNextAction, '') },
      { label: '주요 근거', content: evidence.join('\n\n') },
      { label: '참조 리소스', content: references.join('\n') },
      { label: '영향 리소스', content: affectedResources.join('\n') }
    ]
  );
}

/** openIssueGroupDeepDiveDetail 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function openIssueGroupDeepDiveDetail(group: AnalysisResult) {
  const deepDive = deepDiveForGroup(group);
  const conclusion = conclusionForGroup(group);
  const checks = arrayValue(deepDive.checks).map((check, index) => [
    `${index + 1}. ${textValue(check.title, '확인 항목')}`,
    `목적: ${textValue(check.objective, '-')}`,
    `성공 기준: ${textValue(check.successCriteria, '-')}`,
    `명령: ${textValue(check.command, '-')}`
  ].join('\n'));
  openAnalysisTextDetail(
    `진단 체크 · ${textValue(group.title, 'Issue Group')}`,
    `${textValue(group.issueGroupId, '-')} · ${textValue(group.category, '-')}`,
    [
      { label: '초보자 설명', content: textValue(deepDive.beginnerSummary || conclusion.beginnerExplanation, '') },
      { label: '검증 체크', content: checks.join('\n\n') },
      { label: 'AI 결론 검증', content: [
        `상태: ${textValue(conclusion.status, '-')}`,
        `확신도: ${numberValue(conclusion.confidenceScore, '0')}`,
        `부족한 근거: ${stringArray(conclusion.missingEvidence).join(', ') || '-'}`
      ].join('\n') },
      { label: '권장 재분석 조건', content: textValue(deepDive.reanalysisHint || conclusion.operatorMeaning, '') }
    ]
  );
}

/** analysisInteractionStorageKey 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function analysisInteractionStorageKey(prefix: string, analysisId = selectedAnalysis.value?.id) {
  return `k8s-aiops.${prefix}.${analysisId || 'draft'}`;
}

/** loadAnalysisInteractionState 처리 결과를 조회해 반환한다. */
function loadAnalysisInteractionState(analysis: AnalysisResponse) {
  try {
    const workflow = localStorage.getItem(analysisInteractionStorageKey('workflow', analysis.id));
    const checklist = localStorage.getItem(analysisInteractionStorageKey('checklist', analysis.id));
    issueWorkflowState.value = workflow ? JSON.parse(workflow) as Record<string, WorkflowStatus> : {};
    runbookChecklist.value = checklist ? JSON.parse(checklist) as Record<string, boolean> : {};
  } catch {
    issueWorkflowState.value = {};
    runbookChecklist.value = {};
  }
}

/** persistAnalysisInteractionState 처리에 필요한 데이터를 생성하거나 저장한다. */
function persistAnalysisInteractionState() {
  try {
    localStorage.setItem(analysisInteractionStorageKey('workflow'), JSON.stringify(issueWorkflowState.value));
    localStorage.setItem(analysisInteractionStorageKey('checklist'), JSON.stringify(runbookChecklist.value));
  } catch {
    // localStorage가 제한된 환경에서는 화면 상태만 유지한다.
  }
}

/** workflowStatusForGroup 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function workflowStatusForGroup(group: AnalysisResult): WorkflowStatus {
  const key = textValue(group.issueGroupId, '');
  const saved = key ? issueWorkflowState.value[key] : undefined;
  const initial = textValue(workflowItemForGroup(group).initialStatus || workflowItemForGroup(group).defaultStatus, 'OPEN').toUpperCase();
  return (saved || initial as WorkflowStatus) as WorkflowStatus;
}

/** setWorkflowStatus 처리 대상의 상태를 갱신한다. */
async function setWorkflowStatus(group: AnalysisResult, status: unknown) {
  const key = textValue(group.issueGroupId, '');
  if (!key) {
    return;
  }
  const nextStatus = textValue(status, 'OPEN').toUpperCase() as WorkflowStatus;
  issueWorkflowState.value = {
    ...issueWorkflowState.value,
    [key]: nextStatus
  };
  persistAnalysisInteractionState();
  if (!selectedAnalysis.value?.id) {
    return;
  }
  try {
    await api.updateAnalysisWorkflowState(selectedAnalysis.value.id, key, nextStatus);
  } catch (error) {
    runFeedback.value = {
      tone: 'error',
      message: '처리 상태 저장 실패',
      detail: error instanceof Error ? error.message : '서버에 처리 상태를 저장하지 못했습니다.'
    };
  }
}

/** workflowItemForGroup 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function workflowItemForGroup(group: AnalysisResult) {
  const issueGroupId = textValue(group.issueGroupId, '');
  return arrayValue(selectedActionWorkflow.value.items).find((item) => textValue(item.issueGroupId, '') === issueGroupId) ?? {};
}

/** workflowStatusLabel 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function workflowStatusLabel(status: unknown) {
  const value = textValue(status, '').toUpperCase();
  const found = workflowStatuses.value.find((item) => textValue(item.value, '').toUpperCase() === value);
  return textValue(found?.label, value || 'OPEN');
}

/** workflowStatusClass 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function workflowStatusClass(status: unknown) {
  const value = textValue(status, '').toUpperCase();
  return {
    info: value === 'OPEN',
    medium: value === 'INVESTIGATING',
    warning: value === 'ACTION_PENDING',
    low: value === 'FIXED' || value === 'ACCEPTED'
  };
}

/** deepDiveForGroup 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function deepDiveForGroup(group: AnalysisResult) {
  const issueGroupId = textValue(group.issueGroupId, '');
  return selectedIssueGroupDeepDives.value.find((item) => textValue(item.issueGroupId, '') === issueGroupId) ?? {};
}

/** conclusionForGroup 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function conclusionForGroup(group: AnalysisResult) {
  const issueGroupId = textValue(group.issueGroupId, '');
  return arrayValue(selectedConclusionValidation.value.conclusions).find((item) => textValue(item.issueGroupId, '') === issueGroupId) ?? {};
}

/** runbookChecklistKey 처리의 핵심 작업 흐름을 실행한다. */
function runbookChecklistKey(item: AnalysisResult, index: number) {
  return `${index}-${textValue(item.source, '-')}-${textValue(item.command, '-')}`;
}

/** toggleRunbookChecklist 처리 데이터를 화면 또는 API 표현으로 변환한다. */
function toggleRunbookChecklist(item: AnalysisResult, index: number) {
  const key = runbookChecklistKey(item, index);
  runbookChecklist.value = {
    ...runbookChecklist.value,
    [key]: !runbookChecklist.value[key]
  };
  persistAnalysisInteractionState();
}

/** normalizeNextActionItems 처리 데이터를 화면 또는 API 표현으로 변환한다. */
function normalizeNextActionItems(value: unknown): AnalysisResult[] {
  if (!Array.isArray(value)) {
    return [];
  }
  const items: AnalysisResult[] = [];
  value.forEach((item, index) => {
    if (typeof item === 'string') {
      const action = cleanActionText(item);
      if (action) {
        items.push({
          priority: index === 0 ? 'P1' : 'P2',
          ownerHint: 'operator',
          action,
          verification: ''
        });
      }
      return;
    }
    if (typeof item !== 'object' || item === null || Array.isArray(item)) {
      return;
    }
    const source = item as AnalysisResult;
    const action = firstActionText(
      source.action,
      source.nextAction,
      source.description,
      source.title,
      source.recommendation
    );
    if (!action) {
      return;
    }
    items.push({
      ...source,
      priority: cleanActionText(source.priority) || (index === 0 ? 'P1' : 'P2'),
      ownerHint: cleanActionText(source.ownerHint || source.owner) || 'operator',
      action,
      verification: cleanActionText(source.verification || source.verificationCommand)
    });
  });
  return items;
}

/** fallbackNextActionItems 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function fallbackNextActionItems(): AnalysisResult[] {
  const actions: AnalysisResult[] = [];
  const firstStage = arrayValue(objectValue(selectedAnalysisResult.value?.remediationPlan).stages)[0];
  if (firstStage) {
    const firstCommand = arrayValue(firstStage.commands)[0];
    actions.push({
      priority: 'P1',
      ownerHint: 'operator',
      action: firstActionText(firstStage.objective, firstStage.title, firstStage.expectedResult),
      verification: cleanActionText(firstCommand.command) || cleanActionText(firstCommand.label)
    });
  }

  const firstGroup = arrayValue(selectedAnalysisResult.value?.issueGroups)[0];
  if (firstGroup) {
    actions.push({
      priority: severityToPriority(firstGroup.severity),
      ownerHint: 'platform',
      action: firstActionText(firstGroup.recommendedNextAction, firstGroup.rootCause, firstGroup.title),
      verification: `관련 리소스: ${textValue(firstGroup.representativeResourceKind, 'Resource')}/${textValue(firstGroup.representativeResourceName, '-')}`
    });
  }

  const firstRunbook = safeRunbookActions(selectedAnalysisResult.value?.runbookActions)[0];
  if (firstRunbook) {
    actions.push({
      priority: cleanActionText(firstRunbook.priority) || 'P2',
      ownerHint: 'operator',
      action: firstActionText(firstRunbook.title, firstRunbook.reason),
      verification: cleanActionText(firstRunbook.command)
    });
  }

  const firstReanalysis = arrayValue(selectedReanalysisPlan.value.options)[0];
  if (firstReanalysis) {
    actions.push({
      priority: 'P3',
      ownerHint: 'operator',
      action: `검증 또는 조치 후 ${textValue(firstReanalysis.label, '같은 범위 재분석')}을 실행합니다.`,
      verification: cleanActionText(firstReanalysis.description)
    });
  }

  return actions.filter((item) => Boolean(cleanActionText(item.action))).slice(0, 4);
}

/** firstActionText 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function firstActionText(...values: unknown[]) {
  return values.map(cleanActionText).find(Boolean) || '';
}

/** cleanActionText 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function cleanActionText(value: unknown) {
  const text = textValue(value, '').trim();
  return /^[\s.\-·]+$/.test(text) ? '' : text;
}

/** severityToPriority 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function severityToPriority(value: unknown) {
  const severity = textValue(value, '').toUpperCase();
  if (severity === 'CRITICAL' || severity === 'HIGH') {
    return 'P1';
  }
  if (severity === 'MEDIUM') {
    return 'P2';
  }
  return 'P3';
}

/** openResourceLogs 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function openResourceLogs(resource: { resourceType: string; resourceName: string }) {
  selectedLogTarget.value = {
    mode: 'resource',
    resourceType: resource.resourceType,
    resourceName: resource.resourceName
  };
  selectedLogContainerName.value = '';
  await loadSelectedLogs();
}

/** openPodLogs 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function openPodLogs(log: { podName: string; containerName: string }) {
  selectedLogTarget.value = {
    mode: 'pod',
    resourceName: log.podName,
    containerName: log.containerName
  };
  selectedLogContainerName.value = log.containerName || '';
  await loadSelectedLogs();
}

/** openRunbookLogs 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function openRunbookLogs(action: RunbookAction) {
  if (!action.targetKind || !action.targetName || !canOpenRunbookLogs(action)) {
    return;
  }
  await openResourceLogs({
    resourceType: action.targetKind,
    resourceName: action.targetName
  });
}

/** canOpenRunbookLogs 처리 조건의 충족 여부를 판단한다. */
function canOpenRunbookLogs(action: RunbookAction) {
  return Boolean(
    action.targetKind
    && action.targetName
    && ['Pod', 'Deployment', 'StatefulSet', 'DaemonSet', 'ReplicaSet', 'Job', 'Service'].includes(action.targetKind)
  );
}

/** loadSelectedLogs 처리 결과를 조회해 반환한다. */
async function loadSelectedLogs() {
  if (!selectedLogTarget.value || !selectedClusterId.value || !selectedNamespace.value.trim()) {
    return;
  }

  loadingLogs.value = true;
  logErrorMessage.value = '';
  selectedLogs.value = null;
  try {
    selectedLogs.value = selectedLogTarget.value.mode === 'pod'
      ? await api.getPodLogs(
        selectedClusterId.value,
        selectedNamespace.value.trim(),
        selectedLogTarget.value.resourceName,
        selectedLogTailLines.value,
        selectedLogContainerName.value || undefined
      )
      : await api.getResourceLogs(
        selectedClusterId.value,
        selectedNamespace.value.trim(),
        selectedLogTarget.value.resourceType ?? 'Pod',
        selectedLogTarget.value.resourceName,
        selectedLogTailLines.value,
        selectedLogContainerName.value || undefined
      );
  } catch (error) {
    logErrorMessage.value = error instanceof Error ? error.message : '로그를 조회하지 못했습니다.';
  } finally {
    loadingLogs.value = false;
  }
}

/** closeLogs 처리 대상과 관련 상태를 안전하게 정리한다. */
function closeLogs() {
  selectedLogTarget.value = null;
  selectedLogs.value = null;
  logErrorMessage.value = '';
  selectedLogContainerName.value = '';
}

/** openResourceDetail 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function openResourceDetail(resource: NamespaceDiagnosticsResponse['problemResources'][number]) {
  selectedDiagnosticResource.value = resource;
}

/** closeResourceDetail 처리 대상과 관련 상태를 안전하게 정리한다. */
function closeResourceDetail() {
  selectedDiagnosticResource.value = null;
}

/** logTargetLabel 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function logTargetLabel() {
  if (!selectedLogTarget.value) {
    return '-';
  }
  if (selectedLogTarget.value.mode === 'pod') {
    return `Pod/${selectedLogTarget.value.resourceName}`;
  }
  return `${selectedLogTarget.value.resourceType}/${selectedLogTarget.value.resourceName}`;
}

/** targetLabel 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function targetLabel(item: AnalysisResponse) {
  if (item.applicationId) {
    const application = applications.value.find((candidate) => candidate.id === item.applicationId);
    return application ? `Application ${application.name}` : `Application ${item.applicationId}`;
  }
  if (item.namespace) {
    return `Namespace ${item.namespace}`;
  }
  return item.clusterId ? `Cluster ${item.clusterId}` : 'Target';
}

/** analysisMatchesCurrentSelection 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function analysisMatchesCurrentSelection(item: AnalysisResponse) {
  if (selectedClusterId.value && item.clusterId !== selectedClusterId.value) {
    return false;
  }
  if (analysisMode.value === 'cluster') {
    return true;
  }
  if (analysisMode.value === 'application' && selectedApplicationId.value) {
    return item.applicationId === selectedApplicationId.value;
  }
  if (selectedNamespace.value.trim()) {
    return item.namespace === selectedNamespace.value.trim();
  }
  return true;
}

/** deleteAnalysis 처리 대상과 관련 상태를 안전하게 정리한다. */
async function deleteAnalysis(item: AnalysisResponse) {
  if (!item.id || deletingAnalysisId.value) {
    return;
  }
  const confirmed = window.confirm(`분석 이력을 삭제할까요?\n${targetLabel(item)}\n${item.id}`);
  if (!confirmed) {
    return;
  }

  deletingAnalysisId.value = item.id;
  try {
    await api.deleteAnalysis(item.id);
    history.value = history.value.filter((candidate) => candidate.id !== item.id);
    if (selectedAnalysis.value?.id === item.id) {
      closeAnalysisDetail();
    }
    runFeedback.value = {
      tone: 'success',
      message: '분석 이력 삭제 완료',
      detail: item.id
    };
  } catch (error) {
    runFeedback.value = {
      tone: 'error',
      message: '분석 이력 삭제 실패',
      detail: error instanceof Error ? error.message : '분석 이력을 삭제하지 못했습니다.'
    };
  } finally {
    deletingAnalysisId.value = '';
  }
}

/** applicationMatchesAnalysis 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function applicationMatchesAnalysis(application: ApplicationResponse) {
  if (!selectedAnalysis.value) {
    return false;
  }
  if (selectedAnalysis.value.applicationId === application.id) {
    return true;
  }
  if (selectedAnalysis.value.clusterId && application.clusterId && selectedAnalysis.value.clusterId !== application.clusterId) {
    return false;
  }
  if (selectedAnalysis.value.namespace && application.namespace && selectedAnalysis.value.namespace !== application.namespace) {
    return false;
  }
  const haystack = analysisTextHaystack();
  return Boolean(application.name && haystack.includes(application.name.toLowerCase()));
}

/** analysisTextHaystack 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function analysisTextHaystack() {
  const parts = [
    selectedAnalysis.value?.resultSummary,
    selectedAnalysis.value?.namespace,
    selectedAnalysis.value?.applicationId,
    selectedAnalysis.value?.resultJson
  ].filter(Boolean);
  return parts.join(' ').toLowerCase();
}

/** openApplicationOperations 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function openApplicationOperations(application: ApplicationResponse) {
  router.push({
    path: '/applications',
    query: { applicationId: application.id }
  });
}

/** runApplicationAnalysisFromResult 처리의 핵심 작업 흐름을 실행한다. */
function runApplicationAnalysisFromResult(application: ApplicationResponse) {
  analysisMode.value = 'application';
  selectedClusterId.value = application.clusterId || selectedClusterId.value;
  selectedNamespace.value = application.namespace || selectedNamespace.value;
  selectedApplicationId.value = application.id;
  selectedAnalysis.value = null;
  router.replace({
    path: '/analysis',
    query: {
      mode: 'application',
      applicationId: application.id,
      clusterId: application.clusterId
    }
  });
}

/** modelLabel 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function modelLabel(item: AnalysisResponse) {
  return [item.aiProvider, item.aiModel].filter(Boolean).join(' / ') || '-';
}

/** formattedResultJson 처리 데이터를 화면 또는 API 표현으로 변환한다. */
function formattedResultJson(item: AnalysisResponse | null) {
  return formattedAnalysisJson(item?.resultJson);
}

/** textValue 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function textValue(value: unknown, fallback = '-') {
  return displayText(value, fallback);
}

/** numberValue 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function numberValue(value: unknown, fallback = '-') {
  return typeof value === 'number' ? String(value) : fallback;
}

/** signedNumberValue 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function signedNumberValue(value: unknown, fallback = '0') {
  if (typeof value !== 'number') {
    return fallback;
  }
  return value > 0 ? `+${value}` : String(value);
}

/** severityClass 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function severityClass(severity: unknown) {
  const value = String(severity ?? '').toUpperCase();
  return {
    critical: value === 'CRITICAL',
    high: value === 'HIGH',
    medium: value === 'MEDIUM',
    low: value === 'LOW',
    info: value === 'INFO'
  };
}

/** applicationStatusClass 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function applicationStatusClass(status: unknown) {
  const value = String(status ?? '').toUpperCase();
  return {
    low: ['RUNNING', 'READY', 'DEPLOYED'].includes(value),
    medium: ['DEPLOY_REQUESTED', 'UNKNOWN'].includes(value),
    critical: ['FAILED', 'DEGRADED'].includes(value),
    info: Boolean(value) && !['RUNNING', 'READY', 'DEPLOYED', 'DEPLOY_REQUESTED', 'UNKNOWN', 'FAILED', 'DEGRADED'].includes(value)
  };
}

/** riskLevelClass 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function riskLevelClass(level: unknown) {
  return severityClass(level);
}

/** trendClass 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function trendClass(trend: unknown) {
  const value = textValue(trend, '').toUpperCase();
  return {
    high: value === 'DEGRADED',
    low: value === 'IMPROVED',
    info: value === 'UNCHANGED' || value === 'BASELINE'
  };
}

/** trendLabel 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function trendLabel(trend: unknown) {
  const value = textValue(trend, '').toUpperCase();
  if (value === 'DEGRADED') {
    return '악화';
  }
  if (value === 'IMPROVED') {
    return '개선';
  }
  if (value === 'BASELINE') {
    return '기준선';
  }
  return '유지';
}

/** matchesSignalFilterForRisk 처리 조건의 충족 여부를 판단한다. */
function matchesSignalFilterForRisk(prediction: RiskPrediction) {
  if (signalFilter.value === 'all') {
    return true;
  }
  if (signalFilter.value === 'high') {
    return isHighSignal(prediction.severity) || Number(prediction.probability ?? 0) >= 70;
  }
  return Boolean(prediction.verificationCommand);
}

/** matchesSignalFilterForTimeline 처리 조건의 충족 여부를 판단한다. */
function matchesSignalFilterForTimeline(item: TimelineItem) {
  if (signalFilter.value === 'all') {
    return true;
  }
  if (signalFilter.value === 'high') {
    return isHighSignal(item.severity) || ['degradation', 'current-state', 'log-signal'].includes(String(item.category ?? '').toLowerCase());
  }
  return Boolean(item.suspectedChange || item.detail);
}

/** matchesSignalFilterForRunbook 처리 조건의 충족 여부를 판단한다. */
function matchesSignalFilterForRunbook(action: RunbookAction) {
  if (signalFilter.value === 'all') {
    return true;
  }
  if (signalFilter.value === 'high') {
    return ['P0', 'P1', 'P2'].includes(String(action.priority ?? '').toUpperCase());
  }
  return Boolean(action.command);
}

/** isHighSignal 처리 조건의 충족 여부를 판단한다. */
function isHighSignal(value: unknown) {
  return ['CRITICAL', 'HIGH', 'ERROR', 'WARN', 'WARNING'].includes(String(value ?? '').toUpperCase());
}

/** predictionTargetLabel 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function predictionTargetLabel(prediction: RiskPrediction) {
  const kind = prediction.resourceKind || 'Resource';
  const name = prediction.resourceName && prediction.resourceName !== '-' ? prediction.resourceName : 'namespace';
  return `${kind}/${name}`;
}

/** executeAnalysisCommand 처리의 핵심 작업 흐름을 실행한다. */
async function executeAnalysisCommand(command?: string, confirmText?: string) {
  if (!selectedAnalysis.value?.id || !command) {
    return;
  }
  const normalizedCommand = command.split('${namespace}').join(selectedNamespace.value.trim() || selectedAnalysis.value.namespace || 'default');
  commandExecuting.value = normalizedCommand;
  runFeedback.value = {
    tone: 'info',
    message: '검증 명령 실행 중',
    detail: normalizedCommand
  };
  try {
    const execution = await api.executeAnalysisCommand(selectedAnalysis.value.id, normalizedCommand, confirmText);
    commandExecutions.value = [execution, ...commandExecutions.value.filter((item) => item.id !== execution.id)];
    commandExecutionDetail.value = execution;
    runFeedback.value = {
      tone: execution.status === 'SUCCEEDED' ? 'success' : execution.status === 'BLOCKED' ? 'info' : 'error',
      message: execution.status === 'SUCCEEDED' ? '검증 명령 실행 완료' : execution.status === 'BLOCKED' ? '위험 명령 차단됨' : '검증 명령 실행 실패',
      detail: execution.stderrText || execution.stdoutText || execution.reason || execution.command
    };
  } catch (error) {
    const detail = error instanceof Error ? error.message : '명령 실행 API 호출에 실패했습니다.';
    runFeedback.value = {
      tone: 'error',
      message: '검증 명령 실행 실패',
      detail
    };
    feedbackDetail.value = {
      title: '검증 명령 실행 실패',
      detail
    };
  } finally {
    commandExecuting.value = '';
  }
}

/** previewChangeCommand 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function previewChangeCommand(command?: string) {
  if (!selectedAnalysis.value?.id || !command?.trim()) {
    return;
  }
  const normalizedCommand = command.split('${namespace}').join(selectedNamespace.value.trim() || selectedAnalysis.value.namespace || 'default').trim();
  commandExecuting.value = normalizedCommand;
  try {
    const preview = await api.previewAnalysisCommand(selectedAnalysis.value.id, normalizedCommand);
    pendingChangeCommand.value = preview;
    changeConfirmInput.value = '';
    if (!preview.executable) {
      runFeedback.value = {
        tone: 'error',
        message: '변경 명령 실행 불가',
        detail: preview.reason || normalizedCommand
      };
    }
  } catch (error) {
    runFeedback.value = {
      tone: 'error',
      message: '변경 명령 확인 실패',
      detail: error instanceof Error ? error.message : '명령 확인 API 호출에 실패했습니다.'
    };
  } finally {
    commandExecuting.value = '';
  }
}

/** executePendingChangeCommand 처리의 핵심 작업 흐름을 실행한다. */
async function executePendingChangeCommand() {
  if (!pendingChangeCommand.value) {
    return;
  }
  await executeAnalysisCommand(pendingChangeCommand.value.command, changeConfirmInput.value);
  pendingChangeCommand.value = null;
  changeConfirmInput.value = '';
}

/** closeChangeCommandModal 처리 대상과 관련 상태를 안전하게 정리한다. */
function closeChangeCommandModal() {
  pendingChangeCommand.value = null;
  changeConfirmInput.value = '';
}

/** openCommandExecutionDetail 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function openCommandExecutionDetail(execution: AnalysisCommandExecutionResponse) {
  commandExecutionDetail.value = execution;
}

/** closeCommandExecutionDetail 처리 대상과 관련 상태를 안전하게 정리한다. */
function closeCommandExecutionDetail() {
  commandExecutionDetail.value = null;
}

/** collectedAtLabel 처리의 핵심 작업 흐름을 실행한다. */
function collectedAtLabel(value?: string) {
  if (!value) {
    return '-';
  }
  return new Date(value).toLocaleString();
}

/** resourceMatchesEvent 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function resourceMatchesEvent(
  resource: NamespaceDiagnosticsResponse['problemResources'][number],
  event: NamespaceDiagnosticsResponse['warningEvents'][number]
) {
  return event.involvedKind === resource.resourceType && event.involvedName === resource.resourceName
    || event.involvedName === resource.resourceName;
}

/** timestampValue 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function timestampValue(value?: string) {
  return value ? new Date(value).getTime() : 0;
}

/** formattedResourceSummary 처리 데이터를 화면 또는 API 표현으로 변환한다. */
function formattedResourceSummary(resource: NamespaceDiagnosticsResponse['problemResources'][number] | null) {
  if (!resource?.summaryJson) {
    return '';
  }
  try {
    return JSON.stringify(JSON.parse(resource.summaryJson), null, 2);
  } catch {
    return resource.summaryJson;
  }
}

/** logAvailabilityMessage 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function logAvailabilityMessage(resource: NamespaceDiagnosticsResponse['problemResources'][number] | null) {
  if (!resource) {
    return '';
  }
  if (['Pod', 'Deployment', 'StatefulSet', 'DaemonSet', 'ReplicaSet', 'Job', 'Service'].includes(resource.resourceType)) {
    return '관련 Pod를 찾아 로그를 조회할 수 있습니다.';
  }
  return '이 리소스는 직접 로그를 갖지 않습니다. 관련 Event와 참조 Pod 상태를 우선 확인하세요.';
}

/** resourceTargetFromAnalysisItem 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function resourceTargetFromAnalysisItem(item: AnalysisResult) {
  const resourceType = displayText(
    item.resourceKind || item.representativeResourceKind || item.targetKind || item.kind || item.involvedKind,
    ''
  );
  const resourceName = displayText(
    item.resourceName || item.representativeResourceName || item.targetName || item.name || item.involvedName || item.podName,
    ''
  );
  if (!resourceType || !resourceName || resourceName === 'namespace' || resourceName === '-') {
    return extractResourceTargetFromText([
      item.cause,
      item.signal,
      item.pattern,
      item.title,
      item.command,
      ...(Array.isArray(item.evidence) ? item.evidence : [])
    ]);
  }
  return { resourceType, resourceName };
}

/** extractResourceTargetFromText 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function extractResourceTargetFromText(values: unknown[]) {
  const text = values.map((value) => String(value ?? '')).join(' ');
  const match = text.match(/\b(Pod|Deployment|StatefulSet|DaemonSet|ReplicaSet|Job|Service|ConfigMap|Secret|PersistentVolumeClaim|PVC)\/([A-Za-z0-9_.:-]+)/);
  if (!match) {
    return null;
  }
  return {
    resourceType: match[1] === 'PVC' ? 'PersistentVolumeClaim' : match[1],
    resourceName: match[2]
  };
}

/** findDiagnosticResource 처리 결과를 조회해 반환한다. */
function findDiagnosticResource(resourceType?: string, resourceName?: string) {
  if (!resourceType || !resourceName || !namespaceDiagnostics.value) {
    return null;
  }
  return namespaceDiagnostics.value.problemResources.find((resource) =>
    resource.resourceType === resourceType && resource.resourceName === resourceName
  ) ?? null;
}

/** openAnalysisResourceDrillDown 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function openAnalysisResourceDrillDown(item: AnalysisResult) {
  const target = resourceTargetFromAnalysisItem(item);
  if (!target) {
    runFeedback.value = {
      tone: 'info',
      message: '연결된 리소스 없음',
      detail: '이 분석 항목에서 바로 이동할 Kubernetes 리소스 kind/name을 찾지 못했습니다.'
    };
    return;
  }

  const resource = findDiagnosticResource(target.resourceType, target.resourceName);
  if (resource) {
    openResourceDetail(resource);
    return;
  }

  await openResourceLogs(target);
}

/** openAnalysisLogDrillDown 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function openAnalysisLogDrillDown(item: AnalysisResult) {
  const podName = displayText(item.podName || item.resourceName || item.representativeResourceName || item.targetName, '');
  const containerName = displayText(item.containerName, '');
  if (!podName) {
    await openAnalysisResourceDrillDown(item);
    return;
  }
  await openPodLogs({ podName, containerName });
}

/** openRunbookDrillDown 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function openRunbookDrillDown(item: AnalysisResult) {
  if (isRunbookDestructive(item)) {
    runFeedback.value = {
      tone: 'info',
      message: '위험 명령은 숨김 처리됨',
      detail: 'destructive runbook은 자동 실행 또는 로그 이동 대신 명령 내용을 확인한 뒤 별도 승인 흐름에서 다뤄야 합니다.'
    };
    return;
  }
  await openAnalysisResourceDrillDown(item);
}

/** isRunbookDestructive 처리 조건의 충족 여부를 판단한다. */
function isRunbookDestructive(item: AnalysisResult) {
  return Boolean(item.destructive) || textValue(item.commandType, '').toLowerCase() === 'destructive';
}

/** relatedReferencesForResource 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function relatedReferencesForResource(resource: NamespaceDiagnosticsResponse['problemResources'][number] | null) {
  if (!resource) {
    return [];
  }
  const references: Array<{ kind: string; name: string; reason: string }> = [];
  const summary = parseJsonObject(resource.summaryJson);
  const volumes = Array.isArray(summary.volumes) ? summary.volumes as AnalysisResult[] : [];
  volumes.forEach((volume) => {
    const type = textValue(volume.type, '');
    const sourceName = textValue(volume.sourceName, '');
    if (['ConfigMap', 'Secret', 'PersistentVolumeClaim'].includes(type) && sourceName) {
      references.push({
        kind: type,
        name: sourceName,
        reason: `${textValue(volume.name, 'volume')} volume 참조`
      });
    }
  });
  selectedResourceEvents.value.forEach((event) => {
    const message = event.message || '';
    const configMapMatch = message.match(/configmap "?([A-Za-z0-9_.-]+)"?/i);
    const secretMatch = message.match(/secret "?([A-Za-z0-9_.-]+)"?/i);
    const pvcMatch = message.match(/(?:persistentvolumeclaim|pvc) "?([A-Za-z0-9_.-]+)"?/i);
    if (configMapMatch) {
      references.push({ kind: 'ConfigMap', name: configMapMatch[1], reason: event.reason || 'FailedMount event' });
    }
    if (secretMatch) {
      references.push({ kind: 'Secret', name: secretMatch[1], reason: event.reason || 'FailedMount event' });
    }
    if (pvcMatch) {
      references.push({ kind: 'PersistentVolumeClaim', name: pvcMatch[1], reason: event.reason || 'FailedMount event' });
    }
  });
  return references.filter((reference, index, self) =>
    self.findIndex((item) => item.kind === reference.kind && item.name === reference.name) === index
  );
}

/** parseJsonObject 처리 데이터를 화면 또는 API 표현으로 변환한다. */
function parseJsonObject(value?: string): AnalysisResult {
  if (!value) {
    return {};
  }
  try {
    const parsed = JSON.parse(value);
    return typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed) ? parsed as AnalysisResult : {};
  } catch {
    return {};
  }
}

/** runbookCategoryLabel 처리의 핵심 작업 흐름을 실행한다. */
function runbookCategoryLabel(item: AnalysisResult) {
  const category = textValue(item.category, '').toLowerCase();
  if (category === 'diagnosis') {
    return '원인 확인';
  }
  if (category === 'safe-action') {
    return '안전 조치';
  }
  if (category === 'destructive' || isRunbookDestructive(item)) {
    return '위험 조치';
  }
  return '검증';
}

/** fixReadinessLabel 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function fixReadinessLabel(value: unknown) {
  const readiness = textValue(value, '').toUpperCase();
  if (readiness === 'READY_TO_FIX') {
    return '조치 가능';
  }
  if (readiness === 'NEEDS_VERIFICATION') {
    return '검증 필요';
  }
  if (readiness === 'OBSERVE') {
    return '관찰';
  }
  return textValue(value, '상태 확인');
}

/** confidenceClass 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function confidenceClass(value: unknown) {
  const level = textValue(value, '').toUpperCase();
  return {
    high: level === 'HIGH',
    medium: level === 'MEDIUM',
    low: level === 'LOW'
  };
}

/** validationStatusClass 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function validationStatusClass(value: unknown) {
  const status = textValue(value, '').toUpperCase();
  return {
    success: status === 'CONFIRMED',
    muted: status === 'MISSING',
    warning: status === 'INFERRED',
    info: !['CONFIRMED', 'MISSING', 'INFERRED'].includes(status)
  };
}

/** safetyClass 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function safetyClass(value: unknown) {
  const level = textValue(value, '').toUpperCase();
  return {
    success: level === 'READ_ONLY',
    warning: level === 'RISKY_CHANGE',
    critical: level === 'DESTRUCTIVE',
    info: !['READ_ONLY', 'RISKY_CHANGE', 'DESTRUCTIVE'].includes(level)
  };
}

/** safetyLabel 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function safetyLabel(value: unknown) {
  const level = textValue(value, '').toUpperCase();
  if (level === 'READ_ONLY') {
    return '읽기 전용';
  }
  if (level === 'RISKY_CHANGE') {
    return '변경 가능';
  }
  if (level === 'DESTRUCTIVE') {
    return '위험 조치';
  }
  return '검토 필요';
}

/** guardStateClass 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function guardStateClass(value: unknown) {
  if (value === true) {
    return 'success';
  }
  if (value === false) {
    return 'critical';
  }
  return 'muted';
}

/** guardStateLabel 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function guardStateLabel(value: unknown, neutralLabel = '대상 아님') {
  if (value === true) {
    return '통과';
  }
  if (value === false) {
    return '차단';
  }
  return neutralLabel;
}

/** isRollbackCommand 처리 조건의 충족 여부를 판단한다. */
function isRollbackCommand(command?: string) {
  return textValue(command, '').toLowerCase().includes(' rollout undo ');
}

/** readinessClass 처리 결과를 조회해 반환한다. */
function readinessClass(value: unknown) {
  const readiness = textValue(value, '').toUpperCase();
  return {
    ready: readiness === 'READY_TO_FIX',
    verify: readiness === 'NEEDS_VERIFICATION',
    observe: readiness === 'OBSERVE'
  };
}

/** safeRunbookActions 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function safeRunbookActions(value: unknown) {
  return arrayValue(value).filter((item) => !isRunbookDestructive(item));
}

/** destructiveRunbookActions 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function destructiveRunbookActions(value: unknown) {
  return arrayValue(value).filter(isRunbookDestructive);
}
</script>

<template>
  <section class="page">
    <header class="page-header">
      <div>
        <h1>{{ $t('pages.analysisTitle') }}</h1>
        <p>{{ $t('pages.analysisDescription') }}</p>
      </div>
      <button class="secondary-button" type="button" @click="loadPage">
        <i class="pi pi-refresh"></i>
        <span>{{ $t('common.refresh') }}</span>
      </button>
    </header>

    <div v-if="errorMessage" class="inline-error">
      <i class="pi pi-exclamation-triangle"></i>
      <span>{{ errorMessage }}</span>
    </div>

    <AnalysisRunPanel
      v-model:mode="analysisMode"
      v-model:cluster-id="selectedClusterId"
      v-model:namespace="selectedNamespace"
      v-model:application-id="selectedApplicationId"
      :clusters="clusters"
      :namespaces="namespaces"
      :applications="filteredApplications"
      :readiness-steps="analysisReadinessSteps"
      :loading="loading"
      :loading-namespaces="loadingNamespaces"
      :running="running"
      :can-run="canRunSelectedAnalysis"
      :feedback="runFeedback"
      @run="runSelectedAnalysis"
      @show-feedback="openFeedbackDetail"
    />

    <section class="analysis-diagnostics-panel">
      <header class="panel-header compact-panel-header">
        <div>
          <h2>분석 대상</h2>
          <p>
            <template v-if="analysisMode === 'cluster'">
              {{ selectedClusterName || '-' }} cluster ·
            </template>
            <template v-else>
              {{ selectedNamespace || '-' }} namespace ·
            </template>
            <template v-if="analysisMode === 'application' && selectedApplication">
              {{ selectedApplication.name }} application ·
            </template>
            {{ namespaceDiagnostics ? collectedAtLabel(namespaceDiagnostics.collectedAt) : '-' }}
          </p>
        </div>
        <button
          v-if="analysisMode !== 'cluster'"
          class="secondary-button compact-button"
          :disabled="!selectedClusterId || !selectedNamespace.trim() || loadingDiagnostics"
          type="button"
          @click="loadNamespaceDiagnostics()"
        >
          <i :class="loadingDiagnostics ? 'pi pi-spin pi-spinner' : 'pi pi-refresh'"></i>
          <span>대상 조회</span>
        </button>
      </header>

      <div v-if="loadingDiagnostics" class="empty-state compact">
        <i class="pi pi-spin pi-spinner"></i>
        <span>namespace 진단 대상을 수집하는 중입니다.</span>
      </div>
      <div v-else-if="analysisMode === 'cluster'" class="empty-state compact">
        <i class="pi pi-sitemap"></i>
        <span>Cluster AI 분석은 실행 시점에 전체 cluster inventory와 event를 live 수집해 분석합니다.</span>
      </div>
      <div v-else-if="!hasDiagnosticsSignals" class="empty-state compact">
        <i class="pi pi-search"></i>
        <span>{{ diagnosticsEmptyMessage }}</span>
      </div>
      <div v-else class="diagnostics-body">
        <div class="diagnostics-metrics">
          <div class="metric mini">
            <span class="label">Resources</span>
            <strong>{{ namespaceDiagnostics?.resourceCount ?? 0 }}</strong>
          </div>
          <div class="metric mini">
            <span class="label">Events</span>
            <strong>{{ namespaceDiagnostics?.eventCount ?? 0 }}</strong>
          </div>
          <div class="metric mini">
            <span class="label">Warnings</span>
            <strong>{{ namespaceDiagnostics?.warningEventCount ?? 0 }}</strong>
          </div>
          <div class="metric mini">
            <span class="label">Pod Logs</span>
            <strong>{{ namespaceDiagnostics?.podLogCount ?? 0 }}</strong>
          </div>
          <div class="metric mini">
            <span class="label">Health</span>
            <strong>{{ namespaceDiagnostics?.healthScore?.overall ?? '-' }}</strong>
          </div>
        </div>

        <div v-if="namespaceDiagnostics?.healthScore" class="health-score-grid">
          <span>Availability {{ namespaceDiagnostics.healthScore.availability }}</span>
          <span>Stability {{ namespaceDiagnostics.healthScore.stability }}</span>
          <span>Performance {{ namespaceDiagnostics.healthScore.performance }}</span>
          <span>Security {{ namespaceDiagnostics.healthScore.security }}</span>
          <span>Operability {{ namespaceDiagnostics.healthScore.operability }}</span>
        </div>

        <section class="ops-summary-grid" aria-label="AI 운영 요약">
          <article class="ops-summary-card">
            <span>Most Risky</span>
            <strong>{{ operationsSummary.riskTitle }}</strong>
            <small>{{ operationsSummary.riskDetail }}</small>
          </article>
          <article class="ops-summary-card">
            <span>Next Action</span>
            <strong>{{ operationsSummary.actionTitle }}</strong>
            <small>{{ operationsSummary.actionDetail }}</small>
          </article>
          <article class="ops-summary-card">
            <span>First Signal</span>
            <strong>{{ operationsSummary.timelineTitle }}</strong>
            <small>{{ operationsSummary.timelineDetail }}</small>
          </article>
          <article class="ops-summary-card">
            <span>Posture</span>
            <strong>{{ operationsSummary.postureTitle }}</strong>
            <small>{{ operationsSummary.postureDetail }}</small>
          </article>
        </section>

        <div class="signal-filter-bar" aria-label="분석 신호 필터">
          <button
            type="button"
            :class="{ active: signalFilter === 'all' }"
            @click="signalFilter = 'all'"
          >
            전체
          </button>
          <button
            type="button"
            :class="{ active: signalFilter === 'high' }"
            @click="signalFilter = 'high'"
          >
            고위험
          </button>
          <button
            type="button"
            :class="{ active: signalFilter === 'actionable' }"
            @click="signalFilter = 'actionable'"
          >
            실행가능
          </button>
        </div>

        <AnalysisRunbookPanel
          :actions="filteredRunbookActions"
          :total="runbookPrioritySummary.total"
          :urgent="runbookPrioritySummary.urgent"
          :commands="runbookPrioritySummary.commands"
          :can-open-logs="canOpenRunbookLogs"
          @copy="copyCommand"
          @open-console="openCommandConsole"
          @logs="openRunbookLogs"
        />

        <section v-if="namespaceDiagnostics?.riskForecast" class="risk-forecast-panel">
          <div class="risk-forecast-summary">
            <div>
              <span class="label">Risk Forecast</span>
              <strong>{{ namespaceDiagnostics.riskForecast.overallRisk }}</strong>
            </div>
            <span class="status-pill" :class="riskLevelClass(namespaceDiagnostics.riskForecast.riskLevel)">
              {{ namespaceDiagnostics.riskForecast.riskLevel }}
            </span>
            <p>{{ textValue(namespaceDiagnostics.riskForecast.summary) }}</p>
            <small>{{ namespaceDiagnostics.riskForecast.horizon }} · Kubernetes API 기반 예측</small>
          </div>

          <div v-if="filteredRiskPredictions.length" class="risk-prediction-list">
            <article
              v-for="prediction in filteredRiskPredictions"
              :key="`${prediction.category}-${prediction.resourceKind}-${prediction.resourceName}-${prediction.signal}`"
              class="risk-prediction-item"
            >
              <div class="diagnostic-row-heading">
                <strong>{{ predictionTargetLabel(prediction) }}</strong>
                <span class="status-pill" :class="severityClass(prediction.severity)">
                  {{ prediction.severity }} · {{ prediction.probability }}%
                </span>
              </div>
              <span>{{ textValue(prediction.signal) }}</span>
              <p>{{ textValue(prediction.impact) }}</p>
              <small>{{ textValue(prediction.recommendation) }}</small>
              <code v-if="prediction.verificationCommand">{{ prediction.verificationCommand }}</code>
            </article>
          </div>
          <p v-else class="muted-text">선택한 필터에 해당하는 위험 예측 항목이 없습니다.</p>
        </section>

        <section class="correlation-runbook-grid">
          <article class="diagnostics-section timeline-section">
            <h3>Change Timeline</h3>
            <ul v-if="filteredTimeline.length">
              <li v-for="item in filteredTimeline" :key="`${item.occurredAt}-${item.resourceKind}-${item.resourceName}-${item.title}`">
                <strong>{{ item.title || item.category }} · {{ item.resourceKind || '-' }}/{{ item.resourceName || '-' }}</strong>
                <span>{{ textValue(item.detail) }}</span>
                <small>{{ collectedAtLabel(item.occurredAt) }} · {{ textValue(item.suspectedChange) }}</small>
              </li>
            </ul>
            <p v-else>선택한 필터에 해당하는 이벤트 타임라인이 없습니다.</p>
          </article>
        </section>

        <div class="resource-kind-list">
          <span
            v-for="kind in namespaceDiagnostics?.resourceKinds"
            :key="kind.resourceType"
            class="status-pill info"
          >
            {{ kind.resourceType }} {{ kind.count }}
          </span>
        </div>

        <div class="diagnostics-columns">
          <article class="diagnostics-section">
            <h3>문제 후보 리소스</h3>
            <ul v-if="namespaceDiagnostics?.problemResources.length">
              <li v-for="resource in namespaceDiagnostics.problemResources" :key="`${resource.resourceType}-${resource.resourceName}`">
                <div class="diagnostic-row-heading">
                  <strong>{{ resource.resourceType }}/{{ resource.resourceName }}</strong>
                  <span class="diagnostic-row-actions">
                    <button
                      class="icon-button table-icon-button"
                      title="상세 진단"
                      aria-label="상세 진단"
                      type="button"
                      @click="openResourceDetail(resource)"
                    >
                      <i class="pi pi-search"></i>
                    </button>
                    <button
                      class="icon-button table-icon-button"
                      title="관련 Pod 로그 조회"
                      aria-label="관련 Pod 로그 조회"
                      type="button"
                      @click="openResourceLogs(resource)"
                    >
                      <i class="pi pi-list"></i>
                    </button>
                  </span>
                </div>
                <span>{{ resource.status }}</span>
              </li>
            </ul>
            <p v-else>즉시 문제로 보이는 리소스가 없습니다.</p>
          </article>

          <article class="diagnostics-section">
            <h3>Warning Events</h3>
            <ul v-if="namespaceDiagnostics?.warningEvents.length">
              <li v-for="event in namespaceDiagnostics.warningEvents" :key="`${event.reason}-${event.involvedKind}-${event.involvedName}-${event.message}`">
                <strong>{{ event.reason || 'Warning' }} · {{ event.involvedKind || '-' }}/{{ event.involvedName || '-' }}</strong>
                <span>{{ textValue(event.message) }}</span>
              </li>
            </ul>
            <p v-else>Warning 이벤트가 없습니다.</p>
          </article>

          <article class="diagnostics-section">
            <h3>Evidence</h3>
            <ul v-if="namespaceDiagnostics?.evidenceSignals.length">
              <li v-for="evidence in namespaceDiagnostics.evidenceSignals" :key="`${evidence.severity}-${evidence.source}-${evidence.message}`">
                <strong>{{ evidence.severity || 'INFO' }} · {{ evidence.source || '-' }}</strong>
                <span>{{ textValue(evidence.message) }}</span>
              </li>
            </ul>
            <p v-else>표시할 근거 신호가 없습니다.</p>
          </article>

          <article class="diagnostics-section">
            <h3>Pod Log Sources</h3>
            <ul v-if="namespaceDiagnostics?.podLogSources.length">
              <li v-for="log in namespaceDiagnostics.podLogSources" :key="`${log.podName}-${log.containerName}`">
                <div class="diagnostic-row-heading">
                  <strong>Pod/{{ log.podName }}</strong>
                  <button
                    class="icon-button table-icon-button"
                    title="Pod 로그 조회"
                    aria-label="Pod 로그 조회"
                    type="button"
                    @click="openPodLogs(log)"
                  >
                    <i class="pi pi-list"></i>
                  </button>
                </div>
                <span>{{ log.containerName }}{{ log.truncated ? ' · truncated' : '' }}</span>
              </li>
            </ul>
            <p v-else>수집된 Pod 로그 소스가 없습니다.</p>
          </article>
        </div>
      </div>
    </section>

    <div v-if="loading" class="empty-state">
      <i class="pi pi-spin pi-spinner"></i>
      <span>분석 화면을 준비하는 중입니다.</span>
    </div>
    <div v-else-if="loadingHistory" class="empty-state">
      <i class="pi pi-spin pi-spinner"></i>
      <span>{{ historyScopeLabel }} 기준 분석 이력을 불러오는 중입니다.</span>
    </div>
    <div v-else-if="visibleHistory.length === 0" class="empty-state">
      <i class="pi pi-sparkles"></i>
      <span>{{ historyScopeLabel }} 기준 분석 이력이 없습니다. 현재 선택한 범위로 AI 분석을 실행하세요.</span>
    </div>
    <div v-else class="table-panel">
      <header class="panel-header compact-panel-header">
        <div>
          <h2>분석 이력</h2>
          <p>{{ historyScopeLabel }} · {{ visibleHistory.length }}건</p>
        </div>
      </header>
      <table>
        <thead>
          <tr>
            <th>Target</th>
            <th>Status</th>
            <th>Model</th>
            <th>Summary</th>
            <th>Created</th>
            <th>Result</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in visibleHistory" :key="item.id">
            <td>
              <strong>{{ targetLabel(item) }}</strong>
              <small>{{ item.id }}</small>
            </td>
            <td><span class="status-pill">{{ item.status || 'SUCCEEDED' }}</span></td>
            <td>{{ modelLabel(item) }}</td>
            <td>{{ item.resultSummary || '-' }}</td>
            <td>{{ item.createdAt || '-' }}</td>
            <td>
              <span class="table-action-group">
                <button
                  class="icon-button table-icon-button"
                  title="분석 결과 상세"
                  aria-label="분석 결과 상세"
                  type="button"
                  @click="openAnalysisDetail(item)"
                >
                  <i class="pi pi-file"></i>
                </button>
                <button
                  class="icon-button table-icon-button danger"
                  :disabled="deletingAnalysisId === item.id"
                  title="분석 이력 삭제"
                  aria-label="분석 이력 삭제"
                  type="button"
                  @click="deleteAnalysis(item)"
                >
                  <i :class="deletingAnalysisId === item.id ? 'pi pi-spin pi-spinner' : 'pi pi-trash'"></i>
                </button>
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="selectedAnalysis" class="modal-backdrop" @click.self="closeAnalysisDetail">
      <section class="modal-panel feedback-detail-modal" aria-modal="true" role="dialog">
        <header class="modal-header">
          <div>
            <h2>분석 결과</h2>
            <p>{{ targetLabel(selectedAnalysis) }}</p>
          </div>
          <div class="analysis-mode-switch" role="group" aria-label="분석 결과 보기 모드">
            <button
              type="button"
              :class="{ active: isBeginnerAnalysisUi }"
              title="초보자용 설명 중심 보기"
              @click="setAnalysisUiMode('beginner')"
            >
              <i class="pi pi-compass"></i>
              초보자용
            </button>
            <button
              type="button"
              :class="{ active: isExpertAnalysisUi }"
              title="숙련자용 근거와 명령 중심 보기"
              @click="setAnalysisUiMode('expert')"
            >
              <i class="pi pi-terminal"></i>
              숙련자용
            </button>
          </div>
          <button class="icon-button" title="닫기" type="button" @click="closeAnalysisDetail">
            <i class="pi pi-times"></i>
          </button>
        </header>
        <div class="feedback-detail-body">
          <div v-if="analysisLocaleMismatch" class="inline-feedback warning analysis-locale-warning" role="status">
            <i class="pi pi-language" aria-hidden="true"></i>
            <div>
              <strong>{{ t('analysis.resultLanguage', { language: selectedAnalysisLanguageName }) }}</strong>
              <span>{{ t('analysis.localeMismatch') }}</span>
            </div>
            <button class="secondary-button" type="button" :disabled="running" @click="retrySelectedAnalysis">
              <i class="pi pi-refresh" :class="{ 'pi-spin': running }"></i>
              <span>{{ t('analysis.reanalyze', { language: activeLanguageName }) }}</span>
            </button>
          </div>
          <template v-if="selectedAnalysisResult">
            <AnalysisSummaryOverview
              :result="selectedAnalysisResult"
              :diagnostics="selectedAnalysisDiagnostics"
              :incremental="selectedIncrementalAnalysis"
              :locale-name="selectedAnalysisLanguageName"
              :mode="analysisUiMode"
              @mode-change="setAnalysisUiMode"
            />

            <section
              v-if="Object.keys(selectedAnalysisQuality).length"
              class="analysis-quality-panel"
              aria-label="분석 품질 점수"
            >
              <header>
                <div>
                  <span class="label">Quality Gate</span>
                  <h3>{{ isBeginnerAnalysisUi ? '이 분석 결과를 운영에 써도 될까요?' : 'Analysis Quality' }}</h3>
                  <p>{{ textValue(isBeginnerAnalysisUi ? selectedAnalysisQuality.beginnerSummary || selectedAnalysisQuality.summary : selectedAnalysisQuality.summary || selectedAnalysisQuality.beginnerSummary) }}</p>
                </div>
                <div class="analysis-quality-score">
                  <strong>{{ numberValue(selectedAnalysisQuality.score, '0') }}</strong>
                  <span class="analysis-chip" :class="confidenceClass(selectedAnalysisQuality.level)">
                    {{ textValue(selectedAnalysisQuality.level, 'MEDIUM') }}
                  </span>
                </div>
              </header>
              <div class="analysis-quality-dimensions">
                <article
                  v-for="dimension in arrayValue(selectedAnalysisQuality.dimensions)"
                  :key="`quality-${textValue(dimension.name)}`"
                >
                  <span class="analysis-chip" :class="confidenceClass(dimension.level)">
                    {{ textValue(dimension.level, 'INFO') }}
                  </span>
                  <strong>{{ textValue(dimension.name) }}</strong>
                  <b>{{ numberValue(dimension.score, '0') }}</b>
                  <small>{{ textValue(dimension.explanation) }}</small>
                </article>
              </div>
              <ul v-if="stringArray(selectedAnalysisQuality.qualityRisks).length" class="analysis-quality-risk-list">
                <li v-for="risk in stringArray(selectedAnalysisQuality.qualityRisks).slice(0, isExpertAnalysisUi ? 6 : 3)" :key="risk">
                  {{ risk }}
                </li>
              </ul>
            </section>

            <section v-if="selectedAnalysisApplicationCandidates.length" class="analysis-application-link-panel" aria-label="관련 애플리케이션">
              <header>
                <div>
                  <span class="label">Application Operations</span>
                  <h3>관련 애플리케이션으로 이어서 확인</h3>
                  <p>분석 결과와 같은 cluster/namespace 또는 리소스 이름으로 연결되는 Application입니다.</p>
                </div>
                <span class="analysis-chip">{{ selectedAnalysisApplicationCandidates.length }} apps</span>
              </header>
              <div class="analysis-application-link-list">
                <article
                  v-for="application in selectedAnalysisApplicationCandidates"
                  :key="`analysis-app-${application.id}`"
                >
                  <div>
                    <strong>{{ application.name }}</strong>
                    <span>{{ application.namespace || '-' }} · {{ application.deploymentType || '-' }}</span>
                    <small>{{ application.id }}</small>
                  </div>
                  <span class="status-pill" :class="applicationStatusClass(application.status)">
                    {{ application.status || 'UNKNOWN' }}
                  </span>
                  <div class="analysis-application-link-actions">
                    <button class="secondary-button compact-button" type="button" @click="openApplicationOperations(application)">
                      <i class="pi pi-box"></i>
                      운영
                    </button>
                    <button class="primary-button compact-button" type="button" @click="runApplicationAnalysisFromResult(application)">
                      <i class="pi pi-chart-line"></i>
                      앱 분석
                    </button>
                  </div>
                </article>
              </div>
            </section>

            <section v-if="isExpertAnalysisUi" class="analysis-expert-fast-lane" aria-label="숙련자 빠른 진입">
              <article>
                <span class="label">Top Logs</span>
                <strong>고신호 로그</strong>
                <ul v-if="arrayValue(objectValue(selectedAnalysisResult.logIntelligence).items).length">
                  <li
                    v-for="item in arrayValue(objectValue(selectedAnalysisResult.logIntelligence).items).slice(0, 3)"
                    :key="`expert-log-${textValue(item.podName)}-${textValue(item.category)}-${textValue(item.signal)}`"
                  >
                    <span class="status-pill" :class="severityClass(item.severity)">{{ textValue(item.severity, 'INFO') }}</span>
                    <code>{{ textValue(item.category) }} · {{ textValue(item.podName) }}</code>
                    <small>{{ textValue(item.signal) }}</small>
                  </li>
                </ul>
                <p v-else>고신호 로그 없음</p>
              </article>
              <article>
                <span class="label">Issue Groups</span>
                <strong>우선 원인 후보</strong>
                <ul v-if="arrayValue(selectedAnalysisResult.issueGroups).length">
                  <li
                    v-for="group in arrayValue(selectedAnalysisResult.issueGroups).slice(0, 3)"
                    :key="`expert-group-${textValue(group.issueGroupId)}`"
                  >
                    <span class="status-pill" :class="severityClass(group.severity)">{{ textValue(group.severity, 'INFO') }}</span>
                    <code>{{ textValue(group.issueGroupId) }} · {{ textValue(group.category) }}</code>
                    <small>{{ textValue(group.title) }}</small>
                  </li>
                </ul>
                <p v-else>원인 그룹 없음</p>
              </article>
              <article>
                <span class="label">Commands</span>
                <strong>즉시 검증</strong>
                <div class="analysis-expert-command-list" v-if="executableCommandSafetyCommands.length">
                  <button
                    v-for="command in executableCommandSafetyCommands.slice(0, 3)"
                    :key="`expert-command-${textValue(command.command)}`"
                    type="button"
                    @click="executeAnalysisCommand(textValue(command.command, ''))"
                  >
                    <i class="pi pi-play"></i>
                    <code>{{ textValue(command.command) }}</code>
                  </button>
                </div>
                <p v-else>실행 가능한 읽기 명령 없음</p>
              </article>
            </section>

            <section
              v-if="arrayValue(selectedActionRecommendations.items).length"
              class="analysis-action-recommendation-panel"
              aria-label="추천 조치 후보"
            >
              <header>
                <div>
                  <span class="label">Action Recommendations</span>
                  <h3>{{ isBeginnerAnalysisUi ? '무엇을 조치 후보로 봐야 하나요?' : 'Action Candidates' }}</h3>
                  <p>{{ textValue(isBeginnerAnalysisUi ? selectedActionRecommendations.beginnerSummary || selectedActionRecommendations.summary : selectedActionRecommendations.summary || selectedActionRecommendations.beginnerSummary) }}</p>
                </div>
                <div class="analysis-remediation-meta">
                  <span class="analysis-chip">{{ numberValue(selectedActionRecommendations.totalCandidates, '0') }} candidates</span>
                  <span class="analysis-chip">{{ numberValue(selectedActionRecommendations.issueGroupCount, '0') }} groups</span>
                </div>
              </header>
              <div class="analysis-action-recommendation-list">
                <article
                  v-for="item in arrayValue(selectedActionRecommendations.items).slice(0, isExpertAnalysisUi ? 10 : 5)"
                  :key="`action-${textValue(item.source)}-${textValue(item.title)}-${textValue(item.signal)}`"
                >
                  <div class="analysis-action-heading">
                    <span class="status-pill" :class="severityClass(item.severity)">
                      {{ textValue(item.priority, 'P2') }}
                    </span>
                    <div>
                      <strong>{{ textValue(item.title, '조치 후보') }}</strong>
                      <small>{{ textValue(item.actionType) }} · {{ textValue(item.safetyLevel) }} · {{ textValue(item.category) }}</small>
                    </div>
                    <span class="analysis-chip" :class="confidenceClass(objectValue(item.qualityGate).level)">
                      {{ numberValue(objectValue(item.qualityGate).score, '0') }}
                    </span>
                  </div>
                  <p>{{ textValue(item.rationale) }}</p>
                  <small>{{ textValue(item.recommendedChange) }}</small>
                  <div v-if="isBeginnerAnalysisUi && stringArray(item.manifestHints).length" class="analysis-action-hints">
                    <span v-for="hint in stringArray(item.manifestHints).slice(0, 3)" :key="`hint-${textValue(item.title)}-${hint}`">
                      {{ hint }}
                    </span>
                  </div>
                  <div class="analysis-action-command-columns">
                    <div v-if="stringArray(item.preflightCommands).length">
                      <span class="label">먼저 확인</span>
                      <button
                        v-for="command in stringArray(item.preflightCommands).slice(0, isExpertAnalysisUi ? 3 : 2)"
                        :key="`preflight-${textValue(item.title)}-${command}`"
                        class="command-copy-row"
                        type="button"
                        @click="copyCommand(command)"
                      >
                        <strong>
                          <i class="pi pi-copy"></i>
                          복사
                        </strong>
                        <code>{{ command }}</code>
                      </button>
                    </div>
                    <div v-if="stringArray(item.validationCommands).length">
                      <span class="label">조치 후 검증</span>
                      <button
                        v-for="command in stringArray(item.validationCommands).slice(0, isExpertAnalysisUi ? 3 : 2)"
                        :key="`validation-${textValue(item.title)}-${command}`"
                        class="command-copy-row"
                        type="button"
                        @click="copyCommand(command)"
                      >
                        <strong>
                          <i class="pi pi-copy"></i>
                          복사
                        </strong>
                        <code>{{ command }}</code>
                      </button>
                    </div>
                  </div>
                </article>
              </div>
            </section>

            <section v-if="isBeginnerAnalysisUi && Object.keys(selectedConfidenceValidation).length" class="analysis-trust-panel" aria-label="분석 신뢰도">
              <header>
                <div>
                  <span class="label">Trust Check</span>
                  <h3>이 분석을 얼마나 믿을 수 있나요?</h3>
                  <p>{{ textValue(selectedConfidenceValidation.beginnerSummary || selectedConfidenceValidation.summary) }}</p>
                </div>
                <div class="analysis-trust-score">
                  <strong>{{ numberValue(selectedConfidenceValidation.score, '0') }}</strong>
                  <span class="analysis-chip" :class="confidenceClass(selectedConfidenceValidation.level)">
                    {{ textValue(selectedConfidenceValidation.level, 'MEDIUM') }}
                  </span>
                </div>
              </header>
              <div class="analysis-trust-metrics">
                <span>문제 리소스 {{ numberValue(selectedConfidenceValidation.problemResourceCount, '0') }}</span>
                <span>Warning 이벤트 {{ numberValue(selectedConfidenceValidation.warningEventCount, '0') }}</span>
                <span>로그 신호 {{ numberValue(selectedConfidenceValidation.highSignalLogCount, '0') }}</span>
                <span>원인 그룹 {{ numberValue(selectedConfidenceValidation.issueGroupCount, '0') }}</span>
              </div>
              <div class="analysis-trust-check-list">
                <article
                  v-for="check in arrayValue(selectedConfidenceValidation.checks)"
                  :key="`${textValue(check.name)}-${textValue(check.status)}`"
                >
                  <span class="analysis-chip" :class="validationStatusClass(check.status)">
                    {{ textValue(check.status, 'INFO') }}
                  </span>
                  <strong>{{ textValue(check.name) }}</strong>
                  <p>{{ textValue(check.beginnerExplanation || check.explanation) }}</p>
                </article>
              </div>
            </section>

            <section
              v-if="isBeginnerAnalysisUi && (Object.keys(selectedEventNoise).length || Object.keys(selectedCorrelationMap).length)"
              class="analysis-operator-insight-grid"
              aria-label="운영 인사이트"
            >
              <article v-if="Object.keys(selectedEventNoise).length" class="analysis-insight-panel">
                <header>
                  <div>
                    <span class="label">Event Noise</span>
                    <h3>반복 이벤트 압축</h3>
                    <p>{{ textValue(selectedEventNoise.beginnerSummary || selectedEventNoise.summary) }}</p>
                  </div>
                  <span class="status-pill" :class="severityClass(selectedEventNoise.noiseLevel)">
                    {{ textValue(selectedEventNoise.noiseLevel, 'LOW') }}
                  </span>
                </header>
                <div class="analysis-trust-metrics">
                  <span>Warning {{ numberValue(selectedEventNoise.warningEvents, '0') }}</span>
                  <span>패턴 {{ numberValue(selectedEventNoise.compressedWarningGroups, '0') }}</span>
                  <span>반복 {{ numberValue(selectedEventNoise.warningOccurrences, '0') }}</span>
                </div>
                <ul class="analysis-noise-list" v-if="arrayValue(selectedEventNoise.groups).length">
                  <li v-for="group in arrayValue(selectedEventNoise.groups).slice(0, 4)" :key="`${textValue(group.reason)}-${textValue(group.targetName)}`">
                    <strong>{{ textValue(group.priority, 'INFO') }} · {{ textValue(group.reason) }}</strong>
                    <span>{{ textValue(group.targetKind) }}/{{ textValue(group.targetName) }} · {{ numberValue(group.occurrenceCount, '0') }}회</span>
                    <small>{{ textValue(group.operatorMeaning || group.beginnerExplanation) }}</small>
                  </li>
                </ul>
              </article>

              <article v-if="Object.keys(selectedCorrelationMap).length" class="analysis-insight-panel">
                <header>
                  <div>
                    <span class="label">Correlation Map</span>
                    <h3>무엇이 서로 연결됐나요?</h3>
                    <p>{{ textValue(selectedCorrelationMap.beginnerSummary || selectedCorrelationMap.summary) }}</p>
                  </div>
                  <span class="analysis-chip">nodes {{ arrayValue(selectedCorrelationMap.nodes).length }}</span>
                </header>
                <ul class="analysis-correlation-list" v-if="arrayValue(selectedCorrelationMap.rootHints).length">
                  <li v-for="hint in arrayValue(selectedCorrelationMap.rootHints).slice(0, 5)" :key="`${textValue(hint.title)}-${textValue(hint.target)}`">
                    <strong>{{ textValue(hint.confidence, 'MEDIUM') }} · {{ textValue(hint.title) }}</strong>
                    <span>{{ textValue(hint.target) }} · {{ textValue(hint.reason) }}</span>
                    <small>{{ textValue(hint.nextAction) }}</small>
                  </li>
                </ul>
                <div class="analysis-correlation-edge-list" v-if="arrayValue(selectedCorrelationMap.edges).length">
                  <span
                    v-for="edge in arrayValue(selectedCorrelationMap.edges).slice(0, 8)"
                    :key="`${textValue(edge.from)}-${textValue(edge.to)}-${textValue(edge.relation)}`"
                  >
                    {{ textValue(edge.from) }} → {{ textValue(edge.to) }} · {{ textValue(edge.relation) }}
                  </span>
                </div>
              </article>
            </section>

            <section v-if="Object.keys(selectedAnalysisComparison).length" class="analysis-comparison-panel" aria-label="이전 분석 비교">
              <header>
                <div>
                  <span class="label">Trend Compare</span>
                  <h3>이전 대비 변화</h3>
                  <p>{{ textValue(selectedAnalysisComparison.summary) }}</p>
                </div>
                <span class="status-pill" :class="trendClass(selectedAnalysisComparison.trend)">
                  {{ trendLabel(selectedAnalysisComparison.trend) }}
                </span>
              </header>
              <div class="analysis-comparison-metrics">
                <div>
                  <span>Risk</span>
                  <strong>{{ numberValue(selectedAnalysisComparison.riskScoreBefore, '0') }} → {{ numberValue(selectedAnalysisComparison.riskScoreAfter, '0') }}</strong>
                  <small>{{ signedNumberValue(selectedAnalysisComparison.riskScoreDelta) }}</small>
                </div>
                <div>
                  <span>Severity</span>
                  <strong>{{ textValue(selectedAnalysisComparison.severityBefore, '-') }} → {{ textValue(selectedAnalysisComparison.severityAfter, '-') }}</strong>
                  <small>{{ selectedAnalysisComparison.severityChanged ? '변경됨' : '동일' }}</small>
                </div>
                <div>
                  <span>Issue Groups</span>
                  <strong>{{ numberValue(selectedAnalysisComparison.issueGroupCountBefore, '0') }} → {{ numberValue(selectedAnalysisComparison.issueGroupCountAfter, '0') }}</strong>
                  <small>
                    신규 {{ arrayValue(selectedAnalysisComparison.newIssueGroups).length }}
                    · 해결 {{ arrayValue(selectedAnalysisComparison.resolvedIssueGroups).length }}
                    · 지속 {{ arrayValue(selectedAnalysisComparison.persistentIssueGroups).length }}
                  </small>
                </div>
              </div>
              <div
                v-if="arrayValue(selectedAnalysisComparison.newIssueGroups).length || arrayValue(selectedAnalysisComparison.resolvedIssueGroups).length || arrayValue(selectedAnalysisComparison.persistentIssueGroups).length"
                class="analysis-comparison-columns"
              >
                <article>
                  <h4>신규</h4>
                  <ul v-if="arrayValue(selectedAnalysisComparison.newIssueGroups).length">
                    <li v-for="group in arrayValue(selectedAnalysisComparison.newIssueGroups).slice(0, 4)" :key="`new-${textValue(group.groupKey)}`">
                      <strong>{{ textValue(group.title) }}</strong>
                      <span>{{ textValue(group.resourceKind) }}/{{ textValue(group.resourceName) }} · {{ textValue(group.severity) }}</span>
                    </li>
                  </ul>
                  <p v-else>신규 issue 없음</p>
                </article>
                <article>
                  <h4>해결</h4>
                  <ul v-if="arrayValue(selectedAnalysisComparison.resolvedIssueGroups).length">
                    <li v-for="group in arrayValue(selectedAnalysisComparison.resolvedIssueGroups).slice(0, 4)" :key="`resolved-${textValue(group.groupKey)}`">
                      <strong>{{ textValue(group.title) }}</strong>
                      <span>{{ textValue(group.resourceKind) }}/{{ textValue(group.resourceName) }} · {{ textValue(group.severity) }}</span>
                    </li>
                  </ul>
                  <p v-else>해결된 issue 없음</p>
                </article>
                <article>
                  <h4>지속</h4>
                  <ul v-if="arrayValue(selectedAnalysisComparison.persistentIssueGroups).length">
                    <li v-for="group in arrayValue(selectedAnalysisComparison.persistentIssueGroups).slice(0, 4)" :key="`persistent-${textValue(group.groupKey)}`">
                      <strong>{{ textValue(group.title) }}</strong>
                      <span>
                        {{ textValue(group.resourceKind) }}/{{ textValue(group.resourceName) }}
                        · event {{ signedNumberValue(group.eventOccurrenceDelta) }}
                      </span>
                    </li>
                  </ul>
                  <p v-else>지속 issue 없음</p>
                </article>
              </div>
            </section>

            <section v-if="arrayValue(objectValue(selectedAnalysisResult.remediationPlan).stages).length" class="analysis-remediation-panel" aria-label="처리 계획">
              <header>
                <div>
                  <span class="label">Remediation Planner</span>
                  <h3>권장 처리 순서</h3>
                  <p>{{ textValue(objectValue(selectedAnalysisResult.remediationPlan).summary) }}</p>
                </div>
                <div class="analysis-remediation-meta">
                  <span class="status-pill" :class="severityClass(objectValue(selectedAnalysisResult.remediationPlan).estimatedRisk)">
                    {{ textValue(objectValue(selectedAnalysisResult.remediationPlan).estimatedRisk, 'LOW') }}
                  </span>
                  <span class="analysis-chip">{{ textValue(objectValue(selectedAnalysisResult.remediationPlan).strategy, 'VERIFY_BEFORE_CHANGE') }}</span>
                </div>
              </header>
              <div class="analysis-remediation-stages">
                <article
                  v-for="stage in arrayValue(objectValue(selectedAnalysisResult.remediationPlan).stages)"
                  :key="`${numberValue(stage.order, '0')}-${textValue(stage.title)}`"
                  class="analysis-remediation-stage"
                >
                  <div class="analysis-stage-heading">
                    <span>{{ numberValue(stage.order, '0') }}</span>
                    <div>
                      <strong>{{ textValue(stage.title, '검증 단계') }}</strong>
                      <small>{{ textValue(stage.objective) }}</small>
                    </div>
                    <span class="status-pill" :class="severityClass(stage.riskLevel)">
                      {{ textValue(stage.riskLevel, 'LOW') }}
                    </span>
                  </div>
                  <p>{{ textValue(stage.expectedResult) }}</p>
                  <div v-if="arrayValue(stage.commands).length" class="analysis-stage-command-list">
                    <button
                      v-for="command in arrayValue(stage.commands).slice(0, 4)"
                      :key="`${textValue(stage.title)}-${textValue(command.label)}-${textValue(command.command)}`"
                      class="command-copy-row"
                      type="button"
                      @click="copyCommand(textValue(command.command, ''))"
                    >
                      <strong>{{ textValue(command.label, '검증 명령') }}</strong>
                      <code>{{ textValue(command.command) }}</code>
                      <span>{{ textValue(command.why) }}</span>
                    </button>
                  </div>
                  <small>{{ textValue(stage.rollbackNote) }}</small>
                </article>
              </div>
              <small>{{ textValue(objectValue(selectedAnalysisResult.remediationPlan).safetyNote) }}</small>
            </section>

            <section v-if="workflowIssueGroups.length" class="analysis-workflow-panel" aria-label="문제 처리 흐름">
              <header>
                <div>
                  <span class="label">Action Workflow</span>
                  <h3>이 문제들은 어디까지 처리됐나요?</h3>
                  <p>{{ textValue(selectedActionWorkflow.summary, '원인 그룹별 확인 상태를 표시하고 다음 조치로 이어갑니다.') }}</p>
                </div>
                <span class="analysis-chip">{{ workflowIssueGroups.length }} issues</span>
              </header>
              <div class="analysis-workflow-list">
                <article
                  v-for="group in workflowIssueGroups"
                  :key="`workflow-${textValue(group.issueGroupId)}`"
                  class="analysis-workflow-card"
                >
                  <div class="analysis-workflow-card-heading">
                    <div>
                      <strong>{{ textValue(group.title, 'Issue Group') }}</strong>
                      <span>{{ textValue(group.issueGroupId) }} · {{ textValue(group.category) }}</span>
                    </div>
                    <span class="status-pill" :class="workflowStatusClass(workflowStatusForGroup(group))">
                      {{ workflowStatusLabel(workflowStatusForGroup(group)) }}
                    </span>
                  </div>
                  <p>{{ textValue(workflowItemForGroup(group).nextAction || group.recommendedNextAction) }}</p>
                  <div class="analysis-workflow-status-buttons" role="group" aria-label="처리 상태 변경">
                    <button
                      v-for="status in workflowStatuses"
                      :key="`${textValue(group.issueGroupId)}-${textValue(status.value)}`"
                      class="analysis-workflow-status-button"
                      :class="{ active: workflowStatusForGroup(group) === textValue(status.value).toUpperCase() }"
                      type="button"
                      :title="textValue(status.description)"
                      @click="setWorkflowStatus(group, status.value)"
                    >
                      {{ textValue(status.label) }}
                    </button>
                  </div>
                </article>
              </div>
            </section>

            <section v-if="isBeginnerAnalysisUi && runbookChecklistItems.length" class="analysis-runbook-checklist-panel" aria-label="런북 체크리스트">
              <header>
                <div>
                  <span class="label">Runbook Checklist</span>
                  <h3>실행 전 확인 목록</h3>
                  <p>읽기 전용 검증부터 체크하세요. 변경 명령은 원인 확인 후 별도 승인 흐름에서 처리합니다.</p>
                </div>
                <span class="analysis-chip">{{ checkedRunbookChecklistCount }} / {{ runbookChecklistItems.length }}</span>
              </header>
              <div class="analysis-runbook-checklist">
                <label
                  v-for="(item, index) in runbookChecklistItems"
                  :key="runbookChecklistKey(item, index)"
                  class="analysis-runbook-check-item"
                  :class="{ checked: runbookChecklist[runbookChecklistKey(item, index)] }"
                >
                  <input
                    type="checkbox"
                    :checked="Boolean(runbookChecklist[runbookChecklistKey(item, index)])"
                    @change="toggleRunbookChecklist(item, index)"
                  />
                  <div>
                    <strong>{{ textValue(item.label, '검증 명령') }}</strong>
                    <code>{{ textValue(item.command) }}</code>
                    <span>{{ textValue(item.why, '이 명령으로 분석 근거를 검증합니다.') }}</span>
                  </div>
                  <button class="icon-button" type="button" title="명령 복사" @click.prevent="copyCommand(textValue(item.command, ''))">
                    <i class="pi pi-copy"></i>
                  </button>
                </label>
              </div>
            </section>

            <AnalysisCommandSafetyPanel
              v-if="arrayValue(selectedCommandSafety.commands).length"
              :safety="selectedCommandSafety"
              :commands="visibleCommandSafetyCommands"
              :total-count="selectedCommandSafetyCommands.length"
              :hidden-count="hiddenCommandSafetyCount"
              :expanded="commandSafetyExpanded"
              :beginner="isBeginnerAnalysisUi"
              @copy="copyCommand"
              @open-console="openCommandConsole"
              @toggle="commandSafetyExpanded = !commandSafetyExpanded"
            />

            <section
              v-if="executableCommandSafetyCommands.length || commandExecutions.length"
              class="analysis-command-runner-panel"
              aria-label="안전 검증 명령 실행"
            >
              <header>
                <div>
                  <span class="label">Safe Verification</span>
                  <h3>{{ isBeginnerAnalysisUi ? '플랫폼에서 바로 확인하기' : '검증 명령 실행' }}</h3>
                  <p v-if="isBeginnerAnalysisUi">읽기/진단 명령만 Kubernetes API로 실행합니다. 변경 명령은 저장 전 차단됩니다.</p>
                </div>
                <span v-if="loadingAnalysisOperations" class="analysis-chip">loading</span>
              </header>
              <div v-if="executableCommandSafetyCommands.length" class="analysis-command-runner-grid">
                <article
                  v-for="command in executableCommandSafetyCommands"
                  :key="`runner-${textValue(command.source)}-${textValue(command.command)}`"
                  class="analysis-command-runner-card"
                >
                  <div>
                    <span class="analysis-chip" :class="safetyClass(command.safetyLevel)">
                      {{ safetyLabel(command.safetyLevel) }}
                    </span>
                    <strong>{{ textValue(command.label, '검증 명령') }}</strong>
                    <small>{{ textValue(command.why || command.beginnerExplanation, '분석 근거를 실제 클러스터 상태로 확인합니다.') }}</small>
                  </div>
                  <code>{{ textValue(command.command, '명령 정보 없음') }}</code>
                  <div class="analysis-command-runner-actions">
                    <button class="secondary-button compact-button" type="button" @click="copyCommand(textValue(command.command, ''))">
                      <i class="pi pi-copy"></i>
                      복사
                    </button>
                    <button
                      class="primary-button compact-button"
                      type="button"
                      :disabled="Boolean(commandExecuting)"
                      @click="executeAnalysisCommand(textValue(command.command, ''))"
                    >
                      <i :class="commandExecuting === textValue(command.command, '') ? 'pi pi-spin pi-spinner' : 'pi pi-play'"></i>
                      실행
                    </button>
                  </div>
                </article>
              </div>
              <div v-if="commandExecutions.length" class="analysis-command-results">
                <h4>최근 실행 결과</h4>
                <button
                  v-for="execution in commandExecutions.slice(0, 6)"
                  :key="execution.id"
                  class="analysis-command-result-row"
                  type="button"
                  @click="openCommandExecutionDetail(execution)"
                >
                  <span class="status-pill" :class="{
                    low: execution.status === 'SUCCEEDED',
                    warning: execution.status === 'BLOCKED',
                    critical: execution.status === 'FAILED'
                  }">{{ execution.status }}</span>
                  <code>{{ execution.command }}</code>
                  <small>{{ numberValue(execution.durationMs, '0') }}ms · {{ collectedAtLabel(execution.createdAt) }}</small>
                </button>
              </div>
            </section>

            <section
              v-if="selectedAnalysis"
              class="analysis-change-runner-panel"
              aria-label="안전 변경 조치 테스트"
            >
              <header>
                <div>
                  <span class="label">Safe Change Pilot</span>
                  <h3>{{ isBeginnerAnalysisUi ? '제한된 변경 조치 테스트' : 'Change Guard' }}</h3>
                  <p v-if="isBeginnerAnalysisUi">Deployment restart, replicas 1~20 scale, 명시 revision rollback만 지원합니다. 실행 전 RBAC, dry-run, rollback guard를 확인합니다.</p>
                </div>
                <span class="analysis-chip success">RBAC + dry-run</span>
              </header>
              <div v-if="changeCommandSafetyCommands.length" class="analysis-command-runner-grid">
                <article
                  v-for="command in changeCommandSafetyCommands"
                  :key="`change-${textValue(command.source)}-${textValue(command.command)}`"
                  class="analysis-command-runner-card"
                >
                  <div>
                    <span class="analysis-chip warning">변경</span>
                    <strong>{{ textValue(command.label, '변경 조치') }}</strong>
                    <small>{{ textValue(command.why || command.beginnerExplanation, '클러스터 상태가 바뀌므로 대상과 영향 범위를 확인해야 합니다.') }}</small>
                  </div>
                  <code>{{ textValue(command.command, '명령 정보 없음') }}</code>
                  <div class="analysis-command-runner-actions">
                    <button class="secondary-button compact-button" type="button" @click="copyCommand(textValue(command.command, ''))">
                      <i class="pi pi-copy"></i>
                      복사
                    </button>
                    <button
                      class="danger-button compact-button"
                      type="button"
                      :disabled="Boolean(commandExecuting)"
                      @click="previewChangeCommand(textValue(command.command, ''))"
                    >
                      <i class="pi pi-shield"></i>
                      검토
                    </button>
                  </div>
                </article>
              </div>
              <div class="analysis-manual-change">
                <label class="form-field">
                  <span>직접 변경 명령 테스트</span>
                  <textarea
                    v-model="manualChangeCommand"
                    rows="2"
                    placeholder="kubectl rollout restart deployment/my-app -n default"
                  ></textarea>
                </label>
                <button
                  class="danger-button"
                  type="button"
                  :disabled="!manualChangeCommand.trim() || Boolean(commandExecuting)"
                  @click="previewChangeCommand(manualChangeCommand)"
                >
                  <i class="pi pi-shield"></i>
                  변경 검토
                </button>
              </div>
            </section>

            <section
              v-if="isBeginnerAnalysisUi && (arrayValue(selectedConclusionValidation.conclusions).length || arrayValue(selectedReanalysisPlan.options).length)"
              class="analysis-validation-panel"
              aria-label="분석 신뢰도와 재분석"
            >
              <header>
                <div>
                  <span class="label">Trust & Reanalysis</span>
                  <h3>AI 판단을 어떻게 검증하나요?</h3>
                  <p>{{ textValue(selectedConclusionValidation.summary, 'AI 결론별 근거 충분성을 점검하고 필요한 경우 재분석합니다.') }}</p>
                </div>
                <div class="analysis-remediation-meta">
                  <span class="analysis-chip">validated {{ numberValue(selectedConclusionValidation.validatedCount, '0') }}</span>
                  <span class="analysis-chip warning">needs evidence {{ numberValue(selectedConclusionValidation.needsEvidenceCount, '0') }}</span>
                </div>
              </header>
              <div class="analysis-validation-grid">
                <article>
                  <h4>검증이 필요한 결론</h4>
                  <div class="analysis-validation-list">
                    <div
                      v-for="item in arrayValue(selectedConclusionValidation.conclusions).slice(0, 6)"
                      :key="`validation-${textValue(item.issueGroupId)}`"
                    >
                      <strong>{{ textValue(item.title, '분석 결론') }}</strong>
                      <span>{{ textValue(item.status) }} · confidence {{ numberValue(item.confidenceScore, '0') }}</span>
                      <small>{{ textValue(item.operatorMeaning || item.beginnerExplanation) }}</small>
                    </div>
                  </div>
                </article>
                <article>
                  <h4>재분석 권장 조건</h4>
                  <div class="analysis-reanalysis-options">
                    <div
                      v-for="option in arrayValue(selectedReanalysisPlan.options).slice(0, 4)"
                      :key="`reanalysis-${textValue(option.value)}`"
                    >
                      <strong>{{ textValue(option.label, '재분석 옵션') }}</strong>
                      <small>{{ textValue(option.description) }}</small>
                    </div>
                  </div>
                  <button class="secondary-button" type="button" @click="retrySelectedAnalysis">
                    <i class="pi pi-refresh"></i>
                    현재 범위 재분석
                  </button>
                </article>
              </div>
            </section>

            <AnalysisLogIntelligencePanel
              :intelligence="objectValue(selectedAnalysisResult.logIntelligence)"
              :beginner="isBeginnerAnalysisUi"
              :severity-class="severityClass"
              @copy="copyCommand"
            />

            <AnalysisEvidencePanel
              :ledger="selectedEvidenceLedger"
              :beginner="isBeginnerAnalysisUi"
              @copy="copyCommand"
            />

            <AnalysisIssueGroupPanel
              :groups="workflowIssueGroups"
              :deep-dive-for-group="deepDiveForGroup"
              :conclusion-for-group="conclusionForGroup"
              :fix-readiness-label="fixReadinessLabel"
              :readiness-class="readinessClass"
              :severity-class="severityClass"
              :confidence-class="confidenceClass"
              @text-detail="openIssueGroupTextDetail"
              @deep-dive="openIssueGroupDeepDiveDetail"
              @resource="openAnalysisResourceDrillDown"
              @logs="openAnalysisLogDrillDown"
            />

            <section v-if="arrayValue(selectedAnalysisResult.problemCards).length" class="analysis-problem-card-panel" aria-label="문제 카드">
              <header>
                <div>
                  <span class="label">Evidence Trace</span>
                  <h3>우선 확인할 문제</h3>
                </div>
                <span>{{ arrayValue(selectedAnalysisResult.problemCards).length }} cards</span>
              </header>
              <div class="analysis-problem-card-grid">
                <article
                  v-for="card in arrayValue(selectedAnalysisResult.problemCards)"
                  :key="`${textValue(card.resourceKind)}-${textValue(card.resourceName)}-${textValue(card.title)}`"
                  class="analysis-problem-card"
                >
                  <div class="analysis-problem-card-header">
                    <div>
                      <strong>{{ textValue(card.title, '진단 카드') }}</strong>
                      <span>{{ textValue(card.resourceKind) }}/{{ textValue(card.resourceName) }} · {{ textValue(card.status) }}</span>
                    </div>
                    <span class="status-pill" :class="severityClass(card.severity)">
                      {{ textValue(card.severity, 'INFO') }}
                    </span>
                  </div>
                  <div class="analysis-problem-card-metrics">
                    <span class="analysis-chip" :class="confidenceClass(card.confidenceLevel)">
                      신뢰도 {{ textValue(card.confidenceLevel, 'LOW') }} · {{ numberValue(card.confidenceScore, '0') }}
                    </span>
                    <span class="analysis-chip" :class="readinessClass(card.fixReadiness)">
                      {{ fixReadinessLabel(card.fixReadiness) }}
                    </span>
                  </div>
                  <p>{{ textValue(card.rootCause, 'Kubernetes evidence 기반 문제 후보입니다.') }}</p>
                  <small>{{ textValue(card.confidenceReason) }}</small>
                  <small>{{ textValue(card.fixReadinessReason) }}</small>

                  <div v-if="arrayValue(card.evidenceTrace).length" class="analysis-evidence-trace">
                    <span class="label">근거 흐름</span>
                    <ol>
                      <li v-for="trace in arrayValue(card.evidenceTrace)" :key="`${textValue(trace.type)}-${textValue(trace.source)}-${textValue(trace.message)}`">
                        <strong>{{ textValue(trace.type) }} · {{ textValue(trace.source) }}</strong>
                        <span>{{ textValue(trace.message) }}</span>
                      </li>
                    </ol>
                  </div>

                  <div v-if="arrayValue(card.relatedReferences).length" class="analysis-reference-list">
                    <span class="label">관련 참조</span>
                    <span v-for="reference in arrayValue(card.relatedReferences)" :key="`${textValue(reference.kind)}-${textValue(reference.name)}`">
                      {{ textValue(reference.kind) }}/{{ textValue(reference.name) }} · {{ textValue(reference.reason) }}
                    </span>
                  </div>

                  <div class="analysis-card-command-columns">
                    <div v-if="arrayValue(card.beforeCommands).length">
                      <span class="label">조치 전 확인</span>
                      <button
                        v-for="command in arrayValue(card.beforeCommands).slice(0, 2)"
                        :key="`before-${textValue(command.command)}`"
                        class="command-copy-row"
                        type="button"
                        @click="copyCommand(textValue(command.command, ''))"
                      >
                        <strong>{{ textValue(command.label, '검증') }}</strong>
                        <span>{{ textValue(command.why) }}</span>
                      </button>
                    </div>
                    <div v-if="arrayValue(card.afterCommands).length">
                      <span class="label">조치 후 확인</span>
                      <button
                        v-for="command in arrayValue(card.afterCommands).slice(0, 2)"
                        :key="`after-${textValue(command.command)}`"
                        class="command-copy-row"
                        type="button"
                        @click="copyCommand(textValue(command.command, ''))"
                      >
                        <strong>{{ textValue(command.label, '검증') }}</strong>
                        <span>{{ textValue(command.why) }}</span>
                      </button>
                    </div>
                  </div>

                  <div class="analysis-problem-card-actions">
                    <button class="secondary-button" type="button" @click="openAnalysisResourceDrillDown(card)">
                      <i class="pi pi-search"></i>
                      상세
                    </button>
                    <button
                      v-if="textValue(card.resourceKind) === 'Pod'"
                      class="secondary-button"
                      type="button"
                      @click="openAnalysisLogDrillDown(card)"
                    >
                      <i class="pi pi-list"></i>
                      로그
                    </button>
                  </div>
                </article>
              </div>
            </section>

            <section class="analysis-detail-grid">
              <article class="analysis-detail-section">
                <h3>Root Cause</h3>
                <ul v-if="arrayValue(selectedAnalysisResult.rootCauses).length">
                  <li v-for="cause in arrayValue(selectedAnalysisResult.rootCauses)" :key="textValue(cause.cause)">
                    <div class="diagnostic-row-heading">
                      <strong>{{ textValue(cause.cause) }}</strong>
                      <button
                        class="icon-button table-icon-button"
                        title="관련 리소스 상세"
                        aria-label="관련 리소스 상세"
                        type="button"
                        @click="openAnalysisResourceDrillDown(cause)"
                      >
                        <i class="pi pi-search"></i>
                      </button>
                    </div>
                    <span>{{ stringArray(cause.evidence).join(' · ') || '근거 없음' }}</span>
                  </li>
                </ul>
                <p v-else>명확한 root cause가 식별되지 않았습니다.</p>
              </article>

              <article class="analysis-detail-section">
                <h3>Log Analysis</h3>
                <ul v-if="arrayValue(selectedAnalysisResult.logAnalysis).length">
                  <li v-for="log in arrayValue(selectedAnalysisResult.logAnalysis)" :key="`${textValue(log.podName)}-${textValue(log.containerName)}-${textValue(log.pattern)}`">
                    <div class="diagnostic-row-heading">
                      <strong>{{ textValue(log.signal || log.severity || log.level, 'LOG') }} · {{ textValue(log.podName || log.resourceName) }}</strong>
                      <button
                        class="icon-button table-icon-button"
                        title="로그 조회"
                        aria-label="로그 조회"
                        type="button"
                        @click="openAnalysisLogDrillDown(log)"
                      >
                        <i class="pi pi-list"></i>
                      </button>
                    </div>
                    <span>{{ textValue(log.pattern || log.message || log.summary, '로그 신호가 감지되었습니다.') }}</span>
                    <small>{{ textValue(log.interpretation || log.analysis || log.recommendation, '관련 Pod 로그를 직접 확인하세요.') }}</small>
                  </li>
                </ul>
                <p v-else>반복되는 로그 에러 패턴이 감지되지 않았습니다.</p>
              </article>

              <article class="analysis-detail-section">
                <h3>Performance</h3>
                <p>{{ textValue(objectValue(selectedAnalysisResult.performance).summary, 'Kubernetes API 기준 성능 병목 신호가 감지되지 않았습니다. Prometheus 연동 후 정량 지표로 추가 검증하세요.') }}</p>
                <ul v-if="arrayValue(objectValue(selectedAnalysisResult.performance).bottlenecks).length">
                  <li v-for="item in arrayValue(objectValue(selectedAnalysisResult.performance).bottlenecks)" :key="`${textValue(item.resourceKind)}-${textValue(item.resourceName)}-${textValue(item.signal)}`">
                    <strong>{{ textValue(item.resourceKind) }}/{{ textValue(item.resourceName) }}</strong>
                    <span>{{ textValue(item.signal) }}</span>
                    <small>{{ textValue(item.recommendation) }}</small>
                  </li>
                </ul>
              </article>

              <article class="analysis-detail-section">
                <h3>Scaling</h3>
                <p>{{ textValue(objectValue(selectedAnalysisResult.scaling).summary, 'Kubernetes API 기준 즉시 스케일링 후보가 감지되지 않았습니다. workload와 metrics 연동 상태를 확인하세요.') }}</p>
                <ul v-if="arrayValue(objectValue(selectedAnalysisResult.scaling).scaleUpCandidates).length">
                  <li v-for="item in arrayValue(objectValue(selectedAnalysisResult.scaling).scaleUpCandidates)" :key="`${textValue(item.resourceKind)}-${textValue(item.resourceName)}-${textValue(item.currentSignal)}`">
                    <strong>{{ textValue(item.resourceKind) }}/{{ textValue(item.resourceName) }}</strong>
                    <span>{{ textValue(item.currentSignal) }}</span>
                    <small>{{ textValue(item.recommendation) }}</small>
                  </li>
                </ul>
                <ul v-if="stringArray(objectValue(selectedAnalysisResult.scaling).hpaRecommendations).length">
                  <li v-for="item in stringArray(objectValue(selectedAnalysisResult.scaling).hpaRecommendations)" :key="item">
                    <span>{{ item }}</span>
                  </li>
                </ul>
              </article>

              <article class="analysis-detail-section">
                <h3>Risk Forecast</h3>
                <p>{{ textValue(objectValue(selectedAnalysisResult.riskForecast).summary) }}</p>
                <ul v-if="arrayValue(objectValue(selectedAnalysisResult.riskForecast).predictions).length">
                  <li v-for="item in arrayValue(objectValue(selectedAnalysisResult.riskForecast).predictions)" :key="`${textValue(item.category)}-${textValue(item.resourceKind)}-${textValue(item.resourceName)}-${textValue(item.signal)}`">
                    <div class="diagnostic-row-heading">
                      <strong>{{ textValue(item.severity, 'INFO') }} · {{ textValue(item.category) }} · {{ numberValue(item.probability, '0') }}%</strong>
                      <button
                        class="icon-button table-icon-button"
                        title="위험 리소스 상세"
                        aria-label="위험 리소스 상세"
                        type="button"
                        @click="openAnalysisResourceDrillDown(item)"
                      >
                        <i class="pi pi-search"></i>
                      </button>
                    </div>
                    <span>{{ textValue(item.resourceKind) }}/{{ textValue(item.resourceName) }} · {{ textValue(item.signal) }}</span>
                    <small>{{ textValue(item.recommendation) }}</small>
                  </li>
                </ul>
              </article>

              <article class="analysis-detail-section">
                <h3>Change Timeline</h3>
                <ul v-if="arrayValue(selectedAnalysisResult.changeTimeline).length">
                  <li v-for="item in arrayValue(selectedAnalysisResult.changeTimeline)" :key="`${textValue(item.occurredAt)}-${textValue(item.resourceKind)}-${textValue(item.resourceName)}-${textValue(item.title)}`">
                    <strong>{{ textValue(item.severity, 'INFO') }} · {{ textValue(item.category) }}</strong>
                    <span>{{ textValue(item.resourceKind) }}/{{ textValue(item.resourceName) }} · {{ textValue(item.title) }}</span>
                    <small>{{ textValue(item.suspectedChange) }}</small>
                  </li>
                </ul>
                <p v-else>AI가 별도 change timeline을 생성하지 않았습니다.</p>
              </article>

              <article class="analysis-detail-section">
                <h3>Runbook</h3>
                <ul v-if="safeRunbookActions(selectedAnalysisResult.runbookActions).length">
                  <li v-for="item in safeRunbookActions(selectedAnalysisResult.runbookActions)" :key="`${textValue(item.priority)}-${textValue(item.targetKind)}-${textValue(item.targetName)}-${textValue(item.command)}`">
                    <div class="diagnostic-row-heading">
                      <strong>{{ textValue(item.priority, 'P3') }} · {{ runbookCategoryLabel(item) }} · {{ textValue(item.title) }}</strong>
                      <span class="diagnostic-row-actions">
                        <button
                          v-if="item.command"
                          class="icon-button table-icon-button"
                          title="명령 복사"
                          aria-label="명령 복사"
                          type="button"
                          @click="copyCommand(textValue(item.command, ''))"
                        >
                          <i class="pi pi-copy"></i>
                        </button>
                        <button
                          class="icon-button table-icon-button"
                          title="관련 리소스 상세"
                          aria-label="관련 리소스 상세"
                          type="button"
                          @click="openRunbookDrillDown(item)"
                        >
                          <i class="pi pi-search"></i>
                        </button>
                      </span>
                    </div>
                    <span>{{ textValue(item.targetKind) }}/{{ textValue(item.targetName) }} · {{ textValue(item.reason) }}</span>
                    <small>{{ textValue(item.why, '이 명령으로 분석 근거를 검증합니다.') }}</small>
                    <code v-if="item.command">{{ textValue(item.command) }}</code>
                  </li>
                </ul>
                <p v-else>AI가 별도 안전 runbook을 생성하지 않았습니다.</p>
                <details v-if="destructiveRunbookActions(selectedAnalysisResult.runbookActions).length" class="danger-runbook-details">
                  <summary>위험 조치 {{ destructiveRunbookActions(selectedAnalysisResult.runbookActions).length }}건</summary>
                  <ul>
                    <li v-for="item in destructiveRunbookActions(selectedAnalysisResult.runbookActions)" :key="`danger-${textValue(item.command)}-${textValue(item.targetName)}`">
                      <strong>{{ textValue(item.priority, 'P3') }} · {{ textValue(item.title) }}</strong>
                      <span>{{ textValue(item.why || item.reason, '클러스터 상태를 변경할 수 있어 기본 숨김 처리되었습니다.') }}</span>
                      <code>{{ textValue(item.command) }}</code>
                    </li>
                  </ul>
                </details>
              </article>

              <article class="analysis-detail-section">
                <h3>Recommendations</h3>
                <ul v-if="arrayValue(selectedAnalysisResult.recommendations).length">
                  <li v-for="item in arrayValue(selectedAnalysisResult.recommendations)" :key="`${textValue(item.priority)}-${textValue(item.action)}`">
                    <strong>{{ textValue(item.priority) }} · {{ textValue(item.action) }}</strong>
                    <span>{{ textValue(item.reason) }}</span>
                    <small>{{ stringArray(item.commands).join(' · ') }}</small>
                  </li>
                </ul>
                <p v-else>즉시 권장 조치가 없습니다.</p>
              </article>

              <article class="analysis-detail-section">
                <h3>Operations Guide</h3>
                <p>{{ textValue(objectValue(selectedAnalysisResult.operationsGuide).summary) }}</p>
                <ul>
                  <li v-for="item in stringArray(objectValue(selectedAnalysisResult.operationsGuide).shortTerm)" :key="`short-${item}`">
                    <strong>Short</strong>
                    <span>{{ item }}</span>
                  </li>
                  <li v-for="item in stringArray(objectValue(selectedAnalysisResult.operationsGuide).mediumTerm)" :key="`medium-${item}`">
                    <strong>Medium</strong>
                    <span>{{ item }}</span>
                  </li>
                </ul>
              </article>

              <AnalysisRuntimePanel
                :diagnostics="selectedAnalysisDiagnostics"
                :incremental="selectedIncrementalAnalysis"
              />

              <article class="analysis-detail-section analysis-detail-wide">
                <h3>Next Actions</h3>
                <ul v-if="selectedNextActions.length">
                  <li v-for="item in selectedNextActions" :key="`${textValue(item.priority)}-${textValue(item.action)}-${textValue(item.ownerHint)}`">
                    <strong>{{ textValue(item.priority) }} · {{ textValue(item.ownerHint) }}</strong>
                    <span>{{ textValue(item.action) }}</span>
                    <small v-if="cleanActionText(item.verification)">{{ textValue(item.verification) }}</small>
                  </li>
                </ul>
                <p v-else>현재 결과에서 즉시 실행할 다음 행동을 식별하지 못했습니다. Runbook Checklist와 Issue Group을 먼저 확인하세요.</p>
              </article>
            </section>
          </template>

          <AnalysisFeedbackPanel :analysis-id="selectedAnalysis.id" />

          <details class="analysis-raw-json">
            <summary>Raw JSON</summary>
            <pre>{{ formattedResultJson(selectedAnalysis) }}</pre>
          </details>
        </div>
        <footer class="modal-actions">
          <button class="primary-button" :disabled="running" type="button" @click="retrySelectedAnalysis">
            <i :class="running ? 'pi pi-spin pi-spinner' : 'pi pi-refresh'"></i>
            <span>분석 재시도</span>
          </button>
          <button class="secondary-button" type="button" @click="closeAnalysisDetail">
            <span>닫기</span>
          </button>
        </footer>
      </section>
    </div>

    <div v-if="feedbackDetail" class="modal-backdrop" @click.self="closeFeedbackDetail">
      <section class="modal-panel feedback-detail-modal" aria-modal="true" role="dialog">
        <header class="modal-header">
          <div>
            <h2>{{ feedbackDetail.title }}</h2>
            <p>요청 처리 중 반환된 상세 오류입니다.</p>
          </div>
          <button class="icon-button" title="닫기" type="button" @click="closeFeedbackDetail">
            <i class="pi pi-times"></i>
          </button>
        </header>
        <div class="feedback-detail-body">
          <pre class="feedback-error-detail">{{ feedbackDetail.detail }}</pre>
        </div>
        <footer class="modal-actions">
          <button class="secondary-button" type="button" @click="closeFeedbackDetail">
            <span>닫기</span>
          </button>
        </footer>
      </section>
    </div>

    <AnalysisTextDetailModal
      v-if="analysisTextDetail"
      :title="analysisTextDetail.title"
      :subtitle="analysisTextDetail.subtitle"
      :sections="analysisTextDetail.sections"
      @close="closeAnalysisTextDetail"
    />

    <AnalysisCommandModals
      :pending="pendingChangeCommand"
      :execution="commandExecutionDetail"
      :confirmation-input="changeConfirmInput"
      :executing="Boolean(commandExecuting)"
      :selected-namespace="selectedAnalysis?.namespace"
      :guard-state-class="guardStateClass"
      :guard-state-label="guardStateLabel"
      :is-rollback-command="isRollbackCommand"
      :number-value="numberValue"
      @close-pending="closeChangeCommandModal"
      @close-execution="closeCommandExecutionDetail"
      @execute="executePendingChangeCommand"
      @copy="copyCommand"
      @retry="retrySelectedAnalysis"
      @update:confirmation-input="changeConfirmInput = $event"
    />

    <AnalysisResourceDetailModal
      v-if="selectedDiagnosticResource"
      :resource="selectedDiagnosticResource"
      :events="selectedResourceEvents"
      :evidence="selectedResourceEvidence"
      :pod-logs="selectedResourcePodLogs"
      :references="selectedResourceRelatedReferences"
      :log-availability="logAvailabilityMessage(selectedDiagnosticResource)"
      :formatted-summary="formattedResourceSummary(selectedDiagnosticResource)"
      :collected-at-label="collectedAtLabel"
      @close="closeResourceDetail"
      @logs="openResourceLogs"
      @pod-logs="openPodLogs"
    />

    <AnalysisLogDetailModal
      v-if="selectedLogTarget"
      :target-label="logTargetLabel()"
      :collected-at="selectedLogs ? collectedAtLabel(selectedLogs.collectedAt) : '-'"
      :logs="selectedLogs"
      :loading="loadingLogs"
      :error-message="logErrorMessage"
      :tail-lines="selectedLogTailLines"
      :container-name="selectedLogContainerName"
      :container-options="logContainerOptions"
      @close="closeLogs"
      @reload="loadSelectedLogs"
      @update:tail-lines="selectedLogTailLines = $event"
      @update:container-name="selectedLogContainerName = $event"
    />
  </section>
</template>
