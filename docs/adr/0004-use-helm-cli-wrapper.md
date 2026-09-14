# ADR-0004: Helm CLI Wrapper 채택

## Status

Accepted

## Context

Java 환경에서 Helm 기능을 구현해야 한다.

## Decision

Helm CLI Wrapper 방식을 사용한다.

## Consequences

표준 Helm 동작을 활용할 수 있다. ProcessBuilder argument list, timeout, masking, validation이 필수다.

