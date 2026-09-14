#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/compose.identity.yaml"
BOOTSTRAP="${ROOT_DIR}/deploy/helm/aiops/files/bootstrap-keycloak-realm.sh"
ADMIN_USER="${AIOPS_KEYCLOAK_ADMIN:-admin}"
ADMIN_PASSWORD="${AIOPS_KEYCLOAK_ADMIN_PASSWORD:?AIOPS_KEYCLOAK_ADMIN_PASSWORD is required}"
CLIENT_SECRET="${AIOPS_OIDC_CLIENT_SECRET:?AIOPS_OIDC_CLIENT_SECRET is required}"
INITIAL_ADMIN_USERNAME="${AIOPS_INITIAL_ADMIN_USERNAME:-platform-admin}"
INITIAL_ADMIN_PASSWORD="${AIOPS_INITIAL_ADMIN_PASSWORD:?AIOPS_INITIAL_ADMIN_PASSWORD is required}"
KEYCLOAK_PORT="${AIOPS_KEYCLOAK_PORT:-18080}"
AIOPS_PUBLIC_URL="${AIOPS_PUBLIC_URL:-http://127.0.0.1:5173}"
ADDITIONAL_PUBLIC_URLS="${AIOPS_ADDITIONAL_PUBLIC_URLS:-}"

dc() {
  docker compose -f "${COMPOSE_FILE}" "$@"
}

dc up -d keycloak
dc cp "${BOOTSTRAP}" keycloak:/tmp/bootstrap-keycloak-realm.sh
dc exec -T \
  -e KEYCLOAK_URL=http://localhost:8080 \
  -e KEYCLOAK_ADMIN="${ADMIN_USER}" \
  -e KEYCLOAK_ADMIN_PASSWORD="${ADMIN_PASSWORD}" \
  -e AIOPS_PUBLIC_URL="${AIOPS_PUBLIC_URL}" \
  -e AIOPS_ADDITIONAL_PUBLIC_URLS="${ADDITIONAL_PUBLIC_URLS}" \
  -e AIOPS_AUTH_PUBLIC_URL="http://127.0.0.1:${KEYCLOAK_PORT}" \
  -e AIOPS_OIDC_CLIENT_SECRET="${CLIENT_SECRET}" \
  -e AIOPS_INITIAL_ADMIN_USERNAME="${INITIAL_ADMIN_USERNAME}" \
  -e AIOPS_INITIAL_ADMIN_PASSWORD="${INITIAL_ADMIN_PASSWORD}" \
  keycloak bash /tmp/bootstrap-keycloak-realm.sh

cat <<EOF
Keycloak development realm is ready.
Issuer: http://127.0.0.1:${KEYCLOAK_PORT}/realms/aiops
Client ID: aiops-bff
Portal URL: ${AIOPS_PUBLIC_URL}
Additional Portal URLs: ${ADDITIONAL_PUBLIC_URLS:-none}
Initial administrator: ${INITIAL_ADMIN_USERNAME} (temporary password)
EOF
