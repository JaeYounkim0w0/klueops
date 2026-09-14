# RBAC And Scope Model

## 권한 판단 순서

`인증 사용자 -> 계정 활성 상태 -> application role -> tenant/workspace/cluster/namespace scope -> Kubernetes SSAR -> dry-run/rollback guard`

플랫폼 권한과 대상 Kubernetes ServiceAccount 권한은 서로 대체하지 않는다. 둘 중 하나라도 거부하면 조치를 실행하지 않는다.

## 역할

| 역할 | 목적 |
| --- | --- |
| `PLATFORM_ADMIN` | 사용자/권한/설정과 전체 클러스터 관리 |
| `CLUSTER_ADMIN` | 할당된 클러스터의 설정, 동기화, 변경 조치 |
| `OPERATOR` | 할당 범위 조회, 분석, 안전한 운영 조치 |
| `VIEWER` | 할당 범위 읽기 전용 |

## 범위

- `PLATFORM`: 역할의 capability를 전체 플랫폼에 적용한다. 전사 Viewer/Operator 같은 fleet 역할에도 사용할 수 있으며 `PLATFORM_ADMIN`만 관리자 capability를 가진다.
- `TENANT`: 회사, 고객 또는 독립 업무 조직 전체에 적용한다.
- `WORKSPACE`: Tenant 안의 업무, 서비스 또는 운영팀 단위에 적용한다.
- `CLUSTER`: 특정 cluster UUID 전체 namespace.
- `NAMESPACE`: 특정 cluster UUID의 정확한 namespace.

상위 범위는 `PLATFORM > TENANT > WORKSPACE > CLUSTER > NAMESPACE` 순서로 하위 범위를 포함한다. Cluster의 Tenant/Workspace 소속은 요청값이 아니라 DB의 cluster placement를 조회해 판정한다. 다른 Tenant의 같은 이름 Workspace, Cluster, Namespace는 서로 포함하지 않는다.

바인딩이 없으면 기본 거부한다. 사용자 바인딩과 OIDC group 바인딩은 합집합으로 평가하되, 비활성 계정은 항상 거부한다. Keycloak Group은 역할을 연결하는 외부 IdP 그룹이고 제품의 Tenant/Workspace와는 별도 객체다.

## Capability Matrix

| Capability | PLATFORM_ADMIN | CLUSTER_ADMIN | OPERATOR | VIEWER |
| --- | --- | --- | --- | --- |
| `platform:admin` | O | - | - | - |
| `identity:manage` | O | - | - | - |
| `cluster:read` | O | O | O | O |
| `cluster:manage` | O | O | - | - |
| `analysis:read` | O | O | O | O |
| `analysis:run` | O | O | O | - |
| `operation:execute` | O | O | O | - |
| `policy:manage` | O | O | - | - |
| `audit:read` | O | O | - | - |

## API 적용 규칙

- `/api/auth/me`, health/info, OIDC callback만 인증 예외이다.
- `/api/security/**`는 `identity:manage`가 필요하다.
- HTTP method만으로 변경 권한을 추론하지 않는다. controller 또는 application service 경계에서 명시 capability를 선언한다.
- cluster/namespace 식별자가 있는 요청은 object scope를 반드시 평가한다.
- Tenant, Workspace, Cluster 목록 응답은 사용자가 접근 가능한 scope로 서버에서 필터링한다. 전체 목록을 반환한 뒤 UI에서 숨기는 방식은 금지한다.
- 클러스터 등록 시 Tenant와 Workspace 관계 및 활성 상태를 검증하고, Cluster에 두 식별자를 필수 소유권으로 저장한다.
- cluster 식별자가 없는 fleet/global endpoint는 `PLATFORM` 범위만 허용한다. 예외인 cluster 목록은 서버에서 허용 cluster만 필터링한다.
- 자기 계정 비활성화와 존재하지 않는 사용자에 대한 역할 바인딩은 거부한다.

## 현재 적용 경계

- Tenant/Workspace 관리, 클러스터 등록/목록, 역할 바인딩, object-scope 권한 판정은 엄격한 계층 모델을 적용한다.
- clusterId가 포함된 운영/분석 API는 저장된 cluster placement를 통해 Tenant/Workspace 상속을 적용한다.
- clusterId가 없는 fleet/global 집계 API는 현재 `PLATFORM` 범위만 허용한다. Tenant/Workspace 전체 집계는 여러 cluster를 서버에서 안전하게 결합하는 전용 API로 확장하며, 클라이언트 측 결과 병합으로 우회하지 않는다.
