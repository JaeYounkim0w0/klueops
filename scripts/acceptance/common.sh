#!/usr/bin/env bash

# Shared helpers for commercial acceptance evidence. No credential value is written to artifacts.
ACCEPTANCE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ACCEPTANCE_STARTED="$(date +%s)"
ACCEPTANCE_CHECKS='[]'

require_acceptance_command() {
  command -v "$1" >/dev/null 2>&1 || { echo "$1 is required for commercial evidence." >&2; exit 2; }
}

record_check() {
  local category="$1" code="$2" state="$3" title="$4" detail="$5" observed="$6" action="$7" duration_ms="$8"
  ACCEPTANCE_CHECKS="$(jq \
    --arg category "${category}" --arg code "${code}" --arg state "${state}" \
    --arg title "${title}" --arg detail "${detail}" --arg observedValue "${observed}" \
    --arg action "${action}" --argjson durationMs "${duration_ms}" \
    '. + [{category:$category,code:$code,state:$state,title:$title,detail:$detail,observedValue:$observedValue,action:$action,durationMs:$durationMs,artifacts:[]}]' \
    <<<"${ACCEPTANCE_CHECKS}")"
}

elapsed_ms() {
  local started="$1"
  echo $(( ($(date +%s) - started) * 1000 ))
}

http_status() {
  curl --max-time "${AIOPS_ACCEPTANCE_HTTP_TIMEOUT_SECONDS:-10}" --silent --show-error \
    --output /dev/null --write-out '%{http_code}' "$1" 2>/dev/null || true
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  echo "scripts/acceptance/common.sh is a library." >&2
  exit 2
fi
