# Frontend Style and Reuse Guide

## 목적

Vue 화면별 파일에 CSS와 재사용 로직이 흩어지면 화면이 늘어날수록 유지보수 비용이 커진다. 이 프로젝트는 운영 콘솔 제품이므로 UI 일관성, 화면 밀도, 접근성, 검증 가능성을 위해 스타일과 재사용 로직을 공통 위치에 둔다.

## CSS 배치 원칙

- Vue SFC에는 `<style>` 또는 `<style scoped>`를 작성하지 않는다.
- 정적 inline style은 사용하지 않는다.
- 공통 스타일은 `frontend/src/styles` 아래에 작성한다.
- `frontend/src/main.ts`만 전역 CSS를 import한다.
- 화면별로 필요한 스타일도 재사용 가능한 class로 설계하고 `frontend/src/styles`에 둔다.
- 동적 overlay 위치처럼 런타임 좌표가 필요한 경우에만 Vue `:style` binding을 예외적으로 허용한다.

현재 스타일 파일 역할:

- `frontend/src/styles/base.css`: root, body, button, input 등 전역 기본값
- `frontend/src/styles/main.css`: layout, table, modal, action menu, chat, analysis 등 재사용 UI class

## 재사용 JavaScript/TypeScript 배치 원칙

- API 호출과 DTO 변환은 `frontend/src/api`에 둔다.
- Vue lifecycle이나 reactive state가 없는 순수 함수는 `frontend/src/utils`에 둔다.
- Vue Composition API 기반 재사용 로직은 `frontend/src/composables`에 둔다.
- 화면 간 공유되어야 하는 reactive state는 `frontend/src/stores` Pinia store에 둔다.
- view component에는 화면 조립과 최소 상태만 둔다.
- 포맷팅, 오류 정규화, 정렬/필터, 스트리밍 파싱, Kubernetes 리소스 표시 규칙은 반복되면 즉시 공통 파일로 승격한다.
- 장시간 작업 상태는 화면별 local state에만 두지 않고 전역 Job Center store와 공통 하단 Job Dock으로 노출한다.

## 검증

프론트엔드 검증은 스타일 거버넌스를 먼저 수행한다.

```bash
scripts/validate-frontend.sh
```

개별 확인:

```bash
scripts/validate-frontend-style.sh
```

검증 기준:

- Vue SFC 내부 `<style>` 금지
- 정적 inline style 금지
- CSS import는 `frontend/src/main.ts`로 집중

## 개발 시 체크리스트

- 새 UI class가 기존 `frontend/src/styles` class로 표현 가능한지 먼저 확인한다.
- 버튼, 테이블, 모달, 상태 pill, action menu는 기존 class를 재사용한다.
- 새로운 UI 패턴이 생기면 Vue 파일보다 스타일 공통 파일을 먼저 확장한다.
- view component가 비대해지면 API, util, composable로 분리한다.
- 화면 이동 후에도 유지되어야 하는 상태는 Pinia store로 분리한다.
- 스타일이나 재사용 규칙을 바꾸면 이 문서와 `docs/architecture/frontend-architecture.md`를 함께 갱신한다.
