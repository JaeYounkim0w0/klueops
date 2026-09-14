# Cluster Readiness, AI Trust and Incident Export

기준일: 2026-09-03

## 운영 준비도

`Clusters > 상세 > 운영 준비도`에서 점검을 시작한다. 최초 진입에는 Kubernetes API를 호출하지 않으며 사용자가 점검을 실행할 때만 live evidence를 수집한다. 결과는 5분간 재사용되고 `다시 점검`은 캐시를 우회한다.

- `기능 권한`: SelfSubjectAccessReview 기반 실제 읽기/로그/변경 권한이다. `UNKNOWN`은 허용이 아니라 판정 불가다.
- `자격증명`: API 연결, token과 client/CA certificate 만료, 저장 기간을 확인한다. 원문 credential은 응답이나 검사 결과에 포함하지 않는다.
- `업그레이드`: 현재/목표 Kubernetes minor, node skew, deprecated API, CRD storedVersions/conditions를 확인한다. Git manifest를 보지 못한 항목은 잠재 위험으로 취급한다.

운영자는 `CRITICAL/BLOCKED`를 먼저 해소하고 `UNKNOWN`은 네트워크, API discovery 또는 권한을 보완한 뒤 재검사한다.

## AI Trust Center

`AI > AI 신뢰 센터`는 서버가 같은 시점에 구성한 snapshot을 표시한다. 모델 자체 confidence 대신 운영자 feedback, ground truth, benchmark, 50개 deterministic regression과 release gate를 근거로 상태를 계산한다.

- `TRUSTED`: 최신 근거가 기준을 만족한다.
- `NEEDS_EVIDENCE`: 실패가 확정된 것은 아니지만 benchmark나 ground truth가 부족하다.
- `BLOCKED`: regression/release gate 실패 또는 위험 명령 제안이 존재한다.

로그와 이벤트의 프롬프트 주입 문장은 비신뢰 입력이다. LLM 텍스트는 명령으로 직접 실행하지 않으며 deterministic command policy를 항상 통과해야 한다.

## Incident 보고서

Incident 상세 상단에서 Markdown, JSON 또는 Evidence ZIP을 선택한다. ZIP은 `report.md`, `report.json`, `commands.csv`, `manifest.sha256`을 포함하고 응답의 `X-Content-SHA256`으로 전체 파일을 검증할 수 있다.

보고서는 스트리밍으로 생성되며 credential, token, password와 bearer 값은 공통 마스킹 정책을 통과한다. 내보내기 행위는 `INCIDENT_REPORT_EXPORTED` 감사 이벤트로 기록된다.

## 운영 주의점

- Readiness의 live 검사는 등록 클러스터 API 지연의 영향을 받으므로 동시성 제한 executor를 사용한다.
- Production egress는 등록된 원격 Kubernetes API와 DNS/OIDC/PostgreSQL 목적지를 포함해야 한다. 고객별 CIDR이 동적이므로 기본 chart가 임의의 고정 CIDR을 강제하지 않는다.
- compatibility catalog는 `kubernetes-removals-2026.09` 버전이며 Kubernetes 릴리스 검토 시 갱신한다.
