#!/usr/bin/env bash
set -euo pipefail

MODE="${AIOPS_RUNTIME_MODE:-development}"
BACKEND_URL="${AIOPS_BACKEND_URL:-http://127.0.0.1:8080}"
FRONTEND_URL="${AIOPS_FRONTEND_URL:-http://127.0.0.1:5173}"
NAMESPACE="${AIOPS_RUNTIME_NAMESPACE:-aiops-system}"
RELEASE="${AIOPS_RUNTIME_RELEASE:-aiops}"

http_status() {
  curl --silent --show-error --output /dev/null --write-out '%{http_code}' "$1"
}

require_status() {
  local label="$1"
  local url="$2"
  local expected="$3"
  local actual
  actual="$(http_status "${url}")"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "${label} returned HTTP ${actual}; expected ${expected}: ${url}" >&2
    return 1
  fi
}

validate_development() {
  require_status "Frontend" "${FRONTEND_URL}/" 200
  require_status "Backend readiness" "${BACKEND_URL}/actuator/health/readiness" 200
  require_status "Browser session API" "${BACKEND_URL}/api/auth/me" 200

  local readiness
  readiness="$(curl --fail --silent --show-error "${BACKEND_URL}/actuator/health/readiness")"
  if [[ "${readiness}" != *'"status":"UP"'* ]]; then
    echo "Backend readiness did not report UP: ${readiness}" >&2
    return 1
  fi

  echo "Development runtime convergence passed: frontend=${FRONTEND_URL}, backend=${BACKEND_URL}."
}

validate_kubernetes() {
  command -v kubectl >/dev/null 2>&1 || { echo "kubectl is required for kubernetes mode." >&2; return 2; }

  local component deployment ready desired
  for component in backend frontend keycloak command-runner; do
    deployment="$(kubectl get deployment -n "${NAMESPACE}" \
      -l "app.kubernetes.io/instance=${RELEASE},app.kubernetes.io/component=${component}" \
      -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)"
    if [[ -z "${deployment}" ]]; then
      echo "Missing ${component} Deployment for release ${RELEASE} in namespace ${NAMESPACE}." >&2
      return 1
    fi
    ready="$(kubectl get deployment "${deployment}" -n "${NAMESPACE}" -o jsonpath='{.status.readyReplicas}')"
    desired="$(kubectl get deployment "${deployment}" -n "${NAMESPACE}" -o jsonpath='{.spec.replicas}')"
    ready="${ready:-0}"
    if [[ "${ready}" != "${desired}" ]]; then
      echo "${deployment} is not converged: ready=${ready}, desired=${desired}." >&2
      return 1
    fi
  done

  echo "Kubernetes runtime convergence passed: release=${RELEASE}, namespace=${NAMESPACE}."
}

case "${MODE}" in
  development) validate_development ;;
  kubernetes) validate_kubernetes ;;
  *)
    echo "Unsupported AIOPS_RUNTIME_MODE=${MODE}; use development or kubernetes." >&2
    exit 2
    ;;
esac
