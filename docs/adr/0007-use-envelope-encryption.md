# ADR-0007: Envelope Encryption 채택

## Status

Accepted

## Context

kubeconfig와 token은 클러스터 접근 권한 그 자체다.

## Decision

Envelope Encryption을 사용한다.

## Consequences

개발 환경은 local master key, 운영 환경은 Vault/KMS adapter를 사용할 수 있다.

