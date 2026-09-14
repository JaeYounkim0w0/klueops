#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
FIXTURE_TEMPLATE="${SCRIPT_DIR}/fixtures/ai-analysis-e2e.yaml"
EXPECTED_CONFIRMATION="CREATE AND MUTATE AI ANALYSIS E2E FIXTURES"
EXPECTED_CONTEXT="${AIOPS_AI_E2E_CONTEXT:-docker-desktop}"
SYSTEM_NAMESPACE="${AIOPS_NAMESPACE:-aiops-system}"
APPLICATION_URL="${AIOPS_LIVE_APPLICATION_URL:-http://127.0.0.1:30081}"
TTL_SECONDS="${AIOPS_AI_E2E_TTL_SECONDS:-2700}"
RUN_ID="$(date -u +%Y%m%d%H%M%S)-$$"
FIXTURE_NAMESPACE="aiops-e2e-${RUN_ID}"
REPORT_DIR="${AIOPS_AI_E2E_REPORT_DIR:-${ROOT_DIR}/artifacts/acceptance}"
REPORT="${REPORT_DIR}/ai-analysis-e2e-${RUN_ID}.json"
RENDERED_MANIFEST="$(mktemp "${TMPDIR:-/tmp}/aiops-ai-e2e.XXXXXX.yaml")"
CREDENTIAL_FILE="$(mktemp "${TMPDIR:-/tmp}/aiops-ai-e2e-credential.XXXXXX.json")"
TOKEN_FILE="$(mktemp "${TMPDIR:-/tmp}/aiops-ai-e2e-token.XXXXXX")"
CA_FILE="$(mktemp "${TMPDIR:-/tmp}/aiops-ai-e2e-ca.XXXXXX")"
FIXTURE_CREATED=false
KEYCLOAK_POD=""
KEYCLOAK_USER_ID=""

cleanup() {
  if [[ -n "${KEYCLOAK_POD}" && -n "${KEYCLOAK_USER_ID}" ]]; then
    kubectl -n "${SYSTEM_NAMESPACE}" exec "${KEYCLOAK_POD}" -- \
      /opt/keycloak/bin/kcadm.sh delete "users/${KEYCLOAK_USER_ID}" -r aiops >/dev/null 2>&1 || true
  fi
  rm -f "${RENDERED_MANIFEST}" "${CREDENTIAL_FILE}" "${TOKEN_FILE}" "${CA_FILE}"
  if [[ "${FIXTURE_CREATED}" == "true" ]]; then
    local fixture_label
    fixture_label="$(kubectl get namespace "${FIXTURE_NAMESPACE}" \
      -o jsonpath='{.metadata.labels.aiops\.strato\.io/fixture}' 2>/dev/null || true)"
    if [[ "${fixture_label}" == "ai-analysis-e2e" ]]; then
      kubectl delete namespace "${FIXTURE_NAMESPACE}" --wait=true --timeout=180s >/dev/null
    fi
  fi
}
trap cleanup EXIT INT TERM

fail() {
  echo "[AI-E2E] $*" >&2
  exit 2
}

[[ "${AIOPS_AI_E2E_ENABLED:-false}" == "true" ]] || fail "disabled; set AIOPS_AI_E2E_ENABLED=true"
[[ "${AIOPS_AI_E2E_CONFIRMATION:-}" == "${EXPECTED_CONFIRMATION}" ]] || \
  fail "exact confirmation is required: ${EXPECTED_CONFIRMATION}"
[[ "${TTL_SECONDS}" =~ ^[0-9]+$ ]] && (( TTL_SECONDS >= 600 && TTL_SECONDS <= 7200 )) || \
  fail "AIOPS_AI_E2E_TTL_SECONDS must be between 600 and 7200"

for command in kubectl jq openssl npm sed; do
  command -v "${command}" >/dev/null 2>&1 || fail "${command} is required"
done
[[ -s "${FIXTURE_TEMPLATE}" ]] || fail "fixture template is missing"

current_context="$(kubectl config current-context)"
[[ "${current_context}" == "${EXPECTED_CONTEXT}" ]] || \
  fail "current context ${current_context} does not match approved context ${EXPECTED_CONTEXT}"

for permission in \
  "create namespaces" "delete namespaces" \
  "create deployments.apps --namespace=${FIXTURE_NAMESPACE}" \
  "update deployments.apps --namespace=${FIXTURE_NAMESPACE}" \
  "create services --namespace=${FIXTURE_NAMESPACE}"; do
  read -r verb resource scope <<<"${permission}"
  if [[ -n "${scope:-}" ]]; then
    kubectl auth can-i "${verb}" "${resource}" "${scope}" 2>/dev/null | grep -qx yes || fail "RBAC denied: ${permission}"
  else
    kubectl auth can-i "${verb}" "${resource}" 2>/dev/null | grep -qx yes || fail "RBAC denied: ${permission}"
  fi
done

now_epoch="$(date +%s)"
while IFS=$'\t' read -r namespace expires_at; do
  [[ -n "${namespace}" ]] || continue
  if [[ "${namespace}" != aiops-e2e-* ]]; then
    fail "refusing unexpected fixture namespace ${namespace}"
  fi
  if [[ "${expires_at}" =~ ^[0-9]+$ ]] && (( expires_at < now_epoch )); then
    echo "[AI-E2E] removing expired fixture ${namespace}"
    kubectl delete namespace "${namespace}" --wait=true --timeout=180s >/dev/null
  else
    fail "active or unbounded fixture already exists: ${namespace}"
  fi
done < <(kubectl get namespaces -l aiops.strato.io/fixture=ai-analysis-e2e -o json | \
  jq -r '.items[] | [.metadata.name, (.metadata.annotations["aiops.strato.io/expires-at"] // "")] | @tsv')

expires_at="$((now_epoch + TTL_SECONDS))"
sed -e "s/__NAMESPACE__/${FIXTURE_NAMESPACE}/g" \
  -e "s/__RUN_ID__/${RUN_ID}/g" \
  -e "s/__EXPIRES_AT__/${expires_at}/g" \
  "${FIXTURE_TEMPLATE}" >"${RENDERED_MANIFEST}"

echo "[AI-E2E] creating bounded fixture ${FIXTURE_NAMESPACE}"
kubectl apply -f "${RENDERED_MANIFEST}" >/dev/null
FIXTURE_CREATED=true
kubectl -n "${FIXTURE_NAMESPACE}" rollout status deployment/service-backend --timeout=120s >/dev/null
kubectl -n "${FIXTURE_NAMESPACE}" rollout status deployment/rollout-target --timeout=120s >/dev/null
kubectl -n "${FIXTURE_NAMESPACE}" wait --for=create pod \
  -l app.kubernetes.io/name=privileged-port-failure --timeout=60s >/dev/null
failure_log_observed=false
for _ in $(seq 1 30); do
  if kubectl -n "${FIXTURE_NAMESPACE}" logs deployment/privileged-port-failure --tail=20 2>/dev/null | \
    grep -q 'Permission denied'; then
    failure_log_observed=true
    break
  fi
  sleep 1
done
[[ "${failure_log_observed}" == "true" ]] || fail "privileged-port failure log was not observed"

kubectl -n "${FIXTURE_NAMESPACE}" set env deployment/rollout-target RELEASE_MARKER=revision-2 >/dev/null
kubectl -n "${FIXTURE_NAMESPACE}" rollout status deployment/rollout-target --timeout=120s >/dev/null
kubectl -n "${FIXTURE_NAMESPACE}" create token ai-analysis-acceptance --duration=1h >"${TOKEN_FILE}"
kubectl -n "${FIXTURE_NAMESPACE}" get configmap kube-root-ca.crt -o jsonpath='{.data.ca\.crt}' >"${CA_FILE}"
jq -n --rawfile caCertificate "${CA_FILE}" --rawfile token "${TOKEN_FILE}" \
  '{apiServerUrl:"https://kubernetes.default.svc",caCertificate:$caCertificate,token:($token | sub("\\n$"; ""))}' \
  >"${CREDENTIAL_FILE}"
chmod 600 "${CREDENTIAL_FILE}" "${TOKEN_FILE}" "${CA_FILE}"

KEYCLOAK_POD="$(kubectl -n "${SYSTEM_NAMESPACE}" get pods \
  -l app.kubernetes.io/component=keycloak --field-selector=status.phase=Running \
  -o jsonpath='{.items[0].metadata.name}')"
[[ -n "${KEYCLOAK_POD}" ]] || fail "running managed Keycloak Pod not found"
kcadm() {
  kubectl -n "${SYSTEM_NAMESPACE}" exec "${KEYCLOAK_POD}" -- /opt/keycloak/bin/kcadm.sh "$@"
}
kubectl -n "${SYSTEM_NAMESPACE}" exec deployment/aiops-keycloak -- bash -lc \
  '/opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080 --realm master --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >/dev/null'

temporary_username="aiops-e2e-${RUN_ID}"
temporary_password="$(openssl rand -hex 24)"
KEYCLOAK_USER_ID="$(kcadm create users -r aiops \
  -s "username=${temporary_username}" -s enabled=true -s emailVerified=true \
  -s firstName=Local -s lastName=Acceptance \
  -s "email=${temporary_username}@example.invalid" -s 'requiredActions=[]' -i)"
kcadm set-password -r aiops --username "${temporary_username}" \
  --new-password "${temporary_password}" --temporary=false >/dev/null
platform_admin_group_id="$(kcadm get groups -r aiops -q search=aiops-platform-admins -q exact=true \
  --fields id --format csv --noquotes | head -n 1)"
[[ -n "${platform_admin_group_id}" ]] || fail "aiops-platform-admins group not found"
kcadm update "users/${KEYCLOAK_USER_ID}/groups/${platform_admin_group_id}" -r aiops -n >/dev/null

export AIOPS_AI_E2E_ACCEPTANCE=true
export AIOPS_LIVE_IDENTITY_ENABLED=true
export AIOPS_LIVE_APPLICATION_URL="${APPLICATION_URL}"
export AIOPS_AI_E2E_CLUSTER_NAME="${FIXTURE_NAMESPACE}"
export AIOPS_AI_E2E_NAMESPACE="${FIXTURE_NAMESPACE}"
export AIOPS_AI_E2E_REPORT="${REPORT}"
export AIOPS_AI_E2E_CREDENTIAL_FILE="${CREDENTIAL_FILE}"
export AIOPS_AI_E2E_SOURCE_VERSION="$(kubectl -n "${SYSTEM_NAMESPACE}" get deployment aiops-backend \
  -o jsonpath='{.spec.template.spec.containers[0].image}')"
export AIOPS_AI_E2E_USERNAME="${temporary_username}"
export AIOPS_AI_E2E_PASSWORD="${temporary_password}"
unset temporary_password
mkdir -p "${REPORT_DIR}"

echo "[AI-E2E] running A-1 to A-6 through the authenticated Portal"
(cd "${ROOT_DIR}/frontend" && npx playwright test e2e/ai-analysis-local-acceptance.spec.ts \
  --project=desktop-chromium --workers=1)
unset AIOPS_AI_E2E_PASSWORD AIOPS_AI_E2E_USERNAME

AIOPS_AI_E2E_REPORT="${REPORT}" "${SCRIPT_DIR}/validate-ai-analysis-e2e-report.sh"
echo "[AI-E2E] report: ${REPORT}"
