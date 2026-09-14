# AI Analysis E2E Verification Report

## Release Gate Automation

`./scripts/validate-ai-release-gate.sh`는 backend의 deterministic 8-case regression과 ground-truth threshold를 하나의 출고 판정으로 실행한다. 기본 기준은 regression score 90, ground-truth/verified accuracy 0이며 실제 운영 후보에서는 충분한 operator feedback 표본을 확보한 뒤 두 기준을 상향한다.

```bash
AIOPS_BACKEND_URL=http://127.0.0.1:8080 \
AIOPS_AI_CANDIDATE_VERSION=rc-2026.09 \
AIOPS_AI_MIN_REGRESSION_SCORE=90 \
./scripts/validate-ai-release-gate.sh
```

`BLOCKED` 응답, HTTP 오류, 파싱 실패는 모두 non-zero exit code로 RC를 차단한다. LLM의 자연어 품질은 별도 operator feedback/ground truth로 측정하며 deterministic safety fixture를 대체하지 않는다.

## Machine-checkable operator report

실제 `qa-2/nginx`와 `ops-01/nginx` 수용 시험은 아래 템플릿을 복사해 기록한다.

```bash
cp docs/development/ai-analysis-e2e-report.template.json /tmp/ai-analysis-e2e-report.json
AIOPS_AI_E2E_REPORT=/tmp/ai-analysis-e2e-report.json \
  ./scripts/acceptance/validate-ai-analysis-e2e-report.sh
```

검증기는 A-1부터 A-6까지의 시나리오 ID, 허용 상태, cluster/namespace, 증빙 배열을 검사한다. `PENDING`, `FAILED`, `BLOCKED`, 누락된 증빙은 모두 non-zero로 종료하므로 실환경 시험 전의 템플릿을 릴리즈 통과로 사용할 수 없다. `PASSED`로 표시한 항목은 `analysisId` 또는 `jobId`와 Kubernetes API 응답, command execution, re-analysis 비교 등 사람이 재현 가능한 증빙을 `evidence` 배열에 남긴다.

## Local synthetic acceptance

Docker Desktop의 로컬 Helm 배포에서는 다음 명령으로 A-1~A-6 제품 흐름을 반복 검증한다. 기본 비활성화 상태이며 정확한 확인 문구가 없으면 fixture를 만들지 않는다.

```bash
AIOPS_AI_E2E_ENABLED=true \
AIOPS_AI_E2E_CONFIRMATION='CREATE AND MUTATE AI ANALYSIS E2E FIXTURES' \
  ./scripts/acceptance/run-ai-analysis-local-e2e.sh
```

자동화는 다음 안전 경계를 사용한다.

- 현재 context가 승인된 `docker-desktop`과 같은지 확인한다.
- `aiops-e2e-*` 전용 namespace, run ownership label과 최대 45분 TTL을 사용한다.
- 활성 fixture 중복 실행을 차단하고 만료 fixture만 idempotent하게 정리한다.
- namespace 전용 ServiceAccount와 1시간 토큰을 발급한다. Secret 읽기는 허용하지 않으며 부분 수집 상태를 그대로 유지한다.
- 임시 Platform Admin과 임시 cluster registration을 사용하고 성공·실패와 관계없이 삭제한다.
- OIDC callback 뒤 공통 browser helper가 `/api/auth/me`의 인증 완료를 확인한 다음 첫 보호 API를 호출한다.
- 권한 포트 시작 실패, Service targetPort 불일치, 미확인 restart 차단, 확인 후 restart, 명시 revision rollback, revision 누락 차단과 재분석 비교를 검증한다.

결과는 `artifacts/acceptance/ai-analysis-e2e-<runId>.json`에 저장한다. `environment=local-kubernetes-synthetic`은 로컬 제품 회귀 증빙이며 고객 staging의 TLS, IdP, 실제 RBAC 계정과 원격 cluster 수용을 대체하지 않는다.

## 2026-07-16 dev-k8s Sync and Control Plane

- Cluster: `dev-k8s` (`4a642cc1-65ae-4b6e-b558-98810b2a692c`)
- Sync Job: `989b222e-75bb-4dee-bc43-f31d8967678a`
- Result: `SUCCEEDED`, 760ms, resources 114, events 0, namespaces 5
- Inventory page: page size 20 기준 total 114, total pages 6, problem 1
- Namespace `default`: total 15, Pod 6, problem 1
- UI: 전체 scope 100/114 lazy load, `default` scope 15/15 server filter 확인
- Reconcile: 최신 성공 sync inventory 기준 성공. 기존 `configmap-db-pod` Incident는 마지막 상태 `Pending`, recovery `0/2`, state `OPEN` 유지
- 발견 및 수정: 과거 snapshot 혼합으로 baseline natural key가 충돌하던 문제를 최신 sync inventory 조회와 natural-key upsert로 해소

현재 file-based H2에는 `dev-k8s`만 등록되어 있으므로 `qa-2/nginx`, `ops-01/nginx` 실환경 검증은 재등록 후 수행한다.

기준 범위: Unit A, Unit C, Unit D

제외 범위: PostgreSQL profile, Prometheus/metrics integration, final security/RBAC/multi-user hardening

## 1. Objective

AI Analysis가 실제 Kubernetes 장애 namespace에서 원인을 찾고, 검증 명령과 제한 조치를 제공하고, 조치 후 재분석으로 결과 변화를 비교할 수 있는지 검증한다.

검증 대상:

- `qa-2/nginx`
- `ops-01/nginx`

대표 장애:

- privileged port startup failure
- Service targetPort/containerPort mismatch
- rollout restart
- explicit revision rollback
- rollback guard failure

## 2. Preconditions

- Backend local profile uses file-based H2.
- Frontend is running on `http://127.0.0.1:5173`.
- Backend is running on `http://127.0.0.1:8080`.
- Ollama is reachable from backend.
- Target clusters are registered and synced from the Clusters screen.
- The target namespace has recent resource/event snapshots.

## 3. Scenario Matrix

| ID | Cluster | Namespace | Scenario | Expected AI Signal | Expected Operator Action |
| --- | --- | --- | --- | --- | --- |
| A-1 | qa-2 | nginx | privileged port startup failure | `logIntelligence` contains `port-startup` and privileged port guidance | inspect previous logs, validate securityContext/capability, move app port or add explicit capability after review |
| A-2 | ops-01 | nginx | port-related startup/routing issue | `actionRecommendations` includes preflight and validation commands | compare Service targetPort, containerPort, Endpoint readiness |
| A-3 | qa-2 | nginx | rollout restart | command preview passes RBAC/dry-run guard and requires confirmation | execute restart, verify rollout event and pod replacement |
| A-4 | qa-2 | nginx | rollback with explicit revision | rollback guard passes only with explicit `--to-revision` | execute rollback only after revision diff review |
| A-5 | qa-2 | nginx | rollback without target revision | preview blocks execution | do not execute, select explicit revision first |
| A-6 | qa-2 | nginx | post-action retry analysis | `analysisComparison` shows improved/degraded/unchanged state | compare issue groups, command verification, event timeline |

## 4. Verification Checklist

### 4.1 Baseline Analysis

- [ ] Select cluster.
- [ ] Select namespace.
- [ ] Run Namespace AI Analysis.
- [ ] Confirm Job Dock shows elapsed time, section/runtime information, and failure hints if applicable.
- [ ] Confirm analysis result includes:
  - [ ] `analysisQuality`
  - [ ] `logIntelligence`
  - [ ] `actionRecommendations`
  - [ ] `issueGroups`
  - [ ] `evidenceLedger`
  - [ ] `commandSafety`
  - [ ] `analysisDiagnostics`

### 4.2 Log Intelligence Accuracy

- [ ] Previous container logs are checked when current logs are insufficient.
- [ ] `bind() to 0.0.0.0:80 failed (13: Permission denied)` is classified as privileged port startup failure.
- [ ] The UI does not misclassify Flask `Debug mode: off` as debug mode enabled.
- [ ] Beginner mode explains the cause without requiring Kubernetes expertise.
- [ ] Expert mode exposes commands and evidence densely.

### 4.3 Safe Command Execution

- [ ] Read-only commands execute and store stdout/stderr/duration/status.
- [ ] Unsupported destructive commands are blocked.
- [ ] Deployment restart requires confirmation text.
- [ ] Scale is limited to allowed Deployment replica range.
- [ ] Rollback requires explicit target revision.
- [ ] Rollback guard displays current/target state diff.

### 4.4 Closed-loop Reanalysis

- [ ] Execute an allowed action.
- [ ] Open command execution detail.
- [ ] Trigger reanalysis from the same scope.
- [ ] Confirm `commandVerification` includes command result evidence.
- [ ] Confirm `conclusionValidation` reflects command result.
- [ ] Confirm `analysisComparison` shows previous/current delta.

### 4.5 Application Operations

- [ ] Application status sync maps Deployment snapshot to RUNNING/DEGRADED/UNKNOWN.
- [ ] Application restart creates an AsyncJob and appears in Job Dock.
- [ ] Application rollback preview shows revisions and guard state.
- [ ] Application detail shows recent operation timeline.
- [ ] Application detail links back to application/namespace AI Analysis.

## 5. Result Log

| Time | Tester | Cluster | Namespace | Scenario ID | Result | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| TBD | TBD | qa-2 | nginx | A-1 | TBD | analysisId/jobId/log signal |
| TBD | TBD | ops-01 | nginx | A-2 | TBD | analysisId/jobId/service endpoint evidence |
| TBD | TBD | qa-2 | nginx | A-3 | TBD | commandExecutionId/jobId |
| TBD | TBD | qa-2 | nginx | A-4 | TBD | rollback preview/execution result |
| TBD | TBD | qa-2 | nginx | A-5 | TBD | blocked preview result |
| TBD | TBD | qa-2 | nginx | A-6 | TBD | previous/current analysis comparison |

## 6. Completion Criteria

Unit A/C/D are considered complete for the current non-Prometheus, non-PostgreSQL, non-final-RBAC scope when:

- The above backend and frontend regression tests pass.
- The UI exposes retry/cancel/failure detail paths for long-running analysis jobs.
- Applications screen exposes status sync, restart, rollback preview/execution, action timeline, and AI Analysis navigation.
- The report can be filled from real cluster verification without adding more product workflow.
