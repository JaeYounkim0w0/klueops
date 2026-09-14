#!/usr/bin/env bash

# Shared, non-executable helpers for first-time package initialization.
INIT_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AIOPS_ROOT_DIR="$(cd "${INIT_SCRIPT_DIR}/../.." && pwd)"
AIOPS_CHART="${AIOPS_ROOT_DIR}/deploy/helm/aiops"
source "${AIOPS_ROOT_DIR}/scripts/lib/public-url-config.sh"

stage() {
  printf '[%s] %s\n' "$1" "$2"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || { echo "$1 is required." >&2; exit 2; }
}

set_init_defaults() {
  VALUES="${AIOPS_CHART}/values-local.yaml"
  NAMESPACE="aiops-system"
  RELEASE="aiops"
  EXPECTED_CONTEXT="${AIOPS_LOCAL_KUBERNETES_CONTEXT:-docker-desktop}"
  DRY_RUN=false
  SKIP_TESTS=false
  SKIP_BUILD=false
  IMAGE_TAG=""
  SHOW_HELP=false
  resolve_public_urls
}

parse_init_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --dry-run) DRY_RUN=true; shift ;;
      --skip-tests) SKIP_TESTS=true; shift ;;
      --skip-build) SKIP_BUILD=true; shift ;;
      --tag) IMAGE_TAG="${2:?--tag requires a value}"; shift 2 ;;
      --values) VALUES="${2:?--values requires a path}"; shift 2 ;;
      --namespace) NAMESPACE="${2:?--namespace requires a name}"; shift 2 ;;
      --release) RELEASE="${2:?--release requires a name}"; shift 2 ;;
      --help|-h) SHOW_HELP=true; shift ;;
      *) echo "Unknown option: $1" >&2; return 2 ;;
    esac
  done
}

print_init_help() {
  local command_name="$1"
  local description="$2"
  cat <<EOF
Usage: ${command_name} [options]
  --dry-run          Validate inputs without changing Docker, Kubernetes, or PostgreSQL
  --skip-tests       Skip component verification before image build
  --skip-build       Reuse the component image already present locally
  --tag TAG          Override the component image tag
  --values PATH      Helm values file (default: deploy/helm/aiops/values-local.yaml)
  --namespace NAME   Kubernetes namespace (default: aiops-system)
  --release NAME     Helm release (default: aiops)

${description}
EOF
}

validate_init_inputs() {
  require_command helm
  [[ -s "${VALUES}" ]] || { echo "Values file is missing: ${VALUES}" >&2; exit 2; }
  [[ "${NAMESPACE}" =~ ^[a-z0-9]([-a-z0-9]*[a-z0-9])?$ ]] || { echo "Invalid namespace." >&2; exit 2; }
  [[ "${RELEASE}" =~ ^[a-z0-9]([-a-z0-9]*[a-z0-9])?$ ]] || { echo "Invalid release name." >&2; exit 2; }
  validate_public_urls
  if [[ -n "${IMAGE_TAG}" ]]; then
    [[ "${IMAGE_TAG}" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$ ]] || { echo "Invalid image tag." >&2; exit 2; }
  fi
}

render_chart() {
  local output_file="$1"
  helm template "${RELEASE}" "${AIOPS_CHART}" --namespace "${NAMESPACE}" -f "${VALUES}" \
    --set-string "authentication.publicBaseUrl=${AIOPS_PORTAL_PUBLIC_URL}" \
    --set-string "authentication.managedKeycloak.publicUrl=${AIOPS_OIDC_PUBLIC_URL}" \
    --set "authentication.managedKeycloak.additionalPortalUrls={${AIOPS_ADDITIONAL_PUBLIC_URLS}}" \
    >"${output_file}"
}

ensure_local_cluster() {
  require_command kubectl
  local context
  context="$(kubectl config current-context 2>/dev/null || true)"
  if [[ "${context}" != "${EXPECTED_CONTEXT}" ]]; then
    echo "Refusing local initialization on context '${context:-none}'; expected '${EXPECTED_CONTEXT}'." >&2
    exit 4
  fi
  kubectl get namespace "${NAMESPACE}" >/dev/null 2>&1 || kubectl create namespace "${NAMESPACE}" >/dev/null
}

secret_has_key() {
  local secret_name="$1"
  local key="$2"
  kubectl -n "${NAMESPACE}" get secret "${secret_name}" \
    -o "jsonpath={.data['${key}']}" 2>/dev/null | grep -q '.\+'
}

deployment_for_component() {
  local component="$1"
  local deployment
  deployment="$(kubectl -n "${NAMESPACE}" get deployment \
    -l "app.kubernetes.io/instance=${RELEASE},app.kubernetes.io/component=${component}" \
    -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)"
  if [[ -z "${deployment}" ]]; then
    echo "Missing ${component} Deployment for release ${RELEASE} in namespace ${NAMESPACE}." >&2
    return 1
  fi
  printf '%s\n' "${deployment}"
}

require_secret_key() {
  local secret_name="$1"
  local key="$2"
  secret_has_key "${secret_name}" "${key}" || {
    echo "Required Secret key is missing: ${secret_name}:${key}." >&2
    exit 5
  }
}

build_component_image() {
  local component="$1"
  local repository="$2"
  local tag="$3"
  require_command docker
  stage BUILD "building ${repository}:${tag}"
  docker build -t "${repository}:${tag}" "${AIOPS_ROOT_DIR}/${component}"
}

run_component_validation() {
  local component="$1"
  case "${component}" in
    backend) "${AIOPS_ROOT_DIR}/scripts/validate-backend.sh" ;;
    frontend) "${AIOPS_ROOT_DIR}/scripts/validate-frontend.sh" ;;
    keycloak) "${AIOPS_ROOT_DIR}/scripts/validate-managed-keycloak.sh" ;;
    command-runner) "${AIOPS_ROOT_DIR}/scripts/validate-command-runner.sh" ;;
    *) echo "Unsupported validation component: ${component}" >&2; exit 2 ;;
  esac
}

wait_for_public_endpoint() {
  local label="$1"
  local url="$2"
  local expected_status="${3:-200}"
  local status=""
  local attempt
  for attempt in $(seq 1 30); do
    status="$(curl --max-time 5 --silent --show-error --output /dev/null --write-out '%{http_code}' "${url}" 2>/dev/null || true)"
    if [[ "${status}" == "${expected_status}" ]]; then
      return 0
    fi
    sleep 2
  done
  echo "${label} did not return HTTP ${expected_status}; last status=${status:-none}." >&2
  return 1
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  echo "scripts/init/common.sh is a library and must not be executed directly." >&2
  exit 2
fi
