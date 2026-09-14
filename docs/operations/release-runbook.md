# Release Runbook

이 파일명은 기존 자동화와 링크 호환을 위해 유지한다. 프로젝트 자체는 고객 상용 릴리스 승인을 목표로 하지 않는다. 이 문서는 기능, 운영 안전성, 사용자 문서가 일치하는지 확인하는 통합 품질 절차이며 외부 환경의 운영 승인은 각 배포자가 판단한다.

## 1. 변경 확인

- PRD와 구현 범위를 비교한다.
- API 변경이 있으면 OpenAPI와 생성 클라이언트를 함께 갱신한다.
- UI 변경이 있으면 공통 CSS/컴포넌트를 우선 사용하고 사용자 가이드 원본과 Word 결과물을 갱신한다.
- PostgreSQL migration은 새 버전으로 추가하고 기존 migration을 수정하지 않는다.

## 2. 로컬 검증

```bash
./scripts/validate-docs.sh
./scripts/validate-security.sh
./scripts/validate-packaging.sh
./scripts/validate-backend.sh
./scripts/validate-frontend.sh
```

Docker/Testcontainers와 Playwright를 사용할 수 있는 runner에서 실행한다. Docker socket, preview server 포트, npm registry가 준비되지 않으면 결과를 `BLOCKED`로 기록하며 이전 성공 결과로 대체하지 않는다. Playwright 포트는 `AIOPS_E2E_PORT`로 변경할 수 있다.

1~4 큰 업무 묶음을 한 번에 판정하려면 `scripts/acceptance/run-product-readiness.sh`를 사용한다. 결과 JSON과 단계별 로그는 `artifacts/product-readiness/`에 남는다.

## 3. 통합 운영 검증

```text
login -> Tenant/Workspace -> cluster connection -> sync
      -> namespace/resource -> analysis -> evidence/log
      -> dry-run -> guarded action -> re-analysis -> audit
```

각 단계에서 실패 시 사용자가 다음 행동을 알 수 있는지 확인한다. 긴 오류는 요약과 상세를 분리하고 requestId/correlationId를 유지한다.

## 4. 배포

최초 설치는 `scripts/init`, 소스 변경 후 컴포넌트 배포는 `scripts/deploy`를 사용한다. Backend, Frontend, Keycloak은 독립된 이미지 태그와 rollout 상태를 확인한다. production에서는 HTTPS Ingress, existing Secret, PostgreSQL, OIDC 설정을 적용한다.

## 5. 선택적 배포 기록

운영 환경에 적용하는 사용자는 실행 시각, 소스 버전, 테스트 수, 주요 환경, 실패/차단 항목과 복구 담당자를 자체 기록한다. TLS/IdP, 데이터 복구와 Kubernetes/PostgreSQL/IdP HA는 프로젝트 공개 조건이 아니라 각 배포 토폴로지의 책임이다.
