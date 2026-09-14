#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
FIXTURE_TEMPLATE="${SCRIPT_DIR}/fixtures/large-cluster-soak-base.yaml"
SYSTEM_NAMESPACE="${AIOPS_SYSTEM_NAMESPACE:-aiops-system}"
APPLICATION_URL="${AIOPS_APPLICATION_URL:-http://127.0.0.1:30081}"
EXPECTED_CONTEXT="${AIOPS_SOAK_EXPECTED_CONTEXT:-docker-desktop}"
PROFILE="${AIOPS_SOAK_PROFILE:-smoke}"
TTL_SECONDS="${AIOPS_SOAK_TTL_SECONDS:-3600}"
RESOURCE_COUNT="${AIOPS_SOAK_RESOURCE_COUNT:-500}"
CONCURRENCY="${AIOPS_SOAK_CONCURRENCY:-20,50,100}"
BATCH_SIZE="${AIOPS_SOAK_BATCH_SIZE:-250}"
RUN_ID="$(date -u +%Y%m%d%H%M%S)-$$"
FIXTURE_NAMESPACE="aiops-soak-${RUN_ID}"
RBAC_NAME="aiops-soak-${RUN_ID}"
EXPECTED_CONFIRMATION="RUN ${PROFILE} SOAK ON ${EXPECTED_CONTEXT} WITH ${RESOURCE_COUNT} RESOURCES"
REPORT_DIR="${AIOPS_SOAK_REPORT_DIR:-${ROOT_DIR}/artifacts/acceptance}"
REPORT="${REPORT_DIR}/large-cluster-soak-${PROFILE}-${RESOURCE_COUNT}-${RUN_ID}.json"
RENDERED_MANIFEST="$(mktemp "${TMPDIR:-/tmp}/aiops-soak.XXXXXX.yaml")"
CREDENTIAL_FILE="$(mktemp "${TMPDIR:-/tmp}/aiops-soak-credential.XXXXXX.json")"
TOKEN_FILE="$(mktemp "${TMPDIR:-/tmp}/aiops-soak-token.XXXXXX")"
CA_FILE="$(mktemp "${TMPDIR:-/tmp}/aiops-soak-ca.XXXXXX")"
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
    if [[ "${fixture_label}" == "large-cluster-soak" ]]; then
      kubectl delete clusterrolebinding "${RBAC_NAME}" --ignore-not-found >/dev/null
      kubectl delete clusterrole "${RBAC_NAME}" --ignore-not-found >/dev/null
      kubectl delete namespace "${FIXTURE_NAMESPACE}" --wait=true --timeout=300s >/dev/null
    fi
  fi
}
trap cleanup EXIT INT TERM

fail() {
  echo "[LARGE-SOAK] $*" >&2
  exit 2
}

[[ "${AIOPS_LARGE_CLUSTER_SOAK_ENABLED:-false}" == "true" ]] || \
  fail "disabled; set AIOPS_LARGE_CLUSTER_SOAK_ENABLED=true"
[[ "${AIOPS_LARGE_CLUSTER_SOAK_CONFIRMATION:-}" == "${EXPECTED_CONFIRMATION}" ]] || \
  fail "exact confirmation is required: ${EXPECTED_CONFIRMATION}"
[[ "${PROFILE}" == "smoke" || "${PROFILE}" == "large" ]] || fail "profile must be smoke or large"
[[ "${TTL_SECONDS}" =~ ^[0-9]+$ ]] && (( TTL_SECONDS >= 600 && TTL_SECONDS <= 7200 )) || \
  fail "AIOPS_SOAK_TTL_SECONDS must be between 600 and 7200"
[[ "${RESOURCE_COUNT}" =~ ^[0-9]+$ ]] && (( RESOURCE_COUNT >= 100 && RESOURCE_COUNT <= 20000 )) || \
  fail "AIOPS_SOAK_RESOURCE_COUNT must be between 100 and 20000"
[[ "${BATCH_SIZE}" =~ ^[0-9]+$ ]] && (( BATCH_SIZE >= 50 && BATCH_SIZE <= 500 )) || \
  fail "AIOPS_SOAK_BATCH_SIZE must be between 50 and 500"
[[ "${CONCURRENCY}" =~ ^[0-9]+(,[0-9]+)*$ ]] || fail "invalid concurrency list"
if [[ "${PROFILE}" == "smoke" ]]; then
  (( RESOURCE_COUNT <= 1000 )) || fail "smoke profile is capped at 1000 resources"
else
  [[ "${RESOURCE_COUNT}" == "5000" || "${RESOURCE_COUNT}" == "20000" ]] || \
    fail "large profile resource count must be 5000 or 20000"
  [[ ",${CONCURRENCY}," == *,20,* && ",${CONCURRENCY}," == *,50,* && ",${CONCURRENCY}," == *,100,* ]] || \
    fail "large profile must include concurrency steps 20,50,100"
fi

for command in kubectl jq openssl npm sed docker; do
  command -v "${command}" >/dev/null 2>&1 || fail "${command} is required"
done
[[ -s "${FIXTURE_TEMPLATE}" ]] || fail "fixture template is missing"
[[ "$(kubectl config current-context)" == "${EXPECTED_CONTEXT}" ]] || fail "approved Kubernetes context mismatch"
[[ "$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 10 "${APPLICATION_URL}/actuator/health")" == "200" ]] || \
  fail "Portal health endpoint is not ready"

for permission in \
  "create namespaces" "delete namespaces" \
  "create clusterroles.rbac.authorization.k8s.io" "delete clusterroles.rbac.authorization.k8s.io" \
  "create clusterrolebindings.rbac.authorization.k8s.io" "delete clusterrolebindings.rbac.authorization.k8s.io"; do
  read -r verb resource <<<"${permission}"
  kubectl auth can-i "${verb}" "${resource}" | grep -qx yes || fail "RBAC denied: ${permission}"
done

now_epoch="$(date +%s)"
while IFS=$'\t' read -r namespace run_id expires_at; do
  [[ -n "${namespace}" ]] || continue
  [[ "${namespace}" == aiops-soak-* && "${run_id}" =~ ^[0-9-]+$ ]] || fail "unexpected stale fixture identity: ${namespace}"
  if [[ "${expires_at}" =~ ^[0-9]+$ ]] && (( expires_at < now_epoch )); then
    echo "[LARGE-SOAK] removing expired fixture ${namespace}"
    kubectl delete clusterrolebinding "aiops-soak-${run_id}" --ignore-not-found >/dev/null
    kubectl delete clusterrole "aiops-soak-${run_id}" --ignore-not-found >/dev/null
    kubectl delete namespace "${namespace}" --wait=true --timeout=300s >/dev/null
  else
    fail "active or unbounded large-cluster fixture already exists: ${namespace}"
  fi
done < <(kubectl get namespaces -l aiops.strato.io/fixture=large-cluster-soak -o json | \
  jq -r '.items[] | [.metadata.name, .metadata.labels["aiops.strato.io/run-id"], (.metadata.annotations["aiops.strato.io/expires-at"] // "")] | @tsv')

expires_at="$((now_epoch + TTL_SECONDS))"
sed -e "s/__NAMESPACE__/${FIXTURE_NAMESPACE}/g" \
  -e "s/__RUN_ID__/${RUN_ID}/g" \
  -e "s/__RBAC_NAME__/${RBAC_NAME}/g" \
  -e "s/__EXPIRES_AT__/${expires_at}/g" \
  "${FIXTURE_TEMPLATE}" >"${RENDERED_MANIFEST}"

echo "[LARGE-SOAK] creating bounded fixture ${FIXTURE_NAMESPACE}"
kubectl apply -f "${RENDERED_MANIFEST}" >/dev/null
FIXTURE_CREATED=true
kubectl -n "${FIXTURE_NAMESPACE}" rollout status deployment/log-source --timeout=120s >/dev/null

generate_configmap_batch() {
  local start="$1" end="$2" index separator=""
  printf '{"apiVersion":"v1","kind":"List","items":['
  for ((index = start; index <= end; index++)); do
    printf '%s{"apiVersion":"v1","kind":"ConfigMap","metadata":{"name":"synthetic-%05d","namespace":"%s","labels":{"aiops.strato.io/fixture":"large-cluster-soak","aiops.strato.io/run-id":"%s"}},"data":{"index":"%d","payload":"bounded-synthetic-inventory"}}' \
      "${separator}" "${index}" "${FIXTURE_NAMESPACE}" "${RUN_ID}" "${index}"
    separator=,
  done
  printf ']}'
}

echo "[LARGE-SOAK] creating ${RESOURCE_COUNT} synthetic resources in batches of ${BATCH_SIZE}"
for ((start = 1; start <= RESOURCE_COUNT; start += BATCH_SIZE)); do
  end=$((start + BATCH_SIZE - 1))
  (( end > RESOURCE_COUNT )) && end="${RESOURCE_COUNT}"
  generate_configmap_batch "${start}" "${end}" | kubectl create -f - >/dev/null
done
observed_count="$(kubectl -n "${FIXTURE_NAMESPACE}" get configmaps \
  -l aiops.strato.io/fixture=large-cluster-soak --no-headers | wc -l | tr -d ' ')"
[[ "${observed_count}" == "${RESOURCE_COUNT}" ]] || fail "fixture count mismatch: ${observed_count}"

kubectl -n "${FIXTURE_NAMESPACE}" create token large-cluster-soak --duration=2h >"${TOKEN_FILE}"
kubectl -n "${FIXTURE_NAMESPACE}" get configmap kube-root-ca.crt -o jsonpath='{.data.ca\.crt}' >"${CA_FILE}"
jq -n --rawfile caCertificate "${CA_FILE}" --rawfile token "${TOKEN_FILE}" \
  '{apiServerUrl:"https://kubernetes.default.svc",caCertificate:$caCertificate,token:($token | sub("\\n$"; ""))}' \
  >"${CREDENTIAL_FILE}"
chmod 600 "${CREDENTIAL_FILE}" "${TOKEN_FILE}" "${CA_FILE}"

KEYCLOAK_POD="$(kubectl -n "${SYSTEM_NAMESPACE}" get pods -l app.kubernetes.io/component=keycloak \
  --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}')"
[[ -n "${KEYCLOAK_POD}" ]] || fail "running managed Keycloak Pod not found"
kcadm() {
  kubectl -n "${SYSTEM_NAMESPACE}" exec "${KEYCLOAK_POD}" -- /opt/keycloak/bin/kcadm.sh "$@"
}
kubectl -n "${SYSTEM_NAMESPACE}" exec deployment/aiops-keycloak -- bash -lc \
  '/opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080 --realm master --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >/dev/null'
temporary_username="aiops-soak-${RUN_ID}"
temporary_password="$(openssl rand -hex 24)"
KEYCLOAK_USER_ID="$(kcadm create users -r aiops -s "username=${temporary_username}" -s enabled=true \
  -s emailVerified=true -s firstName=Local -s lastName=Soak -s "email=${temporary_username}@example.invalid" \
  -s 'requiredActions=[]' -i)"
kcadm set-password -r aiops --username "${temporary_username}" --new-password "${temporary_password}" \
  --temporary=false >/dev/null
platform_admin_group_id="$(kcadm get groups -r aiops -q search=aiops-platform-admins -q exact=true \
  --fields id --format csv --noquotes | head -n 1)"
[[ -n "${platform_admin_group_id}" ]] || fail "aiops-platform-admins group not found"
kcadm update "users/${KEYCLOAK_USER_ID}/groups/${platform_admin_group_id}" -r aiops -n >/dev/null

postgres_stat() {
  docker exec postgres-vector sh -lc \
    'psql -X -qAt -U "$POSTGRES_USER" -d aiops -v ON_ERROR_STOP=1 -c "$1"' -- "$1" 2>/dev/null || true
}
tx_before="$(postgres_stat "select xact_commit + xact_rollback from pg_stat_database where datname=current_database()")"
extension_available="$(postgres_stat "select count(*) from pg_extension where extname='pg_stat_statements'")"
calls_before=""
[[ "${extension_available}" == "1" ]] && calls_before="$(postgres_stat "select coalesce(sum(calls),0)::bigint from pg_stat_statements")"

export AIOPS_LARGE_CLUSTER_SOAK=true
export AIOPS_LIVE_IDENTITY_ENABLED=true
export AIOPS_LIVE_APPLICATION_URL="${APPLICATION_URL}"
export AIOPS_SOAK_PROFILE="${PROFILE}"
export AIOPS_SOAK_CLUSTER_NAME="${FIXTURE_NAMESPACE}"
export AIOPS_SOAK_NAMESPACE="${FIXTURE_NAMESPACE}"
export AIOPS_SOAK_LOG_POD="$(kubectl -n "${FIXTURE_NAMESPACE}" get pod -l app.kubernetes.io/name=log-source \
  -o jsonpath='{.items[0].metadata.name}')"
export AIOPS_SOAK_REPORT="${REPORT}"
export AIOPS_SOAK_CREDENTIAL_FILE="${CREDENTIAL_FILE}"
export AIOPS_SOAK_RESOURCE_COUNT="${RESOURCE_COUNT}"
export AIOPS_SOAK_CONCURRENCY="${CONCURRENCY}"
export AIOPS_SOAK_SOURCE_VERSION="$(kubectl -n "${SYSTEM_NAMESPACE}" get deployment aiops-backend \
  -o jsonpath='{.spec.template.spec.containers[0].image}')"
export AIOPS_SOAK_USERNAME="${temporary_username}"
export AIOPS_SOAK_PASSWORD="${temporary_password}"
unset temporary_password
mkdir -p "${REPORT_DIR}"

echo "[LARGE-SOAK] running authenticated workload profile=${PROFILE} resources=${RESOURCE_COUNT} concurrency=${CONCURRENCY}"
playwright_exit=130
set +e
(cd "${ROOT_DIR}/frontend" && npx playwright test e2e/large-cluster-local-soak.spec.ts \
  --project=desktop-chromium --workers=1)
playwright_exit=$?
set -e
unset AIOPS_SOAK_PASSWORD AIOPS_SOAK_USERNAME

tx_after="$(postgres_stat "select xact_commit + xact_rollback from pg_stat_database where datname=current_database()")"
calls_after=""
[[ "${extension_available}" == "1" ]] && calls_after="$(postgres_stat "select coalesce(sum(calls),0)::bigint from pg_stat_statements")"
if [[ -s "${REPORT}" && "${tx_before}" =~ ^[0-9]+$ && "${tx_after}" =~ ^[0-9]+$ ]]; then
  transaction_delta=$((tx_after - tx_before))
  if [[ "${calls_before}" =~ ^[0-9]+$ && "${calls_after}" =~ ^[0-9]+$ ]]; then
    query_delta=$((calls_after - calls_before))
    jq --argjson transactions "${transaction_delta}" --argjson queries "${query_delta}" \
      '.database = {statementCountAvailable:true,queryCount:$queries,transactionCount:$transactions}' \
      "${REPORT}" >"${REPORT}.tmp" && mv "${REPORT}.tmp" "${REPORT}"
  else
    jq --argjson transactions "${transaction_delta}" \
      '.database.transactionCount=$transactions' "${REPORT}" >"${REPORT}.tmp" && mv "${REPORT}.tmp" "${REPORT}"
  fi
fi

(( playwright_exit == 0 )) || fail "Playwright soak failed; report: ${REPORT}"
AIOPS_SOAK_REPORT="${REPORT}" "${SCRIPT_DIR}/validate-large-cluster-soak-report.sh"
echo "[LARGE-SOAK] report: ${REPORT}"
