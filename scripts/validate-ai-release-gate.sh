#!/usr/bin/env bash
set -euo pipefail

BACKEND_URL="${AIOPS_BACKEND_URL:-http://127.0.0.1:8080}"
CANDIDATE_VERSION="${AIOPS_AI_CANDIDATE_VERSION:-local-rc}"
BASELINE_VERSION="${AIOPS_AI_BASELINE_VERSION:-k8s-analysis-contract-v1}"
MIN_REGRESSION_SCORE="${AIOPS_AI_MIN_REGRESSION_SCORE:-90}"
MIN_GROUND_TRUTH="${AIOPS_AI_MIN_GROUND_TRUTH:-0}"
MIN_VERIFIED_ACCURACY="${AIOPS_AI_MIN_VERIFIED_ACCURACY:-0}"

for command in curl jq; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "${command} is required for the AI release gate." >&2
    exit 2
  fi
done

payload="$(jq -n \
  --arg candidate "${CANDIDATE_VERSION}" \
  --arg baseline "${BASELINE_VERSION}" \
  --argjson regression "${MIN_REGRESSION_SCORE}" \
  --argjson groundTruth "${MIN_GROUND_TRUTH}" \
  --argjson accuracy "${MIN_VERIFIED_ACCURACY}" \
  '{candidateVersion:$candidate,baselineVersion:$baseline,minimumRegressionScore:$regression,minimumGroundTruthSamples:$groundTruth,minimumVerifiedAccuracy:$accuracy}')"

response="$(curl --fail-with-body --silent --show-error \
  --header 'Content-Type: application/json' \
  --header 'X-Request-Id: rc-ai-release-gate' \
  --request POST \
  --data "${payload}" \
  "${BACKEND_URL}/api/operations/ai-release-gates")"

state="$(jq -r '.state // "UNKNOWN"' <<<"${response}")"
score="$(jq -r '.regressionScore // 0' <<<"${response}")"
reasons="$(jq -r '(.reasons // []) | join("; ")' <<<"${response}")"

echo "AI release gate: state=${state}, regressionScore=${score}, candidate=${CANDIDATE_VERSION}"
if [[ "${state}" != "PASSED" ]]; then
  echo "AI release gate blocked: ${reasons:-no reason returned}" >&2
  exit 3
fi
