#!/usr/bin/env bash
set -euo pipefail

REPORT="${AIOPS_SOAK_REPORT:-}"
[[ -n "${REPORT}" && -s "${REPORT}" ]] || { echo "large-cluster soak report is missing" >&2; exit 2; }

jq -e '
  .reportVersion == "1.0"
  and (.status == "PASSED")
  and (.fixture.expectedResources | type == "number")
  and (.fixture.concurrencySteps | type == "array" and length > 0)
  and (.metrics.sync.deduplicated == true)
  and (.metrics.inventory.synchronizedConfigMaps >= .fixture.expectedResources)
  and (.metrics.pagination.latency.p50Ms | type == "number")
  and (.metrics.pagination.latency.p95Ms | type == "number")
  and (.metrics.pagination.latency.p99Ms | type == "number")
  and (.metrics.analysis.deduplicated == true)
  and (.metrics.cancellation.finalStatus == "CANCELED")
  and (.metrics.sseDisconnect.cleanAbortOrOpen >= .metrics.sseDisconnect.concurrency)
  and (.database.transactionCount | type == "number")
  and (.errors | length == 0)
  and (if .profile == "large" then
    ((.fixture.expectedResources == 5000) or (.fixture.expectedResources == 20000))
    and ([20, 50, 100] - .fixture.concurrencySteps | length == 0)
    and (.database.statementCountAvailable == true)
    and (.database.queryCount | type == "number")
  else true end)
' "${REPORT}" >/dev/null

echo "Large-cluster soak report contract passed: ${REPORT}"
