# Phase 2 Tenant Access, User Lifecycle and Resource Ownership

기준일: 2026-09-15

상태: 목표 설계 확정, 구현 미착수

## 1. 결정 요약

KlueOps의 접근 제어는 `사용자 역할`, `역할이 부여된 scope`, `Tenant 기능 정책`, `대상 resource 소유권`을 모두 만족해야 한다.

```text
허용 = Tenant 기능 ON
    ∩ 현재 Tenant/Workspace에서 Role이 부여한 Capability
    ∩ 대상 Resource가 허용 Scope 안에 있음
    ∩ 원격 Kubernetes Credential/RBAC 허용(Cluster 작업인 경우)
```

- 메뉴 숨김은 편의 기능이며 보안 경계가 아니다. 직접 URL과 모든 Backend API에서 같은 capability/scope를 다시 검사한다.
- Tenant 설정은 권한을 새로 부여하지 않고 기능을 끄는 방향으로만 동작한다. Role에 없는 capability를 Tenant 메뉴 설정으로 허용할 수 없다.
- `Platform Manager`는 Platform scope에서 모든 Tenant, Workspace, Cluster와 모든 제품 capability를 가진다. 단, 원격 Cluster의 Kubernetes credential/RBAC까지 우회하지는 않으며 실제 Kubernetes API가 거부한 작업은 실행할 수 없다.
- 기존 코드의 `PLATFORM_ADMIN`은 1차 migration 동안 Platform Manager의 저장용 호환 이름으로 취급하고 사용자 화면에는 `Platform Manager`만 표시한다. 이후 DB/API migration에서 enum 명칭을 정리한다.
- Tenant 간 Resource 이동은 일반 수정이 아니다. export/import 또는 별도 Platform Manager 전용 ownership transfer workflow로만 수행한다.

## 2. 현재 구현 점검

| 영역 | 현재 상태 | 판단 |
| --- | --- | --- |
| Tenant/Workspace | DB와 selector 존재 | 재사용 |
| Cluster ownership | `clusters.tenant_id`, `workspace_id` NOT NULL 및 FK 존재 | 적합 |
| 역할 | `PLATFORM_ADMIN`, `CLUSTER_ADMIN`, `OPERATOR`, `VIEWER` | Tenant Admin 추가 필요 |
| Capability | Cluster/Analysis/Operation 중심 9개 | Chart/Application/Tenant member capability 확장 필요 |
| Menu guard | Vue route meta와 `auth.hasCapability()` | scope별 capability가 아니라 전체 binding union이라 개선 필요 |
| API guard | capability와 object scope 검사 | 신규 Phase 2 endpoint와 collection query의 tenant predicate 보강 필요 |
| Existing Application | Cluster ID만 저장 | Tenant/Workspace 직접 ownership snapshot 추가 필요 |
| AI Analysis | Cluster ID만 저장 | Tenant/Workspace 직접 ownership snapshot 추가 필요 |
| Phase 2 Chart/Application | 문서상 Tenant scope | composite FK와 repository 규칙 구체화 필요 |
| User | OIDC 최초 로그인 시 JIT 생성, 활성/비활성 지원 | 초대/사전 등록/탈퇴 workflow 필요 |
| Role binding | User/Group + Platform/Tenant/Workspace/Cluster/Namespace scope | 기반 적합, scoped administration 필요 |

중요한 현재 gap:

1. Session의 capability가 모든 RoleBinding에서 평탄화된다. Tenant A의 권한 때문에 Tenant B 메뉴가 보일 수 있으므로 선택한 Tenant/Workspace 기준 `effectiveCapabilities`가 필요하다.
2. OIDC 표준 group을 자동으로 Platform scope RoleBinding처럼 해석한다. multi-tenant 운영에서는 명시적으로 등록된 Platform Manager group을 제외하고 group도 Tenant/Workspace scope binding을 사용해야 한다.
3. `/api/security`는 현재 Platform 수준 `identity:manage`만 전제로 전체 사용자를 조회한다. Tenant Admin을 추가하기 전에 Tenant별 membership endpoint와 query를 분리해야 한다.
4. 기존 Application과 Analysis는 Cluster join으로 Tenant를 알 수 있지만 row 자체에 ownership이 없다. 조회 성능, 삭제된 Cluster의 감사 보존, 잘못된 join 방지를 위해 immutable ownership snapshot을 둔다.

## 3. 역할 모델

### 3.1 역할

| 역할 | Scope | 책임 |
| --- | --- | --- |
| Platform Manager | PLATFORM | 모든 Tenant와 Platform 설정, Provider/Model, Tenant/User/Role, 모든 Resource와 Audit |
| Tenant Admin | TENANT/WORKSPACE | Tenant 구성원, 기능 정책, 공유 Chart Library/Source, Tenant AI routing과 모든 Tenant 내 운영 |
| Cluster Admin | TENANT/WORKSPACE/CLUSTER | 허용 Cluster 등록·설정과 Namespace/Application 전체 수명주기, 분석/정책/Audit 조회 |
| Operator | CLUSTER/NAMESPACE 또는 상위 scope | 분석 실행, 허용 대상 Application 배포·Upgrade·Rollback, Cook Book/Console 실행 |
| Viewer | 모든 비-Platform scope | 허용 Resource와 분석/Application/Audit의 read-only 조회 |

`Tenant Admin`은 Platform Provider credential, Ollama model 설치, 다른 Tenant, Platform runtime 설정을 관리할 수 없다. `Cluster Admin`도 Tenant 공유 Source credential과 Tenant 구성원을 임의 변경할 수 없다.

### 3.2 핵심 Capability

| Capability | Platform Manager | Tenant Admin | Cluster Admin | Operator | Viewer |
| --- | :---: | :---: | :---: | :---: | :---: |
| `tenant:read` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `tenant:manage` | ✓ | Tenant 설정 일부 | — | — | — |
| `tenant:member:manage` | ✓ | ✓ | — | — | — |
| `cluster:read` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `cluster:manage` | ✓ | ✓ | ✓ | — | — |
| `analysis:read` / `analysis:run` | ✓ | ✓ | ✓ | ✓ | read only |
| `chart:read` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `chart:import` / `chart:manage` | ✓ | ✓ | read only | — | — |
| `values:edit` | ✓ | ✓ | ✓ | ✓ | — |
| `application:read` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `application:deploy` / `rollback` | ✓ | ✓ | ✓ | ✓ | — |
| `application:delete` / `exposure` | ✓ | ✓ | ✓ | — | — |
| `operation:execute` | ✓ | ✓ | ✓ | ✓ | — |
| `policy:manage` | ✓ | ✓ | ✓ | — | — |
| `audit:read` | ✓ | ✓ | ✓ | 자기 작업 | read only |
| `ai-routing:manage` | ✓ | ✓ | — | — | — |
| `ai-provider:manage` / `ai-model:manage` | ✓ | — | — | — | — |

Operator의 rollback은 Preview, RBAC, exact confirmation을 통과한 일반 workload 범위로 제한한다. Uninstall, shared exposure, Namespace 생성과 cluster-scoped resource는 Cluster Admin 이상만 실행한다.

## 4. 메뉴 접근 정책

메뉴는 label별 ACL을 직접 저장하지 않고 안정적인 `featureKey`와 `requiredCapabilities`로 정의한다.

| Feature/Menu | Feature key | 표시 조건 |
| --- | --- | --- |
| Overview | `CORE_OVERVIEW` | `cluster:read` |
| Clusters | `CLUSTER_OPERATIONS` | `cluster:read` |
| Cook Book/Console | `KUBERNETES_CONSOLE` | 조회는 `cluster:read`, 실행은 `operation:execute` |
| AI Analysis/Chat | `AI_OPERATIONS` | `analysis:read`; 실행 UI는 `analysis:run` |
| Application Delivery | `APPLICATION_DELIVERY` | `chart:read` 또는 `application:read` |
| Chart Sources | `APPLICATION_DELIVERY` | `chart:manage` |
| AI Providers - Tenant Routing | `AI_PROVIDER_ROUTING` | `ai-routing:manage` |
| AI Providers - Provider/Model | `AI_PROVIDER_PLATFORM` | `ai-provider:manage` 또는 `ai-model:manage` |
| Users & Access | `ACCESS_CONTROL` | `tenant:member:manage` 또는 Platform Manager |
| Tenants/Platform Runtime | `PLATFORM_ADMINISTRATION` | Platform Manager |

`tenant_feature_policies(tenant_id, feature_key, enabled, updated_by, updated_at)`는 Tenant별 기능 ON/OFF를 저장한다. `CORE_OVERVIEW`, 접근 제어와 감사 기능처럼 안전상 필수인 기능은 비활성화할 수 없다. Frontend는 Backend가 반환한 `navigation[]` 또는 현재 scope의 effective capability로 메뉴를 구성하고 label을 권한 데이터로 사용하지 않는다.

직접 URL 접근 결과:

- Feature OFF: `FEATURE_DISABLED` Problem Detail과 Tenant Admin 문의 안내
- Capability 없음: 403 `CAPABILITY_DENIED`
- 다른 Tenant Resource: 존재 여부를 숨기기 위해 404 사용
- Role/Scope가 실행 중 변경됨: mutation 재검사 후 차단, 입력과 plan은 보존하지 않고 재생성 안내

## 5. Resource ownership

### 5.1 직접 소유해야 하는 Resource

다음 row는 `tenant_id NOT NULL`을 가진다. Workspace가 적용되는 Resource는 `workspace_id NOT NULL`도 가진다.

- Cluster, ClusterCredential metadata
- AnalysisSession, Analysis result/command evidence, Incident와 Runbook의 Tenant copy
- Chart, ChartVersion, ChartSource, ValuesProfile/Revision
- DeploymentPlan, Application, ReleaseOperation, CompanionResource
- AI Conversation와 TenantAiRoutingPolicy
- Async Job, Audit event, Notification

Cluster/Namespace 기반 Resource도 `tenant_id`, `workspace_id`, `cluster_id`를 함께 저장한다. DB에는 `(cluster_id, tenant_id, workspace_id)` composite FK를 두어 서로 다른 Tenant의 Cluster를 참조할 수 없게 한다. Application을 참조하는 Analysis에는 `(application_id, tenant_id)` composite FK도 둔다.

ChartVersion은 Tenant Chart의 하위 Resource다. 같은 digest가 여러 Tenant에 존재할 때 payload blob만 content-addressed storage에서 deduplicate할 수 있고, metadata/승인/trust/사용 이력은 Tenant별로 분리한다. Tenant A가 가져온 Chart 존재 여부나 Values를 Tenant B에 노출하지 않는다.

### 5.2 불변성과 삭제

- 생성 후 `tenant_id`는 일반 update로 변경하지 않는다.
- Tenant 비활성화 시 신규 mutation과 login scope 선택을 차단하되 Audit과 export는 Platform Manager가 조회할 수 있다.
- Tenant 삭제는 dependency report, retention/export, exact confirmation과 async cleanup을 거치는 Platform Manager 전용 작업이다.
- User/Resource 삭제 후에도 Audit의 actor/resource snapshot은 보존한다.
- 모든 repository method는 `tenantId`를 필수 인자로 받고 ID 단독 조회 method를 application code에 노출하지 않는다.

## 6. User 생성, 활성화와 삭제

KlueOps는 OIDC 기반이므로 제품 DB 사용자와 로그인 IdP 사용자를 구분한다.

### 6.1 추가

1. Platform Manager 또는 Tenant Admin이 Tenant의 `Users & Access`에서 `User 추가`를 선택한다.
2. Keycloak 관리 연동이 켜진 설치는 username/email, 임시 활성화 방식, Tenant Role/Scope를 입력한다. Backend가 Keycloak Admin API로 사용자를 만들고 required action 또는 초대 메일을 설정한다.
3. 외부 OIDC/기업 IdP 설치는 KlueOps가 비밀번호 사용자를 만들지 않는다. email/subject 또는 IdP group을 `PENDING` membership으로 사전 등록하고 IdP 관리자가 계정을 준비한다.
4. 첫 로그인 때 issuer+subject를 pending membership에 연결한다. email만으로 자동 연결할 때는 verified email과 issuer allowlist를 필수로 한다.
5. 자기 자신에게 Platform Manager를 부여하거나 마지막 Platform Manager를 제거하는 작업은 차단한다.

### 6.2 상태

`INVITED → ACTIVE → SUSPENDED → OFFBOARDED`

- `SUSPENDED`: 로그인과 API access 차단, RoleBinding 유지 가능, 복구 가능
- `OFFBOARDED`: 모든 RoleBinding, session, personal credential과 미완료 Job access 회수; Audit actor snapshot 유지
- 물리 삭제는 retention 만료 후 개인정보 최소화를 위한 별도 cleanup이며 운영 UI의 기본 `삭제`가 아니다.

### 6.3 삭제 UX

UI 명칭은 모호한 `삭제` 대신 `접근 중지`와 `탈퇴 처리`를 사용한다.

1. 영향 Preview: Tenant, Role/Scope, 소유 중인 작업, 실행 중 Job, API token/session, IdP 처리 범위
2. 다른 관리자로 ownership transfer가 필요한 항목 표시
3. exact username 입력
4. Portal session revoke와 RoleBinding 회수
5. Keycloak 연동이면 `IdP 계정 비활성화`를 기본값으로 하며 영구 삭제는 별도 선택·권한·확인
6. 결과와 실패한 cleanup을 Audit에 기록

## 7. API와 Session 계약

Session 응답은 전역 union capability만 반환하지 않는다.

```json
{
  "platformRole": "PLATFORM_MANAGER",
  "selectedScope": { "tenantId": "...", "workspaceId": "..." },
  "effectiveCapabilities": ["cluster:read", "application:deploy"],
  "enabledFeatures": ["CORE_OVERVIEW", "APPLICATION_DELIVERY"],
  "navigation": [{ "key": "applications", "visible": true }]
}
```

권장 endpoint:

- `GET /api/me/access?tenantId=&workspaceId=`: 현재 scope의 capability, feature와 navigation
- `GET/POST /api/tenants/{tenantId}/members`: Tenant membership 조회/초대
- `PATCH /api/tenants/{tenantId}/members/{membershipId}`: suspend/reactivate
- `POST /api/tenants/{tenantId}/members/{membershipId}/offboard-plan`
- `POST /api/tenants/{tenantId}/members/{membershipId}/offboard`
- `GET/PATCH /api/tenants/{tenantId}/features`
- 기존 `/api/security/users`는 Platform Manager용 전체 directory 진단으로 제한

Backend는 request body의 tenantId를 신뢰하지 않고 선택한 Tenant header/session과 parent Resource에서 scope를 유도한다. collection query도 `WHERE tenant_id = :tenantId`를 기본으로 하고 Platform Manager의 cross-tenant 조회만 명시적인 all-tenants endpoint/filter로 제공한다.

## 8. 구현 순서

1. P2-A0: `Platform Manager` 명칭, Tenant Admin, 신규 capability/feature catalog와 migration
2. P2-A1: scope별 effective access/session과 navigation contract; implicit platform group mapping 제거
3. P2-A2: Resource ownership migration 및 composite FK/repository tenant guard
4. P2-A3: Tenant membership, invite/suspend/offboard와 scoped RoleBinding API
5. P2-A4: Users & Access, Role access preview, Tenant feature UI
6. P2-A5: Tenant A/B/Platform Manager 보안 수용시험 후 Chart/Application 구현

Application Delivery 도메인 구현(P2-B 이후)은 P2-A0~A5가 완료된 뒤 시작한다. UI 메뉴 숨김만 먼저 구현하거나 신규 Chart table을 ownership migration보다 먼저 만들지 않는다.

## 9. 필수 수용시험

- Tenant A Operator가 Tenant B Cluster/Analysis/Chart/Application ID를 알아도 404
- Tenant A Role만 있는 사용자가 Tenant B를 선택하면 해당 메뉴와 mutation capability가 없음
- Operator는 배포/rollback 가능, uninstall/source credential/외부 exposure 변경 불가
- Cluster Admin은 허용 Cluster의 전체 Application 작업 가능, Tenant member와 Platform Provider 관리 불가
- Tenant Admin은 Tenant member/feature/Library/routing 관리 가능, 다른 Tenant와 Platform model 관리 불가
- Platform Manager는 모든 Tenant를 선택하고 모든 Resource/API 사용 가능
- Feature OFF 시 메뉴/직접 route/API가 각각 일관된 상태 반환
- User suspend 즉시 신규 요청 차단, session revoke 후 재접속 차단
- 마지막 Platform Manager 제거/비활성화/탈퇴 차단
- offboard 후 RoleBinding과 secret/token access 제거, 기존 Audit actor 표시 유지
- Cluster/Application/Analysis/Chart composite FK가 cross-tenant write 차단
