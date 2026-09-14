#!/usr/bin/env bash
set -euo pipefail

# Tests, builds, and deploys only the isolated kubectl Command Runner image.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"
set_deploy_defaults
parse_deploy_args "$@"
if [[ "${SHOW_HELP}" == true ]]; then
  print_deploy_help "scripts/deploy/command-runner.sh" "Deploys only portal.commandRunner.image in the existing Helm release."
  exit 0
fi
validate_deploy_inputs
run_component_deploy command-runner
