#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_DIR="${AIOPS_SBOM_OUTPUT_DIR:-${ROOT_DIR}/artifacts/sbom}"

command -v jq >/dev/null 2>&1 || { echo "jq is required." >&2; exit 2; }
command -v mvn >/dev/null 2>&1 || { echo "Maven is required." >&2; exit 2; }
command -v npm >/dev/null 2>&1 || { echo "npm is required." >&2; exit 2; }
mkdir -p "${OUTPUT_DIR}"

echo "[SBOM] generating Backend CycloneDX inventory"
(
  cd "${ROOT_DIR}/backend"
  mvn -q -Dmaven.repo.local="${ROOT_DIR}/.m2/repository" \
    org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom \
    -Dcyclonedx.outputFormat=json -Dcyclonedx.includeTestScope=false
)
cp "${ROOT_DIR}/backend/target/bom.json" "${OUTPUT_DIR}/backend.cdx.json"

echo "[SBOM] generating Frontend production CycloneDX inventory"
(
  cd "${ROOT_DIR}/frontend"
  npm sbom --omit=dev --sbom-format cyclonedx
) >"${OUTPUT_DIR}/frontend.cdx.json"

for sbom in "${OUTPUT_DIR}/backend.cdx.json" "${OUTPUT_DIR}/frontend.cdx.json"; do
  [[ "$(jq -r '.bomFormat' "${sbom}")" == "CycloneDX" ]] || {
    echo "Invalid CycloneDX document: ${sbom}" >&2
    exit 3
  }
done

echo "SBOM generation passed: ${OUTPUT_DIR}"
