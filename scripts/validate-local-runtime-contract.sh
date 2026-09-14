#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OIDC_RUNNER="${ROOT_DIR}/scripts/run-local-oidc.sh"
VITE_CONFIG="${ROOT_DIR}/frontend/vite.config.ts"
KEYCLOAK_BOOTSTRAP="${ROOT_DIR}/scripts/bootstrap-keycloak-dev.sh"

rg -q 'MASTER_KEY_SECRET_NAME=.*aiops-portal-master-key' "${OIDC_RUNNER}"
rg -q 'AIOPS_LOCAL_MASTER_KEY' "${OIDC_RUNNER}"
rg -q 'jsonpath=.*data\.master-key' "${OIDC_RUNNER}"
rg -q 'Local credential encryption master key is missing' "${OIDC_RUNNER}"
rg -q 'DB_SECRET_NAME=.*aiops-portal-db' "${OIDC_RUNNER}"
rg -q 'jsonpath=.*data\.username' "${OIDC_RUNNER}"
rg -q 'jsonpath=.*data\.password' "${OIDC_RUNNER}"
rg -q 'AIOPS_DATASOURCE_PASSWORD' "${OIDC_RUNNER}"
rg -q 'AIOPS_FRONTEND_HOST' "${VITE_CONFIG}"
rg -q 'AIOPS_ADDITIONAL_PUBLIC_URLS' "${KEYCLOAK_BOOTSTRAP}"
rg -q -- '-e AIOPS_ADDITIONAL_PUBLIC_URLS=' "${KEYCLOAK_BOOTSTRAP}"

echo "Local runtime secret contract passed."
