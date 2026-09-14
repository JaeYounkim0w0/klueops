# OpenAPI Rules

모든 API는 springdoc-openapi 기반 Swagger/OpenAPI 문서를 제공한다.

Controller 기준:

- Controller class에 `@Tag`
- endpoint에 `@Operation`
- 주요 응답에 `@ApiResponses`
- Request/Response DTO field에 `@Schema`
- 인증 필요 API는 security scheme 반영

API 변경 시:

1. Controller/DTO 수정
2. OpenAPI annotation 수정
3. OpenAPI spec 생성 확인
4. Frontend Orval client 재생성 필요 여부 확인
5. 관련 docs 갱신

