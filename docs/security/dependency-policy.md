# Dependency and License Policy

기준일: 2026-09-14

## 범위

공개 저장소는 Backend, Command Runner와 Frontend production dependency의 CycloneDX SBOM을 생성하고, 네 runtime container 이미지의 OS·library 취약점을 별도로 검사한다. 개발 도구 dependency는 제품 runtime과 분리해 기록하되 알려진 위험을 숨기지 않는다.

## 라이선스 판정

`config/dependency-license-allowlist.txt`는 현재 검토한 SPDX identifier와 명시적 license 이름만 포함한다. `scripts/validate-dependency-licenses.sh`는 모든 runtime SBOM component에 license metadata와 허용 가능한 선택지가 하나 이상 있는지 검사한다. 새롭거나 누락된 license는 자동 승인하지 않고 검토가 끝날 때까지 gate를 실패시킨다.

현재 runtime SBOM 결과는 다음과 같다.

| 구성요소 | Component 수 | License 누락 | 판정 |
| --- | ---: | ---: | --- |
| Backend | 186 | 0 | 통과 |
| Command Runner | 46 | 0 | 통과 |
| Frontend production | 19 | 0 | 통과 |

현재 목록에는 Apache-2.0, MIT 계열, BSD 계열, CC0, EPL, MPL, LGPL-2.1-only와 GPL-2.0-with-classpath-exception 등이 포함된다. Copyleft 또는 예외 조항이 있는 dependency는 독립 라이브러리로 사용하고 원 저작권·license 고지를 제거하지 않는다. 코드를 복사·수정하거나 배포 방식을 바꾸면 allowlist 통과 여부와 별개로 의무를 다시 검토한다.

## 취약점 정책

- Frontend production dependency의 High/Critical 취약점은 CI와 `scripts/validate-supply-chain.sh`에서 차단한다.
- Backend, Command Runner와 Frontend를 포함한 전체 runtime image는 Trivy로 수정 가능한 High/Critical 취약점을 차단한다.
- 네 runtime image의 SBOM과 scan은 공개 `supply-chain` workflow에서 pull request, `main`, 주간 일정과 수동 실행으로 갱신한다.
- 수정본이 없는 취약점은 영향 경로, 완화책과 재검토 조건을 문서화한다. `ignore-unfixed`는 위험이 없다는 의미가 아니라 아직 적용 가능한 수정본이 없음을 의미한다.
- dependency나 base image를 추가·변경하면 SBOM, license gate와 container scan을 같은 변경에서 실행한다.

2026-09-14 기준 `npm audit --omit=dev`는 production dependency High 0/Critical 0이다. 전체 개발 dependency에는 Moderate 3, High 7, Critical 2가 보고되며 Orval, Vite/Vitest와 관련 transitive build tooling이 포함된다. 현재 자동 수정은 major upgrade를 요구하므로 runtime 위험과 분리해 공개하고, generated-client·typecheck·unit/E2E 계약을 유지하는 독립 upgrade 작업으로 처리한다. 개발 의존성 결과는 CI artifact와 정기 점검에서 계속 추적한다.

### 현재 container blocker

최초 공개 CI scan에서 기존 NGINX 1.27 Alpine과 Temurin 17 Jammy runtime의 수정 가능한 OS 취약점이 확인되어 Frontend는 NGINX unprivileged 1.31.5/Alpine 3.24, Backend와 Command Runner는 Temurin 17 Noble로 갱신했다. 후속 scan에서 Java image의 Tomcat 10.1.55와 kubectl 1.34.1 취약점을 확인해 Tomcat 10.1.59와 같은 Kubernetes minor의 최신 patch인 kubectl 1.34.11로 갱신했다.

최신 안정 Keycloak 26.7.3 image에는 2026-09-14 기준 `io.netty:netty-handler` 4.1.136.Final의 Critical `CVE-2026-75595`가 남아 있고 Trivy가 4.1.137.Final을 수정 버전으로 제시한다. 해당 library는 upstream Keycloak image에 포함되므로 임의 교체나 예외 처리하지 않는다. 수정된 Keycloak 안정 patch가 제공되면 image를 갱신하고 전체 identity 회귀 테스트와 container scan을 다시 수행한다.

Command Runner의 kubectl 1.34.11은 Kubernetes 1.34 계열의 현재 최신 patch지만 Go 1.26.5로 빌드돼 수정 가능한 High 취약점 8건이 남아 있다. Trivy가 제시하는 최소 Go 수정본은 1.26.6이며, 다음 Kubernetes patch 일정은 2026-09-15다. 패키지 Backend는 kubectl을 실행하지 않으므로 중복 바이너리를 제거했고 Runner만 upstream 보안 patch를 기다린다. 두 upstream 항목이 해소되기 전 `supply-chain` workflow는 의도적으로 실패하며 OSS-06 완료로 판정하지 않는다.

## 로컬 실행

```bash
./scripts/generate-sbom.sh
./scripts/validate-dependency-licenses.sh
npm audit --prefix frontend --omit=dev --audit-level=high
./scripts/validate-supply-chain.sh
```

생성된 `artifacts/sbom`과 audit 결과는 로컬 증빙이며 Git에 commit하지 않는다.
