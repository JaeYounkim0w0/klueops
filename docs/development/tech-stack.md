# Tech Stack

## Backend

- Java 17 LTS
- Spring Boot
- Spring AI
- Spring Security OAuth2 Client 기반 OIDC BFF, Spring Session JDBC
- Spring Data JPA
- PostgreSQL
- springdoc-openapi
- Fabric8 Kubernetes Client
- Helm CLI Wrapper
- Maven

## Frontend

- Vue 3
- TypeScript
- Vite
- Pinia
- Vue Router
- PrimeVue
- Orval

### Frontend UX Implementation Policy

- 화면 상단에는 현재 화면의 대표 생성/갱신 액션만 노출한다.
- table row마다 반복되는 작업은 버튼을 나열하지 않고 `Operations` 또는 kebab action menu로 묶는다.
- table, card, scroll container 내부의 action menu는 clipping을 피하기 위해 row 내부에 패널을 직접 렌더링하지 않고 fixed/overlay layer로 분리한다.
- destructive action은 action menu 내부에서 danger style로 구분하고 확인 절차를 둔다.
- 비동기 작업은 row 근처에 짧은 inline feedback으로 결과를 표시한다.
- 장시간 작업은 REST 요청을 blocking하지 않고 backend job id를 표시한 뒤 전역 Job Dock에서 진행 상태를 확인할 수 있게 한다.
- 등록/수정처럼 입력량이 많은 작업은 page 전환보다 modal/drawer를 우선 사용한다.
- cluster credential 입력은 kubeconfig를 기본값으로 두고, ServiceAccount token은 fallback 선택지로 제공한다.
- cluster 등록 후에는 즉시 connection test를 수행할 수 있는 UX를 제공한다.

### Frontend Style and Reuse Policy

- CSS는 `frontend/src/styles` 공통 폴더에서 관리한다.
- Vue SFC 내부 `<style>`과 정적 inline style은 사용하지 않는다.
- `frontend/src/main.ts`만 전역 CSS를 import한다.
- 기본 input/select/textarea/button, surface, form layout, modal과 상태 표현은 공통 CSS와 semantic class를 우선 사용한다.
- 둘 이상의 화면에서 재사용 가능하거나 업무 기능에 종속되지 않는 스타일은 공통 CSS가 소유하며 기능별 CSS에 중복 정의하지 않는다.
- 기능별 CSS는 editor, topology, resource viewer처럼 해당 기능만의 구조·상태·상호작용에 한정하고, 공통 design token과 UI primitive를 기반으로 작성한다.
- `div` 같은 일반 요소의 전역 selector로 화면을 꾸미지 않고 역할이 드러나는 공통 class를 부여한다.
- 재사용 가능한 TypeScript 로직은 `frontend/src/api`, `frontend/src/utils`, `frontend/src/composables`, `frontend/src/stores` 중 목적에 맞는 위치에 둔다.
- 관련 기준은 `docs/development/frontend-style-guide.md`와 `docs/architecture/frontend-architecture.md`를 따른다.

## AI

- 기본 provider: Ollama
- 확장 후보: OpenAI, Gemini, Azure OpenAI, Anthropic

## Security

- OAuth2/OIDC Authorization Code + 서버 session
- Envelope Encryption
- AES-256-GCM
- Vault/KMS adapter 확장

## Observability

- Spring Boot Actuator
- Micrometer
- structured logging
- requestId/correlationId
