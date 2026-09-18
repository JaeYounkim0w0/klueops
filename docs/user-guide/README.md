# KlueOps 사용자 가이드

이 디렉터리는 현재 `main` 브랜치와 실제 로컬 Kubernetes 설치를 기준으로 한 운영자 문서를 관리한다. 화면 위치를 기준으로 설명하지 않고, 운영자가 수행하려는 업무와 Tenant, 업무 그룹, Cluster, Namespace 범위를 기준으로 안내한다.

## 전체 운영 가이드

[KlueOps 운영 가이드](./K8s-AI-Ops-Platform-menu-guide.docx)는 실제 OIDC 로그인 후 캡처한 54개 화면을 사용한다. 21개 메뉴 최초 화면뿐 아니라 독립 상세 페이지, 탭, 팝업과 하위 작업 흐름까지 다음 범위로 설명한다.

- 역할과 Tenant 범위, 공통 운영 흐름
- Dashboard, 실시간 운영 판단, Fleet Command와 Incident 처리
- 클러스터 등록, 연결·동기화, resource 조회와 명령 안전
- Artifact Hub 검색, Chart Library, Custom Values와 Helm Application 배포
- AI Analysis, AI Chat, Runbook과 AI 신뢰도 검토
- Policy, Audit, 운영 신뢰성, Runtime과 보관 정책
- Local Ollama·외부 LLM Provider, 사용자·역할·Tenant 관리
- Fleet 추세·검증 랩, Incident 생성과 7개 상세 탭
- 클러스터 등록·준비도·리소스 상세·Kubernetes 콘솔
- Values Studio·AI 도우미·배포 Wizard와 Chart 업로드·제거
- AI 분석 초보자·숙련자 상세, Runbook 편집과 정책 이력
- Provider Profile, 사용자 초대, OIDC Group Mapping과 기능 정책
- 상태와 오류 해석, 실무 문제 해결 체크리스트

화면에는 개인 계정, 실제 클러스터 식별자와 내부 주소 대신 공개 가능한 예시 값이 표시된다. 로컬 HTTP 설치의 운영 안전 경고는 의도적으로 유지해 실제 운영 환경에서 TLS와 Secure cookie가 필요함을 보여준다.

## 권장 운영 흐름

1. 로그인 후 현재 Tenant와 업무 그룹을 확인한다.
2. Dashboard와 실시간 운영 판단에서 대응 우선순위를 정한다.
3. 대상 클러스터의 마지막 동기화 시각과 현재 resource 상태를 확인한다.
4. AI Analysis 또는 Runbook에서 수집 범위, 근거와 검증 순서를 확인한다.
5. 명령 작업 공간에서는 조회 명령부터 실행하고 변경은 Preview 또는 dry run을 거친다.
6. 조치 후 같은 범위를 재분석하고 Incident와 Audit에 결과가 연결됐는지 확인한다.

## Helm Application 배포

1. Chart Library에서 검증된 Chart를 선택한다. 필요한 Chart가 없으면 Discover, Sources 또는 tgz 가져오기를 사용한다.
2. Values Profile을 만들고 YAML 또는 schema Form으로 override를 저장한다. 빈 입력은 빈 object로 처리하고 exact Chart의 Helm render를 통과해야 한다.
3. AI Values 제안은 선택한 Chart 원본 Values·주석·Schema를 읽고 필요한 자료를 추가 조회한다. `이해한 요청`, `요청과 Chart 설정 연결`, 기존/제안 변경 비교와 YAML을 검토한다. 보충 질문은 요청을 수정해 다시 생성한다. 비밀번호나 token 대신 지원되는 기존 Secret 이름/key를 사용한다. YAML·Helm lint/렌더·제시된 리소스 조건을 통과한 결과만 적용한다. Chart에 없는 Values 경로로 매핑되면 적용하지 않고, 거부된 경로와 사유를 경고로 확인한 뒤 Chart가 제공하는 정확한 필드로 요청을 수정한다. 확인하지 못한 요구사항의 경고를 확인하며 실제 배포·접속은 별도 검사한다.
4. Cluster와 Namespace를 선택하고 Helm release Secret의 `get/list/create` 권한을 확인한다.
5. HTTPRoute는 렌더링된 Service의 `spec.ports[].port`와 Ready Gateway를 사용한다. `targetPort`나 `nodePort`를 HTTPRoute backend port로 사용하지 않는다.
6. PostgreSQL과 Redis 같은 TCP 서비스는 port-forward, NodePort, LoadBalancer 또는 TCPRoute를 사용한다.
7. Preview와 정확한 확인 절차를 거쳐 비동기 배포를 시작하고 Applications에서 최종 상태, Runtime, endpoint와 History를 확인한다.

Application Delivery는 Argo CD나 Flux를 설치하거나 Git repository를 지속 동기화하는 GitOps Controller가 아니다. KlueOps가 관리하는 Helm Release의 명시적 install, upgrade, rollback과 uninstall을 제공한다.

## 화면 상태 해석

| 상태 | 의미 |
| --- | --- |
| `Loading` | 최신 데이터를 조회 중이다. 장시간 유지되면 API와 session 상태를 확인한다. |
| `Empty` | 현재 범위에 표시할 데이터가 없다. 화면의 시작 행동을 수행한다. |
| `Partial` | 일부 Kubernetes 또는 AI source만 완료됐다. 실패 source와 confidence를 확인한다. |
| `Fallback` | AI 대신 Kubernetes 근거와 결정론적 규칙으로 결과를 생성했다. |
| `Blocked` | 권한, 안전 정책 또는 필수 환경조건 때문에 실행하지 않았다. |
| `Degraded` | Route 또는 resource 참조가 수락·해결되지 않았다. 유효한 접근 경로로 취급하지 않는다. |

## 문서 재생성

화면 변경 후 비밀번호를 파일에 기록하지 않고 환경 변수로 전달해 캡처한다.

```bash
AIOPS_GUIDE_USERNAME='<username>' \
AIOPS_GUIDE_PASSWORD='<password>' \
node docs/user-guide/capture_product_screenshots.mjs
```

Word 가이드를 생성하고 번들 LibreOffice에서 한글 글꼴 경로를 명시해 검증한다.

```bash
PY='/path/to/workspace-dependencies/python/bin/python3'
export FONTCONFIG_FILE="$PWD/docs/user-guide/fontconfig.xml"

"$PY" docs/user-guide/update_menu_guide.py
"$PY" /path/to/documents-skill/render_docx.py \
  docs/user-guide/K8s-AI-Ops-Platform-menu-guide.docx \
  --output_dir /tmp/klueops-guide-render
```

DOCX 파일명은 기존 배포 경로 호환성을 위해 유지한다. 설치와 배포 담당자는 [최초 설치](../operations/initial-installation.md), [변경 배포](../operations/component-deployment.md), [OIDC와 보안 구성](../security/operations/keycloak-and-security-configuration.md)을 함께 사용한다.
