# Security Validation Plan

## 자동 검증

1. 미인증 요청: 공개 endpoint 외 모든 API가 401인지 확인한다.
2. CSRF: POST/PUT/PATCH/DELETE가 token 없이 403인지 확인한다.
3. 역할 행렬: 각 role의 capability 허용/거부를 table-driven test로 검증한다.
4. 범위 격리: cluster A 사용자가 cluster B와 다른 namespace를 읽거나 변경할 수 없는지 검증한다.
5. 계정 lifecycle: JIT 생성, 재로그인 갱신, 비활성 차단을 검증한다.
6. 세션: PostgreSQL 저장, logout 폐기, timeout을 검증한다.
7. 계약: `/api/auth/me`와 관리 API가 OpenAPI snapshot에 포함되는지 검증한다.
8. 브라우저: login redirect, 세션 복원, 401/403 UX, logout을 Playwright로 검증한다.
9. 관리 안전성: 자기 계정 비활성화, 존재하지 않는 사용자, 중복 바인딩을 검증한다.
10. 성능: 동일 principal의 연속 API 요청이 사용자 DB row를 매번 갱신하지 않는지 검증한다.

## Release Gate

- `scripts/validate-security.sh`
- backend 전체 test
- frontend Vitest/typecheck/build
- OIDC 실제 provider smoke
- two-user object-scope isolation E2E
- secret scan
- `bash -n scripts/bootstrap-keycloak-dev.sh`와 identity compose config

하나라도 실패하면 다중 사용자 상용 판정을 내리지 않는다.

## Managed Keycloak Live Gate

`scripts/validate-managed-keycloak-live.sh`는 명시적으로 `AIOPS_LIVE_IDENTITY_ENABLED=true`를 설정하고 대상 Kubernetes context가 정확히 일치할 때만 실행한다. 그 외에는 exit code `3`으로 SKIPPED 처리하여 알 수 없는 클러스터에 테스트 리소스를 만들지 않는다.

검증 범위는 Kubernetes Pod에서 외부 PostgreSQL 연결, Realm discovery, 실제 platform administrator/operator/viewer 로그인, 서버측 cluster/namespace 범위 격리, viewer 변경 거부와 logout session 폐기다. 라이브 실행에서는 Playwright trace, video, screenshot을 비활성화하여 비밀번호·token·cookie가 산출물에 남지 않도록 한다.

## Two-Tenant Acceptance

`AIOPS_COMMERCIAL_TENANT_VALIDATION=true ./scripts/validate-commercial-tenant-isolation.sh`는 임시 Keycloak 사용자와 두 Tenant/Workspace/Cluster fixture를 만든다. list API뿐 아니라 Tenant, Workspace, Cluster 직접 URL, 분석 이력, viewer 삭제 거부, platform audit 접근까지 확인하고 자신이 만든 fixture만 정리한다.

2026-09-03 로컬 all-in-one 환경에서 통과했으며 결과는 `artifacts/acceptance/commercial-tenant-isolation.json`과 JUnit XML에 credential을 `REDACTED`로 기록한다. 고객 production 승인에서는 같은 시나리오를 고객 IdP, DNS, TLS와 실제 scope 정책으로 다시 실행한다.
