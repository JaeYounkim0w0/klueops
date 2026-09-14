# ADR-0008: AsyncJob 모델 채택

## Status

Accepted

## Context

Helm, AI 분석, 동기화는 장시간 작업이다.

## Decision

장시간 작업은 AsyncJob으로 추적하고 API는 jobId를 반환한다.

## Consequences

REST 요청 blocking을 줄이고 UI에서 작업 상태를 추적할 수 있다.

