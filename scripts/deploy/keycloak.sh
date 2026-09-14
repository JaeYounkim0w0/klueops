#!/usr/bin/env bash
set -euo pipefail

# Validates, builds, and deploys only the optimized managed Keycloak image.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"
set_deploy_defaults
parse_deploy_args "$@"
if [[ "${SHOW_HELP}" == true ]]; then
  print_deploy_help "scripts/deploy/keycloak.sh" "Deploys only authentication.managedKeycloak.image and verifies Realm discovery."
  exit 0
fi
validate_deploy_inputs
run_component_deploy keycloak
