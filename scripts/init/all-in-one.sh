#!/usr/bin/env bash
set -euo pipefail

# Orchestrates first installation: prepare four components, then perform one Helm transaction.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"
set_init_defaults
parse_init_args "$@"
if [[ "${SHOW_HELP}" == true ]]; then
  print_init_help "scripts/init/all-in-one.sh" "Prepares PostgreSQL, Keycloak, Backend, Frontend, and the isolated Command Runner before one all-in-one Helm installation."
  exit 0
fi
validate_init_inputs

FRONTEND_URL="${AIOPS_PORTAL_PUBLIC_URL}"
KEYCLOAK_URL="${AIOPS_OIDC_PUBLIC_URL}"

common_args=(--values "${VALUES}" --namespace "${NAMESPACE}" --release "${RELEASE}")
[[ "${DRY_RUN}" == true ]] && common_args+=(--dry-run)
[[ "${SKIP_TESTS}" == true ]] && common_args+=(--skip-tests)
[[ "${SKIP_BUILD}" == true ]] && common_args+=(--skip-build)

stage PREPARE "validating external PostgreSQL"
"${SCRIPT_DIR}/postgresql.sh" "${common_args[@]}"
stage PREPARE "preparing managed Keycloak"
"${SCRIPT_DIR}/keycloak.sh" "${common_args[@]}"

application_args=("${common_args[@]}")
[[ -n "${IMAGE_TAG}" ]] && application_args+=(--tag "${IMAGE_TAG}")
stage PREPARE "preparing Backend"
"${SCRIPT_DIR}/backend.sh" "${application_args[@]}"
stage PREPARE "preparing isolated Command Runner"
"${SCRIPT_DIR}/command-runner.sh" "${application_args[@]}"
stage PREPARE "preparing Frontend"
"${SCRIPT_DIR}/frontend.sh" "${application_args[@]}"

rendered="$(mktemp)"
trap 'rm -f "${rendered}"' EXIT
render_chart "${rendered}"
if [[ "${DRY_RUN}" == true ]]; then
  stage COMPLETE "all-in-one initialization dry-run passed"
  exit 0
fi

ensure_local_cluster
require_command curl
helm_args=(upgrade --install "${RELEASE}" "${AIOPS_CHART}" --namespace "${NAMESPACE}" --values "${VALUES}" --wait --timeout 15m --force-conflicts --rollback-on-failure)
helm_args+=(--set-string "authentication.publicBaseUrl=${AIOPS_PORTAL_PUBLIC_URL}")
helm_args+=(--set-string "authentication.managedKeycloak.publicUrl=${AIOPS_OIDC_PUBLIC_URL}")
helm_args+=(--set "authentication.managedKeycloak.additionalPortalUrls={${AIOPS_ADDITIONAL_PUBLIC_URLS}}")
if [[ -n "${IMAGE_TAG}" ]]; then
  helm_args+=(--set-string "portal.backend.image.tag=${IMAGE_TAG}" --set-string "portal.frontend.image.tag=${IMAGE_TAG}" --set-string "portal.commandRunner.image.tag=${IMAGE_TAG}")
fi
stage INSTALL "installing Backend, Frontend, managed Keycloak, and Command Runner"
helm "${helm_args[@]}"

if [[ "${SKIP_BUILD}" != true ]]; then
  # Local image registries are optional, so same-tag rebuilds need an explicit rollout.
  kubectl -n "${NAMESPACE}" rollout restart \
    "deployment/${RELEASE}-backend" "deployment/${RELEASE}-frontend" "deployment/${RELEASE}-keycloak" >/dev/null
  kubectl -n "${NAMESPACE}" rollout restart "deployment/${RELEASE}-command-runner" >/dev/null
fi
for component in backend frontend keycloak command-runner; do
  kubectl -n "${NAMESPACE}" rollout status "deployment/${RELEASE}-${component}" --timeout=5m
done

AIOPS_RUNTIME_MODE=kubernetes AIOPS_RUNTIME_NAMESPACE="${NAMESPACE}" AIOPS_RUNTIME_RELEASE="${RELEASE}" \
  "${AIOPS_ROOT_DIR}/scripts/validate-runtime-convergence.sh"
wait_for_public_endpoint "Frontend health" "${FRONTEND_URL}/healthz"
wait_for_public_endpoint "Portal session endpoint" "${FRONTEND_URL}/api/auth/me"
wait_for_public_endpoint "Managed Keycloak discovery" \
  "${KEYCLOAK_URL}/realms/aiops/.well-known/openid-configuration"
stage COMPLETE "local all-in-one package verified at ${FRONTEND_URL}"
