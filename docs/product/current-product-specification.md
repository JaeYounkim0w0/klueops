# KlueOps Current Product Specification

기준일: 2026-09-15

## 1. 문서 목적

이 문서는 현재 저장소에서 실제로 구현된 제품 범위, 기술 구조, 사용자 기능, 보안 경계와 검증 수준을 한눈에 설명하는 단일 제품 기준 문서다. 과거 PRD, 날짜별 계획서와 진행 기록은 이 문서에 통합했으며 이후 제품 변경은 이 문서와 `remaining-development-items.md`에만 반영한다.

상태 용어는 다음과 같이 사용한다.

| 상태 | 의미 |
| --- | --- |
| 구현 완료 | 소스와 자동 테스트가 존재한다. |
| 로컬 승인 | Docker Desktop Kubernetes와 로컬 연동 환경에서 실제 동작을 확인했다. |
| 자체 환경 적용 가능 | 사용자가 자신의 TLS, IdP, Secret과 복구 정책을 구성해 배포할 수 있다. |
| 제외/유보 | 현재 오픈소스 프로젝트 범위가 아니며 잔여 개발 문서에서 관리한다. |

## 2. 제품 정의와 경계

이 제품은 이미 존재하는 Kubernetes 클러스터를 등록하고, Kubernetes API·Event·상태·Spec·Log를 수집해 운영 현황과 장애 원인을 분석하며, 안전한 확인 및 조치 흐름을 제공하는 AIOps 운영 플랫폼이다.

핵심 원칙은 다음과 같다.

- Kubernetes 원본 데이터와 결정론적 규칙을 먼저 사용하고 LLM은 설명, 상관관계 분석과 운영 조언을 보강한다.
- 상세 리소스와 로그는 가능한 한 실시간 조회하며 DB에는 등록 정보, 안전한 요약, 이력과 증빙만 저장한다.
- AI 결과는 근거, 수집 범위, 신뢰도, fallback과 검증 명령을 함께 제공한다.
- 변경 작업은 RBAC, 범위 고정, dry-run 또는 preview, 실행 이력, 사후 검증과 보수적 rollback 후보를 거친다.
- UI는 초보자와 숙련자 모두가 같은 사실을 서로 다른 정보 밀도로 이해할 수 있게 구성한다.

현재 범위에는 자체 GitOps reconciliation, Prometheus 기반 장기 시계열 분석과 사용자 인프라 자체의 HA 구축이 포함되지 않는다. 대신 Tenant가 보유하거나 Artifact Hub에서 가져온 Helm Chart를 Custom Values로 대상 Cluster에 배포하고 수명주기를 운영하는 Application Delivery를 제공한다. 특정 고객 환경의 상용 인증이나 릴리스 승인은 프로젝트 목표가 아니다.

## 3. 실행 구조

### 3.1 배포 구성

All-in-one Helm 패키지는 다음 네 Kubernetes Deployment를 제공한다.

1. **Frontend**: Vue 3 정적 자산과 nginx 기반 단일 진입점
2. **Backend**: Java 17, Spring Boot API/BFF/업무 처리
3. **Managed Keycloak**: 설치 편의를 위한 단일 replica 인증 프로필
4. **Command Runner**: Java 17과 kubectl을 포함한 격리 명령 실행기

PostgreSQL과 Ollama는 외부 서비스로 연결한다. 따라서 네 Deployment가 네 물리 서버를 의미하지 않으며, 개발 환경에서는 동일 Kubernetes 노드에서 실행할 수 있다. `postgresql.bootstrapMode=automatic`은 외부 PostgreSQL에 Portal/Keycloak database와 서로 분리된 최소 권한 runtime role을 멱등 생성하며 기존 database 소유자가 다르면 중단한다. 공개 Docker Desktop 로컬 프로필은 Portal NodePort `30081`, Keycloak NodePort `30080`, PostgreSQL/Ollama host endpoint에 `host.docker.internal`을 사용한다.

### 3.2 기술 스택

| 영역 | 기술 |
| --- | --- |
| Backend | Java 17, Spring Boot, Spring AI, Spring Security, Spring Session JDBC |
| Kubernetes | Fabric8 Kubernetes Client, kubectl Command Runner |
| AI | Ollama ChatModel, 관심사별 분할 요청, 결정론적 fallback |
| Data | PostgreSQL 17, Spring Data JPA/JDBC, Flyway V1~V32 |
| API | OpenAPI/Swagger, RFC 9457 Problem Detail, Orval 생성 client |
| Frontend | Vue 3, TypeScript, Vite, PrimeVue, Vue I18n |
| 인증 | OIDC BFF, Managed Keycloak 또는 외부 OIDC |
| 배포 | Docker, Helm, Kubernetes, 컴포넌트별 init/deploy script |

Backend는 헥사고날 아키텍처를 사용한다. 도메인·애플리케이션 포트와 Kubernetes, Ollama, DB, OIDC 같은 외부 adapter를 분리해 교체와 테스트가 가능하도록 구성했다.

## 4. 사용자 기능

### 4.1 Dashboard와 공통 운영 UX

- Phase 2 공통 제품 Shell은 짙은 navigation, 상단 Tenant/Workspace context bar, 전역 검색·알림과 반응형 모바일 drawer를 모든 인증 route에 적용한다.
- 공통 semantic token으로 배경, surface, 글자, 상태색, radius, elevation과 keyboard focus를 관리해 화면별 시각 표현 차이를 줄인다.
- 클러스터, Incident, 분석 Job과 운영 위험을 요약한다.
- 전역 Job Dock에서 화면 이동 후에도 분석 및 동기화 진행 상태와 소요 시간을 확인한다.
- 운영 통합 검색으로 클러스터, 리소스, Incident, 분석과 Runbook을 찾고 관련 화면으로 이동한다.
- 한국어와 영어를 설정에서 선택하며 Kubernetes 이름, YAML, 로그와 명령 원문은 번역하지 않는다.
- 제목, 설명, 오류와 빈 상태를 초보 운영자가 이해할 수 있는 용어로 제공하고 상세 근거는 확장해 볼 수 있다.

### 4.2 Cluster 관리

- kubeconfig를 1순위로 등록하고 사용할 수 없을 때 ServiceAccount token 방식을 지원한다.
- 등록 전/후 Kubernetes API 연결 검증과 상세 오류 확인을 제공한다.
- credential은 AES-256-GCM으로 암호화하고 화면과 로그에서 마스킹한다.
- 수동 동기화와 기본 5분 자동 동기화를 제공하며 클러스터별 중복 동기화를 차단한다.
- DB에는 연결 정보, 동기화 상태와 제한된 안전 요약을 저장하고 상세 리소스는 Kubernetes API에서 실시간 조회한다.
- 클러스터 상세에서 Namespace, workload, Pod, Service, Endpoint, Ingress, ConfigMap, Secret metadata, PVC, Job, CronJob, HPA, PDB, Quota, LimitRange, NetworkPolicy 등을 Namespace별로 탐색한다.
- 리소스 YAML, 관계, Event와 최근 N줄 로그를 제공한다. Pod 로그는 컨테이너 선택과 streaming을 지원하며 시작 전 컨테이너 상태도 설명한다.
- Capability Matrix, Credential Health와 Upgrade Readiness로 API 지원, 접근 권한, 인증서/토큰 상태와 업그레이드 위험을 확인한다.

### 4.3 AI Analysis

Namespace 및 Cluster 단위 분석을 지원한다. 수집은 최대 21개 Kubernetes source를 독립적으로 수행하며 한 source의 권한 오류나 timeout이 전체 분석을 중단하지 않는다. 실패 budget을 넘은 나머지 source는 `SKIPPED`로 표시하고, 부분 수집 결과의 confidence를 제한한다.

분석 파이프라인은 다음 순서로 동작한다.

1. Kubernetes 상태, 관계, Event와 제한된 로그를 수집하고 Secret/민감정보를 제거한다.
2. 포트 불일치, FailedMount, selector/endpoints, probe, requests/limits, replica, HPA/PDB 등은 결정론적 규칙으로 우선 판정한다.
3. LLM context를 root cause, log analysis, performance/scaling, risk timeline, runbook/operations 관심사로 분할해 bounded 병렬 호출한다.
4. section별 timeout, schema와 의미 품질을 검증하고 실패 section만 결정론적 fallback으로 대체한다.
5. 근거 범위, context 크기, section latency, fallback과 전체 소요 시간을 결과에 기록한다.

결과 화면은 다음 정보를 제공한다.

- 운영 요약, Root Cause와 confidence
- 관련 Resource, Event, Log와 즉시 drill-down
- Log intelligence와 문제 리소스별 최근 로그 조회
- Performance 및 scale 방향, 단 Kubernetes API로 확인할 수 없는 실제 사용률은 단정하지 않음
- 단기 위험 예측, 변경 timeline과 운영 posture
- 검증·원인 확인·안전 조치 단계로 분리된 Actionable Runbook
- destructive 명령 기본 숨김, 위험 경고와 명령 목적 설명
- Next Action, 중복 원인 그룹, 전후 분석 비교와 feedback
- 분석 재시도·취소, 동일 scope 중복 방지와 최근 결과 재사용

AI Trust Center는 평가 corpus, category별 정확도, 근거 coverage, hallucination/abstention과 feedback 추세를 표시한다. Incident Report는 분석, 근거, command 검증과 snapshot hash를 Markdown, JSON 또는 ZIP으로 내보낸다.

### 4.4 Kubernetes Console과 조치

클러스터 상세의 **Kubernetes 콘솔**에서 권한 범위 안의 kubectl 명령을 자유롭게 실행한다.

- 일반 명령은 격리 Command Runner가 실행하고 stdout/stderr를 SSE로 전달한다.
- `exec/attach -it`는 xterm 기반 WebSocket Pod terminal로 제공한다.
- 명령 history, 즐겨찾기, output filter, 복사와 다운로드를 제공한다.
- 리소스 command builder와 AI Analysis/Runbook에서 전달된 검증 명령을 지원한다.
- 사용자·클러스터 concurrency quota, rate limit, timeout, cancel, output 제한과 재기동 lease recovery를 적용한다.
- 명령 parser, RBAC와 cluster/namespace scope를 서버에서 검증한다.
- 변경/삭제 명령은 가능한 경우 dry-run 또는 preview, 실행 전후 snapshot, 사후 검증 상태와 audit을 남긴다.
- scale은 이전 replica 복원, rollout/set은 근거가 있을 때만 rollout undo 후보를 제공한다.
- Incident report에는 command 상태와 snapshot SHA-256만 포함하며 credential, 원본 Secret과 live log는 제외한다.
- Cook Book은 39개 단일 명령과 Pod/Service/Storage/Node 증상별 절차, 정상 기준과 다음 권장 점검을 제공한다. 리소스 빌더 입력은 placeholder에 반영하며 Metrics API가 실제 Available일 때만 `top` 명령을 활성화한다.

일반 kubectl은 별도 Runner로 격리돼 있으며 TTY exec는 ADR-0009에 따라 Backend Fabric8 WebSocket 경계를 유지한다. TTY는 일반 명령과 별도 quota, rate limit, 단일 사용 ticket, idle/output 상한을 적용한다.

### 4.5 Operations Control Plane

- Triage Queue에서 운영 신호를 우선순위와 신뢰도 기준으로 처리한다.
- Incident 생성, dedup/reopen, 상태 전이, comment, link, merge/split과 activity timeline을 제공한다.
- 운영 알림과 unread 상태, 정책 평가, baseline 및 change timeline을 관리한다.
- Fleet command/validation 화면에서 여러 scope 작업을 추적한다.
- Runbook 생성, 수정, 버전 조회와 복원을 지원한다.
- 리소스 관계와 필드 diff를 통해 변경 전후 원인을 추적한다.
- Production Evidence와 Reliability 화면에서 상용 환경 검증 결과를 통과·실패·차단·만료 상태로 관리한다.
- 운영 설정에서 Incident, analysis, command/audit, Job 등 영속 데이터의 보존 기간과 dry-run 정리 건수를 확인한 뒤 cleanup을 실행한다. 실행 중 명령과 최신 snapshot/regression 기준선은 삭제하지 않으며 cleanup 자체를 Audit으로 기록한다. 보고서 export는 요청 시 생성·다운로드할 뿐 서버에 artifact로 영구 저장하지 않는다.

### 4.6 AI Chat

- 범용 상담과 Kubernetes 클러스터 상담을 분리한다.
- 클러스터 상담은 선택한 Cluster/Namespace 및 언급된 리소스의 live Kubernetes context를 제한된 크기로 수집하고 근거 reference를 답변에 연결한다.
- Spring AI ChatMemory 기반 대화 문맥과 SSE streaming/heartbeat를 사용한다.
- 대화 제목 변경, 즐겨찾기, 보관/복원과 영구 삭제를 제공한다.
- 대화와 context reference는 사용자·Tenant·scope별로 격리한다.

### 4.7 Settings와 접근 제어

- `PLATFORM > TENANT > WORKSPACE > CLUSTER > NAMESPACE` 계층으로 운영 범위를 관리한다.
- Keycloak Group과 제품 Tenant는 분리하며 역할 binding은 각 scope에 부여한다.
- platform-admin, cluster-admin, operator, viewer capability를 API 서버에서 평가한다.
- Tenant/Workspace 생성, 전역 scope 선택, Cluster placement와 접근 가능한 목록 필터링을 제공한다.
- 사용자 관리, 운영 설정, reliability, 언어 설정과 접근 범위 화면을 제공한다.
- 좌측 내비게이션은 `개요`, `운영 대응`, `인프라`, `Application Delivery`, `AI 운영`, `거버넌스`, `플랫폼 설정`, `개인 영역`으로 고정하며 capability가 없는 그룹은 제목과 항목을 함께 숨긴다.
- 좌측의 `사용자 및 권한`은 Tenant Users & Access를 기본 진입점으로 사용하고 Platform Manager에게 플랫폼 계정 권한 화면 연결을 제공한다. 기존 두 접근 관리 URL은 호환성을 유지한다.

### 4.8 Application Delivery와 AI Provider

- Artifact Hub에서 Helm Chart를 검색해 정확한 버전을 Tenant Library로 가져오거나 `.tgz`, Helm Repository source를 등록한다.
- Chart artifact는 digest와 함께 Tenant 범위로 보관하며 Custom은 암호화된 versioned Values Profile만 지원한다.
- 보유 Chart를 기본 진입점으로 선택하고 Values, Cluster/Namespace, `Cluster 내부`·`Chart에서 관리`·`KlueOps HTTPRoute` Exposure와 preview를 거쳐 Helm install을 실행한다. Chart-managed 모드는 렌더된 Ingress/HTTPRoute 존재를 검증하고, KlueOps HTTPRoute는 Service/Port와 Gateway listener를 사전 확인한다.
- install/upgrade/rollback/uninstall은 비동기 Job과 ReleaseOperation으로 추적하며 중단된 작업은 timeout 후 실패 상태로 복구한다.
- Application 상세에서 Helm 상태, workload/Pod health, Service·Ingress·HTTPRoute 접근 endpoint와 `READY/APPLIED/DEGRADED` 상태, operation history를 확인한다.
- Tenant 기능 정책과 `TENANT_ADMIN`, `CLUSTER_ADMIN`, `OPERATOR`, `VIEWER` capability를 메뉴와 API에서 함께 평가한다. Platform Manager는 모든 Tenant 제품 권한을 가지되 대상 Kubernetes RBAC는 우회하지 않는다.
- Users & Access에서 Tenant membership, pending invite, 역할/scope, OIDC Group Mapping과 안전한 offboarding을 관리한다.
- AI Provider profile은 Ollama, OpenAI, Google GenAI, OpenAI-compatible 유형을 저장·검증하고 Tenant 목적별 routing을 제공한다. Credential은 암호화·마스킹하며 외부 전송은 명시적으로 허용한다.
- Ollama 설치 모델을 동기화하고 승인 목록의 9B 이하 모델만 추가 대상으로 허용한다. 기본 모델은 품질 gate가 끝날 때까지 `qwen2.5-coder:7b`를 유지한다.

## 5. 보안 구조

- 브라우저는 OAuth token을 보관하지 않는 OIDC BFF를 사용한다.
- 세션은 PostgreSQL에 저장하고 HttpOnly/SameSite/Secure cookie, CSRF, logout과 만료 복구를 제공한다.
- 인증된 principal, capability와 object scope를 Backend에서 검증하며 Frontend 표시 여부를 권한 판단으로 신뢰하지 않는다.
- 모든 credential과 PII는 로그, prompt, audit와 export에서 마스킹한다.
- Managed Keycloak은 all-in-one 편의 프로필이며 인증 HA가 필요한 운영 환경은 외부 Keycloak/조직 IdP 연결을 권장한다.
- production Helm은 HTTPS Portal/issuer, TLS ingress, Secure cookie와 existing Secret 계약을 fail-fast로 검증한다.
- Command Runner는 token 인증, replay guard, non-root, read-only filesystem, resource limit와 Backend-only NetworkPolicy를 사용하고 장애 시 Backend local fallback을 허용하지 않는다.

## 6. 데이터 및 동기화 원칙

| 데이터 | 처리 방식 |
| --- | --- |
| Cluster credential | 암호화 저장 |
| Cluster/Namespace 및 안전 요약 | 동기화 이력과 최신 snapshot 저장 |
| 상세 Kubernetes resource/YAML | 기본적으로 live API 조회 |
| Pod log | 요청 시 조회/stream, DB 영구 저장 안 함 |
| 분석/Incident/Runbook/명령/Audit | PostgreSQL 영속 저장 |
| 사용자 session/tenant/scope | PostgreSQL 영속 저장 |
| Chart artifact/metadata와 Values revision | Tenant 범위 PostgreSQL 저장, Values 암호화 |
| Application/Release operation | Cluster 소유권에서 Tenant를 유도해 PostgreSQL 영속 저장 |
| AI Provider credential/routing | 암호화된 profile과 Tenant 목적별 정책으로 저장 |

Runtime DB는 PostgreSQL로 통일했으며 H2는 사용하지 않는다. Flyway V1~V32가 schema 변경을 관리한다.

## 7. API와 개발 규칙

- 모든 Backend API는 OpenAPI annotation과 Swagger 문서를 제공한다.
- Frontend API client는 실행 중 OpenAPI snapshot으로 Orval 생성하며 drift 검증을 통과해야 한다.
- 오류는 correlation/request ID를 포함한 Problem Detail로 반환한다.
- 장시간 분석과 동기화는 AsyncJob으로 실행한다.
- LLM에는 하나의 거대한 prompt를 보내지 않고 관심사별 bounded context를 병렬 호출한다.
- 사용자 기능 변경 시 자동 테스트, 관련 Markdown과 Word 사용자 가이드를 같은 변경에서 갱신한다.
- 세부 규칙은 `docs/development`, 구조는 `docs/architecture`, 운영 절차는 `docs/operations`, 보안은 `docs/security`를 따른다.

## 8. 설치와 배포

- 최초 구성은 `scripts/init`에서 PostgreSQL/Realm 준비와 all-in-one 설치를 수행한다.
- 소스 변경 후에는 `scripts/deploy`에서 Backend, Frontend, Keycloak과 Runner를 개별 빌드·이미지화·배포한다.
- 각 Dockerfile은 build stage와 non-root runtime stage를 분리한다.
- Helm release lock, 고유 image tag, 대상 rollout과 비대상 Deployment image 유지 여부를 검증한다.
- 공개 URL은 workstation IP에 고정하지 않고 `AIOPS_PUBLIC_HOST` 또는 명시적인 Portal/issuer URL로 설정한다.

자세한 절차는 `docs/operations/initial-installation.md`, `component-deployment.md`, `public-url-and-client-access.md`를 따른다.

## 9. 현재 검증 기준

2026-09-15 기준 최신 통합 증빙은 다음과 같다.

- Backend: PostgreSQL 17 Testcontainers, Flyway V1~V32 포함 273 tests 통과
- Command Runner: 5 tests 통과
- Frontend: 28 files, 97 tests, typecheck와 production build 통과
- OpenAPI runtime snapshot과 Orval generated client drift 통과
- architecture, security, packaging, docs와 maintainability gate 통과
- Docker Desktop Kubernetes Helm revision 90에서 Frontend, Backend, Managed Keycloak, Command Runner 모두 `1/1 Ready`
- OIDC 관리자 사용자로 `dev-master/default`의 격리 Runner `kubectl get pods --field-selector=status.phase!=Running,status.phase!=Succeeded -o wide` 실행 성공, exit code `0`, 130ms
- 로컬 Keycloak 네 역할과 두 Tenant object scope 격리 검증 통과
- read API 30회/동시성 10 기준 p95 14ms, Backend/Keycloak 순차 재시작, 앱 DB Flyway migration 28건·Keycloak `aiops` Realm sentinel 격리 복원과 AI timeout fallback 증빙
- 로컬 synthetic A-1~A-6 수용시험 6/6 통과(`artifacts/acceptance/ai-analysis-e2e-20260911072724-45137.json`). OIDC callback 후 인증 session readiness를 확인하고, namespace 전용 임시 credential로 권한 포트 오류, Service port mismatch, confirmation guard, restart, explicit rollback, 재분석 비교와 이전 명령 증빙 승계를 검증
- bounded 부하 검증 500 ConfigMap·동시성 20/50/100 통과. 동기화 100 요청과 AI 100 요청은 각각 1 Job으로 합쳐졌고 pagination p95 632ms/p99 646ms, AI 완료 53.5초·fallback 0, cancel 16ms, SSE 100 연결 정리가 통과했다.
- 위 수용 증빙을 명시한 제품 준비성 보고서 `artifacts/product-readiness/product-readiness-20260911T075600Z.json`에서 CORE, COMMERCIAL, QUALITY, DOCS 전체 gate 통과
- guarded Helm rollback으로 revision 52→53→54 전환 및 네 Deployment image identity 유지 검증 통과
- GitHub 공개 저장소를 새로 clone해 Backend 248 tests, Frontend 91 tests·production build, 문서·저장소 위생과 all-in-one Helm dry-run을 별도 cache 없이 재현했다.
- Backend 186개, Command Runner 46개, Frontend production 19개 component의 CycloneDX SBOM에서 license 누락 0건과 allowlist 정책 통과를 확인했다. Frontend production dependency는 High 0/Critical 0이며 개발 도구의 알려진 취약점은 runtime과 분리해 `docs/security/dependency-policy.md`에 공개한다.
- 공개 supply-chain workflow는 네 runtime container를 build·Trivy scan하고 runtime SBOM과 license gate를 실행한다. signed container workflow도 Command Runner를 포함한다. registry별 Cosign identity/issuer 검증은 자체 운영 배포자가 수행한다.
- 월간 Dependabot 정책은 Backend/Command Runner Maven, Frontend npm과 GitHub Actions의 minor/patch version update를 ecosystem별 최대 1개 PR로 제한하며 Docker 일반 update와 모든 major update는 자동 생성하지 않는다. Security update와 주간 supply-chain scan은 계속 유지하고 자동 merge하지 않는다.
- README의 Dashboard와 Kubernetes Console/Cook Book 화면은 별도 namespace의 실제 설치에서 캡처했으며 계정, cluster 식별자와 내부 주소를 공개용 값으로 마스킹했다. 문서 검증은 두 화면 asset의 존재를 확인한다.
- Docker Desktop의 `aiops-system`에서 OIDC 로그인 후 Artifact Hub 검색, nginx Chart import, 암호화 Values 저장·재조회, preview의 Secret redaction, Namespace 생성, Helm install의 `1/1` workload health와 Service endpoint, uninstall, Users & Access, AI Provider 연결 검증과 Ollama model 동기화를 브라우저로 확인했다.
- Phase 2 공통 제품 Shell을 로컬 Kubernetes Frontend 이미지에 반영하고 실제 OIDC 세션에서 Applications 상태 요약·목록·Runtime/Endpoint inspector, Dashboard, 모바일 navigation과 Application 배포 chooser를 브라우저로 확인했다.

검증 명령과 최신 로컬 품질 증적은 `docs/operations/release-candidate-checklist.md`를 따른다. 문서와 스크립트의 `release-candidate` 명칭은 기존 자동화 호환을 위해 유지하며 상용 릴리스 판정을 의미하지 않는다.

## 10. 제품 완성도 판정

| 관점 | 판정 |
| --- | --- |
| 기능 개발 | 핵심 Kubernetes 운영, AI 분석/상담, Incident, 안전 명령, Tenant 접근 관리와 Helm Application Delivery 구현 완료 |
| 개발·데모 | 사용 가능 |
| 내부 Pilot | 사용 가능, 실제 대상 cluster별 권한과 credential 확인 필요 |
| 오픈소스 공개 | LICENSE, 저장소 위생, 기여 흐름, clean-clone 검증, 별도 namespace 신규 설치, 공개 CI, SBOM/license/risk 정책과 마스킹한 제품 화면 문서화 완료. 수정본이 없는 upstream runtime 취약점은 SEC-01로 공개 추적 중 |
| 자체 운영 배포 | 사용자가 환경별 TLS, IdP, Secret, 백업과 HA 책임을 검증해야 함 |
| 대형 cluster 보장 | 현재 제품 범위 아님. 사용자가 bounded 기준을 넘는 규모를 요구하면 별도 SLO와 검증 범위를 정해야 함 |
| Prometheus/자체 GitOps | 현재 프로젝트 범위 제외. Helm 기반 Application Delivery는 제공 |

프로젝트 완성도는 고객 상용 `READY`나 특정 환경의 릴리스 승인으로 판정하지 않는다. 공개 저장소의 법적·보안적 기본 요건, 재현 가능한 설치, 문서와 기여 흐름을 우선한다. 각 사용자의 운영 환경에 필요한 TLS/DNS/IdP, Secret 관리, 백업·복구, 공급망 정책은 선택적 운영 지침으로 제공하며 프로젝트 공개를 차단하지 않는다. 남은 공개 준비와 개발 후보는 `remaining-development-items.md`에서 관리한다.
