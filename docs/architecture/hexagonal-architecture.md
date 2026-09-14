# Hexagonal Architecture

## Layers

- `domain`: 순수 도메인 모델과 규칙
- `application`: usecase, inbound/outbound port, application service
- `adapter.in.web`: REST controller, DTO, OpenAPI 문서화
- `adapter.out.*`: 외부 시스템 구현체
- `config`: Spring configuration

## Forbidden Dependencies

- Domain이 Spring, JPA, Fabric8, Spring AI에 의존하면 안 된다.
- Application service가 adapter 구현체에 의존하면 안 된다.
- Controller가 JPA Entity를 직접 반환하면 안 된다.
- AI provider SDK가 domain/application layer에 노출되면 안 된다.
- Helm CLI 실행 코드가 usecase 내부에 존재하면 안 된다.

