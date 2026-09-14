#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"
require_acceptance_command jq
require_acceptance_command curl

BASE_URL="${AIOPS_BACKEND_URL:-http://127.0.0.1:8080}"
CLUSTER_ID="${AIOPS_SOAK_CLUSTER_ID:-}"
NAMESPACE="${AIOPS_SOAK_NAMESPACE:-default}"
RUNS="${AIOPS_SOAK_RUNS:-5}"
POLL_SECONDS="${AIOPS_SOAK_POLL_SECONDS:-2}"
JOB_TIMEOUT_SECONDS="${AIOPS_SOAK_JOB_TIMEOUT_SECONDS:-360}"
MIN_SUCCESS_RATIO="${AIOPS_SOAK_MIN_SUCCESS_RATIO:-0.90}"
OUTPUT_DIR="${AIOPS_EVIDENCE_DIR:-${ACCEPTANCE_ROOT}/artifacts/production-evidence}"
AUTH_ARGS=()
mkdir -p "${OUTPUT_DIR}"

[[ -n "${AIOPS_EVIDENCE_BEARER_TOKEN:-}" ]] && AUTH_ARGS=(-H "Authorization: Bearer ${AIOPS_EVIDENCE_BEARER_TOKEN}")
[[ -n "${AIOPS_EVIDENCE_COOKIE_FILE:-}" ]] && AUTH_ARGS=(-b "${AIOPS_EVIDENCE_COOKIE_FILE}")
if [[ -z "${CLUSTER_ID}" || ${#AUTH_ARGS[@]} -eq 0 ]]; then
  jq -n '{status:"BLOCKED",reason:"AIOPS_SOAK_CLUSTER_ID and an authenticated bearer token or cookie file are required"}' \
    >"${OUTPUT_DIR}/analysis-soak.json"
  echo "BLOCKED: authenticated cluster scope is required for AI soak." >&2
  exit 6
fi

results='[]'
for run in $(seq 1 "${RUNS}"); do
  submitted_at="$(date +%s)"
  response="$(curl --fail --silent --show-error --max-time 20 "${AUTH_ARGS[@]}" -X POST \
    "${BASE_URL}/api/analysis/namespaces/${NAMESPACE}/jobs?clusterId=${CLUSTER_ID}")"
  job_id="$(jq -r '.jobId' <<<"${response}")"
  analysis_id="$(jq -r '.analysisId' <<<"${response}")"
  status="PENDING"
  error_code=""
  while (( $(date +%s) - submitted_at < JOB_TIMEOUT_SECONDS )); do
    job="$(curl --fail --silent --show-error --max-time 15 "${AUTH_ARGS[@]}" "${BASE_URL}/api/jobs/${job_id}")"
    status="$(jq -r '.status' <<<"${job}")"
    error_code="$(jq -r '.errorCode // empty' <<<"${job}")"
    [[ "${status}" =~ ^(SUCCEEDED|FAILED|CANCELED|TIMEOUT)$ ]] && break
    sleep "${POLL_SECONDS}"
  done
  [[ "${status}" =~ ^(SUCCEEDED|FAILED|CANCELED|TIMEOUT)$ ]] || status="TIMEOUT"
  duration_ms=$(( ($(date +%s) - submitted_at) * 1000 ))
  fallback=false
  if [[ "${status}" == SUCCEEDED ]]; then
    analysis="$(curl --fail --silent --show-error --max-time 20 "${AUTH_ARGS[@]}" \
      "${BASE_URL}/api/analysis/jobs/${job_id}/result")"
    fallback="$(jq -r '(.resultJson | fromjson? | .analysisRuntime.sections // []) | any(.status == "FALLBACK")' <<<"${analysis}")"
  fi
  results="$(jq --argjson run "${run}" --arg jobId "${job_id}" --arg analysisId "${analysis_id}" \
    --arg status "${status}" --arg errorCode "${error_code}" --argjson durationMs "${duration_ms}" \
    --argjson fallback "${fallback}" '. + [{run:$run,jobId:$jobId,analysisId:$analysisId,status:$status,errorCode:$errorCode,durationMs:$durationMs,fallback:$fallback}]' <<<"${results}")"
done

successes="$(jq '[.[] | select(.status == "SUCCEEDED")] | length' <<<"${results}")"
fallbacks="$(jq '[.[] | select(.fallback == true)] | length' <<<"${results}")"
success_ratio="$(awk -v ok="${successes}" -v total="${RUNS}" 'BEGIN { printf "%.4f", ok / total }')"
status="$(awk -v actual="${success_ratio}" -v minimum="${MIN_SUCCESS_RATIO}" 'BEGIN { print actual >= minimum ? "PASSED" : "FAILED" }')"
jq -n --arg status "${status}" --arg profile "namespace-analysis" --arg namespace "${NAMESPACE}" \
  --argjson runs "${RUNS}" --argjson successes "${successes}" --argjson fallbacks "${fallbacks}" \
  --argjson successRatio "${success_ratio}" --argjson minimumSuccessRatio "${MIN_SUCCESS_RATIO}" \
  --argjson results "${results}" \
  '{status:$status,profile:$profile,namespace:$namespace,runs:$runs,successes:$successes,fallbacks:$fallbacks,successRatio:$successRatio,minimumSuccessRatio:$minimumSuccessRatio,results:$results}' \
  >"${OUTPUT_DIR}/analysis-soak.json"
[[ "${status}" == PASSED ]] || { echo "AI soak failed its minimum success ratio." >&2; exit 5; }
echo "AI soak passed: ${successes}/${RUNS}, fallback=${fallbacks}."

