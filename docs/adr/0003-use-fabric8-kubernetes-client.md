# ADR-0003: Fabric8 Kubernetes Client 채택

## Status

Accepted

## Context

Java/Spring Boot에서 Kubernetes 리소스 조회, 로그, 이벤트, mock 테스트가 필요하다.

## Decision

Fabric8 Kubernetes Client를 사용한다.

## Consequences

개발 생산성과 테스트 편의성이 좋다. Fabric8 의존성은 adapter 내부로 제한한다.

