# 프론트엔드·UI 설계 기준

## 책임 배치

| 위치 | 역할 |
| --- | --- |
| `views` | route 화면 조립과 화면에 한정된 상태 |
| `components` | 재사용 UI, typed props/events |
| `api` | 생성 client, 공통 transport와 DTO 변환 |
| `utils` | Vue lifecycle/reactivity 없는 순수 함수 |
| `composables` | Composition API 기반 재사용 로직 |
| `stores` | 화면 간 유지할 Pinia 상태, 전역 Job 추적 |
| `styles` | 공통 token·UI primitive·기능별 CSS |

화면에 API 호출·파싱·포맷팅·상태 orchestration을 모두 쌓지 않는다. 공유하지 않는 상태까지 전역 store에 올리지 않는다. 생성 client는 수정하지 않고 timeout·오류·locale 등은 공통 transport/adapter에서 처리한다.

## 디자인 시스템과 CSS

이 참조 스택은 PrimeVue와 공통 CSS를 사용한다. 새 프로젝트에서 다른 방식으로 바꾸려면 프로필에 결정과 검증 방식을 기록한다.

- Vue SFC의 `<style>`/`<style scoped>`와 정적 inline style을 사용하지 않는다.
- CSS는 `src/styles`에서 관리하고 전역 import는 `src/main.ts`로 모은다.
- 색상·간격·글꼴·radius·focus·disabled 표현은 공통 token과 semantic class를 사용한다.
- control, surface, form, modal, 상태 표시는 공통 primitive가 소유한다. 기능 CSS에는 해당 기능 고유의 구조·상호작용만 둔다.
- 기존 공통 class 조합 → 공통 primitive 확장 → 기능 CSS 추가 순으로 검토한다. 반복되는 기능 스타일은 공통으로 승격한다.
- 일반 HTML 요소를 광범위하게 꾸미는 selector를 추가하지 않는다. 기능 selector에는 소유권이 드러나는 이름을 사용한다.
- 런타임 overlay 좌표처럼 계산된 배치에 한해 동적 `:style`을 허용한다.

## 운영 화면 UX

- loading, empty, error, success 상태와 재시도 경로를 설계한다. 오래 걸리는 작업은 진행 상태·취소 가능 여부를 드러낸다.
- 화면 상단에는 대표 액션, 반복 행 작업은 Operations/kebab 메뉴를 사용한다. overlay가 table/scroll 영역에 잘리지 않게 한다.
- 위험한 변경은 결과·범위를 설명하고 확인 흐름을 제공한다. 확인 dialog는 서버 권한 검증을 대신하지 않는다.
- 긴 로그는 접기·virtual scroll·크기 상한을 적용한다. 장시간 Job은 화면 이동 뒤에도 전역 상태에서 추적한다.
- 입력량이 많은 작업은 modal/drawer를 우선 검토하되, 독립 URL과 복잡한 탐색이 필요한 흐름은 별도 화면을 사용한다.
- tab/filter와 deep-link를 제공하고 새로고침·뒤로 가기·권한 만료를 처리한다.
- label, 키보드 조작, focus 이동/복귀, 대비와 좁은 화면 overflow를 검증한다. 색상만으로 상태를 전달하지 않는다.
- 다국어 적용 시 UI 메시지는 locale key로 관리한다. API schema key, 명령, resource 식별자와 로그 원문은 번역하지 않는다.

스타일 검증·타입 검사만으로 UI 완료를 선언하지 않는다. 주요 화면 변경은 실제 브라우저에서 성공·실패·빈 상태와 responsive 동작을 확인한다.
