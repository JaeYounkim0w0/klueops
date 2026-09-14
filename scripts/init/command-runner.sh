#!/usr/bin/env bash
set -euo pipefail

# Prepares the isolated kubectl runner and its Backend-to-Runner token.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"
set_init_defaults
parse_init_args "$@"
if [[ "${SHOW_HELP}" == true ]]; then
  print_init_help "scripts/init/command-runner.sh" "Builds the isolated Command Runner and creates its internal authentication Secret."
  exit 0
fi
validate_init_inputs
[[ -n "${IMAGE_TAG}" ]] || IMAGE_TAG="0.1.0"

if [[ "${DRY_RUN}" == true ]]; then
  stage COMPLETE "Command Runner initialization plan is valid for aiops/command-runner:${IMAGE_TAG}"
  exit 0
fi

ensure_local_cluster
require_command openssl
if ! secret_has_key aiops-command-runner token; then
  runner_token="$(openssl rand -hex 32)"
  kubectl -n "${NAMESPACE}" create secret generic aiops-command-runner --from-literal="token=${runner_token}" >/dev/null
  unset runner_token
fi
if [[ "${SKIP_TESTS}" != true ]]; then
  run_component_validation command-runner
fi
if [[ "${SKIP_BUILD}" != true ]]; then
  build_component_image command-runner aiops/command-runner "${IMAGE_TAG}"
fi
stage COMPLETE "Command Runner image and internal token prepared"
