#!/usr/bin/env bash

# Resolves browser-facing URLs once so Helm callbacks and post-deploy checks cannot drift.
resolve_public_urls() {
  local public_host="${AIOPS_PUBLIC_HOST:-127.0.0.1}"
  local portal_default="http://${public_host}:30081"
  local issuer_default="http://auth.aiops.local:30080"

  AIOPS_PORTAL_PUBLIC_URL="${AIOPS_PORTAL_PUBLIC_URL:-${AIOPS_LOCAL_FRONTEND_URL:-${portal_default}}}"
  AIOPS_OIDC_PUBLIC_ISSUER="${AIOPS_OIDC_PUBLIC_ISSUER:-${AIOPS_LOCAL_KEYCLOAK_URL:-${issuer_default}}}"
  AIOPS_OIDC_PUBLIC_URL="${AIOPS_OIDC_PUBLIC_ISSUER%/}"
  AIOPS_OIDC_PUBLIC_URL="${AIOPS_OIDC_PUBLIC_URL%/realms/aiops}"

  local configured="${AIOPS_ADDITIONAL_PUBLIC_URLS:-http://127.0.0.1:5173}"
  if [[ ",${configured}," != *",${AIOPS_PORTAL_PUBLIC_URL},"* ]]; then
    configured="${configured},${AIOPS_PORTAL_PUBLIC_URL}"
  fi
  AIOPS_ADDITIONAL_PUBLIC_URLS="${configured#,}"
  export AIOPS_PORTAL_PUBLIC_URL AIOPS_OIDC_PUBLIC_ISSUER AIOPS_OIDC_PUBLIC_URL AIOPS_ADDITIONAL_PUBLIC_URLS
}

validate_public_urls() {
  [[ "${AIOPS_PORTAL_PUBLIC_URL}" =~ ^https?://[^[:space:]]+$ ]] || {
    echo "AIOPS_PORTAL_PUBLIC_URL must be an absolute HTTP(S) URL." >&2
    return 2
  }
  [[ "${AIOPS_OIDC_PUBLIC_ISSUER}" =~ ^https?://[^[:space:]]+$ ]] || {
    echo "AIOPS_OIDC_PUBLIC_ISSUER must be an absolute HTTP(S) URL." >&2
    return 2
  }
  if [[ "${AIOPS_PRODUCTION_MODE:-false}" == true ]]; then
    [[ "${AIOPS_PORTAL_PUBLIC_URL}" == https://* ]] || {
      echo "Production Portal URL must use HTTPS." >&2
      return 2
    }
    [[ "${AIOPS_OIDC_PUBLIC_ISSUER}" == https://* ]] || {
      echo "Production OIDC issuer must use HTTPS." >&2
      return 2
    }
  fi
}

public_url_helm_args() {
  printf '%s\n' \
    "--set-string" "authentication.publicBaseUrl=${AIOPS_PORTAL_PUBLIC_URL}" \
    "--set-string" "authentication.managedKeycloak.publicUrl=${AIOPS_OIDC_PUBLIC_URL}" \
    "--set" "authentication.managedKeycloak.additionalPortalUrls={${AIOPS_ADDITIONAL_PUBLIC_URLS}}"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  echo "scripts/lib/public-url-config.sh is a library and must not be executed directly." >&2
  exit 2
fi
