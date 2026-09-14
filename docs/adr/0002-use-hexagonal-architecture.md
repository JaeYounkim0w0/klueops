# ADR-0002: Hexagonal Architecture 채택

## Status

Accepted

## Context

Kubernetes, Helm, AI provider, DB, crypto adapter 교체 가능성이 높다.

## Decision

Backend는 헥사고날 아키텍처를 따른다.

## Consequences

도메인과 application usecase를 외부 SDK에서 분리할 수 있다.

