#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
NAMESPACE="${AIOPS_RUNTIME_NAMESPACE:-aiops-system}"
RELEASE="${AIOPS_RUNTIME_RELEASE:-aiops}"
OUTPUT_DIR="${AIOPS_EVIDENCE_DIR:-${ROOT_DIR}/artifacts/production-evidence}"
mkdir -p "${OUTPUT_DIR}"

if [[ "${AIOPS_ROLLBACK_REHEARSAL_ENABLED:-false}" != true ]]; then
  printf '{"status":"BLOCKED","reason":"Set AIOPS_ROLLBACK_REHEARSAL_ENABLED=true in an approved non-production environment"}\n' \
    >"${OUTPUT_DIR}/helm-rollback.json"
  echo "BLOCKED: rollback rehearsal requires explicit non-production authorization." >&2
  exit 6
fi
for command in helm kubectl jq; do command -v "${command}" >/dev/null || exit 2; done
[[ "$(kubectl config current-context)" == "${AIOPS_ROLLBACK_EXPECTED_CONTEXT:?Expected context is required}" ]] || {
  echo "Refusing rollback rehearsal on an unexpected context." >&2; exit 5;
}
before_revision="$(helm status "${RELEASE}" -n "${NAMESPACE}" -o json | jq -r '.version')"
before_images="$(kubectl -n "${NAMESPACE}" get deployment -l app.kubernetes.io/instance="${RELEASE}" \
  -o json | jq -c '[.items[] | {name:.metadata.name,images:[.spec.template.spec.containers[].image]}] | sort_by(.name)')"

helm upgrade "${RELEASE}" "${ROOT_DIR}/deploy/helm/aiops" -n "${NAMESPACE}" --reuse-values \
  --set-string "global.rehearsalNonce=$(date +%s)" --wait --timeout 10m --rollback-on-failure
exercise_revision="$(helm status "${RELEASE}" -n "${NAMESPACE}" -o json | jq -r '.version')"
helm rollback "${RELEASE}" "${before_revision}" -n "${NAMESPACE}" --wait --timeout 10m
after_images="$(kubectl -n "${NAMESPACE}" get deployment -l app.kubernetes.io/instance="${RELEASE}" \
  -o json | jq -c '[.items[] | {name:.metadata.name,images:[.spec.template.spec.containers[].image]}] | sort_by(.name)')"
[[ "${before_images}" == "${after_images}" ]] || { echo "Image state changed after rollback." >&2; exit 5; }
jq -n --argjson beforeRevision "${before_revision}" --argjson exerciseRevision "${exercise_revision}" \
  --argjson images "${after_images}" '{status:"PASSED",beforeRevision:$beforeRevision,exerciseRevision:$exerciseRevision,images:$images}' \
  >"${OUTPUT_DIR}/helm-rollback.json"
echo "Helm rollback rehearsal passed."
