# Product Readiness 실행 가이드

이 문서는 오픈소스 저장소의 통합 품질과 로컬 재현성을 확인하기 위해 1~4 큰 업무 묶음을 한 번에 점검하는 절차다. 특정 고객 환경의 상용 릴리스 또는 production 인증을 의미하지 않는다.

## 실행

저장소 루트에서 실행한다.

```bash
./scripts/acceptance/run-product-readiness.sh
```

기본 URL은 로컬 Helm 배포의 Portal `http://127.0.0.1:30081`이다. Backend readiness는 같은 진입점이 제한적으로 공개하는 `/actuator/health/readiness`를 사용한다. 다른 환경에서는 `AIOPS_FRONTEND_URL`, `AIOPS_BACKEND_URL`을 명시한다.

결과는 `artifacts/product-readiness/` 아래 JSON과 실행 로그로 생성된다. 민감정보를 로그에 남기지 않으며, Backend 테스트·Kubernetes read·실환경 AI 수용 증빙처럼 외부 조건이 필요한 항목은 조건이 없으면 `BLOCKED` 또는 `CONDITIONAL`로 남는다.

로컬 synthetic A-1~A-6 결과를 명시적으로 포함하려면 생성된 보고서 경로를 전달한다. 이 실행은 로컬 통합 품질 판정이며 외부 환경의 운영 보증을 의미하지 않는다.

```bash
AIOPS_AI_E2E_REPORT=artifacts/acceptance/ai-analysis-e2e-<runId>.json \
  ./scripts/acceptance/run-product-readiness.sh
```

## 네 업무 묶음

| 묶음 | 확인 내용 | 통과 의미 |
| --- | --- | --- |
| CORE | Backend/Frontend 응답, 설치된 Kubernetes API 읽기 | 운영 흐름을 시작할 실행환경이 준비됨 |
| COMMERCIAL | all-in-one packaging, security 계약 | 기존 결과 schema의 그룹명이며 배포 산출물의 기본 안전 계약이 유효함 |
| QUALITY | Frontend/Backend 테스트, AI A-1~A-6 증빙 | 품질과 실환경 수용 결과가 확인됨 |
| DOCS | 문서 구조, 배포·검증 절차, 사용자 가이드 | 사용자와 기여자용 자료가 준비됨 |

## 판정 원칙

- 하나라도 `BLOCKED`, `CONDITIONAL`, `FAILED`이면 전체 실행은 종료 코드 6으로 종료한다.
- 환경 미비를 코드 성공으로 대체하지 않는다.
- A-3/A-4의 restart, scale, rollback은 이 스크립트가 실행하지 않는다. 운영자가 preview와 확인 문구를 검토한 뒤 테스트 전용 리소스에서 별도로 수행한다.
- 전체 통과는 저장소와 로컬 예제 환경의 품질을 뜻한다. TLS/DNS/IdP, 데이터 복구와 HA 등 외부 환경 검증은 배포자가 별도로 판단하며 이 프로젝트의 공개 여부를 차단하지 않는다.

## 장애 처리

1. JSON에서 `group`, `code`, `status`를 확인한다.
2. 같은 실행의 `logs/<runId>/<code>.log`를 확인한다.
3. Docker/Testcontainers, preview port, Kubernetes context, OIDC/Secret, 실제 클러스터 증빙 조건을 해결한다.
4. 같은 명령을 재실행하고 이전 실행 결과를 통과로 대체하지 않는다.
