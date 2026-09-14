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

