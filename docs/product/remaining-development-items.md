# KlueOps Open-Source Readiness and Remaining Development

기준일: 2026-09-14

## 1. 목적과 운영 원칙

이 프로젝트는 특정 고객의 상용 릴리스나 production 인증이 아니라 오픈소스 저장소 공개를 목표로 한다. 이 문서는 공개 전에 필요한 저장소 기본 요건과 공개 후 검토할 제품 개선만 관리한다.

- `P0`: 오픈소스 저장소를 공개하기 전에 해결해야 하는 법적·보안·재현성 요건
- `P1`: 공개 후 유지보수성과 기여 편의성을 위해 우선 검토할 개선
- `P2`: 외부 연동 또는 선택 기능이며 공개를 차단하지 않는 항목
- 특정 고객 환경의 TLS, IdP, HA, RPO/RTO와 registry 정책은 배포자 책임이며 프로젝트 잔여 개발로 계산하지 않는다.
- 완료한 제품 기능은 `current-product-specification.md`로 옮기고 이 문서에서 제거한다.

## 2. P0 오픈소스 공개 준비

| ID | 항목 | 수행 내용 | 완료 기준 |
| --- | --- | --- | --- |
| OSS-01 | 라이선스 확정 (완료) | Apache-2.0, copyright owner `Jae Youn Kim`, 루트 `LICENSE` 반영. 현재 별도 고지 대상이 없어 `NOTICE`는 두지 않음 | 저장소의 사용·수정·재배포 조건이 명확하고 주요 의존성 라이선스와 충돌 없음 |
| OSS-02 | 저장소 비밀정보 점검 (완료) | 전체 Git history와 현재 tree에서 credential, 개인 경로, 내부 주소, 고객 데이터와 대용량 artifact 검사 | 탐지 결과 검토 완료, 실제 비밀정보 제거·폐기·회전, 예시는 명백한 placeholder만 사용 |
| OSS-03 | 깨끗한 설치 재현 (진행) | 새 clone 또는 빈 작업 디렉터리에서 문서만으로 build, test, Docker/Compose 및 로컬 Kubernetes 설치 재현 | 사전 요구사항부터 로그인·cluster 등록·Cook Book 실행까지 별도 지식 없이 성공 |
| OSS-04 | 공개 문서 정리 (진행) | README에 기능, 구조, 빠른 시작, 지원 범위, 보안 주의사항과 실제 화면 예시 제공 | 신규 사용자가 프로젝트 목적과 실행 방법을 첫 화면에서 이해하고 모든 링크가 유효함 |
| OSS-05 | 기여자 운영 기반 (완료) | 기여 규칙, 보안 제보, 행동강령, issue/PR template와 유지관리 범위 정리 | `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`, issue/PR 흐름이 서로 일치함 |
| OSS-06 | 의존성 공개 적합성 (진행) | Backend/Frontend/Runner/컨테이너의 SBOM, dependency license와 알려진 취약점 검토 | 사용 제한 의존성 없음, 알려진 위험과 업데이트 정책이 문서화됨 |
| OSS-07 | CI 공개 재현성 (완료) | 공개 CI에서 Backend, Frontend, OpenAPI/Orval, 문서, 보안·패키징 계약 실행 | 저장소 전용 Secret 없이 기본 pull request 검증이 통과하고 실패 artifact를 확인 가능 |

루트 `LICENSE`에는 Apache License 2.0 전문을 두고 copyright owner를 `Jae Youn Kim`으로 명시했다. 현재 별도 고지가 필요한 제3자 자료가 없어 `NOTICE`는 만들지 않았다. `CODE_OF_CONDUCT.md`, 구조화된 bug/feature issue form, pull request template와 기여·보안 문서를 서로 맞춰 OSS-05를 완료했다.

현재 tree의 1차 점검과 정리 결과는 다음과 같다.

- Git 저장소를 초기화하고 GitHub `main`과 연결했다. 2026-09-14 기준 현재 tree와 도달 가능한 전체 history에서 고신뢰 credential, 고정 개발자 주소·경로와 로컬 전용 artifact를 검사했다.
- 초기 공개 이력에 포함됐던 `.codex/`는 도달 가능한 history에서 제거하고 `.gitignore` 및 자동 검증 대상으로 고정했다.
- `.github`에는 검증 workflow, bug/feature issue form, 보안 안내와 pull request template가 있다.
- `artifacts/`, Playwright 결과와 report를 로컬 생성물로 분류해 `.gitignore`에 추가했다. 원본 저장소에서 이미 추적 중이면 공개 전에 index와 history도 별도로 정리해야 한다.
- 고정돼 있던 개발자 사설 주소를 제거했다. Docker Desktop DB endpoint는 `host.docker.internal`, LAN Portal은 `<workstation-lan-ip>`, live 검증 DB host는 `AIOPS_LIVE_DATABASE_HOST`로 설정한다.
- OpenAPI 기반 Orval client는 clean clone 검증에 필요한 source artifact이므로 `frontend/src/api/generated/` ignore 규칙을 제거했다.
- `backend/data`, Maven `target`, Frontend test result에는 개인 절대 경로가 포함될 수 있으므로 로컬 생성물로만 유지한다. 현재 ignore 규칙과 도달 가능한 Git history에서 추적되지 않음을 확인했다.
- `scripts/validate-open-source-hygiene.sh`는 현재 tree와 도달 가능한 Git history의 개발자 주소·경로, 고신뢰 credential 패턴, `.codex` 및 로컬 artifact 경로, ignore 계약을 검사한다. `.git`이 없는 복사본에서는 history/index 검사를 건너뛴 사실을 출력한다.
- 2026-09-14 GitHub 새 clone에서 Backend 248 tests, Frontend 91 tests·production build, 문서·저장소 위생과 all-in-one Helm dry-run이 통과했다. 기존 로컬 Kubernetes의 네 Deployment도 `1/1 Ready`다. 별도 namespace에서 신규 설치부터 로그인·cluster 등록·Cook Book 실행까지 재현해야 OSS-03을 완료한다.
- README에 기능, 구조, Mermaid 실행 구조, clean-clone 빠른 시작, 설치·보안·지원 범위를 추가했다. 공개용으로 마스킹한 실제 제품 화면을 추가하면 OSS-04를 완료한다.
- Backend 186개, Command Runner 46개, Frontend production 19개 dependency의 license 누락 0건과 allowlist 통과를 확인했다. production npm audit는 High 0/Critical 0이고 개발 도구의 Moderate 3/High 7/Critical 2는 major upgrade 검증이 필요해 공개 정책에 기록했다.
- `supply-chain` workflow가 PR, `main`, 주간 일정에서 runtime SBOM/license gate와 네 container Trivy scan을 실행하도록 추가했다. 공개 GitHub runner에서 quality gate가 통과했고, 성공·실패 여부와 무관하게 container별 SBOM artifact가 생성됨을 확인해 OSS-07을 완료했다.
- 최초 container scan에서 Frontend/Java runtime의 수정 가능한 OS CVE를 확인해 NGINX unprivileged 1.31.5 Alpine 3.24와 Temurin 17 Noble로 기반 이미지를 갱신했다. 후속 scan에서 확인한 Tomcat은 10.1.59, Backend Netty는 4.1.137, PostgreSQL JDBC는 42.7.12로 보강하고 사용하지 않는 MCP 전이 dependency와 중복 kubectl을 제거했다. Command Runner의 최신 1.34 patch kubectl은 수정 Go toolchain을 포함한 다음 upstream patch가 필요하며, 최신 Keycloak 26.7.3의 Netty Critical `CVE-2026-75595`와 함께 공개 blocker로 유지한다.

## 3. 공개를 차단하지 않는 운영 검증

다음 기능과 검증 도구는 자체 운영 배포자를 위해 계속 제공하지만 오픈소스 공개의 완료 조건은 아니다.

- 외부 TLS/DNS/Ingress/OIDC callback과 logout 검증
- Secret manager, master key rotation과 복구
- 환경별 PostgreSQL/Keycloak RPO·RTO 및 HA failover
- Helm upgrade/rollback/uninstall 후 데이터 복구
- 운영 registry의 image digest, OS CVE와 Cosign identity/issuer 정책
- 실제 조직 계정과 원격 cluster를 이용한 Tenant/RBAC 수용시험

로컬에서는 앱·Keycloak DB 격리 복구, guarded Helm rollback, SBOM, production dependency audit, 배포 image identity, 네 역할과 두 Tenant 격리, A-1~A-6 및 bounded soak가 통과했다. 이 증적은 프로젝트 품질과 예제 배포의 신뢰성을 높이지만 외부 환경의 운영 보증을 의미하지 않는다.

## 4. P1 우선 개선 후보

### P1-03 대형 모듈 추가 분리

분석 서비스, Analysis 화면, 공통 CSS와 Fabric8 진단 adapter는 이미 여러 책임을 분리했지만 여전히 유지보수 상한에 가깝다.

- 기능별 use case/component와 query adapter를 지속 분리
- 공개 API schema와 UX를 바꾸지 않는 characterization test 우선
- line count만을 위한 추상화가 아니라 변경 빈도와 책임을 기준으로 진행

2026-09-11에는 명령 실행 증거 JSON 합성 책임을 `AnalysisCommandEvidenceMerger`로 분리한 데 이어, namespace/cluster section 계약 매핑과 section telemetry 조립을 `AnalysisResultAssembler`로 이동했다. 서비스 상한은 5,210에서 4,890 lines로 낮췄다. 다음 분리 후보는 잔여 deterministic evidence assembly 또는 Kubernetes signal policy다.

완료 기준: maintainability gate의 상한을 단계적으로 낮추고 전체 회귀 테스트가 통과한다.

### P1-05 브라우저 기반 네트워크 작업 확장 검토

`kubectl port-forward`, `cp`와 대용량 artifact 전송은 현재 범용 console의 안정적 지원 범위로 선언하지 않는다.

완료 기준: 인증된 gateway, 전송 크기/시간 제한, malware 검사 정책, path traversal 차단, cancel/감사와 UX 설계가 승인될 때만 구현한다.

## 5. P2 선택 연동 및 유보 기능

| 항목 | 현재 결정 | 착수 조건 |
| --- | --- | --- |
| Prometheus/metrics-server | 마지막 단계로 유보 | 실제 metric 기반 성능·capacity 요구와 대상 설치 정책 확정 |
| Argo CD/GitOps/앱 배포 | 운영 분석 역할에 집중하기 위해 유보 | 배포 승인·drift·rollback 책임 경계와 연동 대상 확정 |
| 외부 알림 | 유보 | Email/Slack/Webhook 등 사용 채널과 retry/secret 정책 확정 |
| Jump server/agent connector | 유보 | Backend에서 API server 직접 접근 불가한 사용 사례 발생 |
| 외부 AI provider | Ollama만 지원 | 데이터 반출, provider credential, 비용·fallback 정책 승인 |
| Managed Keycloak HA | 패키지 범위 제외 | 프로젝트가 IdP 운영 책임까지 다루기로 결정하는 경우 |
| Multi-cluster/DR | 배포자 설계 | 명시적 RTO/RPO와 control-plane HA 요구가 확정된 경우 |

## 6. 다음 권장 실행 순서

1. `OSS-06`: 수정 Go toolchain을 포함한 Kubernetes patch와 Netty 수정본을 포함한 Keycloak 안정 patch가 나오면 갱신하고 네 container scan을 통과시킨다.
2. `OSS-03`: 충돌 없는 별도 namespace에서 신규 설치, 로그인, cluster 등록과 Cook Book 실행을 재현한다.
3. `OSS-04`: 공개용으로 마스킹한 실제 제품 화면을 README에 추가한다.
4. 개발 도구 dependency major upgrade를 generated-client, unit/E2E 계약과 함께 별도 검증한다.
5. 공개 준비가 끝난 뒤 P1-03을 작은 characterization-test 단위로 계속 분리한다.

새 기능을 제안할 때는 이 문서에 단순 후보를 계속 덧붙이지 않는다. 사용자 가치, 운영 책임, 데이터/보안 경계, API/UI와 완료 기준이 확정된 큰 기능만 별도 feature 문서로 설계하고, 구현 완료 즉시 현재 제품 명세에 병합한다.
