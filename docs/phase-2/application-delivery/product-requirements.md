# Phase 2 Application Delivery Product Requirements

기준일: 2026-09-14  
상태: 설계 완료, 구현 미착수

## 1. 제품 정의

Application Delivery는 Tenant 사용자가 Helm Chart를 검색하거나 직접 등록하고, 원본 Chart를 변경하지 않은 채 Custom Values를 작성해 권한이 있는 Kubernetes Cluster와 Namespace에 배포하는 기능이다.

```text
Chart 검색/등록 → Tenant Library → Custom Values → Preview → 승인 → Helm 배포 → Release 운영
```

Application은 이 기능에서 하나의 Helm Release를 의미한다. 기존 Kubernetes workload 자동 발견, Argo CD/Flux 연동, GitOps Controller 제공은 포함하지 않는다.

## 2. 목표

- Artifact Hub에서 Helm package를 검색하고 버전, publisher, 문서와 보안 metadata를 비교한다.
- Artifact Hub가 가리키는 원본 Helm repository 또는 OCI registry에서 정확한 Chart version을 가져온다.
- `.tgz`, Helm repository와 OCI reference를 Tenant별 Chart Library에 등록한다.
- 원본 Chart artifact는 immutable SHA-256으로 보존하고 Custom은 versioned Values Profile만 지원한다.
- `values.schema.json`이 있으면 beginner-friendly form을 제공하고 YAML editor와 양방향 동기화한다.
- LLM이 사용자 요구와 sanitized Cluster capability를 근거로 Values patch를 제안한다.
- Chart, Values, 생성 manifest, Cluster scope와 RBAC를 결정론적으로 검증한다.
- preview, exact confirmation, async job, audit와 사후 health 검증을 거쳐 Helm install/upgrade/rollback/uninstall을 수행한다.
- Application Delivery를 사용하지 않는 설치에서는 Helm Runner와 background work를 비활성화한다.

## 3. 비목표

- Chart template, helper, dependency 파일의 브라우저 편집
- 원본 Chart의 in-place 수정 또는 KlueOps에서 Chart 재패키징
- Argo CD, Flux 또는 자체 GitOps reconciliation
- Artifact Hub나 외부 registry로 Chart publish
- Helm plugin 또는 임의 shell command 실행
- `latest` 같은 mutable version을 이용한 자동 배포
- 승인 없는 자동 upgrade와 여러 Cluster 일괄 배포
- PostgreSQL, object storage 등 application data의 backup/restore 자동화

사용자가 template을 수정한 Chart가 필요하면 외부 개발 도구에서 새 `.tgz` version을 만들어 Tenant Library에 업로드한다.

## 4. 주요 사용자

| 사용자 | 주요 작업 |
| --- | --- |
| Platform Admin | 기능 활성화, Runner/저장 한도, 외부 AI Provider와 전역 정책 관리 |
| Tenant Admin | Tenant Chart source/credential, 허용 Cluster와 AI profile 관리 |
| Application Operator | Chart 검색/import, Values Profile 작성, preview와 배포 수행 |
| Viewer | Chart, Values diff, Release 상태와 Audit 조회 |

## 5. 정보 구조

```text
Applications
├─ Discover
│  ├─ Artifact Hub 검색
│  └─ Repository/OCI/Upload 가져오기
├─ Chart Library
│  ├─ Tenant Chart
│  ├─ Immutable Chart Version
│  └─ Values Profile
└─ Releases
   ├─ Install/Upgrade
   ├─ Status/History
   ├─ Rollback
   └─ Uninstall
```

## 6. 핵심 사용자 흐름

### 6.1 Artifact Hub에서 가져오기

1. 사용자는 이름, category, repository, official/verified 상태로 Helm package를 검색한다.
2. 상세 화면에서 README, available versions, default values, values schema와 security report를 검토한다.
3. 정확한 version을 선택하고 원본 repository/OCI URL을 확인한다.
4. KlueOps가 server-side로 Chart를 내려받아 size/path/symlink, metadata, digest와 provenance를 검사한다.
5. 사용자는 결과를 확인한 후 현재 Tenant Library에 import한다.

Artifact Hub 공개 API는 package search, Helm package/version 상세, values, values schema, templates와 security report endpoint를 제공한다. Artifact Hub 자체는 application을 설치하지 않으므로 KlueOps가 원본 source에서 Chart를 획득하고 검증·배포한다.

- <https://artifacthub.io/docs/api/>
- <https://artifacthub.io/docs/topics/faq/>

### 6.2 직접 가져오기

- `.tgz` upload
- HTTPS Helm repository와 chart/version
- OCI `oci://registry/namespace/chart`와 SemVer version

Browser가 임의 URL을 직접 fetch하지 않는다. Backend가 허용 scheme, DNS/IP, redirect, TLS와 size를 검사해 SSRF를 차단한다. Private source credential은 Tenant scope로 암호화하며 저장 후 다시 표시하지 않는다.

### 6.3 Custom Values

1. immutable Chart version을 선택한다.
2. 새 Values Profile을 생성하거나 기존 revision을 복제한다.
3. Schema Form, YAML Editor 또는 AI Assistant로 값을 수정한다.
4. schema/type/unknown key와 Secret pattern을 검사한다.
5. 저장 시 전체 values, parent revision, author, SHA-256과 redacted diff를 기록한다.

### 6.4 배포

1. Chart version과 Values Profile revision을 고정한다.
2. Tenant에 속한 Cluster와 허용 Namespace를 선택한다.
3. render, policy, live diff와 RBAC preflight를 실행한다.
4. 생성·변경·삭제 resource, cluster-scope, hook와 위험 설정을 표시한다.
5. exact confirmation 후 async Helm job을 시작한다.
6. Job Dock에서 진행을 추적하고 성공 후 Release/Pod health를 검증한다.

### 6.5 Release 운영

- Status/manifest/values 조회
- Chart 또는 Values revision upgrade preview
- Helm history와 revision rollback
- uninstall preview와 exact confirmation
- 실행 전후 resource snapshot, output hash와 Audit
- Application/Namespace AI Analysis로 이동

## 7. Custom Values와 AI Assistant

AI는 배포자가 아니라 Values 제안자다. 출력은 다음 provider-neutral 계약만 사용한다.

```json
{
  "schemaVersion": "helm-values-suggestion.v1",
  "summary": "요청을 반영한 변경 설명",
  "patch": {
    "replicaCount": 3,
    "service": { "type": "ClusterIP" }
  },
  "assumptions": [],
  "warnings": [],
  "evidence": ["values.schema.json#/properties/replicaCount"]
}
```

- Chart README, comments와 templates는 신뢰하지 않는 data이며 system instruction이 아니다.
- Secret value, Kubernetes credential, ConfigMap 원문과 인증서는 prompt에 포함하지 않는다.
- LLM patch는 허용된 Values key에만 merge하고 전체 파일을 임의 교체하지 않는다.
- schema validation과 `helm template`이 실패하면 제안을 적용하거나 배포하지 않는다.
- 사용자가 diff를 승인하기 전에는 Values Profile revision을 만들지 않는다.
- Provider failure 시 수동 Form/YAML 편집은 계속 사용할 수 있어야 한다.

## 8. Chart 신뢰 등급

| 상태 | 의미 |
| --- | --- |
| VERIFIED | provenance/signature와 package digest 검증 성공 |
| CHECKSUMMED | SHA-256은 고정됐으나 publisher signature 없음 |
| UNVERIFIED | source/digest 검증을 완료하지 못해 배포 차단 또는 관리자 예외 필요 |
| REJECTED | 구조, size, metadata 또는 정책 검사 실패 |

Artifact Hub의 official/verified publisher 표시는 검색 판단 근거이지 KlueOps package signature 검증을 대체하지 않는다. Helm provenance가 제공되면 `helm pull --verify`와 keyring 정책으로 검증한다.

- <https://helm.sh/docs/helm/helm_pull/>
- <https://helm.sh/docs/topics/provenance/>

## 9. 권한

| Capability | 동작 |
| --- | --- |
| `chart:read` | Discover와 Tenant Library 조회 |
| `chart:import` | 외부 Chart import와 `.tgz` upload |
| `chart:manage` | source/credential, archive와 retention 관리 |
| `values:edit` | Values Profile 생성·revision 저장·AI 제안 |
| `application:read` | Release와 history 조회 |
| `application:deploy` | install/upgrade와 preview |
| `application:rollback` | Helm revision rollback |
| `application:delete` | uninstall과 Library artifact 삭제 |
| `ai-provider:manage` | Provider profile과 Tenant 허용 정책 관리 |

모든 object 조회와 mutation은 Tenant → Workspace → Cluster → Namespace scope를 application service에서 다시 평가한다. HTTP method나 Frontend 표시 여부만 신뢰하지 않는다.

## 10. 완료 기준

Phase 2 MVP는 다음 수용 흐름이 격리 namespace에서 통과해야 한다.

1. Artifact Hub 검색과 version 상세 조회
2. 선택 version 다운로드, SHA-256 및 provenance 상태 표시
3. Tenant A/B Chart와 Values Profile 상호 비노출
4. schema form/YAML/AI patch의 동일 결과와 invalid key 차단
5. manifest preview, 위험 resource와 RBAC preflight 표시
6. install 성공, Release/Pod health와 audit 확인
7. Values upgrade, history, rollback 성공
8. uninstall preview, exact confirmation과 bounded cleanup
9. Runner timeout/cancel/restart recovery와 Secret/output 마스킹
10. Ollama/OpenAI/Google GenAI profile별 fake adapter 회귀 및 외부 전송 동의 검증

## 11. 단계별 구현

| 단계 | 범위 |
| --- | --- |
| P2-A | domain/API 재정의, Tenant scope, capability와 migration |
| P2-B | Artifact Hub/repository/OCI/upload, Tenant Chart Library |
| P2-C | Schema Form, YAML, Values Profile/version/diff |
| P2-D | AI Provider profile과 Values Assistant |
| P2-E | render/policy/RBAC/live diff와 승인 UX |
| P2-F | Helm Runner install/upgrade/status/history/rollback/uninstall |
| P2-G | AI Analysis/Incident 연결, 전체 수용시험과 문서화 |
