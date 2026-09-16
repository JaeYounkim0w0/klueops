# Configuration

설정은 profile과 환경 변수로 분리한다.

민감 정보는 source control에 저장하지 않는다.

## AI

기본 AI runtime은 Ollama를 사용한다.

```yaml
aiops:
  ai:
    provider: ollama
    model: qwen2.5-coder:7b
    base-url: http://localhost:11434
    connect-timeout-ms: 10000
    timeout-ms: 300000
    temperature: 0.1
    analysis:
      max-context-chars: 60000
    chat-memory:
      max-messages: 20
    chat-stream:
      heartbeat-ms: 15000
```

로컬 Ollama에 다른 모델만 설치되어 있다면 `aiops.ai.model` 또는 환경별 설정으로 모델명을 변경한다.
namespace 전체 분석은 Kubernetes 리소스, 이벤트, 제한된 Pod log tail을 함께 전달하므로 기본 read timeout은 300초로 둔다.
분석 context는 `aiops.ai.analysis.max-context-chars`로 제한해 모델 지연과 token 비용을 제어한다.
운영 분석은 재현성과 근거성이 중요하므로 기본 temperature는 0.1로 둔다.
AI Chat은 Spring AI ChatMemory를 사용하며 `aiops.ai.chat-memory.max-messages` 값으로 모델 context에 포함할 최근 대화 window를 제한한다.
AI Chat SSE의 Servlet 비동기 제한은 `AIOPS_AI_STREAM_TIMEOUT_MS`로 설정하며 기본값은 330초다. 이 값은 Ollama read timeout인 `AIOPS_AI_TIMEOUT_MS` 기본 300초보다 반드시 길어야 upstream 오류를 먼저 받아 실패 질문을 정상 저장할 수 있다. Nginx `proxy_read_timeout` 기본 360초도 이 값보다 길게 유지한다.
`AIOPS_AI_CHAT_HEARTBEAT_MS`는 첫 토큰 또는 다음 토큰을 기다리는 동안 SSE 연결에 보내는 keep-alive 주기이며 기본값은 15초다. 프록시 idle timeout보다 짧게 설정하되 과도한 쓰기를 피하기 위해 운영 권장 범위는 10~30초다.

## Cluster Resource Logs

클러스터 상세 로그는 저장된 snapshot이 아니라 Kubernetes API에서 직접 조회하며 로그 본문을 DB에 저장하지 않는다.

- `AIOPS_CLUSTER_LOG_HEARTBEAT_MS`: SSE heartbeat 주기, 기본 `15000`
- `AIOPS_CLUSTER_LOG_MAX_STREAM_DURATION_MS`: 단일 스트림 최대 연결 시간, 기본 `1800000`(30분)
- `AIOPS_CLUSTER_LOG_MAX_TARGET_PODS`: workload 하나에서 선택 목록에 노출할 최대 Pod 수, 기본 `50`

프록시의 read timeout은 heartbeat 주기보다 길어야 한다. 브라우저가 스트림을 중지하거나 상세 화면을 닫으면 Backend가 연결 해제를 감지해 Fabric8 `LogWatch`를 닫는다. 클러스터 ServiceAccount에는 대상 namespace의 workload/Pod 조회와 `pods/log`의 `get` 권한이 필요하다.

## Kubernetes Command Runner

- `AIOPS_COMMAND_RUNNER_MODE`: `remote`는 격리 Deployment, `local`은 개발용 Backend 프로세스 실행이다.
- `AIOPS_COMMAND_RUNNER_URL`: 기본 cluster-internal URL은 `http://aiops-command-runner:8090` 형식이다.
- `AIOPS_COMMAND_RUNNER_TOKEN`: Backend와 Runner가 공유하는 32자 이상 난수다. source, ConfigMap, 로그에 기록하지 않는다.
- `AIOPS_COMMAND_VERIFICATION_TIMEOUT_SECONDS`: 변경 전후 Kubernetes 상태 조회의 개별 상한이며 기본값은 15초다.

운영 프로필은 `remote`만 Runtime Readiness `READY`다. 원격 Runner 오류는 fail-closed 처리하며 local로 자동 전환하지 않는다. 일반 kubectl은 Runner 경계를 사용하고, 현재 대화형 `exec/attach` TTY는 Backend Fabric8 WebSocket 경계를 사용한다.

## Database

로컬과 운영의 기본 저장소는 모두 PostgreSQL이다. H2 runtime과 H2 test 경로는 지원하지 않는다. 기본 연결은 `jdbc:postgresql://127.0.0.1:5432/aiops`이며 환경 변수로 변경한다.

DB 통합 테스트는 Testcontainers PostgreSQL 17을 사용한다. Docker가 없는 환경에서는 순수 단위 테스트와 패키징만 실행할 수 있고, 전체 Backend gate를 통과한 것으로 간주하지 않는다.

Persistent profile 필수 조건:

- Flyway migration 적용
- JPA `ddl-auto=validate` 통과
- backend 재시작 후 cluster 등록 정보 유지
- backend 재시작 후 analysis/command/workflow history 유지
- command execution stdout/stderr masking 및 크기 제한 유지

### PostgreSQL runtime

- `SPRING_PROFILES_ACTIVE=local`: local PostgreSQL과 개발용 보안 설정 사용
- `AIOPS_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/aiops`
- `AIOPS_DATASOURCE_USERNAME=raguser`
- `AIOPS_DATASOURCE_PASSWORD`: 필수 secret
- `AIOPS_DB_POOL_MIN_IDLE`: 기본 `2`
- `AIOPS_DB_POOL_MAX_SIZE`: 기본 `10`
- `AIOPS_DB_CONNECTION_TIMEOUT_MS`: 기본 `10000`
- `AIOPS_DB_VALIDATION_TIMEOUT_MS`: 기본 `5000`

비밀번호는 `.env.example`, application YAML, 운영 문서에 기본값으로 기록하지 않는다.

OIDC 실행은 동일 PostgreSQL datasource를 사용하며 Spring Session도 JDBC로 저장한다. 운영에서는 application runtime role, Keycloak runtime role과 database를 분리한다.

## Live Validation Lab

실제 Kubernetes fixture 생성은 기본적으로 비활성화한다.

- `AIOPS_VALIDATION_LAB_LIVE_ENABLED=false`
- `AIOPS_VALIDATION_LAB_ALLOW_PRODUCTION=false`
- `AIOPS_VALIDATION_LAB_NAMESPACE_PREFIX=aiops-validation-`
- `AIOPS_VALIDATION_LAB_MAXIMUM_TTL_SECONDS=900`
- `AIOPS_VALIDATION_LAB_OBSERVATION_TIMEOUT_MS=15000`
- `AIOPS_VALIDATION_LAB_CLEANUP_INTERVAL_MS=60000`
- `AIOPS_VALIDATION_LAB_REQUIRED_CONFIRMATION=RUN LIVE VALIDATION`

운영 환경에서는 `ALLOW_PRODUCTION`을 활성화하지 않는 것을 기본 정책으로 한다. 실제 검증이 필요할 때에도 제한된 테스트 cluster credential과 별도 변경창을 사용한다.
# Production Readiness

운영 환경은 `GET /api/operations/runtime-readiness`로 현재 안전 설정을 확인한다. 기본 local profile은 단일 운영자 파일럿으로 판정된다.

- `portal.security.credentialRevealEnabled`: Helm의 자격 증명 원문 조회 정책이다. 기본값과 운영값은 `false`, 로컬 검증값은 `true`이며 `AIOPS_CREDENTIAL_REVEAL_ENABLED`로 Backend에 전달된다.
- 원문 보기는 `cluster:manage` capability가 있는 사용자만 사용할 수 있다. 확인 대화상자를 거쳐 표시하고 감사 이벤트를 남기며, 화면은 60초 후 자동으로 다시 마스킹한다.
- `global.productionMode=true`에서 `portal.security.credentialRevealEnabled=true`를 지정하면 Helm 렌더링을 실패시켜 운영 배포의 원문 조회를 차단한다.
- `AIOPS_LOCAL_MASTER_KEY`: 기본 개발 키를 사용하지 말고 배포 secret으로 제공한다.
- `AIOPS_JOB_MAXIMUM_RUNTIME_SECONDS=900`: 활성 Job 최대 실행시간.
- `AIOPS_JOB_RECOVERY_INTERVAL_MS=60000`: stale Job 복구 검사 주기.
- `AIOPS_AI_ANALYSIS_SECTION_TIMEOUT_MS=180000`: AI 분석 section별 대기 상한.

### Kubernetes 진단 수집

- `AIOPS_KUBERNETES_CONNECT_TIMEOUT_MS=5000`: Kubernetes API 연결 수립 상한이다.
- `AIOPS_KUBERNETES_REQUEST_TIMEOUT_MS=10000`: 개별 Kubernetes API 요청 상한이다.
- `AIOPS_ANALYSIS_MAX_COLLECTION_FAILURES=6`: 한 namespace에서 허용할 리소스 source 실패 수다. 초과 시 남은 source를 `SKIPPED` 처리해 timeout 증폭을 막는다.
- `AIOPS_ANALYSIS_MAX_LOG_PODS=20`: 분석 context에 사용할 Pod 로그 대상 상한이다.
- `AIOPS_ANALYSIS_LOG_TAIL_LINES=80`, `AIOPS_ANALYSIS_MAX_LOG_CHARS=4000`: 컨테이너별 로그 수집량 상한이다.

수집 결과는 source별 `SUCCEEDED/FAILED/SKIPPED`, 건수, 지연, 오류 요약으로 기록된다. 일부 source가 실패하면 분석은 수집된 Kubernetes 근거로 계속하되 `PARTIAL`로 표시하고 confidence 상한을 `0.5`로 낮춘다. 모든 source가 실패한 경우에만 namespace 분석을 실패시킨다.
- `AIOPS_ANALYSIS_JOB_CORE_SIZE`, `AIOPS_ANALYSIS_JOB_MAX_SIZE`, `AIOPS_ANALYSIS_JOB_QUEUE_CAPACITY`: 분석 Job executor.
- `AIOPS_ANALYSIS_SECTION_CORE_SIZE`, `AIOPS_ANALYSIS_SECTION_MAX_SIZE`, `AIOPS_ANALYSIS_SECTION_QUEUE_CAPACITY`: AI section executor.
- `AIOPS_CLUSTER_SYNC_CORE_SIZE`, `AIOPS_CLUSTER_SYNC_MAX_SIZE`, `AIOPS_CLUSTER_SYNC_QUEUE_CAPACITY`: cluster sync executor.

`AIOPS_VALIDATION_LAB_ALLOW_PRODUCTION=true` 또는 비-local profile의 기본 master key/credential reveal은 readiness `BLOCKED` 사유다. 운영에서 문제 해결을 위해 자격 증명 원문을 직접 확인하는 대신 저장된 credential을 새 값으로 교체하고 연결 확인과 동기화를 다시 수행한다.

로컬 OIDC 실행에서도 운영 패키지와 동일한 credential master key를 사용한다. `scripts/run-local-oidc.sh`는 명시적인 `AIOPS_LOCAL_MASTER_KEY`가 없으면 `AIOPS_PORTAL_MASTER_KEY_SECRET_NAME`으로 지정한 Kubernetes Secret(기본 `aiops-portal-master-key`)의 `master-key`를 주입한다. PostgreSQL에 암호화 credential이 존재하는 동안 이 Secret을 재생성하거나 교체하지 않는다.
