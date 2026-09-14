# Phase 2 Application Delivery UI/UX Screen Design

기준일: 2026-09-14  
상태: HTML 시안 완료, 구현 미착수

## 1. 시안 실행

[HTML 시안 열기](ui-mockups/index.html)

브라우저에서 직접 열면 좌측 `Applications` 하위 메뉴와 상단 화면 전환 control로 여섯 개 시안을 확인할 수 있다. Query parameter deep-link도 지원한다.

```text
ui-mockups/index.html?screen=discover
ui-mockups/index.html?screen=library
ui-mockups/index.html?screen=values
ui-mockups/index.html?screen=preview
ui-mockups/index.html?screen=releases
ui-mockups/index.html?screen=ai-settings
```

## 2. 공통 UX 원칙

- 기존 KlueOps sidebar, Tenant/Workspace selector, blue operator surface와 status pill을 유지한다.
- 복합 작업은 `Discover → Library → Values → Preview → Release` 단계로 나눈다.
- tab/filter/선택 screen은 URL에 보존해 deep-link가 가능해야 한다.
- 초보자에게 Chart, Values, Release의 의미와 다음 안전 행동을 짧게 설명한다.
- 위험 작업은 목록에서 즉시 실행하지 않고 설명 → preflight → exact confirmation → 결과 순으로 제공한다.
- 장시간 import/render/deploy는 기존 Job Center와 하단 Job Dock에 등록한다.
- AI 제안은 사용자 입력과 시각적으로 구분하고 근거, assumption과 적용 전 diff를 표시한다.
- Provider/model과 외부 전송 여부를 AI 실행 위치 가까이에 표시한다.

## 3. Navigation

```text
운영 관리
├─ Dashboard
├─ Triage
├─ Fleet Command
├─ Incidents
├─ Clusters
├─ Applications
│  ├─ Discover
│  ├─ Chart Library
│  └─ Releases
├─ Policies
└─ Audit

설정
├─ 사용자 설정
├─ Data & Runtime
├─ AI Provider
└─ 접근 관리
```

Feature가 비활성화되면 Applications 하위 메뉴 전체를 숨기고 직접 URL은 기능 비활성 Problem Detail 화면으로 연결한다.

## 4. 화면 목록

| ID | 화면 | 핵심 목표 |
| --- | --- | --- |
| AD-01 | Discover | Artifact Hub package 검색·비교·import |
| AD-02 | Chart Library | Tenant Chart/version/trust 상태 관리 |
| AD-03 | Values Studio | Form/YAML/AI로 Values Profile 작성 |
| AD-04 | Deployment Preview | manifest 위험·RBAC·변경사항 검토와 승인 |
| AD-05 | Releases | Cluster Helm Release 상태·history·rollback |
| AI-01 | AI Provider Settings | Local/외부 Provider profile과 Tenant routing 관리 |

## 5. AD-01 Discover

![Artifact Hub 검색 시안](ui-mockups/screenshots/01-discover.png)

### 구성

- 검색어, category, official/verified, repository filter
- 결과 card의 publisher, version, license, update 시각과 security summary
- 우측 detail drawer에 README 요약, versions, default Values/Schema 제공 여부
- `Tenant Library에 가져오기` 전 source URL, exact version과 trust 상태 확인

### 상태

- Artifact Hub timeout: 기존 Library와 upload 동작 유지
- 검색 결과 없음: filter 초기화와 직접 repository/upload 안내
- deprecated Chart: 경고와 기본 import 차단
- security report Critical: 위험 설명과 관리자 정책에 따른 차단

## 6. AD-02 Chart Library

![Tenant Chart Library 시안](ui-mockups/screenshots/02-library.png)

### 구성

- 현재 Tenant 소유 Chart만 표시
- source, version 수, trust, Values Profile, 최근 사용과 archive 상태
- `.tgz 업로드`, repository/OCI 가져오기, Artifact Hub로 이동
- 선택 Chart의 immutable versions, digest, provenance와 사용 중 Release 수

### 위험 UX

- 사용 중 version 삭제 금지
- archive는 신규 배포만 차단하고 기존 Release/rollback artifact를 유지
- repository credential 오류는 secret을 표시하지 않고 source health만 표시

## 7. AD-03 Values Studio

![Custom Values와 AI Assistant 시안](ui-mockups/screenshots/03-values-studio.png)

### 구성

- Chart/version과 Values Profile revision 고정 표시
- `Form`, `YAML`, `Diff` tab
- Schema Form의 설명, 기본값, validation과 Cluster capability hint
- 우측 AI Assistant의 자연어 입력, provider/model, 외부 전송 badge
- AI suggestion은 patch, assumptions, warnings와 `Diff 검토`만 제공

### 저장

- validation error가 있으면 revision 저장 차단
- Secret-like field는 masked input과 existing Secret reference 우선
- 저장 시 revision note와 변경 key 수 표시

## 8. AD-04 Deployment Preview

![배포 Preview 시안](ui-mockups/screenshots/04-deployment-preview.png)

### 단계

1. 대상 확인
2. Chart/Values 고정
3. Render 및 정책 검사
4. Live diff
5. exact confirmation

### 중요 정보

- Cluster/Namespace/Release
- Chart digest와 Values revision
- Create/Update/Delete/Unchanged count
- CRD, cluster RBAC, hook, privileged, host access와 PVC 영향
- SSAR 결과와 partial/unknown source
- plan 만료 시간

위험이 없더라도 배포 버튼 옆에 실제 생성 resource 수와 rollback 정책을 표시한다.

## 9. AD-05 Releases

![Helm Releases 시안](ui-mockups/screenshots/05-releases.png)

### 구성

- Tenant/Workspace/Cluster/Namespace filter
- release status, health, Chart/Values revision, 마지막 작업과 운영자
- detail에서 workload health, Helm history, Values diff와 Audit timeline
- `Upgrade 계획`, `Rollback 계획`, `Uninstall 계획`은 서로 다른 modal/workflow

Uninstall은 PVC와 external resource 보존 여부를 설명하고 exact phrase를 요구한다.

## 10. AI-01 AI Provider Settings

![AI Provider 설정 시안](ui-mockups/screenshots/06-ai-provider-settings.png)

### Platform Admin

- Ollama/OpenAI/Google GenAI/OpenAI-compatible profile 등록
- endpoint/API key existing Secret, model과 timeout
- 연결 검사는 비민감 synthetic prompt만 사용
- external/local, validated/error와 최근 검사 시각 표시

### Tenant Admin

- Analysis/Chat/Helm Values별 허용 profile/model 선택
- 외부 데이터 전송 opt-in
- fallback 대상과 동작 확인
- Local → External 자동 fallback은 기본 off

Credential은 저장 후 재표시하지 않고 교체와 삭제만 제공한다.

## 11. Responsive

- 1280px 이상: sidebar + main + detail/assistant 3-column
- 900~1279px: sidebar + main, detail drawer overlay
- 899px 이하: sidebar 접힘, step별 단일 column
- Values Editor와 Preview table은 horizontal scroll보다 field/card 재배치를 우선한다.
- 위험 confirmation은 mobile에서도 viewport 아래로 숨지 않게 sticky action bar를 사용한다.

## 12. 접근성

- status를 색만으로 구분하지 않고 icon/text를 함께 제공한다.
- tab, drawer, modal과 stepper에 keyboard focus 순서와 ARIA 상태를 제공한다.
- AI streaming과 Job progress는 과도한 live announcement를 피하고 완료/실패만 polite region으로 알린다.
- YAML validation은 line/column과 해결 방법을 text로 제공한다.
- 위험 action은 icon-only button으로 제공하지 않는다.

## 13. Frontend 구현 경계

- route: `/applications/discover`, `/applications/library`, `/applications/releases`
- Values Studio: `/applications/charts/:chartId/versions/:versionId/values/:profileId`
- Preview: `/applications/deployment-plans/:planId`
- AI Settings: `/settings/ai-providers`
- catalog/import/deploy API client는 `frontend/src/api`에 둔다.
- long-running job은 `jobCenter` store에 등록한다.
- cross-screen filter는 전용 Pinia store보다 URL query를 우선한다.
- page component는 조합만 담당하고 catalog card, Values editor, AI suggestion, policy result와 release history를 component로 분리한다.
- CSS는 `frontend/src/styles/components/application-delivery.css` 한 ownership 파일에서 시작한다.
