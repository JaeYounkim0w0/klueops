#!/usr/bin/env bash
set -euo pipefail

# Collects fail-closed, importable evidence from an installed AIOps environment.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

require_acceptance_command jq
require_acceptance_command curl

RELEASE_NAME="${AIOPS_RELEASE_NAME:-local-candidate}"
ENVIRONMENT="${AIOPS_EVIDENCE_ENVIRONMENT:-local-kubernetes}"
BACKEND_URL="${AIOPS_BACKEND_URL:-http://127.0.0.1:8080}"
KEYCLOAK_URL="${AIOPS_KEYCLOAK_URL:-http://auth.aiops.local:30080}"
OUTPUT_DIR="${AIOPS_EVIDENCE_DIR:-${ACCEPTANCE_ROOT}/artifacts/production-evidence}"
mkdir -p "${OUTPUT_DIR}"

started="$(date +%s)"
status="$(http_status "${BACKEND_URL}/actuator/health/readiness")"
if [[ "${status}" == "200" ]]; then
  record_check RUNTIME BACKEND_READINESS PASSED "Backend readiness" "Spring Boot readiness probe returned successfully." "HTTP 200" "" "$(elapsed_ms "${started}")"
else
  record_check RUNTIME BACKEND_READINESS BLOCKED "Backend readiness" "The backend did not return a successful readiness response." "HTTP ${status:-unreachable}" "Start the backend and verify PostgreSQL/Flyway before retrying." "$(elapsed_ms "${started}")"
fi

started="$(date +%s)"
discovery_status="$(http_status "${KEYCLOAK_URL}/realms/aiops/.well-known/openid-configuration")"
if [[ "${discovery_status}" == "200" ]]; then
  record_check IDENTITY OIDC_DISCOVERY PASSED "OIDC discovery" "The managed identity discovery endpoint is reachable." "HTTP 200" "" "$(elapsed_ms "${started}")"
else
  record_check IDENTITY OIDC_DISCOVERY BLOCKED "OIDC discovery" "The OIDC discovery endpoint is unavailable." "HTTP ${discovery_status:-unreachable}" "Check Keycloak Service, realm bootstrap, host mapping, and TLS routing." "$(elapsed_ms "${started}")"
fi

started="$(date +%s)"
if command -v pg_isready >/dev/null 2>&1 && [[ -n "${AIOPS_DATABASE_HOST:-}" ]]; then
  if pg_isready -h "${AIOPS_DATABASE_HOST}" -p "${AIOPS_DATABASE_PORT:-5432}" -d "${AIOPS_DATABASE_NAME:-aiops}" -U "${AIOPS_DATABASE_USERNAME:-raguser}" >/dev/null 2>&1; then
    record_check DATABASE POSTGRESQL_CONNECTIVITY PASSED "PostgreSQL connectivity" "The configured PostgreSQL database accepts connections." "reachable" "" "$(elapsed_ms "${started}")"
  else
    record_check DATABASE POSTGRESQL_CONNECTIVITY FAILED "PostgreSQL connectivity" "The configured PostgreSQL database rejected or timed out." "unreachable" "Verify network policy, database role, password Secret, and pg_hba.conf." "$(elapsed_ms "${started}")"
  fi
else
  record_check DATABASE POSTGRESQL_CONNECTIVITY BLOCKED "PostgreSQL connectivity" "A live PostgreSQL probe was not executed." "pg_isready or AIOPS_DATABASE_HOST missing" "Install PostgreSQL client tools and provide non-secret connection metadata." "$(elapsed_ms "${started}")"
fi

started="$(date +%s)"
if command -v kubectl >/dev/null 2>&1 && kubectl cluster-info >/dev/null 2>&1; then
  can_read="$(kubectl auth can-i get pods --all-namespaces 2>/dev/null || true)"
  if [[ "${can_read}" == "yes" ]]; then
    record_check KUBERNETES CLUSTER_READ_ACCESS PASSED "Kubernetes read access" "The acceptance identity can inspect workload state." "get pods: yes" "" "$(elapsed_ms "${started}")"
  else
    record_check KUBERNETES CLUSTER_READ_ACCESS FAILED "Kubernetes read access" "The acceptance identity cannot inspect workloads." "get pods: ${can_read:-unknown}" "Grant the documented least-privilege read role before retrying." "$(elapsed_ms "${started}")"
  fi
else
  record_check KUBERNETES CLUSTER_READ_ACCESS BLOCKED "Kubernetes read access" "No reachable kubectl context was available." "not executed" "Select the installed package cluster context and retry." "$(elapsed_ms "${started}")"
fi

started="$(date +%s)"
supply_result="${ACCEPTANCE_ROOT}/artifacts/supply-chain/result.json"
if [[ -s "${supply_result}" ]] && jq -e '.status == "PASSED"' "${supply_result}" >/dev/null 2>&1; then
  record_check SUPPLY_CHAIN RUNTIME_DEPENDENCY_AUDIT PASSED "Runtime dependency audit" "The bounded production dependency audit and SBOM gate passed." "PASSED" "" "$(elapsed_ms "${started}")"
else
  record_check SUPPLY_CHAIN RUNTIME_DEPENDENCY_AUDIT BLOCKED "Runtime dependency audit" "No current passing supply-chain artifact was found." "missing or not passed" "Run scripts/validate-supply-chain.sh and collect evidence again." "$(elapsed_ms "${started}")"
fi

record_artifact_gate() {
  local category="$1" code="$2" title="$3" artifact="$4" action="$5"
  local pass_predicate="${6:-.status == \"PASSED\"}"
  local started state observed
  started="$(date +%s)"
  if [[ ! -s "${artifact}" ]]; then
    state="BLOCKED"
    observed="missing"
  else
    if jq -e "${pass_predicate}" "${artifact}" >/dev/null 2>&1; then
      state="PASSED"; observed="PASSED"
    elif [[ "$(jq -r '.status // "BLOCKED"' "${artifact}" 2>/dev/null || echo BLOCKED)" == "FAILED" ]]; then
      state="FAILED"; observed="FAILED"
    else
      state="BLOCKED"; observed="BLOCKED"
    fi
  fi
  record_check "${category}" "${code}" "${state}" "${title}" \
    "The dedicated acceptance probe result is included in this release decision." "${observed}" "${action}" \
    "$(elapsed_ms "${started}")"
}

record_artifact_gate IDENTITY HTTPS_OIDC_TRANSPORT "HTTPS and OIDC callback" \
  "${OUTPUT_DIR}/transport.json" "Run scripts/acceptance/validate-production-transport.sh with public HTTPS URLs."
record_artifact_gate RESILIENCE HELM_ROLLBACK_REHEARSAL "Helm rollback rehearsal" \
  "${OUTPUT_DIR}/helm-rollback.json" "Run scripts/acceptance/rehearse-helm-rollback.sh in an approved non-production environment."
record_artifact_gate PERFORMANCE AI_ANALYSIS_SOAK "AI analysis soak" \
  "${OUTPUT_DIR}/analysis-soak.json" "Run scripts/acceptance/run-analysis-soak.sh against a representative large cluster."
record_artifact_gate RESILIENCE APPLICATION_DATABASE_RESTORE "Application and identity restore" \
  "${ACCEPTANCE_ROOT}/artifacts/resilience/result.json" "Run scripts/validate-operational-resilience.sh with restore testing enabled." \
  '.status == "PASSED" and .restoredKeycloakRealms == 1'

payload="$(jq -n --arg releaseName "${RELEASE_NAME}" --arg environment "${ENVIRONMENT}" \
  --argjson checks "${ACCEPTANCE_CHECKS}" '{releaseName:$releaseName,environment:$environment,checks:$checks}')"
output="${OUTPUT_DIR}/${RELEASE_NAME}-$(date -u +%Y%m%dT%H%M%SZ).json"
printf '%s\n' "${payload}" >"${output}"

if [[ "${AIOPS_EVIDENCE_IMPORT:-false}" == "true" ]]; then
  auth_args=()
  [[ -n "${AIOPS_EVIDENCE_BEARER_TOKEN:-}" ]] && auth_args=(-H "Authorization: Bearer ${AIOPS_EVIDENCE_BEARER_TOKEN}")
  [[ -n "${AIOPS_EVIDENCE_COOKIE_FILE:-}" ]] && auth_args=(-b "${AIOPS_EVIDENCE_COOKIE_FILE}")
  import_key="$(shasum -a 256 "${output}" | awk '{print $1}')"
  curl --fail --max-time 15 --silent --show-error "${auth_args[@]}" \
    -H 'Content-Type: application/json' -H "Idempotency-Key: ${import_key}" -X POST \
    --data-binary "@${output}" "${BACKEND_URL}/api/operations/production-evidence/runs" >/dev/null
fi

echo "Production evidence written to ${output}."
if jq -e '.checks[] | select(.state != "PASSED")' "${output}" >/dev/null; then
  echo "Evidence contains FAILED or BLOCKED checks; production approval is not permitted." >&2
  exit 6
fi
