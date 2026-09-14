# Security Architecture

- 사용자 UI 인증은 OAuth2/OIDC BFF와 서버측 JDBC session을 사용한다. 브라우저는 OAuth token을 저장하지 않는다.
- 상세 인증/인가 기준은 `docs/security/`의 identity, authorization, operations, testing 그룹을 따른다.
- kubeconfig/token은 Envelope Encryption으로 저장한다.
- 운영 환경은 Vault 또는 Cloud KMS adapter를 권장한다.
- Secret/PII는 DB, 로그, AI prompt에 평문으로 남기지 않는다.

## Kubernetes Cluster Credentials

클러스터 등록 credential은 kubeconfig를 우선 사용한다.

kubeconfig를 사용할 수 없으면 ServiceAccount token 방식을 사용한다.

ServiceAccount token 방식은 다음 정보를 요구한다.

- Kubernetes API Server URL
- CA certificate
- ServiceAccount token

credential payload는 adapter 내부에서 Kubernetes client config로 변환한다.
