#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NAMESPACE="${AIOPS_KEYCLOAK_NAMESPACE:-aiops-system}"
CLIENT_SECRET_NAME="${AIOPS_KEYCLOAK_CLIENT_SECRET_NAME:-aiops-keycloak-client}"
MASTER_KEY_SECRET_NAME="${AIOPS_PORTAL_MASTER_KEY_SECRET_NAME:-aiops-portal-master-key}"
DB_SECRET_NAME="${AIOPS_PORTAL_DB_SECRET_NAME:-aiops-portal-db}"
AUTH_BASE_URL="${AIOPS_AUTH_BASE_URL:-http://auth.aiops.local:30080}"
REALM_URL="${AUTH_BASE_URL%/}/realms/aiops"

command -v kubectl >/dev/null 2>&1 || { echo "kubectl is required." >&2; exit 2; }
command -v mvn >/dev/null 2>&1 || { echo "Maven is required." >&2; exit 2; }

client_secret="$(kubectl get secret "${CLIENT_SECRET_NAME}" -n "${NAMESPACE}" \
  -o jsonpath='{.data.client-secret}' | base64 --decode)"
[[ -n "${client_secret}" ]] || { echo "OIDC client secret is missing." >&2; exit 3; }

if [[ -z "${AIOPS_LOCAL_MASTER_KEY:-}" ]]; then
  master_key="$(kubectl get secret "${MASTER_KEY_SECRET_NAME}" -n "${NAMESPACE}" \
    -o jsonpath='{.data.master-key}' | base64 --decode)"
  [[ -n "${master_key}" ]] || { echo "Local credential encryption master key is missing." >&2; exit 3; }
  export AIOPS_LOCAL_MASTER_KEY="${master_key}"
  unset master_key
fi

if [[ -z "${AIOPS_DATASOURCE_USERNAME:-}" || -z "${AIOPS_DATASOURCE_PASSWORD:-}" ]]; then
  db_username="$(kubectl get secret "${DB_SECRET_NAME}" -n "${NAMESPACE}" \
    -o jsonpath='{.data.username}' | base64 --decode)"
  db_password="$(kubectl get secret "${DB_SECRET_NAME}" -n "${NAMESPACE}" \
    -o jsonpath='{.data.password}' | base64 --decode)"
  [[ -n "${db_username}" && -n "${db_password}" ]] || {
    echo "Local Portal database credential is missing." >&2
    exit 3
  }
  export AIOPS_DATASOURCE_USERNAME="${AIOPS_DATASOURCE_USERNAME:-${db_username}}"
  export AIOPS_DATASOURCE_PASSWORD="${AIOPS_DATASOURCE_PASSWORD:-${db_password}}"
  unset db_username db_password
fi

export SPRING_PROFILES_ACTIVE="${AIOPS_BACKEND_PROFILES:-security-oidc}"
export AIOPS_OIDC_EXPLICIT_ENDPOINTS_ENABLED=true
export AIOPS_OIDC_ISSUER_URI="${REALM_URL}"
export AIOPS_OIDC_AUTHORIZATION_URI="${REALM_URL}/protocol/openid-connect/auth"
export AIOPS_OIDC_TOKEN_URI="${REALM_URL}/protocol/openid-connect/token"
export AIOPS_OIDC_JWK_SET_URI="${REALM_URL}/protocol/openid-connect/certs"
export AIOPS_OIDC_USER_INFO_URI="${REALM_URL}/protocol/openid-connect/userinfo"
export AIOPS_OIDC_CLIENT_ID="${AIOPS_OIDC_CLIENT_ID:-aiops-bff}"
export AIOPS_OIDC_CLIENT_SECRET="${client_secret}"
export AIOPS_SESSION_COOKIE_SECURE="${AIOPS_SESSION_COOKIE_SECURE:-false}"
export AIOPS_SESSION_TIMEOUT="${AIOPS_SESSION_TIMEOUT:-30m}"
export AIOPS_SESSION_ABSOLUTE_TIMEOUT="${AIOPS_SESSION_ABSOLUTE_TIMEOUT:-8h}"

unset client_secret
cd "${ROOT_DIR}/backend"
exec mvn -q spring-boot:run
