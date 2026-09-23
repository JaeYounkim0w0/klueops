# 프로젝트 프로필: KlueOps

확인 기준일: 2026-09-21. 아래는 저장소 파일을 조사한 결과이며 실환경 수용시험 완료 선언이 아니다. 새 프로젝트에서는 이 파일을 [이식 절차](adoption.md)에 따라 교체한다.

## 목적과 구성

Kubernetes 근거 기반 운영 플랫폼이다. 리소스·이벤트·로그 조회, AI 분석/대화, Helm application delivery, Incident와 kubectl 콘솔을 제공한다.

| 경로 | 역할 |
| --- | --- |
| `backend/` | Java/Spring Boot BFF, 업무 처리·API·외부 adapter |
| `command-runner/` | 별도 Spring Boot 서비스, kubectl argv 실행과 NDJSON 출력 |
| `frontend/` | Vue 운영 화면과 nginx same-origin proxy |
| `deploy/helm/` | Kubernetes 배포 chart/values |
| `keycloak/` | 관리형 IdP 관련 구성 |
| `scripts/` | 개발·계약·문서·배포·공급망 검증 |
| `.github/workflows/` | 품질 gate, 공급망, 서명 이미지 release |

기본 Java package는 `io.product.aiops`, 환경 변수 접두사는 `AIOPS_`이다. 실제 세부 경로는 소스에서 확인하며 이 이름을 새 프로젝트에 그대로 강제하지 않는다.

Helm 기본 구성은 Frontend·Backend·Keycloak·Command Runner의 네 Deployment다. PostgreSQL과 Ollama는 외부 서비스다. 루트 Compose는 PostgreSQL·Backend·Frontend 구성으로 Helm 토폴로지 전체와 같지 않다.

## 확인한 버전

| 기술 | 선언 또는 해석 버전 | 근거 |
| --- | --- | --- |
| Java / Spring Boot | 17 / 3.5.16 | [Backend POM](../../backend/pom.xml), [Runner POM](../../command-runner/pom.xml) |
| Spring AI / Fabric8 / springdoc | 1.1.7 / 7.8.0 / 2.8.17 | Backend POM |
| PostgreSQL / JDBC driver | 17-alpine 이미지 / 42.7.12 | [Compose](../../compose.yaml), Backend POM |
| Maven / Helm CLI | 3.9.11 / 3.19.0 이미지 | [Backend Dockerfile](../../backend/Dockerfile) |
| Node / nginx | 22-alpine / 1.31.5-alpine3.24 unprivileged 이미지 | [Frontend Dockerfile](../../frontend/Dockerfile) |
| kubectl | v1.34.11 | [Runner Dockerfile](../../command-runner/Dockerfile) |
| Vue / TypeScript / Vite | 3.5.39 / 5.9.3 / 5.4.21 | [Frontend lockfile](../../frontend/package-lock.json) |
| Pinia / Vue Router | 2.3.1 / 4.6.4 | Frontend lockfile |
| PrimeVue / vue-i18n / Orval | 4.5.5 / 11.4.10 / 7.21.0 | Frontend lockfile |
| Vitest / Playwright | 2.1.9 / 1.62.1 | Frontend lockfile |
| ArchUnit / Testcontainers / JaCoCo | 1.3.0 / 1.20.2 / 0.8.13 | Backend POM |

Frontend 수치는 `package.json`의 `^` 허용 범위가 아닌 lockfile 해석 값이다. 이미지 tag는 배포 중인 digest 증빙이 아니다. 버전의 최신성·지원 종료 여부는 이 조사에서 평가하지 않았다.

## 적용 모듈과 구현 경계

- OIDC BFF·Spring Session JDBC, Keycloak, tenant/workspace/cluster/namespace 범위 권한을 사용한다. [OIDC 상세](../security/identity/oidc-bff-architecture.md)와 [RBAC](../security/authorization/rbac-and-scope-model.md)를 따른다.
- AI·Kubernetes·CLI 선택 모듈을 모두 사용한다. 기본 Ollama/Spring AI 경로 외에 설정형 provider 호출 경로가 존재한다.
- 현재 설정 서비스의 provider 종류는 `OLLAMA`, `OPENAI`, `GOOGLE_GENAI`, `OPENAI_COMPATIBLE`이다. [설정 서비스](../../backend/src/main/java/io/product/aiops/application/service/AiProviderConfigurationService.java)와 [HTTP adapter](../../backend/src/main/java/io/product/aiops/adapter/out/ai/ConfiguredAiClient.java)가 근거다. Azure/Anthropic 전용 adapter 구현을 가정하지 않는다.
- 일반 kubectl은 Runner, 대화형 exec/attach TTY는 Backend Fabric8 WebSocket 경계다. [결정 근거](../adr/0009-retain-backend-fabric8-for-interactive-tty.md)를 따른다.
- OpenAPI snapshot은 `frontend/openapi/klueops.json`, 생성 client는 `frontend/src/api/generated/klueops.ts`다. [Orval 설정](../../frontend/orval.config.ts)과 [생성 규칙](../api/generated-client-rules.md)을 따른다.
- `src/api/client.ts`의 기존 facade와 대형 서비스·뷰·CSS가 남아 있다. 신규 작업은 공통 기준을 따르며 [리팩터링 기준](../architecture/refactoring-register.md)에 따라 관련 부분만 점진적으로 분리한다.
- [ArchUnit 검사](../../backend/src/test/java/io/product/aiops/ArchitectureTest.java)는 domain의 Spring/JPA/Fabric8 의존과 application의 adapter 의존을 검사한다. 모든 설계 규칙을 자동 검증하는 것은 아니다.

## 검증 명령

모든 명령은 저장소 루트에서 실행한다. Java 17/Maven, Node 22/npm을 사용한다. Backend 통합 테스트에는 Docker/Testcontainers가 필요하며 Frontend 검사 전 `npm ci --prefix frontend`를 실행한다.

| 범위 | 명령 | 성공 기준·주의사항 |
| --- | --- | --- |
| Backend | `./scripts/validate-backend.sh` | Maven test 통과, 실패 상세는 `backend/target/surefire-reports/` |
| Runner | `./scripts/validate-command-runner.sh` | Runner test 통과, `command-runner/target/surefire-reports/` |
| Frontend | `./scripts/validate-frontend.sh` | CSS 검사 → typecheck → Vitest → production build 통과 |
| 생성 client | `./scripts/validate-generated-client.sh` | snapshot 기반 재생성 결과와 기존 파일 일치; 파일 변경 여부 확인 |
| runtime OpenAPI | `./scripts/validate-openapi-snapshot.sh` | 실행 Backend와 snapshot 일치; curl/jq, 기본 localhost:8080 또는 `AIOPS_BACKEND_URL` 필요 |
| 문서 | `./scripts/validate-docs.sh` | 필수 파일 존재 확인; Markdown 링크 검사는 별도 필요 |
| 공개 위생 | `./scripts/validate-open-source-hygiene.sh` | 로컬 정보·credential·생성물 추적 정책 통과 |
| 통합 gate | `./scripts/validate-release-candidate.sh` | Docker·Java·Maven·Node/npm·Helm·curl 및 하위 검사 도구 필요; 실행 중 임시 DB/Backend를 생성하며 `artifacts/rc/` 등 확인 |

`validate-backend.sh`는 `test` 단계이며 JaCoCo `verify` gate를 대신하지 않는다. coverage 검사가 필요하면 `mvn -f backend/pom.xml verify`를 사용한다. 현재 POM 하한(instruction 0.15, branch 0.05)은 기존 프로젝트 설정값이며 새 프로젝트 품질 목표로 복제하지 않는다.

이 표는 실행 안내다. 이번 문서 작성에서 위 전체 애플리케이션 검증을 수행했다는 뜻이 아니다.

## 기존 상세 문서 진입점

화면의 큰 배치와 반응형 기준은 [프론트엔드 UI 레이아웃 규칙](../development/frontend-layout-guide.md)을 참고한다.

| 영역 | 기준 문서 |
| --- | --- |
| 제품 범위·미완료 | [현재 명세](../product/current-product-specification.md), [잔여 항목](../product/remaining-development-items.md) |
| 구조·데이터·Job | [전체 구조](../architecture/overview.md), [데이터 모델](../architecture/data-model.md), [비동기 모델](../architecture/async-job-model.md) |
| API | [API 규칙](../api/api-guidelines.md), [OpenAPI](../api/openapi-rules.md) |
| 코드·UI | [코딩 규칙](../development/coding-standards.md), [CSS/UI](../development/frontend-style-guide.md), [다국어](../development/localization.md) |
| AI | [분석 구조](../architecture/ai-analysis-architecture.md), [마스킹](../security/masking-policy.md) |
| 보안 | [보안 구조](../architecture/security-architecture.md), [Secret 관리](../security/secret-management.md) |
| 검증·완료 | [테스트 전략](../development/testing-strategy.md), [품질 gate](../development/quality-gates.md), [DoD](../development/definition-of-done.md) |
| 운영 | [최초 설치](../operations/initial-installation.md), [배포](../operations/component-deployment.md), [복구](../operations/backup-restore.md) |
| 문서·사용자 가이드 | [문서 규칙](../development/documentation-standards.md), [사용자 가이드](../user-guide/README.md) |

이 프로젝트는 사용자 기능 변경 시 Word 사용자 가이드와 생성 소스까지 갱신하고 render/시각 검증한다. 내부 구조만 변경되어 사용 절차가 같으면 해당 규칙의 예외를 적용한다. 새 프로젝트의 가이드 형식은 별도로 선택한다.
