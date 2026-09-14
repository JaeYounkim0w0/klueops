#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "${ROOT_DIR}/scripts/lib/public-url-config.sh"

unset AIOPS_PUBLIC_HOST AIOPS_PORTAL_PUBLIC_URL AIOPS_OIDC_PUBLIC_ISSUER AIOPS_ADDITIONAL_PUBLIC_URLS
resolve_public_urls
[[ "${AIOPS_PORTAL_PUBLIC_URL}" == "http://127.0.0.1:30081" ]]
[[ "${AIOPS_OIDC_PUBLIC_ISSUER}" == "http://auth.aiops.local:30080" ]]

AIOPS_PUBLIC_HOST="10.20.30.40"
unset AIOPS_PORTAL_PUBLIC_URL AIOPS_ADDITIONAL_PUBLIC_URLS
resolve_public_urls
[[ "${AIOPS_PORTAL_PUBLIC_URL}" == "http://10.20.30.40:30081" ]]
[[ ",${AIOPS_ADDITIONAL_PUBLIC_URLS}," == *",http://10.20.30.40:30081,"* ]]

AIOPS_PORTAL_PUBLIC_URL="https://portal.example.com"
AIOPS_OIDC_PUBLIC_ISSUER="https://id.example.com/realms/aiops"
AIOPS_PRODUCTION_MODE=true
resolve_public_urls
validate_public_urls

AIOPS_PORTAL_PUBLIC_URL="http://portal.example.com"
if validate_public_urls 2>/dev/null; then
  echo "Production URL validation accepted HTTP." >&2
  exit 1
fi

echo "Public URL configuration tests passed."
