#!/usr/bin/env bash
set -euo pipefail

# Tests, builds, and deploys only the Vue/nginx Frontend image.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"
set_deploy_defaults
parse_deploy_args "$@"
if [[ "${SHOW_HELP}" == true ]]; then
  print_deploy_help "scripts/deploy/frontend.sh" "Deploys only portal.frontend.image in the existing Helm release."
  exit 0
fi
validate_deploy_inputs
run_component_deploy frontend
