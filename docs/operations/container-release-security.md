# Container Release Security

기준일: 2026-09-03

## 릴리스 흐름

`.github/workflows/signed-container-release.yml`은 Backend, Frontend, Managed Keycloak 이미지를 동일한 절차로 처리한다.

1. 이미지를 빌드한다.
2. Trivy가 HIGH/CRITICAL 취약점을 차단한다.
3. Anchore Syft가 CycloneDX SBOM을 생성한다.
4. 승인된 GHCR에 digest로 push한다.
5. Cosign keyless signing으로 이미지 digest에 서명한다.
6. digest, SBOM과 서명 검증 정보를 릴리스 증적으로 보관한다.

## 배포 규칙

- `global.productionMode=true`에서는 세 컴포넌트 image digest가 모두 필수다.
- 운영 values에는 mutable tag만 입력할 수 없다.
- 배포 전 `scripts/validate-supply-chain.sh`와 `scripts/validate-packaging.sh`를 실행한다.
- 고객 registry에서 `cosign verify`와 SBOM 조회가 성공한 결과를 release checklist에 첨부한다.

CI 구현 완료와 고객 registry 승인 완료는 별도 상태다. 네트워크, OIDC issuer와 transparency log 정책에 따라 고객 환경의 signing verification을 반드시 다시 수행한다.
