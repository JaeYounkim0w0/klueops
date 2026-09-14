# Documentation Standards

## 목적

문서는 구현과 다른 별도 기록물이 아니라 제품 계약의 일부다. 현재 사실을 빠르게 찾을 수 있게 유지하고, 완료된 요구사항이나 날짜별 진행 기록이 기준 문서와 경쟁하지 않게 한다.

## 단일 기준

- 현재 제품 범위: `docs/product/current-product-specification.md`
- 잔여 개발 및 환경 승인: `docs/product/remaining-development-items.md`
- 사용자 절차: `docs/user-guide/K8s-AI-Ops-Platform-menu-guide.docx`
- 설치·운영·복구: `docs/operations/`
- 기술 구조와 결정: `docs/architecture/`, `docs/adr/`
- API 계약: 실행 중 Backend의 OpenAPI와 `docs/api/`

같은 내용을 여러 PRD, roadmap, status 문서에 복제하지 않는다.

## 변경 유형별 필수 갱신

| 변경 | 필수 문서 |
| --- | --- |
| 사용자 기능 추가·변경 | 현재 제품 명세, Word 사용자 가이드, 관련 운영 문서 |
| API 추가·변경 | OpenAPI annotation, API 규칙/예시, 생성 client |
| 배포·설정 변경 | architecture deployment, operations 설치/배포 문서와 values 예시 |
| 인증·권한·Secret 변경 | security 문서, threat/validation 항목, 운영 가이드 |
| 미완료 기능 또는 수용 시험 | 잔여 개발 문서의 우선순위·완료 기준 |
| 되돌리기 어려운 기술 결정 | ADR |
| 내부 refactor | 공개 동작이 같으면 제품/Word 문서 갱신 불필요, 구조 문서만 필요 시 갱신 |

## 기능 설계 문서

큰 기능은 구현 전에 `docs/features/<feature>/` 아래에 설계할 수 있다. 문서는 요구사항을 그대로 복사하지 말고 사용자 흐름, 책임 경계, API/data, 보안, 성능과 검증 기준을 담는다.

구현 완료 후에는 다음을 수행한다.

1. 확정된 동작을 현재 제품 명세에 병합한다.
2. 미완료 항목만 잔여 개발 문서로 이동한다.
3. 더 이상 독립적으로 필요하지 않은 요구사항, 화면 초안과 구현 계획은 삭제한다.
4. 유지할 feature 문서는 현재 architecture/API/security 계약만 남긴다.

## Word 사용자 가이드

- 메뉴, 버튼, 화면 흐름, 사용자 권한 또는 오류 처리 UX가 바뀌면 반드시 갱신한다.
- 생성 원본과 `.docx`를 함께 관리한다.
- 문서 skill의 render-and-verify 절차로 페이지 잘림, 도형 겹침, 표 overflow와 글꼴을 확인한다.
- 내부 구조 변경처럼 사용 절차가 동일하면 갱신하지 않아도 되며 변경 기록에 그 이유를 남긴다.

## 작성 품질

- 구현 완료, 로컬 검증, 고객 환경 승인과 계획을 같은 표현으로 섞지 않는다.
- 실제 코드/API/script로 확인되지 않은 수치나 상태를 완료로 기록하지 않는다.
- Secret, token, password, kubeconfig와 개인 식별 정보는 예시에도 실값을 넣지 않는다.
- 명령은 실행 위치, 필수 환경 변수, 성공 기준과 실패 시 확인 위치를 함께 적는다.
- 링크는 저장소 내부의 canonical 문서를 향하게 하고 삭제된 이력 문서를 참조하지 않는다.
- 제목·용어는 제품 전체 관점으로 작성하며 화면 위치만으로 기능을 설명하지 않는다.

## 검증

문서 변경 후 다음을 실행한다.

```bash
./scripts/validate-docs.sh
```

사용자 가이드가 변경됐으면 생성 script 실행, DOCX render와 시각 검증까지 완료해야 한다. 문서 검증 실패는 Definition of Done 미충족이다.
