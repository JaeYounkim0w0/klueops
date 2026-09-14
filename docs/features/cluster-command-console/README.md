# Cluster Command Console

상태: 격리 Runner 기반 일반 명령 콘솔과 Pod TTY 구현 완료, 파일/네트워크 특수 기능은 잔여 검토

현재 코드와 배포 범위의 기준 문서: [Kubernetes Console Implementation Status](implementation-status.md)

이 폴더는 원격 Kubernetes 클러스터의 웹 kubectl 콘솔에 필요한 현재 아키텍처, API, 보안과 구현 상태를 관리한다.

## 2026-09-08 구현 현황

- Backend는 실행을 별도 Command Runner Deployment로 전달한다. Runner가 실제 `kubectl` binary를 shell 없이 argv로 실행하고 등록 credential은 실행별 임시 kubeconfig로만 materialize한다.
- credential/context override와 pipe, redirect, command chaining을 Runner 진입 전에 차단한다.
- 일반 명령과 `logs -f`, `get --watch`, `wait`, `port-forward` 출력은 SSE로 전달하며 timeout, cancel, 출력 상한을 적용한다.
- 조회/진단/변경/파괴/특수 명령을 분류하고 변경·파괴 명령은 대상 확인 후 실행한다.
- 실행 결과와 감사 metadata, 개인/공유 즐겨찾기를 PostgreSQL/Flyway V25에 저장한다. 이미 배포된 V17-V24 migration은 변경하지 않는다.
- Cluster 상세와 resource YAML 상세에서 context가 미리 채워진 Kubernetes Console로 이동한다.
- Console은 namespace, quick task, manifest editor, 실시간 출력, 검색/줄바꿈/복사/다운로드, history/favorite를 제공한다.
- 한국어/영어, desktop/mobile 재배치, 긴 명령·출력 overflow를 공통 stylesheet로 처리한다.
- Backend image에 checksum 검증된 kubectl을 포함한다.
- `kubectl exec -it`와 `attach -it`는 REST 일회성 ticket과 동일 사용자 WebSocket, Fabric8 TTY 채널로 연결한다.
- xterm.js가 stdin, Ctrl-C와 터미널 resize를 전달하며 연결 종료, 15분 idle timeout, 출력 상한과 실행 결과를 Audit/History에 남긴다.
- Pod 리소스 상세에서 해당 Pod와 namespace가 미리 채워진 터미널로 바로 이동한다.

현재 `cp` 파일 전송 UI와 브라우저 접근 가능한 port-forward gateway는 일반 명령/TTY와 다른 파일·네트워크 보안 경계이므로 feature flag 기반 운영 검증 전까지 전용 UI를 제공하지 않는다. 비대화형 `exec`, `cp` CLI 형태와 port-forward process 자체는 표준 명령 runner를 사용한다.

## 문서 구성

1. [구현 상태](implementation-status.md)
2. [아키텍처 설계](architecture-design.md)
3. [Closed-loop Runner 설계](closed-loop-runner-design.md)
4. [API 및 데이터 계약](api-and-data-contract.md)
5. [보안과 권한](security-and-permissions.md)

## 권장 결정안

| 항목 | 권장안 | 이유 |
| --- | --- | --- |
| 사용자 명칭 | Kubernetes Console / Kubernetes 콘솔 | 표준 kubectl을 실행하지만 범용 서버 shell은 아님을 명확히 함 |
| 실행 방식 | 격리된 Command Runner에서 실제 kubectl 바이너리를 argv 방식으로 실행 | 표준 kubectl 호환성과 shell injection 방지를 함께 확보 |
| 명령 범위 | 표준 kubectl 명령 전체 | 사용자 숙련 모드에 따라 기능을 제한하지 않음 |
| 실시간 통신 | 일반 출력은 SSE, exec/attach/port-forward는 WebSocket | 단방향 출력과 양방향 세션을 각각 적합한 프로토콜로 처리 |
| 즐겨찾기 | 개인 기본, 플랫폼 관리자가 tenant 공용 템플릿 배포 | 초기에 권한 모델을 과도하게 복잡하게 만들지 않음 |
| 출력 저장 | metadata와 마스킹·절단된 결과만 저장 | 감사 가능성을 유지하면서 로그/Secret 유출과 DB 팽창 방지 |
| 권한 | 플랫폼 scope와 원격 Kubernetes RBAC 모두 통과 | 등록 credential 권한만으로 사용자 권한을 대체하지 않음 |

## 확정된 제품 방향

- Standard/Dense 화면 모드는 사용 가능한 명령을 제한하지 않는다.
- 사용자는 `kubectl get nodes`부터 변경, 삭제, exec, cp, port-forward까지 표준 kubectl 명령을 직접 사용할 수 있다.
- 플랫폼은 kubectl option을 임의 allow-list로 축소하지 않는다.
- 자유 실행은 Kubernetes 범위에 한정한다. Runner host shell, 다른 binary, credential/context 우회는 제공하지 않는다.
- 사용자 계정의 tenant/cluster/namespace scope와 원격 Kubernetes RBAC은 모든 실행에 계속 적용한다.
- AI 자동 조치는 사용자 직접 콘솔보다 엄격한 기존 안전 정책을 유지한다.

## 범위 경계

- 브라우저에 kubeconfig, ServiceAccount token, OAuth token을 전달하지 않는다.
- Backend는 등록 credential을 짧은 수명의 실행 세션으로 전달하고, 격리된 Command Runner가 원격 Kubernetes API를 호출한다.
- `kubectl`의 표준 하위 명령은 모두 제공한다. 단, 범용 OS shell, host command, shell pipe/redirect/chaining은 제공하지 않는다.
- `kubectl exec` 내부에서 사용자가 실행하는 명령은 대상 Pod 내부에서 실행되며 해당 Kubernetes RBAC 권한과 세션 감사를 적용한다.
- jump server/SSH tunnel이 필요한 클러스터는 본 1차 범위에서 제외한다. 추후 agent 또는 connector 방식으로 확장한다.
- Metrics API가 없으면 `kubectl top`은 표준 오류를 반환하고 설치/확인 경로를 함께 안내한다.

## 검토가 필요한 선택

1. tenant 공용 즐겨찾기의 생성 권한을 PLATFORM_ADMIN으로만 제한할지 여부
2. 조회 결과 최대 보관 길이와 보관 일수
3. 파일 업로드/다운로드 최대 크기와 악성 파일 검사 정책
4. port-forward의 허용 포트, 최대 지속 시간, 외부 노출 정책
