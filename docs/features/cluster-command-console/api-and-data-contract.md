# API And Data Contract

## 1. API 원칙

- `/api/clusters/{clusterId}` 경로로 대상 Cluster를 고정한다.
- Namespace는 request에 포함하되 서버가 사용자 scope와 command option을 교차 검증한다.
- 모든 API는 OpenAPI annotation, ProblemDetail, request/correlation ID를 제공한다.
- Frontend는 OpenAPI에서 생성한 Orval client만 사용한다.
- streaming endpoint를 제외한 목록 API는 pagination을 적용한다.

## 2. Endpoints

### Capability

```http
GET /api/clusters/{clusterId}/command-capabilities?namespace=payments
```

응답 핵심:

```json
{
  "clusterId": "uuid",
  "namespace": "payments",
  "connected": true,
  "kubectlVersion": "v1.35.1",
  "clusterVersion": "v1.34.3",
  "versionCompatible": true,
  "capabilities": ["READ", "DIAGNOSE", "CHANGE", "INTERACTIVE"],
  "metricsApiAvailable": false,
  "streamingAvailable": true,
  "credentialHealth": "HEALTHY"
}
```

### Validate

```http
POST /api/clusters/{clusterId}/commands/validate
```

```json
{
  "namespace": "payments",
  "command": "kubectl get pods -n payments -o wide"
}
```

응답에는 tokenized argv, 감지 가능한 target, namespace, safety, platform scope, confirmation requirement와 warning을 포함한다. 자동완성 metadata가 명령을 완전히 해석하지 못해도 실제 kubectl 실행을 허용한다. Kubernetes 문법과 세부 RBAC은 실제 실행 결과로 판정한다.

### Execute

```http
POST /api/clusters/{clusterId}/command-executions
```

```json
{
  "namespace": "payments",
  "command": "kubectl get pods -n payments -o wide",
  "confirmText": null,
  "favoriteId": null,
  "manifestRef": null
}
```

응답은 `202 Accepted`와 execution ID, status, stream URL을 반환한다. 빠른 validation 실패는 execution을 `BLOCKED`로 저장하고 4xx ProblemDetail과 execution ID를 함께 제공한다.

### History And Detail

```http
GET /api/clusters/{clusterId}/command-executions?namespace=payments&page=0&size=30
GET /api/clusters/{clusterId}/command-executions/{executionId}
GET /api/clusters/{clusterId}/command-executions/{executionId}/stream
POST /api/clusters/{clusterId}/command-executions/{executionId}/cancel
```

### Interactive Sessions

```http
POST   /api/clusters/{clusterId}/command-sessions
WS     /ws/command-sessions/{oneTimeTicket}
```

REST 요청은 정규화된 `kubectl exec -it` 또는 `kubectl attach -it` 명령과 사용자 RBAC, tenant/cluster scope를 검증하고 30초 유효한 일회성 ticket을 반환한다. WebSocket transport는 reverse proxy의 Origin 변환과 무관하게 이 ticket을 필수로 검사하며 최초 연결에서 즉시 소모한다. kubeconfig나 ServiceAccount credential은 URL에 포함하지 않는다.

WebSocket client message는 `input`, `resize`, `close` 세 종류이며 server message는 `output`, `status`, `error`를 사용한다. terminal session은 Fabric8의 stdin/stdout/stderr와 TTY resize 채널에 직접 연결하고, idle timeout과 연결 종료 시 Kubernetes watch를 닫는다.

### Manifest And File Transfer

```http
POST /api/clusters/{clusterId}/command-artifacts/manifests
POST /api/clusters/{clusterId}/command-transfers/uploads
POST /api/clusters/{clusterId}/command-transfers/downloads
GET  /api/clusters/{clusterId}/command-transfers/{transferId}/stream
```

manifest와 업로드 파일은 실행/session에 귀속된 짧은 수명의 artifact reference로 반환한다. 실제 Runner 경로는 API 계약에 노출하지 않는다.

### Port Forward

```http
POST   /api/clusters/{clusterId}/port-forwards
GET    /api/clusters/{clusterId}/port-forwards/{sessionId}
DELETE /api/clusters/{clusterId}/port-forwards/{sessionId}
```

응답의 접근 URL은 플랫폼 gateway 경로이며 session cookie와 scope를 매 요청 확인한다. Runner 주소와 remote credential은 반환하지 않는다.

### Favorites

```http
GET    /api/command-favorites?clusterId={clusterId}&namespace={namespace}&visibility=ALL
POST   /api/command-favorites
PATCH  /api/command-favorites/{favoriteId}
DELETE /api/command-favorites/{favoriteId}
POST   /api/command-favorites/{favoriteId}/resolve
```

`resolve`는 typed parameter를 받아 최종 command와 validation 결과만 반환한다. 실행은 항상 execution endpoint를 사용해 우회 경로를 만들지 않는다.

## 3. Execution 상태

```text
VALIDATING → READY → QUEUED → RUNNING → SUCCEEDED
     │         │        │        ├────→ FAILED
     │         │        │        ├────→ TIMED_OUT
     │         │        │        └────→ CANCELED
     └─────────┴────────┴──────────────→ BLOCKED
```

## 4. 데이터 모델

### command_executions

| 필드 | 설명 |
| --- | --- |
| id | UUID |
| tenant_id | tenant scope |
| cluster_id | 고정 실행 대상 |
| namespace | nullable cluster scope |
| source_type | CONSOLE, ANALYSIS, APPLICATION |
| source_id | 분석/애플리케이션 연계 ID |
| raw_command | 마스킹 후 입력 원문 |
| normalized_command | parser 결과 |
| invocation_json | argv와 감지된 구조화 metadata, credential 제외 |
| safety | READ_ONLY, DIAGNOSE, CHANGE, DESTRUCTIVE, PRIVILEGED_INTERACTIVE, BLOCKED |
| status | 실행 상태 |
| validation_json | scope/RBAC/dry-run 결과, Secret 제외 |
| stdout_excerpt | 마스킹·절단 결과 |
| stderr_excerpt | 마스킹·절단 결과 |
| output_bytes / output_rows | 출력량 |
| truncated | 절단 여부 |
| exit_code | 실제 kubectl process exit code |
| duration_ms | 실행 시간 |
| created_by | 플랫폼 사용자 ID |
| created_at / started_at / completed_at | 시각 |
| request_id | 추적 ID |

### command_favorites

| 필드 | 설명 |
| --- | --- |
| id | UUID |
| tenant_id | tenant scope |
| owner_user_id | 개인 소유자 |
| visibility | PRIVATE, TENANT |
| name / description | 표시 정보 |
| category | WORKLOAD, NETWORK, STORAGE, LOGS, SECURITY |
| cluster_id | nullable, 특정 Cluster 고정 시 사용 |
| namespace_mode | FIXED, SELECT_ON_RUN, CLUSTER_SCOPED |
| namespace | FIXED일 때 값 |
| command_template | 검증된 template |
| parameter_schema_json | typed parameter 정의 |
| safety | 저장 시 분류 결과 |
| sort_order | 사용자 정렬 |
| last_used_at / use_count | 최근 사용 |
| created_by / updated_by / timestamps | 감사 정보 |

## 5. Parameter Schema

허용 타입:

- `RESOURCE_NAME`
- `NAMESPACE`
- `CONTAINER_NAME`
- `INTEGER` with min/max
- `DURATION`
- `ENUM`
- `LABEL_SELECTOR`
- `FIELD_SELECTOR`

즐겨찾기 변수는 template token에만 주입하고 전체 command를 shell string으로 평가하지 않는다. 자유 문자열은 quote-aware argv token 하나로 제한하며 shell metacharacter는 실행 의미를 갖지 않는다.

## 6. 오류 코드

- `COMMAND_SYNTAX_INVALID`
- `COMMAND_SCOPE_DENIED`
- `COMMAND_KUBERNETES_RBAC_DENIED`
- `COMMAND_CONFIRMATION_REQUIRED`
- `COMMAND_CONFIRMATION_INVALID`
- `COMMAND_EXECUTION_TIMEOUT`
- `COMMAND_STREAM_LIMIT_EXCEEDED`
- `COMMAND_OUTPUT_LIMIT_EXCEEDED`
- `CLUSTER_CREDENTIAL_UNAVAILABLE`
- `KUBERNETES_API_UNAVAILABLE`
- `COMMAND_FAVORITE_INVALID`
- `COMMAND_FAVORITE_ACCESS_DENIED`
- `COMMAND_RUNNER_UNAVAILABLE`
- `COMMAND_KUBECTL_VERSION_INCOMPATIBLE`
- `COMMAND_INTERACTIVE_LIMIT_EXCEEDED`
- `COMMAND_FILE_TRANSFER_REJECTED`
- `COMMAND_PORT_FORWARD_DENIED`

## 7. 보관

- execution metadata와 audit은 운영 정책 기간 동안 저장한다.
- stdout/stderr excerpt는 기본 30일 후 제거 가능하게 한다.
- streaming 전체 원문은 DB에 저장하지 않는다.
- 즐겨찾기는 soft delete 없이 사용자 삭제 요청 시 제거하되 audit event는 유지한다.
- tenant 또는 Cluster 삭제 시 execution metadata 보관 정책과 favorite 정리를 명시적으로 수행한다.
