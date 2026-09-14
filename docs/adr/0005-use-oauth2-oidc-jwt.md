# ADR-0005: OAuth2/OIDC BFF 채택

## Status

Accepted

## Context

운영 권한을 다루는 제품이므로 자체 비밀번호 저장을 피해야 한다.

## Decision

사용자 UI에는 OAuth2/OIDC Authorization Code 기반 BFF 방식을 채택한다. Spring Boot OAuth2 Client가 token을 서버에서 관리하고 브라우저에는 HttpOnly session cookie만 전달한다. 외부 자동화 API의 machine-to-machine 인증은 별도 ADR에서 정의한다.

## Consequences

외부 IdP와 연동할 수 있고 인증 책임을 줄일 수 있다.

브라우저 token 저장을 제거하고 CSRF와 서버 session 운영이 필수 구성요소가 된다.
