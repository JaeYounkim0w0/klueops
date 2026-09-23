# 검증·문서·완료 기준

## 변경 유형별 검증

실제 명령과 환경 요구사항은 [프로젝트 프로필](project-profile.md)에 기록한다. 파일명이 같다는 이유로 다른 저장소에 존재하지 않는 스크립트를 실행하거나 통과했다고 보고하지 않는다.

| 변경 | 검증할 계약 |
| --- | --- |
| domain/application | 관련 규칙·상태 전이·실패 분기, architecture 의존성 |
| DB/persistence | 실제 DB integration, migration, 범위 제한과 동시성 |
| API | validation·권한·오류, runtime OpenAPI, snapshot/client drift |
| 비동기 mutation | commit 전 미실행·rollback 미제출·포화 실패·중복 거부·복구 |
| Frontend | CSS 정책, typecheck, 관련 Vitest, production build |
| 사용자 흐름 | Playwright 및 실제 브라우저의 성공/오류/권한/deep-link |
| 보안/AI/외부 실행 | 마스킹·schema·fallback·timeout·실행 범위 |
| 배포 | render/lint, health/readiness, upgrade/rollback, 구성·secret |
| 문서만 변경 | 파일·상대 링크·명령의 존재, 구현과 설명 일치 |

단위 검증, 실제 DB/adapter 통합 검증, 브라우저 검증, 운영 수용 검증을 구분한다. 단위 테스트 통과로 실환경 완료를 대신하지 않는다. Frontend generated-client drift 검사는 생성 파일을 갱신할 수 있으므로 실행 후 diff를 확인한다.

## 운영 품질

- 요청·Job·외부 호출을 correlation ID로 연결한다. 로그·metric label에 비밀정보나 무제한 cardinality 값을 넣지 않는다.
- 성능 결과에는 데이터 규모, 동시성, warm-up, 반복 수, p50/p95/p99와 실패율을 남긴다.
- non-root runtime, readiness/liveness, 자원 제한, secret 주입, backup/restore와 migration 호환성을 배포 기준에 포함한다.
- 테스트 환경이 없거나 credential이 없어 실행하지 못한 항목은 미검증으로 남긴다. 운영 차단 상태를 통과로 바꾸지 않는다.

## 문서 관리

- 제품 동작, 기술 구조, 운영 절차, 잔여 작업은 각각 하나의 기준 문서가 소유한다. 날짜별 보고서가 현재 계약과 경쟁하지 않게 한다.
- API 변경은 계약·생성 client, 구조 변경은 architecture, 보안 변경은 security/운영 절차, 사용자 흐름 변경은 사용자 가이드를 함께 갱신한다.
- 되돌리기 어려운 결정은 ADR에 배경·대안·결정·결과·재검토 조건을 기록한다.
- 프로젝트 전용 사용자 가이드 형식과 업데이트 요구는 프로필에 둔다. 원본 프로젝트의 Word 가이드 의무를 모든 새 프로젝트에 자동 부과하지 않는다.
- 로컬 절대 경로·실제 계정·credential을 예제에 넣지 않는다. 저장소 문서는 복사 가능한 상대 링크를 사용한다.

## 완료 보고

결과에는 무엇을 왜 바꿨는지, 실행한 검증과 결과, 남은 위험/미검증 사항을 적는다. code·contract·문서가 일치하고 변경에 필요한 검증이 통과해야 완료다. 검증 범위 확대는 변경 위험에 맞추고 관계없는 전면 재검증은 피한다.
