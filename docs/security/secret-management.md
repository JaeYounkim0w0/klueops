# Secret Management

kubeconfig와 ServiceAccount token은 Envelope Encryption으로 암호화 저장한다.

개발 환경은 env master key 기반 AES-256-GCM을 사용할 수 있다.

운영 환경은 Vault 또는 Cloud KMS를 사용한다.

## Cluster Credential Priority

클러스터 등록 인증 방식은 다음 우선순위를 따른다.

1. kubeconfig
2. ServiceAccount token

kubeconfig를 1순위로 사용한다.

kubeconfig가 없거나 서버에서 사용할 수 없는 형태일 경우 ServiceAccount token 기반 등록을 사용한다.

MVP에서는 다음 방식을 지원한다.

- kubeconfig YAML 텍스트 입력
- kubeconfig 파일 업로드
- Kubernetes API Server URL + CA certificate + ServiceAccount token

MVP에서는 kubeconfig 내부 `exec` 인증 플러그인을 기본 지원하지 않는다.

`exec` 인증 플러그인이 포함된 kubeconfig는 등록 시 경고하거나 차단한다. 이 경우 사용자는 ServiceAccount token 기반 등록을 사용해야 한다.

저장 시에는 원본 credential을 평문 저장하지 않고, credential type과 암호화된 credential payload만 저장한다.

## Credential Reveal Policy

- 마스킹된 등록 정보 조회에는 `cluster:read`, 원문 조회에는 `cluster:manage` capability가 필요하다.
- 원문 조회는 기본 및 운영 Helm profile에서 비활성화한다. 로컬 검증 profile에서만 명시적으로 활성화하며 운영 모드에서는 Helm 검증 단계가 활성화를 거부한다.
- 사용자는 위험 안내를 확인한 후에만 원문을 볼 수 있고, 조회는 감사 이벤트로 기록한다. Frontend는 60초 뒤 자동으로 다시 마스킹하며 사용자가 즉시 숨길 수도 있다.
- UI 자동 마스킹은 서버·브라우저 메모리에서 값을 즉시 소거한다는 보장이 아니다. 공유 화면, 브라우저 개발자 도구, 프록시 및 로그에 값이 남지 않도록 원문 보기는 격리된 로컬 점검에만 사용한다.
- 운영에서는 원문 복구보다 credential rotation을 우선한다. ServiceAccount token과 kubeconfig는 클러스터별 전용 값으로 발급하고 Application 배포용 권한과 운영 동기화용 권한을 분리한다.
