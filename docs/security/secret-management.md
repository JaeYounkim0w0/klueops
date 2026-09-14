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
