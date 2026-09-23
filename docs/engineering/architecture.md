# 아키텍처·백엔드 설계 기준

## 설계 스택

Hexagonal Architecture, port/adapter, use case 중심 application service, DTO 기반 API, migration 기반 데이터 관리, 비동기 Job을 조합한다. 이 구조를 사용한다는 이유만으로 microservice·event sourcing·CQRS를 필수 도입하지 않는다.

```text
HTTP Controller -> inbound port -> application service -> domain
                                      |
                                 outbound port
                                      ^ 구현
                       persistence / external API adapter
```

| 위치 | 책임 | 금지하는 결합 |
| --- | --- | --- |
| `domain` | 값 객체·상태 전이·업무 규칙 | Spring/JPA/외부 SDK 의존 |
| `application/port/in` | 사용 사례 계약 | HTTP DTO·DB Entity 노출 |
| `application/port/out` | 저장·외부 기능 추상화 | SDK 타입 노출 |
| `application/service` | 사용 사례 조합·transaction 경계 | adapter 구현체 직접 의존 |
| `adapter/in/web` | 입력 검증·권한 문맥·DTO 매핑·HTTP 응답 | Entity 직접 반환·업무 규칙 집중 |
| `adapter/out/*` | DB·외부 API·암호화·CLI 구현 | 기술 세부사항의 상위 계층 유출 |
| `config` | 객체 연결과 환경 설정 | 업무 정책 구현 |

## 데이터와 transaction

- schema 변경은 Flyway migration으로 관리한다. 이미 배포된 migration을 고치지 않고 새 migration을 추가한다.
- Entity와 도메인 모델, Request/Response DTO의 책임을 분리한다.
- 목록은 pagination 기본값과 상한, 허용 정렬 필드와 filter를 정의한다. 권한 범위 predicate와 index를 같이 검토한다.
- N+1, 무제한 전체 조회, 요청마다 반복되는 원격 조회를 피한다. 예상 건수·query 수·payload 한도와 cache 무효화 조건을 정한다.
- 네트워크/LLM/CLI 응답을 기다리는 동안 DB transaction을 유지하지 않는다.
- 동일 대상 mutation은 DB lock 또는 원자 claim으로 직렬화한다. 프로세스 내부 lock만으로 여러 replica를 보호하지 않는다.

## API 계약

- 리소스 중심 REST path와 명시적인 Request/Response DTO를 사용한다. 입력 형식과 업무 규칙 검증을 구분한다.
- 오류는 ProblemDetail 기반으로 `type`, `title`, `status`, `detail`, `instance`와 제품 오류 `code`, `requestId`, `timestamp`를 제공한다. 내부 stack trace와 credential은 노출하지 않는다.
- mutation에는 주체·대상·결과를 추적할 audit을 남긴다. audit에도 민감정보를 넣지 않는다.
- 목록의 페이지 시작값, 크기 상한, filter/sort와 오류 코드를 문서·계약 테스트에 고정한다.
- API 변경은 runtime OpenAPI 검증 → snapshot 갱신 → client 재생성 → drift 확인 → 소비 화면 타입/동작 검증 순으로 처리한다.
- 스트리밍은 연결 종료·재연결·취소·권한 만료·출력 상한을 설계한다. SSE/WebSocket은 실제 필요할 때 채택한다.

## 비동기 Job

오래 걸리는 작업은 Job 식별자를 반환하고 조회 또는 stream으로 상태를 제공한다. 참조 상태는 `PENDING → RUNNING → SUCCEEDED / FAILED / CANCELED / TIMEOUT`이다. 허용 전이와 terminal 상태의 의미를 도메인에서 정의한다.

1. 요청 transaction에서 Job·대상 상태·operation 이력을 함께 저장한다.
2. commit 이후에만 제한된 executor에 제출한다. flush는 commit을 대신하지 않는다.
3. rollback 시 미제출, 큐 포화·worker 시작 실패 시 별도 transaction으로 실패를 기록한다.
4. 취소·timeout·재시작 후 stale 작업 복구와 중복 실행 방지를 설계한다.
5. 메모리 executor는 재시작 후 자동 재개를 보장하지 않는다. 내구성 있는 재개가 요구되면 DB queue/outbox/message broker 등을 별도 결정한다.

## 코드 크기와 주석

- 한 파일은 한 책임을 중심으로 작성한다. 약 400줄 초과 또는 독립 검증할 책임이 둘 이상이면 분리를 검토한다. 숫자 충족을 위한 기계적 분할은 피한다.
- 신규·의미가 바뀐 메서드 선언 위에 책임과 의도를 한글 한 문장으로 적는다. Java는 Javadoc, TypeScript/Vue는 JSDoc 또는 설명 주석을 사용한다.
- 생성자, 단순 getter/setter, record 접근자, 명백한 framework 위임과 생성 코드는 주석 예외로 둘 수 있다.
- 핵심 public API, 권한·성능·안전 관련 분기는 제약과 이유를 설명한다. 코드의 동작을 줄마다 반복 설명하지 않는다.
- 기존 거대 파일은 변경 부분의 계약을 확보하고 점진적으로 분리한다. 현재의 기술 부채를 템플릿 구조로 복제하지 않는다.
