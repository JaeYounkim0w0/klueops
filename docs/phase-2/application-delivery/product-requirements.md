# Phase 2 Application Delivery Product Requirements

기준일: 2026-09-15

상태: 설계 완료, 구현 미착수

## 1. 제품 정의

Application Delivery는 Tenant 사용자가 Helm Chart를 검색하거나 직접 등록하고, 원본 Chart를 변경하지 않은 채 Custom Values를 작성해 권한이 있는 Kubernetes Cluster와 Namespace에 배포하는 기능이다.

```text
Chart 검색/등록 → Tenant Library → Custom Values → Target/Exposure → Preview → 승인 → Helm 배포 → Application 운영
```

Application은 이 기능에서 하나의 Helm Release를 의미한다. 기존 Kubernetes workload 자동 발견, Argo CD/Flux 연동, GitOps Controller 제공은 포함하지 않는다.

이 문서에서 `Deployment`는 문맥에 따라 구분한다. `배포 Wizard`는 새 Application을 만드는 일시적 흐름, `배포 작업`은 Job Center가 추적하는 비동기 operation, Kubernetes `Deployment`는 Application의 Workload resource다. 혼동을 막기 위해 독립 메뉴 이름으로 `Deployments`를 사용하지 않는다.

## 2. 목표

### 2.1 P2-0 최우선 선행 요구사항: 기존 제품 UI 현대화

Application Delivery 기능 구현보다 먼저 현재 KlueOps의 기존 화면을 제품 수준의 UI/UX로 현대화한다. 현재 화면의 기능, API 계약, Tenant/Workspace scope와 권한 동작은 유지하되, 프로토타입처럼 보이는 불균일한 layout, 과도한 정보 밀도, 단순 raw form/table, 약한 상태 위계와 화면별 표현 차이를 제거한다.

목표 품질선은 이 Phase 2 HTML 시안의 visual language다. 짙은 navigation shell, 명확한 page hierarchy, 충분한 여백, 정돈된 card/table/form, 일관된 status/risk 표현, 단계형 workflow, 세련된 modal·confirmation과 beginner-friendly 설명을 실제 제품 전체에 공통 적용한다. HTML을 화면별로 그대로 복사하지 않고 재사용 가능한 Vue component와 design token으로 제품화한다.

P2-0 적용 범위:

1. Login과 global shell/sidebar/header/Tenant·Workspace context
2. Overview, Clusters, Cluster Detail과 Kubernetes Console
3. Analysis, AI Chat, Triage, Incidents, Runbooks와 Fleet/Operations 화면
4. Policies, Audit, Access, AI/Runtime와 사용자 설정
5. 이후 추가되는 모든 Application Delivery 화면

P2-0 완료 조건:

- color, typography, spacing, radius, elevation과 responsive breakpoint를 semantic design token으로 정의한다.
- button, input/select, tabs, card, table, status/risk badge, empty/loading/error state, toast, drawer와 modal을 공통 component/style로 통합한다.
- Kubernetes 용어에는 짧은 의미와 다음 안전 행동을 함께 제공하고, 위험 mutation은 preview와 confirmation을 시각적으로 분리한다.
- desktop 1280/1440/1680과 tablet/mobile 기준에서 navigation, table, drawer와 sticky action이 깨지지 않는다.
- keyboard focus, contrast, modal focus trap, status의 비색상 표현과 screen-reader label을 검증한다.
- 기존 route, 기능, 권한, API 호출과 E2E 핵심 흐름의 동작 회귀가 없어야 한다.
- 기준 screenshot과 주요 상태별 visual regression을 만들고 HTML 시안과 동등한 완성도를 사용자 검토로 승인받는다.
- 정적 inline style과 화면별 중복 helper를 늘리지 않고 `frontend/src/styles`, 공통 component, composable과 store를 재사용한다.

P2-0은 단순 색상 변경이 아니라 기존 제품 전체의 정보 구조와 상호작용 품질을 정돈하는 작업이다. 다만 기능 의미나 업무 흐름을 임의로 변경하지 않으며, 필요한 구조 변경은 화면별 before/after와 regression 결과를 검토한 뒤 반영한다. P2-0 품질 게이트를 통과하기 전에는 새로운 Application Delivery 화면 구현을 main에 완료 기능으로 병합하지 않는다.

### 2.2 Application Delivery 기능 목표

- Artifact Hub에서 Helm package를 검색하고 버전, publisher, 문서와 보안 metadata를 비교한다.
- Artifact Hub가 가리키는 원본 Helm repository 또는 OCI registry에서 정확한 Chart version을 가져온다.
- `.tgz`, Helm repository와 OCI reference를 Tenant별 Chart Library에 등록한다.
- 원본 Chart artifact는 immutable SHA-256으로 보존하고 Custom은 versioned Values Profile만 지원한다.
- `values.schema.json`이 있으면 beginner-friendly form을 제공하고 YAML editor와 양방향 동기화한다.
- LLM이 사용자 요구와 sanitized Cluster capability를 근거로 Values patch를 제안한다.
- Chart, Values, 생성 manifest, Cluster scope와 RBAC를 결정론적으로 검증한다.
- preview, exact confirmation, async job, audit와 사후 health 검증을 거쳐 Helm install/upgrade/rollback/uninstall을 수행한다.
- 기존 Namespace를 기본 대상으로 사용하고 권한·정책이 허용하는 경우에만 Namespace 생성을 지원한다.
- Chart가 만든 Service를 Internal only, Chart-managed route 또는 KlueOps-managed HTTPRoute/Ingress로 노출한다.
- 배포 후 Application 상세에서 Workload, Pod, Service, 접근 URL, Route/DNS/TLS와 Release history를 함께 운영한다.
- Ollama endpoint의 설치 모델을 조회하고 9B 이하 모델을 관리자 승인으로 추가해 AI 목적별로 라우팅한다.
- Application Delivery를 사용하지 않는 설치에서는 Helm Runner와 background work를 비활성화한다.

## 3. 비목표

- Chart template, helper, dependency 파일의 브라우저 편집
- 원본 Chart의 in-place 수정 또는 KlueOps에서 Chart 재패키징
- Argo CD, Flux 또는 자체 GitOps reconciliation
- Artifact Hub나 외부 registry로 Chart publish
- Helm plugin 또는 임의 shell command 실행
- `latest` 같은 mutable version을 이용한 자동 배포
- 승인 없는 자동 upgrade와 여러 Cluster 일괄 배포
- PostgreSQL, object storage 등 application data의 backup/restore 자동화

사용자가 template을 수정한 Chart가 필요하면 외부 개발 도구에서 새 `.tgz` version을 만들어 Tenant Library에 업로드한다.

## 4. 주요 사용자

| 사용자 | 주요 작업 |
| --- | --- |
| Platform Admin | 기능 활성화, Runner/저장 한도, 외부 AI Provider와 전역 정책 관리 |
| Tenant Admin | Tenant Chart source/credential, 허용 Cluster와 AI profile 관리 |
| Application Operator | Chart 검색/import, Values Profile 작성, preview와 배포 수행 |
| Viewer | Chart, Values diff, Release 상태와 Audit 조회 |

## 5. 정보 구조

```text
Applications
├─ Discover
│  ├─ Artifact Hub 검색
│  └─ URL로 Chart 직접 가져오기
├─ Chart Library
│  ├─ Charts와 Immutable Version
│  ├─ Sources: Helm Repository/OCI Registry
│  ├─ Uploads
│  └─ Values Profile
├─ Deployed Applications
│  ├─ Workloads/Pods
│  ├─ Network & Endpoints
│  ├─ Configuration/Resources
│  └─ History: Install/Upgrade/Rollback/Uninstall
└─ (전역 공통) Job Center
   ├─ 실행 중·최근 Job progress
   └─ 실패 단계, rollback/cleanup과 대상 Application 연결
```

Job Center는 Applications의 하위 메뉴가 아니라 import, model download 등 모든 장시간 작업이 공유하는 기존 전역 surface다. 별도 Application `Operations` 화면이나 `Deployments` 메뉴를 추가하지 않는다.

## 6. 핵심 사용자 흐름

### 6.1 Artifact Hub에서 가져오기

1. 사용자는 이름, category, repository, official/verified 상태로 Helm package를 검색한다.
2. 상세 화면에서 README, available versions, default values, values schema와 security report를 검토한다.
3. 정확한 version을 선택하고 원본 repository/OCI URL을 확인한다.
4. KlueOps가 server-side로 Chart를 내려받아 size/path/symlink, metadata, digest와 provenance를 검사한다.
5. 사용자는 결과를 확인한 후 현재 Tenant Library에 import한다.

Artifact Hub 공개 API는 package search, Helm package/version 상세, values, values schema, templates와 security report endpoint를 제공한다. Artifact Hub 자체는 application을 설치하지 않으므로 KlueOps가 원본 source에서 Chart를 획득하고 검증·배포한다.

- <https://artifacthub.io/docs/api/>
- <https://artifacthub.io/docs/topics/faq/>

### 6.2 직접 가져오기

- `.tgz` upload
- HTTPS Helm repository와 chart/version
- OCI `oci://registry/namespace/chart`와 SemVer version

Browser가 임의 URL을 직접 fetch하지 않는다. Backend가 허용 scheme, DNS/IP, redirect, TLS와 size를 검사해 SSRF를 차단한다. Private source credential은 Tenant scope로 암호화하며 저장 후 다시 표시하지 않는다.

Discover의 `URL로 직접 가져오기`는 특정 Chart/version을 한 번 조회해 Tenant Library로 import하는 흐름이다. Chart Library의 `Sources`는 반복 사용할 Helm Repository/OCI Registry, credential, 동기화와 health를 영구 관리한다. 직접 가져오기 중 `이 Source를 Tenant에 저장`을 선택한 경우에만 Source가 등록된다.

Import 상태는 `IMPORTING → VALIDATING → READY | REJECTED`로 노출하며 `READY` 이후 Chart Library에 배포 가능한 버전으로 표시한다.

### 6.3 Custom Values

1. immutable Chart version을 선택한다.
2. 새 Values Profile을 생성하거나 기존 revision을 복제한다.
3. Schema Form, YAML Editor 또는 AI Assistant로 값을 수정한다.
4. schema/type/unknown key와 Secret pattern을 검사한다.
5. 저장 시 전체 values, parent revision, author, SHA-256과 redacted diff를 기록한다.

### 6.4 대상과 Namespace

1. 사용자는 Tenant와 Workspace에 연결되고 `application:deploy` 권한이 있는 Cluster를 선택한다.
2. 기본적으로 접근 가능한 기존 Namespace만 선택한다.
3. `namespace:create` capability와 Cluster 정책이 모두 허용할 때만 새 Namespace 생성을 제공한다.
4. 새 Namespace 계획에는 ResourceQuota, LimitRange, 기본 NetworkPolicy와 소유 정책을 Preview한다.
5. Application uninstall은 공유 Namespace를 삭제하지 않는다. KlueOps 전용 Namespace 삭제는 별도 plan과 exact confirmation을 요구한다.

Release 이름은 `Cluster + Namespace` 안에서 유일해야 한다.

### 6.5 Exposure와 도메인

배포 Wizard에 `Exposure` 단계를 두고 다음 모드를 제공한다.

| 모드 | 동작 | 기본값 |
| --- | --- | --- |
| `INTERNAL_ONLY` | Chart가 생성한 ClusterIP Service만 사용 | 기본 |
| `CHART_MANAGED` | Chart Values로 Ingress/HTTPRoute/LoadBalancer를 생성 | Chart가 명시적으로 지원할 때 |
| `KLUEOPS_MANAGED` | 렌더링된 Service/Port에 companion HTTPRoute 또는 Ingress 연결 | 사용자가 선택할 때 |

KlueOps-managed Exposure 입력은 Gateway/Listener, hostname, path, backend Service/Port, TLS와 DNS mode다. 예를 들어 `nginx.cluster.co.kr`은 wildcard DNS가 Gateway를 가리키면 별도 DNS 변경 없이 hostname으로 사용한다. 그렇지 않으면 ExternalDNS/DNS Provider 연동을 사용하거나 `DNS 설정 필요` 상태와 필요한 record를 사용자에게 안내한다.

HTTPRoute는 Gateway API capability, parent Gateway의 allowedRoutes, Service/Port와 `Accepted`/`ResolvedRefs` 조건을 사전·사후 검사한다. Gateway API가 없으면 정책에 따라 Ingress 또는 Internal only를 제안한다. Chart가 이미 Route를 생성하면 중복 companion resource를 만들지 않는다.

TLS는 Gateway wildcard certificate, existing TLS Secret 또는 선택형 cert-manager 연동만 사용한다. Certificate와 DNS를 자동 생성하는 것처럼 표시하지 않고 실제 연동 상태를 구분한다.

### 6.6 배포

1. Chart version과 Values Profile revision을 고정한다.
2. Tenant에 속한 Cluster, 허용 Namespace와 고유 Release 이름을 선택한다.
3. Exposure mode와 Service/Port/hostname/TLS/DNS를 선택한다.
4. render, policy, live diff와 RBAC/Gateway preflight를 실행한다.
5. Helm resource와 companion resource의 생성·변경·삭제, cluster-scope, hook와 위험 설정을 표시한다.
6. exact confirmation 후 async Helm job을 시작한다.
7. 요청이 수락되면 Application을 `DEPLOYING` 상태로 만들어 Deployed Applications에 즉시 표시하고 Job Center/Job Dock에서 진행을 추적한다.
8. 성공 후 Release/Pod/Endpoint health를 검증하고, 실패 시 Application에 실패 단계와 안전한 retry/cleanup 동작을 표시한다.

### 6.7 Application 운영

- Overview, Workload/Pod health, restart와 Event 조회
- Service, HTTPRoute/Ingress, Gateway, DNS/TLS 상태와 접근 URL 조회
- 적용 Values, rendered resource와 companion resource 조회
- Chart 또는 Values revision upgrade preview
- Helm history와 revision rollback
- uninstall preview, PVC/DNS/TLS/companion resource 보존 선택과 exact confirmation
- 실행 전후 resource snapshot, output hash와 Audit
- Application/Namespace AI Analysis로 이동

Deployed Applications에는 `DEPLOYING`, `UPGRADING`, `ROLLING_BACK`, `UNINSTALLING` 같은 진행 상태와 `FAILED`도 포함한다. 전역 Job Center는 실행 단위의 queue/progress/cancel/retry를 담당하고, Application Detail의 History는 해당 Application에 귀속된 완료·실패 operation과 Audit을 영구 조회한다. 동일한 operation을 별도 화면에 중복 저장하지 않는다.

Application은 KlueOps가 배포한 Helm Release만 대상으로 하며 Cluster의 기존 workload 자동 발견과 소유권 편입은 하지 않는다. Uninstall은 Application Release와 연결된 companion resource만 정리하고 Tenant Library Chart는 삭제하지 않는다. Chart artifact 삭제는 별도의 `chart:manage` 작업이다.

## 7. Custom Values와 AI Assistant

AI는 배포자가 아니라 Values 제안자다. 출력은 다음 provider-neutral 계약만 사용한다.

```json
{
  "schemaVersion": "helm-values-suggestion.v1",
  "summary": "요청을 반영한 변경 설명",
  "patch": {
    "replicaCount": 3,
    "service": { "type": "ClusterIP" }
  },
  "assumptions": [],
  "warnings": [],
  "evidence": ["values.schema.json#/properties/replicaCount"]
}
```

- Chart README, comments와 templates는 신뢰하지 않는 data이며 system instruction이 아니다.
- Secret value, Kubernetes credential, ConfigMap 원문과 인증서는 prompt에 포함하지 않는다.
- LLM patch는 허용된 Values key에만 merge하고 전체 파일을 임의 교체하지 않는다.
- schema validation과 `helm template`이 실패하면 제안을 적용하거나 배포하지 않는다.
- 사용자가 diff를 승인하기 전에는 Values Profile revision을 만들지 않는다.
- Provider failure 시 수동 Form/YAML 편집은 계속 사용할 수 있어야 한다.

## 8. Chart 신뢰 등급

| 상태 | 의미 |
| --- | --- |
| VERIFIED | provenance/signature와 package digest 검증 성공 |
| CHECKSUMMED | SHA-256은 고정됐으나 publisher signature 없음 |
| UNVERIFIED | source/digest 검증을 완료하지 못해 배포 차단 또는 관리자 예외 필요 |
| REJECTED | 구조, size, metadata 또는 정책 검사 실패 |

Artifact Hub의 official/verified publisher 표시는 검색 판단 근거이지 KlueOps package signature 검증을 대체하지 않는다. Helm provenance가 제공되면 `helm pull --verify`와 keyring 정책으로 검증한다.

- <https://helm.sh/docs/helm/helm_pull/>
- <https://helm.sh/docs/topics/provenance/>

## 9. 권한

| Capability | 동작 |
| --- | --- |
| `chart:read` | Discover와 Tenant Library 조회 |
| `chart:import` | 외부 Chart import와 `.tgz` upload |
| `chart:manage` | source/credential, archive, retention과 미사용 artifact 삭제 |
| `values:edit` | Values Profile 생성·revision 저장·AI 제안 |
| `application:read` | Release와 history 조회 |
| `application:deploy` | install/upgrade와 preview |
| `application:rollback` | Helm revision rollback |
| `application:delete` | uninstall plan 실행과 companion resource cleanup |
| `application:exposure` | HTTPRoute/Ingress/DNS/TLS exposure 계획과 변경 |
| `namespace:create` | 정책에 맞는 Namespace 생성 계획과 실행 |
| `ai-provider:manage` | Provider profile과 Tenant 허용 정책 관리 |
| `ai-model:manage` | Ollama local model 조회·다운로드·검증·삭제 |

모든 object 조회와 mutation은 Tenant → Workspace → Cluster → Namespace scope를 application service에서 다시 평가한다. HTTP method나 Frontend 표시 여부만 신뢰하지 않는다.

## 10. 완료 기준

Phase 2 MVP는 다음 수용 흐름이 격리 namespace에서 통과해야 한다.

1. 기존 제품 주요 route가 공통 design system으로 현대화되고 기능·권한·API/E2E 회귀 없이 P2-0 visual review를 통과
2. Artifact Hub 검색과 version 상세 조회
3. 선택 version 다운로드, SHA-256 및 provenance 상태 표시
4. Tenant A/B Chart와 Values Profile 상호 비노출
5. schema form/YAML/AI patch의 동일 결과와 invalid key 차단
6. 기존 Namespace 선택과 권한 있는 Namespace 생성 plan 검증
7. Internal/Chart-managed/KlueOps-managed Exposure preview와 HTTPRoute condition 검증
8. manifest preview, 위험 resource와 RBAC/Gateway preflight 표시
9. install 성공, Application/Pod/Endpoint health와 audit 확인
10. Values upgrade, history, rollback 성공
11. uninstall preview, PVC/companion 보존 선택, exact confirmation과 bounded cleanup
12. Runner timeout/cancel/restart recovery와 Secret/output 마스킹
13. 9B 이하 Ollama model install/검증/목적별 routing과 사용 중 삭제 차단
14. Ollama/OpenAI/Google GenAI profile별 fake adapter 회귀 및 외부 전송 동의 검증

## 11. 단계별 구현

| 단계 | 범위 |
| --- | --- |
| P2-0 | 기존 KlueOps 전체 UI audit와 제품형 visual refresh, design token/공통 component, responsive·접근성·visual/functional regression gate |
| P2-A | domain/API 재정의, Tenant scope, capability와 migration |
| P2-B | Artifact Hub/repository/OCI/upload, Tenant Chart Library |
| P2-C | Schema Form, YAML, Values Profile/version/diff |
| P2-D | AI Provider profile과 Values Assistant |
| P2-E | target/Namespace, Exposure, render/policy/RBAC/Gateway/live diff와 승인 UX |
| P2-F | Helm Runner install/upgrade/status/history/rollback/uninstall |
| P2-G | Deployed Application 상세, Endpoint, companion cleanup |
| P2-H | AI Analysis/Incident 연결, 전체 수용시험과 문서화 |

구현 순서는 `P2-0 → P2-A → ... → P2-H`다. P2-0은 이후 화면이 같은 visual system 위에서 개발되도록 하는 선행 기반이며, 임시로 Phase 2 시안과 기존 제품이 서로 다른 디자인 체계로 공존하게 두지 않는다.
