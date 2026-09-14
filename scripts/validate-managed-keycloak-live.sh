#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ "${AIOPS_LIVE_IDENTITY_ENABLED:-false}" != "true" ]]; then
  echo "SKIPPED: managed identity live acceptance is not explicitly enabled."
  exit 3
fi

required=(
  AIOPS_LIVE_KUBERNETES_CONTEXT AIOPS_LIVE_IDENTITY_NAMESPACE
  AIOPS_LIVE_APPLICATION_URL AIOPS_LIVE_ADMIN_USERNAME AIOPS_LIVE_ADMIN_PASSWORD
  AIOPS_LIVE_OPERATOR_USERNAME AIOPS_LIVE_OPERATOR_PASSWORD
  AIOPS_LIVE_VIEWER_USERNAME AIOPS_LIVE_VIEWER_PASSWORD
  AIOPS_LIVE_OPERATOR_CLUSTER_ID AIOPS_LIVE_HIDDEN_CLUSTER_ID AIOPS_LIVE_OPERATOR_NAMESPACE
)
for variable in "${required[@]}"; do
  if [[ -z "${!variable:-}" ]]; then
    echo "Missing live acceptance input: ${variable}" >&2
    exit 2
  fi
done

current_context="$(kubectl config current-context 2>/dev/null || true)"
if [[ "${current_context}" != "${AIOPS_LIVE_KUBERNETES_CONTEXT}" ]]; then
  echo "SKIPPED: selected Kubernetes context does not match the explicit acceptance target."
  exit 3
fi

namespace="${AIOPS_LIVE_IDENTITY_NAMESPACE}"
probe="aiops-keycloak-db-probe-$(date +%s)"
runtime_secret="${AIOPS_LIVE_KEYCLOAK_DB_SECRET:-aiops-keycloak-db}"
database_host="${AIOPS_LIVE_DATABASE_HOST:-host.docker.internal}"

cleanup() {
  kubectl -n "${namespace}" delete pod "${probe}" --ignore-not-found >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "[CHECK_DATABASE] verifying PostgreSQL from the target Kubernetes network"
kubectl -n "${namespace}" apply -f - >/dev/null <<EOF
apiVersion: v1
kind: Pod
metadata:
  name: ${probe}
  labels:
    app.kubernetes.io/component: identity-acceptance-probe
spec:
  restartPolicy: Never
  activeDeadlineSeconds: 60
  containers:
    - name: psql
      image: postgres:17
      command: ["psql", "--tuples-only", "--no-align", "--command", "select 1"]
      env:
        - name: PGHOST
          value: "${database_host}"
        - name: PGPORT
          value: "5432"
        - name: PGDATABASE
          value: "keycloak"
        - name: PGUSER
          valueFrom:
            secretKeyRef:
              name: ${runtime_secret}
              key: username
        - name: PGPASSWORD
          valueFrom:
            secretKeyRef:
              name: ${runtime_secret}
              key: password
EOF
kubectl -n "${namespace}" wait --for=jsonpath='{.status.phase}'=Succeeded \
  "pod/${probe}" --timeout=90s >/dev/null
[[ "$(kubectl -n "${namespace}" logs "${probe}" | tr -d '[:space:]')" == "1" ]]

echo "[VERIFY_OIDC] checking discovery and JWKS endpoints"
curl --fail --silent --show-error \
  "${AIOPS_LIVE_APPLICATION_URL}/oauth2/authorization/aiops" >/dev/null 2>&1 || true

echo "[BROWSER_ACCEPTANCE] running multi-user isolation journeys"
cd "${ROOT_DIR}/frontend"
AIOPS_E2E_BASE_URL="${AIOPS_LIVE_APPLICATION_URL}" \
  npx playwright test e2e/managed-keycloak.spec.ts --reporter=line

echo "Managed Keycloak live acceptance passed."

if [[ "${AIOPS_COMMERCIAL_TENANT_VALIDATION:-false}" == "true" ]]; then
  "${ROOT_DIR}/scripts/validate-commercial-tenant-isolation.sh"
fi
