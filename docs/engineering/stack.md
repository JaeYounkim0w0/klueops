# 기술 스택과 선택 기준

실제 고정 버전과 근거 파일은 [프로젝트 프로필](project-profile.md)에 둔다. 아래 구성은 KlueOps의 참조 스택이다. 새 프로젝트는 요구사항과 운영 환경에 맞춰 선택하고 변경 이유를 ADR에 기록한다.

| 영역 | 참조 기술 | 역할·선택 기준 |
| --- | --- | --- |
| Backend | Java 17, Spring Boot 3, Maven | JVM 서비스, DI·설정·HTTP·운영 기능과 빌드 |
| 저장소 | PostgreSQL, Spring Data JPA, Flyway | 관계형 정합성, persistence adapter, 버전별 schema migration |
| API | Spring MVC, Bean Validation, springdoc-openapi | 입력 검증·REST 계약·OpenAPI 생성 |
| 인증 | Spring Security OAuth2 Client, OIDC BFF, Spring Session JDBC | 브라우저 token 직접 취급 없이 서버 세션 사용 |
| IdP | Keycloak | OIDC 제공자. 조직 IdP로 대체 가능 |
| Frontend | Vue 3, TypeScript, Vite | typed component와 개발·production build |
| 상태·라우팅 | Pinia, Vue Router | 공유 상태·화면 이동·deep-link |
| UI | PrimeVue, PrimeIcons, PrimeUIX Aura, 공통 CSS | 운영 화면의 표·dialog·theme·semantic UI 재사용 |
| 다국어 | vue-i18n | 한국어·영어 메시지 및 locale 처리 |
| API client | Orval fetch client | OpenAPI에서 타입과 호출 코드 생성 |
| Backend 검증 | JUnit/Spring Boot Test, ArchUnit, Testcontainers | 규칙·계층 의존성·실제 PostgreSQL 계약 검증 |
| Frontend 검증 | vue-tsc, Vitest, Playwright | 타입·단위/계약·실제 브라우저 흐름 |
| 실행·배포 | Docker, Compose, Helm, Kubernetes, nginx | 로컬 실행, 이미지, 배포 설정, 정적 자산과 proxy |
| 운영 | Actuator, Micrometer, correlation ID | health/readiness, 지표와 요청 추적 |
| 선택: AI | Spring AI, Ollama, 외부 provider adapter | 모델 호출을 port 뒤에 격리 |
| 선택: Kubernetes | Fabric8, Helm CLI, kubectl Runner, xterm.js | 리소스 조회·배포·명령 실행·대화형 터미널 |

## 의존성 관리

- manifest의 허용 범위와 lockfile의 실제 해석 버전을 구분한다. Frontend 재현 설치는 `npm ci`를 사용한다.
- Backend는 Spring Boot parent와 BOM을 우선 사용한다. 개별 override에는 호환성·보안상 이유와 검증 결과를 남긴다.
- 새 버전은 적용 시점에 공식 지원 범위, 런타임 호환성, 라이선스와 취약점을 확인한다. 이 문서의 과거 버전을 자동으로 채택하거나 일괄 최신화하지 않는다.
- 도메인이 필요로 하지 않는 AI SDK, Kubernetes client, message broker, cache 등을 기본 의존성으로 추가하지 않는다.
- persistence, 인증, API 생성 도구 등 변경 비용이 큰 선택은 대안·선택 이유·포기한 이점·교체 조건을 ADR에 기록한다.

현재 저장소의 Actuator/Micrometer 사용이 Prometheus·Grafana·분산 tracing 전체 구성이 완료됐다는 의미는 아니다. 관측 도구는 운영 요구사항에 따라 별도로 구성하고 검증한다.
