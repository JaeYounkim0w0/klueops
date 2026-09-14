# Release Candidate Checklist

> 이 파일과 `validate-release-candidate.sh` 명칭은 기존 자동화 호환을 위해 유지한다. 현재 프로젝트의 목적은 오픈소스 공개이며 고객 상용 릴리스 승인을 목표로 하지 않는다. 아래 자동 검증은 merge 전 통합 품질 확인에 사용하고, production/customer 항목은 배포자가 필요할 때 선택적으로 수행한다.

## Automated gate

저장소 루트에서 다음 명령을 실행한다.

```bash
./scripts/validate-release-candidate.sh
```

검증 단계:

1. 오픈소스 tree 위생, 문서 구조와 필수 기준
2. 초기화/컴포넌트 배포 lifecycle 계약
3. backend 전체 test와 architecture
4. OpenAPI annotation/runtime contract
5. Orval generated client drift
6. frontend typecheck, Vitest, production build
7. Playwright operator smoke
8. Docker/Compose와 Backend/Frontend all-in-one Helm packaging contract
9. Managed Keycloak/external OIDC Helm/Secret/bootstrap contract
10. 임시 backend readiness
11. running backend OpenAPI snapshot drift
12. deterministic AI quality gate
13. 선택 실행 환경 runtime convergence
14. Backend/Frontend SBOM과 packaged runtime dependency audit
15. 선택 실행 환경 Backend/Keycloak restart, DB 격리 복구, read p95, Ollama fallback
16. AI Analysis operator acceptance report schema와 6개 시나리오 증빙
17. 1~4 큰 업무 묶음 통합 readiness 실행과 JSON artifact

실패 로그와 브라우저 artifact는 `artifacts/rc`, `frontend/test-results`, `frontend/playwright-report`에서 확인한다.

## PostgreSQL gate

- [x] local 기본 PostgreSQL로 backend가 기동되고 H2 dependency가 존재하지 않는다.
- [x] Testcontainers PostgreSQL에서 전체 Backend test와 Flyway V1-V24가 통과한다.
- [x] PostgreSQL 17에서 Flyway migration V1-V24가 적용되고 재기동 시 validate된다.
- [x] `/actuator/health/readiness`가 `UP`이다.
- [x] runtime readiness의 `DATABASE_DURABILITY`가 `READY`다.
- [x] backend 재시작 후 동일 AI release-gate 이력 ID가 유지된다.
- [x] backup dump를 별도 임시 database에 복구하고 migration과 public table을 확인했다. 최신 schema 기준은 28이다.

## Operator acceptance

- [x] cluster 목록과 상세 resource가 테스트 cluster에서 열린다.
- [x] cluster/namespace 변경 시 analysis history가 scope를 벗어나 섞이지 않는다.
- [x] 분석 실행, Job Dock 진행 시간, 결과 상세, 재시도가 연결된다.
- [x] Runbooks 제목, 긴 evidence, command safety가 desktop에서 잘리지 않는다.
- [x] 한국어/영어 전환 시 제목과 설명이 번역되고 resource/log/command 원문은 유지된다.
- [x] `scripts/acceptance/validate-ai-analysis-e2e-report.sh`가 A-1~A-6 로컬 synthetic 실환경 증빙을 모두 `PASSED`로 판정한다.
- [x] A-6 조치 후 재분석 비교에는 이전/현재 analysis ID와 command verification evidence가 함께 남는다.
- [x] `scripts/acceptance/run-product-readiness.sh` 결과 JSON에서 CORE/COMMERCIAL/QUALITY/DOCS가 모두 `PASSED`다.

## Optional deployment environment sign-off

다음은 오픈소스 프로젝트 완료 기준이 아니라 실제 운영 배포자가 결정할 항목이다.

- Prometheus/metrics-server 기반 성능 evidence
- 고객 환경 TLS/DNS, secret manager와 disaster recovery 실행 증적. 고객 인프라 HA는 별도 배포 승인 범위

OIDC BFF, scope RBAC와 all-in-one 패키지 코드는 완료됐고 로컬 두 Tenant object-scope 증적도 확보했다. 외부 환경의 TLS/IdP, Keycloak DB 복구와 배포 artifact 검증은 해당 배포 운영자의 책임이며 오픈소스 공개 잔여 작업으로 계산하지 않는다.

## Optional persistent deployment evidence

```bash
AIOPS_RELEASE_NAME=rc-2026.09 \
AIOPS_EVIDENCE_ENVIRONMENT=customer-staging \
AIOPS_BACKEND_URL=https://aiops.example.com \
AIOPS_KEYCLOAK_URL=https://auth.aiops.example.com \
AIOPS_DATABASE_HOST=postgres.internal \
./scripts/acceptance/collect-production-evidence.sh
```

- [ ] Backend, OIDC, PostgreSQL, Kubernetes read access와 supply-chain check가 모두 `PASSED`다.
- [ ] 생성 JSON을 인증된 `POST /api/operations/production-evidence/runs`로 가져왔다.
- [ ] Operations Reliability 화면에서 실패/차단/만료 상태와 check 상세를 검토했다.
- [ ] 실행하지 않은 check를 통과로 간주하지 않았다.

## All-In-One Package Gate

- [x] Backend/Frontend Deployment, Service, probe, resource, read-only filesystem
- [x] release별 Frontend nginx -> Backend BFF single-origin proxy
- [x] Portal PostgreSQL/OIDC/master-key existing Secret 참조
- [x] productionMode HTTPS, TLS ingress, Secure cookie fail-fast
- [x] Managed Keycloak와 external OIDC 렌더 계약
- [x] Bundled Managed Keycloak은 단일 replica 편의형 프로필이며 인증 HA는 external OIDC 경계로 분리
- [x] Backend/Frontend PDB와 rolling update 기본값
- [ ] 고객 ingress controller에서 login/callback/logout 실제 검증
- [x] 로컬 clean install/upgrade와 앱 DB 격리 restore rehearsal
- [x] 최초 설치와 Backend/Frontend/Keycloak 반복 배포 명령을 별도 lifecycle로 분리
- [x] Backend, Frontend, Keycloak 개별 배포에서 대상 외 Deployment image가 유지됨
- [ ] production rollback/uninstall 후 restore rehearsal

## Managed Identity Gate

- [x] optimized Keycloak image와 Helm render/security contract
- [x] PostgreSQL 17 disposable container DB bootstrap 멱등성/owner mismatch 검증
- [x] Keycloak 26.7.3 disposable container Realm/client/mapper/group bootstrap 2회 검증
- [x] public issuer와 internal transport endpoint Backend test
- [x] 로그인 실패 상태 한국어/영어 UX
- [x] 실제 Kubernetes Pod에서 PostgreSQL 연결
- [x] 실제 platform-admin/cluster-admin/operator/viewer browser 역할 및 API 권한 경계
- [x] 두 Tenant/두 cluster fixture의 cluster/namespace/object-scope 격리
- [x] Keycloak rolling restart와 Realm 지속성
- [x] Keycloak database 격리 restore rehearsal

## Resilience And Supply Chain

- [x] Backend와 Keycloak rolling restart 후 Portal 회복
- [x] 앱 PostgreSQL dump를 임시 database에 복구하고 적용 migration 일치
- [x] Keycloak PostgreSQL dump를 격리 database에 복구하고 `aiops` Realm sentinel 일치
- [x] guarded Helm revision 생성 후 원 revision rollback과 image 동일성 확인
- [ ] 실제 배포 image digest의 Cosign identity/issuer 검증
- [x] 로컬 Pilot 기본 bounded namespace(500 resources, 동시성 20/50/100) AI 분석 soak의 성공률/폴백률 기준 통과
- [x] Ollama timeout deterministic fallback
- [x] read API 30회, 동시성 10, p95 14ms
- [x] Backend/Frontend SBOM 생성
- [x] packaged Frontend production dependency High 0/Critical 0
- [ ] container image OS CVE scan, digest pin, signature verification
- [ ] 개발 도구 dependency major upgrade와 전체 npm audit High/Critical 해소

## Latest automated evidence

### 2026-09-11 로컬 릴리스 리허설

- Docker Desktop `docker-desktop` context의 `aiops-system`에서 guarded Helm rollback 통과: revision 52에서 exercise revision 53을 생성한 뒤 rollback revision 54로 복귀했고 네 Deployment image identity가 유지됨 (`artifacts/production-evidence/helm-rollback.json`)
- Backend/Keycloak 순차 재시작 후 Portal 회복, 앱 DB Flyway migration 28건과 Keycloak `aiops` Realm 1건의 격리 restore, Ollama timeout fallback 통과. read API 30회/동시성 10 p95 14ms (`artifacts/resilience/result.json`)
- Backend/Frontend CycloneDX SBOM 재생성, Frontend production dependency High 0/Critical 0, 실제 배포 Pod image digest 기록과 signed release workflow 계약 검증 통과 (`artifacts/supply-chain/result.json`)
- 실제 운영 registry image의 Cosign identity/issuer 및 OS CVE 검증은 로컬 도구·운영 registry 증빙이 없어 미완료로 유지
- A-1~A-6 로컬 synthetic 수용시험 6/6 및 재분석 command evidence 연결 통과 (`artifacts/acceptance/ai-analysis-e2e-20260911072724-45137.json`)
- 500 ConfigMap, 동시성 20/50/100 bounded soak 통과. 이는 로컬 Pilot 기본 기준이며 고객이 더 큰 규모를 요구하면 별도 SLO를 합의한다.
- 격리 Backend에서 발견한 OpenAPI snapshot 누락 7개 필드를 동기화하고 Orval client를 재생성한 뒤 전체 RC gate를 113초에 통과
- CORE/COMMERCIAL/QUALITY/DOCS 전체 제품 준비성 gate 통과 (`artifacts/product-readiness/product-readiness-20260911T075600Z.json`)
- OpenAPI/Orval 계약 동기화 후 Frontend를 재배포하여 Helm release `aiops` 최종 revision 55, Backend/Frontend/Managed Keycloak/Command Runner 모두 `1/1 Ready`

### 2026-09-07 재검증

- 직접 Backend Maven: 169 tests, failures 0, errors 0, skipped 0
- Frontend: Vitest 78 tests, typecheck와 production build passed
- OpenAPI 직접 계약 테스트, architecture, docs, generated client, packaging, security, maintainability, component lifecycle, AI schema passed
- RC wrapper의 Testcontainers 하위 단계는 현재 shell에서 Docker socket을 찾지 못해 `CONDITIONAL`; 직접 Maven 실행에서는 Docker/Testcontainers가 연결되어 통과
- Playwright preview server는 `4174` bind 권한 부족으로 `BLOCKED`; 실제 acceptance runner에서 재실행 필요
- npm registry DNS 차단으로 최신 supply-chain audit JSON을 받지 못해 `BLOCKED` 유지
- 현재 기준 판정: `docs/product/current-product-specification.md`, 잔여 환경 증빙: `docs/product/remaining-development-items.md`
- 1~4 큰 업무 묶음 재실행: Frontend 78 tests/typecheck/build, security/docs/packaging 통과. Backend/OpenAPI는 Docker/Testcontainers socket 미노출로 `CONDITIONAL`; 실제 qa-2/ops-01 A-1~A-6는 인증된 운영 세션과 증빙 입력 후 재실행한다.

- 2026-09-03 full RC, managed identity package/security/docs gates: passed
- backend: 최종 RC 실행 결과를 `artifacts/rc/backend.log`와 테스트 리포트에서 확인
- frontend: 73 tests, typecheck와 production build passed
- OpenAPI: 131 paths/161 schemas, generated client drift passed
- PostgreSQL DB bootstrap: PostgreSQL 17에서 2회 실행 및 owner mismatch 안전 중단 passed
- Realm bootstrap: Keycloak 26.7.3에서 2회 실행 및 client/mapper/group/user 검증 passed
- managed Keycloak optimized image: local Docker build passed
- Kubernetes live identity base: Docker Desktop `v1.34.1`, Helm release `aiops` deployed, Keycloak Pod `Ready`
- Kubernetes Pod -> workstation PostgreSQL `5432`: passed (당시 로컬 환경 주소는 공개 기본값에 포함하지 않음)
- Realm discovery, four standard groups and initial platform administrator membership: passed
- Keycloak Pod restart and Realm/group/user persistence: passed
- Local public NodePort `30080`, canonical issuer and admin redirect: passed
- Local `/etc/hosts` mapping and Keycloak administrator login page rendering: passed
- Local managed Keycloak RBAC Playwright: 5 tests passed
- Latest local Frontend/Backend readiness/session runtime convergence: passed
- 네 개 표준 그룹의 exact capability mapping과 platform scope: passed
- platform administrator 전용 `/api/security/users` 경계: admin `200`, 나머지 역할 `403`
- viewer의 유효한 CSRF token을 사용한 cluster 생성 시도: `403`
- BFF logout 후 기존 session 무효화: passed
- 최초 로그인 JIT provisioning 8-way 동시성 및 DB lock: passed
- PostgreSQL schema `V17__identity_provision_locks.sql` 및 V1-V23 전체 적용: passed
- Two-Tenant object-scope isolation: passed, redacted JSON/JUnit artifact 생성
- Operational resilience: read p95 20ms, Backend/Keycloak restart, app DB isolated restore, AI fallback passed
- Full RC after component lifecycle split: Backend 128 tests, Frontend 54 tests, browser smoke 3 passed/9 credential-gated skipped, OpenAPI/Orval/Helm/SBOM/AI gate passed in 55s
- Supply chain: Backend/Frontend SBOM과 production dependency High 0/Critical 0
- Component lifecycle: Backend revision 12, Frontend revision 13, Keycloak revision 14 개별 배포와 runtime convergence passed
- 2026-09-04 PostgreSQL 단일화 재검증: Backend 166 tests, Frontend 73 tests/build, Playwright 10 passed/18 credential-gated skipped, packaged JAR + isolated PostgreSQL readiness, OpenAPI/Orval과 AI release gate passed
- 2026-09-04 온라인 npm audit: registry가 제한시간 안에 응답하지 않아 fail-closed `BLOCKED`; 이전 High/Critical 0 증적을 새 실행 통과로 대체하지 않음
- 2026-09-04 상용 마감 코드 검증: Backend 166 tests, Frontend 73 tests/typecheck/build, Flyway V1-V23, maintainability cap 통과
- 공개 URL은 workstation IP를 values에 고정하지 않고 `AIOPS_PUBLIC_HOST` 또는 explicit HTTPS Portal/issuer URL로 파생한다.
- `validate-production-transport.sh`, `run-analysis-soak.sh`, `rehearse-helm-rollback.sh`, `validate-operational-resilience.sh` 결과가 evidence manifest에 포함되며 누락/실패는 production 승인을 차단한다.
