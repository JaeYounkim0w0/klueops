# KlueOps Phase 2

기준일: 2026-09-15

이 디렉터리는 1차 KlueOps 제품 명세와 구현 완료 문서에서 분리한 2차 개발 전용 설계 공간이다. 2차 기능이 구현·검증되기 전까지 `docs/product/current-product-specification.md`의 현재 동작을 변경하거나 완료된 기능처럼 표현하지 않는다.

## Application Delivery

Tenant별 Helm Chart 검색·보관, Custom Values, Cluster/Namespace 선택, HTTPRoute/Ingress Exposure와 Kubernetes 배포·Application 운영을 제공한다. GitOps, Chart template 편집과 외부 registry publish는 현재 범위가 아니다.

`Deployments`는 독립 상시 메뉴가 아니다. 신규 배포는 Values에서 시작하는 Target/Exposure·Preview Wizard이고, 실행 중 작업은 전역 Job Center, 배포된 대상과 실패·진행 상태는 Deployed Applications, 작업 이력은 Application Detail의 History에서 확인한다. Kubernetes `Deployment` 리소스는 Workloads 탭에서 조회한다.

Chart payload는 소규모 기본 설치에서 PostgreSQL에 보관하고 대규모 설치는 S3-compatible storage 또는 기존 OCI Registry adapter를 선택한다. Harbor/MinIO를 필수 dependency로 설치하지 않는다.

Local LLM은 특정 계열에 고정하지 않고 9B 이하 후보를 동일 fixture로 평가한다. Ollama model 추가·검증·승인과 목적별 routing을 제공하며 외부 OpenAI/Google GenAI 연동은 Provider Profile과 Tenant별 데이터 외부 전송 동의를 통해 선택적으로 제공한다.

- [제품 요구사항](application-delivery/product-requirements.md)
- [아키텍처 설계](application-delivery/architecture-design.md)
- [AI Provider 및 모델 전략](application-delivery/ai-provider-and-model-strategy.md)
- [UI/UX 화면 설계](application-delivery/ui-ux-screen-design.md)
- [실행 가능한 HTML 시안](application-delivery/ui-mockups/index.html)

## 문서 상태

| 구분 | 상태 |
| --- | --- |
| 제품 범위 | 설계 완료 |
| 아키텍처 | 설계 완료 |
| UI/UX | 시안 완료, 사용자 검토 대기 |
| Backend/Frontend 구현 | 미착수 |
| 로컬 Kubernetes 수용시험 | 미착수 |

2차 구현이 시작되면 이 문서의 완료 기준을 기준으로 작은 vertical slice 단위로 진행한다. 실제 구현을 시작하지 않은 설계 항목을 1차 제품의 현재 기능으로 합치지 않는다.

문서와 시안 구성 검사는 저장소 루트에서 `bash docs/phase-2/validate.sh`로 실행한다.
