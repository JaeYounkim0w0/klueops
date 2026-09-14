#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if rg -n "password\\s*=\\s*['\"][^'\"]+['\"]|token\\s*=\\s*['\"][^'\"]+['\"]" "${ROOT_DIR}/backend/src/main" >/dev/null; then
  echo "Potential hard-coded secret found." >&2
  exit 1
fi

if rg -n "localStorage\\.(setItem|getItem).*token|sessionStorage\\.(setItem|getItem).*token" "${ROOT_DIR}/frontend/src" >/dev/null; then
  echo "Browser token storage is forbidden by the BFF security model." >&2
  exit 1
fi

required_docs=(
  "docs/product/current-product-specification.md"
  "docs/security/identity/oidc-bff-architecture.md"
  "docs/security/authorization/rbac-and-scope-model.md"
  "docs/security/operations/keycloak-and-security-configuration.md"
  "docs/security/testing/security-validation-plan.md"
)
for document in "${required_docs[@]}"; do
  if [[ ! -s "${ROOT_DIR}/${document}" ]]; then
    echo "Missing security document: ${document}" >&2
    exit 1
  fi
done

rg -q "spring-boot-starter-oauth2-client" "${ROOT_DIR}/backend/pom.xml"
rg -q "spring-session-jdbc" "${ROOT_DIR}/backend/pom.xml"
rg -q "AUTHENTICATION_REQUIRED" "${ROOT_DIR}/backend/src/main/java/io/strato/aiops/config/SecurityConfig.java"
rg -q "visibleClusterIds" "${ROOT_DIR}/backend/src/main/java/io/strato/aiops/adapter/in/web/ClusterController.java"
rg -q "SESSION_COOKIE" "${ROOT_DIR}/backend/src/main/java/io/strato/aiops/application/service/ProductionReadinessService.java"
rg -q "quay.io/keycloak/keycloak:26.7.3" "${ROOT_DIR}/compose.identity.yaml"
bash -n "${ROOT_DIR}/scripts/bootstrap-keycloak-dev.sh"

managed_identity_files=(
  "keycloak/Dockerfile"
  "deploy/helm/aiops/values.yaml"
  "deploy/helm/aiops/files/bootstrap-keycloak-db.sh"
  "deploy/helm/aiops/files/bootstrap-keycloak-realm.sh"
  "deploy/helm/aiops/templates/keycloak-deployment.yaml"
  "deploy/helm/aiops/templates/backend-oidc-config.yaml"
  "scripts/install-managed-keycloak.sh"
  "scripts/validate-managed-keycloak-live.sh"
)
for file in "${managed_identity_files[@]}"; do
  [[ -s "${ROOT_DIR}/${file}" ]] || { echo "Missing managed identity security artifact: ${file}" >&2; exit 1; }
done

rg -q '^  mode: managed-keycloak$' "${ROOT_DIR}/deploy/helm/aiops/values.yaml"
rg -q 'start --optimized|--optimized' "${ROOT_DIR}/deploy/helm/aiops/templates/keycloak-deployment.yaml"
rg -q 'runtimeExistingSecret:' "${ROOT_DIR}/deploy/helm/aiops/values.yaml"
rg -q '^  publicBaseUrl: https://' "${ROOT_DIR}/deploy/helm/aiops/values-production.yaml"
rg -q '^    publicUrl: https://' "${ROOT_DIR}/deploy/helm/aiops/values-production.yaml"

if rg -n 'start-dev' "${ROOT_DIR}/keycloak" "${ROOT_DIR}/deploy/helm" >/dev/null; then
  echo "Development Keycloak mode is forbidden in packaged Kubernetes artifacts." >&2
  exit 1
fi
if rg -n 'password:[[:space:]]+[^{$[:space:]]' "${ROOT_DIR}/deploy/helm/aiops" >/dev/null; then
  echo "Literal credential detected in Helm artifacts." >&2
  exit 1
fi

"${ROOT_DIR}/scripts/validate-managed-keycloak.sh"

echo "Security smoke check passed."
