# Observability

필수:

- Spring Boot Actuator
- health/readiness/liveness
- Micrometer metrics
- structured logging
- requestId/correlationId
- Job duration metric
- Kubernetes API call duration/error metric
- Helm execution duration/error metric
- AI call duration/error metric

## AI Chat 추적

AI Chat은 다음 구조화 event를 동일한 `requestId`, `conversationId`로 기록한다.

- `ai_chat_request_started`: mode, cluster/namespace scope, streaming 여부
- `ai_chat_context_ready`: 정제된 context 문자 수, context 수집 시간, 근거 개수
- `ai_chat_first_token`: 사용자 요청 시작부터 첫 delta까지의 시간
- `ai_chat_request_completed`: model, 첫 토큰/LLM/전체 시간, context/응답 문자 수
- `ai_chat_request_failed`: 실패 단계(`context`/`completion`), 전체 시간, context 문자 수, 예외 유형

질문, 답변, 로그, manifest 원문은 운영 로그에 기록하지 않는다. UI의 응답 처리 정보는 메시지에 저장된 `firstTokenLatencyMs`, `latencyMs`, `totalLatencyMs`, `contextChars`를 사용하므로 재접속 후에도 확인할 수 있다.

SSE는 요청 접수 직후 `status: accepted`를 보내고 기본 15초 heartbeat를 이어서 전송해 프록시 idle disconnect를 방지한다. heartbeat는 전체 요청 제한시간을 늘리지 않으므로 `Ollama read timeout < Servlet async timeout < reverse proxy timeout` 순서를 유지한다.
Frontend는 첫 delta가 도착하기 전 경과 시간과 status/heartbeat 수신 여부를 표시한다. status 또는 heartbeat가 수신되면 연결은 유지 중이고 모델의 첫 응답을 기다리는 상태이며, 연결 신호 없이 stream 오류가 발생하면 중단 상태와 저장된 부분 응답을 표시한다.

## RC 관측 증적

- 분석 결과의 `analysisDiagnostics`에 section별 context chars, latency, status, fallback/cache 여부를 기록한다.
- Job Dock은 started/completed timestamp로 분석 소요 시간을 표시한다.
- `validate-operational-resilience.sh`는 인증 read 경로의 개별 응답시간과 p95를 `artifacts/resilience`에 남긴다.
- 복구 시험용 database dump는 `artifacts`에 보존하지 않고 OS 임시 파일로 사용한 뒤 성공·실패와 관계없이 제거한다.
- Backend/Keycloak restart 뒤 `/api/auth/me`가 회복되지 않으면 30초 bounded wait 후 실패한다.
- 로그와 JSON artifact에는 password, token, cookie를 저장하지 않는다.

현재 로컬 최신 기준 p95는 20ms다. 이는 cluster sync, 대규모 namespace log 수집, Ollama 모델 latency SLO를 대표하지 않으므로 고객 운영에서는 cluster 규모별 별도 부하/soak 시험이 필요하다.
