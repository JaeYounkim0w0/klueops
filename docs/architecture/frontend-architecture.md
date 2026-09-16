# Frontend Architecture

Frontend는 Vue 3 + TypeScript + Vite 기반으로 구성한다.

주요 원칙:

- API 타입은 Orval generated client를 사용한다.
- 전역 상태는 Pinia를 사용한다.
- 라우팅은 Vue Router를 사용한다.
- 운영 콘솔 UX에 맞춰 PrimeVue DataTable, Dialog, Toast, Tabs를 우선 사용한다.
- CSS는 Vue SFC 내부에 작성하지 않고 `frontend/src/styles` 공통 폴더에 둔다.
- 제품 전반의 기본 UI는 공통 CSS와 semantic UI primitive를 적용한다. 기능별 CSS는 해당 기능 고유의 구조·상태·상호작용만 소유하며 공통 control, surface, spacing과 상태 규칙을 복제하지 않는다.
- 일반 HTML 요소에 광범위한 전역 스타일을 적용하지 않고 역할이 드러나는 공통 class와 design token을 사용한다.
- 재사용 가능한 TypeScript 로직은 목적에 따라 `frontend/src/api`, `frontend/src/utils`, `frontend/src/composables`로 분리한다.
- 프론트 개발 시 `docs/development/frontend-style-guide.md`를 기준으로 검증한다.
