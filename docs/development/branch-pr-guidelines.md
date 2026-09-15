# Branch and PR Guidelines

## Branch

- 기능 단위로 작은 branch를 만든다.
- API, 테스트, 문서 변경을 같은 PR에서 함께 반영한다.
- 하나의 통합 기능으로 승인된 장기 개발은 단일 feature branch를 사용할 수 있다. 이 경우 단계별 검증 가능한 commit을 유지하고, 완료 전 최신 `main` 반영과 전체 quality/browser acceptance gate를 다시 수행한 뒤 한 번의 PR로 병합한다.

## PR Checklist

- 변경 요약
- API 변경 여부
- OpenAPI 갱신 여부
- Frontend generated client 갱신 여부
- 테스트 결과
- 보안/마스킹 영향
- 운영 영향
- 관련 docs 갱신 여부
