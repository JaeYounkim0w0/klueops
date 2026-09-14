#!/usr/bin/env bash

# Shared, non-executable helpers for repeatable component image deployments.
DEPLOY_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AIOPS_ROOT_DIR="$(cd "${DEPLOY_SCRIPT_DIR}/../.." && pwd)"
AIOPS_CHART="${AIOPS_ROOT_DIR}/deploy/helm/aiops"
source "${AIOPS_ROOT_DIR}/scripts/lib/public-url-config.sh"
LOCK_DIR=""

stage() {
  printf '[%s] %s\n' "$1" "$2"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || { echo "$1 is required." >&2; exit 2; }
}

set_deploy_defaults() {
  VALUES="${AIOPS_CHART}/values-local.yaml"
  NAMESPACE="aiops-system"
  RELEASE="aiops"
  EXPECTED_CONTEXT="${AIOPS_LOCAL_KUBERNETES_CONTEXT:-docker-desktop}"
  resolve_public_urls
  FRONTEND_URL="${AIOPS_PORTAL_PUBLIC_URL}"
  KEYCLOAK_URL="${AIOPS_OIDC_PUBLIC_URL}"
  DRY_RUN=false
  SKIP_TESTS=false
  SKIP_BUILD=false
  IMAGE_TAG=""
  SHOW_HELP=false
}

parse_deploy_args() {
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

print_deploy_help() {
  local command_name="$1"
  local description="$2"
  cat <<EOF
Usage: ${command_name} [options]
  --dry-run          Print the deployment plan without tests, build, or cluster mutation
  --skip-tests       Skip component verification before image build
  --skip-build       Deploy an existing image; requires --tag
  --tag TAG          Immutable image tag; generated from UTC time and source checksum when omitted
  --values PATH      Values reference used for validation
  --namespace NAME   Kubernetes namespace (default: aiops-system)
  --release NAME     Helm release (default: aiops)

${description}
EOF
}

validate_deploy_inputs() {
  require_command helm
  require_command shasum
  [[ -s "${VALUES}" ]] || { echo "Values file is missing: ${VALUES}" >&2; exit 2; }
  [[ "${NAMESPACE}" =~ ^[a-z0-9]([-a-z0-9]*[a-z0-9])?$ ]] || { echo "Invalid namespace." >&2; exit 2; }
  [[ "${RELEASE}" =~ ^[a-z0-9]([-a-z0-9]*[a-z0-9])?$ ]] || { echo "Invalid release name." >&2; exit 2; }
  validate_public_urls
  if [[ -n "${IMAGE_TAG}" ]]; then
    [[ "${IMAGE_TAG}" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$ ]] || { echo "Invalid image tag." >&2; exit 2; }
    [[ "${IMAGE_TAG}" != "latest" ]] || { echo "The mutable latest tag is not allowed." >&2; exit 2; }
  fi
  if [[ "${SKIP_BUILD}" == true && -z "${IMAGE_TAG}" ]]; then
    echo "--skip-build requires an explicit --tag that already exists." >&2
    exit 2
  fi
}

generate_immutable_tag() {
  local component="$1"
  local timestamp
  local checksum
  timestamp="$(date -u +%Y%m%d%H%M%S)"
  case "${component}" in
    backend)
      checksum="$(find "${AIOPS_ROOT_DIR}/backend/src" "${AIOPS_ROOT_DIR}/backend/pom.xml" -type f -exec shasum {} \; | sort | shasum | awk '{print substr($1,1,10)}')"
      ;;
    frontend)
      checksum="$(find "${AIOPS_ROOT_DIR}/frontend/src" "${AIOPS_ROOT_DIR}/frontend/package.json" "${AIOPS_ROOT_DIR}/frontend/package-lock.json" "${AIOPS_ROOT_DIR}/frontend/nginx.conf" -type f -exec shasum {} \; | sort | shasum | awk '{print substr($1,1,10)}')"
      ;;
    keycloak)
      checksum="$(shasum "${AIOPS_ROOT_DIR}/keycloak/Dockerfile" | awk '{print substr($1,1,10)}')"
      ;;
    command-runner)
      checksum="$(find "${AIOPS_ROOT_DIR}/command-runner/src" "${AIOPS_ROOT_DIR}/command-runner/pom.xml" -type f -exec shasum {} \; | sort | shasum | awk '{print substr($1,1,10)}')"
      ;;
    *) echo "Unsupported component: ${component}" >&2; return 2 ;;
  esac
  printf 'dev-%s-%s\n' "${timestamp}" "${checksum}"
}

acquire_release_lock() {
  LOCK_DIR="${TMPDIR:-/tmp}/aiops-${NAMESPACE}-${RELEASE}.deploy.lock"
  if ! mkdir "${LOCK_DIR}" 2>/dev/null; then
    echo "Another deployment is already changing ${NAMESPACE}/${RELEASE}." >&2
    exit 8
  fi
  trap release_release_lock EXIT
}

release_release_lock() {
  if [[ -n "${LOCK_DIR}" && -d "${LOCK_DIR}" ]]; then
    rmdir "${LOCK_DIR}" >/dev/null 2>&1 || true
  fi
}

ensure_deploy_target() {
  require_command kubectl
  local context
  context="$(kubectl config current-context 2>/dev/null || true)"
  if [[ "${context}" != "${EXPECTED_CONTEXT}" ]]; then
    echo "Refusing deployment on context '${context:-none}'; expected '${EXPECTED_CONTEXT}'." >&2
    exit 4
  fi
  helm status "${RELEASE}" --namespace "${NAMESPACE}" >/dev/null 2>&1 || {
    echo "Helm release ${NAMESPACE}/${RELEASE} is not installed. Run scripts/init/all-in-one.sh first." >&2
    exit 4
  }
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

component_repository() {
  case "$1" in
    backend) printf '%s\n' "${AIOPS_BACKEND_IMAGE_REPOSITORY:-aiops/backend}" ;;
    frontend) printf '%s\n' "${AIOPS_FRONTEND_IMAGE_REPOSITORY:-aiops/frontend}" ;;
    keycloak) printf '%s\n' "${AIOPS_KEYCLOAK_IMAGE_REPOSITORY:-aiops/keycloak}" ;;
    command-runner) printf '%s\n' "${AIOPS_COMMAND_RUNNER_IMAGE_REPOSITORY:-aiops/command-runner}" ;;
    *) return 2 ;;
  esac
}

component_value_prefix() {
  case "$1" in
    backend) printf '%s\n' 'portal.backend.image' ;;
    frontend) printf '%s\n' 'portal.frontend.image' ;;
    keycloak) printf '%s\n' 'authentication.managedKeycloak.image' ;;
    command-runner) printf '%s\n' 'portal.commandRunner.image' ;;
    *) return 2 ;;
  esac
}

build_component_image() {
  local component="$1"
  local repository="$2"
  local tag="$3"
  require_command docker
  stage BUILD "building ${repository}:${tag}"
  docker build -t "${repository}:${tag}" "${AIOPS_ROOT_DIR}/${component}"
}

helm_deploy_component() {
  local component="$1"
  local repository="$2"
  local tag="$3"
  local value_prefix
  value_prefix="$(component_value_prefix "${component}")"
  stage DEPLOY "updating ${component} to ${repository}:${tag}"
  helm upgrade "${RELEASE}" "${AIOPS_CHART}" \
    --namespace "${NAMESPACE}" \
    --reset-then-reuse-values \
    --set-string "${value_prefix}.repository=${repository}" \
    --set-string "${value_prefix}.tag=${tag}" \
    --set-string "${value_prefix}.digest=" \
    --wait --timeout 15m --force-conflicts --rollback-on-failure
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

verify_component_deployment() {
  local component="$1"
  require_command curl
  kubectl -n "${NAMESPACE}" rollout status "deployment/${RELEASE}-${component}" --timeout=5m
  case "${component}" in
    backend)
      wait_for_public_endpoint "Portal session endpoint" "${FRONTEND_URL}/api/auth/me"
      ;;
    frontend)
      wait_for_public_endpoint "Frontend health" "${FRONTEND_URL}/healthz"
      wait_for_public_endpoint "Frontend BFF proxy" "${FRONTEND_URL}/api/auth/me"
      ;;
    keycloak)
      wait_for_public_endpoint "Managed Keycloak discovery" "${KEYCLOAK_URL}/realms/aiops/.well-known/openid-configuration"
      wait_for_public_endpoint "Portal session endpoint" "${FRONTEND_URL}/api/auth/me"
      ;;
    command-runner)
      kubectl -n "${NAMESPACE}" exec "deployment/${RELEASE}-backend" -- \
        sh -c 'curl -fsS -H "X-AIOPS-Runner-Token: $AIOPS_COMMAND_RUNNER_TOKEN" "$AIOPS_COMMAND_RUNNER_URL/internal/v1/capabilities" >/dev/null'
      ;;
  esac
}

run_component_deploy() {
  local component="$1"
  local repository
  local tag
  repository="$(component_repository "${component}")"
  tag="${IMAGE_TAG}"
  [[ -n "${tag}" ]] || tag="$(generate_immutable_tag "${component}")"

  if [[ "${DRY_RUN}" == true ]]; then
    stage PLAN "${component}: test=$([[ "${SKIP_TESTS}" == true ]] && echo skip || echo run), build=$([[ "${SKIP_BUILD}" == true ]] && echo skip || echo run), image=${repository}:${tag}"
    stage PLAN "helm upgrade ${NAMESPACE}/${RELEASE} with only $(component_value_prefix "${component}")"
    return 0
  fi

  ensure_deploy_target
  acquire_release_lock
  if [[ "${SKIP_TESTS}" != true ]]; then
    run_component_validation "${component}"
  fi
  if [[ "${SKIP_BUILD}" != true ]]; then
    build_component_image "${component}" "${repository}" "${tag}"
  fi
  helm_deploy_component "${component}" "${repository}" "${tag}"
  if ! verify_component_deployment "${component}"; then
    echo "Post-deployment verification failed. Inspect 'helm history ${RELEASE} -n ${NAMESPACE}' before a manual rollback." >&2
    return 7
  fi
  stage COMPLETE "${component} deployed as ${repository}:${tag}"
}

run_all_deploy() {
  local backend_repository frontend_repository keycloak_repository runner_repository
  local backend_tag frontend_tag keycloak_tag runner_tag
  backend_repository="$(component_repository backend)"
  frontend_repository="$(component_repository frontend)"
  keycloak_repository="$(component_repository keycloak)"
  runner_repository="$(component_repository command-runner)"
  if [[ -n "${IMAGE_TAG}" ]]; then
    backend_tag="${IMAGE_TAG}"
    frontend_tag="${IMAGE_TAG}"
    keycloak_tag="${IMAGE_TAG}"
    runner_tag="${IMAGE_TAG}"
  else
    backend_tag="$(generate_immutable_tag backend)"
    frontend_tag="$(generate_immutable_tag frontend)"
    keycloak_tag="$(generate_immutable_tag keycloak)"
    runner_tag="$(generate_immutable_tag command-runner)"
  fi

  if [[ "${DRY_RUN}" == true ]]; then
    stage PLAN "backend=${backend_repository}:${backend_tag}"
    stage PLAN "frontend=${frontend_repository}:${frontend_tag}"
    stage PLAN "keycloak=${keycloak_repository}:${keycloak_tag}"
    stage PLAN "command-runner=${runner_repository}:${runner_tag}"
    stage PLAN "one Helm upgrade will update all four image values"
    return 0
  fi

  ensure_deploy_target
  acquire_release_lock
  if [[ "${SKIP_TESTS}" != true ]]; then
    run_component_validation backend
    run_component_validation frontend
    run_component_validation keycloak
    run_component_validation command-runner
  fi
  if [[ "${SKIP_BUILD}" != true ]]; then
    build_component_image backend "${backend_repository}" "${backend_tag}"
    build_component_image frontend "${frontend_repository}" "${frontend_tag}"
    build_component_image keycloak "${keycloak_repository}" "${keycloak_tag}"
    build_component_image command-runner "${runner_repository}" "${runner_tag}"
  fi

  stage DEPLOY "updating Backend, Frontend, Keycloak, and Command Runner in one Helm revision"
  helm upgrade "${RELEASE}" "${AIOPS_CHART}" --namespace "${NAMESPACE}" --reset-then-reuse-values \
    --set-string "portal.backend.image.repository=${backend_repository}" \
    --set-string "portal.backend.image.tag=${backend_tag}" \
    --set-string "portal.backend.image.digest=" \
    --set-string "portal.frontend.image.repository=${frontend_repository}" \
    --set-string "portal.frontend.image.tag=${frontend_tag}" \
    --set-string "portal.frontend.image.digest=" \
    --set-string "authentication.managedKeycloak.image.repository=${keycloak_repository}" \
    --set-string "authentication.managedKeycloak.image.tag=${keycloak_tag}" \
    --set-string "authentication.managedKeycloak.image.digest=" \
    --set-string "portal.commandRunner.image.repository=${runner_repository}" \
    --set-string "portal.commandRunner.image.tag=${runner_tag}" \
    --set-string "portal.commandRunner.image.digest=" \
    --wait --timeout 15m --force-conflicts --rollback-on-failure

  for component in backend frontend keycloak command-runner; do
    verify_component_deployment "${component}" || {
      echo "Post-deployment verification failed. Inspect Helm history before rollback." >&2
      return 7
    }
  done
  stage COMPLETE "all application components deployed in one Helm revision"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  echo "scripts/deploy/common.sh is a library and must not be executed directly." >&2
  exit 2
fi
