# Generated Client Rules

## Source of truth

- Backend `/v3/api-docs`에서 export한 `frontend/openapi/klueops.json`이 frontend 생성 계약이다.
- `frontend/src/api/generated/klueops.ts`는 Orval 생성물이며 수동 편집하지 않는다.
- backend API가 변경되면 OpenAPI contract test, contract export, `npm run generate:api`, typecheck 순으로 수행한다.
- `./scripts/validate-generated-client.sh`는 같은 계약으로 재생성했을 때 drift가 없는지 검사한다.

## Migration policy

기존 `src/api/client.ts`는 한 번에 제거하지 않는다. 화면별 변경 시 생성 client의 함수와 schema를 adapter에서 사용하고, 수동 request/response type을 점진적으로 제거한다. 생성 코드 내부에 product-specific error 처리, locale, timeout을 넣지 않고 공통 transport 또는 adapter에서 적용한다.

Frontend는 Orval을 사용해 Backend OpenAPI spec 기반 client를 생성한다.

수동 API 타입 정의를 금지한다.

API 변경 시 generated client 최신 상태를 확인한다.
