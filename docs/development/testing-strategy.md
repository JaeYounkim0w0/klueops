# Testing Strategy

테스트 범위는 위험도와 변경 범위에 비례하며, 실행 가능한 저장소 script와 CI 결과를 기준으로 판정한다.

## Local

- unit test
- architecture test
- related adapter test

## Pull Request

- backend test
- frontend typecheck/test
- frontend contract test: 핵심 운영 UX가 회귀하지 않는지 검증한다. 예: Cluster detail 초기 렌더링이 live Kubernetes API에 묶이지 않는지, Resource Intelligence가 raw YAML보다 먼저 표시되는지, Analysis 이동 query context가 유지되는지 확인한다.
- OpenAPI generation check
- Orval client freshness check
- Secret masking test

## Main

- Testcontainers PostgreSQL integration test
- Fabric8 mock adapter test
- kind smoke test

## Release Candidate

- kind/k3d E2E
- Helm install/upgrade/rollback test
- AI fixture regression
- masking regression
- bounded local/Pilot 기준인 500 resource와 20/50/100 동시성 검증. 고객이 별도 대형 규모 지원을 요구할 때만 합의한 object 수로 확장한다.

## 필수 원칙

- 단위 테스트는 domain rule, parser, masking, schema와 fallback을 빠르게 검증한다.
- adapter 통합 테스트는 PostgreSQL Testcontainers, Fabric8 mock/fixture와 실제 protocol 계약을 검증한다.
- API 계약 변경은 OpenAPI runtime snapshot과 Orval client drift를 함께 검증한다.
- Frontend는 component/use-case test, typecheck, production build와 핵심 Playwright flow를 사용한다.
- Live OIDC Playwright flow는 callback URL 복귀만으로 로그인을 성공 처리하지 않는다. 공통 helper로 인증 경로를 벗어난 뒤 `/api/auth/me`의 `authenticated=true`를 bounded wait하고 보호 API를 호출한다.
- AI는 고정 fixture의 deterministic rule과 LLM section/fallback을 분리해 평가한다. 근거 없는 유창한 답변을 성공으로 판정하지 않는다.
- 실환경 수용 시험은 실행하지 않은 항목을 통과로 간주하지 않고 `CONDITIONAL` 또는 `BLOCKED`로 남긴다.
- 변경·삭제 기능은 preview/dry-run, 권한 실패, 실행 성공, 사후 검증과 rollback 후보를 함께 시험한다.
- 비동기 mutation은 commit 전 미실행, rollback 시 미제출, commit 후 다중 요청의 독립 제출, executor 포화의 즉시 실패 기록과 동일 대상 중복 mutation 거부를 시험한다.
- 성능 시험은 warm-up, 데이터 규모, concurrency, 반복 수와 p50/p95/p99를 artifact에 기록한다.
- 사용자 기능 완료 전 로컬 Kubernetes 배포와 실제 OIDC 로그인을 거친 자체 브라우저 수용시험을 수행한다. 자동 Playwright와 별개로 대표 workflow를 직접 조작하고 화면·Network·Console·Backend/Runner log를 함께 확인한다.
- 브라우저 수용시험은 최소 성공 흐름, validation 실패, capability 거부, 새로고침/deep-link와 비동기 Job 이동 후 추적을 포함한다. 테스트 계정 credential은 문서·screenshot·log에 남기지 않는다.

## 테스트 피라미드

1. Local: 관련 unit, architecture와 adapter test
2. Pull Request: Backend/Frontend 전체, OpenAPI/Orval, masking와 docs gate
3. Main: PostgreSQL/Flyway, package와 Kubernetes smoke
4. Release Candidate: Helm lifecycle, OIDC/RBAC, AI corpus, resilience와 browser acceptance
5. Customer Acceptance: TLS/DNS/IdP, backup/restore, representative cluster와 운영 증빙

대규모 인벤토리의 fixture 안전장치, 실행 명령과 artifact 계약은 `large-cluster-soak.md`를 따른다.
