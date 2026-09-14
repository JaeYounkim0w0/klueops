# Frontend Architecture

Frontend는 Vue 3 + TypeScript + Vite 기반으로 구성한다.

주요 원칙:

- API 타입은 Orval generated client를 사용한다.
- 전역 상태는 Pinia를 사용한다.
- 라우팅은 Vue Router를 사용한다.
- 운영 콘솔 UX에 맞춰 PrimeVue DataTable, Dialog, Toast, Tabs를 우선 사용한다.
- CSS는 Vue SFC 내부에 작성하지 않고 `frontend/src/styles` 공통 폴더에 둔다.
- 재사용 가능한 TypeScript 로직은 목적에 따라 `frontend/src/api`, `frontend/src/utils`, `frontend/src/composables`로 분리한다.
- 프론트 개발 시 `docs/development/frontend-style-guide.md`를 기준으로 검증한다.
