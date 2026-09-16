# Async Job Model

장시간 작업은 REST 요청 안에서 blocking 처리하지 않는다.

대상:

- Helm install/upgrade/rollback
- AI analysis
- Cluster sync
- Application sync

상태:

- PENDING
- RUNNING
- SUCCEEDED
- FAILED
- CANCELED
- TIMEOUT

## Transaction과 동시성 경계

- 요청 transaction은 대상 상태, Async Job과 operation 이력을 함께 저장한다.
- worker는 transaction commit 이후에만 bounded executor로 제출한다. 단순 `flush`는 다른 transaction의 가시성을 보장하지 않으므로 실행 조건으로 사용하지 않는다.
- rollback된 요청은 worker를 제출하지 않는다.
- executor 포화 또는 worker 시작부 예외는 별도 transaction에서 Job과 연결된 대상·operation을 `FAILED`로 수렴시킨다.
- 서로 다른 대상은 병렬 처리하고 동일 대상 lifecycle mutation은 DB row lock과 active 상태 검증으로 하나만 허용한다. 이 규칙은 다중 사용자와 다중 Backend replica에 동일하게 적용한다.
- Backend 종료로 메모리 큐의 작업이 사라진 경우 stale recovery가 제한 시간 뒤 `TIMEOUT/FAILED`로 수렴시킨다. 완전한 재시작 후 자동 재개가 필요해지는 규모에서는 DB queue 또는 전용 Runner로 확장한다.
