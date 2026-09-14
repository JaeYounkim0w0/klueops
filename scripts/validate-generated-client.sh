#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FRONTEND_DIR="${ROOT_DIR}/frontend"
OPENAPI_FILE="${FRONTEND_DIR}/openapi/klueops.json"
GENERATED_FILE="${FRONTEND_DIR}/src/api/generated/klueops.ts"

if [[ ! -s "${OPENAPI_FILE}" ]]; then
  echo "Checked OpenAPI contract is missing: ${OPENAPI_FILE}" >&2
  exit 2
fi

if [[ ! -s "${GENERATED_FILE}" ]]; then
  echo "Generated API client is missing: ${GENERATED_FILE}" >&2
  exit 3
fi

BEFORE="$(mktemp)"
trap 'rm -f "${BEFORE}"' EXIT
cp "${GENERATED_FILE}" "${BEFORE}"

cd "${FRONTEND_DIR}"
npm run generate:api >/dev/null

if ! cmp -s "${BEFORE}" "${GENERATED_FILE}"; then
  echo "Generated API client drift detected. Run npm run generate:api and include the result." >&2
  diff -u "${BEFORE}" "${GENERATED_FILE}" || true
  exit 4
fi

echo "Generated API client matches the checked OpenAPI contract."
