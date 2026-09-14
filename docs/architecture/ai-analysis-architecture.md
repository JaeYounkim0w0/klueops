# AI Analysis Architecture

AI provider는 port로 추상화한다.

```java
public interface AiAnalysisPort {
    AiAnalysisResult analyze(KubernetesAnalysisContext context);
}
```

기본 provider는 Ollama이며, OpenAI/Gemini 등은 adapter로 확장한다.
Ollama 연동은 Spring AI `ChatClient`를 사용한다.
AI Analysis는 실행 단위별 재현성과 정확도를 우선하므로 ChatMemory를 사용하지 않는 stateless 호출로 처리한다.
Ollama `format=json` 옵션과 저장 전 JSON validation을 적용해 분석 결과 품질을 보장한다.

분석 결과는 `analysis-result.v1` schema를 따른다.
필수 섹션은 `findings`, `rootCauses`, `logAnalysis`, `performance`, `scaling`, `riskForecast`, `changeTimeline`, `runbookActions`, `recommendations`, `operationsGuide`, `nextActions`, `verificationCommands`, `evidence`이다.
운영자 UX용 optional 섹션으로 `analysisComparison`, `confidenceValidation`, `analysisQuality`, `logIntelligence`, `actionRecommendations`, `evidenceLedger`, `eventNoiseReduction`, `correlationMap`, `commandSafety`, `problemCards`, `issueGroups`, `remediationPlan`, `issueGroupDeepDives`, `actionWorkflow`, `conclusionValidation`, `reanalysisPlan`을 제공한다. `analysisComparison`은 동일 cluster/application/namespace scope의 직전 성공 분석과 현재 분석을 비교해 risk delta, severity 변화, 신규/해결/지속 issue group을 제공한다. `confidenceValidation`은 문제 리소스, Warning 이벤트, 로그 신호, issue group 존재 여부로 분석 신뢰도를 설명한다. `analysisQuality`는 evidence alignment, log specificity, actionability, coverage를 점수화해 운영자가 이 분석을 조치 판단에 사용할 수 있는지 확인하게 한다. `logIntelligence`는 Pod log와 previous log의 고신호 line을 category, severity, operator meaning, beginner explanation, verification command, quality gate, action candidates로 정규화한다. `actionRecommendations`는 log intelligence와 issue group에서 preflight command, validation command, safety level, manifest hints를 가진 조치 후보를 생성한다. `evidenceLedger`는 Kubernetes fact, event signal, log signal, inference를 분리해 사용자가 AI 판단 근거를 검증할 수 있게 한다. `eventNoiseReduction`은 반복 이벤트를 같은 reason/resource 단위로 압축하고, `correlationMap`은 Pod/Event/Log/ConfigMap/Secret/PVC 같은 연결 관계를 표시한다. `commandSafety`는 분석 결과의 kubectl 명령을 읽기 전용, 변경 가능, 위험 조치, 검토 필요로 분류한다. `problemCards`는 Root Cause를 대체하지 않고, Kubernetes API evidence를 기반으로 원인 후보를 카드화해 confidence, fixReadiness, evidenceTrace, beforeCommands, afterCommands, relatedReferences를 한 번에 보여준다. `issueGroups`는 반복 이벤트, 문제 리소스, 로그 신호를 동일 원인 후보로 압축하고, `remediationPlan`은 변경 전 검증 중심의 처리 순서를 생성한다. `issueGroupDeepDives`는 원인 그룹별 상태/이벤트/로그/참조 리소스 확인 명령을 초보자 설명과 함께 제공한다. `actionWorkflow`는 원인 그룹 상태를 OPEN, INVESTIGATING, ACTION_PENDING, FIXED, ACCEPTED로 추적하게 한다. `conclusionValidation`은 AI 결론별 evidence 충분성을 VERIFIED, INFERRED, NEEDS_EVIDENCE로 분류한다. `reanalysisPlan`은 조치 후 같은 scope 재분석 전략과 로그 확대 조건을 제공한다.
Ollama가 optional section을 누락하거나 배열/객체 container type을 잘못 반환하는 경우에는 저장 전 schema normalization으로 빈 배열/객체를 보정한다. 단 `summary`, `severity`처럼 분석 의미를 구성하는 핵심 text field는 자동 생성하지 않고 실패로 처리한다.
성능과 스케일링 판단은 Kubernetes metric이 없을 경우 metric 값을 추정하지 않고, Pod 상태, restart count, Endpoint readiness, rollout, PVC, Event, Log signal 기반으로 근거를 제한한다.
Prometheus와 metrics-server 연동은 후순위로 두며, 1차 고도화에서는 Kubernetes API로 조회 가능한 spec/status/event/log 신호를 우선한다.
Kubernetes API 기반 분석 신호에는 HPA, PDB, ResourceQuota, LimitRange, container requests/limits, probe 설정, imagePullPolicy, QoS class를 포함한다.

AI 호출 전 backend rule-based pre analyzer가 다음 신호를 먼저 산출한다.

- 문제 후보 리소스
- Warning 이벤트
- high-signal Pod log
- deterministic log intelligence category
- Service targetPort와 selector 대상 Pod/Workload containerPort 불일치
- Pod startup log의 bind/listen/port 충돌 패턴
- health score
- risk forecast
- change timeline
- runbook actions
- evidence signal
- analysis comparison
- problem cards
- issue groups
- remediation plan
- action recommendations
- analysis quality gate

Risk forecast는 AI 호출 전 결정론적으로 먼저 산출한다. 현재 단계에서는 비정상 리소스 상태, 반복 Warning 이벤트, high-signal 로그, HPA/ResourceQuota/LimitRange/NetworkPolicy 부재처럼 Kubernetes API로 확인 가능한 신호만 사용한다. Prometheus가 필요한 CPU/memory/latency 포화 예측은 metric 연동 이후 확장한다.

Log Intelligence는 AI 호출 전 deterministic catalog로 먼저 산출한다. 로그 원문은 길고 노이즈가 많아 LLM에 한 번에 맡기면 timeout과 오판 가능성이 커지므로, backend가 previous log 여부, 대표 line, category, severity, priority, matched pattern을 먼저 계산한다. 현재 catalog는 port startup, port conflict, memory pressure, filesystem permission, missing config, certificate/TLS, dependency network, DNS, dependency auth, probe/health, application error, runtime hardening을 포함한다. 이 결과는 `logIntelligence` 패널뿐 아니라 `logAnalysis`, `issueGroups`, `riskForecast`, `runbookActions`, `evidenceLedger`, `actionRecommendations`, `analysisQuality`에 재사용한다.

Action Recommendations는 변경을 자동 실행하기 위한 섹션이 아니라 운영자의 검증 순서를 선명하게 만드는 deterministic decision aid이다. 각 candidate는 `VERIFY`, `CHANGE_PLAN`, `PREVENTION`, `OBSERVE` 중 하나의 actionType과 `READ_ONLY`, `CHANGE_REQUIRES_REVIEW`, `BLOCKED` safetyLevel을 가진다. Backend는 previous log의 privileged port bind failure, missing config, filesystem permission, dependency/DNS/auth, probe failure 같은 catalog category별로 preflight command와 validation command를 분리한다. 변경 방향은 manifest hints로 제공하고, 실제 변경 실행 여부는 command runner의 allow-list/RBAC/dry-run/rollback guard가 별도로 판단한다.

Analysis Quality는 최종 JSON 저장 직전 계산한다. 이때 `commandSafety`, `issueGroups`, `logIntelligence`, `actionRecommendations`, `evidenceLedger`, `confidenceValidation` 존재 여부와 Kubernetes diagnostics count를 함께 사용한다. 품질 점수는 AI의 자신감 점수가 아니라 “운영자가 검증 가능한 근거와 안전한 다음 행동을 충분히 받았는지”에 대한 제품 품질 지표이다.

Change timeline은 Event 발생 시각, 현재 문제 리소스, high-signal log를 묶어 장애 시작 추정과 의심 변화 지점을 제공한다. Runbook actions는 kubectl get/describe/logs 같은 검증 우선 명령만 기본 제공하며, 변경성 명령 자동 실행은 별도 승인 기능이 생기기 전까지 제공하지 않는다.

UI는 AI 결과와 별도로 pre analyzer 근거를 표시한다. 로그 조회는 Pod 직접 조회뿐 아니라 Deployment, StatefulSet, DaemonSet, ReplicaSet, Job, Service에서 selector 기반으로 관련 Pod를 찾아 제공한다. ConfigMap, Secret, PVC처럼 직접 로그가 없는 리소스는 직접 로그 없음 상태를 명확히 반환하고 event/evidence 확인을 유도한다.

Risk Forecast UI는 HealthScore 아래에 배치한다. 사용자는 현재 건강도, 향후 위험도, 예측 후보를 한 화면에서 이어서 확인하고 각 후보의 verificationCommand로 검증 행동을 시작할 수 있어야 한다.

Change Timeline과 Runbook UI는 Risk Forecast 바로 아래에 배치한다. 운영자는 위험 후보를 본 뒤 같은 시야 안에서 장애 시작 추정 시점과 검증 명령을 확인할 수 있어야 한다.

문제 후보 리소스 상세 진단 UI는 목록 row를 복잡하게 만들지 않고 icon action으로 진입한다. 상세 진단에서는 리소스 status/summary, 관련 Warning Event timeline, evidence, 관련 Pod log source를 한 화면에 표시해 로그가 없는 상태에서도 다음 확인 지점을 잃지 않게 한다.

Cluster-level AI Analysis는 namespace 분석보다 prompt가 빠르게 커질 수 있으므로 별도 context 상한과 section limit을 적용한다. Ollama timeout이 발생하면 요청을 단순 실패로 끝내지 않고 `analysisMode=kubernetes-api-timeout-fallback` 결과를 저장한다. 이 fallback은 AI 추론 결과가 아니라 Kubernetes API에서 이미 수집한 문제 리소스와 Warning Event 기반의 triage 결과이며, summary와 evidence에 timeout 사실을 명시한다.

LLM context 설계 원칙은 "작고 목적이 분명한 context를 여러 번 호출한 뒤 검증된 schema로 병합"하는 것이다. Kubernetes inventory, event, log, resource summary를 모두 한 prompt에 넣는 방식은 timeout, token 비용, 응답 누락, hallucination 가능성을 높이므로 기본 설계로 사용하지 않는다. 분석 결과가 root cause, log analysis, performance, scaling, risk forecast, runbook처럼 관심사가 분리되는 경우에는 각 관심사별 context slice와 prompt를 별도로 구성한다.

개발 시 LLM 호출은 반드시 관심사 단위로 context를 분할한다. Root Cause, Log Analysis, Performance/Scaling, Risk Timeline, Runbook처럼 서로 다른 판단 기준을 가진 항목을 하나의 대형 prompt로 합치지 않는다. 큰 context가 필요한 기능은 context char 수, section latency, timeout 여부를 `analysisDiagnostics`에 기록하고, 실패한 section만 deterministic fallback으로 보강한다.

Namespace/Application AI Analysis는 sectioned orchestrator를 사용한다. Backend pre analyzer가 Kubernetes API diagnostics를 한 번 수집한 뒤, `root-cause`, `log-analysis`, `runbook-operations` 단위로 작은 context slice를 구성한다. 각 section은 독립 AI 호출로 분석하고, 결과를 `analysis-result.v1` 최종 JSON으로 병합한다. 특정 section이 timeout 또는 format 오류로 실패하면 전체 분석을 실패시키지 않고 해당 section만 Kubernetes API 기반 fallback으로 유지한다.

`risk-timeline`은 LLM 호출 대상에서 제외하고 deterministic section으로 처리한다. Risk forecast와 change timeline은 backend pre analyzer가 problem resource, warning event, high-signal log, event time을 기준으로 산출할 수 있는 사실 기반 영역이므로 Ollama timeout과 queue 지연을 줄이기 위해 AI synthesis 대신 Kubernetes API evidence를 그대로 구조화한다.

`performance-scaling`도 Prometheus 연동 전까지는 deterministic section으로 처리한다. CPU/Memory/Latency 같은 metric은 생성하지 않고, Pending Pod, FailedScheduling/FailedMount, Endpoint readiness, PVC 상태, HPA/ResourceQuota/LimitRange 존재 여부, workload replica 상태처럼 Kubernetes API로 확인 가능한 신호만 구조화한다. 이 섹션은 운영자가 빈 화면을 보지 않도록 summary, bottlenecks, improvements, scaleUpCandidates, hpaRecommendations, capacityNotes를 항상 채운다.

Cluster-level AI Analysis는 namespace보다 context가 훨씬 커지고 교차 namespace 상관관계가 필요하므로 기존 cluster context + timeout fallback 구조를 유지한다. 향후 cluster도 namespace hotspot 단위 sectioned 분석으로 확장할 수 있다.

AI Analysis 실행은 운영 UI에서 비동기 Job 모델을 기본으로 사용한다. 요청 API는 `AsyncJob(type=AI_ANALYSIS)`과 `AnalysisSession(status=RUNNING)`을 생성하고 즉시 `jobId`, `analysisId`를 반환한다. 백그라운드 worker는 Kubernetes context 수집, AI 호출, 결과 normalization을 수행한 뒤 job과 analysis session을 `SUCCEEDED` 또는 `FAILED`로 갱신한다. UI는 job status를 polling하고 완료 후 `/api/analysis/jobs/{jobId}/result`로 결과를 조회한다.

같은 cluster/application/namespace scope에 대해 이미 `RUNNING` 분석 session이 있으면 새 Job을 만들지 않고 기존 `jobId`, `analysisId`를 재사용한다. 분석 결과 화면은 `POST /api/analysis/{analysisId}/retry`로 동일 scope 재분석을 요청할 수 있다.

분석 결과에는 `analysisDiagnostics`를 포함한다. 이 metadata는 분석 mode, 총 latency, 총 context char 수, section별 context char 수, section별 latency, fallback/error 여부를 담아 Ollama timeout 원인과 prompt 크기 문제를 운영자가 확인할 수 있게 한다.

Runbook action은 검증 명령, 원인 확인 명령, 안전한 조치 명령, destructive 명령을 구분한다. UI는 destructive action을 기본 접힘 상태로 표시하고, 각 명령에는 실행 이유(`why`)와 command type을 함께 보여준다.

Problem card는 운영자가 분석 모달을 열었을 때 가장 먼저 보는 요약 단위이다. 각 카드는 대상 리소스, severity, confidence score, fix readiness(`READY_TO_FIX`, `NEEDS_VERIFICATION`, `OBSERVE`), 근거 흐름(Resource -> Event -> Log), 관련 ConfigMap/Secret/PVC 참조, 조치 전 확인 명령, 조치 후 검증 명령을 포함한다. FailedMount처럼 이벤트 메시지에서 누락된 참조 리소스가 명확히 드러나는 경우는 `READY_TO_FIX`로 표시하고, FailedScheduling/BackOff/Unhealthy처럼 추가 검증이 필요한 경우는 `NEEDS_VERIFICATION`으로 표시한다.

Issue group과 Remediation plan은 LLM 호출 없이 backend deterministic analyzer가 생성한다. 중복 FailedMount 이벤트처럼 count가 큰 단일 원인은 하나의 issue group으로 압축하고, 관련 Pod/Event/Log/ConfigMap/Secret/PVC 참조를 함께 제공한다. Remediation plan은 issue group score 순으로 최대 5개 단계만 노출하며, 각 단계는 대상 상세 확인, 이벤트 추적, 로그 확인, 참조 리소스 확인, 조치 후 상태 확인 명령을 포함한다. destructive 명령은 이 단계에서 생성하지 않는다.

Service/Pod 포트 매핑은 deterministic analyzer에서 우선 처리한다. Service summary에는 selector와 ports를 포함하고, Pod와 workload summary에는 labels/templateLabels와 container ports를 포함한다. Analyzer는 Service selector로 대상 Pod/Workload를 찾은 뒤 `targetPort`와 container port name/number/protocol을 대조해 `port-mismatch` issue group을 생성한다. numeric targetPort는 Kubernetes에서 containerPort 선언이 필수는 아니므로 선언된 container ports와 명백히 다를 때 강한 신호로 취급하고, named targetPort 불일치는 직접 문제 후보로 취급한다. bind/listen/address already in use 계열 startup log는 별도 high-signal log와 `PORT_MAPPING_SIGNAL` evidence로 기록한다.

로그 기반 issue group은 단순 문자열 warning이 아니라 `LogInsight.category`를 기준으로 묶는다. `bind() ... permission denied`처럼 컨테이너 종료와 연결되는 previous log는 `port-startup`으로 분류해 runtime hardening warning보다 높은 우선순위로 표시한다. 운영자가 같은 화면에서 원문, 의미, 초보자 설명, 검증 명령을 확인할 수 있도록 UI는 `logIntelligence`를 Evidence Ledger보다 앞쪽에 배치한다.

Analysis comparison은 분석 완료 직전 동일 scope의 최신 성공 분석을 조회해 현재 결과 JSON에 deterministic section으로 주입한다. 첫 분석이면 `trend=BASELINE`으로 표시하고, 이전 분석이 있으면 `DEGRADED`, `IMPROVED`, `UNCHANGED` 중 하나로 요약한다. 비교 기준은 `riskScore`, `severity`, `issueGroups.groupKey`이며, `issueGroups`가 없는 과거 결과는 `problemCards`의 리소스/제목 조합을 fallback key로 사용한다.

분석 결과 UI는 Root Cause, Risk Forecast, Runbook, Log Analysis 항목에서 즉시 drill-down을 제공한다. 리소스 항목은 관련 Problem Resource/Event/Evidence/Log로 연결하고, FailedMount 계열 문제는 Pod volume, volumeMount, Event message에서 ConfigMap, Secret, PVC 참조명을 추출해 함께 표시한다.

분석 결과 UI는 초보자용과 숙련자용 모드를 제공한다. 두 모드는 같은 `analysis-result.v1` 데이터를 사용하고 backend schema를 나누지 않는다. 초보자용은 summary 직후 `analysisQuality`, `confidenceValidation`, `actionRecommendations`, `eventNoiseReduction`, `correlationMap`을 먼저 표시하고, remediation plan, command safety, evidence ledger, issue group, problem card, 상세 분석 순으로 배치한다. 이 순서는 "현재 위험도 확인 -> 이 분석을 믿고 쓸 수 있는지 확인 -> 무엇부터 검증할지 확인 -> 무엇이 반복되는지 확인 -> 상세 drill-down" 흐름을 유지하기 위한 기준이다. 숙련자용은 교육형 설명을 줄이고 Expert Fast Lane에서 `logIntelligence`, `issueGroups`, executable command, runtime metadata와 더 많은 action candidate를 먼저 보여준다.

LLM이 생성한 결론은 가능한 한 deterministic evidence section으로 검증 가능해야 한다. 새로운 분석 섹션을 추가할 때는 최소한 근거 source, beginner explanation, 검증 명령 또는 drill-down target을 함께 제공한다. UI는 Kubernetes 용어를 그대로 노출하기보다 "왜 중요한지"를 한 문장으로 설명한다.
