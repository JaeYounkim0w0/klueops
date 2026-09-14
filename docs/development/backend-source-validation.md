# Backend Source Validation

Backend 검증은 저장소 script를 단일 실행 진입점으로 사용하고 동일 규칙을 로컬, CI와 자동화 도구에서 재사용한다.

표준 검증 명령:

```bash
./scripts/validate-backend.sh
```

검증 대상:

- compile/test
- ArchUnit 기반 헥사고날 아키텍처 규칙
- Flyway migration + JPA schema validation
- OpenAPI annotation/spec 생성
- ProblemDetail 공통 에러 포맷
- AsyncJob 패턴
- Secret/PII 마스킹
- AI schema validation
- Helm/Kubernetes adapter 안전성
- 필수 docs 존재 여부

자동화 원칙:

```text
검증 규칙 = repo scripts
품질 강제 = CI
AI 자동화 = MCP
```

## 검증 단계

1. Java 17 toolchain과 dependency resolution을 확인한다.
2. unit/integration test와 JaCoCo 기준을 실행한다.
3. ArchUnit으로 domain/application/adapter 의존 방향을 검증한다.
4. PostgreSQL Testcontainers에서 Flyway migration과 JPA 계약을 확인한다.
5. OpenAPI runtime spec, annotation과 generated frontend client drift를 확인한다.
6. 인증/인가, scope, CSRF, masking, credential encryption 회귀를 실행한다.
7. AI schema, section fallback, evidence coverage와 command safety fixture를 실행한다.
8. Docker/Helm과 readiness/liveness 계약을 검증한다.

검증 환경이 Docker, registry 또는 외부 cluster에 접근하지 못하면 해당 단계를 성공으로 바꾸지 않는다. 원인과 재실행 위치를 `CONDITIONAL` 또는 `BLOCKED`로 보고한다.
