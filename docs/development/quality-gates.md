# Quality Gates

PR merge 전 최소 조건:

- backend test 통과
- frontend test 통과
- frontend production build 통과
- OpenAPI diff 확인
- generated API client 최신 상태
- ArchUnit rule 통과
- Secret masking test 통과
- AI schema validation test 통과
- 주요 API pagination/filter/sort 동작 확인
- 관련 docs 갱신
- 핵심 API가 실제 `/v3/api-docs` contract test를 통과
- production readiness가 `BLOCKED`이면 배포 승격 금지
