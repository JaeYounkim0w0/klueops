#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANALYSIS_SERVICE="${ROOT_DIR}/backend/src/main/java/io/strato/aiops/application/service/AnalysisApplicationService.java"
ANALYSIS_VIEW="${ROOT_DIR}/frontend/src/views/AnalysisView.vue"
MAIN_STYLES="${ROOT_DIR}/frontend/src/styles/main.css"
OPERATIONS_SERVICE="${ROOT_DIR}/backend/src/main/java/io/strato/aiops/application/service/OperationsControlPlaneService.java"
FABRIC8_DIAGNOSTICS="${ROOT_DIR}/backend/src/main/java/io/strato/aiops/adapter/out/kubernetes/Fabric8KubernetesNamespaceDiagnosticsAdapter.java"

service_lines="$(wc -l <"${ANALYSIS_SERVICE}" | tr -d ' ')"
view_lines="$(wc -l <"${ANALYSIS_VIEW}" | tr -d ' ')"
style_lines="$(wc -l <"${MAIN_STYLES}" | tr -d ' ')"
operations_lines="$(wc -l <"${OPERATIONS_SERVICE}" | tr -d ' ')"
fabric8_lines="$(wc -l <"${FABRIC8_DIAGNOSTICS}" | tr -d ' ')"

if (( service_lines > 4890 )); then
  echo "AnalysisApplicationService exceeded the temporary 4890-line cap: ${service_lines}" >&2
  exit 2
fi
if (( view_lines > 3320 )); then
  echo "AnalysisView exceeded the temporary 3320-line cap: ${view_lines}" >&2
  exit 3
fi
if (( style_lines > 7300 )); then
  echo "main.css exceeded the temporary 7300-line cap: ${style_lines}" >&2
  exit 5
fi
if (( operations_lines > 500 )); then
  echo "OperationsControlPlaneService exceeded the temporary 500-line cap: ${operations_lines}" >&2
  exit 6
fi
if (( fabric8_lines > 1100 )); then
  echo "Fabric8KubernetesNamespaceDiagnosticsAdapter exceeded the temporary 1100-line cap: ${fabric8_lines}" >&2
  exit 7
fi

test -s "${ROOT_DIR}/backend/src/main/java/io/strato/aiops/application/service/AnalysisCommandPolicy.java"
test -s "${ROOT_DIR}/backend/src/test/java/io/strato/aiops/application/service/AnalysisCommandPolicyTest.java"
test -s "${ROOT_DIR}/backend/src/main/java/io/strato/aiops/application/service/NamespaceAnalysisContextBuilder.java"
test -s "${ROOT_DIR}/backend/src/main/java/io/strato/aiops/application/service/AnalysisResultAssembler.java"
test -s "${ROOT_DIR}/backend/src/main/java/io/strato/aiops/application/service/AnalysisSectionExecutor.java"
test -s "${ROOT_DIR}/backend/src/test/java/io/strato/aiops/application/service/AnalysisSectionExecutorTest.java"
test -s "${ROOT_DIR}/backend/src/main/java/io/strato/aiops/application/service/KubernetesPortTopologyAnalyzer.java"
test -s "${ROOT_DIR}/backend/src/test/java/io/strato/aiops/application/service/KubernetesPortTopologyAnalyzerTest.java"
test -s "${ROOT_DIR}/backend/src/main/java/io/strato/aiops/application/service/AnalysisCollectionDiagnosticsWriter.java"
test -s "${ROOT_DIR}/backend/src/test/java/io/strato/aiops/application/service/AnalysisCollectionDiagnosticsWriterTest.java"
test -s "${ROOT_DIR}/backend/src/main/java/io/strato/aiops/adapter/out/kubernetes/KubernetesDiagnosticCollectionGuard.java"
test -s "${ROOT_DIR}/backend/src/test/java/io/strato/aiops/adapter/out/kubernetes/KubernetesDiagnosticCollectionGuardTest.java"
test -s "${ROOT_DIR}/backend/src/main/java/io/strato/aiops/application/service/AnalysisComparisonService.java"
test -s "${ROOT_DIR}/backend/src/test/java/io/strato/aiops/application/service/AnalysisComparisonServiceTest.java"
test -s "${ROOT_DIR}/backend/src/main/java/io/strato/aiops/application/service/AnalysisCommandParser.java"
test -s "${ROOT_DIR}/backend/src/test/java/io/strato/aiops/application/service/AnalysisCommandParserTest.java"
test -s "${ROOT_DIR}/backend/src/main/java/io/strato/aiops/application/service/AnalysisCommandExecutor.java"
test -s "${ROOT_DIR}/backend/src/main/java/io/strato/aiops/application/service/AnalysisCommandEvidenceMerger.java"
test -s "${ROOT_DIR}/backend/src/test/java/io/strato/aiops/application/service/AnalysisCommandEvidenceMergerTest.java"
test -s "${ROOT_DIR}/backend/src/main/java/io/strato/aiops/application/service/OperationsPolicyEvaluator.java"
test -s "${ROOT_DIR}/backend/src/test/java/io/strato/aiops/application/service/OperationsPolicyEvaluatorTest.java"
test -s "${ROOT_DIR}/backend/src/main/java/io/strato/aiops/application/service/IncidentRecoveryCoordinator.java"
test -s "${ROOT_DIR}/backend/src/test/java/io/strato/aiops/application/service/IncidentRecoveryCoordinatorTest.java"
test -s "${ROOT_DIR}/backend/src/main/java/io/strato/aiops/application/service/IncidentSignalCoordinator.java"
test -s "${ROOT_DIR}/backend/src/test/java/io/strato/aiops/application/service/IncidentSignalCoordinatorTest.java"
test -s "${ROOT_DIR}/backend/src/main/java/io/strato/aiops/application/service/OperationsOverviewQueryService.java"
test -s "${ROOT_DIR}/backend/src/test/java/io/strato/aiops/application/service/OperationsOverviewQueryServiceTest.java"
test -s "${ROOT_DIR}/backend/src/main/java/io/strato/aiops/application/service/ResourceBaselineCoordinator.java"
test -s "${ROOT_DIR}/backend/src/test/java/io/strato/aiops/application/service/ResourceBaselineCoordinatorTest.java"
test -s "${ROOT_DIR}/backend/src/main/java/io/strato/aiops/application/service/OperationsNotificationPublisher.java"
test -s "${ROOT_DIR}/backend/src/test/java/io/strato/aiops/application/service/OperationsNotificationPublisherTest.java"
test -s "${ROOT_DIR}/backend/src/main/java/io/strato/aiops/application/service/WatchSignalTriageService.java"
test -s "${ROOT_DIR}/backend/src/test/java/io/strato/aiops/application/service/WatchSignalTriageServiceTest.java"
test -s "${ROOT_DIR}/backend/src/main/java/io/strato/aiops/application/service/AiQualityQueryService.java"
test -s "${ROOT_DIR}/backend/src/test/java/io/strato/aiops/application/service/AiQualityQueryServiceTest.java"
test -s "${ROOT_DIR}/backend/src/main/java/io/strato/aiops/application/service/OperationsScorecardQueryService.java"
test -s "${ROOT_DIR}/backend/src/test/java/io/strato/aiops/application/service/OperationsScorecardQueryServiceTest.java"
test -s "${ROOT_DIR}/frontend/src/utils/analysisResult.ts"
test -s "${ROOT_DIR}/frontend/src/__tests__/analysis-result.test.ts"
test -s "${ROOT_DIR}/frontend/src/components/analysis/AnalysisRunPanel.vue"
test -s "${ROOT_DIR}/frontend/src/components/analysis/AnalysisRuntimePanel.vue"
test -s "${ROOT_DIR}/frontend/src/components/analysis/AnalysisEvidencePanel.vue"
test -s "${ROOT_DIR}/frontend/src/components/analysis/AnalysisCommandSafetyPanel.vue"
test -s "${ROOT_DIR}/frontend/src/components/analysis/AnalysisLogIntelligencePanel.vue"
test -s "${ROOT_DIR}/frontend/src/components/operations/RuntimeReadinessPanel.vue"
test -s "${ROOT_DIR}/frontend/src/composables/useAnalysisOperations.ts"
test -s "${ROOT_DIR}/frontend/src/composables/useAnalysisCommandNavigation.ts"
test -s "${ROOT_DIR}/backend/src/main/java/io/strato/aiops/adapter/out/kubernetes/KubernetesDiagnosticSummaryMapper.java"
test -s "${ROOT_DIR}/backend/src/test/java/io/strato/aiops/adapter/out/kubernetes/KubernetesDiagnosticSummaryMapperTest.java"
test -s "${ROOT_DIR}/frontend/src/styles/components/analysis.css"
test -s "${ROOT_DIR}/frontend/src/styles/components/analysis-operations.css"
test -s "${ROOT_DIR}/frontend/src/components/analysis/AnalysisSummaryOverview.vue"
test -s "${ROOT_DIR}/backend/src/main/java/io/strato/aiops/application/service/AnalysisLocalePolicy.java"
test -s "${ROOT_DIR}/backend/src/test/java/io/strato/aiops/application/service/AnalysisLocalePolicyTest.java"
test -s "${ROOT_DIR}/backend/src/main/java/io/strato/aiops/adapter/out/kubernetes/Fabric8PodLogCollector.java"
test -s "${ROOT_DIR}/docs/architecture/refactoring-register.md"

if rg -q 'private String (commandSafetyLevel|runbookCategory|runbookCommandType)\(' "${ANALYSIS_SERVICE}"; then
  echo "Extracted command policy logic returned to AnalysisApplicationService." >&2
  exit 4
fi

echo "Maintainability guard passed: AnalysisApplicationService=${service_lines}, OperationsControlPlaneService=${operations_lines}, AnalysisView=${view_lines}, main.css=${style_lines}, Fabric8Diagnostics=${fabric8_lines}."
