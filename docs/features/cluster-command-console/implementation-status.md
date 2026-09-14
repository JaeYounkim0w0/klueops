# Kubernetes Console Implementation Status

기준일: 2026-09-09

이 문서는 Kubernetes Console의 현재 코드 기준 구현 범위를 기록한다. 다른 문서의 장기 설계와 내용이 다를 때 현재 동작 판단은 이 문서를 우선한다.

## 사용자 진입 경로

- `Clusters > 클러스터 상세 > Kubernetes 콘솔`
- 직접 경로: `/clusters/{clusterId}/console`
- 리소스 상세 YAML 화면의 `콘솔에서 열기`
- Pod 리소스 상세의 `Pod 터미널 열기`
- `operation:execute` capability가 없는 사용자는 버튼과 라우트에 접근할 수 없다.

## 구현 완료

- 등록 credential로 실제 `kubectl` 명령 실행
- shell을 사용하지 않는 argv 실행 및 context/credential override 차단
- 조회, 진단, 변경, 삭제, 대화형 명령 분류와 변경 명령 확인
- stdout/stderr SSE streaming, 취소, timeout, 1 MiB 기본 출력 상한
- 실행 이력과 개인/클러스터 공유 즐겨찾기 PostgreSQL 저장
- 서비스 상태 점검 Cook Book에서 Pod, Service/Endpoint, workload, 자원, Node, storage, network와 권한 점검 명령을 검색하고 현재 작업 공간으로 가져오기
- Cook Book은 Console의 단일 `kubectl` 명령 계약을 따르며 Secret 값 출력, shell pipe, port-forward와 임시 리소스 생성 명령은 기본 항목에서 제외
- PostgreSQL advisory lock 기반 사용자·클러스터 동시 실행 quota, 분당 시작 rate limit과 만료 lease
- 유효 lease가 없는 `QUEUED/RUNNING` 실행의 재기동 orphan 복구와 Audit
- AI Analysis 검증 명령에서 Console 이동, 원본 Analysis 연결, 복귀와 동일 범위 재분석
- `kubectl exec -it`, `kubectl attach -it`용 일회성 ticket과 WebSocket TTY
- 30초 후 만료되는 난수 session UUID를 일회성 WebSocket ticket으로 사용하고, 연결 후 입력/resize를 인증된 WebSocket principal에 고정
- 터미널 idle timeout, resize, disconnect 정리, 종료 결과 및 audit 저장
- OIDC REST/WebSocket principal 표현 차이로 인한 잘못된 ticket 거부 방지
- Fabric8 exec/attach 생성 시 stdin redirection을 명시하고 입력 채널 부재를 연결 단계에서 차단
- 정상 shell 종료에서는 최종 status frame을 먼저 전달한 뒤 WebSocket과 Fabric8 watch를 순서대로 정리
- 종료 코드와 소요 시간은 값이 생성된 완료 상태에서만 표시하며 WebSocket 거부 상세를 화면과 운영 로그에 기록
- 대화형 터미널은 viewport 높이를 제한하고 자체 scrollback을 사용한다. 키 입력과 툴바의 아래쪽 이동 버튼은 최신 prompt로 복귀하며 ResizeObserver가 레이아웃 변화에 맞춰 TTY 크기를 다시 계산한다.
- 한국어/영어와 desktop/mobile responsive layout
- 별도 화면 밀도 선택은 제거했다. 간결한 context/section 간격을 기본으로 사용하되 터미널은 읽기 쉬운 12px 글꼴과 화면 높이에 반응하는 300~420px viewport를 유지한다.
- Backend container image에 checksum 검증한 `kubectl` 포함. Docker BuildKit의 대상 아키텍처를 사용하고 값이 없으면 컨테이너 아키텍처를 감지하므로 AMD64/ARM64에서 같은 Dockerfile을 사용한다.
- OpenAPI annotation과 Flyway `V25__kubernetes_console.sql`

## 2026-09-09 Closed Loop Runner 완료

- 일반 kubectl 프로세스를 Portal Backend와 분리한 `command-runner` Deployment를 추가했다. Backend는 token 인증된 cluster-internal NDJSON API만 사용하며 원격 실행기가 실패하면 로컬 실행으로 우회하지 않는다.
- Runner는 shell 없이 argv를 실행하고, 일회 execution ID replay guard, 비루트·읽기 전용 파일시스템, 제한된 `/tmp`, resource limit과 Backend 전용 ingress NetworkPolicy를 사용한다.
- 변경·삭제 명령은 실행 전후 대상 리소스의 정규화 Snapshot을 비교해 `VERIFIED_CHANGED`, `VERIFIED_STABLE`, `VERIFICATION_FAILED`로 판정한다. Secret 계열 필드와 volatile metadata는 제거한다.
- `scale`은 실행 전 replicas로 되돌리는 후보를 만들고, `rollout`과 workload `set`은 `rollout undo` 후보를 만든다. 안전하게 복원할 근거가 없으면 롤백 명령을 추측하지 않는다.
- Console은 리소스 기반 Get, Describe, Logs, YAML 명령 빌더와 Event, Log, YAML 후속 확인, 전후 Snapshot, 원본 Analysis 재분석을 한 작업 공간에서 제공한다.
- Incident Markdown, JSON, ZIP 보고서는 원본 Analysis에 연결된 Command Execution의 actor, 시각, exit code, 검증 상태와 Snapshot hash를 포함한다. credential, live log, 원문 Snapshot은 내보내지 않는다.
- 대화형 `exec/attach` TTY는 스트림 특성 때문에 기존 Backend Fabric8 WebSocket 경계를 유지하며 화면에 실행 경계를 구분해 표시한다. TTY의 별도 격리는 후속 보안 변경으로만 진행한다.
- Runner NDJSON writer는 개별 event 직렬화가 HTTP stream을 닫지 않게 분리했다. 출력 frame 뒤 최종 result frame이 반드시 전달되는 계약을 회귀 테스트로 보호한다.

## 현재 실행 구조

- 패키지 Kubernetes 환경의 일반 명령은 별도 Command Runner가 실제 `kubectl` binary를 `ProcessBuilder` argv로 실행한다.
- 개발 환경은 `AIOPS_COMMAND_RUNNER_MODE=local`을 명시할 수 있지만 Runtime Readiness에서 `PILOT`이며 운영 프로필에서는 `BLOCKED`다.
- Runner는 실행마다 권한이 제한된 임시 디렉터리와 kubeconfig를 생성하고 종료 시 삭제한다.
- 대화형 TTY는 Fabric8 Kubernetes Client의 exec/attach 채널을 사용한다.
- 플랫폼 RBAC은 REST 진입 시 `operation:execute`와 cluster/namespace scope를 확인한다.
- WebSocket transport 경로는 proxy별 session/Origin 전달 차이를 허용하되, 인증된 REST에서 `operation:execute`, tenant/cluster scope를 확인한 뒤 발급한 30초·단일 사용 ticket을 필수로 소비한다. 연결 후 입력과 resize 권한은 해당 socket에만 고정한다.
- Docker 이미지의 Nginx 설정과 Helm이 mount하는 Nginx ConfigMap 모두 `/ws/` upgrade proxy를 제공한다. 패키징 검증이 `Upgrade`와 `Connection` header 계약을 검사한다.
- 개발 Vite와 배포 Nginx/Ingress는 구현은 다르지만 `/ws` upgrade와 일회성 ticket이라는 동일한 제품 계약을 사용한다. Vite는 backend 기준 Host/Origin을 정규화하고 배포 proxy는 외부 host의 forwarded header를 보존한다.

## 2026-09-11 Cook Book 및 운영 경계 강화

- 증상별 Pod, Service, Storage, Node 4단계 절차와 정상 판정 기준, 다음 권장 점검을 제공한다.
- 명령 빌더에 입력한 resource/container 이름은 Cook Book placeholder에 자동 반영하며 미입력 값은 placeholder로 남겨 실행을 차단한다.
- 등록 credential로 Metrics APIService의 `Available=True`를 확인한 경우에만 `kubectl top` 항목을 선택할 수 있다.
- Audit과 완료된 CommandExecution 보존 기간, dry-run 삭제 건수, 실제 cleanup과 cleanup Audit을 운영 설정에 추가했다.
- 대화형 TTY는 [ADR-0009](../../adr/0009-retain-backend-fabric8-for-interactive-tty.md)에 따라 Backend Fabric8 경계를 유지한다. 별도 quota/rate limit, 단일 사용 ticket, idle/output 상한과 배포 NetworkPolicy를 제품 계약으로 확정했다.

## 의도적으로 남긴 운영 강화 항목

- tenant 공용 즐겨찾기 관리자 발행 정책
- 전용 `kubectl cp` 파일 전송 UI와 악성 파일 검사
- 인증 gateway가 포함된 browser port-forward UI
- 대형 클러스터 및 실제 원격 RBAC 계정 기반 부하/침투 테스트

`cp`와 `port-forward`는 일반 kubectl 실행 경로에서 표준 명령으로 처리할 수 있으나, 파일 및 네트워크를 브라우저에 안전하게 노출하는 전용 UI는 아직 제공하지 않는다. 전용 gateway의 보안·운영 조건은 `../../product/remaining-development-items.md`의 P1-05에서 관리한다.

## 검증 명령

```bash
./scripts/validate-backend.sh
./scripts/validate-frontend.sh
```

수동 검증에서는 클러스터 상세에서 Console 진입, `kubectl get nodes`, namespace별 `get pods`, 즐겨찾기 CRUD, 실행 이력, 취소, `kubectl exec -it POD -- /bin/sh`를 확인한다.

## 2026-09-09 실제 클러스터 검증

- 대상: `dev-master / strato-product / comp-portal-backend-64db666487-t98hc`
- 명령: `kubectl exec -it comp-portal-backend-64db666487-t98hc -- /bin/sh`
- 확인: WebSocket 연결, TTY prompt, `pwd`, `id`, stdout 출력, `exit` 순서로 검증
- 결과: `SUCCEEDED`, exit code `0`; 화면과 PostgreSQL 실행 이력에 동일하게 반영
- 수정 원인: Fabric8 `ExecWatch`가 stdin redirection 없이 생성되어 `getInput()`이 `null`이었고, watch를 먼저 닫던 종료 순서가 성공 상태 frame 전송을 방해했다.
- 개발 경로 검증: `http://127.0.0.1:5173`의 Vite `/ws` proxy에서 Host/Origin 변환으로 Spring handshake가 거부되는 문제를 재현했다. 개발 proxy의 WebSocket 헤더 정규화와 ticket 기반 transport 경계를 적용한 뒤 `printf 'terminal-5173-ok\n'`, `exit`를 실행해 `SUCCEEDED`, exit code `0`을 확인했다.
- AI 연계 검증: OIDC 로그인 상태에서 `dev-master/default` 분석 ID `f83b67a9-28f5-45bd-b110-5a366f6e4aa7`의 Runbook 검증 버튼으로 Console에 진입했다. URL과 실행 요청에 원본 Analysis ID가 유지됐고 `kubectl get all,events -n default`가 exit code `0`, 122ms로 완료됐다.
- 분산 실행 제어 검증: PostgreSQL 17 Testcontainers에서 사용자·클러스터 quota, lease 해제, 만료 lease 회수와 Flyway V1~V26을 검증했다. Backend 전체 228 tests와 Frontend 84 tests가 통과했다.
- 격리 실행기 검증: Docker Desktop Kubernetes Helm revision 42에서 Backend, Frontend, Command Runner가 모두 `1/1 Ready`다. OIDC `aiops-admin` 세션으로 `dev-master/default`의 `kubectl get pods -n default`를 실행해 Runner 경계 표시, stdout NDJSON, `SUCCEEDED`, exit code `0`, 67ms를 확인했다.
- 2026-09-11 최신 자동 검증: PostgreSQL 17 Testcontainers와 Flyway V1~V28을 포함한 Backend 235 tests, Runner 5 tests, Frontend 26 files/91 tests, typecheck, production build, OpenAPI snapshot/Orval drift gate, architecture/docs/security/packaging/maintainability gate가 모두 통과했다. Docker Desktop Kubernetes Helm revision 46에서 Cook Book 절차, placeholder 자동 치환, Metrics API 차단, 읽기 명령 실행과 운영 보존 미리보기를 OIDC 관리자 세션으로 확인했다.
