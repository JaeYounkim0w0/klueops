# 보안과 선택 모듈

## 공통 보안

- 서버에서 인증·권한·tenant/workspace 등 대상 범위를 확인한다. UI 숨김은 권한 통제가 아니다.
- 브라우저 서비스의 참조 인증 방식은 OIDC Authorization Code + BFF 서버 세션이다. token 교환과 저장은 Backend가 맡고 브라우저는 HttpOnly session cookie를 사용한다.
- 운영 cookie의 Secure/SameSite, CSRF, idle/absolute timeout, logout과 proxy의 원본 Host/protocol 전달을 함께 설계한다.
- 개발용 무인증 profile과 운영 profile을 구분한다. 운영에 개발 우회 설정을 적용하지 않는다.
- credential은 secret 저장소/환경 주입으로 관리한다. 저장이 필요하면 envelope encryption 등 암호화 경계를 두고 master key를 ciphertext와 분리한다.
- 비밀정보를 로그·audit·오류·문서·AI prompt에 평문으로 남기지 않는다. key 이름뿐 아니라 값·중첩 구조·외부 stdout/stderr의 마스킹을 시험한다.
- 외부 호출의 URL/redirect·TLS·timeout·응답 크기·재시도 횟수를 제한한다. 사용자가 지정한 endpoint의 내부망 접근 범위는 제품 요구에 따라 명시적으로 통제한다.
- 공급망 검증은 SBOM, 라이선스, 취약점과 이미지 서명을 제품 배포 정책에 연결한다.

## 선택: AI 기능

AI 기능을 채택한 경우에만 적용한다.

- provider SDK와 HTTP 구현은 outbound adapter에 격리한다. model/provider와 목적별 routing은 설정으로 관리한다.
- 분석은 수집된 사실·결정론적 규칙·모델 추론을 구분한다. 제공되지 않은 metric이나 원인을 만들어 사실처럼 표시하지 않는다.
- context는 목적별 작은 section으로 나누고 token/크기/시간/동시성 상한을 둔다. 실패한 section이 성공 근거를 덮어쓰지 않게 한다.
- JSON 구조와 의미를 저장 전에 검증한다. 핵심 결론 누락을 임의 문장으로 보정하지 않는다.
- fallback은 원래 모델 응답과 구별해 표시한다. 재사용은 scope·근거 fingerprint가 같을 때만 허용하고 diagnostics를 남긴다.
- 재현 가능한 분석은 stateless로, 대화 memory는 별도 수명·보존·권한 정책으로 관리한다.
- 모델 출력은 신뢰하지 않는 입력으로 취급한다. 실행 제안은 서버 정책·권한·범위·preview/확인 검증을 통과해야 한다.
- fixture 기반 품질·schema·마스킹·부분 실패를 검증한다. 유창함만으로 성공 판정하지 않는다.

## 선택: Kubernetes·CLI 운영

Kubernetes를 관리하는 제품에만 적용한다. 일반 업무 서비스에 Runner/Fabric8/Helm을 복사할 필요는 없다.

- Fabric8 조회, Helm lifecycle, 일반 kubectl Runner와 대화형 TTY의 실행 경계를 분리한다.
- CLI는 검증된 argv로 실행하며 입력 문자열을 shell 명령으로 조립하지 않는다. 범위 고정·allowlist·RBAC·시간·출력 상한을 적용한다.
- Runner 장애 시 더 높은 권한의 Backend-local 실행으로 우회하지 않는다.
- mutation은 사전 검증, dry-run/preview, 위험에 맞는 확인, audit, 사후 확인과 rollback/cleanup 경로를 제공한다.
- Watch 연결 실패는 bounded polling으로 복구하고 중복 상태를 무한 저장하지 않는다.
- 실환경 fault fixture는 기본 비활성화, 전용 namespace·소유권 label·RBAC preflight·명시적 확인·TTL·idempotent cleanup을 적용한다.

제품 사용자의 위험 작업 확인 절차와 개발 에이전트의 일반 수정 권한은 별개다. 이 문서는 모든 코드·문서 변경에 추가 승인을 요구하는 규칙이 아니다.
