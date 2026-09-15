# Coding Standards

## 공통 구현 원칙

- 새로 작성하거나 의미를 변경하는 핵심 class, public API, 복잡한 분기와 안전·권한·성능상 중요한 로직에는 의도와 제약을 설명하는 한글 주석을 작성한다. 코드 자체를 그대로 읽는 무의미한 줄별 주석은 만들지 않는다.
- 한 파일에 여러 책임을 모으지 않는다. Controller, application service, domain policy, persistence adapter, 외부 adapter와 DTO를 역할별로 분리하고 Frontend view는 화면 조립에 집중한다.
- Java/Vue/TypeScript 파일이 약 400줄을 넘거나 독립적으로 시험할 수 있는 책임이 둘 이상이면 분리를 우선 검토한다. 생성 코드, schema와 데이터 fixture는 예외지만 수동 구현 파일의 비대화를 정당화하지 않는다.
- Collection API는 기본 pagination과 상한을 적용하고, DB 조회는 Tenant/Scope predicate와 필요한 index를 함께 설계한다. N+1 query, 무제한 전체 조회와 요청마다 반복되는 원격 Kubernetes/Registry/LLM 호출을 금지한다.
- 외부 I/O는 timeout, 동시성 상한, 취소와 bounded retry를 갖추고 장시간 작업은 Async Job으로 분리한다. Kubernetes 또는 외부 API를 기다리는 동안 DB transaction을 유지하지 않는다.
- 성능에 영향을 주는 기능은 예상 데이터 크기, query 수, payload 상한, cache/deduplication과 실패 시 fallback을 테스트 또는 검증 문서에 남긴다.

## Backend

- Domain layer는 framework에 의존하지 않는다.
- Application layer는 port interface를 통해 외부 기능을 사용한다.
- Adapter layer만 외부 SDK와 framework 세부 구현에 의존한다.
- Controller는 JPA Entity를 직접 반환하지 않는다.
- Request/Response DTO를 분리한다.
- 장시간 작업은 AsyncJob으로 처리한다.
- LLM을 호출하는 기능은 대형 context를 한 번에 보내지 않는다. 관심사별 context slice로 분할하고, section별 timeout/fallback을 설계한다.
- 동일 scope의 최신 성공 분석과 section context fingerprint가 같으면 기존 section을 재사용한다. 재사용 여부, fingerprint, latency는 diagnostics에 남기며 최신 Kubernetes 근거가 달라지면 반드시 다시 분석한다.
- Watch 기반 수집은 연결 불안정 시 bounded polling으로 전환할 수 있어야 한다. Polling은 전체 manifest를 저장하지 않고 비정상 상태와 Warning Event만 수집하며 같은 현재 상태를 반복 저장하지 않는다.
- 실제 Kubernetes fault fixture는 application port 뒤에 두며 기본 비활성화, 전용 namespace, 소유권 label, RBAC preflight, exact confirmation, bounded TTL과 idempotent cleanup을 모두 적용한다.
- 외부 Kubernetes API 대기 구간에서 DB transaction을 열어두지 않는다. 동일 cluster의 검증 작업은 정리 완료 전 중복 실행하지 않는다.
- 운영 추세는 근거 종류를 API와 UI에 표시한다. Prometheus 전 Incident/Event 기반 통계를 utilization 또는 saturation으로 표현하지 않는다.
- AI Analysis, AI Chat, prompt, schema, Ollama/Spring AI adapter 변경 시 이 문서의 Backend 기준과 `docs/architecture/ai-analysis-architecture.md`, `docs/security/masking-policy.md`를 먼저 확인한다.

## Frontend

- API 타입은 Orval generated client를 우선 사용한다.
- 화면 상태는 loading, empty, error, success를 모두 고려한다.
- 긴 로그/이벤트는 접기 또는 virtual scroll을 사용한다.
- 위험 작업은 확인 dialog를 둔다.
- 위험 작업은 설명, 사전점검, exact confirmation, 실행 결과, cleanup을 하나의 사용자 흐름으로 제공하고 기본 화면과 분리한다.
- 운영 화면은 한 페이지에 모든 기능을 펼치지 않고 작업 목적별 tab과 deep-link 가능한 filter를 제공한다.
- Vue SFC 내부에는 `<style>` 또는 `<style scoped>`를 작성하지 않는다.
- 정적 inline style은 금지하고, 공통 CSS는 `frontend/src/styles`에 작성한다.
- 재사용 가능한 순수 TypeScript helper는 `frontend/src/utils`, Vue Composition API 로직은 `frontend/src/composables`에 둔다.
- CSS import는 `frontend/src/main.ts`로 집중한다.
- 화면별 의미가 다른 selector를 하나의 거대 stylesheet에 섞지 않는다. 공통 primitive, domain component와 page layout의 책임에 맞는 CSS 파일과 class namespace를 사용하고 유사 class의 복제·오용을 금지한다.
