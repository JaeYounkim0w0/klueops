# AI Analysis Mutation E2E Checklist

## Purpose

AI Analysis와 Applications 화면의 제한 변경 조치가 실제 Kubernetes 클러스터에서 안전하게 동작하는지 검증한다.

이 체크리스트는 Prometheus 연동 전 기준이며, Kubernetes API, Job Dock, command execution history, Evidence Ledger, post-action reanalysis를 함께 확인한다.

## Preconditions

- 테스트 전용 cluster가 등록되어 있어야 한다.
- 테스트 namespace와 Deployment가 준비되어 있어야 한다.
- Deployment는 최소 2개 이상의 rollout revision을 가져야 rollback 검증이 가능하다.
- 사용 credential은 Deployment `get`, `list`, `patch`, `scale`, ReplicaSet `list`, SelfSubjectAccessReview 권한을 가져야 한다.
- 운영 데이터가 있는 production namespace에서는 수행하지 않는다.

로컬 반복 검증은 `ai-analysis-e2e-report.md`의 `run-ai-analysis-local-e2e.sh`를 사용한다. 스크립트는 전용 namespace, TTL, 실행 소유권, 최소 권한 ServiceAccount, 정확한 확인 문구와 자동 cleanup을 강제한다.

## Scenario 1. Read-only Verification

1. AI Analysis 결과에서 read-only command를 실행한다.
2. command execution detail modal이 `SUCCEEDED`로 열리는지 확인한다.
3. Evidence Ledger에 `COMMAND_RESULT`가 추가되는지 확인한다.
4. 성공 결과 modal의 `현재 범위 재분석` 버튼으로 reanalysis job을 생성한다.

Expected:

- command history에 stdout/stderr/duration/status가 저장된다.
- reanalysis job이 Job Dock에 표시된다.
- 재분석 결과의 `commandVerification`과 `COMMAND_RESULT`에는 이전 Analysis ID가 출처로 기록된다.

## Scenario 2. Deployment Scale Guard

1. AI Analysis Safe Change Pilot에서 `kubectl scale deployment/<name> --replicas=<n> -n <namespace>`를 preview한다.
2. RBAC, dry-run이 통과하는지 확인한다.
3. confirmation text 없이 실행이 차단되는지 확인한다.
4. 정확한 confirmation text로 실행한다.
5. 상태 확인 또는 read-only command로 replica 변경을 확인한다.
6. 원래 replica 수로 되돌린다.

Expected:

- unsupported replicas 범위는 차단된다.
- 성공/차단 이력이 command execution history에 남는다.

2026-07-13 result:

- cluster: `dev-k8s`
- namespace: `default`
- deployment: `hostname-deployment`
- analysisId: `0d14daaf-56ac-4e4c-a1af-30c93f399918`
- original replicas: `3`
- blocked command: `kubectl scale deployment/hostname-deployment --replicas=2 -n default` without confirmation
- executed command: `kubectl scale deployment/hostname-deployment --replicas=2 -n default`
- recovery command: `kubectl scale deployment/hostname-deployment --replicas=3 -n default`
- final status command: `kubectl get deployment/hostname-deployment -n default`
- final status: `Deployment/hostname-deployment namespace=default status=3/3`

## Scenario 3. Application Rollback Preview And Execution

1. Applications 화면에서 대상 application을 선택한다.
2. `롤백 검토`를 연다.
3. revision 목록과 현재/대상 state diff가 표시되는지 확인한다.
4. 현재 revision은 선택할 수 없어야 한다.
5. 이전 revision을 선택하면 backend preview가 다시 호출되고 guard 결과가 갱신되는지 확인한다.
6. confirmation text를 정확히 입력한 뒤 실행한다.
7. Job Dock에서 rollback job이 `SUCCEEDED` 또는 명확한 guarded failure로 종료되는지 확인한다.
8. rollback 후 application status와 AI Analysis reanalysis를 수행한다.

Expected:

- rollback은 explicit target revision 없이는 실행되지 않는다.
- rollback guard가 실패하면 실행 버튼이 활성화되지 않는다.
- 실행 성공 후 Deployment template이 대상 ReplicaSet template 기준으로 갱신된다.

## Scenario 4. Failure Cases

다음 케이스는 모두 사용자 친화 오류 또는 guarded failure로 종료되어야 한다.

- target Deployment 없음
- target revision 없음
- target revision이 current revision과 동일
- SelfSubjectAccessReview deny
- confirmation text 불일치
- ReplicaSet revision history 없음

## Result Recording

검증 후 결과를 release evidence artifact에 기록하고, 제품 판정이 달라지면 `docs/product/current-product-specification.md` 또는 `docs/product/remaining-development-items.md`를 갱신한다.

- cluster name
- namespace
- deployment name
- original replicas/current revision
- executed action
- job id
- final status
- rollback/recovery 여부
- reanalysis result id
