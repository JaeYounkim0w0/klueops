# KlueOps

> Evidence-guided Kubernetes Operations

Kubernetes 운영 자동화와 근거 기반 AI 진단·상담 기능을 제공하는 오픈소스 프로젝트입니다. 이 저장소의 목표는 특정 고객 상용 릴리스 승인이 아니라, 누구나 코드를 검토하고 로컬 또는 자체 환경에서 재현·확장할 수 있는 공개 프로젝트를 만드는 것입니다.

이 워크스페이스는 다음 프로젝트를 포함합니다.

- `backend/`: Spring Boot + Spring AI 기반 API 서버
- `frontend/`: Vue 3 + TypeScript + PrimeVue 기반 운영 UI
- `docs/`: 현재 제품 명세, 아키텍처, API, 개발·운영·보안 기준
- `scripts/`: 공통 검증 스크립트

기존 로컬 설치와 데이터 호환성을 위해 Helm release/namespace, `AIOPS_*` 환경변수, OIDC realm ID, DB 이름과 Java package의 `aiops` 식별자는 유지합니다. 사용자에게 표시되는 제품명과 새 build artifact·공개 container registry 이름은 `KlueOps`를 사용합니다.

## 개발 전 필수 확인 문서

1. `docs/product/current-product-specification.md`
2. `docs/product/remaining-development-items.md`
3. `docs/development/tech-stack.md`
4. `docs/architecture/overview.md`
5. `docs/architecture/hexagonal-architecture.md`
6. `docs/api/openapi-rules.md`
7. `docs/development/definition-of-done.md`
8. `docs/development/testing-strategy.md`
9. `docs/development/backend-source-validation.md`
10. `docs/development/documentation-standards.md`
11. `docs/security/masking-policy.md`

## 표준 검증 명령

```bash
./scripts/validate-backend.sh
./scripts/validate-frontend.sh
./scripts/validate-docs.sh
./scripts/validate-open-source-hygiene.sh
```

## 개발 원칙

- Java 17 LTS 기준으로 개발한다.
- Backend는 헥사고날 아키텍처를 따른다.
- 모든 Backend API는 OpenAPI/Swagger 문서를 제공한다.
- Frontend API client는 OpenAPI spec 기반으로 생성한다.
- 장시간 작업은 `AsyncJob` 패턴을 사용한다.
- Secret/PII는 로그, DB, AI prompt에 평문으로 남기지 않는다.
- 기능 변경 시 테스트와 관련 docs를 함께 갱신한다.

## Project governance

- 기여 방법: `CONTRIBUTING.md`
- 보안 제보와 운영 경계: `SECURITY.md`
- 오픈소스 공개 전 잔여 항목: `docs/product/remaining-development-items.md`

프로젝트는 별도의 상용 릴리스 판정을 목표로 하지 않으며, 공개 저장소의 품질·보안·재현성 검증을 기준으로 관리합니다.

## License

Copyright 2026 Jae Youn Kim.

KlueOps is licensed under the [Apache License 2.0](./LICENSE). 현재 별도 고지가 필요한 제3자 자료가 없어 `NOTICE` 파일은 두지 않으며, 향후 해당 자료가 추가되면 함께 갱신합니다.
