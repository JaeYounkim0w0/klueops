#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_URL="${AIOPS_BACKEND_URL:-http://127.0.0.1:8080}"
CHECKED="${ROOT_DIR}/frontend/openapi/klueops.json"
ACTUAL="$(mktemp)"
EXPECTED="$(mktemp)"
trap 'rm -f "${ACTUAL}" "${EXPECTED}"' EXIT

curl --fail --silent --show-error "${BACKEND_URL}/v3/api-docs" \
  | jq --sort-keys 'del(.servers)' >"${ACTUAL}"
jq --sort-keys 'del(.servers)' "${CHECKED}" >"${EXPECTED}"

if ! cmp -s "${EXPECTED}" "${ACTUAL}"; then
  echo "Checked OpenAPI contract differs from the running backend." >&2
  diff -u "${EXPECTED}" "${ACTUAL}" || true
  exit 2
fi

echo "Running backend OpenAPI matches the checked contract."
