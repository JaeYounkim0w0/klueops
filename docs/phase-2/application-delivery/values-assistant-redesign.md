# Custom Values 도우미 고도화

상태: 2026-09-18 사용자 요청에 따라 기본 검증 범위로 완료. 고복잡도 자연어 요청의 생성 정확도·토큰 최적화·확장 인수 검증은 추후 개선사항으로 분리한다. 모든 복합 요청의 정상 생성을 보증하는 완료 판정은 아니다.

## 원칙

- 자연어 입력을 유지한다. 사용자가 YAML 필드 경로를 외워 입력하도록 요구하지 않는다.
- 시스템 프롬프트 `helm-values.grounded.v2`는 공통 생성·보안·응답 계약만 정의한다. Chart 이름, 제공사, Chart/App 버전, Values, Schema, 요청은 런타임 데이터이다.
- `service.type`, `server.service.type` 등은 archive의 실제 경로 근거로 판단한다. 제품별 key 이동, NodePort 경로 추정·자동 수정 분기는 사용하지 않는다.
- 고정된 해석/매핑 2단계 호출과 v11 생성 경로를 단일 엔진으로 통합했다. 구형 동기 API도 같은 엔진에 위임한다.

## Chart 편입

Root에 읽을 수 있는 비어 있지 않은 mapping 형식 `values.yaml`이 있어야 한다. 누락, 빈 mapping, 중복 key, 복수 YAML 문서는 편입 전에 거부한다. `values.schema.json`은 선택 사항이다. 이는 Helm 전체의 필수 규칙이 아니라 KlueOps의 지원 정책이다. 기존 Library 자료는 자동 삭제하지 않고 도우미에서 부적합 사유를 안내한다.

정확한 immutable archive의 Values 원문과 주석을 보존한다. 압축·해제 크기, 파일 수, dependency 깊이를 제한하며 하위 Chart와 alias의 Values도 계약에 포함한다. 부모 override는 하위 Chart 기본값보다 우선한다.

루트 Chart와 달리 하위 library Chart의 주석뿐인 빈 Values는 허용한다. 설정이 없는 공통 template dependency 때문에 정상적인 Application Chart를 거부하지 않는다.

## 생성 흐름

1. Tenant 권한과 immutable Chart 좌표를 확인한다. 공백 Custom Values는 `{}`로 취급한다.
2. Values 원문·Schema·Chart dependency·정적 템플릿 경로를 주소가 있는 참조로 구성한다. 큰 파일은 줄 경계로 나누고 파일명, 줄 위치, 상위 mapping 문맥과 key 색인을 제공한다. 각 원문 조각에 YAML 구문 트리에서 추출한 루트 기준 JSON Pointer를 함께 제공하므로 중간에서 들여쓰기가 복귀하는 경우도 경로를 추측하지 않는다. dependency 경로는 alias를 포함한다. 템플릿에만 있는 선택형 경로도 명시한다.
3. 모델은 원문 요청을 변경/유지 요구사항으로 나누고 Chart Values 경로·값·이유를 반환한다. 자료가 부족하면 색인의 다른 조각을 요청한다. 특정 기능 영역을 지원 범위에서 임의로 제외하지 않는다.
4. 모호한 범위, Chart 미지원 기능, 인증 참조 부족은 구체적인 한국어 보충 질문으로 반환한다. 기술적 실패와 입력 보완을 구분한다.
5. 서버가 기존 Values에 검증된 변경만 합성한다. 무관한 설정과 민감값을 보존하고 parent/child 충돌과 미확인 경로를 차단한다. 배열 변경은 전체 배열이므로 결과 차이를 검토한다.
6. YAML 구조 → Chart 경로 → `helm lint` → `helm template`을 검사한다. Custom Values 단계에서는 Cluster 리소스의 준비 상태·권한·스케줄링·PVC·endpoint를 검증하지 않는다. 최대 2회 교정하며 추가 참조 조회를 포함한 전체 모델 호출은 최대 5회이다.
7. 이해한 요청, 경로 매핑, 기존/제안 비교, YAML과 검증 한계를 표시한다. 사용자가 적용한 뒤 Revision을 저장한다. 생성만으로 저장·배포하지 않는다.

## 결과와 검증 범위

- `HELM_TEMPLATE_VALIDATED`: Values YAML, Chart 경로, `helm lint`와 `helm template` 검증을 통과했다. 모든 자연어 요구가 완전히 충족됐거나 실제 Cluster 배포가 성공했다는 보증은 아니다.
- `NEEDS_INPUT`: 질문에 답해 요청을 보완한다. 현재 Values는 유지하며 적용은 불가능하다.
- `GENERATION_FAILED`: 모델 응답·자료·검증을 완료하지 못했다. 부분 YAML을 적용하지 않는다.

매핑 실패 시 Chart에 없는 Values 경로를 임의로 보정하지 않는다. `Chart에서 확인되지 않은 설정 경로`와 함께 실제로 거부된 JSON Pointer 경로를 결과 경고에 표시하여 사용자가 해당 Chart의 `values.yaml`/Schema에서 지원 필드를 확인할 수 있게 한다. 지원하지 않는 NodePort·PVC 정책 등은 배포 조건으로 추측해 추가하지 않고, 요청을 Chart가 제공하는 정확한 필드로 수정하도록 안내한다.
- Values 경로를 확인할 수 없거나 Chart Schema·렌더링과 충돌하는 요청은 보충 질문 또는 실패로 반환한다. 사용자는 적용 전 변경 diff를 검토해야 한다.
- 실제 Cluster admission, RBAC, 스케줄링, PVC, 포트 충돌, 준비 상태와 접속은 Target/Preview/배포 후 별도 검사한다. 최종 배포 Job이 이 결과를 기록하고 실패 원인을 사용자에게 표시한다.

## 검증 책임 경계

Custom Values 도우미의 책임은 Chart에 맞는 Values를 생성·수정하고 정적 Helm 검증을 통과시키는 것까지다. Kubernetes API에 적용한 뒤에만 알 수 있는 권한, admission, 스케줄링, PVC 바인딩, Pod readiness, Service/Ingress/HTTPRoute 접근성은 배포 Job의 책임이다. 따라서 Values 제안이 성공해도 배포가 실패할 수 있으며, 사용자는 배포 Job의 terminal 결과와 상세 이벤트를 기준으로 조치한다.

## 보안·비동기 실행

- credential은 전송 전에 차단/마스킹한다. 임의 인증 환경변수를 만들지 않고 지원되는 인증 방식과 기존 Secret 참조를 요청한다.
- Chart 설명도 신뢰하지 않는 데이터이다. 기존 민감값은 모델에 보내지 않고 최종 합성 시 복원한다. 환경변수 배열의 Secret은 이름 기준으로 복원한다.
- 기본 worker 2개·대기열 8개를 사용하며 외부 AI/Helm 대기 동안 DB transaction을 유지하지 않는다.
- 로컬 모델의 긴 추론을 고려해 Values AI 요청은 기본 10분 제한(환경변수 `AIOPS_AI_HELM_VALUES_TIMEOUT_MS`)을 사용하며, 서버에서 최대 10분으로 상한을 둔다. 일반 AI 분석·채팅의 제한시간은 별도로 유지한다.
- Job ID로 결과를 복원하고 Tenant·사용자 소유권·Values 편집 권한을 재확인한다. 원본 Values digest가 달라지면 적용을 차단한다.
- 요청·결과는 암호화하고 완료 후 요청을 제거한다. 결과는 7일 후 정리한다. 취소/timeout 후 늦은 응답은 성공으로 덮어쓰지 않는다.
- 로그에는 단계, 호출 수, 오류 유형, 코드 위치와 지연만 기록한다. 프롬프트·Values·credential·예외 원문은 남기지 않는다.
- Ollama Values 호출은 공통 응답 JSON Schema와 `think: false`를 사용한다. NDJSON을 읽다가 첫 완전한 JSON object를 받으면 구독을 취소하고 바로 Chart 검증으로 넘어간다. 뒤따르는 불필요한 출력 때문에 전체 토큰 예산을 소모하지 않는다. 부분 JSON·중복 key·timeout은 성공으로 취급하지 않는다.
- 생성 검증 실패 결과도 암호화해 조회할 수 있으나 Job Center 상태는 `FAILED`로 기록한다. 보충 질문은 처리 완료 결과로 구분한다.

## 자원 상한과 알려진 범위

- 요청 2,000자, 요구사항 30개, 변경/확인 조건 각각 80개, 모델 응답 48,000자이다.
- 참조 페이지 6,000자, 추가 조회당 최대 3개이다. 작은 원문은 초기 2개 페이지로 전달한다. 원문이 크지만 주석을 제외한 루트 구조가 18,000자 이하이면 **민감정보 보호 후 전체 루트 key·값·타입을 보존한 JSON**을 초기 근거로 제공하고 원문 주석은 추가 조회로 제공한다. 민감 subtree는 보호 표시로 대체한다. 그보다 큰 자료는 주소 기반 조회를 사용한다. 현재 Values·색인·참조·피드백 합계 42,000자와 Provider context 한도를 적용한다. 상한 초과는 명시적으로 실패한다.
- 정적 경로 추출은 모든 Helm 동적 표현식의 의미를 해석하지 않는다. Schema 없는 Chart는 타입 검증에 제한이 있다.
- 기본값이 빈 object이거나 Schema가 명시적으로 열린 map인 경로는 사용자 정의 하위 key를 허용한다. 최종 lint/렌더는 필수다.
- 임의 크기의 Chart·요청이나 모든 모델의 생성 성공을 보장하지 않는다. 실패나 보충 질문을 성공으로 표시하지 않는다.

## 검증 기록

자동 회귀는 원문/주석/민감값, 빈 override, Chart identity, Tenant 격리, 추가 조회, 잘못된 경로·렌더 결과 차단, bounded 교정, 기존 값 보존 및 화면 diff를 검사한다.

실제 모델은 로컬 `gemma4:e4b`로 기본 검증했다. 최초 실행에서 검증 조건의 리소스 이름/경로 오류가 확인되어 실제 렌더 식별자·경로 피드백을 추가했다.

- 브라우저 단순 요청(2026-09-18, Job `4e16fd7e`): NodePort 30001, 내부 포트/나머지 유지. 1회 호출·44초, `server.service.type: NodePort`, `server.service.nodePort: 30001` 생성 및 lint/렌더 검증 통과. 자동 저장/배포하지 않았다.
- PostgreSQL 0.20.5 / cloudpirates-postgres / App 18.6.0 (Job `3e1f3ff1`): 기존 NodePort 30432를 30433으로 변경, 내부 포트 5432 유지, 나머지 기존 설정 유지. 브라우저에서 1회 호출·26초로 lint/렌더 검증과 변경 비교 및 편집기 적용까지 통과했다. 기존 persistence/resources는 유지됐고 Revision 저장·배포는 하지 않았다. 하위 library의 빈 Values와 민감 mapping 뒤 개행 처리 결함을 이 교차 검증 과정에서 수정했다.
- 복합 요청(Job `0ee0f49c`): 단일 서버, 부속 component 비활성화, NodePort, PVC, 보존기간, CPU/메모리 조건. 경로 매핑 교정 후 모델 호출 실패로 종료했으며 성공으로 처리하지 않았다. 추가 검증 Job `5d47ca22`는 사용자의 검증 범위 축소 요청에 따라 중단했다. 복합 생성 안정화는 후속 개선으로 이관한다.
- 동일 복합 조건의 수동 Values는 공개 Prometheus 29.30.0 archive로 `helm lint/template` 및 렌더된 workload 1개·replica 1·NodePort 30001·Service 80·PVC 5Gi·보존기간 7d·CPU/메모리 requests/limits 검사를 통과했다. 생성 정확도와 Chart 지원 여부를 구분하여 판정한다.
- Ollama의 실제 추론 동시성에 따라 대기 시간이 Provider 응답 제한에 포함될 수 있다. 애플리케이션 worker 수가 모델의 병렬 처리 능력을 보장하지 않는다.

최신 자동 회귀: Backend 356개, Frontend 135개 통과. Frontend 빌드와 생성 API client 일치 검사 통과. 이는 로컬 모델의 모든 복합 요구사항 생성 성공을 의미하지 않는다.

Provider 계약 참고: [Ollama 구조화 출력](https://docs.ollama.com/capabilities/structured-outputs), [Ollama thinking 제어](https://docs.ollama.com/capabilities/thinking). JSON Schema는 응답 형식을 보조하며 내용의 정확성을 보증하지 않는다.
