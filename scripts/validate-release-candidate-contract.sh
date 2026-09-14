#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GATE="${ROOT_DIR}/scripts/validate-release-candidate.sh"
RESILIENCE_GATE="${ROOT_DIR}/scripts/validate-operational-resilience.sh"
EVIDENCE_COLLECTOR="${ROOT_DIR}/scripts/acceptance/collect-production-evidence.sh"

if [[ ! -s "${GATE}" ]]; then
  echo "Release candidate gate is missing: ${GATE}" >&2
  exit 2
fi

required_stages=(
  validate-component-lifecycle.sh
  validate-local-runtime-contract.sh
  validate-docs.sh
  validate-maintainability.sh
  validate-backend.sh
  validate-openapi.sh
  validate-openapi-snapshot.sh
  validate-generated-client.sh
  validate-frontend.sh
  test:e2e
  validate-ai-release-gate.sh
  validate-packaging.sh
  validate-runtime-convergence.sh
  validate-operational-resilience.sh
  validate-supply-chain.sh
)

for stage in "${required_stages[@]}"; do
  if ! rg -q "${stage}" "${GATE}"; then
    echo "Release candidate gate does not include: ${stage}" >&2
    exit 3
  fi
done

if rg -q 'dump_file="\$\{ARTIFACT_DIR\}' "${RESILIENCE_GATE}"; then
  echo "Resilience gate must not retain a database dump under artifacts/." >&2
  exit 4
fi
rg -q 'dump_file="\$\(mktemp ' "${RESILIENCE_GATE}" || {
  echo "Resilience gate must create the restore dump as a temporary file." >&2
  exit 4
}
rg -q 'rm -f "\$\{dump_file\}"' "${RESILIENCE_GATE}" || {
  echo "Resilience gate must delete the temporary restore dump during cleanup." >&2
  exit 4
}

rg -q 'AIOPS_AUDIT_TIMEOUT_SECONDS' "${ROOT_DIR}/scripts/validate-supply-chain.sh" || {
  echo "Supply-chain audit must have a bounded timeout." >&2
  exit 5
}
rg -q 'kill "KILL", -\$pid' "${ROOT_DIR}/scripts/validate-supply-chain.sh" || {
  echo "Supply-chain timeout must terminate the complete npm audit process group." >&2
  exit 5
}

if rg -q 'jdbc:tc:' "${GATE}"; then
  echo "Packaged RC runtime must not depend on the test-scoped Testcontainers JDBC driver." >&2
  exit 6
fi
rg -q 'postgres:17-alpine' "${GATE}" || {
  echo "Release candidate startup must use an isolated PostgreSQL 17 database." >&2
  exit 6
}
rg -q 'AIOPS_DATASOURCE_DRIVER=org.postgresql.Driver' "${GATE}" || {
  echo "Release candidate runtime must use the production PostgreSQL driver." >&2
  exit 6
}

required_evidence=(
  validate-production-transport.sh
  rehearse-helm-rollback.sh
  run-analysis-soak.sh
  validate-operational-resilience.sh
)
for evidence in "${required_evidence[@]}"; do
  if ! rg -q "${evidence%.sh}" "${EVIDENCE_COLLECTOR}"; then
    echo "Production evidence collector does not gate: ${evidence}" >&2
    exit 7
  fi
done

rg -q 'restoredKeycloakRealms' "${RESILIENCE_GATE}" || {
  echo "Resilience evidence must verify the managed Keycloak database restore." >&2
  exit 8
}
rg -q 'deployedImageEvidence' "${ROOT_DIR}/scripts/validate-supply-chain.sh" || {
  echo "Supply-chain evidence must include deployed image identities." >&2
  exit 9
}
rg -q 'AIOPS_SUPPLY_CHAIN_PRODUCTION' "${ROOT_DIR}/scripts/validate-supply-chain.sh" || {
  echo "Supply-chain gate must expose fail-closed production signature verification." >&2
  exit 9
}
rg -q '"status":"BLOCKED"' "${ROOT_DIR}/scripts/validate-supply-chain.sh" || {
  echo "Supply-chain validation must invalidate stale passing evidence before execution." >&2
  exit 9
}
rg -q 'restoredKeycloakRealms == 1' "${EVIDENCE_COLLECTOR}" || {
  echo "Production evidence must reject restore artifacts without Keycloak realm verification." >&2
  exit 10
}

echo "Release candidate stage contract passed."
