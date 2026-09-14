#!/usr/bin/env bash
set -euo pipefail

# Prepares the Vue/nginx image and verifies the single-origin BFF proxy contract.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"
set_init_defaults
parse_init_args "$@"
if [[ "${SHOW_HELP}" == true ]]; then
  print_init_help "scripts/init/frontend.sh" "Validates the Frontend proxy contract and builds the unprivileged nginx image."
  exit 0
fi
validate_init_inputs
[[ -n "${IMAGE_TAG}" ]] || IMAGE_TAG="0.1.0"

rendered="$(mktemp)"
trap 'rm -f "${rendered}"' EXIT
render_chart "${rendered}"
grep -q 'app.kubernetes.io/component: frontend$' "${rendered}" || { echo "Frontend is not rendered." >&2; exit 3; }
grep -q 'location /api/' "${AIOPS_ROOT_DIR}/frontend/nginx.conf" || { echo "Frontend nginx /api proxy is missing." >&2; exit 3; }
grep -q 'location = /healthz' "${AIOPS_ROOT_DIR}/frontend/nginx.conf" || { echo "Frontend nginx health endpoint is missing." >&2; exit 3; }

if [[ "${DRY_RUN}" == true ]]; then
  stage COMPLETE "Frontend initialization plan is valid for aiops/frontend:${IMAGE_TAG}"
  exit 0
fi

ensure_local_cluster
if [[ "${SKIP_TESTS}" != true ]]; then
  run_component_validation frontend
fi
if [[ "${SKIP_BUILD}" != true ]]; then
  build_component_image frontend aiops/frontend "${IMAGE_TAG}"
fi
stage COMPLETE "Frontend image and proxy contract prepared"
