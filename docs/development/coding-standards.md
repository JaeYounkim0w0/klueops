# Coding Standards

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
