# Large-cluster Load and Soak

이 문서는 Kubernetes object 규모와 Portal 동시 작업을 재현하는 수용 시험 계약이다. 기본 제품 검증은 500 resource·동시성 20/50/100이며, 결과가 없는 더 큰 규모를 지원한다고 판정하지 않는다.

## 시험 범위

`run-large-cluster-local-soak.sh`는 실행마다 전용 `aiops-soak-*` Namespace, ServiceAccount, 읽기 전용 ClusterRole/Binding과 임시 Keycloak 관리자를 만든다. 합성 ConfigMap으로 인벤토리 크기를 만들고 다음 경로를 인증된 Portal 세션에서 측정한다.

- cluster 연결과 full inventory sync
- 동시 sync 요청의 Job 중복 제거
- resource pagination의 단계별 p50/p95/p99와 성공률
- namespace AI context assembly, 동시 요청 중복 제거와 fallback
- 비동기 Job 취소
- resource log SSE 연결/해제 storm
- PostgreSQL transaction 수와, 사용 가능한 경우 `pg_stat_statements` query 수

종료 시 Portal cluster 등록, 임시 Keycloak 사용자, ClusterRole/Binding, Namespace와 token/CA 임시 파일을 정리한다. 활성 fixture가 있으면 중복 실행을 거부하고, TTL이 지난 소유권 label fixture만 정리한다.

## 안전 계약

- 기본 비활성화: `AIOPS_LARGE_CLUSTER_SOAK_ENABLED=true`가 필요하다.
- 현재 context가 `docker-desktop`인지 확인한다. 다른 승인 context는 `AIOPS_SOAK_EXPECTED_CONTEXT`로 명시한다.
- profile, context와 resource 수를 포함한 exact confirmation이 필요하다.
- `smoke`는 100~1,000 resource만 허용한다.
- 선택적인 `large`는 5,000 또는 20,000 resource만 허용하고 동시성 20/50/100을 모두 요구한다.
- TTL은 600~7,200초, 생성 batch는 50~500으로 제한한다.
- 합성 resource는 ConfigMap이므로 object inventory와 저장소 부하는 재현하지만 같은 수의 Pod scheduling 부하는 의미하지 않는다.

## 로컬 안전 스모크

```bash
AIOPS_LARGE_CLUSTER_SOAK_ENABLED=true \
AIOPS_LARGE_CLUSTER_SOAK_CONFIRMATION='RUN smoke SOAK ON docker-desktop WITH 500 RESOURCES' \
./scripts/acceptance/run-large-cluster-local-soak.sh
```

기본 동시성은 20/50/100이고 임계치는 pagination p95 1,500ms, 분석 완료 180초, 성공률 95%다. 환경별 합의 값은 `AIOPS_SOAK_PAGE_P95_MS`, `AIOPS_SOAK_SYNC_P95_MS`, `AIOPS_SOAK_MIN_SUCCESS_RATIO`로 명시한다.

## 고객 요구 시 선택하는 확장 프로파일

5천 resource 예시는 다음과 같다.

```bash
AIOPS_LARGE_CLUSTER_SOAK_ENABLED=true \
AIOPS_SOAK_PROFILE=large \
AIOPS_SOAK_RESOURCE_COUNT=5000 \
AIOPS_SOAK_CONCURRENCY=20,50,100 \
AIOPS_LARGE_CLUSTER_SOAK_CONFIRMATION='RUN large SOAK ON docker-desktop WITH 5000 RESOURCES' \
./scripts/acceptance/run-large-cluster-local-soak.sh
```

2만 resource는 resource count와 confirmation의 `5000`을 `20000`으로 바꾼다. 이 프로파일은 현재 제품의 필수 완료 조건이 아니며 고객이 bounded 기준을 넘는 지원 규모를 요구할 때만 실행한다. validator는 `pg_stat_statements` query count가 없으면 실패하고 DB 설정을 자동 변경하지 않으므로 시험 환경에서 extension과 운영 SLO를 먼저 합의해야 한다.

## 결과와 판정

결과는 `artifacts/acceptance/large-cluster-soak-<profile>-<resource-count>-<run-id>.json`에 생성되고 `validate-large-cluster-soak-report.sh`가 계약을 검사한다. artifact에는 source image, fixture 크기, 동시성, Job ID 수, terminal status, latency percentile, fallback/cancel/SSE 결과, DB counter와 제한사항이 포함된다.

2026-09-11 로컬 기준 최신 안전 스모크는 500 ConfigMap, 동시성 20/50/100에서 통과했다. pagination p95는 632ms, AI 분석 완료는 53.5초였고 동기화와 AI 요청은 각각 100개가 1 Job으로 중복 제거됐다. 이 증빙은 현재 bounded local/Pilot 기준이며, 별도 대형 cluster 지원 승인으로 사용하지 않는다.
