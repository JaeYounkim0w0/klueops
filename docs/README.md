# Documentation Guide

이 디렉터리는 현재 동작하는 제품, 개발 규칙, 아키텍처, API, 보안과 운영 절차만 관리한다. 날짜별 요구사항·계획·진행 일지는 제품 기준 문서에 반영한 뒤 보관하지 않는다.

## 먼저 읽을 문서

1. [현재 제품 통합 명세](product/current-product-specification.md)
2. [오픈소스 공개 및 잔여 개발](product/remaining-development-items.md)
3. [기술 스택](development/tech-stack.md)
4. [전체 아키텍처](architecture/overview.md)
5. [헥사고날 아키텍처](architecture/hexagonal-architecture.md)
6. [API 작성 규칙](api/api-guidelines.md)
7. [Definition of Done](development/definition-of-done.md)
8. [테스트 전략](development/testing-strategy.md)
9. [문서 관리 규칙](development/documentation-standards.md)
10. [대규모 cluster 부하·soak 시험](development/large-cluster-soak.md)

## 문서 그룹

| 폴더 | 책임 |
| --- | --- |
| `product/` | 현재 제품 명세와 잔여 항목의 단일 기준 |
| `architecture/` | 시스템 경계, 배포 구조, 데이터와 리팩터링 기준 |
| `api/` | OpenAPI와 API 설계 규칙 |
| `development/` | 기술 스택, 코딩·테스트·문서화 규칙 |
| `features/` | 아직 독립 설명이 필요한 대형 기능의 현재 설계·계약 |
| `operations/` | 설치, 배포, 복구와 선택적 환경 검증 절차 |
| `security/` | 인증, 권한, Secret, masking과 보안 검증 |
| `adr/` | 되돌리기 어려운 기술 결정과 그 근거 |
| `user-guide/` | 운영자용 Word 가이드와 생성 소스 |

## 설치와 운영 검증

- 최초 설치: [Initial Installation](operations/initial-installation.md)
- 변경 배포: [Component Deployment](operations/component-deployment.md)
- 외부 접근: [Public URL and Client Access](operations/public-url-and-client-access.md)
- 통합 품질 검증: [Release Candidate Checklist](operations/release-candidate-checklist.md). 파일명과 스크립트명은 기존 자동화 호환을 위해 유지한다.
- 제품 준비 실행: [Product Readiness Runbook](operations/product-readiness-runbook.md)
- 사용자 안내서: [User Guide](user-guide/README.md)

저장소 공개와 외부 기여 규칙은 루트 `SECURITY.md`, `CONTRIBUTING.md`를 함께 따른다. 이 프로젝트는 고객 상용 릴리스 승인을 목표로 하지 않으며, 루트 `LICENSE`가 확정돼야 오픈소스 저장소로 공개할 수 있다.

## 변경 규칙

- 구현된 제품 동작은 `product/current-product-specification.md`에 반영한다.
- 아직 남은 오픈소스 공개 준비와 제품 개선은 `product/remaining-development-items.md`에만 둔다.
- 사용자 기능 변경은 Markdown, API 문서, 테스트와 Word 사용자 가이드를 같은 변경에서 갱신한다.
- 오래된 PRD, 날짜별 계획서, 진행 보고서를 새로 만들지 않는다.
- 문서 링크와 필수 파일은 `./scripts/validate-docs.sh`로 검증한다.
- 공개 tree의 로컬 주소·경로·credential 및 생성물 추적 여부는 `./scripts/validate-open-source-hygiene.sh`로 검증한다.
