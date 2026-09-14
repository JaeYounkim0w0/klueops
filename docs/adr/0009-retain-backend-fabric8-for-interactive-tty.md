# ADR-0009: Interactive TTY는 Backend Fabric8 경계를 유지한다

## Status

Accepted

## Context

일반 kubectl 명령은 shell 없는 격리 Command Runner에서 실행하지만 `exec -it`와 `attach -it`는 양방향 stdin/stdout, resize, WebSocket 수명주기가 필요하다. Runner에 별도 WebSocket protocol과 인증 경계를 중복 구현하면 공격면과 운영 구성요소가 늘어난다.

## Decision

대화형 TTY는 Backend의 Fabric8 Kubernetes Client 경계를 유지한다. 대신 일반 명령과 다른 실행 경계임을 capability API와 UI에 명시하고 다음 통제를 제품 계약으로 고정한다.

- REST에서 `operation:execute`와 tenant/cluster/namespace scope를 확인한 뒤 30초 단일 사용 ticket을 발급한다.
- WebSocket 연결은 인증 principal과 ticket actor를 일치시키고 input/resize를 해당 연결에만 고정한다.
- 사용자 3개, cluster 20개 기본 동시 TTY 상한과 사용자/cluster 시작 rate limit을 PostgreSQL lease로 강제한다.
- 15분 기본 idle timeout, 1 MiB 출력 저장 상한, 5,000줄 브라우저 scrollback을 적용한다.
- Pod의 resource limit이 Backend 메모리 경계를 제공하고, Backend ingress는 Frontend Pod로 제한한다. 등록 cluster API로 향하는 egress는 대상 CIDR이 고객별로 달라 Helm의 단일 고정 CIDR로 제한하지 않는다. 고객 방화벽 또는 egress gateway에서 등록 API server allowlist를 적용한다.
- 시작/완료/취소/복구 상태와 actor, scope, request ID를 Audit 및 CommandExecution에 기록하며 Secret과 원문 credential은 저장하지 않는다.

## Verification

- `CommandTerminalSessionServiceTest`: ticket, quota/lease, 출력 상한과 종료 상태
- `TerminalCommandParserTest`: 허용된 exec/attach 형식과 context override 차단
- WebSocket 수동 시험: resize, stdin/stdout, 정상 종료, disconnect, idle timeout
- `./scripts/validate-security.sh`, `./scripts/validate-packaging.sh`: NetworkPolicy, securityContext, proxy upgrade 계약
- `AIOPS_RESILIENCE_ENABLED=true ./scripts/validate-operational-resilience.sh`: Backend 재시작과 orphan 회복

## Consequences

TTY 장애는 Backend Pod 자원에 영향을 줄 수 있으므로 일반 명령보다 낮은 별도 quota를 유지한다. Backend가 여러 replica일 때 ticket/session은 발급 instance에 고정되어야 하며 ingress의 session affinity 또는 공유 session registry가 필요하다. Runner 통합은 현재 결정의 전제보다 명확한 보안·확장 이점이 증명될 때 새 ADR로 재검토한다.
