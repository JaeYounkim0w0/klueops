# KlueOps Phase 2

기준일: 2026-09-15

이 디렉터리는 1차 KlueOps 제품 명세와 분리해 Phase 2의 설계, 구현 범위와 검증 기준을 관리한다. 2026-09-15에 핵심 vertical slice를 `feature/phase-2`에서 구현하고 로컬 Kubernetes 수용 검증을 수행했으며, 실제 구현 범위는 `docs/product/current-product-specification.md`와 함께 확인한다.

Phase 2의 첫 작업은 `P2-0 기존 제품 UI 현대화`다. Application Delivery 구현에 앞서 현재 KlueOps 전체 화면을 이 디렉터리의 HTML 시안 수준으로 정돈하고, 기존 기능·권한·API 동작을 유지한 상태에서 공통 design system, responsive/accessibility와 visual regression 기준을 확립한다.

## Application Delivery

Tenant별 Helm Chart 검색·보관, Custom Values, Cluster/Namespace 선택, HTTPRoute/Ingress Exposure와 Kubernetes 배포·Application 운영을 제공한다. GitOps, Chart template 편집과 외부 registry publish는 현재 범위가 아니다.

`Deployments`는 독립 상시 메뉴가 아니다. Applications의 `Application 배포`는 보유 Library Chart(권장), 새 Chart 검색, URL/.tgz 가져오기 중 시작점을 고른 뒤 Values→Target/Exposure→Preview Wizard로 연결한다. 실행 중 작업은 전역 Job Center, 배포된 대상과 실패·진행 상태는 Deployed Applications, 작업 이력은 Application Detail의 History에서 확인한다. Kubernetes `Deployment` 리소스는 Workloads 탭에서 조회한다.

Chart payload는 소규모 기본 설치에서 PostgreSQL에 보관하고 대규모 설치는 S3-compatible storage 또는 기존 OCI Registry adapter를 선택한다. Harbor/MinIO를 필수 dependency로 설치하지 않는다.

Local LLM은 특정 계열에 고정하지 않고 9B 이하 후보를 동일 fixture로 평가한다. Ollama model 추가·검증·승인과 목적별 routing을 제공하며 외부 OpenAI/Google GenAI 연동은 Provider Profile과 Tenant별 데이터 외부 전송 동의를 통해 선택적으로 제공한다.

- [제품 요구사항](application-delivery/product-requirements.md)
- [아키텍처 설계](application-delivery/architecture-design.md)
- [AI Provider 및 모델 전략](application-delivery/ai-provider-and-model-strategy.md)
- [UI/UX 화면 설계](application-delivery/ui-ux-screen-design.md)
- [실행 가능한 HTML 시안](application-delivery/ui-mockups/index.html)
- [로컬 Exposure 수용시험](application-delivery/local-exposure-acceptance.md)

## 문서 상태

| 구분 | 상태 |
| --- | --- |
| 제품 범위 | 핵심 범위 구현 완료, 고급 연동은 후속 항목으로 분리 |
| 아키텍처 | 단일 Frontend/Backend 내 bounded context와 선택형 Helm 실행 경계 구현 |
| UI/UX | 공통 design token 기반 Application Delivery, Users & Access, AI Provider 화면 구현 및 반응형 보완 |
| Tenant/RBAC | Platform Manager, Tenant 역할, 메뉴 정책, membership/offboarding과 직접·Cluster 파생 Resource scope 구현 |
| Backend/Frontend 구현 | 핵심 vertical slice 구현 완료 |
| 로컬 Kubernetes 수용시험 | OIDC 로그인, 검색·가져오기·Values·preview·Helm install/uninstall·상태 조회와 Chart-managed Ingress·companion HTTPRoute 실제 HTTP 접근 검증 완료 |

Schema 기반 Form/YAML 양방향 편집, OCI/Object Storage adapter, 자동 DNS·TLS와 고급 Gateway 정책 연동, PVC 보존 선택과 로컬 모델 삭제 보호처럼 아직 구현되지 않은 항목은 완료 기능으로 표시하지 않고 잔여 개발 문서에서 관리한다.

문서와 시안 구성 검사는 저장소 루트에서 `bash docs/phase-2/validate.sh`로 실행한다.
