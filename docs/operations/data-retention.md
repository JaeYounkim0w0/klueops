# Data Retention

기본 보존 정책:

- SyncJob: 30일
- Helm execution log: 30일
- KubernetesResourceSnapshot: 30일
- KubernetesEventSnapshot: 30일
- AnalysisSession: 180일
- AuditLog: 1년
- CommandExecution: 90일(완료 상태만 정리하며 실행 중인 세션은 보존)
- KubernetesWatchSignal: Event snapshot 보존 기간과 동일
- AnalysisRegressionRun: AnalysisSession 보존 기간과 동일, 최신 실행 1건은 항상 보존

Kubernetes runtime 데이터는 전체 영구 미러링하지 않는다.

보존 기간이 지난 snapshot/event/sync/분석/알림/변경/해결 Incident/Audit/완료 명령 이력은 운영 설정의 미리보기 후 cleanup으로 삭제한다. Audit은 30~2555일, 명령 이력은 7~730일 범위에서 설정할 수 있다. 정리는 `OPERATION_DATA_CLEANED` Audit을 새로 기록하며 Kubernetes 실제 리소스는 변경하지 않는다.

Secret value, ConfigMap 민감 값, Pod log 원문 전체, managedFields 전체는 저장하지 않는다.
