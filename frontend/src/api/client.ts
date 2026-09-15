import { API_BASE_URL, ApiError, parseError, readCookie, requestJson as request } from './http';
import { getLocale } from '@/i18n/locale';

export { ApiError } from './http';

export interface ClusterResponse {
  id: string;
  tenantId: string;
  workspaceId: string;
  name: string;
  description?: string;
  environment?: string;
  provider?: string;
  region?: string;
  status?: string;
  createdAt?: string;
}

export interface ClusterReadinessResponse {
  clusterId: string;
  clusterName: string;
  overallStatus: 'READY' | 'REVIEW' | 'BLOCKED' | 'CRITICAL';
  overallScore: number;
  capabilities: {
    status: 'ALLOWED' | 'PARTIAL' | 'DENIED' | 'UNKNOWN';
    allowedCount: number;
    deniedCount: number;
    unknownCount: number;
    score: number;
    checks: Array<{
      id: string;
      category: string;
      displayName: string;
      verb: string;
      apiGroup: string;
      resource: string;
      namespace?: string;
      allowed: boolean;
      state: 'ALLOWED' | 'PARTIAL' | 'DENIED' | 'UNKNOWN';
      reason: string;
      evidenceSource: string;
    }>;
  };
  credential: {
    status: 'HEALTHY' | 'WARNING' | 'CRITICAL';
    credentialType: 'KUBECONFIG' | 'SERVICE_ACCOUNT_TOKEN';
    connectionReachable: boolean;
    kubernetesVersion?: string;
    storedAt: string;
    ageDays: number;
    tokenExpiresAt?: string;
    tokenExpiresInDays?: number;
    clientCertificateExpiresAt?: string;
    caCertificateExpiresAt?: string;
    encryptionAlgorithm: string;
    keyId: string;
    secretValueExposed: false;
    rotationStatus: 'CURRENT' | 'SCHEDULE' | 'DUE';
    recommendedRotationBy: string;
    rotationSteps: string[];
    findings: string[];
    recommendations: string[];
    connectionMessage: string;
  };
  upgrade: {
    status: 'READY' | 'REVIEW' | 'BLOCKED' | 'UNKNOWN';
    score: number;
    currentVersion?: string;
    targetVersion?: string;
    nodes: Array<{ name: string; status?: string; kubeletVersion?: string; minorSkew: number }>;
    findings: Array<{
      severity: string;
      category: string;
      title: string;
      detail: string;
      resourceRef?: string;
      evidence: string;
    }>;
    recommendedSteps: string[];
    compatibilityCatalogVersion: string;
    evidenceSource: string;
  };
  checkedAt: string;
  cacheExpiresAt: string;
}

export interface RuntimeReadinessResponse {
  status: 'READY' | 'PILOT' | 'BLOCKED';
  mode: string;
  checkedAt: string;
  checks: Array<{
    code: string;
    status: 'READY' | 'PILOT' | 'BLOCKED';
    title: string;
    detail: string;
    action: string;
    observedValue: string;
  }>;
}

export interface RegisterClusterRequest {
  tenantId: string;
  workspaceId: string;
  name: string;
  description?: string;
  environment: 'DEV' | 'STAGING' | 'PROD' | 'ETC';
  provider: 'EKS' | 'GKE' | 'AKS' | 'ON_PREM' | 'KIND' | 'ETC';
  region?: string;
  credentialType: 'KUBECONFIG' | 'SERVICE_ACCOUNT_TOKEN';
  kubeconfig?: string;
  serviceAccount?: {
    apiServerUrl: string;
    caCertificate: string;
    token: string;
  };
  namespaceAccess?: {
    clusterWide: boolean;
    allowedNamespaces?: string[];
    defaultNamespace?: string;
  };
  syncSettings?: {
    autoSyncEnabled?: boolean;
    syncIntervalSeconds?: number;
  };
}

export interface TenantResponse {
  id: string;
  code: string;
  name: string;
  description?: string;
  status: 'ACTIVE' | 'SUSPENDED';
  createdAt?: string;
}

export interface WorkspaceResponse {
  id: string;
  tenantId: string;
  code: string;
  name: string;
  description?: string;
  status: 'ACTIVE' | 'ARCHIVED';
  createdAt?: string;
}

export interface CreateTenancyRequest {
  code: string;
  name: string;
  description?: string;
}

export interface ClusterConnectionTestResponse {
  clusterId: string;
  reachable: boolean;
  kubernetesVersion?: string;
  namespaces?: string[];
  message?: string;
  checkedAt?: string;
}

export interface ClusterCredentialResponse {
  clusterId: string;
  credentialType: 'KUBECONFIG' | 'SERVICE_ACCOUNT_TOKEN';
  payload: string;
  revealed: boolean;
  masked: boolean;
  updatedAt?: string;
}

export interface ClusterSyncSettingsResponse {
  id: string;
  clusterId: string;
  autoSyncEnabled: boolean;
  syncIntervalSeconds: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface ClusterSyncStatusResponse {
  syncJobId: string;
  asyncJobId?: string;
  clusterId: string;
  syncType?: string;
  status?: string;
  resourceCount: number;
  eventCount: number;
  startedAt?: string;
  completedAt?: string;
  errorMessage?: string;
  createdAt?: string;
}

export interface KubernetesNamespaceResponse {
  name: string;
  phase?: string;
}

export interface KubernetesNodeResponse {
  name: string;
  status?: string;
  kubernetesVersion?: string;
  osImage?: string;
  containerRuntimeVersion?: string;
}

export interface CommandCapabilityResponse {
  clusterId: string;
  namespace?: string;
  runnerAvailable: boolean;
  kubectlVersion: string;
  executionBoundary: 'LOCAL_PROCESS' | 'ISOLATED_RUNNER';
  terminalBoundary: 'BACKEND_FABRIC8';
  metricsApiAvailable: boolean;
  supportedModes: string[];
  maximumCommandLength: number;
  maximumOutputBytes: number;
  maximumUserCommands: number;
  maximumUserTerminals: number;
  maximumClusterCommands: number;
  maximumClusterTerminals: number;
  maximumUserStartsPerMinute: number;
}

export interface CommandValidationResponse {
  normalizedCommand: string;
  arguments: string[];
  namespace?: string;
  safety: 'READ_ONLY' | 'DIAGNOSE' | 'CHANGE' | 'DESTRUCTIVE' | 'PRIVILEGED_INTERACTIVE';
  requiresConfirmation: boolean;
  interactive: boolean;
  targetSummary: string;
  warnings: string[];
}

export interface CommandExecutionResponse {
  id: string;
  clusterId: string;
  sourceAnalysisId?: string;
  namespace?: string;
  command: string;
  safety: CommandValidationResponse['safety'];
  status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'TIMED_OUT' | 'CANCELED';
  stdoutText?: string;
  stderrText?: string;
  exitCode?: number;
  durationMs?: number;
  truncated: boolean;
  verificationStatus?: 'NOT_REQUIRED' | 'PENDING' | 'VERIFIED_CHANGED' | 'VERIFIED_STABLE' | 'VERIFICATION_FAILED';
  verificationSummary?: string;
  beforeSnapshot?: string;
  afterSnapshot?: string;
  rollbackCommand?: string;
  verifiedAt?: string;
  createdBy: string;
  requestId?: string;
  createdAt: string;
  startedAt?: string;
  completedAt?: string;
}

export interface TerminalSessionResponse {
  sessionId: string;
  executionId: string;
  websocketPath: string;
  expiresAt: string;
}

export interface CommandFavoriteResponse {
  id: string;
  clusterId: string;
  ownerUserId: string;
  name: string;
  description?: string;
  command: string;
  namespace?: string;
  shared: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface CommandFavoriteRequest {
  name: string;
  description?: string;
  command: string;
  namespace?: string;
  shared: boolean;
  sortOrder: number;
}

export interface KubernetesResourceSnapshotResponse {
  id: string;
  clusterId: string;
  syncJobId: string;
  namespace?: string;
  resourceType: string;
  resourceName: string;
  resourceUid?: string;
  status?: string;
  summaryJson?: string;
  truncated: boolean;
  collectedAt?: string;
}

export interface ResourceFacetResponse {
  value: string;
  count: number;
}

export interface ClusterResourcePageResponse {
  items: KubernetesResourceSnapshotResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  problemCount: number;
  namespaceFacets: ResourceFacetResponse[];
  resourceTypeFacets: ResourceFacetResponse[];
}

export interface KubernetesResourceManifestResponse {
  clusterId: string;
  namespace?: string;
  resourceType: string;
  resourceName: string;
  manifestYaml: string;
  secretRedacted: boolean;
  collectedAt?: string;
  source?: 'LIVE' | 'SNAPSHOT_FALLBACK' | string;
  fallbackReason?: string;
}

export interface ClusterResourceLogTargetsResponse {
  clusterId: string;
  namespace: string;
  resourceType: string;
  resourceName: string;
  supported: boolean;
  unavailableReason?: string;
  pods: Array<{
    podName: string;
    phase: string;
    startedAt?: string;
    containers: Array<{
      containerName: string;
      ready: boolean;
      restartCount: number;
      state: string;
      initContainer: boolean;
    }>;
  }>;
}

export interface ClusterResourceLogResponse {
  clusterId: string;
  namespace: string;
  resourceType: string;
  resourceName: string;
  podName: string;
  containerName: string;
  tailLines: number;
  previous: boolean;
  log: string;
  truncated: boolean;
  collectedAt?: string;
}

export interface ClusterResourceLogLine {
  podName: string;
  containerName: string;
  line: string;
  observedAt?: string;
}

export interface ClusterResourceLogStreamResult {
  reason: string;
  lineCount: number;
  durationMs: number;
}

export interface ClusterResourceLogStreamRequest {
  clusterId: string;
  namespace: string;
  resourceType: string;
  resourceName: string;
  podName: string;
  containerName: string;
  tailLines: number;
}

export interface KubernetesEventSnapshotResponse {
  id: string;
  clusterId: string;
  syncJobId: string;
  namespace?: string;
  involvedKind?: string;
  involvedName?: string;
  reason?: string;
  type?: string;
  message?: string;
  eventTime?: string;
  count?: number;
  collectedAt?: string;
}

export interface NamespaceDiagnosticsResponse {
  clusterId: string;
  namespace: string;
  collectedAt?: string;
  resourceCount: number;
  eventCount: number;
  warningEventCount: number;
  podLogCount: number;
  healthScore?: {
    availability: number;
    stability: number;
    performance: number;
    security: number;
    operability: number;
    overall: number;
  };
  riskForecast?: {
    overallRisk: number;
    riskLevel: string;
    horizon: string;
    summary: string;
    predictions: Array<{
      category: string;
      severity: string;
      probability: number;
      horizon: string;
      resourceKind?: string;
      resourceName?: string;
      signal?: string;
      impact?: string;
      recommendation?: string;
      evidence?: string;
      verificationCommand?: string;
    }>;
  };
  changeTimeline: Array<{
    occurredAt?: string;
    severity?: string;
    category?: string;
    resourceKind?: string;
    resourceName?: string;
    title?: string;
    detail?: string;
    suspectedChange?: string;
    recommendation?: string;
  }>;
  runbookActions: Array<{
    priority?: string;
    title?: string;
    targetKind?: string;
    targetName?: string;
    reason?: string;
    why?: string;
    category?: string;
    commandType?: string;
    command?: string;
    destructive?: boolean;
  }>;
  resourceKinds: Array<{
    resourceType: string;
    count: number;
  }>;
  problemResources: Array<{
    resourceType: string;
    resourceName: string;
    status: string;
    summaryJson?: string;
  }>;
  warningEvents: Array<{
    type?: string;
    reason?: string;
    involvedKind?: string;
    involvedName?: string;
    message?: string;
    count?: number;
    eventTime?: string;
  }>;
  evidenceSignals: Array<{
    severity?: string;
    source?: string;
    message?: string;
  }>;
  podLogSources: Array<{
    podName: string;
    containerName: string;
    truncated: boolean;
  }>;
}

export interface PodLogsResponse {
  clusterId: string;
  namespace: string;
  podName: string;
  tailLines: number;
  collectedAt?: string;
  containers: Array<{
    containerName: string;
    log: string;
    truncated: boolean;
  }>;
}

export interface StartJobResponse {
  jobId: string;
}

export interface StartAnalysisJobResponse {
  jobId: string;
  analysisId: string;
}

export interface JobResponse {
  id: string;
  type: string;
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELED' | 'TIMEOUT';
  createdAt?: string;
  startedAt?: string;
  completedAt?: string;
  errorCode?: string;
  errorMessage?: string;
}

export interface ApplicationResponse {
  id: string;
  name: string;
  namespace?: string;
  deploymentType?: string;
  helmReleaseName?: string;
  status?: string;
  clusterId?: string;
  createdAt?: string;
  currentReleaseRevision?: number;
  chartVersionId?: string;
  valuesRevisionId?: string;
}

export interface ApplicationRuntimeResponse {
  readyPods: number;
  totalPods: number;
  restarts: number;
  workloads: Array<{ kind: string; name: string; ready: number; desired: number; status: string }>;
  endpoints: Array<{ type: string; name: string; url: string; status: string }>;
}

export interface ApplicationStatusResponse {
  applicationId: string;
  clusterId: string;
  namespace: string;
  name: string;
  status: string;
  lastSyncedAt?: string;
  lastSyncStatus?: string;
  lastSyncError?: string;
}

export interface ApplicationRollbackRevisionResponse {
  revision: string;
  current: boolean;
  replicaSetName?: string;
  replicas?: number;
  image?: string;
  state?: string;
  createdAt?: string;
}

export interface ApplicationRollbackPreviewResponse {
  applicationId: string;
  clusterId: string;
  namespace: string;
  deploymentName: string;
  currentRevision?: string;
  targetRevision?: string;
  executable: boolean;
  reason?: string;
  confirmationText?: string;
  currentState?: string;
  targetState?: string;
  revisions: ApplicationRollbackRevisionResponse[];
  plannedAt?: string;
}

export interface AnalysisResponse {
  id: string;
  asyncJobId?: string;
  clusterId?: string;
  applicationId?: string;
  namespace?: string;
  status?: string;
  aiProvider?: string;
  aiModel?: string;
  promptVersion?: string;
  schemaVersion?: string;
  locale?: 'ko-KR' | 'en-US';
  resultSummary?: string;
  resultJson?: string;
  createdBy?: string;
  createdAt?: string;
}

export interface AnalysisCommandExecutionResponse {
  id: string;
  analysisId: string;
  clusterId: string;
  namespace?: string;
  command: string;
  safety: 'READ_ONLY' | 'DIAGNOSE' | 'CHANGE' | 'DESTRUCTIVE' | 'BLOCKED';
  status: 'SUCCEEDED' | 'FAILED' | 'BLOCKED';
  reason?: string;
  stdoutText?: string;
  stderrText?: string;
  exitCode?: number;
  durationMs?: number;
  createdBy?: string;
  createdAt?: string;
}

export interface AnalysisCommandPreviewResponse {
  command: string;
  safety: 'READ_ONLY' | 'DIAGNOSE' | 'CHANGE' | 'DESTRUCTIVE' | 'BLOCKED';
  executable: boolean;
  reason?: string;
  normalizedNamespace?: string;
  rbacAllowed?: boolean;
  dryRunPassed?: boolean;
  rollbackGuardPassed?: boolean;
  guardMessage?: string;
  dryRunSummary?: string;
  requiresConfirmation: boolean;
  confirmationText?: string;
}

export interface AnalysisWorkflowStateResponse {
  id: string;
  analysisId: string;
  issueGroupId: string;
  status: WorkflowStatusValue;
  note?: string;
  updatedBy?: string;
  updatedAt?: string;
}

export type WorkflowStatusValue = 'OPEN' | 'INVESTIGATING' | 'ACTION_PENDING' | 'FIXED' | 'ACCEPTED';

export interface AiChatConversationResponse {
  id: string;
  title: string;
  mode: 'GENERAL' | 'CLUSTER';
  favorite: boolean;
  clusterId?: string;
  namespace?: string;
  applicationId?: string;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
  archivedAt?: string;
}

export interface AiChatMessageResponse {
  id: string;
  conversationId: string;
  role: 'USER' | 'ASSISTANT' | 'SYSTEM';
  content: string;
  model?: string;
  finishReason?: string;
  latencyMs?: number;
  firstTokenLatencyMs?: number;
  totalLatencyMs?: number;
  contextChars?: number;
  errorCode?: string;
  errorMessage?: string;
  createdAt?: string;
}

export interface AiChatSendMessageResponse {
  conversationId: string;
  userMessage: AiChatMessageResponse;
  assistantMessage: AiChatMessageResponse;
  contextReferences?: Array<{
    id: string;
    referenceType: string;
    referenceId?: string;
    label: string;
  }>;
}

export interface AiChatContextReferenceResponse {
  id: string;
  messageId: string;
  referenceType: string;
  referenceId?: string;
  label: string;
  createdAt?: string;
}

export interface CreateConversationRequest {
  title: string;
  mode: 'GENERAL' | 'CLUSTER';
  clusterId?: string;
  namespace?: string;
  applicationId?: string;
}

export interface SendMessageRequest {
  message: string;
  contextSelection?: {
    clusterId?: string;
    namespace?: string;
    applicationId?: string;
    includeRecentEvents?: boolean;
    resourceType?: string;
    resourceName?: string;
    includeLogs?: boolean;
    logLineLimit?: number;
    resourceSnapshotIds?: string[];
    eventSnapshotIds?: string[];
  };
}

export type IncidentState = 'OPEN' | 'ACKNOWLEDGED' | 'INVESTIGATING' | 'MITIGATING' | 'MONITORING' | 'RESOLVED' | 'REOPENED';

export interface IncidentResponse {
  id: string;
  fingerprint: string;
  clusterId: string;
  clusterName?: string;
  namespace?: string;
  resourceKind?: string;
  resourceName?: string;
  category: string;
  severity: string;
  state: IncidentState;
  title: string;
  summary?: string;
  nextAction?: string;
  occurrenceCount: number;
  reopenCount: number;
  sourceAnalysisId?: string;
  firstDetectedAt?: string;
  lastDetectedAt?: string;
  updatedBy?: string;
}

export interface IncidentEvidenceResponse {
  id: string;
  incidentId: string;
  evidenceKey: string;
  evidenceType: string;
  sourceRef?: string;
  summary: string;
  factual: boolean;
  occurredAt?: string;
}

export interface IncidentActivityResponse {
  id: string;
  incidentId: string;
  activityType: string;
  fromState?: IncidentState;
  toState?: IncidentState;
  note?: string;
  actor?: string;
  createdAt?: string;
}

export interface IncidentDetailResponse {
  incident: IncidentResponse;
  evidence: IncidentEvidenceResponse[];
  timeline: IncidentActivityResponse[];
  recovery: {
    incidentId: string;
    consecutiveHealthyCount: number;
    requiredHealthyCount: number;
    firstHealthyAt?: string;
    lastObservedAt?: string;
    lastObservedStatus?: string;
    autoResolvable: boolean;
  };
  intelligence: {
    correlation: {
      nodes: Array<{
        id: string;
        namespace?: string;
        resourceKind: string;
        resourceName: string;
        status?: string;
        role: string;
        unhealthy: boolean;
        inferred: boolean;
      }>;
      edges: Array<{
        sourceId: string;
        targetId: string;
        relation: string;
        inferred: boolean;
        evidence?: string;
      }>;
      impactedResourceCount: number;
      impactedWorkloadCount: number;
      impactedServiceCount: number;
      blastRadiusSummary: string;
      inventoryCollectedAt?: string;
    };
    changeCandidates: Array<{
      changeId: string;
      namespace?: string;
      resourceKind: string;
      resourceName: string;
      changeType: string;
      summary?: string;
      detectedAt?: string;
      relevanceScore: number;
      relation: string;
      explanation: string;
    }>;
    confidence: {
      score: number;
      level: 'HIGH' | 'MEDIUM' | 'LOW';
      freshness: 'FRESH' | 'STALE' | 'UNKNOWN';
      factualEvidenceCount: number;
      inferenceEvidenceCount: number;
      verifiedRelationCount: number;
      missingEvidence: string[];
      rationale: string[];
    };
    verificationPlan: Array<{
      order: number;
      title: string;
      purpose: string;
      command: string;
      expectedSignal: string;
      safetyLevel: string;
      destructive: boolean;
    }>;
  };
}

export interface WatchRuntimeStatusResponse {
  clusterId: string;
  clusterName: string;
  state: 'STARTING' | 'CONNECTING' | 'CONNECTED' | 'DEGRADED' | 'FAILED' | 'DISABLED' | 'PAUSED' | 'POLLING' | 'RECOVERING';
  connectedAt?: string;
  lastSignalAt?: string;
  reconnectCount: number;
  lastError?: string;
  updatedAt?: string;
  lastHeartbeatAt?: string;
  nextRetryAt?: string;
  consecutiveFailures: number;
  paused: boolean;
}

export interface FleetQueueResponse {
  generatedAt: string;
  totalItems: number;
  immediateItems: number;
  degradedCollectors: number;
  items: Array<{
    id: string;
    sourceType: string;
    clusterId?: string;
    clusterName: string;
    namespace?: string;
    severity: string;
    score: number;
    title: string;
    summary?: string;
    nextAction?: string;
    targetPath?: string;
    detectedAt?: string;
  }>;
}

export interface ShiftBriefingResponse {
  generatedAt: string;
  posture: 'ACTION_REQUIRED' | 'DEGRADED_VISIBILITY' | 'STABLE';
  beginnerSummary: string;
  expertSummary: string;
  openIncidents: number;
  criticalIncidents: number;
  runningJobs: number;
  failedJobs: number;
  degradedCollectors: number;
  immediateActions: string[];
  watchItems: string[];
}

export interface ValidationScenarioResponse {
  id: string;
  title: string;
  category: string;
  signal: string;
  expectedRootCause: string;
  resourceKind: string;
  safetyMode: 'VIRTUAL_SAFE';
}

export interface ValidationLabRunResponse {
  runId: string;
  status: 'PASSED' | 'FAILED';
  score: number;
  passedCases: number;
  totalCases: number;
  mode: 'VIRTUAL_SAFE';
  completedAt: string;
  cases: Array<{
    scenarioId: string;
    title: string;
    category: string;
    status: 'PASSED' | 'FAILED';
    score: number;
    assertions: string[];
    failures: string[];
    durationMs: number;
  }>;
}

export interface LiveValidationPolicyResponse {
  enabled: boolean;
  safetyMode: 'LIVE_GUARDED';
  namespacePrefix: string;
  maximumTtlSeconds: number;
  requiredConfirmation: string;
  safeguards: string[];
}

export interface LiveValidationPreviewResponse {
  clusterId: string;
  clusterName: string;
  scenarioId: string;
  namespace: string;
  ttlSeconds: number;
  executable: boolean;
  passedChecks: string[];
  blockingReasons: string[];
  plannedResources: string[];
  expiresAt: string;
}

export interface LiveValidationRunResponse {
  id: string;
  clusterId: string;
  clusterName: string;
  scenarioId: string;
  namespace: string;
  state: 'RUNNING' | 'PASSED' | 'EVIDENCE_PENDING' | 'FAILED' | 'CLEANED' | 'CLEANUP_FAILED';
  safetyMode: 'LIVE_GUARDED';
  ttlSeconds: number;
  safetyChecks: string[];
  resources: string[];
  observedSignal?: string;
  detail?: string;
  cleanupRequired: boolean;
  startedAt: string;
  expiresAt: string;
  completedAt?: string;
  triggeredBy: string;
}

export interface AnalysisBenchmarkResponse {
  runId: string;
  state: 'PASSED' | 'BLOCKED';
  overallScore: number;
  classificationAccuracy: number;
  evidenceCoverage: number;
  commandSafetyRate: number;
  falseAssertionRate: number;
  p95LatencyMs: number;
  passedCases: number;
  totalCases: number;
  baselineVersion: string;
  releaseRecommendation: 'RELEASE_READY' | 'HOLD';
  blockingReasons: string[];
  completedAt: string;
}

export interface RemediationLearningResponse {
  incidentId: string;
  category: string;
  resourceKind?: string;
  comparableSamples: number;
  evidenceNotice: string;
  recommendations: Array<{
    category: string;
    resourceKind?: string;
    action: string;
    sampleCount: number;
    succeededCount: number;
    failedCount: number;
    successRate: number;
    averageObservationSeconds: number;
    confidence: 'LOW' | 'MEDIUM' | 'HIGH';
    explanation: string;
  }>;
}

export interface ReliabilityTrendResponse {
  generatedAt: string;
  windowDays: number;
  clusterId?: string;
  namespace?: string;
  evidenceType: 'EVENT_BASED';
  incidentsDetected: number;
  incidentsResolved: number;
  recurringIncidents: number;
  recurrenceRate: number;
  meanTimeToAcknowledgeMinutes: number;
  meanTimeToResolveMinutes: number;
  remediationSuccessRate: number;
  collectorCoverageRate: number;
  daily: Array<{ date: string; detected: number; resolved: number; recurred: number }>;
  scopes: Array<{
    clusterId: string;
    clusterName: string;
    namespace?: string;
    detected: number;
    open: number;
    resolved: number;
    recurred: number;
  }>;
}

export interface WatchContinuityResponse {
  clusterId: string;
  podResourceVersion?: string;
  eventResourceVersion?: string;
  continuityState: 'RECONCILED' | 'STREAMING' | 'DEGRADED' | string;
  gapSignalCount: number;
  lastReconciledAt?: string;
  lastError?: string;
  updatedAt?: string;
}

export interface SignalNoisePolicyResponse {
  id: string;
  name: string;
  clusterId?: string;
  namespacePattern: string;
  severityFloor: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  repeatThreshold: number;
  maintenanceStart?: string;
  maintenanceEnd?: string;
  snoozeUntil?: string;
  enabled: boolean;
  updatedBy?: string;
  updatedAt?: string;
}

export interface RemediationObservationResponse {
  id: string;
  incidentId: string;
  analysisId?: string;
  commandExecutionId?: string;
  state: 'OBSERVING' | 'SUCCEEDED' | 'FAILED' | 'INCONCLUSIVE' | 'CANCELLED';
  observationSeconds: number;
  startedAt: string;
  observeUntil: string;
  baselineJson: string;
  latestJson?: string;
  conclusion?: string;
  rollbackCandidate?: string;
  updatedBy?: string;
  updatedAt?: string;
}

export interface AiReleaseGateResponse {
  id: string;
  candidateVersion: string;
  baselineVersion: string;
  state: 'PASSED' | 'BLOCKED';
  regressionScore: number;
  minimumRegressionScore: number;
  groundTruthSamples: number;
  minimumGroundTruthSamples: number;
  verifiedAccuracy: number;
  minimumVerifiedAccuracy: number;
  dangerousSuggestionCount: number;
  reasons: string[];
  evaluatedAt: string;
  evaluatedBy?: string;
}

export interface IncidentPostmortemResponse {
  incidentId: string;
  title: string;
  impact: string;
  rootCause: string;
  resolution: string;
  evidence: string[];
  prevention: string[];
  generatedAt: string;
  generatedBy?: string;
}

export interface WatchSignalGroupResponse {
  id: string;
  clusterId: string;
  clusterName: string;
  namespace?: string;
  resourceKind: string;
  resourceName: string;
  category: string;
  severity: string;
  state: 'OPEN' | 'ACKNOWLEDGED' | 'SUPPRESSED' | 'INCIDENT_CREATED';
  reason?: string;
  summary?: string;
  occurrenceCount: number;
  firstObservedAt?: string;
  lastObservedAt?: string;
  incidentId?: string;
}

export interface TriageItemResponse {
  id: string;
  sourceType: string;
  clusterId: string;
  clusterName: string;
  namespace?: string;
  resourceKind: string;
  resourceName: string;
  severity: string;
  state: string;
  score: number;
  confidence: number;
  impact: number;
  occurrences: number;
  title: string;
  summary?: string;
  nextAction?: string;
  incidentId?: string;
  firstObservedAt?: string;
  lastObservedAt?: string;
}

export interface TriageQueueResponse {
  generatedAt: string;
  openItems: number;
  highPriority: number;
  promotedSignals: number;
  suppressedSignals: number;
  items: TriageItemResponse[];
}

export interface WatchSignalResponse {
  id: string;
  clusterId: string;
  clusterName: string;
  namespace?: string;
  resourceKind: string;
  resourceName: string;
  action: string;
  reason?: string;
  status?: string;
  summary?: string;
  observedAt?: string;
}

export interface RegressionCaseResultResponse {
  id: string;
  runId: string;
  caseId: string;
  title: string;
  category: string;
  status: 'PASSED' | 'FAILED';
  score: number;
  assertions: string[];
  failures: string[];
  durationMs: number;
}

export interface RegressionRunResponse {
  id: string;
  status: 'PASSED' | 'FAILED';
  passedCases: number;
  totalCases: number;
  score: number;
  baselineVersion: string;
  triggeredBy: string;
  startedAt?: string;
  completedAt?: string;
  cases: RegressionCaseResultResponse[];
}

export interface OperationNotificationResponse {
  id: string;
  notificationType: string;
  severity: string;
  title: string;
  message?: string;
  targetPath?: string;
  read: boolean;
  occurrenceCount: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface PolicyDefinitionResponse {
  id: string;
  name: string;
  description: string;
  category: string;
  severity: string;
  enabled: boolean;
}

export interface PolicyEvaluationResponse {
  id: string;
  policyId: string;
  clusterId: string;
  clusterName?: string;
  namespace?: string;
  resourceKind?: string;
  resourceName?: string;
  result: 'PASS' | 'WARN' | 'FAIL' | 'NOT_APPLICABLE';
  evidence?: string;
  recommendation?: string;
  evaluatedAt?: string;
}

export interface ResourceChangeResponse {
  id: string;
  clusterId: string;
  clusterName?: string;
  namespace?: string;
  resourceKind: string;
  resourceName: string;
  changeType: 'CREATED' | 'UPDATED' | 'DELETED' | 'STATUS_CHANGED';
  previousStatus?: string;
  currentStatus?: string;
  summary?: string;
  detectedAt?: string;
}

export interface RunbookTemplateResponse {
  id: string;
  signal: string;
  category: string;
  resourceKind?: string;
  title: string;
  beginnerExplanation: string;
  verificationCommand: string;
  expectedResult: string;
  safeAction?: string;
  validationCommand: string;
  rollbackGuidance?: string;
  safetyLevel: string;
  version: number;
  enabled: boolean;
}

export interface OperatorSearchResultResponse {
  id: string;
  type: 'CLUSTER' | 'RESOURCE' | 'INCIDENT' | 'ANALYSIS' | 'RUNBOOK';
  title: string;
  description?: string;
  clusterId?: string;
  clusterName?: string;
  namespace?: string;
  resourceKind?: string;
  resourceName?: string;
  status?: string;
  targetPath: string;
  updatedAt?: string;
}

export interface ResourceContextResponse {
  clusterId: string;
  namespace?: string;
  resourceKind: string;
  resourceName: string;
  status?: string;
  collectedAt?: string;
  relations: Array<{ relation: string; resourceKind: string; resourceName: string; status?: string; explicit: boolean; evidence: string }>;
  changes: Array<{ id: string; changeType: string; previousStatus?: string; currentStatus?: string; summary?: string; detectedAt?: string }>;
  fieldDiffs: Array<{ field: string; previousValue?: string; currentValue?: string }>;
  incidents: Array<{ id: string; severity: string; state: string; title: string; lastDetectedAt?: string }>;
}

export interface IncidentCollaborationResponse {
  incidentId: string;
  assignee?: string;
  tags: string[];
  acknowledgeDueAt?: string;
  resolveDueAt?: string;
  updatedBy?: string;
  updatedAt?: string;
  links: Array<{ incidentId: string; relatedIncidentId: string; relationType: string; relatedTitle: string; relatedState: string; createdBy: string; createdAt: string }>;
}

export interface ManagedRunbookResponse extends RunbookTemplateResponse {
  sourceType: 'SYSTEM' | 'CUSTOM';
  owner: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface RunbookVersionResponse {
  id: string;
  runbookId: string;
  version: number;
  changeNote?: string;
  createdBy: string;
  createdAt: string;
}

export type RunbookWriteRequest = Omit<ManagedRunbookResponse, 'id' | 'sourceType' | 'owner' | 'createdAt' | 'updatedAt' | 'version'> & { changeNote?: string };

export interface AnalysisFeedbackResponse {
  analysisId: string;
  accuracy: 'CORRECT' | 'PARTIAL' | 'INCORRECT';
  outcome: 'RESOLVED' | 'IMPROVED' | 'NO_CHANGE' | 'WORSE' | 'NOT_TRIED';
  dangerousSuggestion: boolean;
  comment?: string;
  actualRootCause?: string;
  actualResolution?: string;
  validatedResourceKind?: string;
  validatedResourceName?: string;
  confidenceExpectation?: 'LOW' | 'MEDIUM' | 'HIGH';
  submittedBy?: string;
  updatedAt?: string;
}

export interface AiCalibrationSummaryResponse {
  feedbackCount: number;
  groundTruthCount: number;
  groundTruthCoverageRate: number;
  verifiedAccuracyRate: number;
  resolutionRate: number;
  dangerousSuggestionCount: number;
  profiles: Array<{
    model: string;
    promptVersion: string;
    feedbackCount: number;
    verifiedAccuracyRate: number;
    resolutionRate: number;
    dangerousSuggestionCount: number;
  }>;
  recentGroundTruth: Array<{
    analysisId: string;
    model: string;
    promptVersion: string;
    accuracy: string;
    actualRootCause?: string;
    actualResolution?: string;
    validatedResourceKind?: string;
    validatedResourceName?: string;
    updatedAt?: string;
  }>;
}

export interface OperationsScorecardResponse {
  generatedAt: string;
  totalIncidents: number;
  openIncidents: number;
  resolvedIncidents: number;
  meanTimeToAcknowledgeMinutes: number;
  meanTimeToResolveMinutes: number;
  recurrenceRate: number;
  mitigationSuccessRate: number;
  analysisSuccessRate: number;
  fallbackRate: number;
  openSignalGroups: number;
  promotedSignalGroups: number;
  suppressedSignalGroups: number;
  weeklyTrend: {
    currentIncidents: number;
    previousIncidents: number;
    currentResolved: number;
    previousResolved: number;
    currentAnalyses: number;
    previousAnalyses: number;
    direction: 'IMPROVING' | 'DEGRADING' | 'STABLE';
  };
  hotspots: Array<{
    clusterId: string;
    clusterName: string;
    namespace?: string;
    resourceKind?: string;
    resourceName?: string;
    incidentCount: number;
    recurrenceCount: number;
    severity: string;
    lastDetectedAt?: string;
  }>;
}

export interface AiQualitySummaryResponse {
  feedbackCount: number;
  correctCount: number;
  partialCount: number;
  incorrectCount: number;
  resolvedOrImprovedCount: number;
  dangerousSuggestionCount: number;
  successfulAnalyses: number;
  failedAnalyses: number;
  successRate: number;
  verifiedAccuracyRate: number;
}

export interface AiTrustSnapshotResponse {
  schemaVersion: 'ai-trust.v1';
  generatedAt: string;
  state: 'TRUSTED' | 'NEEDS_EVIDENCE' | 'BLOCKED';
  quality: AiQualitySummaryResponse;
  calibration: AiCalibrationSummaryResponse;
  latestBenchmark?: AnalysisBenchmarkResponse;
  corpus: {
    version: string;
    distinctCases: number;
    categories: string[];
    abstentionCases: number;
    generatedVariantsRemoved: boolean;
  };
  contractEvaluation: {
    state: 'MEASURED' | 'INSUFFICIENT_EVIDENCE';
    sampleCount: number;
    macroF1: number;
    abstentionAccuracy: number;
    categories: Array<{
      category: string;
      truePositive: number;
      falsePositive: number;
      falseNegative: number;
      precision: number;
      recall: number;
      f1: number;
    }>;
  };
  recentRegressions: RegressionRunResponse[];
  recentReleaseGates: AiReleaseGateResponse[];
}

export interface ProductionEvidenceRunResponse {
  id: string;
  releaseName: string;
  createNamespace: boolean;
  environment: string;
  state: 'NOT_RUN' | 'RUNNING' | 'PASSED' | 'FAILED' | 'BLOCKED' | 'EXPIRED';
  triggeredBy: string;
  startedAt: string;
  completedAt?: string;
  expiresAt?: string;
  checks: Array<{
    id: string;
    category: string;
    code: string;
    state: 'NOT_RUN' | 'PASSED' | 'FAILED' | 'BLOCKED';
    title: string;
    detail?: string;
    observedValue?: string;
    action?: string;
    durationMs: number;
    checkedAt: string;
    artifacts: Array<{ id: string; fileName: string; mediaType: string; checksum: string; sizeBytes: number; reference?: string }>;
  }>;
}

export interface OperationalTelemetryResponse {
  schemaVersion: 'operations-telemetry.v1';
  generatedAt: string;
  retention: 'INSTANCE_SNAPSHOT';
  requestCount: number;
  failureCount: number;
  series: Array<{ operation: string; outcome: string; count: number; averageMs: number; p95Ms: number; maxMs: number }>;
}

export interface ClusterHealthResponse {
  clusterId: string;
  clusterName: string;
  status: string;
  healthScore: number;
  openIncidents: number;
  failedPolicies: number;
  warningEvents: number;
  lastObservedAt?: string;
  posture: string;
}

export interface PriorityItemResponse {
  id: string;
  sourceType: string;
  urgency: string;
  score: number;
  severity: string;
  clusterId?: string;
  clusterName?: string;
  namespace?: string;
  title: string;
  reason?: string;
  nextAction?: string;
  targetPath: string;
  detectedAt?: string;
}

export interface OperationsOverviewResponse {
  generatedAt: string;
  clusters: number;
  openIncidents: number;
  criticalIncidents: number;
  unreadNotifications: number;
  failedPolicyEvaluations: number;
  runningJobs: number;
  failedJobs: number;
  averageJobDurationMs: number;
  successfulAnalyses: number;
  failedAnalyses: number;
  priorityQueue: PriorityItemResponse[];
  clusterHealth: ClusterHealthResponse[];
  capacityPosture: {
    dataSource: string;
    workloads: number;
    unavailableWorkloads: number;
    pods: number;
    unhealthyPods: number;
    pendingPvcs: number;
    namespacesWithoutNetworkPolicy: number;
    governanceEvidenceGaps: number;
    summary: string;
  };
  aiQuality: AiQualitySummaryResponse;
}

export interface OperationSettingsResponse {
  eventRetentionDays: number;
  analysisRetentionDays: number;
  jobRetentionDays: number;
  notificationRetentionDays: number;
  resolvedIncidentRetentionDays: number;
  changeRetentionDays: number;
  auditRetentionDays: number;
  commandRetentionDays: number;
  notificationSuppressMinutes: number;
  staleSyncMinutes: number;
  longRunningJobSeconds: number;
  updatedBy?: string;
  updatedAt?: string;
}

export interface CleanupPreviewResponse {
  eventSnapshots: number;
  jobs: number;
  notifications: number;
  changes: number;
  resolvedIncidents: number;
  policyEvaluations: number;
  analyses: number;
  watchSignals: number;
  regressionRuns: number;
  auditLogs: number;
  commandExecutions: number;
  executed: boolean;
}

export interface AuditLogResponse {
  id: string;
  action: string;
  targetType: string;
  targetId: string;
  actor: string;
  requestId?: string;
  createdAt: string;
}

export interface CatalogPackageResponse {
  packageId: string;
  repository: string;
  repositoryDisplayName: string;
  repositoryUrl?: string;
  name: string;
  description?: string;
  version: string;
  appVersion?: string;
  contentUrl?: string;
  official: boolean;
  verifiedPublisher: boolean;
  availableVersions: string[];
}

export interface ChartVersionResponse {
  id: string;
  chartVersion: string;
  appVersion?: string;
  digestSha256: string;
  provenanceStatus: string;
  sourceReference: string;
  importedAt: string;
}

export interface LibraryChartResponse {
  id: string;
  tenantId: string;
  name: string;
  description?: string;
  sourceType: string;
  sourceName?: string;
  repositoryUrl?: string;
  trustStatus: string;
  versions: ChartVersionResponse[];
}

export interface ChartSourceResponse {
  id: string;
  tenantId: string;
  sourceType: 'HELM_REPOSITORY' | 'OCI_REGISTRY';
  name: string;
  endpoint: string;
  credentialConfigured: boolean;
  tlsPolicy: string;
  enabled: boolean;
  updatedAt: string;
}

export interface ValuesProfileResponse {
  id: string;
  tenantId: string;
  chartVersionId: string;
  name: string;
  description?: string;
  updatedAt: string;
}

export interface ValuesRevisionResponse {
  id: string;
  revision: number;
  valuesSha256: string;
  parentRevision?: number;
  createdBy: string;
  createdAt: string;
}

export interface DeploymentPlanResponse {
  id: string;
  applicationId?: string;
  clusterId: string;
  chartVersionId: string;
  valuesRevisionId?: string;
  namespace: string;
  releaseName: string;
  exposureType: 'NONE' | 'HTTP_ROUTE';
  hostname?: string;
  exposurePath?: string;
  backendServiceName?: string;
  backendServicePort?: number;
  gatewayName?: string;
  gatewayNamespace?: string;
  manifestSha256: string;
  warnings: string[];
  confirmationText: string;
  renderedManifest: string;
  expiresAt: string;
}

export interface DeploymentAcceptedResponse {
  applicationId: string;
  jobId: string;
  operationId: string;
}

export interface ReleaseOperationResponse {
  id: string;
  jobId: string;
  type: string;
  status: string;
  releaseRevision?: number;
  outputSummary?: string;
  errorMessage?: string;
  requestedBy: string;
  requestedAt: string;
  completedAt?: string;
}

export interface ApplicationReleaseResponse {
  id: string;
  revision: number;
  chartVersionId: string;
  valuesRevisionId?: string;
  manifestSha256: string;
  status: string;
  createdBy: string;
  createdAt: string;
}

export interface AiProviderProfileResponse {
  id: string;
  name: string;
  providerType: 'OLLAMA' | 'OPENAI' | 'GOOGLE_GENAI' | 'OPENAI_COMPATIBLE';
  baseUrl: string;
  credentialConfigured: boolean;
  defaultModel: string;
  allowedModels: string[];
  enabled: boolean;
  externalDataTransfer: boolean;
  validationStatus: string;
  lastValidatedAt?: string;
}

export interface AiRoutingResponse {
  purpose: 'ANALYSIS' | 'CHAT' | 'HELM_VALUES';
  primaryProfileId: string;
  model: string;
  fallbackProfileId?: string;
  fallbackModel?: string;
  externalTransferAllowed: boolean;
  maximumContextChars: number;
  maximumOutputTokens: number;
  updatedAt: string;
}

export interface LocalAiModelResponse {
  id: string;
  modelTag: string;
  parameterBillions?: number;
  status: 'PULLING' | 'READY' | 'FAILED' | 'UNSUPPORTED';
  sizeBytes?: number;
  digest?: string;
  updatedAt: string;
}

export interface TenantMemberResponse {
  id: string;
  tenantId: string;
  userId?: string;
  username?: string;
  displayName?: string;
  email?: string;
  role: 'TENANT_ADMIN' | 'CLUSTER_ADMIN' | 'OPERATOR' | 'VIEWER';
  scopeType: 'TENANT' | 'WORKSPACE' | 'CLUSTER' | 'NAMESPACE';
  workspaceId?: string;
  clusterId?: string;
  namespace?: string;
  status: 'INVITED' | 'ACTIVE' | 'SUSPENDED' | 'OFFBOARDED';
  updatedAt: string;
}

export interface OidcGroupMappingResponse {
  id: string;
  issuer: string;
  groupValue: string;
  tenantId: string;
  role: string;
  scopeType: string;
  workspaceId?: string;
  clusterId?: string;
  namespace?: string;
  active: boolean;
  updatedAt: string;
}

export interface TenantMemberOffboardPlanResponse {
  membershipId: string;
  username: string;
  status: string;
  roleBindingsToRemove: number;
  sessionsRevoked: boolean;
  confirmationText: string;
}

export type TenantFeatureKey =
  | 'CORE_OVERVIEW' | 'CLUSTER_OPERATIONS' | 'KUBERNETES_CONSOLE' | 'AI_OPERATIONS'
  | 'APPLICATION_DELIVERY' | 'AI_PROVIDER_ROUTING' | 'AI_PROVIDER_PLATFORM'
  | 'ACCESS_CONTROL' | 'AUDIT' | 'PLATFORM_ADMINISTRATION';

export const api = {
  getRuntimeReadiness: () => request<RuntimeReadinessResponse>('/api/operations/runtime-readiness'),
  listTenants: () => request<TenantResponse[]>('/api/tenants'),
  createTenant: (body: CreateTenancyRequest) => request<TenantResponse>('/api/tenants', {
    method: 'POST', body: JSON.stringify(body),
  }),
  listWorkspaces: (tenantId: string) => request<WorkspaceResponse[]>(`/api/tenants/${encodeURIComponent(tenantId)}/workspaces`),
  createWorkspace: (tenantId: string, body: CreateTenancyRequest) => request<WorkspaceResponse>(`/api/tenants/${encodeURIComponent(tenantId)}/workspaces`, {
    method: 'POST', body: JSON.stringify(body),
  }),
  listClusters: (scope?: { tenantId?: string; workspaceId?: string }) => {
    const query = new URLSearchParams();
    if (scope?.tenantId) query.set('tenantId', scope.tenantId);
    if (scope?.workspaceId) query.set('workspaceId', scope.workspaceId);
    return request<ClusterResponse[]>(`/api/clusters${query.size ? `?${query}` : ''}`);
  },
  getCluster: (clusterId: string) => request<ClusterResponse>(`/api/clusters/${encodeURIComponent(clusterId)}`),
  getClusterReadiness: (clusterId: string, options?: { namespace?: string; targetVersion?: string; refresh?: boolean }) => {
    const query = new URLSearchParams();
    if (options?.namespace) query.set('namespace', options.namespace);
    if (options?.targetVersion) query.set('targetVersion', options.targetVersion);
    if (options?.refresh) query.set('refresh', 'true');
    return request<ClusterReadinessResponse>(
      `/api/clusters/${encodeURIComponent(clusterId)}/readiness${query.size ? `?${query.toString()}` : ''}`
    );
  },
  registerCluster: (body: RegisterClusterRequest) => request<ClusterResponse>('/api/clusters', {
    method: 'POST',
    body: JSON.stringify(body)
  }),
  getClusterCredential: (clusterId: string, reveal = false) => request<ClusterCredentialResponse>(
    `/api/clusters/${encodeURIComponent(clusterId)}/credential?reveal=${String(reveal)}`
  ),
  getClusterSyncSettings: (clusterId: string) => request<ClusterSyncSettingsResponse>(
    `/api/clusters/${encodeURIComponent(clusterId)}/sync-settings`
  ),
  getClusterSyncStatus: (clusterId: string) => request<ClusterSyncStatusResponse>(
    `/api/clusters/${encodeURIComponent(clusterId)}/sync-status`
  ),
  testClusterConnection: (clusterId: string) => request<ClusterConnectionTestResponse>(`/api/clusters/${clusterId}/connection-test`, {
    method: 'POST'
  }),
  syncCluster: (clusterId: string) => request<StartJobResponse>(`/api/clusters/${clusterId}/sync`, {
    method: 'POST'
  }),
  deleteCluster: (clusterId: string) => request<void>(`/api/clusters/${clusterId}`, {
    method: 'DELETE'
  }),
  listNamespaces: (clusterId: string) => request<KubernetesNamespaceResponse[]>(`/api/clusters/${clusterId}/namespaces`),
  listNodes: (clusterId: string) => request<KubernetesNodeResponse[]>(`/api/clusters/${encodeURIComponent(clusterId)}/nodes`),
  getCommandCapabilities: (clusterId: string, namespace?: string) => {
    const query = namespace ? `?namespace=${encodeURIComponent(namespace)}` : '';
    return request<CommandCapabilityResponse>(`/api/clusters/${encodeURIComponent(clusterId)}/command-capabilities${query}`);
  },
  validateCommand: (clusterId: string, body: { namespace?: string; command: string }) => request<CommandValidationResponse>(
    `/api/clusters/${encodeURIComponent(clusterId)}/commands/validate`,
    { method: 'POST', body: JSON.stringify(body) }
  ),
  executeCommand: (clusterId: string, body: { sourceAnalysisId?: string; namespace?: string; command: string; manifest?: string; confirmed: boolean }) =>
    request<CommandExecutionResponse>(`/api/clusters/${encodeURIComponent(clusterId)}/command-executions`, {
      method: 'POST', body: JSON.stringify(body)
    }),
  createTerminalSession: (clusterId: string, body: { sourceAnalysisId?: string; namespace?: string; command: string; confirmed: boolean }) =>
    request<TerminalSessionResponse>(`/api/clusters/${encodeURIComponent(clusterId)}/command-sessions`, {
      method: 'POST', body: JSON.stringify(body)
    }),
  getCommandExecution: (clusterId: string, executionId: string) => request<CommandExecutionResponse>(
    `/api/clusters/${encodeURIComponent(clusterId)}/command-executions/${encodeURIComponent(executionId)}`
  ),
  listCommandExecutions: (clusterId: string, namespace?: string, limit = 30) => {
    const query = new URLSearchParams({ limit: String(limit) });
    if (namespace) query.set('namespace', namespace);
    return request<CommandExecutionResponse[]>(
      `/api/clusters/${encodeURIComponent(clusterId)}/command-executions?${query.toString()}`
    );
  },
  cancelCommandExecution: (clusterId: string, executionId: string) => request<CommandExecutionResponse>(
    `/api/clusters/${encodeURIComponent(clusterId)}/command-executions/${encodeURIComponent(executionId)}/cancel`,
    { method: 'POST' }
  ),
  listCommandFavorites: (clusterId: string) => request<CommandFavoriteResponse[]>(
    `/api/clusters/${encodeURIComponent(clusterId)}/command-favorites`
  ),
  createCommandFavorite: (clusterId: string, body: CommandFavoriteRequest) => request<CommandFavoriteResponse>(
    `/api/clusters/${encodeURIComponent(clusterId)}/command-favorites`,
    { method: 'POST', body: JSON.stringify(body) }
  ),
  updateCommandFavorite: (clusterId: string, favoriteId: string, body: CommandFavoriteRequest) => request<CommandFavoriteResponse>(
    `/api/clusters/${encodeURIComponent(clusterId)}/command-favorites/${encodeURIComponent(favoriteId)}`,
    { method: 'PUT', body: JSON.stringify(body) }
  ),
  deleteCommandFavorite: (clusterId: string, favoriteId: string) => request<void>(
    `/api/clusters/${encodeURIComponent(clusterId)}/command-favorites/${encodeURIComponent(favoriteId)}`,
    { method: 'DELETE' }
  ),
  listClusterResources: (clusterId: string, filters?: { namespace?: string; resourceType?: string }) => {
    const query = new URLSearchParams();
    if (filters?.namespace) {
      query.set('namespace', filters.namespace);
    }
    if (filters?.resourceType) {
      query.set('resourceType', filters.resourceType);
    }
    const suffix = query.toString() ? `?${query.toString()}` : '';
    return request<KubernetesResourceSnapshotResponse[]>(`/api/clusters/${encodeURIComponent(clusterId)}/resources${suffix}`);
  },
  pageClusterResources: (clusterId: string, filters?: { namespace?: string; resourceType?: string; page?: number; size?: number }) => {
    const query = new URLSearchParams();
    if (filters?.namespace) query.set('namespace', filters.namespace);
    if (filters?.resourceType) query.set('resourceType', filters.resourceType);
    query.set('page', String(filters?.page ?? 0));
    query.set('size', String(filters?.size ?? 100));
    return request<ClusterResourcePageResponse>(
      `/api/clusters/${encodeURIComponent(clusterId)}/resources/page?${query.toString()}`
    );
  },
  getClusterResourceManifest: (clusterId: string, resourceType: string, resourceName: string, namespace?: string) => {
    const query = new URLSearchParams();
    if (namespace) {
      query.set('namespace', namespace);
    }
    const suffix = query.toString() ? `?${query.toString()}` : '';
    return request<KubernetesResourceManifestResponse>(
      `/api/clusters/${encodeURIComponent(clusterId)}/resources/${encodeURIComponent(resourceType)}/${encodeURIComponent(resourceName)}/manifest${suffix}`
    );
  },
  getClusterResourceLogTargets: (clusterId: string, resourceType: string, resourceName: string, namespace: string) => {
    const query = new URLSearchParams({ namespace });
    return request<ClusterResourceLogTargetsResponse>(
      `/api/clusters/${encodeURIComponent(clusterId)}/resources/${encodeURIComponent(resourceType)}/${encodeURIComponent(resourceName)}/logs/targets?${query.toString()}`
    );
  },
  getClusterResourceLogs: (
    clusterId: string,
    resourceType: string,
    resourceName: string,
    namespace: string,
    podName: string,
    containerName: string,
    tailLines: number,
    previous: boolean
  ) => {
    const query = new URLSearchParams({
      namespace,
      podName,
      containerName,
      tailLines: String(tailLines),
      previous: String(previous)
    });
    return request<ClusterResourceLogResponse>(
      `/api/clusters/${encodeURIComponent(clusterId)}/resources/${encodeURIComponent(resourceType)}/${encodeURIComponent(resourceName)}/logs?${query.toString()}`
    );
  },
  listClusterEvents: (clusterId: string, namespace?: string) => {
    const query = new URLSearchParams();
    if (namespace) {
      query.set('namespace', namespace);
    }
    const suffix = query.toString() ? `?${query.toString()}` : '';
    return request<KubernetesEventSnapshotResponse[]>(`/api/clusters/${encodeURIComponent(clusterId)}/events${suffix}`);
  },
  cancelJob: (jobId: string) => request<JobResponse>(`/api/jobs/${encodeURIComponent(jobId)}/cancel`, {
    method: 'POST'
  }),
  searchChartCatalog: (tenantId: string, query: string, limit = 20) => request<CatalogPackageResponse[]>(
    `/api/v2/application-delivery/catalog/search?${new URLSearchParams({ tenantId, query, limit: String(limit) })}`
  ),
  importChart: (body: { tenantId: string; repository: string; name: string; version: string }) =>
    request<{ chart: LibraryChartResponse }>('/api/v2/application-delivery/charts/import', {
      method: 'POST', body: JSON.stringify(body),
    }, { timeoutMs: 120_000 }),
  uploadChart: (tenantId: string, file: File, sourceName = 'manual-upload') => {
    const body = new FormData();
    body.set('tenantId', tenantId);
    body.set('sourceName', sourceName);
    body.set('file', file);
    return request<{ chart: LibraryChartResponse }>('/api/v2/application-delivery/charts/upload',
      { method: 'POST', body }, { timeoutMs: 120_000 });
  },
  listLibraryCharts: (tenantId: string) => request<LibraryChartResponse[]>(
    `/api/v2/application-delivery/charts?tenantId=${encodeURIComponent(tenantId)}`
  ),
  listChartSources: (tenantId: string) => request<ChartSourceResponse[]>(
    `/api/v2/application-delivery/sources?tenantId=${encodeURIComponent(tenantId)}`
  ),
  createChartSource: (body: { tenantId: string; sourceType: string; name: string; endpoint: string; credential?: string }) =>
    request<ChartSourceResponse>('/api/v2/application-delivery/sources', { method: 'POST', body: JSON.stringify(body) }),
  deleteChartSource: (tenantId: string, sourceId: string) => request<void>(
    `/api/v2/application-delivery/sources/${encodeURIComponent(sourceId)}?tenantId=${encodeURIComponent(tenantId)}`,
    { method: 'DELETE' }
  ),
  listValuesProfiles: (tenantId: string, chartVersionId: string) => request<ValuesProfileResponse[]>(
    `/api/v2/application-delivery/values-profiles?${new URLSearchParams({ tenantId, chartVersionId })}`
  ),
  createValuesProfile: (body: { tenantId: string; chartVersionId: string; name: string; description?: string }) =>
    request<ValuesProfileResponse>('/api/v2/application-delivery/values-profiles', { method: 'POST', body: JSON.stringify(body) }),
  createValuesRevision: (tenantId: string, profileId: string, valuesYaml: string) => request<ValuesRevisionResponse>(
    `/api/v2/application-delivery/values-profiles/${encodeURIComponent(profileId)}/revisions`,
    { method: 'POST', body: JSON.stringify({ tenantId, valuesYaml }) }
  ),
  suggestValues: (body: { tenantId: string; chartVersionId: string; currentValuesYaml: string; instruction: string }) =>
    request<{ valuesYaml: string }>('/api/v2/application-delivery/values-suggestions', {
      method: 'POST', body: JSON.stringify(body),
    }, { timeoutMs: 180_000 }),
  createDeploymentPlan: (body: {
    tenantId: string; applicationId?: string; clusterId: string; chartVersionId: string; valuesRevisionId?: string;
    namespace: string; releaseName: string; createNamespace: boolean; exposureType: string; hostname?: string;
    exposurePath?: string; backendServiceName?: string; backendServicePort?: number;
    gatewayName?: string; gatewayNamespace?: string;
  }) => request<DeploymentPlanResponse>('/api/v2/application-delivery/deployment-plans', {
    method: 'POST', body: JSON.stringify(body),
  }, { timeoutMs: 60_000 }),
  executeDeploymentPlan: (planId: string, tenantId: string, confirmationText: string) =>
    request<DeploymentAcceptedResponse>(`/api/v2/application-delivery/deployment-plans/${encodeURIComponent(planId)}/execute`, {
      method: 'POST', body: JSON.stringify({ tenantId, confirmationText }),
    }),
  listReleaseOperations: (tenantId: string, applicationId: string) => request<ReleaseOperationResponse[]>(
    `/api/v2/application-delivery/applications/${encodeURIComponent(applicationId)}/operations?tenantId=${encodeURIComponent(tenantId)}`
  ),
  listApplicationReleases: (tenantId: string, applicationId: string) => request<ApplicationReleaseResponse[]>(
    `/api/v2/application-delivery/applications/${encodeURIComponent(applicationId)}/releases?tenantId=${encodeURIComponent(tenantId)}`
  ),
  getRollbackConfirmation: (tenantId: string, applicationId: string, revision: number) => request<{ confirmationText: string; impactSummary: string }>(
    `/api/v2/application-delivery/applications/${encodeURIComponent(applicationId)}/rollback-confirmation?${new URLSearchParams({ tenantId, revision: String(revision) })}`
  ),
  rollbackHelmApplication: (tenantId: string, applicationId: string, revision: number, confirmationText: string) =>
    request<DeploymentAcceptedResponse>(`/api/v2/application-delivery/applications/${encodeURIComponent(applicationId)}/rollback`, {
      method: 'POST', body: JSON.stringify({ tenantId, revision, confirmationText }),
    }),
  getApplicationRuntime: (tenantId: string, applicationId: string) => request<ApplicationRuntimeResponse>(
    `/api/v2/application-delivery/applications/${encodeURIComponent(applicationId)}/runtime?tenantId=${encodeURIComponent(tenantId)}`,
    undefined, { timeoutMs: 20_000 }
  ),
  getUninstallConfirmation: (tenantId: string, applicationId: string) => request<{ confirmationText: string; impactSummary: string }>(
    `/api/v2/application-delivery/applications/${encodeURIComponent(applicationId)}/uninstall-confirmation?tenantId=${encodeURIComponent(tenantId)}`
  ),
  uninstallHelmApplication: (tenantId: string, applicationId: string, confirmationText: string) => request<DeploymentAcceptedResponse>(
    `/api/v2/application-delivery/applications/${encodeURIComponent(applicationId)}/uninstall`,
    { method: 'POST', body: JSON.stringify({ tenantId, confirmationText }) }
  ),
  listApplications: () => request<ApplicationResponse[]>('/api/applications'),
  listTenantApplications: (tenantId: string) => request<ApplicationResponse[]>(
    `/api/v2/application-delivery/applications?tenantId=${encodeURIComponent(tenantId)}`
  ),
  listAiProviderProfiles: (tenantId: string) => request<AiProviderProfileResponse[]>(
    `/api/v2/ai-configuration/providers?tenantId=${encodeURIComponent(tenantId)}`
  ),
  createAiProviderProfile: (body: {
    tenantId: string; name: string; providerType: string; baseUrl?: string; apiKey?: string;
    defaultModel: string; allowedModels: string[]; externalDataTransfer: boolean;
  }) => request<AiProviderProfileResponse>('/api/v2/ai-configuration/providers', {
    method: 'POST', body: JSON.stringify(body),
  }),
  validateAiProviderProfile: (tenantId: string, profileId: string) => request<{ valid: boolean; message: string; checkedAt: string }>(
    `/api/v2/ai-configuration/providers/${encodeURIComponent(profileId)}/validate?tenantId=${encodeURIComponent(tenantId)}`,
    { method: 'POST' }, { timeoutMs: 20_000 }
  ),
  listLocalAiModels: (tenantId: string, profileId: string) => request<LocalAiModelResponse[]>(
    `/api/v2/ai-configuration/providers/${encodeURIComponent(profileId)}/models?tenantId=${encodeURIComponent(tenantId)}`
  ),
  refreshLocalAiModels: (tenantId: string, profileId: string) => request<LocalAiModelResponse[]>(
    `/api/v2/ai-configuration/providers/${encodeURIComponent(profileId)}/models/refresh?tenantId=${encodeURIComponent(tenantId)}`,
    { method: 'POST' }, { timeoutMs: 20_000 }
  ),
  pullLocalAiModel: (tenantId: string, profileId: string, modelTag: string) => request<{ jobId: string; status: string }>(
    `/api/v2/ai-configuration/providers/${encodeURIComponent(profileId)}/models/pull`,
    { method: 'POST', body: JSON.stringify({ tenantId, modelTag }) }
  ),
  listAiRouting: (tenantId: string) => request<AiRoutingResponse[]>(
    `/api/v2/ai-configuration/routing?tenantId=${encodeURIComponent(tenantId)}`
  ),
  saveAiRouting: (purpose: string, body: {
    tenantId: string; primaryProfileId: string; model: string; fallbackProfileId?: string;
    fallbackModel?: string; externalTransferAllowed: boolean; maximumContextChars: number; maximumOutputTokens: number;
  }) => request<AiRoutingResponse>(`/api/v2/ai-configuration/routing/${encodeURIComponent(purpose)}`, {
    method: 'PUT', body: JSON.stringify(body),
  }),
  listTenantMembers: (tenantId: string) => request<TenantMemberResponse[]>(`/api/tenants/${encodeURIComponent(tenantId)}/members`),
  inviteTenantMember: (tenantId: string, body: {
    issuer: string; email?: string; subject?: string; role: string; scopeType: string;
    workspaceId?: string; clusterId?: string; namespace?: string;
  }) => request<TenantMemberResponse>(`/api/tenants/${encodeURIComponent(tenantId)}/members`, {
    method: 'POST', body: JSON.stringify(body),
  }),
  setTenantMemberStatus: (tenantId: string, membershipId: string, status: 'ACTIVE' | 'SUSPENDED') =>
    request<TenantMemberResponse>(`/api/tenants/${encodeURIComponent(tenantId)}/members/${encodeURIComponent(membershipId)}`, {
      method: 'PATCH', body: JSON.stringify({ status }),
    }),
  getTenantMemberOffboardPlan: (tenantId: string, membershipId: string) => request<TenantMemberOffboardPlanResponse>(
    `/api/tenants/${encodeURIComponent(tenantId)}/members/${encodeURIComponent(membershipId)}/offboard-plan`,
    { method: 'POST' }
  ),
  offboardTenantMember: (tenantId: string, membershipId: string, confirmationText: string) => request<TenantMemberResponse>(
    `/api/tenants/${encodeURIComponent(tenantId)}/members/${encodeURIComponent(membershipId)}/offboard`,
    { method: 'POST', body: JSON.stringify({ confirmationText }) }
  ),
  getTenantFeatures: (tenantId: string) => request<Record<TenantFeatureKey, boolean>>(
    `/api/tenants/${encodeURIComponent(tenantId)}/features`
  ),
  updateTenantFeature: (tenantId: string, featureKey: TenantFeatureKey, enabled: boolean) =>
    request<Record<TenantFeatureKey, boolean>>(`/api/tenants/${encodeURIComponent(tenantId)}/features`, {
      method: 'PATCH', body: JSON.stringify({ featureKey, enabled }),
    }),
  listOidcGroupMappings: (tenantId: string) => request<OidcGroupMappingResponse[]>(
    `/api/tenants/${encodeURIComponent(tenantId)}/oidc-group-mappings`
  ),
  createOidcGroupMapping: (tenantId: string, body: {
    issuer: string; groupValue: string; role: string; scopeType: string;
    workspaceId?: string; clusterId?: string; namespace?: string;
  }) => request<OidcGroupMappingResponse>(`/api/tenants/${encodeURIComponent(tenantId)}/oidc-group-mappings`, {
    method: 'POST', body: JSON.stringify(body),
  }),
  setOidcGroupMappingActive: (tenantId: string, mappingId: string, active: boolean) =>
    request<OidcGroupMappingResponse>(
      `/api/tenants/${encodeURIComponent(tenantId)}/oidc-group-mappings/${encodeURIComponent(mappingId)}`,
      { method: 'PATCH', body: JSON.stringify({ active }) }
    ),
  deleteOidcGroupMapping: (tenantId: string, mappingId: string) => request<void>(
    `/api/tenants/${encodeURIComponent(tenantId)}/oidc-group-mappings/${encodeURIComponent(mappingId)}`,
    { method: 'DELETE' }
  ),
  getApplication: (applicationId: string) => request<ApplicationResponse>(
    `/api/applications/${encodeURIComponent(applicationId)}`
  ),
  getApplicationStatus: (applicationId: string) => request<ApplicationStatusResponse>(
    `/api/applications/${encodeURIComponent(applicationId)}/status`
  ),
  syncApplication: (applicationId: string) => request<StartJobResponse>(
    `/api/applications/${encodeURIComponent(applicationId)}/sync`,
    { method: 'POST' }
  ),
  restartApplication: (applicationId: string) => request<StartJobResponse>(
    `/api/applications/${encodeURIComponent(applicationId)}/restart`,
    { method: 'POST' }
  ),
  previewApplicationRollback: (applicationId: string, targetRevision?: string) => {
    const query = targetRevision ? `?targetRevision=${encodeURIComponent(targetRevision)}` : '';
    return request<ApplicationRollbackPreviewResponse>(
      `/api/applications/${encodeURIComponent(applicationId)}/rollback/preview${query}`
    );
  },
  rollbackApplication: (applicationId: string, targetRevision: string, confirmText: string) => request<StartJobResponse>(
    `/api/applications/${encodeURIComponent(applicationId)}/rollback`,
    {
      method: 'POST',
      body: JSON.stringify({ targetRevision: Number(targetRevision), confirmText })
    }
  ),
  getJob: (jobId: string) => request<JobResponse>(`/api/jobs/${encodeURIComponent(jobId)}`),
  listAnalysisHistory: (filters?: { clusterId?: string; namespace?: string; applicationId?: string }) => {
    const query = new URLSearchParams();
    if (filters?.clusterId) {
      query.set('clusterId', filters.clusterId);
    }
    if (filters?.namespace) {
      query.set('namespace', filters.namespace);
    }
    if (filters?.applicationId) {
      query.set('applicationId', filters.applicationId);
    }
    const suffix = query.toString() ? `?${query.toString()}` : '';
    return request<AnalysisResponse[]>(`/api/analysis/history${suffix}`);
  },
  getAnalysis: (analysisId: string) => request<AnalysisResponse>(`/api/analysis/${encodeURIComponent(analysisId)}`),
  deleteAnalysis: (analysisId: string) => request<void>(`/api/analysis/${encodeURIComponent(analysisId)}`, {
    method: 'DELETE'
  }),
  listAnalysisCommandExecutions: (analysisId: string) => request<AnalysisCommandExecutionResponse[]>(
    `/api/analysis/${encodeURIComponent(analysisId)}/commands`
  ),
  previewAnalysisCommand: (analysisId: string, command: string) => request<AnalysisCommandPreviewResponse>(
    `/api/analysis/${encodeURIComponent(analysisId)}/commands/preview`,
    {
      method: 'POST',
      body: JSON.stringify({ command })
    }
  ),
  executeAnalysisCommand: (analysisId: string, command: string, confirmText?: string) => request<AnalysisCommandExecutionResponse>(
    `/api/analysis/${encodeURIComponent(analysisId)}/commands`,
    {
      method: 'POST',
      body: JSON.stringify({ command, confirmText })
    }
  ),
  listAnalysisWorkflowStates: (analysisId: string) => request<AnalysisWorkflowStateResponse[]>(
    `/api/analysis/${encodeURIComponent(analysisId)}/workflow`
  ),
  updateAnalysisWorkflowState: (analysisId: string, issueGroupId: string, status: WorkflowStatusValue, note?: string) =>
    request<AnalysisWorkflowStateResponse>(
      `/api/analysis/${encodeURIComponent(analysisId)}/workflow/${encodeURIComponent(issueGroupId)}`,
      {
        method: 'POST',
        body: JSON.stringify({ status, note })
      }
    ),
  retryAnalysis: (analysisId: string) => request<StartAnalysisJobResponse>(`/api/analysis/${encodeURIComponent(analysisId)}/retry`, {
    method: 'POST'
  }),
  getAnalysisByJobId: (jobId: string) => request<AnalysisResponse>(`/api/analysis/jobs/${encodeURIComponent(jobId)}/result`),
  getNamespaceDiagnostics: (clusterId: string, namespace: string) => request<NamespaceDiagnosticsResponse>(
    `/api/analysis/namespaces/${encodeURIComponent(namespace)}/diagnostics?clusterId=${encodeURIComponent(clusterId)}`
  ),
  getPodLogs: (clusterId: string, namespace: string, podName: string, tailLines: number, containerName?: string) => {
    const query = new URLSearchParams({
      clusterId,
      tailLines: String(tailLines)
    });
    if (containerName) {
      query.set('containerName', containerName);
    }
    return request<PodLogsResponse>(
      `/api/analysis/namespaces/${encodeURIComponent(namespace)}/pods/${encodeURIComponent(podName)}/logs?${query.toString()}`
    );
  },
  getResourceLogs: (
    clusterId: string,
    namespace: string,
    resourceType: string,
    resourceName: string,
    tailLines: number,
    containerName?: string
  ) => {
    const query = new URLSearchParams({
      clusterId,
      tailLines: String(tailLines)
    });
    if (containerName) {
      query.set('containerName', containerName);
    }
    return request<PodLogsResponse>(
      `/api/analysis/namespaces/${encodeURIComponent(namespace)}/resources/${encodeURIComponent(resourceType)}/${encodeURIComponent(resourceName)}/logs?${query.toString()}`
    );
  },
  analyzeNamespace: (clusterId: string, namespace: string) => request<AnalysisResponse>(
    `/api/analysis/namespaces/${encodeURIComponent(namespace)}?clusterId=${encodeURIComponent(clusterId)}`,
    { method: 'POST' }
  ),
  startNamespaceAnalysis: (clusterId: string, namespace: string) => request<StartAnalysisJobResponse>(
    `/api/analysis/namespaces/${encodeURIComponent(namespace)}/jobs?clusterId=${encodeURIComponent(clusterId)}`,
    { method: 'POST' }
  ),
  analyzeCluster: (clusterId: string) => request<AnalysisResponse>(`/api/analysis/clusters/${encodeURIComponent(clusterId)}`, {
    method: 'POST'
  }),
  startClusterAnalysis: (clusterId: string) => request<StartAnalysisJobResponse>(
    `/api/analysis/clusters/${encodeURIComponent(clusterId)}/jobs`,
    { method: 'POST' }
  ),
  analyzeApplication: (applicationId: string) => request<AnalysisResponse>(`/api/analysis/applications/${applicationId}`, {
    method: 'POST'
  }),
  startApplicationAnalysis: (applicationId: string) => request<StartAnalysisJobResponse>(
    `/api/analysis/applications/${encodeURIComponent(applicationId)}/jobs`,
    { method: 'POST' }
  ),
  getOperationsOverview: () => request<OperationsOverviewResponse>('/api/operations/overview'),
  reconcileOperations: () => request<OperationsOverviewResponse>('/api/operations/reconcile', { method: 'POST' }),
  listIncidents: (filters?: { clusterId?: string; namespace?: string; state?: string; severity?: string }) => {
    const query = new URLSearchParams();
    if (filters?.clusterId) query.set('clusterId', filters.clusterId);
    if (filters?.namespace) query.set('namespace', filters.namespace);
    if (filters?.state) query.set('state', filters.state);
    if (filters?.severity) query.set('severity', filters.severity);
    const suffix = query.toString() ? `?${query.toString()}` : '';
    return request<IncidentResponse[]>(`/api/incidents${suffix}`);
  },
  getIncident: (incidentId: string) => request<IncidentDetailResponse>(`/api/incidents/${encodeURIComponent(incidentId)}`),
  updateIncidentState: (incidentId: string, state: IncidentState, note?: string) => request<IncidentResponse>(
    `/api/incidents/${encodeURIComponent(incidentId)}/state`,
    { method: 'PATCH', body: JSON.stringify({ state, note }) }
  ),
  listNotifications: (unreadOnly = false) => request<OperationNotificationResponse[]>(
    `/api/notifications?unreadOnly=${String(unreadOnly)}`
  ),
  getUnreadNotificationCount: () => request<{ count: number }>('/api/notifications/unread-count'),
  readNotification: (notificationId: string) => request<void>(`/api/notifications/${encodeURIComponent(notificationId)}/read`, {
    method: 'PATCH'
  }),
  readAllNotifications: () => request<void>('/api/notifications/read-all', { method: 'POST' }),
  listPolicies: () => request<PolicyDefinitionResponse[]>('/api/policies'),
  updatePolicy: (policyId: string, enabled: boolean, severity: string) => request<PolicyDefinitionResponse>(
    `/api/policies/${encodeURIComponent(policyId)}`,
    { method: 'PUT', body: JSON.stringify({ enabled, severity }) }
  ),
  evaluatePolicies: (clusterId: string) => request<PolicyEvaluationResponse[]>(
    `/api/policies/evaluate?clusterId=${encodeURIComponent(clusterId)}`,
    { method: 'POST' }
  ),
  listPolicyEvaluations: (filters?: { clusterId?: string; namespace?: string; result?: string }) => {
    const query = new URLSearchParams();
    if (filters?.clusterId) query.set('clusterId', filters.clusterId);
    if (filters?.namespace) query.set('namespace', filters.namespace);
    if (filters?.result) query.set('result', filters.result);
    const suffix = query.toString() ? `?${query.toString()}` : '';
    return request<PolicyEvaluationResponse[]>(`/api/policies/evaluations${suffix}`);
  },
  listResourceChanges: (filters?: { clusterId?: string; namespace?: string }) => {
    const query = new URLSearchParams();
    if (filters?.clusterId) query.set('clusterId', filters.clusterId);
    if (filters?.namespace) query.set('namespace', filters.namespace);
    const suffix = query.toString() ? `?${query.toString()}` : '';
    return request<ResourceChangeResponse[]>(`/api/changes${suffix}`);
  },
  listRunbooks: (filters?: { signal?: string; category?: string }) => {
    const query = new URLSearchParams();
    if (filters?.signal) query.set('signal', filters.signal);
    if (filters?.category) query.set('category', filters.category);
    const suffix = query.toString() ? `?${query.toString()}` : '';
    return request<RunbookTemplateResponse[]>(`/api/runbooks${suffix}`);
  },
  searchOperatorWorkspace: (q: string, filters?: { clusterId?: string; namespace?: string; types?: string[]; limit?: number }) => {
    const query = new URLSearchParams({ q });
    if (filters?.clusterId) query.set('clusterId', filters.clusterId);
    if (filters?.namespace) query.set('namespace', filters.namespace);
    filters?.types?.forEach((type) => query.append('types', type));
    query.set('limit', String(filters?.limit ?? 30));
    return request<OperatorSearchResultResponse[]>(`/api/search?${query.toString()}`);
  },
  getResourceContext: (clusterId: string, namespace: string | undefined, kind: string, name: string) => {
    const query = namespace ? `?namespace=${encodeURIComponent(namespace)}` : '';
    return request<ResourceContextResponse>(`/api/clusters/${encodeURIComponent(clusterId)}/resources/${encodeURIComponent(kind)}/${encodeURIComponent(name)}/context${query}`);
  },
  getIncidentCollaboration: (incidentId: string) => request<IncidentCollaborationResponse>(`/api/incidents/${encodeURIComponent(incidentId)}/collaboration`),
  updateIncidentCollaboration: (incidentId: string, body: { assignee?: string; tags: string[]; acknowledgeDueAt?: string; resolveDueAt?: string }) =>
    request<IncidentCollaborationResponse>(`/api/incidents/${encodeURIComponent(incidentId)}/collaboration`, { method: 'PATCH', body: JSON.stringify(body) }),
  addIncidentComment: (incidentId: string, comment: string) => request<void>(`/api/incidents/${encodeURIComponent(incidentId)}/comments`, { method: 'POST', body: JSON.stringify({ comment }) }),
  createManualIncident: (body: { clusterId: string; namespace?: string; resourceKind?: string; resourceName?: string; severity: string; title: string; summary?: string; nextAction?: string }) =>
    request<IncidentResponse>('/api/incidents/manual', { method: 'POST', body: JSON.stringify(body) }),
  linkIncident: (incidentId: string, relatedIncidentId: string, relationType: string) => request<IncidentCollaborationResponse>(`/api/incidents/${encodeURIComponent(incidentId)}/links`, { method: 'POST', body: JSON.stringify({ relatedIncidentId, relationType }) }),
  unlinkIncident: (incidentId: string, relatedIncidentId: string) => request<void>(`/api/incidents/${encodeURIComponent(incidentId)}/links/${encodeURIComponent(relatedIncidentId)}`, { method: 'DELETE' }),
  mergeIncidents: (incidentId: string, sourceIncidentIds: string[], note?: string) => request<IncidentCollaborationResponse>(`/api/incidents/${encodeURIComponent(incidentId)}/merge`, { method: 'POST', body: JSON.stringify({ sourceIncidentIds, note }) }),
  splitIncident: (incidentId: string, evidenceIds: string[], title: string, severity: string) => request<IncidentResponse>(`/api/incidents/${encodeURIComponent(incidentId)}/split`, { method: 'POST', body: JSON.stringify({ evidenceIds, title, severity }) }),
  listRunbookLibrary: () => request<ManagedRunbookResponse[]>('/api/runbooks/library'),
  createCustomRunbook: (body: RunbookWriteRequest) => request<ManagedRunbookResponse>('/api/runbooks/custom', { method: 'POST', body: JSON.stringify(body) }),
  updateCustomRunbook: (id: string, body: RunbookWriteRequest) => request<ManagedRunbookResponse>(`/api/runbooks/custom/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify(body) }),
  duplicateRunbook: (id: string) => request<ManagedRunbookResponse>(`/api/runbooks/${encodeURIComponent(id)}/duplicate`, { method: 'POST' }),
  setCustomRunbookEnabled: (id: string, enabled: boolean) => request<ManagedRunbookResponse>(`/api/runbooks/custom/${encodeURIComponent(id)}/enabled`, { method: 'PATCH', body: JSON.stringify({ enabled }) }),
  listCustomRunbookVersions: (id: string) => request<RunbookVersionResponse[]>(`/api/runbooks/custom/${encodeURIComponent(id)}/versions`),
  restoreCustomRunbookVersion: (id: string, version: number) => request<ManagedRunbookResponse>(`/api/runbooks/custom/${encodeURIComponent(id)}/versions/${version}/restore`, { method: 'POST' }),
  deleteCustomRunbook: (id: string) => request<void>(`/api/runbooks/custom/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  getAnalysisFeedback: (analysisId: string) => request<AnalysisFeedbackResponse | null>(
    `/api/analysis/${encodeURIComponent(analysisId)}/feedback`
  ),
  saveAnalysisFeedback: (
    analysisId: string,
    body: Pick<AnalysisFeedbackResponse, 'accuracy' | 'outcome' | 'dangerousSuggestion' | 'comment'
      | 'actualRootCause' | 'actualResolution' | 'validatedResourceKind' | 'validatedResourceName'
      | 'confidenceExpectation'>
  ) => request<AnalysisFeedbackResponse>(`/api/analysis/${encodeURIComponent(analysisId)}/feedback`, {
    method: 'PUT',
    body: JSON.stringify(body)
  }),
  getAiQuality: () => request<AiQualitySummaryResponse>('/api/operations/ai-quality'),
  getAiCalibration: () => request<AiCalibrationSummaryResponse>('/api/operations/ai-calibration'),
  getAiTrustSnapshot: () => request<AiTrustSnapshotResponse>('/api/operations/ai-trust'),
  listProductionEvidenceRuns: (limit = 20) => request<ProductionEvidenceRunResponse[]>(
    `/api/operations/production-evidence/runs?limit=${limit}`
  ),
  getOperationalTelemetry: () => request<OperationalTelemetryResponse>('/api/operations/telemetry'),
  getOperationsScorecard: () => request<OperationsScorecardResponse>('/api/operations/scorecard'),
  getFleetQueue: () => request<FleetQueueResponse>('/api/operations/fleet-queue'),
  getShiftBriefing: () => request<ShiftBriefingResponse>('/api/operations/shift-briefing'),
  listValidationScenarios: () => request<ValidationScenarioResponse[]>('/api/operations/validation-lab/scenarios'),
  runValidationLab: () => request<ValidationLabRunResponse>('/api/operations/validation-lab/runs', { method: 'POST' }),
  getLiveValidationPolicy: () => request<LiveValidationPolicyResponse>('/api/operations/validation-lab/live/policy'),
  previewLiveValidation: (body: { clusterId: string; scenarioId: string; ttlSeconds: number }) =>
    request<LiveValidationPreviewResponse>('/api/operations/validation-lab/live/preview', {
      method: 'POST',
      body: JSON.stringify(body)
    }),
  runLiveValidation: (body: {
    clusterId: string;
    scenarioId: string;
    ttlSeconds: number;
    confirmation: string;
  }) => request<LiveValidationRunResponse>('/api/operations/validation-lab/live/runs', {
    method: 'POST',
    body: JSON.stringify(body)
  }),
  cleanupLiveValidation: (runId: string) => request<LiveValidationRunResponse>(
    `/api/operations/validation-lab/live/runs/${encodeURIComponent(runId)}`,
    { method: 'DELETE' }
  ),
  runAnalysisBenchmark: () => request<AnalysisBenchmarkResponse>(
    '/api/operations/validation-lab/benchmarks', { method: 'POST' }
  ),
  getLatestAnalysisBenchmark: () => request<AnalysisBenchmarkResponse | undefined>(
    '/api/operations/validation-lab/benchmarks/latest'
  ),
  getRemediationLearning: (incidentId: string) => request<RemediationLearningResponse>(
    `/api/operations/incidents/${encodeURIComponent(incidentId)}/remediation-learning`
  ),
  getReliabilityTrend: (filters?: { clusterId?: string; namespace?: string; days?: number }) => {
    const query = new URLSearchParams();
    if (filters?.clusterId) query.set('clusterId', filters.clusterId);
    if (filters?.namespace) query.set('namespace', filters.namespace);
    if (filters?.days) query.set('days', String(filters.days));
    const suffix = query.toString() ? `?${query.toString()}` : '';
    return request<ReliabilityTrendResponse>(`/api/operations/reliability-trend${suffix}`);
  },
  getTriageQueue: (filters?: { clusterId?: string; namespace?: string; state?: string; limit?: number }) => {
    const query = new URLSearchParams();
    if (filters?.clusterId) query.set('clusterId', filters.clusterId);
    if (filters?.namespace) query.set('namespace', filters.namespace);
    if (filters?.state) query.set('state', filters.state);
    if (filters?.limit) query.set('limit', String(filters.limit));
    const suffix = query.toString() ? `?${query.toString()}` : '';
    return request<TriageQueueResponse>(`/api/operations/triage${suffix}`);
  },
  updateTriageState: (groupId: string, state: 'OPEN' | 'ACKNOWLEDGED' | 'SUPPRESSED') =>
    request<WatchSignalGroupResponse>(`/api/operations/triage/${encodeURIComponent(groupId)}/state`, {
      method: 'PATCH',
      body: JSON.stringify({ state })
    }),
  listAuditLogs: (filters?: { actor?: string; action?: string; targetType?: string; requestId?: string }) => {
    const query = new URLSearchParams();
    if (filters?.actor) query.set('actor', filters.actor);
    if (filters?.action) query.set('action', filters.action);
    if (filters?.targetType) query.set('targetType', filters.targetType);
    if (filters?.requestId) query.set('requestId', filters.requestId);
    const suffix = query.toString() ? `?${query.toString()}` : '';
    return request<AuditLogResponse[]>(`/api/audit-logs${suffix}`);
  },
  getOperationSettings: () => request<OperationSettingsResponse>('/api/settings/operations'),
  updateOperationSettings: (body: OperationSettingsResponse) => request<OperationSettingsResponse>('/api/settings/operations', {
    method: 'PUT',
    body: JSON.stringify(body)
  }),
  previewOperationCleanup: () => request<CleanupPreviewResponse>('/api/settings/operations/cleanup-preview', { method: 'POST' }),
  executeOperationCleanup: () => request<CleanupPreviewResponse>('/api/settings/operations/cleanup', { method: 'POST' }),
  listWatchStatuses: () => request<WatchRuntimeStatusResponse[]>('/api/operations/watch/status'),
  listWatchSignals: (filters?: { clusterId?: string; namespace?: string; limit?: number }) => {
    const query = new URLSearchParams();
    if (filters?.clusterId) query.set('clusterId', filters.clusterId);
    if (filters?.namespace) query.set('namespace', filters.namespace);
    if (filters?.limit) query.set('limit', String(filters.limit));
    const suffix = query.toString() ? `?${query.toString()}` : '';
    return request<WatchSignalResponse[]>(`/api/operations/watch/signals${suffix}`);
  },
  restartWatch: (clusterId: string) => request<WatchRuntimeStatusResponse>(
    `/api/operations/watch/clusters/${encodeURIComponent(clusterId)}/restart`, { method: 'POST' }
  ),
  pauseWatch: (clusterId: string) => request<WatchRuntimeStatusResponse>(
    `/api/operations/watch/clusters/${encodeURIComponent(clusterId)}/pause`, { method: 'POST' }
  ),
  resumeWatch: (clusterId: string) => request<WatchRuntimeStatusResponse>(
    `/api/operations/watch/clusters/${encodeURIComponent(clusterId)}/resume`, { method: 'POST' }
  ),
  listWatchContinuity: () => request<WatchContinuityResponse[]>('/api/operations/watch/continuity'),
  listNoisePolicies: () => request<SignalNoisePolicyResponse[]>('/api/operations/noise-policies'),
  createNoisePolicy: (body: Omit<SignalNoisePolicyResponse, 'id' | 'updatedBy' | 'updatedAt'>) =>
    request<SignalNoisePolicyResponse>('/api/operations/noise-policies', {
      method: 'POST',
      body: JSON.stringify(body)
    }),
  updateNoisePolicy: (policyId: string, body: Omit<SignalNoisePolicyResponse, 'id' | 'updatedBy' | 'updatedAt'>) =>
    request<SignalNoisePolicyResponse>(`/api/operations/noise-policies/${encodeURIComponent(policyId)}`, {
      method: 'PUT',
      body: JSON.stringify(body)
    }),
  deleteNoisePolicy: (policyId: string) =>
    request<void>(`/api/operations/noise-policies/${encodeURIComponent(policyId)}`, { method: 'DELETE' }),
  listRemediationObservations: (incidentId: string) => request<RemediationObservationResponse[]>(
    `/api/incidents/${encodeURIComponent(incidentId)}/remediation-observations`
  ),
  startRemediationObservation: (incidentId: string, observationSeconds = 300, analysisId?: string, commandExecutionId?: string) =>
    request<RemediationObservationResponse>(
      `/api/incidents/${encodeURIComponent(incidentId)}/remediation-observations`,
      { method: 'POST', body: JSON.stringify({ observationSeconds, analysisId, commandExecutionId }) }
    ),
  evaluateRemediationObservation: (observationId: string) => request<RemediationObservationResponse>(
    `/api/remediation-observations/${encodeURIComponent(observationId)}/evaluate`, { method: 'POST' }
  ),
  cancelRemediationObservation: (observationId: string) => request<RemediationObservationResponse>(
    `/api/remediation-observations/${encodeURIComponent(observationId)}/cancel`, { method: 'POST' }
  ),
  evaluateAiReleaseGate: (body: {
    candidateVersion: string;
    baselineVersion: string;
    minimumRegressionScore: number;
    minimumGroundTruthSamples: number;
    minimumVerifiedAccuracy: number;
  }) => request<AiReleaseGateResponse>('/api/operations/ai-release-gates', {
    method: 'POST',
    body: JSON.stringify(body)
  }),
  listAiReleaseGates: () => request<AiReleaseGateResponse[]>('/api/operations/ai-release-gates'),
  generateIncidentPostmortem: (incidentId: string) => request<IncidentPostmortemResponse>(
    `/api/incidents/${encodeURIComponent(incidentId)}/postmortem`, { method: 'POST' }
  ),
  getIncidentPostmortem: (incidentId: string) => request<IncidentPostmortemResponse>(
    `/api/incidents/${encodeURIComponent(incidentId)}/postmortem`
  ),
  listRegressionRuns: () => request<RegressionRunResponse[]>('/api/analysis-regression/runs'),
  getRegressionRun: (runId: string) => request<RegressionRunResponse>(
    `/api/analysis-regression/runs/${encodeURIComponent(runId)}`
  ),
  runAnalysisRegression: () => request<RegressionRunResponse>('/api/analysis-regression/runs', { method: 'POST' }),
  listConversations: (archived = false) => request<AiChatConversationResponse[]>(
    `/api/ai-chat/conversations?archived=${archived}`
  ),
  createConversation: (body: CreateConversationRequest) => request<AiChatConversationResponse>('/api/ai-chat/conversations', {
    method: 'POST',
    body: JSON.stringify(body)
  }),
  listMessages: (conversationId: string) => request<AiChatMessageResponse[]>(`/api/ai-chat/conversations/${conversationId}/messages`),
  listContextReferences: (messageId: string) => request<AiChatContextReferenceResponse[]>(
    `/api/ai-chat/messages/${messageId}/context-references`
  ),
  listConversationContextReferences: (conversationId: string) => request<AiChatContextReferenceResponse[]>(
    `/api/ai-chat/conversations/${conversationId}/context-references`
  ),
  updateConversation: (conversationId: string, body: { title?: string; favorite?: boolean; archived?: boolean }) =>
    request<AiChatConversationResponse>(`/api/ai-chat/conversations/${conversationId}`, {
      method: 'PATCH',
      body: JSON.stringify(body)
    }),
  deleteConversation: (conversationId: string) => request<void>(
    `/api/ai-chat/conversations/${conversationId}`, { method: 'DELETE' }
  ),
  sendMessage: (conversationId: string, body: SendMessageRequest) => request<AiChatSendMessageResponse>(`/api/ai-chat/conversations/${conversationId}/messages`, {
    method: 'POST',
    body: JSON.stringify(body)
  })
};

export class AiChatStreamError extends Error {
  constructor(message: string, readonly partial: boolean) {
    super(message);
    this.name = 'AiChatStreamError';
  }
}

export class ResourceLogStreamError extends Error {
  constructor(message: string, readonly partial: boolean) {
    super(message);
    this.name = 'ResourceLogStreamError';
  }
}

export async function streamClusterResourceLogs(
  request: ClusterResourceLogStreamRequest,
  onLine: (line: ClusterResourceLogLine) => void,
  signal?: AbortSignal
): Promise<ClusterResourceLogStreamResult> {
  const query = new URLSearchParams({
    namespace: request.namespace,
    podName: request.podName,
    containerName: request.containerName,
    tailLines: String(request.tailLines)
  });
  const response = await fetch(
    `${API_BASE_URL}/api/clusters/${encodeURIComponent(request.clusterId)}`
      + `/resources/${encodeURIComponent(request.resourceType)}/${encodeURIComponent(request.resourceName)}`
      + `/logs/stream?${query.toString()}`,
    {
      method: 'GET',
      headers: { Accept: 'text/event-stream', 'Accept-Language': getLocale() },
      credentials: 'same-origin',
      signal
    }
  );
  if (!response.ok || !response.body) {
    throw new ApiError(response.status, await parseError(response));
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let result: ClusterResourceLogStreamResult | null = null;
  let receivedLine = false;

  const consume = (rawEvent: string) => {
    const event = parseSseEvent(rawEvent);
    if (event.type === 'heartbeat' || event.type === 'meta' || !event.data) return;
    if (event.type === 'error') throw new ResourceLogStreamError(event.data, receivedLine);
    if (event.type === 'log') {
      const line = JSON.parse(event.data) as ClusterResourceLogLine;
      receivedLine = true;
      onLine(line);
      return;
    }
    if (event.type === 'done') {
      result = JSON.parse(event.data) as ClusterResourceLogStreamResult;
    }
  };

  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const events = buffer.split('\n\n');
      buffer = events.pop() ?? '';
      for (const event of events) consume(event);
    }
    if (buffer.trim()) consume(buffer);
  } catch (error) {
    if (error instanceof ResourceLogStreamError || signal?.aborted) throw error;
    throw new ResourceLogStreamError(error instanceof Error ? error.message : 'Resource log stream was interrupted', receivedLine);
  }
  if (!result) {
    throw new ResourceLogStreamError('Resource log stream ended before completion', receivedLine);
  }
  return result;
}

export async function downloadIncidentReport(
  incidentId: string,
  format: 'markdown' | 'json' | 'zip'
): Promise<{ blob: Blob; filename: string; sha256?: string }> {
  const response = await fetch(
    `${API_BASE_URL}/api/incidents/${encodeURIComponent(incidentId)}/report?format=${encodeURIComponent(format)}`,
    { method: 'GET', headers: { 'Accept-Language': getLocale() }, credentials: 'same-origin' }
  );
  if (!response.ok) throw new ApiError(response.status, await parseError(response));
  const disposition = response.headers.get('Content-Disposition') || '';
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  const plain = disposition.match(/filename="?([^";]+)"?/i)?.[1];
  return {
    blob: await response.blob(),
    filename: encoded ? decodeURIComponent(encoded) : plain || `incident-${incidentId}.${format === 'markdown' ? 'md' : format}`,
    sha256: response.headers.get('X-Content-SHA256') || undefined
  };
}

export async function streamCommandExecution(
  clusterId: string,
  executionId: string,
  onEvent: (event: { type: string; data: unknown }) => void,
  signal?: AbortSignal
): Promise<void> {
  const response = await fetch(
    `${API_BASE_URL}/api/clusters/${encodeURIComponent(clusterId)}/command-executions/${encodeURIComponent(executionId)}/stream`,
    { headers: { Accept: 'text/event-stream', 'Accept-Language': getLocale() }, credentials: 'same-origin', signal }
  );
  if (!response.ok || !response.body) throw new ApiError(response.status, await parseError(response));
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const blocks = buffer.split(/\r?\n\r?\n/);
    buffer = blocks.pop() ?? '';
    blocks.forEach((block) => emitCommandEvent(block, onEvent));
  }
  if (buffer) emitCommandEvent(buffer, onEvent);
}

function emitCommandEvent(block: string, onEvent: (event: { type: string; data: unknown }) => void): void {
  let type = 'message';
  const data: string[] = [];
  block.split(/\r?\n/).forEach((line) => {
    if (line.startsWith('event:')) type = line.slice(6).trim();
    if (line.startsWith('data:')) data.push(line.slice(5).trimStart());
  });
  if (!data.length) return;
  const value = data.join('\n');
  try {
    onEvent({ type, data: JSON.parse(value) as unknown });
  } catch {
    onEvent({ type, data: value });
  }
}

export async function streamAiChatMessage(
  conversationId: string,
  body: SendMessageRequest,
  onDelta: (delta: string) => void,
  signal?: AbortSignal
): Promise<void> {
  const csrfToken = readCookie('XSRF-TOKEN');
  const response = await fetch(`${API_BASE_URL}/api/ai-chat/conversations/${conversationId}/messages/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      'Accept-Language': getLocale(),
      ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {})
    },
    credentials: 'same-origin',
    body: JSON.stringify(body),
    signal
  });

  if (!response.ok || !response.body) {
    throw new ApiError(response.status, await parseError(response));
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let completed = false;
  let receivedDelta = false;

  const consume = (rawEvent: string) => {
    const event = parseSseEvent(rawEvent);
    if (event.type === 'heartbeat') return;
    if (!event.data && event.type !== 'done') return;
    if (event.type === 'error') throw new AiChatStreamError(event.data || 'AI response stream was interrupted', receivedDelta);
    if (event.type === 'done' || event.data === '[DONE]') {
      completed = true;
      return;
    }
    receivedDelta = true;
    onDelta(event.data);
  };

  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const events = buffer.split('\n\n');
      buffer = events.pop() ?? '';
      for (const event of events) {
        consume(event);
      }
    }
    if (buffer.trim()) consume(buffer);
  } catch (error) {
    if (error instanceof AiChatStreamError || signal?.aborted) throw error;
    throw new AiChatStreamError(error instanceof Error ? error.message : 'AI response stream was interrupted', receivedDelta);
  }

  if (!completed) throw new AiChatStreamError('AI response stream ended before completion', receivedDelta);
}

function parseSseEvent(event: string): { type: string; data: string } {
  const type = event.split('\n').find((line) => line.startsWith('event:'))?.slice(6).trim() ?? 'message';
  const data = event
    .split('\n')
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n');
  return { type, data };
}
