# Sync Strategy

v1에서는 5분 자동 동기화와 UI 수동 동기화를 제공한다.

DB는 배포/분석/작업 이력의 source of truth로 사용한다.

Kubernetes 클러스터는 현재 runtime 상태의 source of truth로 사용한다.

Pod/Event Watch는 5분 sync 사이의 변화 신호를 보조 수집한다. Watch는 전체 리소스 mirror와 baseline 계산을 대체하지 않는다.

## Storage Policy

Kubernetes 리소스는 전체 영구 미러링하지 않는다.

DB에는 UI 목록, 장애 분석, 검색, 이력 추적에 필요한 요약 정보와 snapshot을 저장한다.

최신 상세 상태가 필요한 화면이나 즉시 실행 작업은 Kubernetes API를 실시간 조회한다.

### DB 저장 대상

- Cluster, ClusterCredential, ClusterSyncSetting
- SyncJob 실행 이력과 성공/실패 결과
- Namespace, Deployment, ReplicaSet, Pod, Service, Ingress, Endpoint, PVC/PV 상태 요약 snapshot
- Kubernetes Event 중 분석에 필요한 최근 event
- AI 분석 입력으로 사용한 KubernetesResourceSnapshot
- AI 분석 결과와 사용자 조치 이력
- Pod/Event Watch의 안전한 signal summary

### 실시간 조회 대상

- 특정 리소스의 최신 상세 manifest
- Pod log 조회
- exec, port-forward 등 interactive 작업
- scale, restart, delete, rollout 등 즉시 실행 작업 직전의 최신 상태 확인
- connection test

### 저장 제외 또는 제한 대상

- Secret value
- ConfigMap value 중 민감 정보로 분류될 수 있는 값
- Pod log 원문 전체
- Kubernetes managedFields 전체
- 모든 리소스 revision의 무제한 이력
- 대용량 rawJson

## Collection Scope

v1 수동/자동 sync는 다음 리소스를 우선 수집한다.

- Namespace
- Deployment
- ReplicaSet
- Pod
- Service
- Ingress
- Event
- Service endpoint
- PVC/PV

## Retention

- KubernetesResourceSnapshot은 기본 30일 보존한다.
- SyncJob은 기본 30일 보존한다.
- Event snapshot은 기본 30일 보존한다.
- rawJson은 크기 제한을 적용하고 초과 시 `truncated=true`를 저장한다.

## Consistency Model

DB의 Kubernetes snapshot은 eventually consistent 데이터다.

UI 목록과 AI 분석은 기본적으로 최근 sync snapshot을 사용한다.

상세 화면은 snapshot을 먼저 표시하고, 필요한 경우 Kubernetes API를 실시간 조회해 최신 상태를 보강한다.

수동 sync가 완료되면 UI는 마지막 sync 시각과 sync 상태를 기준으로 데이터를 갱신한다.

## Watch Consistency

- cluster별 Fabric8 Watch 연결 상태를 메모리에서 관리한다.
- 연결이 닫히면 registration/client를 정리하고 30초 reconcile 주기에서 재연결한다.
- 동일 cluster/namespace/resource/action/reason/status/summary 신호는 10초 동안 한 번만 저장한다.
- Watch signal은 즉시 변화 탐지용이며 resource baseline/change 비교는 최신 성공 sync에서만 수행한다.
- Watch 실패는 sync와 AI Analysis 요청 실패로 전파하지 않는다.
- Watch에는 kubeconfig, token, Secret 값, 전체 manifest를 저장하지 않는다.
