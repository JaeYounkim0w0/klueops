#!/usr/bin/env bash
set -euo pipefail

# Static contract for first-install and repeat-deployment command boundaries.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

required_commands=(
  scripts/init/postgresql.sh
  scripts/init/keycloak.sh
  scripts/init/backend.sh
  scripts/init/frontend.sh
  scripts/init/all-in-one.sh
  scripts/deploy/backend.sh
  scripts/deploy/frontend.sh
  scripts/deploy/keycloak.sh
  scripts/deploy/all.sh
)

required_libraries=(
  scripts/init/common.sh
  scripts/deploy/common.sh
)

for relative_path in "${required_commands[@]}"; do
  command_path="${ROOT_DIR}/${relative_path}"
  [[ -x "${command_path}" ]] || {
    echo "Lifecycle command is missing or not executable: ${relative_path}" >&2
    exit 2
  }
  help_output="$("${command_path}" --help)"
  for option in --dry-run --skip-tests --skip-build --tag --values --namespace --release; do
    grep -Fq -- "${option}" <<<"${help_output}" || {
      echo "${relative_path} help is missing ${option}." >&2
      exit 3
    }
  done
done

for relative_path in "${required_libraries[@]}"; do
  [[ -s "${ROOT_DIR}/${relative_path}" ]] || {
    echo "Lifecycle library is missing: ${relative_path}" >&2
    exit 2
  }
done

wrapper="${ROOT_DIR}/scripts/install-local-all-in-one.sh"
rg -q 'exec .*scripts/init/all-in-one\.sh.*"\$@"' "${wrapper}" || {
  echo "The legacy all-in-one installer must delegate every argument to scripts/init/all-in-one.sh." >&2
  exit 4
}

deploy_common="${ROOT_DIR}/scripts/deploy/common.sh"
for function_name in generate_immutable_tag acquire_release_lock wait_for_public_endpoint; do
  rg -q "^${function_name}\\(\\)" "${deploy_common}" || {
    echo "Deploy common library is missing ${function_name}()." >&2
    exit 5
  }
done
rg -q -- '--reset-then-reuse-values' "${deploy_common}" || {
  echo "Component deployment must merge current chart defaults with installed Helm overrides." >&2
  exit 5
}
rg -q -- '--rollback-on-failure' "${deploy_common}" || {
  echo "Component deployment must roll back a failed Helm transaction." >&2
  exit 5
}

for directory in "${ROOT_DIR}/scripts/init" "${ROOT_DIR}/scripts/deploy"; do
  if rg -n 'set -x|echo .*PASSWORD|printf .*PASSWORD|echo .*SECRET|printf .*SECRET' "${directory}"; then
    echo "Lifecycle scripts must not enable command tracing or print secret variables." >&2
    exit 6
  fi
done

rg -q -- '--pod-running-timeout=60s' "${ROOT_DIR}/scripts/init/postgresql.sh" || {
  echo "PostgreSQL connectivity probing must have a bounded Pod startup wait." >&2
  exit 7
}

init_common="${ROOT_DIR}/scripts/init/common.sh"
init_all="${ROOT_DIR}/scripts/init/all-in-one.sh"
rg -q '^deployment_for_component\(\)' "${init_common}" || {
  echo "Initial installation must discover chart resources by release/component labels." >&2
  exit 8
}
rg -q 'deployment_for_component' "${init_all}" || {
  echo "All-in-one rollout verification must support release names that do not contain the chart name." >&2
  exit 8
}
if rg -q 'deployment/\$\{RELEASE\}-(backend|frontend|keycloak|command-runner)' "${init_all}"; then
  echo "All-in-one installation must not assume a fixed Helm fullname." >&2
  exit 8
fi

echo "Component lifecycle contract passed."
