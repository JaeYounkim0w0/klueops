#!/usr/bin/env bash
set -euo pipefail

# Validates the operator acceptance report without treating pending work as passed.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
REPORT="${AIOPS_AI_E2E_REPORT:-${ROOT_DIR}/docs/development/ai-analysis-e2e-report.template.json}"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required to validate the AI Analysis E2E report." >&2
  exit 2
fi
if [[ ! -s "${REPORT}" ]]; then
  echo "AI Analysis E2E report is missing: ${REPORT}" >&2
  exit 3
fi

required_ids='["A-1","A-2","A-3","A-4","A-5","A-6"]'
if ! jq -e --argjson ids "${required_ids}" '
  (.reportVersion | type == "string" and length > 0)
  and (.environment | type == "string" and length > 0)
  and (.scenarios | type == "array" and length == 6)
  and ([.scenarios[].scenarioId] | sort) == ($ids | sort)
  and ([.scenarios[].status] | all(. == "PENDING" or . == "PASSED" or . == "FAILED" or . == "BLOCKED"))
  and all(.scenarios[];
    .status == "PENDING"
    or ((.cluster | type == "string" and length > 0)
      and (.namespace | type == "string" and length > 0)
      and (.evidence | type == "array" and length > 0)))
' "${REPORT}" >/dev/null; then
  echo "AI Analysis E2E report schema or scenario matrix is invalid: ${REPORT}" >&2
  exit 4
fi

pending="$(jq '[.scenarios[] | select(.status == "PENDING")] | length' "${REPORT}")"
failed="$(jq '[.scenarios[] | select(.status == "FAILED" or .status == "BLOCKED")] | length' "${REPORT}")"
passed="$(jq '[.scenarios[] | select(.status == "PASSED")] | length' "${REPORT}")"

if [[ "${pending}" -gt 0 || "${failed}" -gt 0 ]]; then
  echo "AI Analysis E2E report is not releasable: passed=${passed}, pending=${pending}, failed_or_blocked=${failed}." >&2
  exit 6
fi

echo "AI Analysis E2E report passed: ${passed}/6 scenarios with evidence."
