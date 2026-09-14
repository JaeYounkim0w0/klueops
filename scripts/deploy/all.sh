#!/usr/bin/env bash
set -euo pipefail

# Deploys Backend, Frontend, Keycloak, and Command Runner images in one Helm revision.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"
set_deploy_defaults
parse_deploy_args "$@"
if [[ "${SHOW_HELP}" == true ]]; then
  print_deploy_help "scripts/deploy/all.sh" "Builds all four runtime images and updates them in one Helm transaction."
  exit 0
fi
validate_deploy_inputs
run_all_deploy
