# 사용자 매뉴얼

사용자 매뉴얼은 초보 운영자와 숙련 운영자가 같은 화면에서 필요한 깊이로 작업할 수 있도록 구성한다.

## 시작 순서

1. 로그인 후 상단에서 Tenant와 Workspace를 확인한다.
2. `Clusters`에서 대상 클러스터의 연결 상태와 마지막 동기화 시각을 확인한다.
3. 클러스터 상세에서 Namespace와 리소스를 선택한다.
4. Kubernetes 콘솔이 필요하면 대상 Cluster와 Namespace가 잠겼는지 확인한 뒤 조회 명령부터 실행한다.
   명령이 익숙하지 않으면 `Cook Book`에서 점검 목적을 검색하고 범위와 요구 조건을 확인한 뒤 명령 작업 공간으로 가져온다.
5. 문제가 있으면 `Analysis`에서 범위를 고정하고 분석을 실행한다.
6. 결과의 Kubernetes 근거, 로그, Event, 명령 안전도를 순서대로 확인한다.
7. `콘솔에서 검증`으로 명령과 원본 분석을 연결하고 실행 결과를 확인한다.
8. 변경 명령은 먼저 검증 또는 dry-run을 실행하고, 결과를 확인한 뒤 조치한다.
9. 조치 후 같은 범위를 재분석해 상태가 개선됐는지 확인한다.

## 메뉴 안내

| 메뉴 | 주요 용도 |
| --- | --- |
| Dashboard | 여러 클러스터의 운영 우선순위와 진행 중인 Job 확인 |
| Clusters | 클러스터 등록, 연결 확인, 동기화, Namespace/리소스 조회 |
| Kubernetes Console | 선택 Cluster에서 kubectl 조회·변경·Pod TTY 실행과 이력 확인 |
| Applications | Tenant Chart 검색·보관, Custom Values, Helm 배포와 Application 수명주기 관리 |
| AI Analysis | Namespace 또는 Cluster 범위의 원인·로그·성능·위험·Runbook 분석 |
| AI Chat | 일반 상담 또는 선택한 Kubernetes 리소스 기반 상담 |
| Incidents | 반복 장애, 담당자, 상태, 영향 범위와 타임라인 관리 |
| Runbooks | 검증 명령, 안전한 조치, 예상 결과와 복구 절차 관리 |
| Operations | Watch, 품질 인증, 정리 정책, 운영 신뢰성 확인 |
| Settings | 사용자·OIDC Group, Tenant/Workspace, AI Provider·모델, 시스템 설정 및 감사 이력 관리 |

## Helm Application 배포 시작

1. `Applications > Chart Library`에서 보유 Chart를 선택한다. 필요한 Chart가 없으면 `Discover`에서 검색해 현재 Tenant로 가져오거나 Source/.tgz를 등록한다.
2. Values Profile을 생성하고 YAML을 저장한다. AI 제안은 diff를 검토한 뒤 적용하며 Secret은 입력·출력에서 노출하지 않는다.
3. `배포`에서 권한이 있는 Cluster와 기존 Namespace를 선택한다. 새 Namespace는 capability가 있을 때만 생성한다.
4. 기본은 내부 Service이며, 필요하면 대상 Service/Port와 hostname을 지정해 HTTPRoute를 함께 계획한다.
5. Preview에서 렌더링 결과와 경고를 확인하고 정확한 확인 문구를 입력해 실행한다.
6. Applications에서 Job, workload/Pod, Service·접근 경로와 History를 확인한다. 종료된 Application에는 upgrade, rollback, uninstall을 다시 실행할 수 없다.

Application Delivery는 Argo CD/Flux를 설치하거나 Git 저장소를 지속 동기화하는 GitOps Controller가 아니다. KlueOps가 관리하는 Helm Release의 명시적 install, upgrade, rollback과 uninstall을 제공한다.

## 화면의 공통 상태

- `Loading`: 최신 데이터를 조회하는 중이다.
- `Empty`: 오류가 아니라 현재 범위에 표시할 데이터가 없는 상태다. 화면의 다음 행동을 따른다.
- `Partial`: 일부 Kubernetes 또는 AI 섹션만 완료됐다. 완료된 근거와 실패 원인을 구분해 확인한다.
- `Fallback`: AI 응답 대신 Kubernetes API 근거로 안전하게 생성된 결과다.
- `Blocked`: 권한, 안전 정책, 필수 환경조건 때문에 실행하지 않았다.

## AI Analysis 수집 품질 확인

분석 결과의 `Analysis Runtime`에서 `Kubernetes 정보 수집` 상태를 먼저 확인한다. `Partial`이면 성공·실패·건너뜀 source 수와 source별 지연 및 오류를 펼쳐 확인한다. 부분 수집 결과의 confidence는 제한되므로, 실패 source가 현재 장애와 관련 있다면 credential, Kubernetes API 연결, 권한을 복구한 뒤 같은 범위를 재분석한다. 수집된 근거와 deterministic fallback은 사용할 수 있지만 이를 전체 리소스가 정상이라는 의미로 해석하지 않는다.

## 상세 매뉴얼

시각 중심 메뉴 설명과 흐름도는 [KlueOps 메뉴 가이드](./K8s-AI-Ops-Platform-menu-guide.docx)를 함께 제공한다. 설치·배포 담당자는 `docs/operations/initial-installation.md`와 `docs/operations/deployment.md`를 사용한다. DOCX 파일명은 기존 배포 경로 호환성을 위해 유지한다.

Word 가이드의 반복 가능한 내용 및 글꼴 보정은 `update_menu_guide.py`가 관리한다. 화면과 운영 절차를 변경한 뒤 이 스크립트를 실행하고 `render_docx.py`로 전체 페이지를 검수한다.

원문 리소스명, Pod 상태, Event reason, 로그, kubectl 명령은 정확성을 위해 원문을 유지하고, 제목과 설명만 선택한 언어로 번역한다.
