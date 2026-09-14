#!/usr/bin/env bash
set -euo pipefail

# Runs the four release workstreams as one fail-closed product decision.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
ARTIFACT_DIR="${AIOPS_READINESS_DIR:-${ROOT_DIR}/artifacts/product-readiness}"
BACKEND_URL="${AIOPS_BACKEND_URL:-http://127.0.0.1:30081}"
FRONTEND_URL="${AIOPS_FRONTEND_URL:-http://127.0.0.1:30081}"
RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)"
REPORT="${ARTIFACT_DIR}/product-readiness-${RUN_ID}.json"
LOG_DIR="${ARTIFACT_DIR}/logs/${RUN_ID}"
mkdir -p "${LOG_DIR}"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required to run product readiness." >&2
  exit 2
fi

CHECKS='[]'
run_check() {
  local group="$1" code="$2" title="$3" classification="$4"
  shift 4
  local log_file="${LOG_DIR}/${code}.log"
  local started ended exit_code status
  started="$(date +%s)"
  set +e
  (cd "${ROOT_DIR}" && "$@") >"${log_file}" 2>&1
  exit_code=$?
  set -e
  ended="$(date +%s)"
  if [[ "${exit_code}" -eq 0 ]]; then
    status="PASSED"
  else
    status="${classification}"
  fi
  CHECKS="$(jq -c --arg group "${group}" --arg code "${code}" --arg title "${title}" \
    --arg status "${status}" --arg log "${log_file}" --argjson exitCode "${exit_code}" \
    --argjson durationMs "$(((ended - started) * 1000))" \
    '. + [{group:$group,code:$code,title:$title,status:$status,exitCode:$exitCode,durationMs:$durationMs,log:$log}]' <<<"${CHECKS}")"
}

echo "[1/4] Product core runtime"
run_check CORE BACKEND_READINESS "Backend readiness" BLOCKED curl --fail --silent --show-error --max-time 5 "${BACKEND_URL}/actuator/health/readiness"
run_check CORE FRONTEND_HTTP "Frontend HTTP" BLOCKED curl --fail --silent --show-error --max-time 5 "${FRONTEND_URL}/"
run_check CORE KUBERNETES_READ "Kubernetes API read access" BLOCKED kubectl get pods -n aiops-system

echo "[2/4] Commercial runtime package"
run_check COMMERCIAL PACKAGING_CONTRACT "All-in-one packaging contract" BLOCKED "${ROOT_DIR}/scripts/validate-packaging.sh"
run_check COMMERCIAL SECURITY_CONTRACT "Security contract" BLOCKED "${ROOT_DIR}/scripts/validate-security.sh"

echo "[3/4] Quality and evidence"
run_check QUALITY FRONTEND_VALIDATION "Frontend typecheck, tests and build" CONDITIONAL "${ROOT_DIR}/scripts/validate-frontend.sh"
run_check QUALITY BACKEND_VALIDATION "Backend tests and Testcontainers" CONDITIONAL "${ROOT_DIR}/scripts/validate-backend.sh"
run_check QUALITY AI_E2E_REPORT "AI Analysis A-1 to A-6 evidence" BLOCKED "${ROOT_DIR}/scripts/acceptance/validate-ai-analysis-e2e-report.sh"

echo "[4/4] Documentation and handoff"
run_check DOCS DOCUMENTATION_GATE "Documentation structure" BLOCKED "${ROOT_DIR}/scripts/validate-docs.sh"
run_check DOCS RELEASE_RUNBOOK "Release runbook" BLOCKED test -s "${ROOT_DIR}/docs/operations/release-runbook.md"
run_check DOCS USER_GUIDE "User guide entrypoint" BLOCKED test -s "${ROOT_DIR}/docs/user-guide/README.md"

payload="$(jq -n --arg runId "${RUN_ID}" --arg generatedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  --argjson checks "${CHECKS}" '{runId:$runId,generatedAt:$generatedAt,workstreams:["CORE","COMMERCIAL","QUALITY","DOCS"],checks:$checks}')"
printf '%s\n' "${payload}" >"${REPORT}"

echo "Product readiness report: ${REPORT}"
jq -r '.checks[] | "\(.group) / \(.code): \(.status) (\(.durationMs)ms)"' "${REPORT}"

if jq -e '.checks[] | select(.status != "PASSED")' "${REPORT}" >/dev/null; then
  echo "Product readiness is not releasable. Review the report and logs; no failed check was promoted to passed." >&2
  exit 6
fi

echo "Product readiness passed: all four workstreams are green."
