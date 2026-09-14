#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NAMESPACE="${AIOPS_RUNTIME_NAMESPACE:-aiops-system}"
RELEASE="${AIOPS_RUNTIME_RELEASE:-aiops}"
FRONTEND_URL="${AIOPS_FRONTEND_URL:-http://127.0.0.1:30081}"
POSTGRES_CONTAINER="${AIOPS_POSTGRES_CONTAINER:-postgres-vector}"
POSTGRES_USER="${AIOPS_POSTGRES_USER:-raguser}"
POSTGRES_DATABASE="${AIOPS_POSTGRES_DATABASE:-aiops}"
KEYCLOAK_DATABASE="${AIOPS_KEYCLOAK_DATABASE:-keycloak}"
REQUESTS="${AIOPS_RESILIENCE_REQUESTS:-30}"
CONCURRENCY="${AIOPS_RESILIENCE_CONCURRENCY:-10}"
P95_LIMIT_MS="${AIOPS_RESILIENCE_P95_LIMIT_MS:-2000}"
ARTIFACT_DIR="${ROOT_DIR}/artifacts/resilience"
RESTORE_DATABASE="aiops_restore_probe_$(date +%s)"
KEYCLOAK_RESTORE_DATABASE="keycloak_restore_probe_$(date +%s)"
dump_file=""
keycloak_dump_file=""

if [[ "${AIOPS_RESILIENCE_ENABLED:-false}" != "true" ]]; then
  echo "SKIPPED: operational resilience is not explicitly enabled."
  exit 3
fi

for command in kubectl curl docker jq mvn; do
  command -v "${command}" >/dev/null 2>&1 || { echo "${command} is required." >&2; exit 2; }
done
[[ "$(kubectl config current-context 2>/dev/null || true)" == "${AIOPS_LOCAL_KUBERNETES_CONTEXT:-docker-desktop}" ]] || {
  echo "Refusing resilience drill on an unexpected Kubernetes context." >&2
  exit 2
}
mkdir -p "${ARTIFACT_DIR}"

cleanup() {
  docker exec "${POSTGRES_CONTAINER}" dropdb --if-exists -U "${POSTGRES_USER}" "${RESTORE_DATABASE}" >/dev/null 2>&1 || true
  docker exec "${POSTGRES_CONTAINER}" dropdb --if-exists -U "${POSTGRES_USER}" "${KEYCLOAK_RESTORE_DATABASE}" >/dev/null 2>&1 || true
  if [[ -n "${dump_file}" ]]; then
    rm -f "${dump_file}"
  fi
  if [[ -n "${keycloak_dump_file}" ]]; then
    rm -f "${keycloak_dump_file}"
  fi
}
trap cleanup EXIT

wait_for_portal() {
  local attempt
  for attempt in $(seq 1 30); do
    if curl --fail --silent --show-error "${FRONTEND_URL}/api/auth/me" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "Portal did not recover within 30 seconds." >&2
  return 1
}

echo "[RESILIENCE] measuring bounded concurrent read requests"
times_file="${ARTIFACT_DIR}/read-times-ms.txt"
: >"${times_file}"
export FRONTEND_URL times_file
seq 1 "${REQUESTS}" | xargs -P "${CONCURRENCY}" -I '{}' sh -c '
  elapsed=$(curl --fail --silent --output /dev/null --write-out "%{time_total}" "${FRONTEND_URL}/api/auth/me") || exit 1
  awk -v seconds="${elapsed}" "BEGIN { printf \"%d\\n\", seconds * 1000 }" >>"${times_file}"
'
count="$(wc -l <"${times_file}" | tr -d ' ')"
[[ "${count}" == "${REQUESTS}" ]] || { echo "Only ${count}/${REQUESTS} read probes completed." >&2; exit 4; }
p95="$(sort -n "${times_file}" | awk -v count="${count}" 'NR == int((count * 95 + 99) / 100) { print; exit }')"
[[ -n "${p95}" ]] || { echo "Unable to calculate p95." >&2; exit 4; }
(( p95 <= P95_LIMIT_MS )) || { echo "Read p95 ${p95}ms exceeds ${P95_LIMIT_MS}ms." >&2; exit 4; }

echo "[RESILIENCE] restarting Backend and Keycloak one at a time"
for component in backend keycloak; do
  kubectl -n "${NAMESPACE}" rollout restart "deployment/${RELEASE}-${component}" >/dev/null
  kubectl -n "${NAMESPACE}" rollout status "deployment/${RELEASE}-${component}" --timeout=5m
  wait_for_portal
done

echo "[RESILIENCE] restoring an application dump into an isolated temporary database"
dump_file="$(mktemp "${TMPDIR:-/tmp}/aiops-restore-probe.XXXXXX.dump")"
docker exec "${POSTGRES_CONTAINER}" pg_dump -Fc -U "${POSTGRES_USER}" "${POSTGRES_DATABASE}" >"${dump_file}"
[[ -s "${dump_file}" ]] || { echo "Backup probe did not create a dump." >&2; exit 5; }
source_migrations="$(docker exec "${POSTGRES_CONTAINER}" psql -U "${POSTGRES_USER}" -d "${POSTGRES_DATABASE}" -Atc \
  'select count(*) from flyway_schema_history where success=true')"
docker exec "${POSTGRES_CONTAINER}" createdb -U "${POSTGRES_USER}" "${RESTORE_DATABASE}"
docker exec -i "${POSTGRES_CONTAINER}" pg_restore -U "${POSTGRES_USER}" -d "${RESTORE_DATABASE}" <"${dump_file}"
restored_migrations="$(docker exec "${POSTGRES_CONTAINER}" psql -U "${POSTGRES_USER}" -d "${RESTORE_DATABASE}" -Atc \
  'select count(*) from flyway_schema_history where success=true')"
[[ "${source_migrations}" == "${restored_migrations}" ]] || {
  echo "Restore migration count mismatch: source=${source_migrations}, restored=${restored_migrations}." >&2
  exit 5
}

echo "[RESILIENCE] restoring the managed Keycloak database and realm sentinel"
keycloak_dump_file="$(mktemp "${TMPDIR:-/tmp}/keycloak-restore-probe.XXXXXX.dump")"
docker exec "${POSTGRES_CONTAINER}" pg_dump -Fc -U "${POSTGRES_USER}" "${KEYCLOAK_DATABASE}" >"${keycloak_dump_file}"
[[ -s "${keycloak_dump_file}" ]] || { echo "Keycloak backup probe did not create a dump." >&2; exit 5; }
source_realms="$(docker exec "${POSTGRES_CONTAINER}" psql -U "${POSTGRES_USER}" -d "${KEYCLOAK_DATABASE}" -Atc \
  "select count(*) from realm where name='aiops'")"
[[ "${source_realms}" == "1" ]] || { echo "Source Keycloak aiops realm sentinel is missing." >&2; exit 5; }
docker exec "${POSTGRES_CONTAINER}" createdb -U "${POSTGRES_USER}" "${KEYCLOAK_RESTORE_DATABASE}"
docker exec -i "${POSTGRES_CONTAINER}" pg_restore -U "${POSTGRES_USER}" -d "${KEYCLOAK_RESTORE_DATABASE}" <"${keycloak_dump_file}"
restored_realms="$(docker exec "${POSTGRES_CONTAINER}" psql -U "${POSTGRES_USER}" -d "${KEYCLOAK_RESTORE_DATABASE}" -Atc \
  "select count(*) from realm where name='aiops'")"
[[ "${restored_realms}" == "${source_realms}" ]] || {
  echo "Keycloak realm restore mismatch: source=${source_realms}, restored=${restored_realms}." >&2
  exit 5
}

echo "[RESILIENCE] validating deterministic AI timeout fallback"
(
  cd "${ROOT_DIR}/backend"
  mvn -q -Dmaven.repo.local="${ROOT_DIR}/.m2/repository" \
    -Dtest=AnalysisApiTest#clusterAnalysisFallsBackToKubernetesEvidenceWhenAiTimesOut test
)

cat >"${ARTIFACT_DIR}/result.json" <<EOF
{"status":"PASSED","requests":${REQUESTS},"concurrency":${CONCURRENCY},"p95Ms":${p95},"p95LimitMs":${P95_LIMIT_MS},"restoredMigrations":${restored_migrations},"restoredKeycloakRealms":${restored_realms},"credentials":"REDACTED"}
EOF
echo "Operational resilience gate passed: read p95=${p95}ms."
