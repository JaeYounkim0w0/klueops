#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHART="${ROOT_DIR}/deploy/helm/aiops"
VALUES="${CHART}/values-local.yaml"
NAMESPACE="aiops-system"
RELEASE="aiops"
DRY_RUN=false
CREATE_TEMPORARY_BOOTSTRAP=false
TEMP_BOOTSTRAP_SECRET=""
PORT_FORWARD_PID=""
SENSITIVE_DIR=""
RENDERED=""

usage() {
  cat <<'EOF'
Usage: install-managed-keycloak.sh [options]
  --dry-run                         Validate and render without cluster access
  --values PATH                    Helm values file
  --namespace NAME                 Kubernetes namespace (default: aiops-system)
  --release NAME                   Helm release (default: aiops)
  --create-temporary-bootstrap     Prompt for temporary PostgreSQL bootstrap credentials
EOF
}

stage() {
  printf '[%s] %s\n' "$1" "$2"
}

cleanup() {
  local exit_code=$?
  trap - EXIT
  if [[ -n "${PORT_FORWARD_PID}" ]]; then
    kill "${PORT_FORWARD_PID}" >/dev/null 2>&1 || true
    wait "${PORT_FORWARD_PID}" >/dev/null 2>&1 || true
  fi
  if [[ -n "${TEMP_BOOTSTRAP_SECRET}" ]] && command -v kubectl >/dev/null 2>&1; then
    kubectl -n "${NAMESPACE}" delete secret "${TEMP_BOOTSTRAP_SECRET}" --ignore-not-found >/dev/null 2>&1 || true
  fi
  [[ -z "${SENSITIVE_DIR}" ]] || rm -rf "${SENSITIVE_DIR}"
  [[ -z "${RENDERED}" ]] || rm -f "${RENDERED}"
  return "${exit_code}"
}
trap cleanup EXIT

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY_RUN=true; shift ;;
    --values) VALUES="${2:?--values requires a path}"; shift 2 ;;
    --namespace) NAMESPACE="${2:?--namespace requires a name}"; shift 2 ;;
    --release) RELEASE="${2:?--release requires a name}"; shift 2 ;;
    --create-temporary-bootstrap) CREATE_TEMPORARY_BOOTSTRAP=true; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

stage VALIDATE_INPUT "validating chart inputs"
command -v helm >/dev/null 2>&1 || { echo "Helm 3 is required." >&2; exit 2; }
[[ -s "${VALUES}" ]] || { echo "Values file is missing." >&2; exit 2; }
[[ "${NAMESPACE}" =~ ^[a-z0-9]([-a-z0-9]*[a-z0-9])?$ ]] || { echo "Invalid namespace." >&2; exit 2; }
[[ "${RELEASE}" =~ ^[a-z0-9]([-a-z0-9]*[a-z0-9])?$ ]] || { echo "Invalid release name." >&2; exit 2; }

RENDERED="$(mktemp)"
helm template "${RELEASE}" "${CHART}" --namespace "${NAMESPACE}" -f "${VALUES}" \
  --set portal.enabled=false >"${RENDERED}"
mode="external-oidc"
if rg -q 'app.kubernetes.io/component: keycloak$' "${RENDERED}"; then
  mode="managed-keycloak"
fi
database_host="$(awk '
  $0 ~ /name: PGHOST$/ { getline; sub(/^[[:space:]]*value:[[:space:]]*/, ""); gsub(/"/, ""); print; exit }
' "${RENDERED}")"
[[ -n "${database_host}" ]] || database_host="not-applicable"

if [[ "${DRY_RUN}" == true ]]; then
  stage VALIDATE_INPUT "authentication.mode=${mode}"
  stage CHECK_DATABASE "postgresql.host=${database_host}"
  stage COMPLETE "render validation passed"
  exit 0
fi

command -v kubectl >/dev/null 2>&1 || { echo "kubectl is required." >&2; exit 2; }
stage CHECK_CLUSTER "checking current Kubernetes context"
context="$(kubectl config current-context 2>/dev/null || true)"
[[ -n "${context}" ]] || { echo "No Kubernetes context is selected." >&2; exit 3; }
kubectl get namespace "${NAMESPACE}" >/dev/null 2>&1 || kubectl create namespace "${NAMESPACE}" >/dev/null

helm_overrides=()
if [[ "${CREATE_TEMPORARY_BOOTSTRAP}" == true ]]; then
  SENSITIVE_DIR="$(mktemp -d)"
  chmod 700 "${SENSITIVE_DIR}"
  read -r -p 'PostgreSQL bootstrap user: ' bootstrap_user
  read -r -s -p 'PostgreSQL bootstrap credential: ' bootstrap_value
  printf '\n'
  printf '%s' "${bootstrap_user}" >"${SENSITIVE_DIR}/username"
  printf '%s' "${bootstrap_value}" >"${SENSITIVE_DIR}/password"
  unset bootstrap_user bootstrap_value
  chmod 600 "${SENSITIVE_DIR}/username" "${SENSITIVE_DIR}/password"
  TEMP_BOOTSTRAP_SECRET="${RELEASE}-db-bootstrap-$(date +%s)"
  kubectl -n "${NAMESPACE}" create secret generic "${TEMP_BOOTSTRAP_SECRET}" \
    --from-file="${SENSITIVE_DIR}/username" --from-file="${SENSITIVE_DIR}/password" >/dev/null
  helm_overrides+=(--set-string "postgresql.bootstrapExistingSecret=${TEMP_BOOTSTRAP_SECRET}")
fi

stage CHECK_DATABASE "checking required Kubernetes Secret contracts"
while IFS='|' read -r secret_name secret_key; do
  [[ -n "${secret_name}" && -n "${secret_key}" ]] || continue
  if ! kubectl -n "${NAMESPACE}" get secret "${secret_name}" \
      -o "jsonpath={.data['${secret_key}']}" 2>/dev/null | rg -q '.+'; then
    echo "Required credential Secret or key is missing: ${secret_name}." >&2
    exit 4
  fi
done < <(awk '
  /secretKeyRef:/ { in_ref=1; name=""; key=""; next }
  in_ref && $1 == "name:" { name=$2; gsub(/"/, "", name); next }
  in_ref && $1 == "key:" { key=$2; gsub(/"/, "", key); print name "|" key; in_ref=0 }
' "${RENDERED}" | sort -u)

stage BOOTSTRAP_DATABASE "running idempotent database pre-install hook"
stage INSTALL_KEYCLOAK "installing managed identity workload"
helm_arguments=(
  upgrade --install "${RELEASE}" "${CHART}"
  --namespace "${NAMESPACE}"
  -f "${VALUES}"
  --set portal.enabled=false
)
if [[ ${#helm_overrides[@]} -gt 0 ]]; then
  helm_arguments+=("${helm_overrides[@]}")
fi
helm_arguments+=(
  --wait
  --timeout 10m
  --force-conflicts
  --rollback-on-failure
)
helm "${helm_arguments[@]}"

stage BOOTSTRAP_REALM "waiting for idempotent Realm post-install hook"
kubectl -n "${NAMESPACE}" rollout status "deployment/${RELEASE}-keycloak" --timeout=5m

stage INSTALL_BACKEND "verifying Backend OIDC configuration"
kubectl -n "${NAMESPACE}" get configmap "${RELEASE}-backend-oidc" >/dev/null

stage VERIFY_OIDC "checking Realm discovery through the cluster service"
local_port="${AIOPS_KEYCLOAK_VERIFY_PORT:-18081}"
kubectl -n "${NAMESPACE}" port-forward "service/${RELEASE}-keycloak" \
  "${local_port}:8080" >/dev/null 2>&1 &
PORT_FORWARD_PID=$!
for _ in $(seq 1 30); do
  if curl --fail --silent --show-error \
      "http://127.0.0.1:${local_port}/realms/aiops/.well-known/openid-configuration" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
curl --fail --silent --show-error \
  "http://127.0.0.1:${local_port}/realms/aiops/.well-known/openid-configuration" >/dev/null

stage COMPLETE "managed identity installation verified"
