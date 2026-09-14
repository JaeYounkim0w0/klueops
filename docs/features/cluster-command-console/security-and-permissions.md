# Security And Permissions

## 1. 이중 권한 판정

명령 실행은 두 권한을 모두 만족해야 한다.

1. 플랫폼 사용자의 tenant/cluster/namespace scope와 capability
2. 등록된 kubeconfig 또는 ServiceAccount가 원격 Cluster에서 가진 Kubernetes RBAC

사용자 숙련 모드는 권한이 아니다. 초보자/숙련자 보기 모두 동일한 kubectl 명령을 입력할 수 있으며 capability와 scope만 실행 가능 여부를 결정한다.

공유 ServiceAccount를 사용하면 Kubernetes audit에는 플랫폼 최종 사용자가 직접 나타나지 않는다. 따라서 플랫폼 audit에 사용자 ID, tenant, Cluster, Namespace, argv metadata, safety, 결과와 request ID를 기록한다.

## 2. Capability

| Capability | 설명 | 기본 역할 |
| --- | --- | --- |
| `command:read` | 조회 명령 실행과 이력 조회 | VIEWER 이상 |
| `command:diagnose` | 로그/이벤트/describe와 streaming | VIEWER 이상 |
| `command:change` | 생성·수정·삭제 kubectl 명령 | OPERATOR 이상, 할당 scope 내 |
| `command:interactive` | exec/attach interactive session | OPERATOR 이상, 할당 scope 내 |
| `command:file-transfer` | kubectl cp 업로드/다운로드 | OPERATOR 이상, 정책 활성화 시 |
| `command:port-forward` | 인증된 임시 port-forward | OPERATOR 이상, 정책 활성화 시 |
| `command:favorite:write` | 개인 즐겨찾기 | VIEWER 이상 |
| `command:favorite:publish` | tenant 공용 즐겨찾기 | PLATFORM_ADMIN |
| `command:audit:read` | 전체 실행 감사 조회 | PLATFORM_ADMIN, 정책상 AUDITOR |

역할만 확인하지 않고 기존 object scope guard를 모든 endpoint와 repository query에 적용한다. Platform Admin도 원격 Kubernetes credential이 거부하는 동작을 우회하지 않는다. 권한 없는 명령은 숨기지 않지만 실행 시 명확한 403과 필요한 capability를 반환한다.

## 3. 명령 처리 정책

### 표준 kubectl 호환

- 첫 executable은 고정된 `kubectl`이며 생략 입력도 서버가 kubectl argv로 정규화한다.
- verb, resource kind와 option을 allow-list로 제한하지 않는다.
- shell을 호출하지 않고 tokenizer 결과를 argv로 직접 전달한다.
- 알 수 없는 option의 유효성은 선택된 kubectl 바이너리가 판정한다.
- `--namespace`가 화면 context와 다르면 대상 변경을 명시하고 플랫폼 scope를 다시 검사한다.
- `--context`, `--cluster`, `--user`로 현재 Cluster를 바꾸는 동작은 허용하지 않는다. 대상 Cluster는 상세 페이지와 API path로 고정한다.

### kubectl 외 실행 차단

- shell 의미를 갖는 `;`, `&&`, `||`, `|`, `>`, `<`, backtick, `$(`과 null byte
- 명령 구분용 newline. YAML/JSON은 별도 manifest field 또는 파일로 전송한다.
- `--kubeconfig`, `--server`, `--token`, `--certificate-authority`와 credential 교체 option
- host의 다른 executable과 kubectl plugin

`apply -f`, `create -f`, `replace -f`와 stdin manifest는 허용한다. 브라우저의 manifest editor 또는 검증된 업로드 파일을 실행별 임시 경로에 배치하며 사용자가 Runner 경로를 지정할 수 없게 한다.

`--as`와 `--as-group` impersonation은 기본 비활성화한다. 제품이 사용자별 Kubernetes identity delegation을 제공할 때 별도 보안 검토 후 활성화한다.

## 4. 출력 보호

- Secret 조회는 허용하지만 화면, 다운로드, 저장 출력에서는 `data`와 `stringData`를 기본 마스킹한다. 민감정보 열람 capability는 별도 정책으로만 제공한다.
- ConfigMap은 일반 리소스로 조회하되 credential pattern을 마스킹한다.
- token, password, authorization header, kubeconfig, private key pattern을 마스킹한다.
- Pod log는 화면 전송과 저장 전에 credential/PII pattern을 마스킹한다.
- 다운로드 파일도 화면과 같은 redaction policy를 적용한다.
- 마스킹 전 원문을 application log 또는 audit에 기록하지 않는다.

## 5. 변경 안전성

- 변경 명령은 제한된 allow-list가 아니라 실제 kubectl로 실행한다.
- parser가 분류한 `CHANGE`, `DESTRUCTIVE`, `PRIVILEGED_INTERACTIVE` 위험도에 따라 확인 단계를 적용한다.
- 가능한 명령은 server dry-run/diff와 대상 resource UID/resourceVersion snapshot을 제공한다.
- dry-run을 지원하지 않거나 rollback plan이 없어도 권한 있는 사용자가 정확한 Cluster 이름을 입력하고 실행할 수 있다. 이 사실은 경고와 audit에 남긴다.
- Namespace 삭제, node drain/delete, CRD/webhook/RBAC 변경 등 blast radius가 큰 명령은 영향 대상과 복구 불확실성을 별도 강조한다.
- kubectl exit code가 0이어도 후속 상태 검증 전까지 변경 요청 성공과 운영 결과를 구분한다.

AI가 생성한 명령의 자동 실행에는 더 엄격한 기존 allow-list를 유지한다. 사용자가 콘솔에서 직접 검토해 실행하는 것과 AI 자동 조치를 동일한 정책으로 취급하지 않는다.

## 6. Interactive And Network Safety

- `exec/attach`는 세션 ID, 사용자, Pod, container, TTY 여부, 시작/종료 시각과 종료 코드를 감사한다. 입출력 전문은 credential과 개인정보 위험 때문에 기본 저장하지 않는다.
- interactive session은 동시 공유를 기본 금지하고 idle/max lifetime을 적용한다.
- `cp` 경로는 정규화하고 symlink/path traversal을 차단하며 업로드 크기와 파일 수를 제한한다.
- `port-forward`는 브라우저가 Runner 포트에 직접 연결하지 않는다. 인증된 gateway URL, 허용 포트 정책, TTL, idle timeout, 동시 세션 제한을 적용한다.
- `kubectl proxy`는 port-forward와 같은 인증 gateway를 거치며 외부 공개 URL을 만들지 않는다.

## 7. 남용 방지

- 사용자별 concurrent 일반 명령 5개, stream/interactive 3개, port-forward 2개
- Cluster별 concurrent 일반 명령 50개, stream/interactive 20개
- 사용자별 분당 validate 120회, execute 30회
- 같은 mutation target에 대한 동시 실행 경고와 선택적 lock
- 최대 command input 16KB, manifest와 파일은 별도 제한, text output 기본 1MB
- timeout과 cancel은 kubectl process 및 원격 stream까지 전파
- 반복 거부/실패는 security audit signal로 기록

## 8. CSRF와 세션

- 기존 OIDC BFF와 Spring Session JDBC를 사용한다.
- execute, cancel, favorite CRUD, file transfer, port-forward 생성은 CSRF 보호를 적용한다.
- SSE는 인증 session과 scope를 검사한다. WebSocket transport 경로는 개발 proxy, Ingress, 외부 TLS 종료를 지원하도록 Origin에 의존하지 않으며, 인증된 REST에서 RBAC과 tenant/cluster scope를 확인한 뒤 발급한 30초·단일 사용 ticket을 필수로 검사한다.
- stream URL에 token, command, credential을 query string으로 전달하지 않는다.
- execution/session ID는 추측 불가능한 UUID이며, terminal ticket은 최초 연결에서 원자적으로 소모하고 이후 입력과 resize를 그 연결에만 고정한다.

## 9. 감사 이벤트

- `COMMAND_VALIDATION_BLOCKED`
- `COMMAND_EXECUTION_STARTED/COMPLETED/FAILED/CANCELED`
- `COMMAND_INTERACTIVE_STARTED/COMPLETED`
- `COMMAND_FILE_TRANSFER_STARTED/COMPLETED/FAILED`
- `COMMAND_PORT_FORWARD_STARTED/COMPLETED/EXPIRED`
- `COMMAND_FAVORITE_CREATED/UPDATED/DELETED/PUBLISHED`

audit에는 원문 Secret과 전체 stdout/stderr 또는 interactive keystroke를 넣지 않는다. 안전하게 정규화한 argv metadata, target, safety, outcome과 evidence ID만 기록한다.
