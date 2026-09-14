#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

required_files=(
  "backend/Dockerfile"
  "command-runner/Dockerfile"
  "command-runner/pom.xml"
  "frontend/Dockerfile"
  "frontend/nginx.conf"
  "compose.yaml"
  ".env.example"
  "deploy/helm/aiops/Chart.yaml"
  "deploy/helm/aiops/values.yaml"
  "deploy/helm/aiops/values-local.yaml"
  "deploy/helm/aiops/values-production.yaml"
  "deploy/helm/aiops/templates/_helpers.tpl"
  "deploy/helm/aiops/templates/portal-contract.yaml"
  "deploy/helm/aiops/templates/portal-db-bootstrap-job.yaml"
  "deploy/helm/aiops/files/bootstrap-portal-db.sh"
  "deploy/helm/aiops/templates/backend-config.yaml"
  "deploy/helm/aiops/templates/backend-deployment.yaml"
  "deploy/helm/aiops/templates/backend-service.yaml"
  "deploy/helm/aiops/templates/command-runner.yaml"
  "deploy/helm/aiops/templates/frontend-nginx-config.yaml"
  "deploy/helm/aiops/templates/frontend-deployment.yaml"
  "deploy/helm/aiops/templates/frontend-service.yaml"
  "deploy/helm/aiops/templates/portal-ingress.yaml"
  "deploy/helm/aiops/templates/portal-pdb.yaml"
  "scripts/validate-managed-keycloak.sh"
  "keycloak/Dockerfile"
  "scripts/install-managed-keycloak.sh"
  "scripts/install-local-all-in-one.sh"
  "scripts/init/all-in-one.sh"
  "scripts/deploy/backend.sh"
  "scripts/deploy/frontend.sh"
  "scripts/deploy/keycloak.sh"
  "scripts/deploy/command-runner.sh"
  "scripts/init/command-runner.sh"
  "scripts/validate-command-runner.sh"
  "scripts/test-portal-db-bootstrap.sh"
)

for file in "${required_files[@]}"; do
  if [[ ! -s "${ROOT_DIR}/${file}" ]]; then
    echo "Packaging artifact is missing: ${file}" >&2
    exit 2
  fi
done

rg -q '^FROM maven:.*temurin-17' "${ROOT_DIR}/backend/Dockerfile"
rg -q '^USER 999:999$' "${ROOT_DIR}/backend/Dockerfile"
rg -q '^HEALTHCHECK ' "${ROOT_DIR}/backend/Dockerfile"
rg -q '^USER 999:999$' "${ROOT_DIR}/command-runner/Dockerfile"
rg -q '^FROM nginxinc/nginx-unprivileged:' "${ROOT_DIR}/frontend/Dockerfile"
rg -q 'listen 8080;' "${ROOT_DIR}/frontend/nginx.conf"
rg -q 'try_files \$uri \$uri/ /index.html;' "${ROOT_DIR}/frontend/nginx.conf"
rg -q 'proxy_pass http://backend:8080;' "${ROOT_DIR}/frontend/nginx.conf"
rg -q 'proxy_set_header X-Forwarded-Host \$http_host;' "${ROOT_DIR}/frontend/nginx.conf"
rg -F -q 'location /ws/' "${ROOT_DIR}/deploy/helm/aiops/templates/frontend-nginx-config.yaml"
rg -F -q 'proxy_set_header Upgrade $http_upgrade;' "${ROOT_DIR}/deploy/helm/aiops/templates/frontend-nginx-config.yaml"
rg -F -q 'proxy_set_header Connection "upgrade";' "${ROOT_DIR}/deploy/helm/aiops/templates/frontend-nginx-config.yaml"
rg -q 'AIOPS_FRONTEND_PORT:-8088}:8080' "${ROOT_DIR}/compose.yaml"
rg -q 'AIOPS_DATASOURCE_PASSWORD:\s*\$\{AIOPS_DATASOURCE_PASSWORD:\?' "${ROOT_DIR}/compose.yaml"
rg -q 'AIOPS_LOCAL_MASTER_KEY:\s*\$\{AIOPS_LOCAL_MASTER_KEY:\?' "${ROOT_DIR}/compose.yaml"
rg -q 'scripts/init/all-in-one\.sh' "${ROOT_DIR}/scripts/install-local-all-in-one.sh"
rg -q '^wait_for_public_endpoint\(\)' "${ROOT_DIR}/scripts/init/common.sh"
rg -q 'curl --max-time' "${ROOT_DIR}/scripts/init/common.sh"
"${ROOT_DIR}/scripts/validate-component-lifecycle.sh"

if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  AIOPS_DATASOURCE_PASSWORD=packaging-validation \
  AIOPS_LOCAL_MASTER_KEY=packaging-validation \
    docker compose --file "${ROOT_DIR}/compose.yaml" config --quiet
fi

"${ROOT_DIR}/scripts/validate-managed-keycloak.sh"
"${ROOT_DIR}/scripts/test-portal-db-bootstrap.sh"

if ! command -v helm >/dev/null 2>&1; then
  echo "helm is required to validate the all-in-one Kubernetes package." >&2
  exit 4
fi

rendered_local="$(mktemp)"
rendered_production="$(mktemp)"
rendered_external_oidc="$(mktemp)"
trap 'rm -f "${rendered_local}" "${rendered_production}" "${rendered_external_oidc}"' EXIT

helm template aiops "${ROOT_DIR}/deploy/helm/aiops" \
  --namespace aiops-system \
  --values "${ROOT_DIR}/deploy/helm/aiops/values-local.yaml" >"${rendered_local}"

helm template aiops "${ROOT_DIR}/deploy/helm/aiops" \
  --namespace aiops-system \
  --values "${ROOT_DIR}/deploy/helm/aiops/values-production.yaml" \
  --set portal.database.runtimeExistingSecret=aiops-portal-db \
  --set portal.crypto.masterKeyExistingSecret=aiops-portal-master-key \
  --set portal.commandRunner.tokenExistingSecret=aiops-command-runner \
  --set authentication.managedKeycloak.database.runtimeExistingSecret=aiops-keycloak-db \
  --set authentication.managedKeycloak.adminExistingSecret=aiops-keycloak-admin \
  --set authentication.managedKeycloak.clientExistingSecret=aiops-keycloak-client \
  --set authentication.managedKeycloak.initialAdminExistingSecret=aiops-initial-platform-admin \
  --set postgresql.host=postgresql.example.internal \
  --set postgresql.networkCidr=10.20.0.10/32 >"${rendered_production}"

helm template aiops "${ROOT_DIR}/deploy/helm/aiops" \
  --namespace aiops-system \
  --values "${ROOT_DIR}/deploy/helm/aiops/values-production.yaml" \
  --set authentication.mode=external-oidc \
  --set authentication.externalOidc.issuerUri=https://identity.example.invalid/realms/aiops \
  --set authentication.externalOidc.clientExistingSecret=external-oidc-client \
  --set portal.database.runtimeExistingSecret=aiops-portal-db \
  --set portal.crypto.masterKeyExistingSecret=aiops-portal-master-key \
  --set portal.commandRunner.tokenExistingSecret=aiops-command-runner \
  --set postgresql.host=postgresql.example.internal \
  --set postgresql.networkCidr=10.20.0.10/32 >"${rendered_external_oidc}"

for component in backend frontend command-runner; do
  rg -q "app.kubernetes.io/component: ${component}$" "${rendered_local}"
done

local_frontend_service="$(awk 'BEGIN { RS="---" } /kind: Service/ && /name: aiops-frontend/ { print }' "${rendered_local}")"
production_frontend_service="$(awk 'BEGIN { RS="---" } /kind: Service/ && /name: aiops-frontend/ { print }' "${rendered_production}")"
if [[ -z "${local_frontend_service}" ]] || ! grep -q 'type: NodePort' <<<"${local_frontend_service}"; then
  echo "Local package must expose the frontend through NodePort." >&2
  exit 7
fi
if ! grep -q 'nodePort: 30081' <<<"${local_frontend_service}"; then
  echo "Local frontend NodePort must be 30081." >&2
  exit 7
fi
if [[ -z "${production_frontend_service}" ]] || ! grep -q 'type: ClusterIP' <<<"${production_frontend_service}"; then
  echo "Production frontend service must remain ClusterIP-only." >&2
  exit 7
fi
rg -q '^kind: Ingress$' "${rendered_production}"
rg -q 'secretName: aiops-portal-tls' "${rendered_production}"
rg -q 'name: AIOPS_DATASOURCE_PASSWORD' "${rendered_production}"
rg -q 'name: AIOPS_LOCAL_MASTER_KEY' "${rendered_production}"
rg -q 'name: AIOPS_OIDC_CLIENT_SECRET' "${rendered_production}"
rg -q 'name: AIOPS_COMMAND_RUNNER_TOKEN' "${rendered_production}"
rg -q 'image: aiops/command-runner@sha256:' "${rendered_production}"
rg -q 'readOnlyRootFilesystem: true' "${rendered_production}"
rg -q 'topologySpreadConstraints:' "${rendered_production}"
rg -q 'kind: NetworkPolicy' "${rendered_production}"
rg -q 'Content-Security-Policy' "${rendered_production}"
rg -q 'Strict-Transport-Security' "${rendered_production}"
rg -q 'image: aiops/backend@sha256:' "${rendered_production}"
rg -q 'image: aiops/frontend@sha256:' "${rendered_production}"
rg -q 'proxy_set_header X-Forwarded-Host \$http_host;' "${rendered_production}"
rg -q 'runAsUser: 999' "${rendered_production}"
rg -q 'runAsGroup: 999' "${rendered_production}"
rg -q 'AIOPS_OIDC_ISSUER_URI: "https://identity.example.invalid/realms/aiops"' "${rendered_external_oidc}"
if rg -q 'app.kubernetes.io/component: keycloak$' "${rendered_external_oidc}"; then
  echo "External OIDC package rendered the managed Keycloak workload." >&2
  exit 6
fi

if helm template aiops "${ROOT_DIR}/deploy/helm/aiops" \
  --values "${ROOT_DIR}/deploy/helm/aiops/values-production.yaml" \
  --set authentication.publicBaseUrl=http://unsafe.example.invalid \
  --set portal.database.runtimeExistingSecret=aiops-portal-db \
  --set portal.crypto.masterKeyExistingSecret=aiops-portal-master-key \
  --set portal.commandRunner.tokenExistingSecret=aiops-command-runner \
  --set authentication.managedKeycloak.database.runtimeExistingSecret=aiops-keycloak-db \
  --set authentication.managedKeycloak.adminExistingSecret=aiops-keycloak-admin \
  --set authentication.managedKeycloak.clientExistingSecret=aiops-keycloak-client \
  --set authentication.managedKeycloak.initialAdminExistingSecret=aiops-initial-platform-admin \
  --set postgresql.host=postgresql.example.internal \
  --set postgresql.networkCidr=10.20.0.10/32 >/dev/null 2>&1; then
  echo "Production package accepted an insecure publicBaseUrl." >&2
  exit 5
fi

if helm template aiops "${ROOT_DIR}/deploy/helm/aiops" \
  --values "${ROOT_DIR}/deploy/helm/aiops/values-production.yaml" \
  --set portal.frontend.service.type=NodePort \
  --set portal.frontend.service.nodePort=30081 \
  --set portal.database.runtimeExistingSecret=aiops-portal-db \
  --set portal.crypto.masterKeyExistingSecret=aiops-portal-master-key \
  --set portal.commandRunner.tokenExistingSecret=aiops-command-runner \
  --set authentication.managedKeycloak.database.runtimeExistingSecret=aiops-keycloak-db \
  --set authentication.managedKeycloak.adminExistingSecret=aiops-keycloak-admin \
  --set authentication.managedKeycloak.clientExistingSecret=aiops-keycloak-client \
  --set authentication.managedKeycloak.initialAdminExistingSecret=aiops-initial-platform-admin \
  --set postgresql.host=postgresql.example.internal \
  --set postgresql.networkCidr=10.20.0.10/32 >/dev/null 2>&1; then
  echo "Production package accepted a public frontend NodePort." >&2
  exit 8
fi

echo "Packaging contracts passed."
