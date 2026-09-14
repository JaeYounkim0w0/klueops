#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="${ROOT_DIR}/backend"
ARTIFACT_DIR="${ROOT_DIR}/artifacts/rc"
RC_PORT="${AIOPS_RC_BACKEND_PORT:-18081}"
RC_URL="http://127.0.0.1:${RC_PORT}"
BACKEND_PID=""
RC_DB_CONTAINER="aiops-rc-postgres-$$"
RC_DB_PORT=""
RC_DB_PASSWORD="rc-${RANDOM}-validation"
TOTAL_STARTED="$(date +%s)"

cleanup() {
  if [[ -n "${BACKEND_PID}" ]] && kill -0 "${BACKEND_PID}" 2>/dev/null; then
    kill "${BACKEND_PID}"
    wait "${BACKEND_PID}" 2>/dev/null || true
  fi
  docker rm -f "${RC_DB_CONTAINER}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

run_stage() {
  local label="$1"
  shift
  local started
  started="$(date +%s)"
  echo
  echo "[RC] ${label}"
  "$@"
  echo "[RC] ${label} passed in $(( $(date +%s) - started ))s"
}

preflight() {
  local command
  for command in curl docker java mvn npm node helm; do
    command -v "${command}" >/dev/null 2>&1 || {
      echo "RC prerequisite is missing: ${command}" >&2
      return 2
    }
  done

  docker info >/dev/null 2>&1 || {
    echo "Docker is unavailable to the RC process. Start Docker and expose its socket to Testcontainers." >&2
    return 3
  }
}

package_backend() {
  cd "${BACKEND_DIR}"
  mvn -q -Dmaven.repo.local="${ROOT_DIR}/.m2/repository" -DskipTests package
}

start_database() {
  docker run --detach --rm --name "${RC_DB_CONTAINER}" \
    --env POSTGRES_DB=aiops_rc \
    --env POSTGRES_USER=aiops_rc \
    --env "POSTGRES_PASSWORD=${RC_DB_PASSWORD}" \
    --publish 127.0.0.1::5432 \
    postgres:17-alpine >/dev/null

  for _ in $(seq 1 60); do
    if docker exec "${RC_DB_CONTAINER}" pg_isready --username aiops_rc --dbname aiops_rc >/dev/null 2>&1; then
      RC_DB_PORT="$(docker port "${RC_DB_CONTAINER}" 5432/tcp | awk -F: 'NR == 1 {print $NF}')"
      [[ -n "${RC_DB_PORT}" ]] && return 0
    fi
    sleep 1
  done

  docker logs --tail 120 "${RC_DB_CONTAINER}" >&2 || true
  echo "RC PostgreSQL did not become ready within 60 seconds." >&2
  return 5
}

start_backend() {
  mkdir -p "${ARTIFACT_DIR}"
  local jar
  jar="$(find "${BACKEND_DIR}/target" -maxdepth 1 -name 'klueops-backend-*.jar' ! -name '*.original' | head -n 1)"
  if [[ -z "${jar}" ]]; then
    echo "Packaged backend jar was not found." >&2
    return 2
  fi

  if [[ -z "${RC_DB_PORT}" ]]; then
    echo "RC PostgreSQL port is unavailable." >&2
    return 5
  fi

  AIOPS_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${RC_DB_PORT}/aiops_rc" \
  AIOPS_DATASOURCE_USERNAME=aiops_rc \
  AIOPS_DATASOURCE_PASSWORD="${RC_DB_PASSWORD}" \
  AIOPS_DATASOURCE_DRIVER=org.postgresql.Driver \
  AIOPS_LOCAL_MASTER_KEY=rc-validation-master-key \
    java -jar "${jar}" --server.port="${RC_PORT}" >"${ARTIFACT_DIR}/backend.log" 2>&1 &
  BACKEND_PID="$!"

  for _ in $(seq 1 60); do
    if curl --fail --silent "${RC_URL}/actuator/health/readiness" >/dev/null 2>&1; then
      return 0
    fi
    if ! kill -0 "${BACKEND_PID}" 2>/dev/null; then
      tail -n 120 "${ARTIFACT_DIR}/backend.log" >&2
      return 3
    fi
    sleep 1
  done

  tail -n 120 "${ARTIFACT_DIR}/backend.log" >&2
  echo "RC backend did not become ready within 60 seconds." >&2
  return 4
}

validate_runtime_contracts() {
  AIOPS_BACKEND_URL="${RC_URL}" "${ROOT_DIR}/scripts/validate-openapi-snapshot.sh"
  AIOPS_BACKEND_URL="${RC_URL}" "${ROOT_DIR}/scripts/validate-ai-release-gate.sh"
}

run_stage "Toolchain preflight" preflight
run_stage "Component lifecycle" "${ROOT_DIR}/scripts/validate-component-lifecycle.sh"
run_stage "Local runtime secret contract" "${ROOT_DIR}/scripts/validate-local-runtime-contract.sh"
run_stage "Open-source tree hygiene" "${ROOT_DIR}/scripts/validate-open-source-hygiene.sh"
run_stage "Documentation" "${ROOT_DIR}/scripts/validate-docs.sh"
run_stage "Maintainability guard" "${ROOT_DIR}/scripts/validate-maintainability.sh"
run_stage "Security and managed identity" "${ROOT_DIR}/scripts/validate-security.sh"
run_stage "Backend tests" "${ROOT_DIR}/scripts/validate-backend.sh"
run_stage "OpenAPI annotations and contract" "${ROOT_DIR}/scripts/validate-openapi.sh"
run_stage "Generated API client" "${ROOT_DIR}/scripts/validate-generated-client.sh"
run_stage "Frontend unit, type and build" "${ROOT_DIR}/scripts/validate-frontend.sh"
run_stage "Browser E2E" npm --prefix "${ROOT_DIR}/frontend" run test:e2e
run_stage "Packaging" "${ROOT_DIR}/scripts/validate-packaging.sh"
run_stage "Supply-chain runtime dependencies" "${ROOT_DIR}/scripts/validate-supply-chain.sh"
if [[ "${AIOPS_LIVE_IDENTITY_ENABLED:-false}" == "true" ]]; then
  run_stage "Managed identity live acceptance" "${ROOT_DIR}/scripts/validate-managed-keycloak-live.sh"
else
  echo "[RC] Managed identity live acceptance SKIPPED; multi-user production label is not permitted."
fi
run_stage "Backend package" package_backend
run_stage "Isolated RC PostgreSQL" start_database
run_stage "RC backend startup" start_backend
run_stage "OpenAPI snapshot and AI release gate" validate_runtime_contracts
if [[ "${AIOPS_RUNTIME_CONVERGENCE_ENABLED:-false}" == "true" ]]; then
  run_stage "Runtime convergence" "${ROOT_DIR}/scripts/validate-runtime-convergence.sh"
else
  echo "[RC] Runtime convergence SKIPPED; set AIOPS_RUNTIME_CONVERGENCE_ENABLED=true for a running environment."
fi
if [[ "${AIOPS_RESILIENCE_ENABLED:-false}" == "true" ]]; then
  run_stage "Operational resilience" "${ROOT_DIR}/scripts/validate-operational-resilience.sh"
else
  echo "[RC] Operational resilience SKIPPED; production resilience approval is not permitted."
fi

echo
echo "Release candidate validation passed in $(( $(date +%s) - TOTAL_STARTED ))s."
