# Phase 2 Application Delivery Product Requirements

기준일: 2026-09-15

상태: 핵심 범위와 P2-0 공통 제품 UI 구현 및 로컬 수용 검증 완료, 고급 확장 항목은 후속

## 구현 기준선

2026-09-15 `feature/phase-2` 기준으로 Tenant별 Artifact Hub 검색·가져오기, Chart Library와 Source, 암호화된 Values revision, 대상 Cluster/Namespace 선택과 Namespace 생성, Helm preview/install/upgrade/rollback/uninstall, Application runtime·Service·Ingress·HTTPRoute 조회, Tenant 역할·메뉴 기능 정책·사용자 membership/offboarding, Ollama 및 외부 Provider profile·목적별 routing을 구현했다. P2-0은 짙은 공통 navigation shell, 상단 Tenant/Workspace context bar, semantic visual token과 전역 responsive surface로 실제 제품에 적용했다. Applications는 상태 요약, 검색·상태 필터 목록과 선택 상세 panel 구조로 시안의 정보 위계를 반영했다. 실제 OIDC 로그인 후 검색부터 배포·상태 확인·삭제까지 로컬 Kubernetes에서 검증했으며, Chart Values가 생성한 Ingress와 KlueOps가 Service에 연결한 companion HTTPRoute는 각각 실제 HTTP 응답까지 확인했다.

다음 항목은 설계를 유지하지만 이번 핵심 구현 완료 범위에는 포함하지 않는다.

- `values.schema.json` 기반 Form과 YAML의 양방향 편집
- OCI 및 S3-compatible artifact adapter, provenance 서명 검증
- 자동 DNS/TLS Provider, cross-namespace `ReferenceGrant`·`allowedRoutes`를 포함한 Gateway 전체 preflight와 companion Ingress
- PVC/DNS/TLS 보존 선택을 포함한 고급 uninstall plan
- Local model 삭제·사용 중 보호와 모델별 정식 품질 승격 corpus

따라서 아래 요구사항에서 위 항목은 다음 확장 단계의 승인 기준이며, 현재 제품 동작은 `docs/product/current-product-specification.md`를 단일 기준으로 삼는다.

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
- Chart가 만든 Service를 Cluster 내부, Chart-managed route 또는 KlueOps companion HTTPRoute로 노출한다.
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
| Platform Manager | 기능 활성화, Runner/저장 한도, 외부 AI Provider와 전역 정책 및 모든 Tenant 관리 |
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
5. Application uninstall은 공유 Namespace를 삭제하지 않는다. Helm Release와 KlueOps companion resource 제거가 성공하면 `managed_applications` 및 해당 Application의 Endpoint, Release, Operation, 소비된 Plan metadata를 함께 삭제해 `UNINSTALLED` 잔여 행을 노출하지 않는다. 비동기 Job 결과는 최소 실행 증거로 보존한다. KlueOps 전용 Namespace 삭제는 별도 plan과 exact confirmation을 요구한다.

Release 이름은 `Cluster + Namespace` 안에서 유일해야 한다.

### 6.5 Exposure와 도메인

배포 Wizard에 `Exposure` 단계를 두고 다음 모드를 제공한다.

| 모드 | 동작 | 기본값 |
| --- | --- | --- |
| `NONE` | Chart가 생성한 Service만 사용하고 KlueOps가 외부 경로를 추가하지 않음 | 기본 |
| `CHART_MANAGED` | Chart Values로 Ingress/HTTPRoute/LoadBalancer를 생성 | Chart가 명시적으로 지원할 때 |
| `HTTP_ROUTE` | 렌더링된 Service/Port에 KlueOps companion HTTPRoute 연결 | 사용자가 선택할 때 |

KlueOps HTTPRoute Exposure 입력은 Gateway, hostname, path와 backend Service/Port다. Service와 Port는 현재 Chart/Values를 `helm template`로 렌더링한 결과에서, Gateway는 선택한 Cluster의 실제 `Gateway` 중 HTTP/HTTPS listener가 있는 항목에서 고른다. HTTPRoute backend가 참조하는 포트는 Service의 `spec.ports[].port`이며 Pod 연결용 `targetPort`나 Node 외부 노출용 `nodePort`를 대신 사용하지 않는다. 예를 들어 `nginx.cluster.co.kr`은 wildcard DNS가 Gateway를 가리키면 별도 DNS 변경 없이 hostname으로 사용한다. 그렇지 않으면 ExternalDNS/DNS Provider 연동을 사용하거나 `DNS 설정 필요` 상태와 필요한 record를 사용자에게 안내한다.

현재 구현은 `CHART_MANAGED` preview에서 렌더 결과에 실제 Ingress 또는 HTTPRoute가 없으면 배포를 차단한다. `HTTP_ROUTE`는 Target 화면 조회, Preview와 실제 실행 직전에 대상 Service/Port, parent Gateway, HTTP/HTTPS listener와 Gateway `Accepted/Programmed` 준비 상태를 다시 검사한다. 적용 뒤에는 HTTPRoute `Accepted`와 `ResolvedRefs`를 수집해 `READY`, `APPLIED`, `DEGRADED`로 표시한다. Gateway API CRD, Controller 또는 준비된 Gateway가 없으면 선택과 배포를 차단하고 필요한 확인 명령을 안내한다. Gateway 자동 선택에는 등록 Cluster credential의 Gateway `get/list` 읽기 권한이 필요하며, 조회 권한 부족은 설치되지 않은 상태와 구분해 표시한다. KlueOps는 Tenant 소유 Cluster에 Gateway API/Controller를 자동 설치하지 않는다. Cluster Admin이 나중에 설치한 경우 Target의 `다시 조회`로 이어서 배포하거나, 이미 내부용으로 배포한 Application은 Upgrade에서 HTTPRoute 노출을 추가한다. `allowedRoutes`, cross-namespace `ReferenceGrant`, DNS와 TLS 자동화까지 포함한 전체 preflight는 후속 확장이다.

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

배포 시작은 Applications에서 무조건 Discover로 보내지 않는다. `Application 배포` 진입 시 Tenant Library의 검증된 Chart 선택을 기본·권장 경로로 제공하고, Chart가 없을 때만 Artifact Hub 검색 또는 URL/.tgz 직접 가져오기를 선택하게 한다. Chart 검색·가져오기는 Cluster를 변경하지 않으며 Cluster/Namespace는 Target 단계에서 선택한다.

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

Application은 KlueOps가 배포한 Helm Release만 대상으로 하며 Cluster의 기존 workload 자동 발견과 소유권 편입은 하지 않는다. Uninstall은 Application Release와 연결된 companion resource와 KlueOps의 Application 상세 metadata를 정리하되 Tenant Library Chart와 공유 Namespace는 삭제하지 않는다. Chart artifact 삭제는 별도의 `chart:manage` 작업이다.

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

Application Delivery는 다음 공통 Tenant 보안 기반이 완성된 뒤 구현한다.

```text
허용 = Tenant 기능 ON
    ∩ 선택한 Tenant/Workspace에서 Role이 부여한 Capability
    ∩ 대상 Resource가 허용 Scope 안에 있음
    ∩ 원격 Kubernetes Credential/RBAC 허용(Cluster 작업인 경우)
```

- 메뉴 숨김은 편의 기능일 뿐 보안 경계가 아니다. 직접 URL과 모든 Backend API에서 capability와 scope를 다시 검사한다.
- Tenant 기능 설정은 Role에 없는 권한을 추가하지 않고 기능을 끄는 방향으로만 동작한다.
- Platform Manager는 모든 Tenant와 KlueOps capability를 가지지만 원격 Kubernetes Credential/RBAC를 우회하지 않는다.
- Tenant 간 Resource 이동은 일반 수정이 아니라 Platform Manager 전용 ownership transfer 또는 export/import로 처리한다.

### 9.1 역할과 Capability

| 역할 | 기본 Scope | 책임 |
| --- | --- | --- |
| Platform Manager | Platform | 모든 Tenant, Platform 설정, Provider/Model, Tenant/User/Role, 모든 Resource와 Audit |
| Tenant Admin | Tenant/Workspace | 구성원, 기능 정책, 공유 Chart/Source, Tenant AI routing과 Tenant 내 운영 |
| Cluster Admin | Tenant/Workspace/Cluster | 허용 Cluster 등록·설정, Namespace와 Application 전체 수명주기 |
| Operator | Cluster/Namespace 또는 상위 scope | 분석, 일반 workload 배포·Upgrade·Rollback, Cook Book/Console 실행 |
| Viewer | 비-Platform scope | 허용 Resource, 분석, Application과 Audit의 read-only 조회 |

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

| Capability 영역 | Platform Manager | Tenant Admin | Cluster Admin | Operator | Viewer |
| --- | :---: | :---: | :---: | :---: | :---: |
| Tenant/구성원 | 전체 관리 | 현재 Tenant 관리 | 조회 | 조회 | 조회 |
| Cluster | 전체 관리 | 현재 Tenant 관리 | 허용 scope 관리 | 조회 | 조회 |
| AI Analysis | 실행/조회 | 실행/조회 | 실행/조회 | 실행/조회 | 조회 |
| Chart/Source | 전체 관리 | 현재 Tenant 관리 | 조회 | Chart 조회 | 조회 |
| Values | 편집 | 편집 | 편집 | 편집 | 조회 |
| Application | 전체 수명주기 | 전체 수명주기 | 허용 scope 전체 수명주기 | 배포/Upgrade/Rollback | 조회 |
| Policy/Audit | 관리/조회 | 현재 Tenant 관리/조회 | 허용 scope 관리/조회 | 실행/자기 Audit | 조회 |
| AI Routing | 전체 관리 | 현재 Tenant 관리 | — | — | — |
| Provider/Model | 전체 관리 | — | — | — | — |

Operator의 rollback은 Preview, RBAC와 exact confirmation을 통과한 일반 workload로 제한한다. Uninstall, shared exposure, Namespace 생성과 cluster-scoped resource는 Cluster Admin 이상만 실행한다. Tenant Admin은 Platform Provider credential, Local Model 설치와 다른 Tenant를 관리할 수 없고 Cluster Admin은 Tenant 구성원이나 공유 Source credential을 변경할 수 없다.

### 9.2 Tenant 기능과 메뉴

메뉴는 label별 ACL 대신 안정적인 `featureKey`와 `requiredCapabilities`로 정의한다.

| 메뉴 | Feature key | 표시 조건 |
| --- | --- | --- |
| Overview | `CORE_OVERVIEW` | `cluster:read` |
| Clusters | `CLUSTER_OPERATIONS` | `cluster:read` |
| Cook Book/Console | `KUBERNETES_CONSOLE` | 조회 `cluster:read`, 실행 `operation:execute` |
| AI Analysis/Chat | `AI_OPERATIONS` | 조회 `analysis:read`, 실행 `analysis:run` |
| Application Delivery | `APPLICATION_DELIVERY` | `chart:read` 또는 `application:read` |
| Sources | `APPLICATION_DELIVERY` | `chart:manage` |
| AI Provider Routing | `AI_PROVIDER_ROUTING` | `ai-routing:manage` |
| Provider/Local Models | `AI_PROVIDER_PLATFORM` | `ai-provider:manage` 또는 `ai-model:manage` |
| Users & Access | `ACCESS_CONTROL` | `tenant:member:manage` 또는 Platform Manager |
| Tenants/Platform Runtime | `PLATFORM_ADMINISTRATION` | Platform Manager |

Feature OFF는 `FEATURE_DISABLED`, capability 부족은 403 `CAPABILITY_DENIED`, 다른 Tenant Resource는 존재 여부를 숨기기 위해 404를 반환한다. `CORE_OVERVIEW`, Access Control과 Audit처럼 안전상 필수인 기능은 Tenant에서 비활성화할 수 없다.

### 9.3 OIDC Group과 Company/Tenant Mapping

OIDC Group은 외부 IdP의 사용자 집합이며 Company나 Tenant, KlueOps 역할 그 자체가 아니다. KlueOps는 Group 이름에서 권한을 추측하지 않고 `OIDC issuer + group claim → TenantMembership → Role → Scope`를 명시적으로 저장한다.

Company AA의 권장 예시는 `/companies/aa/cluster-admins`의 u1을 `AA / Cluster Admin / Tenant scope`, `/companies/aa/operators`의 u2·u3를 `AA / Operator / Tenant scope`로 Mapping하는 것이다. 필요하면 u1은 특정 Cluster로, u2·u3는 별도 Group을 통해 특정 Namespace로 scope를 좁힌다. Group Mapping을 기본으로 사용하고 사용자 직접 RoleBinding은 예외·임시 권한에 사용한다. 두 경로의 허용 권한은 합집합이며 MVP에는 명시적 deny를 두지 않는다. Platform Manager Group Mapping은 기존 Platform Manager만 만들 수 있다.

### 9.4 User 생명주기

User 상태는 `INVITED → ACTIVE → SUSPENDED → OFFBOARDED`다.

- Keycloak 관리 연동에서는 User 생성과 required action/초대 메일을 지원한다.
- 외부 OIDC에서는 비밀번호 User를 만들지 않고 email/subject 또는 Group 기반 pending membership을 사전 등록한다.
- `SUSPENDED`는 로그인/API를 차단하되 복구할 수 있고, `OFFBOARDED`는 RoleBinding, session, personal credential과 미완료 Job 접근을 회수한다.
- 운영 UI는 모호한 삭제 대신 `접근 중지`와 `탈퇴 처리`를 사용하고, 탈퇴 전에 영향 Preview와 ownership transfer, exact username 확인을 요구한다.
- 마지막 Platform Manager의 제거·비활성화·탈퇴는 차단하며 User 제거 후에도 Audit actor snapshot을 보존한다.
- Pending membership을 email로 연결할 때는 verified email과 issuer allowlist를 모두 요구한다.

### 9.5 Resource ownership 요구사항

Chart, Source, Values Profile, Tenant Membership/Feature/AI routing처럼 Tenant에서 공유하는 Resource는 `tenant_id`를 직접 소유한다. Analysis, Managed Application, DeploymentPlan, ReleaseOperation처럼 Cluster가 필수인 Resource는 `cluster_id`만 저장하고 불변인 Cluster ownership에서 Tenant/Workspace를 유도한다. 후자는 모든 조회에서 Cluster의 현재 Tenant를 join해 검사하고 Cluster hard delete와 일반 Tenant 이동을 금지한다. 동일 Chart payload는 digest로 deduplicate할 수 있지만 metadata, 승인, Values와 사용 이력은 Tenant별로 분리한다.

Tenant 비활성화 시 신규 mutation과 login scope 선택을 차단하되 Platform Manager의 Audit/export는 유지한다. Tenant 삭제는 dependency report, retention/export, exact confirmation과 비동기 cleanup을 거치는 Platform Manager 전용 workflow이며 운영 화면에서 즉시 hard delete하지 않는다.

모든 object 조회와 mutation은 Tenant → Workspace → Cluster → Namespace scope를 application service에서 다시 평가한다. HTTP method나 Frontend 표시 여부만 신뢰하지 않는다.

## 10. 완료 기준

Phase 2 MVP는 다음 수용 흐름이 격리 namespace에서 통과해야 한다.

1. 기존 제품 주요 route가 공통 design system으로 현대화되고 기능·권한·API/E2E 회귀 없이 P2-0 visual review를 통과
2. Artifact Hub 검색과 version 상세 조회
3. 선택 version 다운로드, SHA-256 및 provenance 상태 표시
4. Tenant A/B Chart와 Values Profile 상호 비노출
5. schema form/YAML/AI patch의 동일 결과와 invalid key 차단
6. 기존 Namespace 선택과 권한 있는 Namespace 생성 plan 검증
7. Cluster 내부/Chart-managed/KlueOps HTTPRoute Exposure preview와 HTTPRoute condition 검증
8. manifest preview, 위험 resource와 RBAC/Gateway preflight 표시
9. install 성공, Application/Pod/Endpoint health와 audit 확인
10. Values upgrade, history, rollback 성공
11. uninstall preview, PVC/companion 보존 선택, exact confirmation과 bounded cleanup
12. Runner timeout/cancel/restart recovery와 Secret/output 마스킹
13. 9B 이하 Ollama model install/검증/목적별 routing과 사용 중 삭제 차단
14. Ollama/OpenAI/Google GenAI profile별 fake adapter 회귀 및 외부 전송 동의 검증
15. Tenant A 사용자가 Tenant B의 Cluster/Analysis/Chart/Application ID를 알아도 404
16. Company AA Group Mapping에서 u1은 지정 Cluster Admin scope, u2·u3는 지정 Operator scope만 획득
17. 선택 Tenant를 바꾸면 `effectiveCapabilities`, 메뉴와 mutation 권한이 즉시 해당 scope로 재계산
18. User suspend/offboard의 session·RoleBinding·credential 회수와 Audit actor 보존
19. 마지막 Platform Manager 제거 차단과 Platform Manager의 모든 Tenant 접근 검증
20. Feature OFF 상태에서 메뉴, 직접 route와 API가 일관된 차단 결과를 반환

2026-09-15 로컬 수용시험에서는 Kubernetes v1.34.1에서 Chart-managed Ingress와 Service 기반 companion HTTPRoute를 각각 배포했다. 두 Application 모두 Pod `1/1 Ready`와 `RUNNING`으로 수렴했고, Ingress Controller와 Envoy Gateway를 경유한 hostname 요청에서 HTTP 200 nginx 응답을 확인했다. HTTPRoute는 `Accepted=True`, `ResolvedRefs=True`였다. 재현 범위와 테스트 전용 Controller는 [로컬 Exposure 수용시험](local-exposure-acceptance.md)에 기록한다.

## 11. 단계별 구현

| 단계 | 범위 |
| --- | --- |
| P2-0 | 기존 KlueOps 전체 UI audit와 제품형 visual refresh, design token/공통 component, responsive·접근성·visual/functional regression gate |
| P2-A | Platform Manager/Tenant 역할, scope별 capability·메뉴 기능 정책, User 생명주기, Tenant 직접/Cluster 파생 ownership guard와 migration |
| P2-B | Artifact Hub/repository/OCI/upload, Tenant Chart Library |
| P2-C | Schema Form, YAML, Values Profile/version/diff |
| P2-D | AI Provider profile과 Values Assistant |
| P2-E | target/Namespace, Exposure, render/policy/RBAC/Gateway/live diff와 승인 UX |
| P2-F | Helm Runner install/upgrade/status/history/rollback/uninstall |
| P2-G | Deployed Application 상세, Endpoint, companion cleanup |
| P2-H | AI Analysis/Incident 연결, 전체 수용시험과 문서화 |

구현 순서는 `P2-0 → P2-A → ... → P2-H`다. P2-0은 이후 화면이 같은 visual system 위에서 개발되도록 하는 선행 기반이며, 임시로 Phase 2 시안과 기존 제품이 서로 다른 디자인 체계로 공존하게 두지 않는다.

P2-A는 다음 순서를 지킨다.

1. P2-A0: Platform Manager 명칭, Tenant Admin, capability/feature catalog와 migration
2. P2-A1: scope별 effective access/session/navigation, implicit Platform Group mapping 제거
3. P2-A2: Cluster 파생 ownership join guard와 Tenant 직접 소유 FK/repository 정비
4. P2-A3: membership, invite/suspend/offboard와 scoped RoleBinding API
5. P2-A4: Users & Access, Access Preview와 Tenant Feature UI
6. P2-A5: Tenant A/B/Platform Manager 보안 수용시험

P2-B의 Chart/Application 구현은 P2-A5를 통과한 뒤 시작한다.
