# Authentication And Authorization

이 문서는 요약 진입점이다. 상세 기준은 아래 그룹 문서를 따른다.

- 현재 계정/로그인 범위: [제품 통합 명세](../product/current-product-specification.md#47-settings와-접근-제어)
- BFF 구조: [identity/oidc-bff-architecture.md](identity/oidc-bff-architecture.md)
- 역할/범위: [authorization/rbac-and-scope-model.md](authorization/rbac-and-scope-model.md)
- 운영 설정: [operations/keycloak-and-security-configuration.md](operations/keycloak-and-security-configuration.md)
- 검증: [testing/security-validation-plan.md](testing/security-validation-plan.md)

현재 표준은 Spring Security OAuth2 Client 기반 OIDC BFF와 서버 세션이다. 역할은 `PLATFORM_ADMIN`, `CLUSTER_ADMIN`, `OPERATOR`, `VIEWER`이며 목록을 포함한 API 응답은 서버에서 cluster/namespace 범위로 제한한다.
