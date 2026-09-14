#!/usr/bin/env bash
set -euo pipefail

# Prepares the external PostgreSQL bootstrap contract; it does not install a database server.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"
set_init_defaults
parse_init_args "$@"
if [[ "${SHOW_HELP}" == true ]]; then
  print_init_help "scripts/init/postgresql.sh" "Validates external PostgreSQL connectivity and creates the bootstrap Secret when absent."
  exit 0
fi
validate_init_inputs

rendered="$(mktemp)"
trap 'rm -f "${rendered}"' EXIT
render_chart "${rendered}"
database_host="$(awk '$0 ~ /name: PGHOST$/ { getline; sub(/^[[:space:]]*value:[[:space:]]*/, ""); gsub(/"/, ""); print; exit }' "${rendered}")"
database_port="$(awk '$0 ~ /name: PGPORT$/ { getline; sub(/^[[:space:]]*value:[[:space:]]*/, ""); gsub(/"/, ""); print; exit }' "${rendered}")"
[[ -n "${database_host}" ]] || { echo "Rendered PostgreSQL host is empty." >&2; exit 3; }
[[ -n "${database_port}" ]] || database_port="5432"

if [[ "${DRY_RUN}" == true ]]; then
  stage COMPLETE "PostgreSQL contract valid for ${database_host}:${database_port}; no server is installed"
  exit 0
fi

ensure_local_cluster
if ! secret_has_key aiops-postgresql-bootstrap username || ! secret_has_key aiops-postgresql-bootstrap password; then
  : "${AIOPS_POSTGRES_BOOTSTRAP_USERNAME:?AIOPS_POSTGRES_BOOTSTRAP_USERNAME is required}"
  : "${AIOPS_POSTGRES_BOOTSTRAP_PASSWORD:?AIOPS_POSTGRES_BOOTSTRAP_PASSWORD is required}"
  kubectl -n "${NAMESPACE}" delete secret aiops-postgresql-bootstrap --ignore-not-found >/dev/null
  kubectl -n "${NAMESPACE}" create secret generic aiops-postgresql-bootstrap \
    --from-literal="username=${AIOPS_POSTGRES_BOOTSTRAP_USERNAME}" \
    --from-literal="password=${AIOPS_POSTGRES_BOOTSTRAP_PASSWORD}" >/dev/null
fi

# pg_isready confirms network reachability without exposing a database credential.
probe_name="aiops-postgresql-probe-$(date +%s)"
kubectl -n "${NAMESPACE}" run "${probe_name}" --rm -i --restart=Never \
  --pod-running-timeout=60s --image=postgres:17 --command -- \
  pg_isready -h "${database_host}" -p "${database_port}" >/dev/null
stage COMPLETE "external PostgreSQL connectivity and bootstrap contract verified"
