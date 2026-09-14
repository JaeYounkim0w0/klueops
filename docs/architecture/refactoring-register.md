# Refactoring Register

기준일: 2026-09-11

이 문서는 기능 개발과 무관한 전면 재작성을 방지하면서, 현재 소스의 구조 위험을 우선순위로 관리한다. 변경은 기존 계약 테스트를 먼저 확보한 뒤 작은 책임 단위로 이동한다.

## 이번 단계 완료

- `AnalysisCommandPolicy`: kubectl 명령의 조회/변경/파괴 분류와 runbook category를 `AnalysisApplicationService`에서 분리했다.
- `analysisResult.ts`: AI JSON fence 제거, object/array 정규화, raw JSON 표시를 `AnalysisView`에서 분리했다.
- OIDC health probe matcher: Kubernetes readiness/liveness group을 익명 probe 대상으로 명시하고 보안 회귀 테스트를 추가했다.
- Frontend container: root nginx에서 unprivileged nginx 8080으로 전환했다.
- `NamespaceAnalysisContextBuilder`: namespace section 공통 header, signal append, 명시적 context truncation을 분리했다.
- `AnalysisResultAssembler`: AI section 결과를 JSON shape와 section 이름에 맞게 namespace/cluster fallback 결과에 병합하고, section status·bounded diagnostics·incremental reuse metadata를 조립하는 책임을 분리했다. 부분 실패와 잘못된 JSON shape가 fallback evidence를 덮지 않으며 단일 호출 timeout 계약은 유지한다.
- `AnalysisRunPanel`, `AnalysisRuntimePanel`, `AnalysisEvidencePanel`: 분석 실행, runtime 진단, evidence 표시를 typed props/events 경계로 분리했다.
- `useAnalysisOperations`: 비동기 분석 Job 완료/실패 결과 회수 orchestration을 View에서 분리했다.
- `styles/components/analysis.css`: 분석 실행 flow와 responsive 스타일을 공통 `main.css`에서 분리했다.
- `ProductionEvidenceService`: 상용 검증 state, masking, checksum과 저장 책임을 운영 제어 서비스에서 분리했다.
- `OperationalTelemetry`: bounded cardinality, API/AI section 평균·P95·최대 지연 집계를 공통 계측 경계로 분리했다.
- `KubernetesUpgradeCatalog`, `CapabilityMatrixSummary`: Readiness의 catalog loading과 권한 집계를 독립 단위 테스트 가능한 객체로 분리했다.
- `ProductionEvidencePanel`, `OperationalTelemetryPanel`: Operations Reliability의 증빙/품질 화면을 typed component로 분리했다.
- `AnalysisComparisonService`: 이전/현재 분석의 risk와 issue group 비교 및 trend 생성을 대형 분석 서비스에서 분리했다.
- `AnalysisCommandParser`: 지원 kubectl 문법, namespace 강제, 위험 분류와 mutation 상한을 분석 orchestration에서 분리했다.
- `AnalysisCommandExecutor`: RBAC/dry-run/rollback guard, Kubernetes 조회·로그·변경 실행과 bounded 출력 포맷을 분석 orchestration에서 분리했다.
- `AnalysisCommandSafetyPanel`: 명령 안전 분류와 단계별 command presentation을 `AnalysisView`에서 분리했다.
- `RuntimeReadinessPanel`: 실행 환경 차단 항목 정렬, 상태 집계와 조치 안내를 Operations view에서 분리했다.
- `DeterministicRiskTimelineSectionBuilder`: 위험 예측과 변경 타임라인의 JSON 계약 조립을 `AnalysisApplicationService`에서 분리했다. 계산 규칙과 Kubernetes 수집은 기존 서비스에 유지해 동작을 보존하고, null 정규화와 최대 8건 제한을 독립 단위 테스트로 보호한다.
- `DeterministicPerformanceScalingSectionBuilder`: 성능 병목·스케일 후보·HPA 권고·capacity note의 JSON 계약 조립을 분리했다. 기존 `signal`/`currentSignal` 필드와 fallback 항목을 유지하고, 입력 신호와 출력 항목에 bounded limit을 적용한다.
- `KubernetesPerformanceSignalPolicy`: performance/scaling에 사용할 문제 리소스·Warning Event·로그·capacity 신호 선별과 권고 문구 결정을 `AnalysisApplicationService`에서 분리했다.
- `AnalysisRunbookPanel`, `AnalysisIssueGroupPanel`: Runbook 검증 큐와 Issue Group 카드 표시 및 사용자 이벤트 전달을 `AnalysisView`에서 분리했다. 데이터 조회, 로그 이동, drill-down orchestration은 부모가 유지한다.
- `analysis-operations.css`: 새 분석 운영 컴포넌트의 overflow·responsive ownership을 별도 스타일 파일로 분리했다.
- `IncidentStateTransitionPolicy`: Incident 상태 전이 허용 규칙을 `OperationsControlPlaneService`에서 분리했다. lifecycle 저장, audit, event 발행은 기존 서비스에 유지한다.
- `AnalysisTextDetailModal`: 긴 Issue Group/분석 설명 표시를 `AnalysisView`에서 독립 modal component로 분리했다.
- `AnalysisResourceDetailModal`, `AnalysisLogDetailModal`: Resource 진단과 로그 조회의 표시·overflow·사용자 이벤트를 `AnalysisView`에서 분리했다. API 호출과 로그 대상 검증은 부모 orchestration에 유지한다.
- `AnalysisCommandModals`: 변경 Preview/확인과 명령 실행 결과 표시를 `AnalysisView`에서 분리했다. RBAC, dry-run, rollback guard 판정과 실제 실행 API 호출은 부모에 유지한다.
- `OperationsPolicyEvaluator`: workload 가용성, Pod/PVC 상태, Service endpoint/port, 참조 무결성, HPA/PDB와 Namespace governance 평가를 `OperationsControlPlaneService`에서 분리했다. 평가기는 전달받은 snapshot과 활성 정책만 사용하며 저장, baseline, audit 책임은 제어 서비스에 유지한다.
- `IncidentRecoveryCoordinator`: 최신 snapshot 기반 자동 회복 가능 여부, 중복 관찰 차단, 연속 정상 관찰, MONITORING/RESOLVED/REOPENED 전이와 recovery/activity 저장을 분리했다. 알림은 값 객체로 반환해 기존 dedup 발행 경계를 유지한다.
- `IncidentSignalCoordinator`: AI High/Critical finding, 동기화 Warning Event와 실시간 Watch 승격을 공통 `IncidentSignal`로 정규화하고 fingerprint, evidence dedup, legacy 보정, 재오픈과 Incident 저장을 분리했다. AI 추론과 Kubernetes factual evidence의 신뢰도 구분을 유지한다.
- `OperationsOverviewQueryService`: 클러스터 건강도, Incident/Job 우선순위, 작업·분석 통계와 구성 기반 capacity posture 집계를 읽기 전용 조회 경계로 분리했다. 제어 서비스는 15초 cache와 invalidation만 유지한다.
- `ResourceBaselineCoordinator`: 최초 baseline 생성, canonical summary/status hash, CREATED/STATUS_CHANGED/UPDATED/DELETED 판정과 변경 이력 저장을 분리했다. inventory가 500건 이상이면 삭제를 확정하지 않는 기존 안전장치를 유지한다.
- `OperationsNotificationPublisher`: 알림 suppress window bucket, SHA-256 dedup key, 민감정보 마스킹, 길이 제한과 occurrence 증가 저장을 공통 경계로 분리했다.
- `WatchSignalTriageService`: 실시간 Watch 신호 분류, noise policy 적용, 반복 임계치, signal group 저장, Incident 승격과 triage queue/state 변경을 분리했다. 기존 use case 구현은 제어 서비스의 cache invalidation delegate로 유지한다.
- `AiQualityQueryService`: AI 품질과 model/prompt calibration 집계를 분리하고, feedback별 분석 세션 조회를 `findByIds` 일괄 조회로 변경했다.
- `OperationsScorecardQueryService`: MTTA/MTTR, 주간 추세, hotspot과 Signal Group 통계를 분리하고, Incident별 activity N+1 조회를 단일 batch 조회로 변경했다.
- `AnalysisSectionExecutor`: namespace/cluster AI section의 비동기 실행, timeout fallback, 오류 격리, cluster 의미 검증, 증분 fingerprint 재사용과 bounded telemetry를 `AnalysisApplicationService`에서 분리했다.
- `KubernetesPortTopologyAnalyzer`: Service selector/targetPort와 Pod·workload containerPort 상관 분석을 `AnalysisApplicationService`에서 분리했다. 불완전한 manifest에서는 보수적으로 판단을 보류하고 최대 12개 신호만 반환한다.
- `AnalysisLogIntelligencePanel`: 로그 패턴·근거·신뢰도 표시를 `AnalysisView`에서 분리했다. 최대 8개 항목과 긴 텍스트 overflow를 컴포넌트 경계에서 관리한다.
- `KubernetesDiagnosticCollectionGuard`: Namespace Kubernetes API 수집을 source별로 격리하고 실패 budget, 지연, 건수, 오류 요약을 기록한다. 하나 이상의 source가 성공하면 부분 근거를 보존한다.
- `AnalysisCollectionDiagnosticsWriter`: 수집 품질을 분석 runtime JSON에 병합하고 부분 coverage의 confidence 상한을 적용하는 책임을 대형 분석 서비스에서 분리했다.
- `KubernetesDiagnosticSummaryMapper`: Fabric8 리소스의 상태·replica·quantity·JSON summary 변환을 Kubernetes API 수집 adapter에서 분리하고 직접 단위 테스트로 보호했다.
- `useAnalysisCommandNavigation`: AI 분석 검증 명령의 namespace 치환, 복사, Kubernetes 콘솔 이동 query 조립을 `AnalysisView`에서 분리했다. 콘솔 실행 이력은 원본 analysis ID를 유지한다.
- `AnalysisLocalePolicy`: 분석 응답 언어 지시와 결과 locale 부착을 대형 분석 서비스에서 분리했다. Kubernetes 이름, 로그, 명령과 schema key는 번역하지 않는 계약을 직접 테스트한다.
- `AnalysisSummaryOverview`: 분석 요약, risk/runtime badge와 초보자·숙련자 전환 표시를 `AnalysisView`에서 분리했다.
- `Fabric8PodLogCollector`: container 상태, current/previous log 수집, 크기 제한, 민감정보 마스킹과 사용자 친화 오류를 Fabric8 진단 adapter에서 분리했다.
- `AnalysisCommandEvidenceMerger`: 명령 실행 결과를 analysis JSON의 command verification, evidence ledger, issue group 검증과 conclusion validation에 연결하는 계약 변환을 `AnalysisApplicationService`에서 분리했다. 저장 orchestration은 서비스에 유지하고, 중복 제거와 10/5건 이력 상한 및 손상 JSON 무시 동작을 직접 단위 테스트로 보호한다.

## 잔여 우선순위

| 우선순위 | 대상 | 현재 위험 | 다음 안전 경계 |
| --- | --- | --- | --- |
| P1 | `AnalysisApplicationService` | 4,877 lines로 감소했으나 deterministic evidence 조립과 일부 Kubernetes signal 계산 책임 잔존 | 남은 evidence assembly와 signal policy를 순차 분리 |
| P1 | `AnalysisView.vue` | 3,309 lines로 감소했으나 일부 상세 결과와 API orchestration 책임 잔존 | 남은 분석 상세 표시와 API orchestration을 순차 분리 |
| P1 | `main.css` | 7,291 lines, 여러 도메인 selector가 한 파일에 공존 | 기존 cluster/operations selector를 ownership 파일로 순차 이동 |
| P2 | `Fabric8KubernetesNamespaceDiagnosticsAdapter` | log collector 분리 후 1,089 lines이며 API source별 수집 책임이 잔존 | resource collector를 source 단위로 순차 분리 |
| P2 | generated API facade | `client.ts`가 수동 wrapper를 집중 보유 | 도메인별 API module로 이동하며 generated type은 유지 |

## 보호 규칙

- `AnalysisApplicationService.java`는 4,890 lines를 초과하지 않는다.
- `AnalysisView.vue`는 3,320 lines를 초과하지 않는다.
- `main.css`는 7,300 lines를 초과하지 않는다.
- `Fabric8KubernetesNamespaceDiagnosticsAdapter.java`는 1,100 lines를 초과하지 않는다.
- 새 deterministic 정책은 stateless class와 직접 단위 테스트를 가진다.
- `OperationsControlPlaneService.java`는 500 lines를 초과하지 않는다.
- deterministic section builder는 AI 호출을 수행하지 않고, 입력 결과를 bounded·null-safe JSON 계약으로 변환한다.
- 새 범용 UI 데이터 변환은 `frontend/src/utils`에 두고 Vitest를 가진다.
- 리팩터링은 OpenAPI/result JSON schema/UI command 실행 동작을 변경하지 않는다.

이 line cap은 목표 크기가 아니라 신규 결합 증가를 막는 임시 상한이다. 각 후속 분리에서 상한을 낮춘다.
