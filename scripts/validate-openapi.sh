#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="${ROOT_DIR}/backend"

if ! rg -n "@Operation" "${ROOT_DIR}/backend/src/main/java/io/strato/aiops/adapter/in/web" >/dev/null; then
  echo "No @Operation annotations found in web adapters." >&2
  exit 1
fi

if ! rg -n "@Tag" "${ROOT_DIR}/backend/src/main/java/io/strato/aiops/adapter/in/web" >/dev/null; then
  echo "No @Tag annotations found in web adapters." >&2
  exit 1
fi

cd "${BACKEND_DIR}"
if [[ -x "./mvnw" ]]; then
  MVN="./mvnw"
elif command -v mvn >/dev/null 2>&1; then
  MVN="mvn"
else
  echo "Maven is required for the OpenAPI contract test." >&2
  exit 1
fi

"${MVN}" -q -Dmaven.repo.local="${ROOT_DIR}/.m2/repository" -Dtest=OpenApiContractTest test
echo "OpenAPI annotation and runtime contract checks passed."
