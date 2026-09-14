# Development Guide

이 폴더는 구현에 반복 적용하는 개발 규칙만 관리한다.

- `tech-stack.md`: 지원 runtime과 주요 library
- `coding-standards.md`: Backend/Frontend 코드 작성 규칙
- `frontend-style-guide.md`: 공통 CSS와 UI 구현 기준
- `localization.md`: 한국어/영어 locale 계약
- `testing-strategy.md`: 테스트 계층과 환경 수용 기준
- `backend-source-validation.md`: Backend 자동 검증 범위
- `quality-gates.md`: merge/release 품질 gate
- `definition-of-done.md`: 기능 완료 조건
- `documentation-standards.md`: 문서 통합과 Word 가이드 갱신 규칙
- `local-development.md`: 로컬 실행 절차
- `branch-pr-guidelines.md`: 변경·리뷰 절차

AI Analysis 실환경 수용 fixture와 보고서 template은 실행 가능한 검증 자산이므로 이 폴더에 유지한다.

Frontend CSS 실제 소스는 `frontend/src/styles/`에서 관리한다. Vue SFC의 정적 inline style과 중복 `<style>`을 만들지 않고 공통 stylesheet와 component를 재사용한다.
