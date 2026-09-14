#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"
require_acceptance_command jq
require_acceptance_command curl

OUTPUT_DIR="${AIOPS_EVIDENCE_DIR:-${ACCEPTANCE_ROOT}/artifacts/production-evidence}"
OUTPUT="${OUTPUT_DIR}/transport.json"
PORTAL_URL="${AIOPS_PORTAL_PUBLIC_URL:-}"
ISSUER="${AIOPS_OIDC_PUBLIC_ISSUER:-}"
CA_ARGS=()
mkdir -p "${OUTPUT_DIR}"

[[ -n "${AIOPS_TLS_CA_BUNDLE:-}" ]] && CA_ARGS=(--cacert "${AIOPS_TLS_CA_BUNDLE}")

if [[ -z "${PORTAL_URL}" || -z "${ISSUER}" ]]; then
  jq -n '{status:"BLOCKED",reason:"AIOPS_PORTAL_PUBLIC_URL and AIOPS_OIDC_PUBLIC_ISSUER are required"}' >"${OUTPUT}"
  echo "BLOCKED: public Portal and OIDC issuer URLs are required." >&2
  exit 6
fi
if [[ "${PORTAL_URL}" != https://* || "${ISSUER}" != https://* ]]; then
  jq -n --arg portal "${PORTAL_URL}" --arg issuer "${ISSUER}" \
    '{status:"FAILED",reason:"Production browser endpoints must use HTTPS",portal:$portal,issuer:$issuer}' >"${OUTPUT}"
  echo "FAILED: production browser endpoints must use HTTPS." >&2
  exit 5
fi

discovery_url="${ISSUER%/}/.well-known/openid-configuration"
discovery_file="$(mktemp)"
trap 'rm -f "${discovery_file}"' EXIT
curl --fail --silent --show-error --max-time "${AIOPS_ACCEPTANCE_HTTP_TIMEOUT_SECONDS:-15}" \
  "${CA_ARGS[@]}" "${discovery_url}" >"${discovery_file}"
actual_issuer="$(jq -r '.issuer // empty' "${discovery_file}")"
authorization_endpoint="$(jq -r '.authorization_endpoint // empty' "${discovery_file}")"
jwks_uri="$(jq -r '.jwks_uri // empty' "${discovery_file}")"
[[ "${actual_issuer%/}" == "${ISSUER%/}" ]] || { echo "OIDC discovery issuer mismatch." >&2; exit 5; }
[[ "${authorization_endpoint}" == https://* && "${jwks_uri}" == https://* ]] || {
  echo "OIDC discovery exposes a non-HTTPS browser or JWKS endpoint." >&2
  exit 5
}
curl --fail --silent --show-error --max-time "${AIOPS_ACCEPTANCE_HTTP_TIMEOUT_SECONDS:-15}" \
  "${CA_ARGS[@]}" "${PORTAL_URL%/}/healthz" >/dev/null

redirect_url="$(curl --silent --show-error --max-time "${AIOPS_ACCEPTANCE_HTTP_TIMEOUT_SECONDS:-15}" \
  "${CA_ARGS[@]}" --output /dev/null --write-out '%{redirect_url}' \
  "${PORTAL_URL%/}/oauth2/authorization/aiops")"
[[ "${redirect_url}" == "${authorization_endpoint}"* ]] || {
  echo "Portal OAuth redirect does not use the discovered authorization endpoint." >&2
  exit 5
}
[[ "${redirect_url}" == *"login%2Foauth2%2Fcode%2Faiops"* || "${redirect_url}" == *"login/oauth2/code/aiops"* ]] || {
  echo "Portal OAuth redirect is missing the expected callback path." >&2
  exit 5
}

jq -n --arg portal "${PORTAL_URL}" --arg issuer "${ISSUER}" --arg authorizationEndpoint "${authorization_endpoint}" \
  '{status:"PASSED",portal:$portal,issuer:$issuer,authorizationEndpoint:$authorizationEndpoint,tlsVerified:true,callbackVerified:true}' >"${OUTPUT}"
echo "Production HTTPS/OIDC transport gate passed."

