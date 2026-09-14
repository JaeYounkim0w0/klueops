#!/usr/bin/env bash
set -euo pipefail

# Prepares the Spring Boot image and runtime Secret contracts for first installation.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"
set_init_defaults
parse_init_args "$@"
if [[ "${SHOW_HELP}" == true ]]; then
  print_init_help "scripts/init/backend.sh" "Validates Backend configuration and builds the Java 17 runtime image."
  exit 0
fi
validate_init_inputs
[[ -n "${IMAGE_TAG}" ]] || IMAGE_TAG="0.1.0"

rendered="$(mktemp)"
trap 'rm -f "${rendered}"' EXIT
render_chart "${rendered}"
grep -q 'app.kubernetes.io/component: backend$' "${rendered}" || { echo "Backend is not rendered." >&2; exit 3; }

if [[ "${DRY_RUN}" == true ]]; then
  stage COMPLETE "Backend initialization plan is valid for aiops/backend:${IMAGE_TAG}"
  exit 0
fi

ensure_local_cluster
require_command openssl
if ! secret_has_key aiops-portal-db username || ! secret_has_key aiops-portal-db password; then
  : "${AIOPS_PORTAL_DB_USERNAME:?AIOPS_PORTAL_DB_USERNAME is required}"
  : "${AIOPS_PORTAL_DB_PASSWORD:?AIOPS_PORTAL_DB_PASSWORD is required}"
  kubectl -n "${NAMESPACE}" delete secret aiops-portal-db --ignore-not-found >/dev/null
  kubectl -n "${NAMESPACE}" create secret generic aiops-portal-db \
    --from-literal="username=${AIOPS_PORTAL_DB_USERNAME}" \
    --from-literal="password=${AIOPS_PORTAL_DB_PASSWORD}" >/dev/null
fi
if ! secret_has_key aiops-portal-master-key master-key; then
  master_key="$(openssl rand -hex 32)"
  kubectl -n "${NAMESPACE}" create secret generic aiops-portal-master-key \
    --from-literal="master-key=${master_key}" >/dev/null
  unset master_key
fi

if [[ "${SKIP_TESTS}" != true ]]; then
  run_component_validation backend
fi
if [[ "${SKIP_BUILD}" != true ]]; then
  build_component_image backend aiops/backend "${IMAGE_TAG}"
fi
stage COMPLETE "Backend image and contracts prepared"
