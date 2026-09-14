# API Guidelines

- RESTful path를 사용한다.
- 목록 API는 pagination을 지원한다.
- 필요한 목록 API는 filter/sort를 지원한다.
- mutation API는 audit log를 남긴다.
- 장시간 작업 API는 `jobId`를 반환한다.
- 모든 요청은 requestId/correlationId를 가진다.

