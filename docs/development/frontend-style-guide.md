# Frontend Style and Reuse Guide

## 목적

Vue 화면별 파일에 CSS와 재사용 로직이 흩어지면 화면이 늘어날수록 유지보수 비용이 커진다. 이 프로젝트는 운영 콘솔 제품이므로 UI 일관성, 화면 밀도, 접근성, 검증 가능성을 위해 스타일과 재사용 로직을 공통 위치에 둔다.

## CSS 배치 원칙

- Vue SFC에는 `<style>` 또는 `<style scoped>`를 작성하지 않는다.
- 정적 inline style은 사용하지 않는다.
- 공통 스타일은 `frontend/src/styles` 아래에 작성한다.
- `frontend/src/main.ts`만 전역 CSS를 import한다.
- input, select, textarea, button, card, panel, form grid, field, modal처럼 제품 전반에서 반복되는 기본 UI는 공통 CSS와 semantic UI class를 반드시 사용한다.
- 기능 화면을 구현하기 전에 기존 공통 class로 표현할 수 있는지 확인하고, 공통 표현이 부족하면 기능별 CSS를 만들기 전에 공통 CSS를 확장한다.
- 둘 이상의 화면에서 재사용할 수 있거나 특정 업무 도메인에 종속되지 않는 스타일은 공통 CSS가 소유한다. 기능별 CSS에 같은 border, radius, spacing, typography, focus, disabled 규칙을 복제하지 않는다.
- 기능별 CSS는 YAML/terminal editor, topology, Kubernetes resource viewer처럼 해당 기능에서만 필요한 구조·상태·상호작용을 표현할 때만 사용한다.
- `div` 같은 일반 HTML 요소를 포괄적으로 꾸미지 않고 `.ui-surface-card`, `.ui-form-grid`처럼 의미가 드러나는 공통 class를 부여한다. 이 규칙은 예상하지 못한 화면까지 전역 스타일이 전파되는 것을 방지한다.
- 기능별 스타일도 `frontend/src/styles/components` 등 소유권이 드러나는 파일에 두며 Vue 파일 안에 두지 않는다.
- 동적 overlay 위치처럼 런타임 좌표가 필요한 경우에만 Vue `:style` binding을 예외적으로 허용한다.

현재 스타일 파일 역할:

- `frontend/src/styles/base.css`: root, body, typography, button 등 전역 기본값과 design token
- `frontend/src/styles/form-controls.css`: 제품 전 화면의 input/select/textarea 상태와 form/card 계열 `.ui-*` 공통 primitive
- `frontend/src/styles/main.css`: layout, table, modal, action menu, chat 등 기존 교차 도메인 재사용 UI class
- `frontend/src/styles/product-shell.css`: 제품 shell, context bar, 전역 responsive visual rule
- `frontend/src/styles/components/*.css`: 특정 기능에서만 사용하는 구조·상태·상호작용

## 공통 CSS와 기능별 CSS 판단 순서

새 UI를 구현할 때 다음 순서를 지킨다.

1. 기존 공통 class와 design token을 조합하여 구현한다.
2. 기본 control, surface, layout 또는 상태 표현이 부족하면 공통 CSS에 재사용 가능한 semantic class를 추가한다.
3. 특정 기능의 데이터 구조나 상호작용에만 필요한 경우에 한해 기능별 CSS를 추가한다.
4. 기능별 CSS에서도 색상, 간격, 테두리, focus ring을 임의 값으로 다시 정의하지 않고 공통 token을 사용한다.
5. 새 규칙이 다른 화면에서도 반복되기 시작하면 즉시 공통 CSS로 승격하고 중복 규칙을 제거한다.

예를 들어 일반 입력창의 높이, 테두리, hover/focus, disabled 상태는 `form-controls.css`가 소유한다. Values Studio의 YAML 편집기 구문 표시나 Kubernetes 콘솔의 terminal 출력처럼 일반 입력창과 다른 동작만 해당 기능 CSS가 소유한다.

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
- input, select, textarea, button, card, panel, form grid, field, 테이블, 모달, 상태 pill, action menu는 공통 class를 재사용한다.
- 새로운 UI 패턴이 생기면 Vue 파일보다 스타일 공통 파일을 먼저 확장한다.
- 기능별 CSS에는 해당 기능에만 필요한 차이만 남기고 공통 UI 기본값을 중복 작성하지 않는다.
- 일반 요소 selector 대신 역할이 드러나는 semantic class를 사용한다.
- view component가 비대해지면 API, util, composable로 분리한다.
- 화면 이동 후에도 유지되어야 하는 상태는 Pinia store로 분리한다.
- 스타일이나 재사용 규칙을 바꾸면 이 문서와 `docs/architecture/frontend-architecture.md`를 함께 갱신한다.
