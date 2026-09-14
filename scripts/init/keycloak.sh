#!/usr/bin/env bash
set -euo pipefail

# Prepares the optimized managed Keycloak image and validates its Secret contracts.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"
set_init_defaults
parse_init_args "$@"
if [[ "${SHOW_HELP}" == true ]]; then
  print_init_help "scripts/init/keycloak.sh" "Builds the managed Keycloak image and validates DB, admin, client, and initial-user Secrets."
  exit 0
fi
validate_init_inputs
[[ -n "${IMAGE_TAG}" ]] || IMAGE_TAG="26.7.3-1"

rendered="$(mktemp)"
trap 'rm -f "${rendered}"' EXIT
render_chart "${rendered}"
grep -q 'app.kubernetes.io/component: keycloak$' "${rendered}" || {
  echo "Managed Keycloak is not enabled in the selected values." >&2
  exit 3
}

if [[ "${DRY_RUN}" == true ]]; then
  stage COMPLETE "managed Keycloak initialization plan is valid for aiops/keycloak:${IMAGE_TAG}"
  exit 0
fi

ensure_local_cluster
require_secret_key aiops-keycloak-db username
require_secret_key aiops-keycloak-db password
require_secret_key aiops-keycloak-admin username
require_secret_key aiops-keycloak-admin password
require_secret_key aiops-keycloak-client client-secret
require_secret_key aiops-initial-platform-admin username
require_secret_key aiops-initial-platform-admin password

if [[ "${SKIP_TESTS}" != true ]]; then
  run_component_validation keycloak
fi
if [[ "${SKIP_BUILD}" != true ]]; then
  build_component_image keycloak aiops/keycloak "${IMAGE_TAG}"
fi
stage COMPLETE "managed Keycloak image and contracts prepared"
