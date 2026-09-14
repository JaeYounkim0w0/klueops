# Isolated Runner and Closed-loop Operations Design

기준일: 2026-09-09

## 목표와 범위

Kubernetes Console의 자유로운 kubectl 사용성을 유지하면서 명령 실행 권한과 장애를 Portal Backend에서 분리한다. 변경 명령은 종료 코드만으로 성공 판정하지 않고 실행 전후 Kubernetes 상태를 비교해 운영 결과를 검증한다. Analysis, Runbook, Incident와 Command Execution은 하나의 증빙 흐름으로 조회한다.

이번 범위는 다음 다섯 항목이다.

1. 별도 Command Runner Deployment와 인증된 내부 실행 경계
2. 변경 전후 Snapshot, 자동 검증과 rollback 후보
3. 분석·명령·Incident·Runbook 통합 증빙
4. 실패 중심 Console UX와 파라미터 기반 빠른 명령
5. Analysis/Fabric8/CSS의 책임 분리

대형 클러스터 합성 부하 시험은 구현 완료 후 비용과 필요성을 별도 판단한다.

## Runner 신뢰 경계

Portal Backend는 명령 정책, 사용자 RBAC, Tenant/Cluster scope, admission lease와 감사 이력을 소유한다. Runner는 검증을 통과한 argv와 일회성 요청만 실행하며 사용자 계정, Portal DB, OIDC token을 알지 못한다.

- 기본 운영 모드는 내부 HTTP Runner다. Runner가 설정됐는데 연결할 수 없으면 로컬 실행으로 우회하지 않는다.
- Backend와 Runner는 Kubernetes Secret의 공유 token을 `X-AIOPS-Runner-Token`으로 검증한다.
- 요청 ID는 Runner의 bounded replay cache에서 중복 실행을 거부한다.
- credential은 요청 메모리와 실행별 `0600` 임시 kubeconfig에만 존재하고 응답·로그·DB에 저장하지 않는다.
- Runner Pod는 non-root, read-only root filesystem, seccomp RuntimeDefault, capability drop, 제한된 `/tmp`, resource limit과 NetworkPolicy를 사용한다.
- 일반 명령은 Runner로 이동한다. 대화형 TTY는 WebSocket transport 전환이 완료될 때까지 기존 Fabric8 채널을 사용하며 UI와 readiness에 이 경계를 명시한다.

## 변경 후 검증

`READ_ONLY`, `DIAGNOSE`는 별도 Snapshot 없이 기존 결과를 유지한다. `CHANGE`, `DESTRUCTIVE` 명령은 다음 상태 기계를 따른다.

1. tokenizer 결과에서 대상 kind/name/namespace와 안전한 조회 명령을 만든다.
2. 실행 전 조회 결과를 bounded Snapshot으로 저장한다.
3. 사용자 명령을 실행한다.
4. exit code가 0이면 짧은 안정화 대기 후 같은 조회를 다시 실행한다.
5. canonical SHA-256과 핵심 상태를 비교해 `VERIFIED_CHANGED`, `VERIFIED_STABLE`, `VERIFICATION_FAILED`로 판정한다.

명령 성공과 운영 결과는 분리한다. 변경 명령의 exit code가 0이어도 상태 확인에 실패하면 `VERIFICATION_FAILED`이며 UI는 완료로 과장하지 않는다. Snapshot은 Secret 값과 managed fields를 제거하고 크기를 제한한다.

rollback은 자동 실행하지 않는다. Deployment/StatefulSet/DaemonSet rollout 계열은 `kubectl rollout undo`, scale은 이전 replica가 확인된 경우 이전 값으로 되돌리는 후보를 만든다. delete, secret 변경 또는 대상이 불명확한 명령은 rollback 후보를 생성하지 않는다.

## 증빙과 UX

Command Execution 상세는 다음 순서로 표시한다.

- 실행 결과: status, exit code, duration
- 운영 검증: 전후 상태, 판정, 변경 요약
- 출처: Analysis ID와 원본 범위
- 후속 동작: Event, Log, YAML, 재실행, 재분석
- rollback 후보: 기본 접힘, 위험 경고, Console에서 검토

Incident Report는 연결된 analysis와 command 실행의 hash, 검증 판정, actor, timestamp를 포함한다. 원문 stdout과 credential은 내보내지 않는다. 초보자는 판정과 다음 행동을 먼저 보고 숙련자는 전후 Snapshot과 stderr를 펼쳐 본다.

## 장애 처리

- Runner timeout/5xx: execution `FAILED`, lease 해제, 재시도 가능한 오류 표시
- 중복 request ID: `409`, 같은 명령을 다시 실행하지 않음
- Backend 재기동: DB lease가 없는 orphan execution 회수
- 검증 조회 실패: 사용자 명령 상태는 유지하고 verification만 실패 처리
- 출력 초과: 기존 truncation 표시와 hash를 남김

## 완료 기준

- Backend 단위·API·PostgreSQL 통합 테스트
- Runner 인증, replay, timeout, credential 비노출 테스트
- Frontend Vitest, typecheck, production build
- OpenAPI/generated client drift 없음
- Helm lint/template, NetworkPolicy/Secret 계약 통과
- 실제 OIDC 로그인 후 Analysis에서 Console 이동, 변경 전후 검증 표시와 read-only 명령 실행 확인
- 변경 명령 실환경 시험은 전용 테스트 namespace와 명시적 안전 fixture에서만 수행
