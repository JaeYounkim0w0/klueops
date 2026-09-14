# Data Model

초기 핵심 모델:

- Tenant
- Workspace
- Cluster
- ClusterCredential
- ManagedApplication
- AnalysisSession
- KubernetesResourceSnapshot
- SyncJob
- AsyncJob
- ClusterSyncSetting
- AuditLog

JSON 데이터는 PostgreSQL `jsonb` 사용을 권장한다.

## Tenant And Workspace Ownership

제품의 관리 계층은 `PLATFORM > TENANT > WORKSPACE > CLUSTER > NAMESPACE`다.

- Tenant는 회사, 고객 또는 독립 업무 조직을 나타낸다.
- Workspace는 Tenant 안의 업무, 서비스 또는 운영팀 단위를 나타낸다.
- Workspace는 정확히 하나의 Tenant에 속한다.
- Cluster는 정확히 하나의 Tenant와 그 Tenant에 속한 Workspace를 함께 참조한다.
- Cluster 이름의 유일성은 플랫폼 전체가 아니라 Tenant 안에서 보장한다.
- Keycloak Group은 역할 바인딩 주체이며 Tenant/Workspace 소유권 모델과 분리한다.

기존 레코드는 Flyway V18에서 안정적인 UUID를 가진 `Default Tenant`와 `Default Workspace`로 이관한다. 신규 등록은 Tenant/Workspace를 명시하며, 레거시 API 호환 입력은 application service에서 기본 배치로 정규화한다.

## Kubernetes Runtime Data Policy

Kubernetes 클러스터는 runtime 상태의 source of truth다.

KlueOps DB는 Kubernetes 전체 상태를 영구 미러링하지 않고, 분석과 운영 UI에 필요한 요약/snapshot/이력만 저장한다.

### KubernetesResourceSnapshot

KubernetesResourceSnapshot은 특정 sync 또는 분석 시점에 수집한 Kubernetes 리소스 요약이다.

필드:

- id
- clusterId
- syncJobId
- namespace
- resourceType
- resourceName
- resourceUid
- status
- summaryJson
- rawJson
- truncated
- collectedAt

저장 원칙:

- 목록/검색/분석에 필요한 요약은 `summaryJson`에 저장한다.
- 상세 manifest/YAML은 DB에 저장하지 않고 사용자가 리소스를 클릭한 시점에 Kubernetes API에서 실시간 조회한다.
- `rawJson`은 하위 호환 컬럼으로만 유지하며 신규 sync에서는 기본적으로 채우지 않는다.
- `metadata.managedFields`, Secret value, Pod log 원문 전체는 저장하지 않는다.
- Secret manifest를 실시간 조회할 때도 `data/stringData` 값은 마스킹한다.
- 대용량 원문 저장 대신 summary 중심 snapshot과 live manifest 조회를 분리해 DB 크기와 민감정보 노출을 최소화한다.

### KubernetesEventSnapshot

KubernetesEventSnapshot은 sync 시점에 수집한 최근 Kubernetes Event다.

필드:

- id
- clusterId
- syncJobId
- namespace
- involvedKind
- involvedName
- reason
- type
- message
- eventTime
- count
- collectedAt

저장 원칙:

- 기본 수집 범위는 최근 1시간이다.
- message에는 secret/token/password 등 민감 정보가 포함되지 않도록 masking을 적용한다.
- 이벤트 원문 전체를 무제한 저장하지 않는다.

### SyncJob

SyncJob은 수동/자동 sync 실행 단위다.

필드:

- id
- asyncJobId
- clusterId
- syncType
- status
- requestedBy
- resourceCount
- eventCount
- startedAt
- completedAt
- errorMessage
- createdAt

SyncJob은 AsyncJob과 연결된다. API는 AsyncJob id를 반환하고, SyncJob은 sync 대상/수집량/오류를 추적한다.

## Migration

Backend schema는 Flyway migration으로 관리한다.

주요 migration:

- `backend/src/main/resources/db/migration/V1__initial_schema.sql`
- `backend/src/main/resources/db/migration/V2__cluster_registration.sql`
- `backend/src/main/resources/db/migration/V18__strict_tenant_workspace.sql`

초기 구현 테이블:

- `async_jobs`
- `audit_logs`
- `clusters`
- `cluster_credentials`
- `cluster_sync_settings`
- `tenants`
- `workspaces`
- `role_bindings`의 tenant/workspace scope

Kubernetes sync 구현 테이블:

- `sync_jobs`
- `kubernetes_resource_snapshots`
- `kubernetes_event_snapshots`

credential type은 다음 값을 우선 지원한다.

- `KUBECONFIG`
- `SERVICE_ACCOUNT_TOKEN`

JPA는 `ddl-auto=validate`로 실행하며, schema 변경은 JPA 자동 생성이 아니라 migration으로 반영한다.
