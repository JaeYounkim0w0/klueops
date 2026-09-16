# Definition of Done

기능은 다음 조건을 만족해야 완료로 본다.

- Backend API 구현
- Request/Response DTO 정의
- OpenAPI annotation 작성
- OpenAPI spec 생성 확인
- Frontend generated client 갱신 필요 여부 확인
- 성공/실패 테스트 작성
- ProblemDetail 공통 에러 포맷 적용
- 권한 체크 적용
- audit log 필요 여부 확인
- Secret/PII 마스킹 확인
- 관련 docs 갱신
- Frontend UI 변경은 기존 공통 CSS와 semantic UI class를 우선 사용했는지, 기능별 CSS에 공통 control/surface/layout/state 규칙을 중복 작성하지 않았는지 확인한다.
- 새 기능별 CSS는 해당 기능 고유의 구조·상태·상호작용만 포함하며 공통 design token을 사용하고 소유권이 드러나는 파일에 배치한다.
- 사용자에게 노출되는 메뉴, 화면, 워크플로, 권한 또는 운영 절차를 변경했다면 `docs/user-guide/`의 Word 가이드와 문서 생성 스크립트를 함께 갱신한다.
- 갱신한 Word는 `render_docx.py`로 PNG 렌더링하고 모든 페이지의 잘림, 겹침, 글꼴, 용어를 시각 검수한다.
- 사용자 영향이 전혀 없는 내부 변경만 Word 갱신을 생략할 수 있으며 진행 문서에 `Word update: N/A`와 근거를 기록한다.
- `./scripts/validate-backend.sh` 또는 관련 검증 스크립트 통과
- 사용자 화면이 바뀐 기능은 로컬 Kubernetes에 반영한 뒤 자체 브라우저에서 실제 OIDC 로그인하고 대표 성공·실패·권한 흐름을 직접 수행한다. API 응답, 화면 상태, Console 오류와 관련 Pod log를 함께 확인하며 자동 테스트만으로 완료 처리하지 않는다.
- 브라우저 수용 검증에는 최소 desktop viewport, 주요 modal/confirmation, 새로고침·deep-link, 권한 거부와 장시간 Job 추적을 포함하고 실행한 계정·환경·결과를 문서화한다. Secret과 비밀번호는 증빙에 기록하지 않는다.
- 성능 민감 경로는 pagination/query count/payload bound, 외부 호출 timeout·동시성 상한과 N+1 방지 여부를 검토하고 관련 테스트를 통과한다.

AI 기능은 다음 조건을 추가로 만족해야 한다.

- provider 독립 port 사용
- prompt template version 관리
- `analysis-result.v1` schema validation
- Secret 값 prompt 포함 금지
- AI 실패 fallback 처리
- fixture 기반 분석 테스트 작성
- 대형 LLM context 단일 호출 금지. Kubernetes context가 커질 수 있는 기능은 관심사별 section context로 분할 호출한다.
- section 단위 timeout, invalid JSON, missing field가 전체 분석 실패로 전파되지 않도록 Kubernetes API 기반 fallback을 제공한다.
- section 재사용은 cluster/application/namespace scope와 정규화한 context fingerprint가 모두 일치할 때만 허용하고 UI에서 cache hit를 표시한다.
- Validation fixture는 실제 운영 클러스터를 변경하지 않는 안전 모드에서 반복 실행할 수 있어야 하며 분류, 근거, read-only 명령과 mutation guard를 함께 검증한다.
- Live Validation은 기본 비활성화, non-production guard, RBAC preflight, exact confirmation, 관리 label, TTL cleanup, 중복 실행 차단과 audit를 검증한다.
- 조치 학습은 완료된 closed-loop 표본만 사용하고 표본 수와 confidence를 UI에 함께 표시한다.
- 운영 신뢰도 지표는 evidence type을 표시하며 외부 metrics가 없을 때 utilization을 추정하지 않는다.
- LLM 호출 규칙은 `docs/development/coding-standards.md`와 `docs/architecture/ai-analysis-architecture.md`를 따른다.
